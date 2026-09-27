plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
}

// ── Rust 核心（jasmine-ffi）：交叉编译 + 生成 Kotlin 绑定 ──────────────────────
//
// 产物都落在 build/ 下，跟着 clean / rebuild 一起走，不往源码树里塞生成物。
// 编译到哪些 ABI 由这里决定，与 Android 打包的 ABI 保持一致。

val rustDir = rootProject.layout.projectDirectory.dir("rust")
val rustManifest = rustDir.file("Cargo.toml").asFile
val rustLock = rustDir.file("Cargo.lock").asFile
val rustSources = rustDir.asFileTree.matching { exclude("target/**") }

val rustJniLibs = layout.buildDirectory.dir("rust/jniLibs")
val rustKotlin = layout.buildDirectory.dir("rust/kotlin")
rustJniLibs.get().asFile.mkdirs()
rustKotlin.get().asFile.mkdirs()


/** ABI → Rust target triple。 */
val rustAbis = linkedMapOf(
  "arm64-v8a" to "aarch64-linux-android",
  "armeabi-v7a" to "armv7-linux-androideabi",
  "x86_64" to "x86_64-linux-android",
  "x86" to "i686-linux-android",
)

android {
  namespace = "com.lhzkml.jasmine.core.agent"
  compileSdk { version = release(37) }

  // ADK core 的 Android 变体要求 minSdk 26，模块必须对齐。
  defaultConfig {
    minSdk = 26
    // JNA 靠反射，release 的 R8 会改坏它；规则归本模块所有，见 consumer-rules.pro。
    consumerProguardFiles("consumer-rules.pro")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  // Rust 核心的两个产物（.so 与生成的 Kotlin）走变体源集挂进来，见文件末尾的
  // androidComponents；这里不用旧式 sourceSets：AGP 9 的 library 模块访问它必崩
  // （`DefaultAndroidLibrarySourceSet_Decorated cannot be cast to AndroidLibrarySourceSet`）。
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }

// Rust 核心的产物：交叉编译出来的 .so 与 UniFFI 生成的 Kotlin，都挂到变体源集上。
androidComponents {
  onVariants { variant ->
    variant.sources.jniLibs?.addStaticSourceDirectory(rustJniLibs.get().asFile.absolutePath)
    variant.sources.kotlin?.addStaticSourceDirectory(rustKotlin.get().asFile.absolutePath)
  }
}

dependencies {
  // 供应商配置（baseUrl / apiKey / apiType）。`api` 而非 `implementation`：
  // ProviderConfig 出现在 ProviderProbe 的公开签名里，消费者必须能看到该类型。
  api(project(":core:data"))

  // ADK 的模型抽象：只需 core，不涉及任何 Google 后端模块。
  implementation(libs.androidx.adk.core) {
    // kxml2 自带一份 org.xmlpull.v1，而 Android 平台已提供该包，R8 会直接
    // 失败："Library class android.content.res.XmlResourceParser implements
    // program class org.xmlpull.v1.XmlPullParser"。
    //
    // 取舍：ADK 只有一个类真正用它（`FunctionToolExtensionsKt`，把 OpenAPI
    // 规范转成工具时直接 new 了 org.kxml2.io.KXmlSerializer），而本项目不使用
    // OpenAPI 规范式工具，因此该路径不可达。若将来要支持它，需要改用能剥离
    // org.xmlpull.v1 包的 transform，而不是排除整个模块。
    exclude(group = "net.sf.kxml", module = "kxml2")
  }

  // ADK 官方 KSP 处理器：把 @Tool 注解的函数在编译期变成 FunctionTool（含 schema），
  // 替代手写 FunctionDeclaration/Schema。
  ksp(libs.androidx.adk.processor)

  implementation(libs.okhttp)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.coroutines.core)

  // Rust 核心的绑定由 UniFFI 生成，生成物跑在 JNA 上（Android 用 aar 那个变体）。
  implementation(libs.jna) { artifact { type = "aar" } }

  // DI: the agent layer is reached through its Hilt-provided facade.
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)

  // Wire-format translation and the session/replay path are covered by plain JVM
  // unit tests (the latter against a recording Model — no network involved).
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}

val buildRustCore = tasks.register<Exec>("buildRustCore") {
  group = "rust"
  description = "交叉编译 Rust 核心（每个 Android ABI 一个 .so）。"
  workingDir = rustDir.asFile
  inputs.files(rustSources)
  inputs.file(rustManifest)
  inputs.file(rustLock)
  outputs.dir(rustJniLibs)
  val args = mutableListOf("cargo", "ndk")
  rustAbis.keys.forEach { args += listOf("-t", it) }
  args += listOf("-o", rustJniLibs.get().asFile.absolutePath, "build", "--release", "-p", "jasmine-ffi")
  commandLine(args)
}

val buildRustHostLib = tasks.register<Exec>("buildRustHostLib") {
  group = "rust"
  description = "为本机构建 Rust 核心（生成 Kotlin 绑定时要读它）。"
  workingDir = rustDir.asFile
  inputs.files(rustSources)
  inputs.file(rustManifest)
  inputs.file(rustLock)
  commandLine("cargo", "build", "-p", "jasmine-ffi")
}

val generateRustBindings = tasks.register<Exec>("generateRustBindings") {
  group = "rust"
  description = "用 UniFFI 生成 Kotlin 绑定。"
  dependsOn(buildRustHostLib)
  workingDir = rustDir.asFile
  inputs.files(rustSources)
  inputs.file(rustManifest)
  inputs.file(rustLock)
  outputs.dir(rustKotlin)
  val hostOs = org.gradle.internal.os.OperatingSystem.current()
  val hostLibrary = when {
    hostOs.isWindows -> "jasmine_ffi.dll"
    hostOs.isMacOsX -> "libjasmine_ffi.dylib"
    else -> "libjasmine_ffi.so"
  }
  commandLine(
    "cargo", "run", "-p", "jasmine-ffi", "--features", "bindgen-cli", "--bin", "uniffi-bindgen", "--",
    "generate",
    "--library", rustDir.dir("target/debug/$hostLibrary").asFile.absolutePath,
    "--language", "kotlin",
    "--out-dir", rustKotlin.get().asFile.absolutePath,
  )
}

// 编译这一层之前，先有 .so 和绑定。
tasks.named("preBuild") { dependsOn(buildRustCore, generateRustBindings) }

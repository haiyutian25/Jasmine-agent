plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.lhzkml.jasmine.core.agent"
  compileSdk { version = release(37) }

  // ADK core 的 Android 变体要求 minSdk 26，模块必须对齐。
  defaultConfig { minSdk = 26 }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }

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

  // DI: the agent layer is reached through its Hilt-provided facade.
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)

  // Wire-format translation and the session/replay path are covered by plain JVM
  // unit tests (the latter against a recording Model — no network involved).
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}

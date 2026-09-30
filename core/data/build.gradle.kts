plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.lhzkml.jasmine.core.data"
  compileSdk { version = release(37) }

  defaultConfig { minSdk = 26 }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }

dependencies {
  // 会话转写由 Rust 核心的 rollout 自己落盘（每会话一个 JSONL），本模块不再持有任何数据库。
  implementation(project(":core:network"))

  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.androidx.datastore.preferences)
  // 界面语言经 AppCompat 的 per-app locales 落地（AppLanguageRepositoryImpl）。
  implementation(libs.androidx.appcompat)
  // ProviderConfig 列表以 JSON 形式存进 DataStore（模型提供商配置）
  implementation(libs.kotlinx.serialization.json)
  // ResponseBody type of FontDownloadApi (core:network) used by FontRemoteDataSource.
  implementation(libs.okhttp)

  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
}

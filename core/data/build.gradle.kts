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
  // Chat transcript persistence. `implementation`, not `api`: Room entities and
  // DAOs stay behind the repository boundary, so no consumer needs to see them.
  // Room itself is a direct dependency because this module touches RoomDatabase
  // (to obtain the DAO) and the Room-annotated entity types.

  implementation(libs.androidx.room.runtime)
  implementation(project(":core:network"))

  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.androidx.datastore.preferences)
  // ProviderConfig 列表以 JSON 形式存进 DataStore（模型提供商配置）
  implementation(libs.kotlinx.serialization.json)
  // ResponseBody type of FontDownloadApi (core:network) used by FontRemoteDataSource.
  implementation(libs.okhttp)

  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
}

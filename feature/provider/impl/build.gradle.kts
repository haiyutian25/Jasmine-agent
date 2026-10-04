plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.lhzkml.jasmine.feature.provider.impl"
  compileSdk { version = release(37) { minorApiLevel = 2 } }

  defaultConfig { minSdk = 26 }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures { compose = true }

  // ViewModel 的失败路径会打日志（android.util.Log）；纯 JVM 单测里它必须退化成 no-op，
  // 而不是抛 "not mocked"（main:impl 同一个做法）。
  testOptions { unitTests { isReturnDefaultValues = true } }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }

dependencies {
  api(project(":feature:provider:api"))
  implementation(project(":core:ui"))
  // 部分操作按钮改用 core:widgets 的自有 Button/Text（不再用 core:ui 的自建按钮）。
  implementation(project(":core:widgets"))
  implementation(project(":core:data"))
  // Provider connectivity check runs through the agent layer's probe facade.
  implementation(project(":core:agent"))

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)

  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}

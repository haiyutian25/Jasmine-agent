plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.lhzkml.jasmine.feature.settings.impl"
  compileSdk { version = release(37) { minorApiLevel = 2 } }

  defaultConfig { minSdk = 26 }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures { compose = true }

  // 语言页 ViewModel 的单测在纯 JVM 跑：android.* 桩方法返回默认值而不是抛。
  testOptions { unitTests { isReturnDefaultValues = true } }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }

dependencies {
  api(project(":feature:settings:api"))
  implementation(project(":core:ui"))
  implementation(project(":core:data"))
  // 只给调试页预览自建组件层（按钮等）用；别的地方一行都不碰它。
  implementation(project(":core:widgets"))
  // 用量页要显示核心读出来的统计（`AppUsage`），页面本身是静态的。
  implementation(project(":core:agent"))

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)

  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}

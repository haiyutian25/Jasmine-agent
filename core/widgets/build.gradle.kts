plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.lhzkml.jasmine.core.widgets"
  compileSdk { version = release(37) { minorApiLevel = 2 } }

  defaultConfig { minSdk = 26 }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures { compose = true }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.material3)
  // 形状变换型加载指示器与波形进度条：Morph / RoundedPolygon（自带形状库，与 Compose 版本无关）。
  implementation(libs.androidx.graphics.shapes)
  // BottomSheet 的 Android 实现直接用到了这几个库的扩展（返回手势、Dialog、ViewTree 属主）：
  // material3 在这是 implementation，它的传递依赖不会自动上来。
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  // 颜色与圆角一律来自设计令牌（CssVariables）；牌面不读 M3 的 ColorScheme。
  implementation(project(":core:ui"))
}

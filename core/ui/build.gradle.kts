plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.lhzkml.jasmine.core.ui"
  compileSdk { version = release(37) { minorApiLevel = 2 } }

  defaultConfig { minSdk = 26 }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures { compose = true }

  // 出站命令的失败路径与组件逻辑要能在纯 JVM 下测（android.* 桩方法退化为默认值）。
  testOptions { unitTests { isReturnDefaultValues = true } }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.ui.graphics)
  // 图标是自带的 LucideIcons（不依赖 material-icons-extended）。
  // UDF base: BaseViewModel (stateFlow/eventFlow/actionChannel) + EffectRunner + EventsEffect
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}

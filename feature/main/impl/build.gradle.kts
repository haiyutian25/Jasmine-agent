plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.lhzkml.jasmine.feature.main.impl"
  compileSdk { version = release(37) }

  defaultConfig { minSdk = 26 }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures { compose = true }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }

dependencies {
  api(project(":feature:main:api"))
  // Chat surface: the shell hosts the conversation, so it owns the AgentChat facade.
  implementation(project(":core:agent"))
  implementation(project(":core:ui"))
  implementation(project(":core:data"))
  implementation(project(":core:navigation"))
  // 设置流：导航契约走 :api，屏幕实现由外壳的 NavDisplay 组装
  implementation(project(":feature:settings:api"))
  implementation(project(":feature:settings:impl"))
  // 模型提供商流：同样由外壳的 NavDisplay 组装
  implementation(project(":feature:provider:api"))
  implementation(project(":feature:provider:impl"))

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  // NavHost 内获取 provider 特性的 ViewModel（条目作用域）
  implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
  // 为每个 NavEntry 提供独立的 ViewModelStoreOwner（条目弹出即清除 ViewModel）
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)

  // The chat state machine is tested against fake repositories and a fake AgentChat.
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}

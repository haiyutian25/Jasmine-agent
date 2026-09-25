plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.lhzkml.jasmine.core.markdown"
  compileSdk { version = release(37) }
  ndkVersion = "28.2.13676358"

  defaultConfig {
    minSdk = 26

    // JNI 按原始类名/签名查找回调类，规则随模块走，消费方自动生效。
    // 缺了它 release 包会在首个流式 chunk 到达时闪退 —— 详见该文件注释。
    consumerProguardFiles("consumer-rules.pro")

    externalNativeBuild {
      cmake {
        // 与 ima 的 libincremark_jni.so 一致：C11，静态链接 libc++。
        arguments += listOf(
          "-DANDROID_STL=c++_static",
          "-DCMAKE_BUILD_TYPE=Release",
        )
        // ⚠️ 不要加 -fvisibility=hidden：ima 的 .so 导出了完整的
        //    cmark_gfm_* / create_*_extension / _scan_* 符号集（见
        //    verification/01_dynsym_exports.txt），全局隐藏会让它们全部消失。
        //    只需要隐藏的部分（incremark_jni.c 自身）由 CMakeLists 里的
        //    C_VISIBILITY_PRESET 控制。
        cFlags += listOf("-O2")
      }
    }

    // ⚠️ 必须与 APK 的其它 .so 覆盖同一组 ABI。
    //    只编 2 个的话，armeabi-v7a / x86 设备上 System.loadLibrary("incremark_jni")
    //    会抛 UnsatisfiedLinkError（APK 里有那俩 ABI 目录，但里面没有本库）。
    ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }
  }

  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }

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
  implementation(libs.androidx.compose.material3)

  // 渲染层取色/圆角一律走设计系统令牌（CssVariables），与 core:ui 的组件同一约定。
  api(project(":core:ui"))

  // 解析核心是 native，纯 JVM 单测跑不到 .so；
  // 与解析无关的纯 Kotlin 逻辑（渲染映射等）仍可在此做单测。
  testImplementation(libs.junit)
}

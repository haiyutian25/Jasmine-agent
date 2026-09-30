package com.lhzkml.jasmine.core.data.repository

/** 界面语言：跟随系统、英文、简体中文。 */
enum class AppLanguage {
    SYSTEM,
    ENGLISH,
    CHINESE,
}

/**
 * 应用界面的语言。
 *
 * 这份选择由平台自己持久化（AppCompat 的 per-app locales，写进系统侧并在应用时重建 Activity），
 * 所以这里不落我们自己的存储：读回来的就是平台当前生效的那一个。
 *
 * 平台 API 只在这一层出现 —— View 与 ViewModel 都不直接调用它（UDF：状态由 ViewModel 持有，
 * 平台细节由 data 层包起来）。
 */
interface AppLanguageRepository {
    /** 当前生效的语言。 */
    fun current(): AppLanguage

    /** 应用选择：持久化并让界面切到该语言。 */
    fun apply(language: AppLanguage)
}

package com.lhzkml.jasmine.core.data.repository

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * [AppLanguageRepository] 的平台实现。
 *
 * 读的是应用当前生效的 locales（空列表 = 跟随系统）；写走 `setApplicationLocales`，它自己负责
 * 持久化，并在生效的语言变了时重建 Activity。
 */
class AppLanguageRepositoryImpl : AppLanguageRepository {

    override fun current(): AppLanguage {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) {
            return AppLanguage.SYSTEM
        }
        return when (locales.get(0)?.language) {
            "zh" -> AppLanguage.CHINESE
            else -> AppLanguage.ENGLISH
        }
    }

    override fun apply(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(
            when (language) {
                AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
                AppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags("en")
                AppLanguage.CHINESE -> LocaleListCompat.forLanguageTags("zh-CN")
            }
        )
    }
}

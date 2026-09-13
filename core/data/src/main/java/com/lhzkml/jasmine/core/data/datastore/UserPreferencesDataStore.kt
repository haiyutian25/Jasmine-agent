package com.lhzkml.jasmine.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lhzkml.jasmine.core.data.model.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPreferencesStore: DataStore<Preferences> by preferencesDataStore(
    name = PREFS_FILE_NAME
)

/**
 * Preferences DataStore backing persisted UI preferences (key-value).
 *
 * This replaces the former single-row Room `user_preferences` table: DataStore
 * is the recommended store for simple preferences, while Room is reserved for
 * structured local data. Reads expose a [Flow]; writes are atomic via [update].
 */
@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store: DataStore<Preferences> = context.userPreferencesStore

    /** Preferences stream; missing keys fall back to [UserPreferences.DEFAULT]. */
    val preferences: Flow<UserPreferences> = store.data.map { it.toUserPreferences() }

    /** Atomically updates preferences; [transform] receives the current value. */
    suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        store.edit { prefs ->
            val updated = transform(prefs.toUserPreferences())
            prefs[KEY_THEME_ID] = updated.themeId
            prefs[KEY_TYPOGRAPHY] = updated.typographyChoice
            prefs[KEY_COLOR_MODE] = updated.colorMode
            prefs[KEY_FONT_SCALE] = updated.fontScale
            prefs[KEY_ACTIVE_FONT] = updated.activeCustomFontId
        }
    }

    private fun Preferences.toUserPreferences(): UserPreferences = UserPreferences(
        themeId = this[KEY_THEME_ID] ?: UserPreferences.DEFAULT.themeId,
        typographyChoice = this[KEY_TYPOGRAPHY] ?: UserPreferences.DEFAULT.typographyChoice,
        colorMode = this[KEY_COLOR_MODE] ?: UserPreferences.DEFAULT.colorMode,
        fontScale = this[KEY_FONT_SCALE] ?: UserPreferences.DEFAULT.fontScale,
        activeCustomFontId = this[KEY_ACTIVE_FONT] ?: UserPreferences.DEFAULT.activeCustomFontId,
    )

    private companion object {
        val KEY_THEME_ID = stringPreferencesKey("themeId")
        val KEY_TYPOGRAPHY = stringPreferencesKey("typographyChoice")
        val KEY_COLOR_MODE = stringPreferencesKey("colorMode")
        val KEY_FONT_SCALE = floatPreferencesKey("fontScale")
        val KEY_ACTIVE_FONT = stringPreferencesKey("activeCustomFontId")
    }
}

internal const val PREFS_FILE_NAME = "user_preferences"

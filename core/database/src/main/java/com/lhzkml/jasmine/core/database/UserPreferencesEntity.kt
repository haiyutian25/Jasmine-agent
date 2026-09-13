package com.lhzkml.jasmine.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Legacy single-row preferences entity.
 *
 * Active preferences now live in Preferences DataStore (see `core:data`). This
 * entity is retained only so Room declares at least one entity while the
 * database is reserved for future structured local data; drop it (with a
 * migration) once real structured data is introduced.
 */
@Entity(tableName = "user_preferences")
data class UserPreferencesEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val themeId: String,
    val typographyChoice: String,
    val colorMode: String,
    val fontScale: Float,
    val activeCustomFontId: String,
) {
    companion object {
        const val SINGLE_ROW_ID = 0
    }
}

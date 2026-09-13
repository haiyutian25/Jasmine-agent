package com.lhzkml.jasmine.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database reserved for structured local data.
 *
 * Active preferences moved to Preferences DataStore (see `core:data`), so the
 * legacy [UserPreferencesEntity] is kept only to satisfy Room's requirement of
 * at least one entity; no DAO is exposed for it. Add an `@Entity` + `@Dao` and
 * bump [version] when introducing real structured local data.
 */
@Database(
    entities = [UserPreferencesEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        const val NAME = "jasmine.db"
    }
}

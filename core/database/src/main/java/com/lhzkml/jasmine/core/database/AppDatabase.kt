package com.lhzkml.jasmine.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database holding the chat transcript.
 *
 * The legacy single-row `user_preferences` table (and its entity) was removed in
 * v5: UI preferences live in Preferences DataStore, so once real entities arrived
 * there was no reason to keep a table nothing could read.
 *
 * Schemas are exported to `core/database/schemas` so migrations can be verified
 * against Room's own generated DDL instead of hand-written guesses.
 */
@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun chatHistoryDao(): ChatHistoryDao

    companion object {
        const val NAME = "jasmine.db"
    }
}

package com.lhzkml.jasmine.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database holding the chat transcript.
 */
@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun chatHistoryDao(): ChatHistoryDao

    companion object {
        const val NAME = "jasmine.db"
    }
}
package com.lhzkml.jasmine.core.database

/**
 * The DDL that v5 adds, as a single source of truth.
 *
 * Copied verbatim from Room's exported schema
 * (`core/database/schemas/com.lhzkml.jasmine.core.database.AppDatabase/5.json`)
 * rather than written by hand: Room validates the schema after migrating, so a
 * single character difference in a column definition would only surface as a
 * crash on open. `MigrationDdlTest` asserts these statements stay identical to
 * the exported schema, which makes that check part of the build.
 */
internal object ChatTranscriptSchema {

    val STATEMENTS: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `conversations` (" +
            "`id` TEXT NOT NULL, `title` TEXT NOT NULL, `providerId` TEXT NOT NULL, " +
            "`modelId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `messages` (" +
            "`seq` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`conversationId` TEXT NOT NULL, `role` TEXT NOT NULL, `text` TEXT NOT NULL, " +
            "`isError` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
            "FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_messages_conversationId` " +
            "ON `messages` (`conversationId`)",
    )

    /** Preferences moved to DataStore long ago; nothing can read this table. */
    const val DROP_LEGACY_PREFERENCES = "DROP TABLE IF EXISTS `user_preferences`"
}

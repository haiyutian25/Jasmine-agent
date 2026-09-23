package com.lhzkml.jasmine.core.database

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the hand-written v4 -> v5 migration DDL to Room's exported schema.
 *
 * Room validates the schema after migrating and throws if it differs, which
 * would mean a crash on open for every existing install — but only at runtime.
 * This test moves that check into the build: change an entity, regenerate the
 * schema, and the mismatch fails here instead of on a device.
 *
 * It deliberately needs no Android runtime: it compares SQL text.
 */
class MigrationDdlTest {

    @Test
    fun `migration DDL matches the exported schema`() {
        val expected = exportedCreateStatements()
        assertTrue("exported schema not found; run a build first", expected.isNotEmpty())

        val actual = ChatTranscriptSchema.STATEMENTS.map(::normalize)
        val tables = listOf("conversations", "messages")

        expected.forEach { statement ->
            val matched = tables.any { table ->
                normalize(statement.replace("\${TABLE_NAME}", table)) in actual
            }
            assertTrue(
                "migration DDL is out of sync with the exported schema:\n$statement\n" +
                    "actual statements:\n${ChatTranscriptSchema.STATEMENTS.joinToString("\n")}",
                matched,
            )
        }
    }

    @Test
    fun `every migration statement is covered by the exported schema`() {
        val expected = exportedCreateStatements()
        val tables = listOf("conversations", "messages")
        val expectedNormalized = expected.flatMap { statement ->
            tables.map { table -> normalize(statement.replace("\${TABLE_NAME}", table)) }
        }

        // Guards the other direction too: a statement added to the migration that
        // Room does not know about would be dead weight at best.
        ChatTranscriptSchema.STATEMENTS.forEach { statement ->
            assertTrue(
                "migration statement is not part of the exported schema: $statement",
                normalize(statement) in expectedNormalized,
            )
        }
    }

    /** Reads every `createSql` value out of Room's exported schema for version 5. */
    private fun exportedCreateStatements(): List<String> {
        val schema = SCHEMA_CANDIDATES.map(::File).firstOrNull(File::isFile) ?: return emptyList()
        return CREATE_SQL.findAll(schema.readText()).map { it.groupValues[1] }.toList()
    }

    /** Whitespace is irrelevant to SQLite; comparing without it keeps this readable. */
    private fun normalize(sql: String): String = sql.filterNot(Char::isWhitespace)

    private companion object {
        const val SCHEMA_PATH =
            "schemas/com.lhzkml.jasmine.core.database.AppDatabase/5.json"

        /**
         * Gradle runs unit tests with the module directory as the working
         * directory, but resolve defensively so the test also works from an IDE.
         */
        val SCHEMA_CANDIDATES = listOf(
            SCHEMA_PATH,
            "core/database/$SCHEMA_PATH",
            "../core/database/$SCHEMA_PATH",
        )

        val CREATE_SQL = Regex("\"createSql\"\\s*:\\s*\"([^\"]+)\"")
    }
}

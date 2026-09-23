package com.lhzkml.jasmine.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lhzkml.jasmine.core.database.AppDatabase
import com.lhzkml.jasmine.core.database.ChatTranscriptSchema
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * v1 -> v2: add the color-mode column (defaults to following the system).
     *
     * The three legacy migrations below only matter for pre-release installs:
     * they build up the `user_preferences` table that v5 then drops. They are
     * kept so such an install can still open the database instead of hitting
     * Room's "no migration path" error.
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE user_preferences ADD COLUMN colorMode TEXT NOT NULL DEFAULT 'system'")
        }
    }

    /** v2 -> v3: add the font-scale column (defaults to no scaling). */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE user_preferences ADD COLUMN fontScale REAL NOT NULL DEFAULT 1.0")
        }
    }

    /** v3 -> v4: add the active custom-font column (defaults to none/system engine). */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE user_preferences ADD COLUMN activeCustomFontId TEXT NOT NULL DEFAULT ''")
        }
    }

    /**
     * v4 -> v5: the transcript arrives; the legacy preferences table goes away.
     *
     * The statements live in [ChatTranscriptSchema], where a test pins them to
     * Room's exported schema.
     */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            ChatTranscriptSchema.STATEMENTS.forEach(db::execSQL)
            db.execSQL(ChatTranscriptSchema.DROP_LEGACY_PREFERENCES)
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
}

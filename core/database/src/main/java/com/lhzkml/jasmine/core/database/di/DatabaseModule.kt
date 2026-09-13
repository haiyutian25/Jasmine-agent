package com.lhzkml.jasmine.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lhzkml.jasmine.core.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** v1 -> v2: add the color-mode column (defaults to following the system). */
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
     * Room is reserved for future structured local data. Active preferences
     * live in DataStore, so no DAO is provided for the legacy preferences
     * entity; it exists only to keep the database valid.
     */
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
}

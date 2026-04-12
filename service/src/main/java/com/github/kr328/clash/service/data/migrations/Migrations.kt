package com.github.kr328.clash.service.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE imported ADD COLUMN home TEXT")
        database.execSQL("ALTER TABLE imported ADD COLUMN crisp TEXT")
        database.execSQL("ALTER TABLE pending ADD COLUMN home TEXT")
        database.execSQL("ALTER TABLE pending ADD COLUMN crisp TEXT")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE imported ADD COLUMN coreMode TEXT NOT NULL DEFAULT 'Meta'")
        database.execSQL("ALTER TABLE pending ADD COLUMN coreMode TEXT NOT NULL DEFAULT 'Meta'")
    }
}

val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

val LEGACY_MIGRATION = ::migrationFromLegacy

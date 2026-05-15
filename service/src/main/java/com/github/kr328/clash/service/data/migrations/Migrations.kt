package com.github.kr328.clash.service.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE imported ADD COLUMN profileOrder INTEGER NOT NULL DEFAULT 0")
        database.execSQL("UPDATE imported SET profileOrder = createdAt")
        database.execSQL("ALTER TABLE pending ADD COLUMN profileOrder INTEGER NOT NULL DEFAULT 0")
        database.execSQL("UPDATE pending SET profileOrder = createdAt")
    }
}

val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

val LEGACY_MIGRATION = ::migrationFromLegacy

package com.example.gallery.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration15To16 : Migration(15, 16) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // media_metadata テーブルに hasThumbnail 列を追加
        database.execSQL(
            "ALTER TABLE media_metadata ADD COLUMN hasThumbnail INTEGER NOT NULL DEFAULT 0"
        )
    }
}

package com.example.gallery.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration16To17 : Migration(16, 17) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE media_metadata ADD COLUMN startupThumbnailAttempted INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL(
            "ALTER TABLE media_metadata ADD COLUMN startupVectorAttempted INTEGER NOT NULL DEFAULT 0"
        )
    }
}

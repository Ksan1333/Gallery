package com.example.gallery.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration18To19 : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE media_metadata ADD COLUMN aiAnalysisModel TEXT NOT NULL DEFAULT ''"
        )
        db.execSQL(
            "UPDATE media_metadata SET aiAnalysisModel = 'NORMAL' WHERE isAiAnalyzed = 1 AND aiAnalysisModel = ''"
        )
    }
}

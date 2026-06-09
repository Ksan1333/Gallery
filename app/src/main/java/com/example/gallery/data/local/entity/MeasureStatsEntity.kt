package com.example.gallery.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "measure_stats")
data class MeasureStatsEntity(
    @PrimaryKey
    val uri: String,
    val viewCount: Int = 0,
    val totalDurationSeconds: Long = 0,
    val lastViewedTimestamp: Long = System.currentTimeMillis()
)

package com.example.gallery.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_downloads")
data class VideoDownloadEntity(
    @PrimaryKey val url: String,
    val title: String,
    val savePath: String,
    val downloadDate: Long,
    val status: String = "COMPLETED" // COMPLETED, DOWNLOADING, FAILED
)

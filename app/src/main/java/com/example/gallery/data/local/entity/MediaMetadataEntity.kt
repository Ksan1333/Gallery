package com.example.gallery.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_metadata")
data class MediaMetadataEntity(
    @PrimaryKey val uri: String,
    val isFavorite: Boolean = false,
    val colorComposition: String? = null, // JSON string of Map<String, Float>
    val ageRating: String = "SFW", // "SFW", "R15", "R18"
    val isAiAnalyzed: Boolean = false,
    val folderName: String = "",
    val isDeleted: Boolean = false,
    val deletedDate: Long? = null
)

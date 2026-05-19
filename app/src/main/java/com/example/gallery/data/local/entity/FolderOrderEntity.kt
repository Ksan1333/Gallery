package com.example.gallery.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folder_order")
data class FolderOrderEntity(
    @PrimaryKey val id: String, // folderName or group:name
    val position: Int
)

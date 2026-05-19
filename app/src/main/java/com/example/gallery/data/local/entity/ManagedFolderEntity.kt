package com.example.gallery.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "managed_folders")
data class ManagedFolderEntity(
    @PrimaryKey val folderName: String,
    val dateCreated: Long = System.currentTimeMillis()
)

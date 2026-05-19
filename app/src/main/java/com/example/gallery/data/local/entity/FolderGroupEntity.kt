package com.example.gallery.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folder_groups")
data class FolderGroupEntity(
    @PrimaryKey
    val name: String,
    val dateCreated: Long = System.currentTimeMillis()
)

@Entity(tableName = "folder_group_members", primaryKeys = ["groupName", "folderName"])
data class FolderGroupMemberEntity(
    val groupName: String,
    val folderName: String
)

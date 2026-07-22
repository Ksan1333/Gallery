package com.example.gallery.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "media_tags",
    primaryKeys = ["uri", "tag"],
    indices = [Index(value = ["tag"])]
)
data class TagEntity(
    val uri: String,
    val tag: String,
    val confidence: Float = 0f // AI分析時のスコア
)

data class TagCount(
    val tag: String,
    val count: Int
)

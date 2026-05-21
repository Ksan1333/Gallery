package com.example.gallery.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tag_translations")
data class TagTranslationEntity(
    @PrimaryKey
    val original: String,
    val translated: String
)

package com.example.gallery.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "reference_projects")
data class ReferenceProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val status: String = "ACTIVE", // ACTIVE, FINISHED
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "reference_items",
    foreignKeys = [
        ForeignKey(
            entity = ReferenceProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class ReferenceItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val localUri: String? = null,
    val remoteUrl: String,
    val title: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

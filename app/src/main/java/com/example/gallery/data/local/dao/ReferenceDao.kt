package com.example.gallery.data.local.dao

import androidx.room.*
import com.example.gallery.data.local.entity.ReferenceProjectEntity
import com.example.gallery.data.local.entity.ReferenceItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReferenceDao {
    @Query("SELECT * FROM reference_projects ORDER BY createdAt DESC")
    fun getAllProjectsFlow(): Flow<List<ReferenceProjectEntity>>

    @Insert
    suspend fun insertProject(project: ReferenceProjectEntity): Long

    @Update
    suspend fun updateProject(project: ReferenceProjectEntity)

    @Delete
    suspend fun deleteProject(project: ReferenceProjectEntity)

    @Query("SELECT * FROM reference_items WHERE projectId = :projectId ORDER BY addedAt DESC")
    fun getItemsForProjectFlow(projectId: Long): Flow<List<ReferenceItemEntity>>

    @Query("SELECT * FROM reference_items WHERE projectId = :projectId")
    suspend fun getItemsForProject(projectId: Long): List<ReferenceItemEntity>

    @Insert
    suspend fun insertItem(item: ReferenceItemEntity): Long

    @Update
    suspend fun updateItem(item: ReferenceItemEntity)

    @Delete
    suspend fun deleteItem(item: ReferenceItemEntity)

    @Query("UPDATE reference_items SET localUri = NULL WHERE projectId = :projectId")
    suspend fun clearLocalUrisForProject(projectId: Long)
}

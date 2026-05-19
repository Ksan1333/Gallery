package com.example.gallery.data.local.dao

import androidx.room.*
import com.example.gallery.data.local.entity.MediaMetadataEntity
import com.example.gallery.data.local.entity.TagCount
import com.example.gallery.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_metadata WHERE uri = :uri")
    suspend fun getMetadata(uri: String): MediaMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: MediaMetadataEntity)

    @Query("SELECT * FROM media_tags WHERE uri = :uri")
    fun getTagsForMedia(uri: String): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity)

    @Query("DELETE FROM media_tags WHERE tag IN ('R15', 'R18', 'SFW')")
    suspend fun cleanupAgeRatingTags()

    @Delete
    suspend fun deleteTag(tag: TagEntity)

    @Query("SELECT tag FROM media_tags GROUP BY tag")
    fun getAllTags(): Flow<List<String>>

    @Query("SELECT tag, COUNT(*) as count FROM media_tags GROUP BY tag")
    fun getAllTagsWithCounts(): Flow<List<TagCount>>

    @Query("SELECT * FROM media_tags")
    fun getAllTagsWithUris(): Flow<List<TagEntity>>

    @Query("SELECT * FROM media_tags WHERE tag = :tag")
    fun getMediaForTag(tag: String): Flow<List<TagEntity>>

    @Query("SELECT * FROM media_tags WHERE tag LIKE '%系'")
    fun getAllColorTags(): Flow<List<TagEntity>>

    @Query("SELECT COUNT(*) FROM media_tags WHERE tag = :tag")
    fun getCountForTag(tag: String): Flow<Int>

    @Query("SELECT uri FROM media_tags WHERE tag = :tag LIMIT 1")
    fun getThumbnailForTag(tag: String): Flow<String?>
    
    @Query("SELECT uri FROM media_metadata WHERE isFavorite = 1")
    fun getFavoriteUris(): Flow<List<String>>

    @Query("SELECT DISTINCT uri FROM media_tags WHERE tag NOT LIKE '%系'")
    fun getManualTaggedUrisFlow(): Flow<List<String>>

    @Query("SELECT DISTINCT uri FROM media_tags")
    fun getAllTaggedUrisFlow(): Flow<List<String>>

    @Query("SELECT DISTINCT uri FROM media_tags")
    suspend fun getAllTaggedUris(): List<String>

    @Query("SELECT * FROM media_metadata")
    suspend fun getAllMetadata(): List<MediaMetadataEntity>

    @Query("SELECT * FROM media_metadata")
    fun getAllMetadataFlow(): Flow<List<MediaMetadataEntity>>

    @Query("SELECT * FROM media_metadata WHERE isDeleted = 1 ORDER BY deletedDate DESC")
    fun getDeletedMetadataFlow(): Flow<List<MediaMetadataEntity>>

    @Query("UPDATE media_metadata SET isDeleted = :isDeleted, deletedDate = :deletedDate WHERE uri = :uri")
    suspend fun setDeleted(uri: String, isDeleted: Boolean, deletedDate: Long?)

    @Query("UPDATE media_metadata SET isDeleted = :isDeleted, deletedDate = :deletedDate WHERE uri IN (:uris)")
    suspend fun bulkSetDeleted(uris: List<String>, isDeleted: Boolean, deletedDate: Long?)

    // フォルダグループ用
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertFolderGroup(group: com.example.gallery.data.local.entity.FolderGroupEntity)

    @Query("SELECT * FROM folder_groups")
    fun getAllFolderGroups(): kotlinx.coroutines.flow.Flow<List<com.example.gallery.data.local.entity.FolderGroupEntity>>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertFolderGroupMember(member: com.example.gallery.data.local.entity.FolderGroupMemberEntity)

    @Query("SELECT * FROM folder_group_members")
    fun getAllFolderGroupMembers(): kotlinx.coroutines.flow.Flow<List<com.example.gallery.data.local.entity.FolderGroupMemberEntity>>

    @Query("DELETE FROM folder_group_members WHERE folderName = :folderName")
    suspend fun removeFolderFromAllGroups(folderName: String)

    @Query("DELETE FROM folder_groups WHERE name = :groupName")
    suspend fun deleteFolderGroup(groupName: String)

    @Query("DELETE FROM folder_group_members WHERE groupName = :groupName")
    suspend fun deleteFolderGroupMembers(groupName: String)

    // フォルダ順序用
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolderOrder(order: com.example.gallery.data.local.entity.FolderOrderEntity)

    @Query("SELECT * FROM folder_order ORDER BY position ASC")
    fun getAllFolderOrders(): kotlinx.coroutines.flow.Flow<List<com.example.gallery.data.local.entity.FolderOrderEntity>>

    @Query("DELETE FROM folder_order WHERE id = :id")
    suspend fun deleteFolderOrder(id: String)

    // 管理フォルダ用
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertManagedFolder(folder: com.example.gallery.data.local.entity.ManagedFolderEntity)

    @Query("SELECT folderName FROM managed_folders")
    fun getAllManagedFolderNames(): kotlinx.coroutines.flow.Flow<List<String>>
}

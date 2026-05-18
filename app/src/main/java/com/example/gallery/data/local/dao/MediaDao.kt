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
}

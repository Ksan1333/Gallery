package com.example.gallery.data.local.dao

import androidx.room.*
import com.example.gallery.data.local.entity.MediaMetadataEntity
import com.example.gallery.data.local.entity.MediaMetadataSummary
import com.example.gallery.data.local.entity.MediaVector
import com.example.gallery.data.local.entity.TagCount
import com.example.gallery.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_metadata WHERE uri = :uri")
    suspend fun getMetadata(uri: String): MediaMetadataEntity?

    @Query("SELECT * FROM media_metadata WHERE uri = :uri")
    fun getMetadataFlow(uri: String): Flow<MediaMetadataEntity?>

    @Query("SELECT uri, dateAdded, mimeType, duration, width, height, fileSize, fileName, isFavorite, ageRating, isAiAnalyzed, folderName, isDeleted, deletedDate, (featureVector IS NOT NULL) as hasFeatureVector, hasThumbnail, startupThumbnailAttempted, startupVectorAttempted FROM media_metadata WHERE uri = :uri")
    fun getMetadataSummaryFlow(uri: String): Flow<MediaMetadataSummary?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: MediaMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun bulkInsertMetadata(metadataList: List<MediaMetadataEntity>)

    @Query("DELETE FROM media_metadata WHERE uri = :uri")
    suspend fun deleteMetadata(uri: String)

    @Query("DELETE FROM media_metadata WHERE uri IN (:uris)")
    suspend fun bulkDeleteMetadata(uris: List<String>)

    @Query("DELETE FROM media_tags WHERE uri IN (:uris)")
    suspend fun bulkDeleteTags(uris: List<String>)

    @Query("SELECT * FROM media_tags WHERE uri = :uri")
    fun getTagsForMedia(uri: String): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<TagEntity>)

    @Query("DELETE FROM media_tags WHERE uri = :uri")
    suspend fun deleteTagsForMedia(uri: String)

    @Transaction
    suspend fun saveAiAnalysisResult(
        uri: String,
        ageRating: String,
        isAiAnalyzed: Boolean,
        folderName: String,
        tags: List<TagEntity>
    ) {
        val current = getMetadata(uri)
        if (current == null) {
            insertMetadata(
                MediaMetadataEntity(
                    uri = uri,
                    ageRating = ageRating,
                    isAiAnalyzed = isAiAnalyzed,
                    folderName = folderName
                )
            )
        } else {
            updateAiAnalysisResult(uri, ageRating, isAiAnalyzed)
        }
        if (tags.isNotEmpty()) {
            insertTags(tags)
        }
    }

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

    @Query("SELECT COUNT(*) FROM media_tags WHERE tag = :tag")
    fun getCountForTag(tag: String): Flow<Int>

    @Query("SELECT uri FROM media_tags WHERE tag = :tag LIMIT 1")
    fun getThumbnailForTag(tag: String): Flow<String?>

    @Query("SELECT uri FROM media_metadata WHERE isFavorite = 1")
    fun getFavoriteUris(): Flow<List<String>>

    @Query("SELECT DISTINCT uri FROM media_tags")
    fun getManualTaggedUrisFlow(): Flow<List<String>>

    @Query("SELECT DISTINCT uri FROM media_tags")
    fun getAllTaggedUrisFlow(): Flow<List<String>>

    @Query("SELECT DISTINCT uri FROM media_tags")
    suspend fun getAllTaggedUris(): List<String>

    @Query("SELECT * FROM media_metadata")
    suspend fun getAllMetadata(): List<MediaMetadataEntity>

    @Query("SELECT * FROM media_metadata")
    fun getAllMetadataFlow(): Flow<List<MediaMetadataEntity>>

    @Query("SELECT uri, dateAdded, mimeType, duration, width, height, fileSize, fileName, isFavorite, ageRating, isAiAnalyzed, folderName, isDeleted, deletedDate, (featureVector IS NOT NULL) as hasFeatureVector, hasThumbnail, startupThumbnailAttempted, startupVectorAttempted FROM media_metadata ORDER BY dateAdded DESC")
    suspend fun getAllMetadataSummary(): List<MediaMetadataSummary>

    @Query("SELECT uri, dateAdded, mimeType, duration, width, height, fileSize, fileName, isFavorite, ageRating, isAiAnalyzed, folderName, isDeleted, deletedDate, (featureVector IS NOT NULL) as hasFeatureVector, hasThumbnail, startupThumbnailAttempted, startupVectorAttempted FROM media_metadata ORDER BY dateAdded DESC")
    fun getAllMetadataSummaryFlow(): Flow<List<MediaMetadataSummary>>

    @Query("SELECT * FROM media_metadata WHERE isDeleted = 1 ORDER BY deletedDate DESC")
    fun getDeletedMetadataFlow(): Flow<List<MediaMetadataEntity>>

    @Query("SELECT uri, dateAdded, mimeType, duration, width, height, fileSize, fileName, isFavorite, ageRating, isAiAnalyzed, folderName, isDeleted, deletedDate, (featureVector IS NOT NULL) as hasFeatureVector, hasThumbnail, startupThumbnailAttempted, startupVectorAttempted FROM media_metadata WHERE isDeleted = 1 ORDER BY deletedDate DESC")
    fun getDeletedMetadataSummaryFlow(): Flow<List<MediaMetadataSummary>>

    @Query("SELECT * FROM media_metadata WHERE ageRating = :ageRating AND featureVector IS NOT NULL")
    suspend fun getMetadataWithFeatureVectorByRating(ageRating: String): List<MediaMetadataEntity>

    @Query("SELECT uri, featureVector FROM media_metadata WHERE ageRating = :ageRating AND featureVector IS NOT NULL")
    suspend fun getVectorsByRating(ageRating: String): List<MediaVector>

    @Query("SELECT uri, featureVector FROM media_metadata WHERE featureVector IS NOT NULL")
    suspend fun getAllVectors(): List<MediaVector>

    @Query("UPDATE media_metadata SET isDeleted = :isDeleted, deletedDate = :deletedDate WHERE uri = :uri")
    suspend fun setDeleted(uri: String, isDeleted: Boolean, deletedDate: Long?)

    @Query("UPDATE media_metadata SET isDeleted = :isDeleted, deletedDate = :deletedDate WHERE uri IN (:uris)")
    suspend fun bulkSetDeleted(uris: List<String>, isDeleted: Boolean, deletedDate: Long?)

    @Query("UPDATE media_metadata SET isFavorite = :isFavorite WHERE uri = :uri")
    suspend fun updateFavorite(uri: String, isFavorite: Boolean)

    @Query("UPDATE media_metadata SET isFavorite = :isFavorite WHERE uri IN (:uris)")
    suspend fun bulkUpdateFavorite(uris: List<String>, isFavorite: Boolean)

    @Query("UPDATE media_metadata SET ageRating = :ageRating WHERE uri IN (:uris)")
    suspend fun bulkUpdateAgeRating(uris: List<String>, ageRating: String)

    @Query("UPDATE media_metadata SET folderName = :folderName WHERE uri IN (:uris)")
    suspend fun bulkUpdateFolderName(uris: List<String>, folderName: String)

    @Query("UPDATE media_metadata SET ageRating = :ageRating, isAiAnalyzed = :isAiAnalyzed WHERE uri = :uri")
    suspend fun updateAiAnalysisResult(uri: String, ageRating: String, isAiAnalyzed: Boolean)

    @Query("UPDATE media_metadata SET featureVector = :featureVector WHERE uri = :uri")
    suspend fun updateFeatureVector(uri: String, featureVector: ByteArray?)

    @Query("UPDATE media_metadata SET hasThumbnail = :hasThumbnail WHERE uri = :uri")
    suspend fun updateHasThumbnail(uri: String, hasThumbnail: Boolean)

    @Query("UPDATE media_metadata SET hasThumbnail = :hasThumbnail WHERE uri IN (:uris)")
    suspend fun bulkUpdateHasThumbnail(uris: List<String>, hasThumbnail: Boolean)

    @Query("UPDATE media_metadata SET startupThumbnailAttempted = :attempted WHERE uri = :uri")
    suspend fun updateStartupThumbnailAttempted(uri: String, attempted: Boolean)

    @Query("UPDATE media_metadata SET startupVectorAttempted = :attempted WHERE uri = :uri")
    suspend fun updateStartupVectorAttempted(uri: String, attempted: Boolean)

    // 計測データ用
    @Query("SELECT * FROM measure_stats WHERE uri = :uri")
    suspend fun getMeasureStats(uri: String): com.example.gallery.data.local.entity.MeasureStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeasureStats(stats: com.example.gallery.data.local.entity.MeasureStatsEntity)

    @Query("SELECT * FROM measure_stats ORDER BY totalDurationSeconds DESC, viewCount DESC LIMIT :limit")
    fun getTopMeasureStats(limit: Int): Flow<List<com.example.gallery.data.local.entity.MeasureStatsEntity>>

    @Query("SELECT * FROM measure_stats")
    fun getAllMeasureStatsFlow(): Flow<List<com.example.gallery.data.local.entity.MeasureStatsEntity>>

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

    @Query("SELECT * FROM managed_folders")
    fun getAllManagedFolders(): kotlinx.coroutines.flow.Flow<List<com.example.gallery.data.local.entity.ManagedFolderEntity>>

    @Query("UPDATE managed_folders SET customThumbnailUri = :uri WHERE folderName = :folderName")
    suspend fun updateFolderThumbnail(folderName: String, uri: String?)

    @Query("SELECT folderName FROM managed_folders")
    fun getAllManagedFolderNames(): kotlinx.coroutines.flow.Flow<List<String>>

    // タグ翻訳用
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTagTranslation(translation: com.example.gallery.data.local.entity.TagTranslationEntity)

    @Query("SELECT translated FROM tag_translations WHERE original = :original")
    suspend fun getTagTranslation(original: String): String?

    // 動画ダウンロード履歴用
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideoDownload(download: com.example.gallery.data.local.entity.VideoDownloadEntity)

    @Query("SELECT * FROM video_downloads ORDER BY downloadDate DESC")
    fun getAllVideoDownloads(): kotlinx.coroutines.flow.Flow<List<com.example.gallery.data.local.entity.VideoDownloadEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM video_downloads WHERE url = :url)")
    suspend fun isVideoDownloaded(url: String): Boolean

    @Query("DELETE FROM video_downloads")
    suspend fun clearVideoDownloadHistory()

    // Paging Support with Filtering
    @Query("""
        SELECT uri, dateAdded, mimeType, duration, width, height, fileSize, fileName, isFavorite, ageRating, isAiAnalyzed, folderName, isDeleted, deletedDate, (featureVector IS NOT NULL) as hasFeatureVector, hasThumbnail, startupThumbnailAttempted, startupVectorAttempted 
        FROM media_metadata 
        WHERE isDeleted = 0 
        AND (:mediaType = 'ALL' 
            OR (:mediaType = 'IMAGE' AND mimeType NOT LIKE 'video/%' AND mimeType NOT LIKE 'image/gif' AND uri NOT LIKE '%.gif') 
            OR (:mediaType = 'VIDEO' AND mimeType LIKE 'video/%') 
            OR (:mediaType = 'GIF' AND (mimeType LIKE 'image/gif' OR uri LIKE '%.gif')))
        AND (:ageRating = 'ALL' OR ageRating = :ageRating)
        AND (:deviceFilter = 'ALL' 
            OR (:deviceFilter = 'SMARTPHONE' AND height > width AND (height * 100 / width) BETWEEN (:aspectRatio100 - 20) AND (:aspectRatio100 + 20))
            OR (:deviceFilter = 'PC' AND width > height AND (width * 100 / height) > 130))
        ORDER BY 
            CASE WHEN :sortMode = 'DATE_ADDED' AND :isAscending = 1 THEN dateAdded END ASC,
            CASE WHEN :sortMode = 'DATE_ADDED' AND :isAscending = 0 THEN dateAdded END DESC,
            CASE WHEN :sortMode = 'SIZE' AND :isAscending = 1 THEN fileSize END ASC,
            CASE WHEN :sortMode = 'SIZE' AND :isAscending = 0 THEN fileSize END DESC,
            CASE WHEN :sortMode = 'NAME' AND :isAscending = 1 THEN fileName END ASC,
            CASE WHEN :sortMode = 'NAME' AND :isAscending = 0 THEN fileName END DESC
    """)
    fun getFilteredMediaPagingSource(
        mediaType: String, 
        ageRating: String, 
        deviceFilter: String,
        aspectRatio100: Int,
        sortMode: String,
        isAscending: Boolean
    ): androidx.paging.PagingSource<Int, MediaMetadataSummary>
}

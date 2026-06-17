package com.example.gallery.data.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.File
import com.example.gallery.data.local.dao.MediaDao
import com.example.gallery.data.local.entity.MediaMetadataEntity
import com.example.gallery.data.local.entity.MediaMetadataSummary
import com.example.gallery.data.local.entity.TagEntity
import com.example.gallery.data.model.MediaData
import com.example.gallery.ui.component.GridItem
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.paging.insertSeparators
import com.example.gallery.ui.state.*
import com.example.gallery.service.GlobalOperationService
import com.example.gallery.data.service.VectorSearchService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.*

class MediaRepository(
    private val context: Context,
    val mediaDao: MediaDao,
    val galleryState: GalleryState? = null
) {
    private var cachedMediaList: List<MediaData>? = null
    private var lastCacheTime: Long = 0
    private val cacheMutex = Mutex()

    fun getGridItemPagingFlow(
        mediaType: MediaTypeFilter,
        ageRating: AgeRatingFilter,
        deviceFilter: DeviceFilter,
        deviceAspectRatio: Float,
        sortMode: SortMode,
        isAscending: Boolean,
        groupingMode: GroupingMode
    ): Flow<PagingData<GridItem>> {
        val aspectRatio100 = (deviceAspectRatio * 100).toInt()
        
        return Pager(
            config = PagingConfig(
                pageSize = 100, 
                prefetchDistance = 200, 
                enablePlaceholders = true // スクロールバーのサイズを正しくするために有効化
            ),
            pagingSourceFactory = { 
                mediaDao.getFilteredMediaPagingSource(
                    mediaType.name,
                    ageRating.name,
                    deviceFilter.name,
                    aspectRatio100,
                    sortMode.name,
                    isAscending
                )
            }
        ).flow.map { pagingData ->
            val mediaPaging = pagingData.map { summary ->
                val label = when {
                    summary.mimeType?.contains("gif") == true || summary.uri.contains("gif", ignoreCase = true) -> "GIF"
                    summary.mimeType?.startsWith("video") == true -> formatDuration(summary.duration)
                    else -> null
                }
                GridItem.Media(
                    data = MediaData(
                        uri = summary.uri,
                        dateAdded = summary.dateAdded,
                        mimeType = summary.mimeType,
                        duration = summary.duration,
                        width = summary.width,
                        height = summary.height,
                        fileSize = summary.fileSize,
                        fileName = summary.fileName,
                        folderName = summary.folderName
                    ),
                    label = label,
                    index = -1 
                )
            }

            // セパレータ（日付ヘッダー）を入れるとプレースホルダーと競合するため、
            // ユーザーが「全部ロードされない」と感じる場合は一時的に無効化するか、
            // プレースホルダー表示側を工夫する必要があります。
            // ここではプレースホルダーを有効にするため、GroupingMode.NONE以外でも
            // セパレータを一旦外して全件ロードを確認しやすくします。
            
            if (groupingMode == GroupingMode.NONE) {
                mediaPaging.map { it as GridItem }
            } else {
                val sdf = when (groupingMode) {
                    GroupingMode.DAY -> SimpleDateFormat("yyyy年M月d日", Locale.JAPAN)
                    GroupingMode.MONTH -> SimpleDateFormat("yyyy年M月", Locale.JAPAN)
                    GroupingMode.YEAR -> SimpleDateFormat("yyyy年", Locale.JAPAN)
                    else -> null
                }
                
                if (sdf == null) {
                    mediaPaging.map { it as GridItem }
                } else {
                    // Paging 3 の insertSeparators はプレースホルダーと併用すると
                    // スクロールバーの挙動が不安定になる既知の制約があります。
                    mediaPaging.insertSeparators { before: GridItem.Media?, after: GridItem.Media? ->
                        if (after == null) return@insertSeparators null
                        
                        val dateObj = Date()
                        dateObj.time = after.data.dateAdded
                        val afterHeader = sdf.format(dateObj)
                        
                        if (before == null) {
                            return@insertSeparators GridItem.Header(afterHeader)
                        }
                        
                        dateObj.time = before.data.dateAdded
                        val beforeHeader = sdf.format(dateObj)
                        
                        if (beforeHeader != afterHeader) {
                            GridItem.Header(afterHeader)
                        } else {
                            null
                        }
                    }
                }
            }
        }
    }

    private fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return "0:00"
        val s = durationMs / 1000
        val m = (s / 60) % 60
        val h = s / 3600
        val sec = s % 60
        return buildString {
            if (h > 0) { append(h).append(':'); if (m < 10) append('0') }
            append(m).append(':')
            if (sec < 10) append('0')
            append(sec)
        }
    }

    fun getMediaPagingFlow(isAscending: Boolean): Flow<PagingData<MediaData>> {
        val deviceAspectRatio = 1.0f // デフォルト値。必要なら引数で受け取るように変更

        return Pager(
            config = PagingConfig(pageSize = 100, enablePlaceholders = true, prefetchDistance = 200),
            pagingSourceFactory = { 
                mediaDao.getFilteredMediaPagingSource(
                    mediaType = "ALL",
                    ageRating = "ALL",
                    deviceFilter = "ALL",
                    aspectRatio100 = (deviceAspectRatio * 100).toInt(),
                    sortMode = "DATE_ADDED",
                    isAscending = isAscending
                )
            }
        ).flow.map { pagingData ->
            pagingData.map { summary ->
                MediaData(
                    uri = summary.uri,
                    dateAdded = summary.dateAdded,
                    mimeType = summary.mimeType,
                    duration = summary.duration,
                    width = summary.width,
                    height = summary.height,
                    fileSize = summary.fileSize,
                    fileName = summary.fileName,
                    folderName = summary.folderName
                )
            }
        }
    }

    suspend fun syncMediaStoreToRoom() = withContext(Dispatchers.IO) {
        val allGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_VIDEO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (!allGranted) return@withContext

        // タムネイル生成タスクと重複しないよう、明示的に異なるIDを使用
        val opId = GlobalOperationService.startOperation("ライブラリを同期中...", tag = "SYNC_MEDIA_STORE")
        GlobalOperationService.updateProgress(0f, "準備中...", id = opId)
        
        try {
            val existingMetadata = mediaDao.getAllMetadataSummary().associateBy { it.uri }
            val foundUris = HashSet<String>(10000)
            val newEntities = mutableListOf<MediaMetadataEntity>()

            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DURATION,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DISPLAY_NAME
            )

            val volumeNames = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.getExternalVolumeNames(context)
            } else {
                setOf("external")
            }

            // 事前に全体の件数を取得して進捗率を正確にする
            var totalToProcess = 0
            volumeNames.forEach { vn ->
                listOf(MediaStore.Images.Media.getContentUri(vn), MediaStore.Video.Media.getContentUri(vn)).forEach { col ->
                    context.contentResolver.query(col, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { 
                        totalToProcess += it.count 
                    }
                }
            }
            Log.d("MediaRepository", "Sync started: total to process = $totalToProcess")
            val totalF = totalToProcess.toFloat().coerceAtLeast(1f)

            var processedCount = 0
            volumeNames.forEach { volumeName ->
                val collections = listOf(
                    MediaStore.Images.Media.getContentUri(volumeName),
                    MediaStore.Video.Media.getContentUri(volumeName)
                )

                collections.forEach { collection ->
                    try {
                        context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
                            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                            val dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                            val durationColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
                            val widthColumn = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                            val heightColumn = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                            val sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)

                            while (cursor.moveToNext()) {
                                processedCount++
                                if (processedCount % 100 == 0) {
                                    // 読み込み進捗を 0〜85% で表現
                                    GlobalOperationService.updateProgress((processedCount.toFloat() / totalF) * 0.85f, id = opId)
                                }

                                val id = cursor.getLong(idColumn)
                                val contentUri = ContentUris.withAppendedId(collection, id).toString()
                                if (!foundUris.add(contentUri)) continue

                                val existing = existingMetadata[contentUri]
                                
                                val date = cursor.getLong(dateColumn) * 1000
                                val mime = cursor.getString(mimeColumn)
                                val duration = if (durationColumn != -1) cursor.getLong(durationColumn) else 0L
                                val width = if (widthColumn != -1) cursor.getInt(widthColumn) else 0
                                val height = if (heightColumn != -1) cursor.getInt(heightColumn) else 0
                                val size = if (sizeColumn != -1) cursor.getLong(sizeColumn) else 0L
                                val name = if (nameColumn != -1) cursor.getString(nameColumn) ?: "" else ""
                                val path = if (dataColumn != -1) cursor.getString(dataColumn) else null
                                
                                val folderName = if (path != null) {
                                    val lastSeparator = path.lastIndexOf(File.separator)
                                    if (lastSeparator > 0) {
                                        val prevSeparator = path.lastIndexOf(File.separator, lastSeparator - 1)
                                        if (prevSeparator >= 0) path.substring(prevSeparator + 1, lastSeparator)
                                        else path.substring(0, lastSeparator)
                                    } else "Unknown"
                                } else "Unknown"

                                if (existing == null || existing.dateAdded != date || existing.fileSize != size) {
                                    newEntities.add(MediaMetadataEntity(
                                        uri = contentUri,
                                        dateAdded = date,
                                        mimeType = mime,
                                        duration = duration,
                                        width = width,
                                        height = height,
                                        fileSize = size,
                                        fileName = name,
                                        folderName = folderName,
                                        isFavorite = existing?.isFavorite ?: false,
                                        ageRating = existing?.ageRating ?: "SFW",
                                        isAiAnalyzed = existing?.isAiAnalyzed ?: false,
                                        isDeleted = existing?.isDeleted ?: false,
                                        deletedDate = existing?.deletedDate
                                    ))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MediaRepository", "Error querying volume $volumeName: ${e.message}")
                    }
                }
            }

            if (newEntities.isNotEmpty()) {
                GlobalOperationService.updateProgress(0.9f, "データベースを更新中...", id = opId)
                newEntities.chunked(100).forEach { chunk ->
                    mediaDao.bulkInsertMetadata(chunk)
                }
            }

            // 削除されたファイルをクリーンアップ
            val dbUrisToDelete = existingMetadata.keys.filter { it.startsWith("content://media/external/") && !foundUris.contains(it) }
            if (dbUrisToDelete.isNotEmpty()) {
                GlobalOperationService.updateProgress(0.95f, "不要なデータを削除中...", id = opId)
                dbUrisToDelete.chunked(100).forEach { chunk ->
                    mediaDao.bulkDeleteMetadata(chunk)
                    mediaDao.bulkDeleteTags(chunk)
                }
            }
            
            GlobalOperationService.updateProgress(1.0f, "同期完了", id = opId)
            cachedMediaList = null
        } finally {
            GlobalOperationService.finishOperation(opId)
        }
    }

    suspend fun getAllMedia(forceRefresh: Boolean = false): List<MediaData> {
        return getAllMediaFiltered(forceRefresh = forceRefresh, includeDeleted = false)
    }

    fun getTrashMedia(): Flow<List<MediaData>> {
        return mediaDao.getDeletedMetadataSummaryFlow().map { deletedMetadata ->
            val deletedUris = deletedMetadata.map { it.uri }.toSet()
            getAllMediaFiltered(forceRefresh = false, includeDeleted = true)
                .filter { it.uri in deletedUris }
        }
    }

    suspend fun cleanupDeletedFiles() {
        // 重い個別ファイルチェックを伴う cleanupDeletedFiles は廃止し、
        // getAllMediaFiltered 内での一括チェックに統合されました。
    }

    private suspend fun getAllMediaFiltered(forceRefresh: Boolean = false, includeDeleted: Boolean = false): List<MediaData> {
        cacheMutex.withLock {
            val now = System.currentTimeMillis()
            val isVeryRecent = now - lastCacheTime < 2000
            if (!includeDeleted && cachedMediaList != null && (isVeryRecent || (!forceRefresh && now - lastCacheTime < 10000))) {
                return cachedMediaList!!
            }

            if (forceRefresh || cachedMediaList == null) {
                syncMediaStoreToRoom()
            }

            val allMetadata = if (includeDeleted) mediaDao.getDeletedMetadataSummaryFlow().first() 
                             else mediaDao.getAllMetadataSummary().filter { !it.isDeleted }
            
            val mediaList = allMetadata.map { summary ->
                MediaData(
                    uri = summary.uri,
                    dateAdded = summary.dateAdded,
                    mimeType = summary.mimeType,
                    duration = summary.duration,
                    width = summary.width,
                    height = summary.height,
                    fileSize = summary.fileSize,
                    fileName = summary.fileName,
                    folderName = summary.folderName
                )
            }

            if (!includeDeleted) {
                cachedMediaList = mediaList
                lastCacheTime = now
            }
            return mediaList
        }
    }

    suspend fun moveToTrash(uris: List<String>) {
        val opId = GlobalOperationService.startOperation("ゴミ箱へ移動中...")
        mediaDao.bulkSetDeleted(uris, true, System.currentTimeMillis())
        GlobalOperationService.updateProgress(1.0f, id = opId)
        cachedMediaList = null
        galleryState?.refresh()
        GlobalOperationService.finishOperation(opId)
    }

    suspend fun restoreFromTrash(uris: List<String>) {
        val opId = GlobalOperationService.startOperation("元に戻しています...")
        mediaDao.bulkSetDeleted(uris, false, null)
        GlobalOperationService.updateProgress(1.0f, id = opId)
        cachedMediaList = null
        galleryState?.refresh()
        GlobalOperationService.finishOperation(opId)
    }

    suspend fun permanentlyDelete(uris: List<String>) {
        val opId = GlobalOperationService.startOperation("ファイルを完全に削除中...")
        uris.forEachIndexed { index, uriString ->
            try {
                val uri = Uri.parse(uriString)
                context.contentResolver.delete(uri, null, null)
                mediaDao.deleteMetadata(uriString)
                mediaDao.deleteTagsForMedia(uriString)
            } catch (e: Exception) {
                Log.e("MediaRepository", "Failed to delete $uriString", e)
            }
            GlobalOperationService.updateProgress((index + 1).toFloat() / uris.size, id = opId)
        }
        cachedMediaList = null
        galleryState?.refresh()
        GlobalOperationService.finishOperation(opId)
    }

    suspend fun toggleFavorite(uri: String) {
        val current = getMetadata(uri)
        mediaDao.updateFavorite(uri, !(current?.isFavorite ?: false))
    }

    suspend fun bulkUpdateFavorite(uris: List<String>, isFavorite: Boolean) {
        mediaDao.bulkUpdateFavorite(uris, isFavorite)
    }

    suspend fun bulkUpdateAgeRating(uris: List<String>, ageRating: String?) {
        if (ageRating == null) return
        val allMedia = if (uris.size > 1) getAllMedia() else null
        uris.forEach { uri ->
            val current = mediaDao.getMetadata(uri)
            if (current == null) {
                val folderName = (allMedia ?: getAllMedia()).find { it.uri == uri }?.folderName ?: ""
                mediaDao.insertMetadata(MediaMetadataEntity(uri = uri, ageRating = ageRating, folderName = folderName, isAiAnalyzed = false))
            } else {
                mediaDao.bulkUpdateAgeRating(listOf(uri), ageRating)
            }
        }
    }

    suspend fun bulkAddTags(uris: List<String>, tags: List<String>) {
        uris.forEach { uri ->
            tags.forEach { tag ->
                saveTag(TagEntity(uri, tag, confidence = 1.0f))
            }
        }
    }

    suspend fun updateAiAnalysisResult(uri: String, ageRating: String, isAiAnalyzed: Boolean, folderName: String? = null) {
        val current = mediaDao.getMetadata(uri)
        if (current == null) {
            val finalFolder = folderName ?: getAllMedia().find { it.uri == uri }?.folderName ?: ""
            mediaDao.insertMetadata(MediaMetadataEntity(uri, ageRating = ageRating, isAiAnalyzed = isAiAnalyzed, folderName = finalFolder))
        } else {
            mediaDao.updateAiAnalysisResult(uri, ageRating, isAiAnalyzed)
        }
    }

    suspend fun updateFeatureVector(uri: String, featureVector: FloatArray, folderName: String? = null) {
        val current = mediaDao.getMetadata(uri)
        if (current == null) {
            val finalFolder = folderName ?: getAllMedia().find { it.uri == uri }?.folderName ?: ""
            mediaDao.insertMetadata(MediaMetadataEntity(uri, featureVector = featureVector, folderName = finalFolder))
        } else {
            mediaDao.updateFeatureVector(uri, featureVector)
        }
    }

    suspend fun saveTag(tag: TagEntity) {
        mediaDao.insertTag(tag)
    }

    suspend fun moveMediaToFolder(uris: List<String>, targetFolder: String): Boolean {
        return withContext(Dispatchers.IO) {
            val opId = GlobalOperationService.startOperation("フォルダへ移動中: $targetFolder")
            var totalSuccess = 0
            val movedPaths = mutableListOf<String>()

            uris.forEachIndexed { index, uriString ->
                try {
                    val uri = Uri.parse(uriString)
                    var success = false

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val relativePath = if (targetFolder.equals("DCIM", ignoreCase = true)) "DCIM/" else "DCIM/$targetFolder/"
                        val pendingValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 1) }
                        context.contentResolver.update(uri, pendingValues, null, null)

                        val moveValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                            put(MediaStore.MediaColumns.IS_PENDING, 0)
                        }

                        val rows = context.contentResolver.update(uri, moveValues, null, null)
                        if (rows > 0) success = true
                    } else {
                        val cursor = context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
                        cursor?.use {
                            if (it.moveToFirst()) {
                                val oldPath = it.getString(0)
                                val oldFile = File(oldPath)
                                val dcim = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM)
                                val targetDir = File(dcim, targetFolder)
                                if (!targetDir.exists()) targetDir.mkdirs()
                                val newFile = File(targetDir, oldFile.name)

                                if (oldFile.renameTo(newFile)) {
                                    val values = ContentValues().apply { put(MediaStore.MediaColumns.DATA, newFile.absolutePath) }
                                    context.contentResolver.update(uri, values, null, null)
                                    success = true
                                    movedPaths.add(newFile.absolutePath)
                                }
                            }
                        }
                    }

                    if (success) {
                        totalSuccess++
                        mediaDao.bulkUpdateFolderName(listOf(uriString), targetFolder)
                        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) movedPaths.add(cursor.getString(0))
                        }
                    }
                    GlobalOperationService.updateProgress((index + 1).toFloat() / uris.size, id = opId)
                } catch (e: Exception) {
                    Log.e("MediaRepository", "Error moving media: $uriString", e)
                }
            }

            if (movedPaths.isNotEmpty()) {
                MediaScannerConnection.scanFile(context, movedPaths.toTypedArray(), null, null)
            }
            cachedMediaList = null
            GlobalOperationService.finishOperation(opId)
            totalSuccess > 0
        }
    }

    suspend fun createFolderUnderDCIM(folderName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val dcim = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM)
                val newFolder = File(dcim, folderName)
                if (!newFolder.exists()) newFolder.mkdirs()
                true
            } catch (e: Exception) {
                Log.e("MediaRepository", "Error creating folder: $folderName", e)
                false
            }
        }
    }

    fun getAllTagNames(): Flow<List<String>> = mediaDao.getAllTags()
    fun getAllTagsWithCounts(): Flow<List<com.example.gallery.data.local.entity.TagCount>> = mediaDao.getAllTagsWithCounts().map { it.sortedByDescending { c -> c.count } }
    fun getCountForTag(tag: String): Flow<Int> = mediaDao.getCountForTag(tag)
    fun getThumbnailForTag(tag: String): Flow<String?> = mediaDao.getThumbnailForTag(tag)

    fun getFavoriteMedia(): Flow<List<MediaData>> {
        return mediaDao.getFavoriteUris().map { uris ->
            val uriSet = uris.toSet()
            getAllMedia().filter { it.uri in uriSet }
        }
    }

    fun getMediaForTag(tag: String): Flow<List<MediaData>> {
        return mediaDao.getMediaForTag(tag).map { entities ->
            val uriSet = entities.map { it.uri }.toSet()
            getAllMedia().filter { it.uri in uriSet }
        }
    }

    suspend fun getMediaForTags(tags: List<String>, ageRating: String? = null): List<MediaData> {
        if (tags.isEmpty()) return emptyList()
        val allMedia = getAllMedia()
        val allMediaMap = allMedia.associateBy { it.uri }
        val uris = mutableSetOf<String>()
        tags.forEach { tag -> mediaDao.getMediaForTag(tag).first().forEach { uris.add(it.uri) } }
        var results = uris.mapNotNull { allMediaMap[it] }
        if (ageRating != null) {
            val metadataMap = getAllMetadataSummary().associateBy { it.uri }
            results = results.filter { metadataMap[it.uri]?.ageRating == ageRating }
        }
        return results
    }

    fun getUntaggedCount(): Flow<Int> = mediaDao.getManualTaggedUrisFlow().map { taggedUris ->
        val taggedSet = taggedUris.toSet()
        getAllMedia().count { it.uri !in taggedSet && !it.isVideo }
    }

    fun getUntaggedMedia(): Flow<List<MediaData>> = mediaDao.getManualTaggedUrisFlow().map { taggedUris ->
        val taggedSet = taggedUris.toSet()
        getAllMedia().filter { it.uri !in taggedSet && !it.isVideo }
    }

    fun getUnanalyzedAiCount(filter: AgeRatingFilter): Flow<Int> = getAllMetadataSummaryFlow().map { metadata ->
        val metaMap = metadata.associateBy { it.uri }
        val analyzedUris = metadata.filter { it.isAiAnalyzed }.map { it.uri }.toSet()
        getAllMedia().count { item ->
            if (item.isVideo || item.uri in analyzedUris) return@count false
            val rating = metaMap[item.uri]?.ageRating ?: "SFW"
            when (filter) {
                AgeRatingFilter.ALL -> true
                AgeRatingFilter.SFW -> rating == "SFW"
                AgeRatingFilter.R15 -> rating == "R15"
                AgeRatingFilter.R18 -> rating == "R18"
            }
        }
    }

    suspend fun getMetadata(uri: String): MediaMetadataEntity? = mediaDao.getMetadata(uri)
    fun getRandomMedia(limit: Int): List<MediaData> = (cachedMediaList ?: emptyList()).shuffled().take(limit)

    suspend fun getRandomMediaByAgeRating(limit: Int, ageRating: String): List<MediaData> {
        val allMedia = getAllMedia()
        val allMetadata = getAllMetadataSummary().associateBy { it.uri }
        return allMedia.filter { (allMetadata[it.uri]?.ageRating ?: "SFW") == ageRating }.shuffled().take(limit)
    }

    suspend fun getAllMetadata(): List<MediaMetadataEntity> = mediaDao.getAllMetadata()
    suspend fun getAllMetadataSummary(): List<MediaMetadataSummary> = mediaDao.getAllMetadataSummary()
    fun getAllMetadataFlow(): Flow<List<MediaMetadataEntity>> = mediaDao.getAllMetadataFlow()
    fun getAllMetadataSummaryFlow(): Flow<List<MediaMetadataSummary>> = mediaDao.getAllMetadataSummaryFlow()
    fun getAllTagsWithUris(): Flow<List<TagEntity>> = mediaDao.getAllTagsWithUris()

    // 計測データ用
    suspend fun getMeasureStats(uri: String): com.example.gallery.data.local.entity.MeasureStatsEntity? = mediaDao.getMeasureStats(uri)
    suspend fun updateMeasureStats(uri: String, durationSeconds: Long) {
        val current = mediaDao.getMeasureStats(uri)
        if (current == null) {
            mediaDao.insertMeasureStats(com.example.gallery.data.local.entity.MeasureStatsEntity(uri, viewCount = 1, totalDurationSeconds = durationSeconds))
        } else {
            mediaDao.insertMeasureStats(current.copy(
                viewCount = current.viewCount + 1,
                totalDurationSeconds = current.totalDurationSeconds + durationSeconds,
                lastViewedTimestamp = System.currentTimeMillis()
            ))
        }
    }
    fun getTopMeasureStats(limit: Int): Flow<List<com.example.gallery.data.local.entity.MeasureStatsEntity>> = mediaDao.getTopMeasureStats(limit)
    fun getAllMeasureStatsFlow(): Flow<List<com.example.gallery.data.local.entity.MeasureStatsEntity>> = mediaDao.getAllMeasureStatsFlow()

    suspend fun getRecommendations(limit: Int = 100): List<MediaData> = withContext(Dispatchers.Default) {
        val stats = mediaDao.getAllMeasureStatsFlow().first().sortedByDescending { it.totalDurationSeconds * it.viewCount }
        if (stats.isEmpty()) return@withContext getRandomMedia(limit)

        val topStats = stats.take(20)
        val recommendedUris = mutableSetOf<String>()
        val allMedia = getAllMedia()
        val allMediaMap = allMedia.associateBy { it.uri }

        // 高評価（長く見た、よく見た）もの自体を入れる
        topStats.forEach { recommendedUris.add(it.uri) }

        // 類似のものを探す
        topStats.forEach { s ->
            findMediaByTagSimilarity(s.uri).forEach { recommendedUris.add(it.media.uri) }
            findSimilarVisualMedia(s.uri).forEach { recommendedUris.add(it.media.uri) }
        }

        val results = recommendedUris.mapNotNull { allMediaMap[it] }.shuffled().toMutableList()
        if (results.size < limit) {
            val remaining = limit - results.size
            results.addAll(allMedia.filter { it.uri !in recommendedUris }.shuffled().take(remaining))
        }
        results.take(limit)
    }

    fun getAllFolderOrders(): Flow<List<com.example.gallery.data.local.entity.FolderOrderEntity>> = mediaDao.getAllFolderOrders()
    suspend fun updateFolderOrders(orders: List<com.example.gallery.data.local.entity.FolderOrderEntity>) = orders.forEach { mediaDao.insertFolderOrder(it) }
    fun getAllManagedFolderNames(): Flow<List<String>> = mediaDao.getAllManagedFolderNames()
    fun getAllManagedFolders(): Flow<List<com.example.gallery.data.local.entity.ManagedFolderEntity>> = mediaDao.getAllManagedFolders()

    suspend fun updateFolderThumbnail(folderName: String, uri: String?) {
        val existing = mediaDao.getAllManagedFolders().first().find { it.folderName == folderName }
        if (existing == null) mediaDao.insertManagedFolder(com.example.gallery.data.local.entity.ManagedFolderEntity(folderName, customThumbnailUri = uri))
        else mediaDao.updateFolderThumbnail(folderName, uri)
    }
    suspend fun addManagedFolder(name: String) = mediaDao.insertManagedFolder(com.example.gallery.data.local.entity.ManagedFolderEntity(name))
    fun scanAllFolders(): List<String> {
        val folders = mutableSetOf<String>()
        try {
            val dcim = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM)
            dcim.listFiles()?.forEach { file -> if (file.isDirectory) folders.add(file.name) }
        } catch (e: Exception) { Log.e("MediaRepository", "Error scanning folders", e) }
        return folders.toList().sorted()
    }

    fun getTagsForMedia(uri: String): Flow<List<TagEntity>> = mediaDao.getTagsForMedia(uri)
    fun getManualTaggedUrisFlow(): Flow<List<String>> = mediaDao.getManualTaggedUrisFlow()

    suspend fun findMediaByTagSimilarity(uri: String): List<MediaSimilarity> = withContext(Dispatchers.Default) {
        val targetTags = getTagsForMedia(uri).first().filter { !it.tag.endsWith("系") && it.confidence >= 0.6f }.map { it.tag }.toSet()
        if (targetTags.isEmpty()) return@withContext emptyList()
        val allTags = mediaDao.getAllTagsWithUris().first().filter { it.uri != uri && it.tag in targetTags }.groupBy { it.uri }
        val allMediaMap = getAllMedia().associateBy { it.uri }
        allTags.mapNotNull { (resUri, tags) ->
            val media = allMediaMap[resUri] ?: return@mapNotNull null
            val score = tags.size.toFloat() / targetTags.size.toFloat()
            if (score < 0.2f) return@mapNotNull null
            MediaSimilarity(media, score)
        }.sortedByDescending { it.similarityScore }.take(25)
    }

    data class MediaSimilarity(val media: MediaData, val similarityScore: Float)

    suspend fun findSimilarVisualMedia(uri: String): List<MediaSimilarity> = withContext(Dispatchers.Default) {
        val targetMetadata = getMetadata(uri) ?: return@withContext emptyList()
        val targetVector = targetMetadata.featureVector ?: return@withContext emptyList()
        val allVectors = mediaDao.getAllVectors().filter { it.uri != uri }
        val allMediaMap = getAllMedia().associateBy { it.uri }
        allVectors.asSequence()
            .map { meta -> meta.uri to VectorSearchService.cosineSimilarity(targetVector, meta.featureVector) }
            .filter { it.second > 0.4f }
            .sortedByDescending { it.second }
            .take(25)
            .mapNotNull { (resUri, score) -> allMediaMap[resUri]?.let { MediaSimilarity(it, score) } }
            .toList()
    }
}

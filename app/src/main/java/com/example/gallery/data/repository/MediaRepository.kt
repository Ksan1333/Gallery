package com.example.gallery.data.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import java.io.File
import com.example.gallery.R
import com.example.gallery.util.buildAdjacentSimilarityGroups
import com.example.gallery.data.local.dao.MediaDao
import com.example.gallery.data.local.Converters
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
import com.example.gallery.ui.AppDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.*

private const val FOLDER_MOVE_TRACE = "FOLDER_MOVE_TRACE"
private const val SIMILAR_GROUP_TRACE = "GALLERY_SIMILAR_GROUP_TRACE"

class MediaRepository(
    private val context: Context,
    val mediaDao: MediaDao,
    val galleryState: GalleryState? = null
) {
    sealed interface MoveMediaResult {
        data class Completed(
            val movedCount: Int,
            val failedCount: Int,
            val failedUris: List<String> = emptyList()
        ) : MoveMediaResult

        data class PermissionRequired(
            val intentSender: IntentSender,
            val pendingUris: List<String>,
            val targetFolder: String,
            val movedCount: Int,
            val failedCount: Int,
            val failedUris: List<String>
        ) : MoveMediaResult
    }

    data class SimilarMediaGroup(
        val items: List<MediaData>,
        val minimumSimilarity: Float
    )

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
                enablePlaceholders = true // スクロールバーのサイズを正しく出すため有効化する。
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

            // 日付などのセパレータはプレースホルダーと競合しやすいため、
            // グルーピング時だけ全件ロードでセパレータを生成する。
            
            if (groupingMode == GroupingMode.NONE) {
                mediaPaging.map { it as GridItem }
            } else {
                val sdf = when (groupingMode) {
                    GroupingMode.DAY -> SimpleDateFormat(context.getString(R.string.format_date_day), Locale.JAPAN)
                        GroupingMode.MONTH -> SimpleDateFormat(context.getString(R.string.format_date_month), Locale.JAPAN)
                    GroupingMode.YEAR -> SimpleDateFormat(context.getString(R.string.format_date_year), Locale.JAPAN)
                    else -> null
                }
                
                if (sdf == null) {
                    mediaPaging.map { it as GridItem }
                } else {
                    // insertSeparators はプレースホルダーと併用すると
                    // スクロールバーの挙動が不安定になりやすい。
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
        val deviceAspectRatio = 1.0f // デフォルト値。必要なら引数で受け取る。

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

        var opId: String? = null
        fun ensureSyncOperation(): String {
            opId?.let { return it }
            val newOpId = GlobalOperationService.startOperation(context.getString(R.string.msg_syncing_library), tag = "SYNC_MEDIA_STORE")
            opId = newOpId
            GlobalOperationService.updateProgress(0f, context.getString(R.string.msg_applying_changes), id = newOpId)
            return newOpId
        }
        
        try {
            val existingFullMetadata = mediaDao.getAllMetadata().associateBy { it.uri }
            val existingMetadata = existingFullMetadata.mapValues { (_, entity) ->
                com.example.gallery.data.local.entity.MediaMetadataSummary(
                    uri = entity.uri,
                    dateAdded = entity.dateAdded,
                    mimeType = entity.mimeType,
                    duration = entity.duration,
                    width = entity.width,
                    height = entity.height,
                    fileSize = entity.fileSize,
                    fileName = entity.fileName,
                    isFavorite = entity.isFavorite,
                    ageRating = entity.ageRating,
                    isAiAnalyzed = entity.isAiAnalyzed,
                    aiAnalysisModel = entity.aiAnalysisModel,
                    folderName = entity.folderName,
                    isDeleted = entity.isDeleted,
                    deletedDate = entity.deletedDate,
                    hasFeatureVector = entity.featureVector != null,
                    hasThumbnail = entity.hasThumbnail,
                    startupThumbnailAttempted = entity.startupThumbnailAttempted,
                    startupVectorAttempted = entity.startupVectorAttempted
                )
            }
            val foundUris = HashSet<String>(10000)
            val newEntities = mutableListOf<MediaMetadataEntity>()

            val projection = buildList {
                add(MediaStore.MediaColumns._ID)
                add(MediaStore.MediaColumns.DATA)
                add(MediaStore.MediaColumns.DATE_ADDED)
                add(MediaStore.MediaColumns.DATE_TAKEN)
                add(MediaStore.MediaColumns.MIME_TYPE)
                add(MediaStore.MediaColumns.DURATION)
                add(MediaStore.MediaColumns.WIDTH)
                add(MediaStore.MediaColumns.HEIGHT)
                add(MediaStore.MediaColumns.SIZE)
                add(MediaStore.MediaColumns.DISPLAY_NAME)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(MediaStore.MediaColumns.RELATIVE_PATH)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    add(MediaStore.MediaColumns.IS_TRASHED)
                }
            }.toTypedArray()

            val volumeNames = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.getExternalVolumeNames(context)
            } else {
                setOf("external")
            }

            // 先に全体件数を取得して進捗率を正確にする。
            var totalToProcess = 0
            volumeNames.forEach { vn ->
                listOf(MediaStore.Images.Media.getContentUri(vn), MediaStore.Video.Media.getContentUri(vn)).forEach { col ->
                    queryMediaStore(col, arrayOf(MediaStore.MediaColumns._ID))?.use {
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
                        queryMediaStore(collection, projection)?.use { cursor ->
                            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                            val dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                            val dateTakenColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                            val durationColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
                            val widthColumn = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                            val heightColumn = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                            val sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                            val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                            } else {
                                -1
                            }
                            val trashedColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                cursor.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED)
                            } else {
                                -1
                            }

                            while (cursor.moveToNext()) {
                                processedCount++
                                if (processedCount % 100 == 0) {
                                    // 読み込み進捗を 0-85% で表現する。
                                    opId?.let { activeOpId ->
                                        GlobalOperationService.updateProgress(
                                            (processedCount.toFloat() / totalF) * 0.85f,
                                            id = activeOpId
                                        )
                                    }
                                }

                                val id = cursor.getLong(idColumn)
                                val contentUri = ContentUris.withAppendedId(collection, id).toString()
                                if (!foundUris.add(contentUri)) continue

                                val existing = existingMetadata[contentUri]
                                val existingEntity = existingFullMetadata[contentUri]
                                
                                val dateTaken = if (dateTakenColumn != -1) cursor.getLong(dateTakenColumn) else 0L
                                val dateAdded = cursor.getLong(dateColumn) * 1000
                                val date = if (dateTaken > 0L) dateTaken else dateAdded
                                val mime = cursor.getString(mimeColumn)
                                val duration = if (durationColumn != -1) cursor.getLong(durationColumn) else 0L
                                val width = if (widthColumn != -1) cursor.getInt(widthColumn) else 0
                                val height = if (heightColumn != -1) cursor.getInt(heightColumn) else 0
                                val size = if (sizeColumn != -1) cursor.getLong(sizeColumn) else 0L
                                val name = if (nameColumn != -1) cursor.getString(nameColumn) ?: "" else ""
                                val path = if (dataColumn != -1) cursor.getString(dataColumn) else null
                                val relativePath = if (relativePathColumn != -1) cursor.getString(relativePathColumn) else null
                                val isMediaStoreTrashed = trashedColumn != -1 && cursor.getInt(trashedColumn) == 1
                                
                                val folderName = if (!relativePath.isNullOrBlank()) {
                                    relativePath.replace('\\', '/').trim('/').substringAfterLast('/').ifBlank { "Unknown" }
                                } else if (path != null) {
                                    val lastSeparator = path.lastIndexOf(File.separator)
                                    if (lastSeparator > 0) {
                                        val prevSeparator = path.lastIndexOf(File.separator, lastSeparator - 1)
                                        if (prevSeparator >= 0) path.substring(prevSeparator + 1, lastSeparator)
                                        else path.substring(0, lastSeparator)
                                    } else "Unknown"
                                } else "Unknown"

                                val nextIsDeleted = existing?.isDeleted == true || isMediaStoreTrashed
                                if (existing == null ||
                                    existing.dateAdded != date ||
                                    existing.fileSize != size ||
                                    existing.folderName != folderName ||
                                    existing.isDeleted != nextIsDeleted
                                ) {
                                    ensureSyncOperation()
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
                                        aiAnalysisModel = existing?.aiAnalysisModel ?: "",
                                        featureVector = existingEntity?.featureVector,
                                        isDeleted = nextIsDeleted,
                                        deletedDate = existing?.deletedDate ?: if (isMediaStoreTrashed) System.currentTimeMillis() else null,
                                        hasThumbnail = existing?.hasThumbnail ?: false,
                                        startupThumbnailAttempted = existing?.startupThumbnailAttempted ?: false,
                                        startupVectorAttempted = existing?.startupVectorAttempted ?: false
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
                val activeOpId = ensureSyncOperation()
                GlobalOperationService.updateProgress(0.9f, context.getString(R.string.msg_updating_db), id = activeOpId)
                newEntities.chunked(100).forEach { chunk ->
                    mediaDao.bulkInsertMetadata(chunk)
                }
            }

            // 削除されたファイルをクリーンアップする。
            val dbUrisToDelete = existingMetadata.keys.filter {
                it.startsWith("content://media/") && !foundUris.contains(it)
            }
            if (dbUrisToDelete.isNotEmpty()) {
                val activeOpId = ensureSyncOperation()
                GlobalOperationService.updateProgress(0.95f, context.getString(R.string.msg_deleting_obsolete), id = activeOpId)
                dbUrisToDelete.chunked(100).forEach { chunk ->
                    mediaDao.bulkDeleteMetadata(chunk)
                    mediaDao.bulkDeleteTags(chunk)
                }
            }
            
            opId?.let { activeOpId ->
                GlobalOperationService.updateProgress(1.0f, context.getString(R.string.msg_sync_complete), id = activeOpId)
            }
            cachedMediaList = null
        } finally {
            opId?.let { GlobalOperationService.finishOperation(it) }
        }
    }

    private fun queryMediaStore(
        collection: Uri,
        projection: Array<String>
    ): android.database.Cursor? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val queryArgs = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
            }
            context.contentResolver.query(collection, projection, queryArgs, null)
        } else {
            context.contentResolver.query(collection, projection, null, null, null)
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
        // getAllMediaFiltered 内の一括チェックへ統合した。
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
        val opId = GlobalOperationService.startOperation(context.getString(R.string.msg_moving_to_trash))
        updateMediaStoreTrashState(uris, true)
        mediaDao.bulkSetDeleted(uris, true, System.currentTimeMillis())
        GlobalOperationService.updateProgress(1.0f, id = opId)
        cachedMediaList = null
        galleryState?.refresh()
        GlobalOperationService.finishOperation(opId)
    }

    suspend fun restoreFromTrash(uris: List<String>) {
        val opId = GlobalOperationService.startOperation(context.getString(R.string.msg_restoring))
        updateMediaStoreTrashState(uris, false)
        mediaDao.bulkSetDeleted(uris, false, null)
        GlobalOperationService.updateProgress(1.0f, id = opId)
        cachedMediaList = null
        galleryState?.refresh()
        GlobalOperationService.finishOperation(opId)
    }

    private suspend fun updateMediaStoreTrashState(uris: List<String>, isTrashed: Boolean) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_TRASHED, if (isTrashed) 1 else 0)
        }
        uris.forEach { uriString ->
            runCatching {
                context.contentResolver.update(Uri.parse(uriString), values, null, null)
            }.onFailure { e ->
                Log.w("MediaRepository", "Failed to update MediaStore trash state: $uriString", e)
            }
        }
    }

    suspend fun permanentlyDelete(uris: List<String>) {
        val opId = GlobalOperationService.startOperation(context.getString(R.string.msg_permanently_deleting))
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

    suspend fun updateAiAnalysisResult(
        uri: String,
        ageRating: String,
        isAiAnalyzed: Boolean,
        folderName: String? = null,
        aiAnalysisModel: String = if (isAiAnalyzed) AppDefaults.AI_TAGGER_MODEL_NORMAL else ""
    ) {
        val current = mediaDao.getMetadata(uri)
        if (current == null) {
            val finalFolder = folderName ?: getAllMedia().find { it.uri == uri }?.folderName ?: ""
            mediaDao.insertMetadata(
                MediaMetadataEntity(
                    uri,
                    ageRating = ageRating,
                    isAiAnalyzed = isAiAnalyzed,
                    aiAnalysisModel = aiAnalysisModel,
                    folderName = finalFolder
                )
            )
        } else {
            mediaDao.updateAiAnalysisResult(uri, ageRating, isAiAnalyzed, aiAnalysisModel)
        }
    }

    suspend fun updateFeatureVector(uri: String, featureVector: FloatArray, folderName: String? = null) {
        val current = mediaDao.getMetadata(uri)
        if (current == null) {
            val finalFolder = folderName ?: getAllMedia().find { it.uri == uri }?.folderName ?: ""
            mediaDao.insertMetadata(MediaMetadataEntity(uri, featureVector = featureVector, folderName = finalFolder))
        } else {
            mediaDao.updateFeatureVector(uri, Converters().fromFloatArray(featureVector))
        }
    }

    suspend fun saveTag(tag: TagEntity) {
        mediaDao.insertTag(tag)
    }

    suspend fun saveTags(tags: List<TagEntity>) {
        if (tags.isNotEmpty()) {
            mediaDao.insertTags(tags)
        }
    }

    suspend fun saveAiAnalysisResult(
        uri: String,
        ageRating: String,
        isAiAnalyzed: Boolean,
        folderName: String?,
        aiAnalysisModel: String,
        tags: List<TagEntity>
    ) {
        val finalFolder = folderName ?: getAllMedia().find { it.uri == uri }?.folderName ?: ""
        mediaDao.saveAiAnalysisResult(uri, ageRating, isAiAnalyzed, aiAnalysisModel, finalFolder, tags)
    }

    suspend fun moveMediaToFolder(uris: List<String>, targetFolder: String): MoveMediaResult {
        return withContext(Dispatchers.IO) {
            if (uris.isEmpty()) return@withContext MoveMediaResult.Completed(0, 0)
            val targetRelativePath = resolveMoveTargetRelativePath(targetFolder)
            val targetFolderName = targetRelativePath.trimEnd('/').substringAfterLast('/').ifBlank { "DCIM" }
            val opId = GlobalOperationService.startOperation(context.getString(R.string.msg_moving_to_folder, targetFolderName))
            var totalSuccess = 0
            var totalFailed = 0
            val failedUris = mutableListOf<String>()
            val movedPaths = mutableListOf<String>()

            Log.d(
                FOLDER_MOVE_TRACE,
                "start count=${uris.size} requested=${targetFolder.take(80)} target=$targetRelativePath sdk=${Build.VERSION.SDK_INT}"
            )
            try {
                uris.forEachIndexed { index, uriString ->
                    val uri = Uri.parse(uriString)
                    try {
                        val beforePath = queryMediaRelativePath(uri)
                        val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val moveValues = ContentValues().apply {
                                put(MediaStore.MediaColumns.RELATIVE_PATH, targetRelativePath)
                            }
                            val rows = context.contentResolver.update(uri, moveValues, null, null)
                            val afterPath = queryMediaRelativePath(uri)
                            val verified = sameRelativePath(afterPath, targetRelativePath)
                            Log.d(
                                FOLDER_MOVE_TRACE,
                                "item index=$index uriHash=${uriString.hashCode()} before=$beforePath target=$targetRelativePath " +
                                    "rows=$rows after=$afterPath verified=$verified"
                            )
                            verified
                        } else {
                            moveLegacyMedia(uri, targetRelativePath, movedPaths).also { moved ->
                                Log.d(
                                    FOLDER_MOVE_TRACE,
                                    "item_legacy index=$index uriHash=${uriString.hashCode()} before=$beforePath " +
                                        "target=$targetRelativePath verified=$moved"
                                )
                            }
                        }

                        if (success) {
                            totalSuccess++
                            mediaDao.bulkUpdateFolderName(listOf(uriString), targetFolderName)
                        } else {
                            totalFailed++
                            failedUris.add(uriString)
                        }
                    } catch (securityException: SecurityException) {
                        val pendingUris = uris.drop(index)
                        val intentSender = createMovePermissionIntentSender(
                            securityException = securityException,
                            uris = pendingUris.map(Uri::parse)
                        )
                        Log.w(
                            FOLDER_MOVE_TRACE,
                            "permission_required index=$index uriHash=${uriString.hashCode()} pending=${pendingUris.size} " +
                                "target=$targetRelativePath sender=${intentSender != null}",
                            securityException
                        )
                        if (intentSender != null) {
                            return@withContext MoveMediaResult.PermissionRequired(
                                intentSender = intentSender,
                                pendingUris = pendingUris,
                                targetFolder = targetRelativePath,
                                movedCount = totalSuccess,
                                failedCount = totalFailed,
                                failedUris = failedUris.toList()
                            )
                        }
                        totalFailed++
                        failedUris.add(uriString)
                    } catch (e: Exception) {
                        totalFailed++
                        failedUris.add(uriString)
                        Log.e(
                            FOLDER_MOVE_TRACE,
                            "item_failed index=$index uriHash=${uriString.hashCode()} target=$targetRelativePath",
                            e
                        )
                    } finally {
                        GlobalOperationService.updateProgress((index + 1).toFloat() / uris.size, id = opId)
                    }
                }

                if (movedPaths.isNotEmpty()) {
                    MediaScannerConnection.scanFile(context, movedPaths.distinct().toTypedArray(), null, null)
                }
                cachedMediaList = null
                Log.d(
                    FOLDER_MOVE_TRACE,
                    "complete requested=${uris.size} moved=$totalSuccess failed=$totalFailed target=$targetRelativePath"
                )
                MoveMediaResult.Completed(totalSuccess, totalFailed, failedUris.toList())
            } finally {
                cachedMediaList = null
                GlobalOperationService.finishOperation(opId)
            }
        }
    }

    private suspend fun resolveMoveTargetRelativePath(targetFolder: String): String {
        val normalizedInput = normalizeRelativePath(targetFolder)
        if (targetFolder.replace('\\', '/').contains('/')) return normalizedInput

        val folderName = targetFolder.trim().ifBlank { "DCIM" }
        val representativeUri = mediaDao.getAllMetadataSummary()
            .firstOrNull { it.folderName.equals(folderName, ignoreCase = true) }
            ?.uri
            ?.let(Uri::parse)
        val existingPath = representativeUri?.let(::queryMediaRelativePath)
        if (!existingPath.isNullOrBlank()) {
            Log.d(FOLDER_MOVE_TRACE, "target_resolved folder=$folderName relativePath=$existingPath")
            return normalizeRelativePath(existingPath)
        }

        val rootFolder = listOf("DCIM", "Pictures", "Movies", "Download")
            .firstOrNull { it.equals(folderName, ignoreCase = true) }
        return if (rootFolder != null) "$rootFolder/" else "DCIM/$folderName/"
    }

    private fun normalizeRelativePath(path: String): String {
        val normalized = path.trim().replace('\\', '/').trim('/')
        return if (normalized.isBlank()) "DCIM/" else "$normalized/"
    }

    private fun sameRelativePath(first: String?, second: String): Boolean {
        if (first == null) return false
        return normalizeRelativePath(first).equals(normalizeRelativePath(second), ignoreCase = true)
    }

    private fun queryMediaRelativePath(uri: Uri): String? {
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(MediaStore.MediaColumns.RELATIVE_PATH)
        } else {
            arrayOf(MediaStore.MediaColumns.DATA)
        }
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val rawPath = cursor.getString(0) ?: return@use null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    normalizeRelativePath(rawPath)
                } else {
                    val parent = File(rawPath).parentFile ?: return@use null
                    val storageRoot = android.os.Environment.getExternalStorageDirectory()
                    parent.relativeToOrNull(storageRoot)?.path?.let(::normalizeRelativePath)
                }
            }
        }.onFailure { error ->
            Log.w(FOLDER_MOVE_TRACE, "location_query_failed uriHash=${uri.toString().hashCode()}", error)
        }.getOrNull()
    }

    private fun moveLegacyMedia(uri: Uri, targetRelativePath: String, movedPaths: MutableList<String>): Boolean {
        val oldPath = context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DATA),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: return false
        val oldFile = File(oldPath)
        val storageRoot = android.os.Environment.getExternalStorageDirectory()
        val targetDir = File(storageRoot, targetRelativePath.trim('/'))
        if (!targetDir.exists() && !targetDir.mkdirs()) return false
        val targetFile = File(targetDir, oldFile.name)
        if (oldFile.absolutePath.equals(targetFile.absolutePath, ignoreCase = true)) return true
        if (targetFile.exists()) return false
        if (!oldFile.renameTo(targetFile)) return false

        val values = ContentValues().apply { put(MediaStore.MediaColumns.DATA, targetFile.absolutePath) }
        context.contentResolver.update(uri, values, null, null)
        movedPaths.add(targetFile.absolutePath)
        return true
    }

    private fun createMovePermissionIntentSender(
        securityException: SecurityException,
        uris: List<Uri>
    ): IntentSender? {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> runCatching {
                MediaStore.createWriteRequest(context.contentResolver, uris).intentSender
            }.onFailure { error ->
                Log.e(FOLDER_MOVE_TRACE, "permission_request_create_failed count=${uris.size}", error)
            }.getOrNull()
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                (securityException as? android.app.RecoverableSecurityException)
                    ?.userAction
                    ?.actionIntent
                    ?.intentSender
            }
            else -> null
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
        val selectedTaggerModel = currentAiTaggerModel()
        getAllMedia().count { item ->
            if (item.isVideo) return@count false
            val itemMetadata = metaMap[item.uri]
            if (
                itemMetadata?.isAiAnalyzed == true &&
                AppDefaults.aiTaggerModelRank(itemMetadata.aiAnalysisModel) >= AppDefaults.aiTaggerModelRank(selectedTaggerModel)
            ) {
                return@count false
            }
            val rating = itemMetadata?.ageRating ?: "SFW"
            when (filter) {
                AgeRatingFilter.ALL -> true
                AgeRatingFilter.SFW -> rating == "SFW"
                AgeRatingFilter.R15 -> rating == "R15"
                AgeRatingFilter.R18 -> rating == "R18"
            }
        }
    }

    private fun currentAiTaggerModel(): String {
        val prefs = context.getSharedPreferences("global_settings", Context.MODE_PRIVATE)
        return AppDefaults.normalizedAiTaggerModel(
            prefs.getString(AppDefaults.AI_TAGGER_MODEL_KEY, AppDefaults.AI_TAGGER_MODEL_NORMAL)
        )
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

    // 計測データ用。
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

        // 高評価のタグ自体を入れる。
        topStats.forEach { recommendedUris.add(it.uri) }

        // 類似のものを探す。
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
        val tagSuffixGroup = context.getString(R.string.label_tag_suffix_group)
        val targetTags = getTagsForMedia(uri).first().filter { !it.tag.endsWith(tagSuffixGroup) && it.confidence >= 0.6f }.map { it.tag }.toSet()
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

    suspend fun findAdjacentSimilarMediaGroups(
        mediaItems: List<MediaData>,
        threshold: Float = 0.6f
    ): List<SimilarMediaGroup> = withContext(Dispatchers.Default) {
        if (mediaItems.size < 2) return@withContext emptyList()
        val startedAt = System.currentTimeMillis()
        val safeThreshold = threshold.coerceIn(-1f, 1f)
        val mediaUriSet = mediaItems.asSequence().map { it.uri }.toHashSet()
        val vectorsByUri = mediaDao.getAllVectors()
            .asSequence()
            .filter { vector -> vector.uri in mediaUriSet }
            .associate { it.uri to it.featureVector }
        if (vectorsByUri.size < 2) return@withContext emptyList()

        val chronological = mediaItems
            .distinctBy { it.uri }
            .sortedWith(compareBy<MediaData> { it.dateAdded }.thenBy { it.uri })
        val groups = buildAdjacentSimilarityGroups(
            items = chronological,
            threshold = safeThreshold
        ) { previous, media ->
            val previousVector = vectorsByUri[previous.uri]
            val candidateVector = vectorsByUri[media.uri]
            if (
                !previous.isVideo && !media.isVideo && previousVector != null && candidateVector != null
            ) {
                VectorSearchService.cosineSimilarity(previousVector, candidateVector)
            } else {
                null
            }
        }.map { group ->
            SimilarMediaGroup(group.items, group.minimumSimilarity)
        }

        Log.d(
            SIMILAR_GROUP_TRACE,
            "built media=${mediaItems.size} vectors=${vectorsByUri.size} groups=${groups.size} " +
                "grouped=${groups.sumOf { it.items.size }} largest=${groups.maxOfOrNull { it.items.size } ?: 0} " +
                "threshold=$safeThreshold adjacentChain=true " +
                "elapsedMs=${System.currentTimeMillis() - startedAt}"
        )
        groups
    }

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

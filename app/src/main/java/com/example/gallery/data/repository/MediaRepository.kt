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
import com.example.gallery.ui.MediaData
import com.example.gallery.ui.AgeRatingFilter
import com.example.gallery.service.GlobalOperationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MediaRepository(
    private val context: Context,
    val mediaDao: MediaDao,
    val galleryState: com.example.gallery.ui.GalleryState? = null
) {
    private val tagNames = listOf("人物", "美少女", "風景", "動物", "食べ物", "建物", "空", "海", "花")
    private val folderNames = listOf("Camera", "Downloads", "Twitter", "Screenshots", "Pixiv", "Instagram")

    private var mockMetadata = mutableMapOf<String, MediaMetadataEntity>()
    private var mockTags = mutableListOf<TagEntity>()

    private val mockMediaList: List<MediaData> by lazy {
        (1..100).map { i ->
            val isPortrait = i % 3 == 0
            val width = if (isPortrait) 1080 else 1920
            val height = if (isPortrait) 1920 else 1080
            // valid Picsum URL: https://picsum.photos/id/1/1920/1080
            val uri = "https://picsum.photos/id/$i/$width/$height"
            MediaData(
                uri = uri,
                dateAdded = System.currentTimeMillis() - (i * 3600000L),
                mimeType = "image/jpeg",
                width = width,
                height = height,
                fileName = "MockImage_$i.jpg",
                folderName = folderNames[i % folderNames.size]
            )
        }
    }

    private val mockFolderMap: Map<String, String> by lazy {
        mockMediaList.associate { it.uri to it.folderName }
    }

    fun getMockFolder(uri: String): String? = mockFolderMap[uri]

    private fun ensureMockDataInitialized() {
        if (mockMetadata.isEmpty() || mockTags.isEmpty()) {
            mockMetadata.clear()
            mockTags.clear()
            mockMediaList.forEachIndexed { i, item ->
                // 全てのアイテムに基本データを設定（奇数偶数関係なく）
                val isAnalyzed = i % 2 == 0
                mockMetadata[item.uri] = MediaMetadataEntity(
                    uri = item.uri,
                    ageRating = when {
                        i % 7 == 0 -> "R18"
                        i % 5 == 0 -> "R15"
                        else -> "SFW"
                    },
                    isAiAnalyzed = isAnalyzed,
                    isFavorite = i % 12 == 0
                )
                
                // タグを追加
                mockTags.add(TagEntity(item.uri, tagNames[i % tagNames.size]))
                mockTags.add(TagEntity(item.uri, "テスト済"))
            }
        }
    }

    fun clearMockData() {
        mockMetadata.clear()
        mockTags.clear()
    }

    private var cachedMediaList: List<MediaData>? = null
    private var lastCacheTime: Long = 0
    private val cacheMutex = Mutex()

    suspend fun getAllMedia(forceRefresh: Boolean = false): List<MediaData> {
        return getAllMediaFiltered(forceRefresh = forceRefresh, includeDeleted = false)
    }

    /**
     * DB内のメタデータのうち、実際のファイルが存在しないものを削除する。
     * getAllMediaFiltered の中から効率的に呼び出すように変更。
     */
    suspend fun cleanupDeletedFiles() {
        // 重い個別ファイルチェックを伴う cleanupDeletedFiles は廃止し、
        // getAllMediaFiltered 内での一括チェックに統合されました。
    }

    private suspend fun getAllMediaFiltered(forceRefresh: Boolean = false, includeDeleted: Boolean = false): List<MediaData> {
        if (galleryState?.isMockMode == true) {
            return mockMediaList
        }

        cacheMutex.withLock {
            val now = System.currentTimeMillis()
            // 同時に複数の画面からリクエストが来た場合、
            // 既に直近（2秒以内）で更新されていれば、強制更新であってもキャッシュを返す
            val isVeryRecent = now - lastCacheTime < 2000
            if (!includeDeleted && cachedMediaList != null && (isVeryRecent || (!forceRefresh && now - lastCacheTime < 10000))) {
                return cachedMediaList!!
            }

            // メモリ節約：リフレッシュ時は古いキャッシュを一旦解放してGCを助ける
            if (forceRefresh) {
                cachedMediaList = null
            }

            val allGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_VIDEO) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }

            if (!allGranted) return emptyList()

            // 削除済みURIと既存メタデータを先に取得してフィルタリングとクリーンアップに使用
            val allMetadata = mediaDao.getAllMetadataSummary()
            val deletedUris = allMetadata.filter { it.isDeleted }.map { it.uri }.toSet()
            
            // 初期容量を大きめに確保してリサイズを減らす
            val mediaList = ArrayList<MediaData>(10000)
            val foundUris = HashSet<String>(10000)

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
            val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

            val volumeNames = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.getExternalVolumeNames(context)
            } else {
                @Suppress("DEPRECATION")
                setOf(MediaStore.VOLUME_EXTERNAL)
            }

            volumeNames.forEach { volumeName ->
                val collections = listOf(
                    MediaStore.Images.Media.getContentUri(volumeName),
                    MediaStore.Video.Media.getContentUri(volumeName)
                )

                collections.forEach { collection ->
                    try {
                        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                            // 実際の件数に合わせて容量を調整
                            mediaList.ensureCapacity(mediaList.size + cursor.count)
                            
                            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                            val dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                            val durationColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
                            val widthColumn = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                            val heightColumn = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                            val sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                            val nameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)

                            while (cursor.moveToNext()) {
                                val id = cursor.getLong(idColumn)
                                val contentUri = ContentUris.withAppendedId(collection, id).toString()
                                
                                // 重複チェック
                                if (!foundUris.add(contentUri)) continue

                                // 削除済みかどうかに基づいて早期フィルタリング（メモリ節約）
                                val isDeleted = contentUri in deletedUris
                                if (isDeleted != includeDeleted) continue

                                val date = cursor.getLong(dateColumn) * 1000
                                val mime = cursor.getString(mimeColumn)
                                val duration = if (durationColumn != -1) cursor.getLong(durationColumn) else 0L
                                val width = if (widthColumn != -1) cursor.getInt(widthColumn) else 0
                                val height = if (heightColumn != -1) cursor.getInt(heightColumn) else 0
                                val size = if (sizeColumn != -1) cursor.getLong(sizeColumn) else 0L
                                val name = if (nameColumn != -1) cursor.getString(nameColumn) ?: "" else ""
                                val path = if (dataColumn != -1) cursor.getString(dataColumn) else null
                                val folderName = if (path != null) File(path).parentFile?.name ?: "Unknown" else "Unknown"

                                mediaList.add(MediaData(contentUri, date, mime, duration, width, height, size, name, folderName))
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MediaRepository", "Error querying volume $volumeName: ${e.message}")
                    }
                }
            }

            // 日付順にソート
            mediaList.sortByDescending { it.dateAdded }

            // DB内の実在しないデータのクリーンアップ
            if (forceRefresh || cachedMediaList == null) {
                allMetadata.forEach { meta ->
                    val exists = if (meta.uri.startsWith("content://")) {
                        try {
                            context.contentResolver.openInputStream(Uri.parse(meta.uri))?.use { true } ?: false
                        } catch (e: Exception) {
                            false
                        }
                    } else {
                        foundUris.contains(meta.uri)
                    }

                    if (!exists) {
                        mediaDao.deleteMetadata(meta.uri)
                        mediaDao.deleteTagsForMedia(meta.uri)
                    }
                }
            }
            
            // メモリ解放のヒント
            foundUris.clear()

            if (!includeDeleted) {
                cachedMediaList = mediaList
                lastCacheTime = now
            }
            return mediaList
        }
    }

    suspend fun moveToTrash(uris: List<String>) {
        GlobalOperationService.startOperation("ゴミ箱へ移動中...")
        if (galleryState?.isMockMode == true) {
            uris.forEachIndexed { index, uri ->
                val current = getMetadata(uri)
                mockMetadata[uri] = current?.copy(isDeleted = true, deletedDate = System.currentTimeMillis()) 
                    ?: MediaMetadataEntity(uri, isDeleted = true, deletedDate = System.currentTimeMillis())
                GlobalOperationService.updateProgress((index + 1).toFloat() / uris.size)
            }
        } else {
            mediaDao.bulkSetDeleted(uris, true, System.currentTimeMillis())
            GlobalOperationService.updateProgress(1.0f)
        }
        cachedMediaList = null
        galleryState?.refresh()
        GlobalOperationService.finishOperation()
    }

    suspend fun restoreFromTrash(uris: List<String>) {
        GlobalOperationService.startOperation("元に戻しています...")
        if (galleryState?.isMockMode == true) {
            uris.forEachIndexed { index, uri ->
                val current = getMetadata(uri)
                mockMetadata[uri] = current?.copy(isDeleted = false, deletedDate = null)
                    ?: MediaMetadataEntity(uri, isDeleted = false, deletedDate = null)
                GlobalOperationService.updateProgress((index + 1).toFloat() / uris.size)
            }
        } else {
            mediaDao.bulkSetDeleted(uris, false, null)
            GlobalOperationService.updateProgress(1.0f)
        }
        cachedMediaList = null
        galleryState?.refresh()
        GlobalOperationService.finishOperation()
    }

    suspend fun permanentlyDelete(uris: List<String>) {
        GlobalOperationService.startOperation("ファイルを完全に削除中...")
        if (galleryState?.isMockMode == true) {
            uris.forEach { uri -> mockMetadata.remove(uri) }
        } else {
            uris.forEachIndexed { index, uriString ->
                try {
                    val uri = Uri.parse(uriString)
                    context.contentResolver.delete(uri, null, null)
                    mediaDao.deleteMetadata(uriString)
                    mediaDao.deleteTagsForMedia(uriString)
                } catch (e: Exception) {
                    Log.e("MediaRepository", "Failed to delete $uriString", e)
                }
                GlobalOperationService.updateProgress((index + 1).toFloat() / uris.size)
            }
        }
        cachedMediaList = null
        galleryState?.refresh()
        GlobalOperationService.finishOperation()
    }

    fun getTrashMedia(): Flow<List<MediaData>> {
        if (galleryState?.isMockMode == true) {
            return galleryState.refreshTriggerFlow().map {
                ensureMockDataInitialized()
                val deletedUris = mockMetadata.filter { it.value.isDeleted }.keys
                mockMediaList.filter { it.uri in deletedUris }
            }
        }
        return mediaDao.getDeletedMetadataSummaryFlow().map { metadataList ->
            val deletedUris = metadataList.map { it.uri }.toSet()
            if (deletedUris.isEmpty()) return@map emptyList<MediaData>()

            val allMedia = getAllMediaFiltered(forceRefresh = true, includeDeleted = true)
            allMedia.filter { it.uri in deletedUris }
        }
    }

    suspend fun toggleFavorite(uri: String) {
        if (galleryState?.isMockMode == true) {
            val current = getMetadata(uri)
            mockMetadata[uri] = current?.copy(isFavorite = !(current?.isFavorite ?: false))
                ?: MediaMetadataEntity(uri, isFavorite = true)
        } else {
            val current = getMetadata(uri)
            mediaDao.updateFavorite(uri, !(current?.isFavorite ?: false))
        }
    }

    suspend fun bulkUpdateFavorite(uris: List<String>, isFavorite: Boolean) {
        if (galleryState?.isMockMode == true) {
            uris.forEach { uri ->
                val current = getMetadata(uri)
                mockMetadata[uri] = current?.copy(isFavorite = isFavorite)
                    ?: MediaMetadataEntity(uri, isFavorite = isFavorite)
            }
        } else {
            mediaDao.bulkUpdateFavorite(uris, isFavorite)
        }
    }

    suspend fun bulkUpdateAgeRating(uris: List<String>, ageRating: String?) {
        if (ageRating == null) return
        if (galleryState?.isMockMode == true) {
            uris.forEach { uri ->
                val current = getMetadata(uri)
                mockMetadata[uri] = current?.copy(ageRating = ageRating)
                    ?: MediaMetadataEntity(uri, ageRating = ageRating)
            }
        } else {
            // 大量のアイテムを処理する場合を考慮して、一括で必要な情報を取得
            val allMedia = if (uris.size > 1) getAllMedia() else null
            uris.forEach { uri ->
                val current = mediaDao.getMetadata(uri)
                if (current == null) {
                    // メタデータが存在しない場合は新規作成（フォルダ名が必要）
                    val folderName = (allMedia ?: getAllMedia()).find { it.uri == uri }?.folderName ?: ""
                    mediaDao.insertMetadata(
                        MediaMetadataEntity(
                            uri = uri,
                            ageRating = ageRating,
                            folderName = folderName,
                            isAiAnalyzed = false
                        )
                    )
                } else {
                    mediaDao.bulkUpdateAgeRating(listOf(uri), ageRating)
                }
            }
        }
    }

    suspend fun bulkAddTags(uris: List<String>, tags: List<String>) {
        uris.forEach { uri ->
            tags.forEach { tag ->
                saveTag(TagEntity(uri, tag, confidence = 1.0f)) // 手動タグは 1.0f
            }
        }
    }

    suspend fun saveMetadata(entity: MediaMetadataEntity) {
        if (galleryState?.isMockMode == true) {
            mockMetadata[entity.uri] = entity
        } else {
            mediaDao.insertMetadata(entity)
        }
    }

    suspend fun updateAiAnalysisResult(uri: String, ageRating: String, isAiAnalyzed: Boolean) {
        if (galleryState?.isMockMode == true) {
            val current = mockMetadata[uri]
            mockMetadata[uri] = (current ?: MediaMetadataEntity(uri)).copy(ageRating = ageRating, isAiAnalyzed = isAiAnalyzed)
        } else {
            val current = mediaDao.getMetadata(uri)
            if (current == null) {
                // MediaDataからフォルダ名を取得
                val allMedia = getAllMedia()
                val folderName = allMedia.find { it.uri == uri }?.folderName ?: ""
                mediaDao.insertMetadata(MediaMetadataEntity(uri, ageRating = ageRating, isAiAnalyzed = isAiAnalyzed, folderName = folderName))
            } else {
                mediaDao.updateAiAnalysisResult(uri, ageRating, isAiAnalyzed)
            }
        }
    }

    suspend fun updateFeatureVector(uri: String, featureVector: FloatArray) {
        if (galleryState?.isMockMode == true) {
            val current = mockMetadata[uri]
            mockMetadata[uri] = (current ?: MediaMetadataEntity(uri)).copy(featureVector = featureVector)
        } else {
            val current = mediaDao.getMetadata(uri)
            if (current == null) {
                val allMedia = getAllMedia()
                val folderName = allMedia.find { it.uri == uri }?.folderName ?: ""
                mediaDao.insertMetadata(MediaMetadataEntity(uri, featureVector = featureVector, folderName = folderName))
            } else {
                mediaDao.updateFeatureVector(uri, featureVector)
            }
        }
    }

    suspend fun saveTag(tag: TagEntity) {
        if (galleryState?.isMockMode == true) {
            if (mockTags.none { it.uri == tag.uri && it.tag == tag.tag }) {
                mockTags.add(tag)
            }
        } else {
            mediaDao.insertTag(tag)
        }
    }

    suspend fun moveMediaToFolder(uris: List<String>, targetFolder: String): Boolean {
        return withContext(Dispatchers.IO) {
            GlobalOperationService.startOperation("フォルダへ移動中: $targetFolder")
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
                    GlobalOperationService.updateProgress((index + 1).toFloat() / uris.size)
                } catch (e: Exception) {
                    Log.e("MediaRepository", "Error moving media: $uriString", e)
                }
            }

            if (movedPaths.isNotEmpty()) {
                MediaScannerConnection.scanFile(context, movedPaths.toTypedArray(), null, null)
            }
            cachedMediaList = null
            GlobalOperationService.finishOperation()
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

    fun getAllTagNames(): Flow<List<String>> {
        if (galleryState?.isMockMode == true) {
            ensureMockDataInitialized()
            return kotlinx.coroutines.flow.flowOf(mockTags.map { it.tag }.distinct())
        }
        return mediaDao.getAllTags()
    }

    fun getAllTagsWithCounts(): Flow<List<com.example.gallery.data.local.entity.TagCount>> {
        if (galleryState?.isMockMode == true) {
            return galleryState.refreshTriggerFlow().map {
                ensureMockDataInitialized()
                val counts = mockTags.groupingBy { it.tag }.eachCount()
                counts.map { com.example.gallery.data.local.entity.TagCount(it.key, it.value) }.sortedByDescending { it.count }
            }
        }
        return mediaDao.getAllTagsWithCounts().map { list -> list.sortedByDescending { it.count } }
    }

    fun getCountForTag(tag: String): Flow<Int> {
        if (galleryState?.isMockMode == true) {
            ensureMockDataInitialized()
            return kotlinx.coroutines.flow.flowOf(mockTags.count { it.tag == tag })
        }
        return mediaDao.getCountForTag(tag)
    }

    fun getThumbnailForTag(tag: String): Flow<String?> {
        if (galleryState?.isMockMode == true) {
            ensureMockDataInitialized()
            return kotlinx.coroutines.flow.flowOf(mockTags.find { it.tag == tag }?.uri)
        }
        return mediaDao.getThumbnailForTag(tag)
    }

    fun getFavoriteMedia(): Flow<List<MediaData>> {
        if (galleryState?.isMockMode == true) {
            return galleryState.refreshTriggerFlow().map {
                ensureMockDataInitialized()
                val favUris = mockMetadata.filter { it.value.isFavorite }.keys
                mockMediaList.filter { it.uri in favUris }
            }
        }
        return mediaDao.getFavoriteUris().map { uris ->
            val uriSet = uris.toSet()
            getAllMedia().filter { it.uri in uriSet }
        }
    }

    fun getMediaForTag(tag: String): Flow<List<MediaData>> {
        if (galleryState?.isMockMode == true) {
            ensureMockDataInitialized()
            val uris = mockTags.filter { it.tag == tag }.map { it.uri }.toSet()
            return kotlinx.coroutines.flow.flowOf(mockMediaList.filter { it.uri in uris })
        }
        return mediaDao.getMediaForTag(tag).map { entities ->
            val uriSet = entities.map { it.uri }.toSet()
            getAllMedia().filter { it.uri in uriSet }
        }
    }

    suspend fun getMediaForTags(tags: List<String>, ageRating: String? = null): List<MediaData> {
        if (tags.isEmpty()) return emptyList()
        if (galleryState?.isMockMode == true) {
            ensureMockDataInitialized()
            val uris = mockTags.filter { it.tag in tags }.map { it.uri }.toSet()
            var results = mockMediaList.filter { it.uri in uris }
            if (ageRating != null) results = results.filter { mockMetadata[it.uri]?.ageRating == ageRating }
            return results
        }

        val allMedia = getAllMedia()
        val allMediaMap = allMedia.associateBy { it.uri }
        val uris = mutableSetOf<String>()
        tags.forEach { tag ->
            mediaDao.getMediaForTag(tag).first().forEach { uris.add(it.uri) }
        }

        var results = uris.mapNotNull { allMediaMap[it] }
        if (ageRating != null) {
            val metadataMap = getAllMetadataSummary().associateBy { it.uri }
            results = results.filter { metadataMap[it.uri]?.ageRating == ageRating }
        }
        return results
    }

    fun getUntaggedCount(): Flow<Int> {
        if (galleryState?.isMockMode == true) {
            return galleryState.refreshTriggerFlow().map {
                ensureMockDataInitialized()
                val taggedUris = mockTags.map { it.uri }.toSet()
                mockMediaList.count { it.uri !in taggedUris && !it.isVideo }
            }
        }
        return mediaDao.getManualTaggedUrisFlow().map { taggedUris ->
            val taggedSet = taggedUris.toSet()
            val allMedia = getAllMedia()
            allMedia.count { it.uri !in taggedSet && !it.isVideo }
        }
    }

    fun getUntaggedMedia(): Flow<List<MediaData>> {
        if (galleryState?.isMockMode == true) {
            return galleryState.refreshTriggerFlow().map {
                ensureMockDataInitialized()
                val taggedUris = mockTags.map { it.uri }.toSet()
                mockMediaList.filter { it.uri !in taggedUris && !it.isVideo }
            }
        }
        return mediaDao.getManualTaggedUrisFlow().map { taggedUris ->
            val taggedSet = taggedUris.toSet()
            val allMedia = getAllMedia()
            allMedia.filter { it.uri !in taggedSet && !it.isVideo }
        }
    }

    fun getUnanalyzedAiCount(filter: AgeRatingFilter): Flow<Int> {
        if (galleryState?.isMockMode == true) {
            return galleryState.refreshTriggerFlow().map {
                ensureMockDataInitialized()
                val analyzedUris = mockMetadata.filter { it.value.isAiAnalyzed }.keys
                mockMediaList.count { item ->
                    if (item.isVideo || item.uri in analyzedUris) return@count false
                    val rating = mockMetadata[item.uri]?.ageRating ?: "SFW"
                    when (filter) {
                        AgeRatingFilter.ALL -> true
                        AgeRatingFilter.SFW -> rating == "SFW"
                        AgeRatingFilter.R15 -> rating == "R15"
                        AgeRatingFilter.R18 -> rating == "R18"
                    }
                }
            }
        }
        return getAllMetadataSummaryFlow().map { metadata ->
            val metaMap = metadata.associateBy { it.uri }
            val analyzedUris = metadata.filter { it.isAiAnalyzed }.map { it.uri }.toSet()
            val allMedia = getAllMedia()
            allMedia.count { item ->
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
    }

    suspend fun getMetadata(uri: String): MediaMetadataEntity? {
        if (galleryState?.isMockMode == true) {
            ensureMockDataInitialized()
            return mockMetadata[uri]
        }
        return mediaDao.getMetadata(uri)
    }

    fun getRandomMedia(limit: Int, ageRating: String? = null): List<MediaData> {
        val allMedia = if (galleryState?.isMockMode == true) mockMediaList else (cachedMediaList ?: emptyList())
        if (allMedia.isEmpty()) return emptyList()
        return allMedia.shuffled().take(limit)
    }

    suspend fun getRandomMediaByAgeRating(limit: Int, ageRating: String): List<MediaData> {
        val allMedia = getAllMedia()
        val allMetadata = getAllMetadataSummary().associateBy { it.uri }
        return allMedia.filter { 
            val rating = allMetadata[it.uri]?.ageRating ?: "SFW"
            rating == ageRating 
        }
            .shuffled()
            .take(limit)
    }

    suspend fun getAllMetadata(): List<MediaMetadataEntity> {
        if (galleryState?.isMockMode == true) {
            ensureMockDataInitialized()
            return mockMetadata.values.toList()
        }
        return mediaDao.getAllMetadata()
    }

    suspend fun getAllMetadataSummary(): List<MediaMetadataSummary> {
        if (galleryState?.isMockMode == true) {
            ensureMockDataInitialized()
            return mockMetadata.values.map { 
                MediaMetadataSummary(it.uri, it.isFavorite, it.ageRating, it.isAiAnalyzed, it.folderName, it.isDeleted, it.deletedDate, it.featureVector != null) 
            }
        }
        return mediaDao.getAllMetadataSummary()
    }

    fun getAllMetadataFlow(): Flow<List<MediaMetadataEntity>> {
        if (galleryState?.isMockMode == true) {
            return galleryState.refreshTriggerFlow().map {
                ensureMockDataInitialized()
                mockMetadata.values.toList()
            }
        }
        return mediaDao.getAllMetadataFlow()
    }

    fun getAllMetadataSummaryFlow(): Flow<List<MediaMetadataSummary>> {
        if (galleryState?.isMockMode == true) {
            return galleryState.refreshTriggerFlow().map {
                ensureMockDataInitialized()
                mockMetadata.values.map { 
                    MediaMetadataSummary(it.uri, it.isFavorite, it.ageRating, it.isAiAnalyzed, it.folderName, it.isDeleted, it.deletedDate, it.featureVector != null)
                }
            }
        }
        return mediaDao.getAllMetadataSummaryFlow()
    }

    fun getAllTagsWithUris(): Flow<List<TagEntity>> {
        if (galleryState?.isMockMode == true) {
            return galleryState.refreshTriggerFlow().map {
                ensureMockDataInitialized()
                mockTags.toList()
            }
        }
        return mediaDao.getAllTagsWithUris()
    }

    fun getAllFolderGroups(): Flow<List<com.example.gallery.data.local.entity.FolderGroupEntity>> = mediaDao.getAllFolderGroups()
    fun getAllFolderGroupMembers(): Flow<List<com.example.gallery.data.local.entity.FolderGroupMemberEntity>> = mediaDao.getAllFolderGroupMembers()

    suspend fun createFolderGroup(name: String) = mediaDao.insertFolderGroup(com.example.gallery.data.local.entity.FolderGroupEntity(name))

    suspend fun deleteFolderGroup(name: String) {
        mediaDao.deleteFolderGroupMembers(name)
        mediaDao.deleteFolderGroup(name)
    }

    suspend fun addFolderToGroup(folderName: String, groupName: String) {
        if (folderName == "group:$groupName") return
        if (folderName.startsWith("group:")) {
            val targetAsChildId = "group:$groupName"
            val movingGroupName = folderName.removePrefix("group:")
            val members = mediaDao.getAllFolderGroupMembers().first()
            if (members.any { it.groupName == movingGroupName && it.folderName == targetAsChildId }) {
                mediaDao.removeFolderFromAllGroups(targetAsChildId)
            }
        }
        mediaDao.removeFolderFromAllGroups(folderName)
        mediaDao.insertFolderGroupMember(com.example.gallery.data.local.entity.FolderGroupMemberEntity(groupName, folderName))
    }

    suspend fun removeFolderFromGroup(folderName: String) = mediaDao.removeFolderFromAllGroups(folderName)

    fun getAllFolderOrders(): Flow<List<com.example.gallery.data.local.entity.FolderOrderEntity>> = mediaDao.getAllFolderOrders()

    suspend fun updateFolderOrders(orders: List<com.example.gallery.data.local.entity.FolderOrderEntity>) = orders.forEach { mediaDao.insertFolderOrder(it) }

    fun getAllManagedFolderNames(): Flow<List<String>> = mediaDao.getAllManagedFolderNames()

    suspend fun addManagedFolder(name: String) = mediaDao.insertManagedFolder(com.example.gallery.data.local.entity.ManagedFolderEntity(name))

    fun scanAllFolders(): List<String> {
        val folders = mutableSetOf<String>()
        try {
            val dcim = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM)
            dcim.listFiles()?.forEach { file -> if (file.isDirectory) folders.add(file.name) }
        } catch (e: Exception) { Log.e("MediaRepository", "Error scanning folders", e) }
        return folders.toList().sorted()
    }

    fun getTagsForMedia(uri: String): Flow<List<TagEntity>> {
        if (galleryState?.isMockMode == true) {
            ensureMockDataInitialized()
            return galleryState.refreshTriggerFlow().map { mockTags.filter { it.uri == uri } }
        }
        return mediaDao.getTagsForMedia(uri)
    }

    fun getManualTaggedUrisFlow(): Flow<List<String>> = mediaDao.getManualTaggedUrisFlow()

    suspend fun findMediaByTagSimilarity(uri: String): List<MediaSimilarity> = withContext(Dispatchers.Default) {
        val targetTags = getTagsForMedia(uri).first().filter { !it.tag.endsWith("系") && it.confidence >= 0.6f }.map { it.tag }.toSet()
        if (targetTags.isEmpty()) return@withContext emptyList()

        val allMetadata = getAllMetadataSummary().associateBy { it.uri }
        
        // 全てのタグ情報を取得
        val allTags = mediaDao.getAllTagsWithUris().first()
            .filter { it.uri != uri && it.tag in targetTags }
            .groupBy { it.uri }

        val allMediaMap = getAllMedia().associateBy { it.uri }

        allTags.mapNotNull { (resUri, tags) ->
            val media = allMediaMap[resUri] ?: return@mapNotNull null
            
            // 一致率の計算: (一致したタグ数 / ターゲットのタグ総数)
            val score = tags.size.toFloat() / targetTags.size.toFloat()
            if (score < 0.2f) return@mapNotNull null // さらに閾値を下げて確実に表示させる
            
            MediaSimilarity(media, score)
        }.sortedByDescending { it.similarityScore }.take(25)
    }

    data class MediaSimilarity(val media: MediaData, val similarityScore: Float)

    suspend fun findSimilarVisualMedia(uri: String): List<MediaSimilarity> = withContext(Dispatchers.Default) {
        val targetMetadata = getMetadata(uri) ?: return@withContext emptyList()
        val targetVector = targetMetadata.featureVector ?: return@withContext emptyList()

        val allVectors = if (galleryState?.isMockMode == true) {
            mockMetadata.values
                .filter { it.uri != uri && it.featureVector != null }
                .map { com.example.gallery.data.local.entity.MediaVector(it.uri, it.featureVector!!) }
        } else {
            mediaDao.getAllVectors().filter { it.uri != uri }
        }
        val allMediaMap = getAllMedia().associateBy { it.uri }

        allVectors.asSequence()
            .map { meta ->
                val score = VectorSearchService.cosineSimilarity(targetVector, meta.featureVector)
                meta.uri to score
            }
            .filter { it.second > 0.5f } // 閾値を少し下げて 50% 以上にする
            .sortedByDescending { it.second }
            .take(25)
            .mapNotNull { (resUri, score) -> allMediaMap[resUri]?.let { MediaSimilarity(it, score) } }
            .toList()
    }
}

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
import com.example.gallery.ui.state.AgeRatingFilter
import com.example.gallery.ui.state.GalleryState
import com.example.gallery.service.GlobalOperationService
import com.example.gallery.data.service.VectorSearchService
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
    val galleryState: GalleryState? = null
) {
    private var cachedMediaList: List<MediaData>? = null
    private var lastCacheTime: Long = 0
    private val cacheMutex = Mutex()

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

            val allMetadata = mediaDao.getAllMetadataSummary()
            val deletedUris = allMetadata.filter { it.isDeleted }.map { it.uri }.toSet()
            
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
                            mediaList.ensureCapacity(mediaList.size + cursor.count)
                            
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
                                val id = cursor.getLong(idColumn)
                                val contentUri = ContentUris.withAppendedId(collection, id).toString()
                                if (!foundUris.add(contentUri)) continue
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

            mediaList.sortByDescending { it.dateAdded }

            if (forceRefresh || cachedMediaList == null) {
                val dbUrisToDelete = mutableListOf<String>()
                allMetadata.forEach { meta ->
                    val isMediaStoreUri = meta.uri.startsWith("content://media/external/")
                    if (isMediaStoreUri && !foundUris.contains(meta.uri)) {
                        dbUrisToDelete.add(meta.uri)
                    }
                }
                if (dbUrisToDelete.isNotEmpty()) {
                    dbUrisToDelete.chunked(100).forEach { chunk ->
                        mediaDao.bulkDeleteMetadata(chunk)
                        mediaDao.bulkDeleteTags(chunk)
                    }
                }
            }
            
            foundUris.clear()
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

    fun getAllFolderGroups(): Flow<List<com.example.gallery.data.local.entity.FolderGroupEntity>> = mediaDao.getAllFolderGroups()
    fun getAllFolderGroupMembers(): Flow<List<com.example.gallery.data.local.entity.FolderGroupMemberEntity>> = mediaDao.getAllFolderGroupMembers()
    suspend fun createFolderGroup(name: String) = mediaDao.insertFolderGroup(com.example.gallery.data.local.entity.FolderGroupEntity(name))
    suspend fun deleteFolderGroup(name: String) { mediaDao.deleteFolderGroupMembers(name); mediaDao.deleteFolderGroup(name) }
    suspend fun addFolderToGroup(folderName: String, groupName: String) {
        if (folderName == "group:$groupName") return
        mediaDao.removeFolderFromAllGroups(folderName)
        mediaDao.insertFolderGroupMember(com.example.gallery.data.local.entity.FolderGroupMemberEntity(groupName, folderName))
    }
    suspend fun removeFolderFromGroup(folderName: String) = mediaDao.removeFolderFromAllGroups(folderName)
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

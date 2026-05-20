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
import com.example.gallery.data.local.entity.TagEntity
import com.example.gallery.ui.MediaData
import com.example.gallery.ui.AgeRatingFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MediaRepository(
    private val context: Context,
    val mediaDao: MediaDao,
    private val galleryState: com.example.gallery.ui.GalleryState? = null
) {
    private val colorNames = listOf("レッド系", "オレンジ系", "イエロー系", "グリーン系", "ブルー系", "パープル系", "ピンク系", "ホワイト系", "グレー系", "ブラック系")
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
                    colorComposition = if (isAnalyzed) "{ \"${colorNames[i % colorNames.size]}\": 0.8 }" else null,
                    isFavorite = i % 12 == 0
                )
                
                // タグを追加
                mockTags.add(TagEntity(item.uri, colorNames[i % colorNames.size]))
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

    private suspend fun getAllMediaFiltered(forceRefresh: Boolean = false, includeDeleted: Boolean = false): List<MediaData> {
        if (galleryState?.isMockMode == true) {
            return mockMediaList
        }

        cacheMutex.withLock {
            val now = System.currentTimeMillis()
            if (!forceRefresh && !includeDeleted && cachedMediaList != null && now - lastCacheTime < 5000) {
                return cachedMediaList!!
            }

            val allGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }

            if (!allGranted) return emptyList()

            val mediaList = mutableListOf<MediaData>()
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
                                val date = cursor.getLong(dateColumn) * 1000
                                val mime = cursor.getString(mimeColumn)
                                val duration = if (durationColumn != -1) cursor.getLong(durationColumn) else 0L
                                val width = if (widthColumn != -1) cursor.getInt(widthColumn) else 0
                                val height = if (heightColumn != -1) cursor.getInt(heightColumn) else 0
                                val size = if (sizeColumn != -1) cursor.getLong(sizeColumn) else 0L
                                val name = if (nameColumn != -1) cursor.getString(nameColumn) ?: "" else ""
                                val path = if (dataColumn != -1) cursor.getString(dataColumn) else null
                                val folderName = if (path != null) File(path).parentFile?.name ?: "Unknown" else "Unknown"

                                val contentUri = ContentUris.withAppendedId(collection, id).toString()
                                mediaList.add(MediaData(contentUri, date, mime, duration, width, height, size, name, folderName))
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MediaRepository", "Error querying volume $volumeName: ${e.message}")
                    }
                }
            }

            val sorted = mediaList.distinctBy { it.uri }.sortedByDescending { it.dateAdded }
            val deletedUris = mediaDao.getDeletedMetadataFlow().first().map { it.uri }.toSet()

            val filtered = if (includeDeleted) {
                sorted.filter { it.uri in deletedUris }
            } else {
                sorted.filter { it.uri !in deletedUris }
            }

            if (!includeDeleted) {
                cachedMediaList = filtered
                lastCacheTime = now
            }
            return filtered
        }
    }

    suspend fun moveToTrash(uris: List<String>) {
        if (galleryState?.isMockMode == true) {
            uris.forEach { uri ->
                val current = getMetadata(uri)
                mockMetadata[uri] = current?.copy(isDeleted = true, deletedDate = System.currentTimeMillis()) 
                    ?: MediaMetadataEntity(uri, isDeleted = true, deletedDate = System.currentTimeMillis())
            }
        } else {
            mediaDao.bulkSetDeleted(uris, true, System.currentTimeMillis())
        }
        cachedMediaList = null
        galleryState?.refresh()
    }

    suspend fun restoreFromTrash(uris: List<String>) {
        if (galleryState?.isMockMode == true) {
            uris.forEach { uri ->
                val current = getMetadata(uri)
                mockMetadata[uri] = current?.copy(isDeleted = false, deletedDate = null)
                    ?: MediaMetadataEntity(uri, isDeleted = false, deletedDate = null)
            }
        } else {
            mediaDao.bulkSetDeleted(uris, false, null)
        }
        cachedMediaList = null
        galleryState?.refresh()
    }

    fun getTrashMedia(): Flow<List<MediaData>> {
        if (galleryState?.isMockMode == true) {
            return galleryState.refreshTriggerFlow().map {
                ensureMockDataInitialized()
                val deletedUris = mockMetadata.filter { it.value.isDeleted }.keys
                mockMediaList.filter { it.uri in deletedUris }
            }
        }
        return mediaDao.getDeletedMetadataFlow().map { metadataList ->
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
            mediaDao.bulkUpdateAgeRating(uris, ageRating)
        }
    }

    suspend fun bulkAddTags(uris: List<String>, tags: List<String>) {
        uris.forEach { uri ->
            tags.forEach { tag ->
                saveTag(TagEntity(uri, tag))
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
            var totalSuccess = 0
            val movedPaths = mutableListOf<String>()

            uris.forEach { uriString ->
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
                } catch (e: Exception) {
                    Log.e("MediaRepository", "Error moving media: $uriString", e)
                }
            }

            if (movedPaths.isNotEmpty()) {
                MediaScannerConnection.scanFile(context, movedPaths.toTypedArray(), null, null)
            }
            cachedMediaList = null
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

    fun getAllColorTags(): Flow<Map<String, List<MediaData>>> {
        if (galleryState?.isMockMode == true) {
            return galleryState.refreshTriggerFlow().map {
                ensureMockDataInitialized()
                val resultMap = mutableMapOf<String, MutableList<MediaData>>()
                mockTags.forEach { entity ->
                    if (entity.tag.endsWith("系")) {
                        val list = resultMap.getOrPut(entity.tag) { mutableListOf() }
                        mockMediaList.find { it.uri == entity.uri }?.let { list.add(it) }
                    }
                }
                resultMap
            }
        }
        return mediaDao.getAllColorTags().map { entities ->
            val allMedia = getAllMedia().associateBy { it.uri }
            val resultMap = mutableMapOf<String, MutableList<MediaData>>()
            entities.forEach { entity ->
                allMedia[entity.uri]?.let { media ->
                    resultMap.getOrPut(entity.tag) { mutableListOf() }.add(media)
                }
            }
            resultMap
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
            val metadataMap = getAllMetadata().associateBy { it.uri }
            results = results.filter { metadataMap[it.uri]?.ageRating == ageRating }
        }
        return results
    }

    fun getUntaggedCount(): Flow<Int> {
        if (galleryState?.isMockMode == true) {
            return galleryState.refreshTriggerFlow().map {
                ensureMockDataInitialized()
                val taggedUris = mockTags.filter { !it.tag.endsWith("系") }.map { it.uri }.toSet()
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
                val taggedUris = mockTags.filter { !it.tag.endsWith("系") }.map { it.uri }.toSet()
                mockMediaList.filter { it.uri !in taggedUris && !it.isVideo }
            }
        }
        return mediaDao.getManualTaggedUrisFlow().map { taggedUris ->
            val taggedSet = taggedUris.toSet()
            val allMedia = getAllMedia()
            allMedia.filter { it.uri !in taggedSet && !it.isVideo }
        }
    }

    fun getUnanalyzedColorCount(filter: AgeRatingFilter): Flow<Int> {
        if (galleryState?.isMockMode == true) {
            return galleryState.refreshTriggerFlow().map {
                ensureMockDataInitialized()
                val analyzedUris = mockMetadata.filter { it.value.colorComposition != null }.keys
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
        return getAllMetadataFlow().map { metadata ->
            val metaMap = metadata.associateBy { it.uri }
            val analyzedUris = metadata.filter { it.colorComposition != null }.map { it.uri }.toSet()
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
        return getAllMetadataFlow().map { metadata ->
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
        val allMetadata = getAllMetadata().associateBy { it.uri }
        return allMedia.filter { allMetadata[it.uri]?.ageRating == ageRating }
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

    fun getAllMetadataFlow(): Flow<List<MediaMetadataEntity>> {
        if (galleryState?.isMockMode == true) {
            return galleryState.refreshTriggerFlow().map {
                ensureMockDataInitialized()
                mockMetadata.values.toList()
            }
        }
        return mediaDao.getAllMetadataFlow()
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

    fun getTagsForMedia(uri: String): Flow<List<String>> {
        if (galleryState?.isMockMode == true) {
            ensureMockDataInitialized()
            return galleryState.refreshTriggerFlow().map { mockTags.filter { it.uri == uri }.map { it.tag } }
        }
        return mediaDao.getTagsForMedia(uri).map { entities -> entities.map { it.tag } }
    }

    fun getManualTaggedUrisFlow(): Flow<List<String>> = mediaDao.getManualTaggedUrisFlow()

    data class MediaSimilarity(val media: MediaData, val similarityScore: Float)

    suspend fun findSimilarColorMedia(uri: String): List<MediaSimilarity> = withContext(Dispatchers.Default) {
        val targetMetadata = getMetadata(uri) ?: return@withContext emptyList()
        val targetCompositionJson = targetMetadata.colorComposition ?: return@withContext emptyList()
        val targetComp = parseComposition(targetCompositionJson)
        val targetAgeRating = targetMetadata.ageRating

        val allMetadataList = getAllMetadata().filter { it.uri != uri && it.colorComposition != null && it.ageRating == targetAgeRating }
        val allMediaMap = getAllMedia().associateBy { it.uri }

        allMetadataList.asSequence()
            .map { meta ->
                val comp = parseComposition(meta.colorComposition!!)
                val distance = calculateDistance(targetComp, comp)
                val score = (1.0f - (distance / 2.0f)).coerceIn(0f, 1f)
                meta.uri to score
            }
            .filter { it.second > 0.5f }
            .sortedByDescending { it.second }
            .take(10)
            .mapNotNull { (resUri, score) -> allMediaMap[resUri]?.let { MediaSimilarity(it, score) } }
            .toList()
    }

    private fun parseComposition(jsonStr: String): Map<String, Float> {
        val json = JSONObject(jsonStr)
        val map = mutableMapOf<String, Float>()
        json.keys().forEach { key -> map[key] = json.getDouble(key).toFloat() }
        return map
    }

    private fun calculateDistance(c1: Map<String, Float>, c2: Map<String, Float>): Float {
        var distance = 0f
        val allKeys = c1.keys + c2.keys
        allKeys.forEach { key -> distance += Math.abs((c1[key] ?: 0f) - (c2[key] ?: 0f)) }
        return distance
    }

    suspend fun findSimilarVisualMedia(uri: String): List<MediaSimilarity> = withContext(Dispatchers.Default) {
        val targetMetadata = getMetadata(uri) ?: return@withContext emptyList()
        val targetVector = targetMetadata.featureVector ?: return@withContext emptyList()
        val targetAgeRating = targetMetadata.ageRating

        val allMetadataList = getAllMetadata().filter { 
            it.uri != uri && it.featureVector != null && it.ageRating == targetAgeRating 
        }
        val allMediaMap = getAllMedia().associateBy { it.uri }

        allMetadataList.asSequence()
            .map { meta ->
                val score = VectorSearchService.cosineSimilarity(targetVector, meta.featureVector!!)
                meta.uri to score
            }
            .filter { it.second > 0.6f } // 類似度 60% 以上
            .sortedByDescending { it.second }
            .take(20)
            .mapNotNull { (resUri, score) -> allMediaMap[resUri]?.let { MediaSimilarity(it, score) } }
            .toList()
    }
}

package com.example.gallery.data.repository

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.gallery.data.local.dao.MediaDao
import com.example.gallery.data.local.entity.MediaMetadataEntity
import com.example.gallery.data.local.entity.TagEntity
import com.example.gallery.ui.MediaData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MediaRepository(
    private val context: Context,
    val mediaDao: MediaDao,
    private val galleryState: com.example.gallery.ui.GalleryState? = null
) {
    private val mockMediaList: List<MediaData> by lazy {
        (1..100).map { i ->
            val isPortrait = i % 3 == 0
            MediaData(
                uri = "mock://image_$i",
                dateAdded = System.currentTimeMillis() - (i * 3600000L),
                mimeType = "image/jpeg",
                width = if (isPortrait) 1080 else 1920,
                height = if (isPortrait) 1920 else 1080
            )
        }
    }

    private val colorNames = listOf("レッド系", "オレンジ系", "イエロー系", "グリーン系", "ブルー系", "パープル系", "ピンク系", "ホワイト系", "グレー系", "ブラック系")
    private val tagNames = listOf("人物", "美少女", "風景", "動物", "食べ物", "建物", "空", "海", "花")

    private val mockMetadataMap: Map<String, MediaMetadataEntity> by lazy {
        mockMediaList.associate { item ->
            val i = item.uri.substringAfterLast("_").toInt()
            item.uri to MediaMetadataEntity(
                uri = item.uri,
                ageRating = when {
                    i % 7 == 0 -> "R18"
                    i % 5 == 0 -> "R15"
                    else -> "SFW"
                },
                isAiAnalyzed = true,
                colorComposition = "{ \"${colorNames[i % colorNames.size]}\": 0.8 }",
                isFavorite = i % 12 == 0
            )
        }
    }

    private val mockTagsMap: Map<String, List<String>> by lazy {
        mockMediaList.associate { item ->
            val i = item.uri.substringAfterLast("_").toInt()
            item.uri to listOf(
                colorNames[i % colorNames.size],
                tagNames[i % tagNames.size],
                tagNames[(i + 3) % tagNames.size],
                "テスト済"
            ).distinct()
        }
    }

    private var cachedMediaList: List<MediaData>? = null
    private var lastCacheTime: Long = 0
    private val cacheMutex = Mutex()

    suspend fun getAllMedia(forceRefresh: Boolean = false): List<MediaData> {
        if (galleryState?.isMockMode == true) {
            return mockMediaList
        }
        
        cacheMutex.withLock {
            val now = System.currentTimeMillis()
            if (!forceRefresh && cachedMediaList != null && now - lastCacheTime < 5000) {
                return cachedMediaList!!
            }

            val allGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }

            if (!allGranted) {
                Log.e("MediaRepository", "Permissions not granted for media access")
                return emptyList()
            }

            val mediaList = mutableListOf<MediaData>()
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DURATION,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT
            )
            val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

            val collections = listOf(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            )

            collections.forEach { collection ->
                context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                    val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                    val durationColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
                    val widthColumn = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                    val heightColumn = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val date = cursor.getLong(dateColumn) * 1000
                        val mime = cursor.getString(mimeColumn)
                        val duration = if (durationColumn != -1) cursor.getLong(durationColumn) else 0L
                        val width = if (widthColumn != -1) cursor.getInt(widthColumn) else 0
                        val height = if (heightColumn != -1) cursor.getInt(heightColumn) else 0
                        val contentUri = ContentUris.withAppendedId(collection, id).toString()
                        mediaList.add(MediaData(contentUri, date, mime, duration, width, height))
                    }
                }
            }
            val sorted = mediaList.sortedByDescending { it.dateAdded }
            cachedMediaList = sorted
            lastCacheTime = now
            return sorted
        }
    }

    suspend fun toggleFavorite(uri: String) {
        val current = getMetadata(uri)
        mediaDao.insertMetadata(
            MediaMetadataEntity(
                uri = uri,
                isFavorite = !(current?.isFavorite ?: false),
                colorComposition = current?.colorComposition,
                ageRating = current?.ageRating ?: "SFW",
                isAiAnalyzed = current?.isAiAnalyzed ?: false
            )
        )
    }

    suspend fun bulkUpdateFavorite(uris: List<String>, isFavorite: Boolean) {
        uris.forEach { uri ->
            val current = getMetadata(uri)
            mediaDao.insertMetadata(
                MediaMetadataEntity(
                    uri = uri,
                    isFavorite = isFavorite,
                    colorComposition = current?.colorComposition,
                    ageRating = current?.ageRating ?: "SFW",
                    isAiAnalyzed = current?.isAiAnalyzed ?: false
                )
            )
        }
    }

    suspend fun bulkUpdateAgeRating(uris: List<String>, ageRating: String) {
        uris.forEach { uri ->
            val current = getMetadata(uri)
            mediaDao.insertMetadata(
                MediaMetadataEntity(
                    uri = uri,
                    isFavorite = current?.isFavorite ?: false,
                    colorComposition = current?.colorComposition,
                    ageRating = ageRating,
                    isAiAnalyzed = current?.isAiAnalyzed ?: false
                )
            )
        }
    }

    suspend fun bulkAddTags(uris: List<String>, tags: List<String>) {
        uris.forEach { uri ->
            tags.forEach { tag ->
                mediaDao.insertTag(TagEntity(uri, tag))
            }
        }
    }

    fun getAllTagNames(): Flow<List<String>> {
        if (galleryState?.isMockMode == true) {
            return kotlinx.coroutines.flow.flowOf(mockTagsMap.values.flatten().distinct())
        }
        return mediaDao.getAllTags()
    }

    fun getAllTagsWithCounts(): Flow<List<com.example.gallery.data.local.entity.TagCount>> {
        if (galleryState?.isMockMode == true) {
            val counts = mockTagsMap.values.flatten().groupingBy { it }.eachCount()
            return kotlinx.coroutines.flow.flowOf(counts.map { com.example.gallery.data.local.entity.TagCount(it.key, it.value) }.sortedByDescending { it.count })
        }
        return mediaDao.getAllTagsWithCounts().map { list -> list.sortedByDescending { it.count } }
    }

    fun getCountForTag(tag: String): Flow<Int> {
        if (galleryState?.isMockMode == true) {
            return kotlinx.coroutines.flow.flowOf(mockTagsMap.values.count { it.contains(tag) })
        }
        return mediaDao.getCountForTag(tag)
    }

    fun getThumbnailForTag(tag: String): Flow<String?> {
        if (galleryState?.isMockMode == true) {
            return kotlinx.coroutines.flow.flowOf(mockTagsMap.entries.find { it.value.contains(tag) }?.key)
        }
        return mediaDao.getThumbnailForTag(tag)
    }

    fun getFavoriteMedia(): Flow<List<MediaData>> {
        if (galleryState?.isMockMode == true) {
            return kotlinx.coroutines.flow.flowOf(mockMediaList.filter { mockMetadataMap[it.uri]?.isFavorite == true })
        }
        return mediaDao.getFavoriteUris().map { uris ->
            val uriSet = uris.toSet()
            getAllMedia().filter { it.uri in uriSet }
        }
    }

    fun getMediaForTag(tag: String): Flow<List<MediaData>> {
        if (galleryState?.isMockMode == true) {
            val uris = mockTagsMap.filter { it.value.contains(tag) }.keys
            return kotlinx.coroutines.flow.flowOf(mockMediaList.filter { it.uri in uris })
        }
        return mediaDao.getMediaForTag(tag).map { entities ->
            val uriSet = entities.map { it.uri }.toSet()
            getAllMedia().filter { it.uri in uriSet }
        }
    }

    suspend fun getMediaForTags(tags: List<String>): List<MediaData> {
        if (tags.isEmpty()) return emptyList()
        val allMedia = getAllMedia()
        val allMediaMap = allMedia.associateBy { it.uri }
        val uris = mutableSetOf<String>()
        tags.forEach { tag ->
            mediaDao.getMediaForTag(tag).first().forEach { uris.add(it.uri) }
        }
        return uris.mapNotNull { allMediaMap[it] }
    }

    fun getUntaggedCount(): Flow<Int> {
        if (galleryState?.isMockMode == true) return kotlinx.coroutines.flow.flowOf(0)
        return mediaDao.getManualTaggedUrisFlow().map { taggedUris ->
            val taggedSet = taggedUris.toSet()
            val allMedia = getAllMedia()
            val untaggedImages = allMedia.filter { it.uri !in taggedSet && !it.isVideo }
            untaggedImages.size
        }
    }

    fun getUntaggedMedia(): Flow<List<MediaData>> {
        if (galleryState?.isMockMode == true) return kotlinx.coroutines.flow.flowOf(emptyList())
        return mediaDao.getManualTaggedUrisFlow().map { taggedUris ->
            val taggedSet = taggedUris.toSet()
            val allMedia = getAllMedia()
            allMedia.filter { it.uri !in taggedSet && !it.isVideo }
        }
    }

    fun getUnanalyzedColorCount(): Flow<Int> {
        if (galleryState?.isMockMode == true) return kotlinx.coroutines.flow.flowOf(0)
        return getAllMetadataFlow().map { metadata ->
            val analyzedUris = metadata.filter { it.colorComposition != null }.map { it.uri }.toSet()
            val allMedia = getAllMedia()
            allMedia.count { !it.isVideo && it.uri !in analyzedUris }
        }
    }

    fun getUnanalyzedAiCount(): Flow<Int> {
        if (galleryState?.isMockMode == true) return kotlinx.coroutines.flow.flowOf(0)
        return getAllMetadataFlow().map { metadata ->
            val analyzedUris = metadata.filter { it.isAiAnalyzed }.map { it.uri }.toSet()
            val allMedia = getAllMedia()
            allMedia.count { !it.isVideo && it.uri !in analyzedUris }
        }
    }

    suspend fun getMetadata(uri: String): MediaMetadataEntity? {
        if (galleryState?.isMockMode == true && uri.startsWith("mock://")) {
            return mockMetadataMap[uri]
        }
        return mediaDao.getMetadata(uri)
    }

    fun getRandomMedia(limit: Int): List<MediaData> {
        val allMedia = try {
             // ライフサイクル外で呼ばれる可能性があるので、Blockingは避けるべきだが、
             // ここでは既存の同期シグネチャに従う。
             // 本来はsuspendにするか、cachedMediaListを直接見るべき。
             cachedMediaList ?: emptyList()
        } catch(e: Exception) { emptyList() }

        return if (allMedia.isEmpty()) emptyList() else allMedia.shuffled().take(limit)
    }

    suspend fun getAllMetadata(): List<MediaMetadataEntity> {
        if (galleryState?.isMockMode == true) {
            return mockMetadataMap.values.toList()
        }
        return mediaDao.getAllMetadata()
    }

    fun getAllMetadataFlow(): Flow<List<MediaMetadataEntity>> {
        if (galleryState?.isMockMode == true) {
            return kotlinx.coroutines.flow.flowOf(mockMetadataMap.values.toList())
        }
        return mediaDao.getAllMetadataFlow()
    }

    fun getTagsForMedia(uri: String): Flow<List<String>> = mediaDao.getTagsForMedia(uri).map { entities -> entities.map { it.tag } }

    fun getManualTaggedUrisFlow(): Flow<List<String>> = mediaDao.getManualTaggedUrisFlow()

    data class MediaSimilarity(val media: MediaData, val similarityScore: Float)

    suspend fun findSimilarColorMedia(uri: String): List<MediaSimilarity> {
        val targetMetadata = getMetadata(uri) ?: return emptyList()
        val targetCompositionJson = targetMetadata.colorComposition ?: return emptyList()
        val targetComp = parseComposition(targetCompositionJson)

        val allMetadata = getAllMetadata().filter { it.uri != uri && it.colorComposition != null }
        val allMedia = getAllMedia().associateBy { it.uri }

        return allMetadata.asSequence()
            .map { meta ->
                val comp = parseComposition(meta.colorComposition!!)
                val distance = calculateDistance(targetComp, comp)
                val score = (1.0f - (distance / 2.0f)).coerceIn(0f, 1f)
                meta.uri to score
            }
            .filter { it.second > 0.5f }
            .sortedByDescending { it.second }
            .take(10)
            .mapNotNull { (uri, score) -> allMedia[uri]?.let { MediaSimilarity(it, score) } }
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
        allKeys.forEach { key ->
            val v1 = c1[key] ?: 0f
            val v2 = c2[key] ?: 0f
            distance += Math.abs(v1 - v2)
        }
        return distance
    }
}

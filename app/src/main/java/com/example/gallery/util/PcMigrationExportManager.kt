package com.example.gallery.util

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.room.withTransaction
import com.example.gallery.data.local.GalleryDatabase
import com.example.gallery.data.local.entity.MediaMetadataEntity
import com.example.gallery.data.local.entity.ReferenceItemEntity
import com.example.gallery.data.local.entity.ReferenceProjectEntity
import com.example.gallery.data.local.entity.TagEntity
import com.example.gallery.data.local.entity.VideoDownloadEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.net.URLConnection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Read-only exporter for moving Gallery's Android data to GalleryWeb on Windows.
 *
 * This is intentionally separate from [GalleryBackupManager]. The legacy backup
 * remains compatible with existing Android restore flows, while this manager
 * writes the versioned, checksummed ZIP contract consumed by the PC importer.
 *
 * The archive never contains media bytes. Optional hashes stream media only to
 * calculate a fingerprint; they do not copy those bytes into the ZIP.
 */
object PcMigrationExportManager {
    private val settingsPreferenceNames = listOf(
        "global_settings",
        "app_prefs",
        "media_viewer_settings",
        "book_viewer_settings",
        "video_viewer_settings"
    )
    private val favoritePreferenceNames = listOf(
        "favorite_artists",
        "favorite_sites_prefs",
        "book_favorites"
    )
    private const val BOOKMARKS_PREFERENCES = "book_bookmarks"
    private const val MIGRATION_FOLDER = "Gallery/Migrations"
    private const val DATABASE_VERSION = 19

    /**
     * Hashes improve matching after a file copy changes timestamps, but can make
     * exports of large libraries take substantially longer.
     */
    data class Options(
        val includeFeatureVectors: Boolean = true,
        val includeMediaHashes: Boolean = false
    )

    data class Result(
        val formatVersion: Int,
        val destination: String,
        val recordCounts: Map<String, Int>,
        val checksums: Map<String, String>
    )

    fun suggestedArchiveName(exportedAtEpochMillis: Long = System.currentTimeMillis()): String {
        val formatter = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).apply {
            timeZone = TimeZone.getDefault()
        }
        return "pixvault-migration-${formatter.format(Date(exportedAtEpochMillis))}.zip"
    }

    /**
     * Creates a new archive in app-scoped Documents/Gallery/Migrations.
     * No storage permission is required for this location.
     */
    suspend fun exportToDefaultFile(
        context: Context,
        options: Options = Options()
    ): Result = withContext(Dispatchers.IO) {
        val exportedAt = System.currentTimeMillis()
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: File(context.filesDir, Environment.DIRECTORY_DOCUMENTS)
        val directory = File(base, MIGRATION_FOLDER).apply {
            check(exists() || mkdirs()) { "Unable to create migration directory: $absolutePath" }
        }
        val destination = uniqueFile(directory, suggestedArchiveName(exportedAt))
        exportToFileAtTime(context, destination, options, exportedAt)
    }

    /**
     * Writes to a caller-selected Storage Access Framework URI. The URI should
     * come from ACTION_CREATE_DOCUMENT so replacement policy remains user-owned.
     */
    suspend fun exportToUri(
        context: Context,
        destination: Uri,
        options: Options = Options()
    ): Result = withContext(Dispatchers.IO) {
        val exportedAt = System.currentTimeMillis()
        val payload = collectPayload(context.applicationContext, options, exportedAt)
        val output = openOutputStream(context.contentResolver, destination)
            ?: error("Unable to open migration destination: $destination")
        val summary = output.use { PcMigrationArchiveWriter.write(payload, it) }
        Result(
            formatVersion = summary.formatVersion,
            destination = destination.toString(),
            recordCounts = summary.recordCounts,
            checksums = summary.checksums
        )
    }

    /**
     * Writes a new archive to [destination]. Existing files are never overwritten.
     */
    suspend fun exportToFile(
        context: Context,
        destination: File,
        options: Options = Options()
    ): Result = withContext(Dispatchers.IO) {
        exportToFileAtTime(context.applicationContext, destination, options, System.currentTimeMillis())
    }

    private suspend fun exportToFileAtTime(
        context: Context,
        destination: File,
        options: Options,
        exportedAt: Long
    ): Result {
        require(!destination.exists()) { "Migration archive already exists: ${destination.absolutePath}" }
        val parent = destination.parentFile ?: error("Migration destination has no parent directory")
        check(parent.exists() || parent.mkdirs()) { "Unable to create migration directory: ${parent.absolutePath}" }

        val payload = collectPayload(context.applicationContext, options, exportedAt)
        val partial = File(parent, ".${destination.name}.${System.nanoTime()}.partial")
        return try {
            val summary = partial.outputStream().use { PcMigrationArchiveWriter.write(payload, it) }
            check(partial.renameTo(destination)) {
                "Unable to finalize migration archive: ${destination.absolutePath}"
            }
            Result(
                formatVersion = summary.formatVersion,
                destination = destination.absolutePath,
                recordCounts = summary.recordCounts,
                checksums = summary.checksums
            )
        } catch (error: Throwable) {
            partial.delete()
            throw error
        }
    }

    private suspend fun collectPayload(
        context: Context,
        options: Options,
        exportedAt: Long
    ): PcMigrationArchivePayload {
        val database = GalleryDatabase.getDatabase(context)
        val snapshot = database.withTransaction {
            val mediaDao = database.mediaDao()
            val referenceDao = database.referenceDao()
            DatabaseSnapshot(
                metadata = mediaDao.getAllMetadata(),
                tags = mediaDao.getAllTagsForPcMigration(),
                videoDownloads = mediaDao.getAllVideoDownloadsForPcMigration(),
                referenceProjects = referenceDao.getAllProjectsForPcMigration(),
                referenceItems = referenceDao.getAllItemsForPcMigration()
            )
        }

        val resolver = PortableMediaResolver(context.contentResolver)
        val metadataByUri = snapshot.metadata.associateBy(MediaMetadataEntity::uri)
        val mediaSources = linkedSetOf<String>().apply {
            addAll(metadataByUri.keys)
            addAll(snapshot.tags.map(TagEntity::uri))
            snapshot.videoDownloads.map(VideoDownloadEntity::savePath)
                .filterTo(this, ::looksLikeLocalResource)
            snapshot.referenceItems.mapNotNull(ReferenceItemEntity::localUri)
                .filterTo(this, ::looksLikeLocalResource)
        }

        val media = mediaSources.sorted().map { source ->
            val metadata = metadataByUri[source]
            val portable = resolver.resolve(source, metadata, options.includeMediaHashes)
            PcMigrationMediaRecord(
                exportId = PcMigrationIds.media(source),
                androidUri = source,
                relativePath = portable.relativePath,
                fileName = portable.fileName,
                fileSize = portable.fileSize,
                dateAdded = portable.dateAdded,
                dateModified = portable.dateModified,
                mimeType = portable.mimeType,
                duration = portable.duration,
                width = portable.width,
                height = portable.height,
                sha256 = portable.sha256,
                favorite = metadata?.isFavorite ?: false,
                ageRating = metadata?.ageRating ?: "SFW",
                aiAnalyzed = metadata?.isAiAnalyzed ?: false,
                aiModel = metadata?.aiAnalysisModel.orEmpty(),
                featureVector = metadata?.featureVector
                    ?.takeIf { options.includeFeatureVectors && it.all(Float::isFinite) }
                    ?.toList(),
                deleted = metadata?.isDeleted ?: false,
                deletedDate = metadata?.deletedDate
            )
        }
        val mediaByUri = media.associateBy(PcMigrationMediaRecord::androidUri)

        val tags = snapshot.tags.map { tag ->
            PcMigrationTagRecord(
                exportId = PcMigrationIds.media(tag.uri),
                tag = tag.tag,
                confidence = tag.confidence.takeIf(Float::isFinite) ?: 0f
            )
        }

        val videoDownloads = snapshot.videoDownloads.map { download ->
            val matchedMedia = mediaByUri[download.savePath]
            val portable = matchedMedia?.toPortableInfo()
                ?: resolver.resolve(download.savePath, metadata = null, includeHash = options.includeMediaHashes)
                    .takeIf { looksLikeLocalResource(download.savePath) }
            PcMigrationVideoDownloadRecord(
                exportId = PcMigrationIds.videoDownload(download.url),
                sourceUrl = PcMigrationPrivacy.sanitizePublicUrl(download.url),
                title = download.title,
                androidSavePath = download.savePath,
                mediaExportId = matchedMedia?.exportId,
                relativePath = portable?.relativePath,
                fileName = portable?.fileName,
                fileSize = portable?.fileSize,
                dateModified = portable?.dateModified,
                mimeType = portable?.mimeType,
                sha256 = portable?.sha256,
                downloadDate = download.downloadDate,
                status = download.status
            )
        }

        val projectExportIds = snapshot.referenceProjects.associate { project ->
            project.id to PcMigrationIds.referenceProject(project.id)
        }
        val references = buildList<PcMigrationReferenceRecord> {
            snapshot.referenceProjects.forEach { project ->
                add(
                    PcMigrationReferenceRecord.Project(
                        exportId = projectExportIds.getValue(project.id),
                        title = project.title,
                        status = project.status,
                        createdAt = project.createdAt
                    )
                )
            }
            snapshot.referenceItems.forEach { item ->
                val projectExportId = projectExportIds[item.projectId]
                    // A malformed orphan is still exported deterministically and
                    // will be reported as invalid by the Windows dry-run.
                    ?: PcMigrationIds.referenceProject(item.projectId)
                add(
                    PcMigrationReferenceRecord.Item(
                        exportId = PcMigrationIds.referenceItem(item.id),
                        projectExportId = projectExportId,
                        localMediaExportId = item.localUri?.let(mediaByUri::get)?.exportId,
                        androidLocalUri = item.localUri,
                        remoteUrl = PcMigrationPrivacy.sanitizePublicUrl(item.remoteUrl),
                        title = item.title,
                        addedAt = item.addedAt
                    )
                )
            }
        }

        val bookmarks = readPreferenceValues(context, BOOKMARKS_PREFERENCES)
            .map { (bookmarkId, value) ->
                val parsed = parseBookmark(value)
                val file = bookmarkFile(bookmarkId)
                PcMigrationBookmarkRecord(
                    exportId = PcMigrationIds.bookmark(bookmarkId),
                    bookmarkId = bookmarkId,
                    value = value,
                    title = parsed.first,
                    page = parsed.second,
                    relativePath = file?.let {
                        PcMigrationPortablePath.build(it.absolutePath, it.name)
                    },
                    fileName = file?.name,
                    fileSize = file?.takeIf(File::isFile)?.length(),
                    dateModified = file?.takeIf(File::exists)?.lastModified()
                )
            }

        val packageInfo = @Suppress("DEPRECATION") context.packageManager
            .getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        return PcMigrationArchivePayload(
            androidPackageName = context.packageName,
            androidAppVersion = packageInfo.versionName.orEmpty(),
            androidAppVersionCode = versionCode,
            databaseVersion = DATABASE_VERSION,
            exportedAtEpochMillis = exportedAt,
            deviceTimezone = TimeZone.getDefault().id,
            settings = readPreferenceSnapshot(context, settingsPreferenceNames),
            favorites = readPreferenceSnapshot(context, favoritePreferenceNames),
            media = media,
            tags = tags,
            videoDownloads = videoDownloads,
            references = references,
            bookmarks = bookmarks
        )
    }

    private fun readPreferenceSnapshot(
        context: Context,
        names: List<String>
    ): PcMigrationPreferenceSnapshot = PcMigrationPreferenceSnapshot(
        namespaces = names.associateWith { name -> readPreferenceValues(context, name) }
    )

    private fun readPreferenceValues(
        context: Context,
        name: String
    ): Map<String, PcMigrationPreferenceValue> {
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        return preferences.all.entries
            .asSequence()
            .filterNot { (key, _) -> PcMigrationPrivacy.isSecretKey(key) }
            .mapNotNull { (key, value) -> typedPreference(value)?.let { key to it } }
            .sortedBy { (key, _) -> key }
            .toMap(linkedMapOf())
    }

    private fun typedPreference(value: Any?): PcMigrationPreferenceValue? = when (value) {
        is Boolean -> PcMigrationPreferenceValue(PcMigrationPreferenceType.BOOLEAN, value)
        is Int -> PcMigrationPreferenceValue(PcMigrationPreferenceType.INT, value)
        is Long -> PcMigrationPreferenceValue(PcMigrationPreferenceType.LONG, value)
        is Float -> value.takeIf(Float::isFinite)
            ?.let { PcMigrationPreferenceValue(PcMigrationPreferenceType.FLOAT, it) }
        is String -> PcMigrationPreferenceValue(PcMigrationPreferenceType.STRING, value)
        is Set<*> -> PcMigrationPreferenceValue(
            PcMigrationPreferenceType.STRING_SET,
            value.filterIsInstance<String>().sorted()
        )
        else -> null
    }

    private fun parseBookmark(value: PcMigrationPreferenceValue): Pair<String?, Int?> {
        val raw = value.value as? String ?: return null to null
        return runCatching {
            val json = JSONObject(raw)
            json.optString("title").takeIf(String::isNotBlank) to
                json.optInt("page").takeIf { json.has("page") }
        }.getOrDefault(null to null)
    }

    private fun bookmarkFile(bookmarkId: String): File? {
        val uri = runCatching { Uri.parse(bookmarkId) }.getOrNull()
        val path = when (uri?.scheme?.lowercase(Locale.ROOT)) {
            "file" -> uri.path
            null -> bookmarkId
            else -> null
        } ?: return null
        return File(path)
    }

    private fun openOutputStream(resolver: ContentResolver, uri: Uri): OutputStream? =
        runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(uri, "w")

    private fun uniqueFile(directory: File, preferredName: String): File {
        val preferred = File(directory, preferredName)
        if (!preferred.exists()) return preferred
        val base = preferredName.removeSuffix(".zip")
        generateSequence(2) { it + 1 }.forEach { suffix ->
            val candidate = File(directory, "$base-$suffix.zip")
            if (!candidate.exists()) return candidate
        }
        error("Unable to allocate migration archive name")
    }

    private fun looksLikeLocalResource(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.startsWith("content://", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true) ||
            trimmed.startsWith("/") ||
            Regex("^[A-Za-z]:[\\\\/]").containsMatchIn(trimmed)
    }

    private data class DatabaseSnapshot(
        val metadata: List<MediaMetadataEntity>,
        val tags: List<TagEntity>,
        val videoDownloads: List<VideoDownloadEntity>,
        val referenceProjects: List<ReferenceProjectEntity>,
        val referenceItems: List<ReferenceItemEntity>
    )

    private data class PortableInfo(
        val relativePath: String?,
        val fileName: String,
        val fileSize: Long,
        val dateAdded: Long,
        val dateModified: Long?,
        val mimeType: String?,
        val duration: Long,
        val width: Int,
        val height: Int,
        val sha256: String?
    )

    private fun PcMigrationMediaRecord.toPortableInfo(): PortableInfo = PortableInfo(
        relativePath = relativePath,
        fileName = fileName,
        fileSize = fileSize,
        dateAdded = dateAdded,
        dateModified = dateModified,
        mimeType = mimeType,
        duration = duration,
        width = width,
        height = height,
        sha256 = sha256
    )

    private class PortableMediaResolver(
        private val resolver: ContentResolver
    ) {
        fun resolve(
            source: String,
            metadata: MediaMetadataEntity?,
            includeHash: Boolean
        ): PortableInfo {
            val uri = runCatching { Uri.parse(source) }.getOrNull()
            val file = sourceFile(source, uri)
            val queried = uri
                ?.takeIf { it.scheme.equals("content", ignoreCase = true) }
                ?.let(::query)

            val fileName = queried?.fileName
                ?.takeIf(String::isNotBlank)
                ?: metadata?.fileName?.takeIf(String::isNotBlank)
                ?: file?.name?.takeIf(String::isNotBlank)
                ?: source.substringAfterLast('/').substringAfterLast('\\')
            val pathSource = queried?.relativeDirectory
                ?: queried?.dataPath
                ?: file?.absolutePath
                ?: metadata?.folderName
            val fileSize = queried?.fileSize
                ?.takeIf { it >= 0L }
                ?: metadata?.fileSize
                ?: file?.takeIf(File::isFile)?.length()
                ?: 0L
            val dateAdded = metadata?.dateAdded
                ?.takeIf { it > 0L }
                ?: queried?.dateAdded
                ?: file?.takeIf(File::exists)?.lastModified()
                ?: 0L
            val dateModified = queried?.dateModified
                ?: file?.takeIf(File::exists)?.lastModified()
                    ?.takeIf { it > 0L }
            val mimeType = queried?.mimeType
                ?: metadata?.mimeType
                ?: URLConnection.guessContentTypeFromName(fileName)
            val hash = if (includeHash) {
                openInput(source, uri, file)?.use(PcMigrationDigests::sha256)
            } else {
                null
            }

            return PortableInfo(
                relativePath = PcMigrationPortablePath.build(pathSource, fileName),
                fileName = fileName,
                fileSize = fileSize,
                dateAdded = dateAdded,
                dateModified = dateModified,
                mimeType = mimeType,
                duration = queried?.duration ?: metadata?.duration ?: 0L,
                width = queried?.width ?: metadata?.width ?: 0,
                height = queried?.height ?: metadata?.height ?: 0,
                sha256 = hash
            )
        }

        private fun query(uri: Uri): QueriedMedia? {
            val projection = buildList {
                add(MediaStore.MediaColumns.DISPLAY_NAME)
                add(MediaStore.MediaColumns.SIZE)
                add(MediaStore.MediaColumns.DATE_ADDED)
                add(MediaStore.MediaColumns.DATE_MODIFIED)
                add(MediaStore.MediaColumns.MIME_TYPE)
                add(MediaStore.MediaColumns.DURATION)
                add(MediaStore.MediaColumns.WIDTH)
                add(MediaStore.MediaColumns.HEIGHT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(MediaStore.MediaColumns.RELATIVE_PATH)
                } else {
                    @Suppress("DEPRECATION")
                    add(MediaStore.MediaColumns.DATA)
                }
            }.toTypedArray()
            return runCatching {
                resolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    QueriedMedia(
                        fileName = cursor.string(MediaStore.MediaColumns.DISPLAY_NAME),
                        fileSize = cursor.long(MediaStore.MediaColumns.SIZE),
                        dateAdded = cursor.epochSecondsAsMillis(MediaStore.MediaColumns.DATE_ADDED),
                        dateModified = cursor.epochSecondsAsMillis(MediaStore.MediaColumns.DATE_MODIFIED),
                        mimeType = cursor.string(MediaStore.MediaColumns.MIME_TYPE),
                        duration = cursor.long(MediaStore.MediaColumns.DURATION),
                        width = cursor.int(MediaStore.MediaColumns.WIDTH),
                        height = cursor.int(MediaStore.MediaColumns.HEIGHT),
                        relativeDirectory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            cursor.string(MediaStore.MediaColumns.RELATIVE_PATH)
                        } else {
                            null
                        },
                        dataPath = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            @Suppress("DEPRECATION")
                            cursor.string(MediaStore.MediaColumns.DATA)
                        } else {
                            null
                        }
                    )
                }
            }.getOrNull()
        }

        private fun openInput(source: String, uri: Uri?, file: File?) =
            runCatching {
                when {
                    uri?.scheme.equals("content", ignoreCase = true) -> resolver.openInputStream(uri!!)
                    file?.isFile == true -> file.inputStream()
                    else -> null
                }
            }.getOrNull()

        private fun sourceFile(source: String, uri: Uri?): File? {
            val path = when (uri?.scheme?.lowercase(Locale.ROOT)) {
                "file" -> uri.path
                null -> source
                else -> null
            } ?: return null
            return File(path)
        }

        private fun Cursor.string(column: String): String? =
            getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)

        private fun Cursor.long(column: String): Long? =
            getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)

        private fun Cursor.int(column: String): Int? =
            getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getInt)

        private fun Cursor.epochSecondsAsMillis(column: String): Long? =
            long(column)?.takeIf { it > 0L }?.let { seconds ->
                if (seconds > 10_000_000_000L) seconds else seconds * 1000L
            }

        private data class QueriedMedia(
            val fileName: String?,
            val fileSize: Long?,
            val dateAdded: Long?,
            val dateModified: Long?,
            val mimeType: String?,
            val duration: Long?,
            val width: Int?,
            val height: Int?,
            val relativeDirectory: String?,
            val dataPath: String?
        )
    }
}

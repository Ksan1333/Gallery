package com.example.gallery.util

import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Pure Kotlin/JVM representation of the Android -> Windows migration archive.
 *
 * This file intentionally has no Android or Room dependencies. Keeping archive
 * layout, JSON encoding, privacy filtering and checksums here makes the wire
 * format directly unit-testable without a device.
 */
internal const val PC_MIGRATION_FORMAT_VERSION = 1
internal const val PC_MIGRATION_HASH_ALGORITHM = "SHA-256"

internal enum class PcMigrationPreferenceType(val wireName: String) {
    BOOLEAN("boolean"),
    INT("int"),
    LONG("long"),
    FLOAT("float"),
    STRING("string"),
    STRING_SET("stringSet")
}

internal data class PcMigrationPreferenceValue(
    val type: PcMigrationPreferenceType,
    val value: Any
)

internal data class PcMigrationPreferenceSnapshot(
    val namespaces: Map<String, Map<String, PcMigrationPreferenceValue>>
)

internal data class PcMigrationMediaRecord(
    val exportId: String,
    val androidUri: String,
    val relativePath: String?,
    val fileName: String,
    val fileSize: Long,
    val dateAdded: Long,
    val dateModified: Long?,
    val mimeType: String?,
    val duration: Long,
    val width: Int,
    val height: Int,
    val sha256: String?,
    val favorite: Boolean,
    val ageRating: String,
    val aiAnalyzed: Boolean,
    val aiModel: String,
    val featureVector: List<Float>?,
    val deleted: Boolean,
    val deletedDate: Long?
)

internal data class PcMigrationTagRecord(
    /** ID of the corresponding record in media.jsonl. */
    val exportId: String,
    val tag: String,
    val confidence: Float
)

internal data class PcMigrationVideoDownloadRecord(
    val exportId: String,
    val sourceUrl: String,
    val title: String,
    val androidSavePath: String,
    val mediaExportId: String?,
    val relativePath: String?,
    val fileName: String?,
    val fileSize: Long?,
    val dateModified: Long?,
    val mimeType: String?,
    val sha256: String?,
    val downloadDate: Long,
    val status: String
)

internal sealed interface PcMigrationReferenceRecord {
    val exportId: String

    data class Project(
        override val exportId: String,
        val title: String,
        val status: String,
        val createdAt: Long
    ) : PcMigrationReferenceRecord

    data class Item(
        override val exportId: String,
        val projectExportId: String,
        val localMediaExportId: String?,
        val androidLocalUri: String?,
        val remoteUrl: String,
        val title: String,
        val addedAt: Long
    ) : PcMigrationReferenceRecord
}

internal data class PcMigrationBookmarkRecord(
    val exportId: String,
    val bookmarkId: String,
    val value: PcMigrationPreferenceValue,
    val title: String?,
    val page: Int?,
    val relativePath: String?,
    val fileName: String?,
    val fileSize: Long?,
    val dateModified: Long?
)

internal data class PcMigrationArchivePayload(
    val androidPackageName: String,
    val androidAppVersion: String,
    val androidAppVersionCode: Long,
    val databaseVersion: Int,
    val exportedAtEpochMillis: Long,
    val deviceTimezone: String,
    val settings: PcMigrationPreferenceSnapshot,
    val favorites: PcMigrationPreferenceSnapshot,
    val media: List<PcMigrationMediaRecord>,
    val tags: List<PcMigrationTagRecord>,
    val videoDownloads: List<PcMigrationVideoDownloadRecord>,
    val references: List<PcMigrationReferenceRecord>,
    val bookmarks: List<PcMigrationBookmarkRecord>
)

internal data class PcMigrationArchiveSummary(
    val formatVersion: Int,
    val recordCounts: Map<String, Int>,
    val checksums: Map<String, String>
)

internal object PcMigrationArchiveWriter {
    const val MANIFEST_ENTRY = "manifest.json"
    const val SETTINGS_ENTRY = "settings.json"
    const val FAVORITES_ENTRY = "favorites.json"
    const val MEDIA_ENTRY = "media.jsonl"
    const val TAGS_ENTRY = "tags.jsonl"
    const val VIDEO_DOWNLOADS_ENTRY = "video-downloads.jsonl"
    const val REFERENCES_ENTRY = "references.jsonl"
    const val BOOKMARKS_ENTRY = "bookmarks.jsonl"
    const val CHECKSUMS_ENTRY = "checksums.json"

    val requiredEntryNames: List<String> = listOf(
        MANIFEST_ENTRY,
        SETTINGS_ENTRY,
        FAVORITES_ENTRY,
        MEDIA_ENTRY,
        TAGS_ENTRY,
        VIDEO_DOWNLOADS_ENTRY,
        REFERENCES_ENTRY,
        BOOKMARKS_ENTRY,
        CHECKSUMS_ENTRY
    )

    /**
     * Writes and closes [output]. Archive entry order, JSON field order, record
     * order and ZIP entry timestamps are fixed for reproducible exports.
     */
    fun write(payload: PcMigrationArchivePayload, output: OutputStream): PcMigrationArchiveSummary {
        val dataEntries = linkedMapOf(
            SETTINGS_ENTRY to jsonFile(settingsDocument(payload.settings)),
            FAVORITES_ENTRY to jsonFile(favoritesDocument(payload.favorites)),
            MEDIA_ENTRY to jsonLines(payload.media.sortedBy { it.exportId }.map(::mediaDocument)),
            TAGS_ENTRY to jsonLines(
                payload.tags
                    .sortedWith(compareBy(PcMigrationTagRecord::exportId, PcMigrationTagRecord::tag, PcMigrationTagRecord::confidence))
                    .map(::tagDocument)
            ),
            VIDEO_DOWNLOADS_ENTRY to jsonLines(payload.videoDownloads.sortedBy { it.exportId }.map(::videoDownloadDocument)),
            REFERENCES_ENTRY to jsonLines(sortedReferences(payload.references).map(::referenceDocument)),
            BOOKMARKS_ENTRY to jsonLines(payload.bookmarks.sortedBy { it.exportId }.map(::bookmarkDocument))
        )

        val recordCounts = linkedMapOf(
            "media" to payload.media.size,
            "tags" to payload.tags.size,
            "videoDownloads" to payload.videoDownloads.size,
            "references" to payload.references.size,
            "bookmarks" to payload.bookmarks.size
        )
        val manifestBytes = jsonFile(manifestDocument(payload, recordCounts))

        // checksums.json deliberately never contains a checksum for itself.
        val checksummedEntries = linkedMapOf(MANIFEST_ENTRY to manifestBytes).apply {
            putAll(dataEntries)
        }
        val checksums = checksummedEntries.mapValuesTo(linkedMapOf()) { (_, bytes) ->
            PcMigrationDigests.sha256(bytes)
        }
        val checksumsBytes = jsonFile(
            mapOf(
                "formatVersion" to PC_MIGRATION_FORMAT_VERSION,
                "hashAlgorithm" to PC_MIGRATION_HASH_ALGORITHM,
                "files" to checksums
            )
        )

        val allEntries = linkedMapOf(MANIFEST_ENTRY to manifestBytes).apply {
            putAll(dataEntries)
            put(CHECKSUMS_ENTRY, checksumsBytes)
        }
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            requiredEntryNames.forEach { name ->
                val bytes = checkNotNull(allEntries[name]) { "Missing archive entry: $name" }
                val entry = ZipEntry(name).apply {
                    // DOS ZIP timestamps cannot represent the epoch and clamp this
                    // consistently. It also prevents wall-clock time from leaking
                    // into an otherwise identical archive.
                    time = 0L
                }
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
        }

        return PcMigrationArchiveSummary(
            formatVersion = PC_MIGRATION_FORMAT_VERSION,
            recordCounts = recordCounts,
            checksums = checksums
        )
    }

    private fun manifestDocument(
        payload: PcMigrationArchivePayload,
        recordCounts: Map<String, Int>
    ): Map<String, Any?> = mapOf(
        "formatVersion" to PC_MIGRATION_FORMAT_VERSION,
        "androidApp" to mapOf(
            "packageName" to payload.androidPackageName,
            "versionName" to payload.androidAppVersion,
            "versionCode" to payload.androidAppVersionCode
        ),
        "databaseVersion" to payload.databaseVersion,
        "exportedAt" to formatUtcTimestamp(payload.exportedAtEpochMillis),
        "exportedAtEpochMillis" to payload.exportedAtEpochMillis,
        "deviceTimezone" to payload.deviceTimezone,
        "recordCounts" to recordCounts,
        "hashAlgorithm" to PC_MIGRATION_HASH_ALGORITHM,
        "featureVectorsPresent" to payload.media.any { !it.featureVector.isNullOrEmpty() },
        "mediaHashesPresent" to payload.media.any { it.sha256 != null }
    )

    private fun settingsDocument(snapshot: PcMigrationPreferenceSnapshot): Map<String, Any?> = mapOf(
        "formatVersion" to PC_MIGRATION_FORMAT_VERSION,
        // "version" and "settings" intentionally mirror gallery_backup.json.
        "version" to 1,
        "settings" to preferenceValues(snapshot),
        "preferenceTypes" to preferenceTypes(snapshot)
    )

    private fun favoritesDocument(snapshot: PcMigrationPreferenceSnapshot): Map<String, Any?> {
        val values = preferenceValues(snapshot)
        return mapOf(
            "formatVersion" to PC_MIGRATION_FORMAT_VERSION,
            "version" to 1,
            "favorite_artists" to (values["favorite_artists"] ?: emptyMap<String, Any?>()),
            "favorite_sites" to (values["favorite_sites_prefs"] ?: emptyMap<String, Any?>()),
            "book_favorites" to (values["book_favorites"] ?: emptyMap<String, Any?>()),
            "preferenceTypes" to preferenceTypes(snapshot)
        )
    }

    private fun preferenceValues(snapshot: PcMigrationPreferenceSnapshot): Map<String, Map<String, Any>> =
        snapshot.namespaces.mapValues { (_, values) ->
            values.mapValues { (_, typed) -> typed.value }
        }

    private fun preferenceTypes(snapshot: PcMigrationPreferenceSnapshot): Map<String, Map<String, String>> =
        snapshot.namespaces.mapValues { (_, values) ->
            values.mapValues { (_, typed) -> typed.type.wireName }
        }

    private fun mediaDocument(record: PcMigrationMediaRecord): Map<String, Any?> = optionalMap(
        "exportId" to record.exportId,
        "androidUri" to record.androidUri,
        "relativePath" to record.relativePath,
        "fileName" to record.fileName,
        "fileSize" to record.fileSize,
        "dateAdded" to record.dateAdded,
        "dateModified" to record.dateModified,
        "mimeType" to record.mimeType,
        "duration" to record.duration,
        "width" to record.width,
        "height" to record.height,
        "sha256" to record.sha256,
        "favorite" to record.favorite,
        "ageRating" to record.ageRating,
        "aiAnalyzed" to record.aiAnalyzed,
        "aiModel" to record.aiModel,
        "featureVector" to record.featureVector,
        "deleted" to record.deleted,
        "deletedDate" to record.deletedDate
    )

    private fun tagDocument(record: PcMigrationTagRecord): Map<String, Any?> = mapOf(
        "exportId" to record.exportId,
        "tag" to record.tag,
        "confidence" to record.confidence,
        "source" to if (record.confidence >= 1f) "manual" else "ai"
    )

    private fun videoDownloadDocument(record: PcMigrationVideoDownloadRecord): Map<String, Any?> = optionalMap(
        "exportId" to record.exportId,
        "sourceUrl" to record.sourceUrl,
        "title" to record.title,
        "androidSavePath" to record.androidSavePath,
        "mediaExportId" to record.mediaExportId,
        "relativePath" to record.relativePath,
        "fileName" to record.fileName,
        "fileSize" to record.fileSize,
        "dateModified" to record.dateModified,
        "mimeType" to record.mimeType,
        "sha256" to record.sha256,
        "downloadDate" to record.downloadDate,
        "status" to record.status
    )

    private fun sortedReferences(records: List<PcMigrationReferenceRecord>): List<PcMigrationReferenceRecord> {
        val projects = records.filterIsInstance<PcMigrationReferenceRecord.Project>().sortedBy { it.exportId }
        val items = records.filterIsInstance<PcMigrationReferenceRecord.Item>()
            .sortedWith(compareBy(PcMigrationReferenceRecord.Item::projectExportId, PcMigrationReferenceRecord.Item::exportId))
        return projects + items
    }

    private fun referenceDocument(record: PcMigrationReferenceRecord): Map<String, Any?> = when (record) {
        is PcMigrationReferenceRecord.Project -> mapOf(
            "recordType" to "project",
            "exportId" to record.exportId,
            "title" to record.title,
            "status" to record.status,
            "createdAt" to record.createdAt
        )
        is PcMigrationReferenceRecord.Item -> optionalMap(
            "recordType" to "item",
            "exportId" to record.exportId,
            "projectExportId" to record.projectExportId,
            "localMediaExportId" to record.localMediaExportId,
            "androidLocalUri" to record.androidLocalUri,
            "remoteUrl" to record.remoteUrl,
            "title" to record.title,
            "addedAt" to record.addedAt
        )
    }

    private fun bookmarkDocument(record: PcMigrationBookmarkRecord): Map<String, Any?> = optionalMap(
        "exportId" to record.exportId,
        "bookmarkId" to record.bookmarkId,
        "value" to record.value.value,
        "valueType" to record.value.type.wireName,
        "title" to record.title,
        "page" to record.page,
        "relativePath" to record.relativePath,
        "fileName" to record.fileName,
        "fileSize" to record.fileSize,
        "dateModified" to record.dateModified
    )

    private fun optionalMap(vararg entries: Pair<String, Any?>): Map<String, Any?> =
        entries.filter { it.second != null }.toMap()

    private fun jsonFile(value: Any?): ByteArray =
        (PcMigrationJson.encode(value) + "\n").toByteArray(Charsets.UTF_8)

    private fun jsonLines(records: List<Map<String, Any?>>): ByteArray {
        if (records.isEmpty()) return ByteArray(0)
        return records.joinToString(separator = "\n", postfix = "\n", transform = PcMigrationJson::encode)
            .toByteArray(Charsets.UTF_8)
    }

    private fun formatUtcTimestamp(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(epochMillis))
}

internal object PcMigrationJson {
    fun encode(value: Any?): String = buildString { appendValue(value) }

    private fun StringBuilder.appendValue(value: Any?) {
        when (value) {
            null -> append("null")
            is Boolean -> append(if (value) "true" else "false")
            is Byte, is Short, is Int, is Long -> append(value.toString())
            is Float -> appendFloatingPoint(value.toDouble())
            is Double -> appendFloatingPoint(value)
            is String -> appendQuoted(value)
            is Map<*, *> -> {
                val entries = value.entries.map { entry ->
                    val key = entry.key as? String
                        ?: throw IllegalArgumentException("JSON object keys must be strings")
                    key to entry.value
                }.sortedBy { it.first }
                append('{')
                entries.forEachIndexed { index, (key, item) ->
                    if (index > 0) append(',')
                    appendQuoted(key)
                    append(':')
                    appendValue(item)
                }
                append('}')
            }
            is Iterable<*> -> appendArray(value)
            is Array<*> -> appendArray(value.asIterable())
            is FloatArray -> appendArray(value.asIterable())
            is DoubleArray -> appendArray(value.asIterable())
            is IntArray -> appendArray(value.asIterable())
            is LongArray -> appendArray(value.asIterable())
            else -> throw IllegalArgumentException("Unsupported JSON value: ${value::class.java.name}")
        }
    }

    private fun StringBuilder.appendFloatingPoint(value: Double) {
        require(value.isFinite()) { "JSON does not support non-finite numbers" }
        append(value.toString())
    }

    private fun StringBuilder.appendArray(values: Iterable<*>) {
        append('[')
        values.forEachIndexed { index, value ->
            if (index > 0) append(',')
            appendValue(value)
        }
        append(']')
    }

    private fun StringBuilder.appendQuoted(value: String) {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\u2028' -> append("\\u2028")
                '\u2029' -> append("\\u2029")
                else -> if (char.code < 0x20) {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                } else {
                    append(char)
                }
            }
        }
        append('"')
    }
}

internal object PcMigrationDigests {
    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance(PC_MIGRATION_HASH_ALGORITHM)
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance(PC_MIGRATION_HASH_ALGORITHM)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
        return digest.digest()
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}

internal object PcMigrationIds {
    fun media(androidUri: String): String = stable("media", androidUri)
    fun videoDownload(url: String): String = stable("video-download", url)
    fun referenceProject(id: Long): String = stable("reference-project", id.toString())
    fun referenceItem(id: Long): String = stable("reference-item", id.toString())
    fun bookmark(id: String): String = stable("bookmark", id)

    private fun stable(kind: String, source: String): String =
        UUID.nameUUIDFromBytes("pixvault:$kind:$source".toByteArray(Charsets.UTF_8)).toString()
}

internal object PcMigrationPortablePath {
    /**
     * Produces a slash-separated path relative to Android shared storage.
     * Absolute device/volume prefixes and traversal segments are never emitted.
     */
    fun build(pathOrDirectory: String?, fileName: String?): String? {
        val normalizedFileName = fileName
            ?.replace('\\', '/')
            ?.substringAfterLast('/')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val rawPath = pathOrDirectory
            ?.trim()
            ?.replace('\\', '/')
            ?.takeIf { it.isNotEmpty() }

        val stripped = rawPath?.let(::stripAndroidStoragePrefix).orEmpty()
        val safeSegments = mutableListOf<String>()
        stripped.split('/').forEach { segment ->
            when {
                segment.isBlank() || segment == "." -> Unit
                segment == ".." -> if (safeSegments.isNotEmpty()) safeSegments.removeAt(safeSegments.lastIndex)
                segment.endsWith(":") && safeSegments.isEmpty() -> Unit
                else -> safeSegments += segment
            }
        }
        if (normalizedFileName != null && safeSegments.lastOrNull() != normalizedFileName) {
            safeSegments += normalizedFileName
        }
        return safeSegments.joinToString("/").takeIf { it.isNotEmpty() }
    }

    private fun stripAndroidStoragePrefix(path: String): String {
        val withoutLeadingSlash = path.trimStart('/')
        val knownPrefixes = listOf(
            "storage/emulated/0/",
            "storage/self/primary/",
            "sdcard/",
            "mnt/sdcard/"
        )
        knownPrefixes.firstOrNull { withoutLeadingSlash.startsWith(it, ignoreCase = true) }
            ?.let { return withoutLeadingSlash.substring(it.length) }

        // Removable storage normally uses /storage/<volume-id>/...
        if (withoutLeadingSlash.startsWith("storage/", ignoreCase = true)) {
            return withoutLeadingSlash.substringAfter('/', "").substringAfter('/', "")
        }
        return withoutLeadingSlash
    }
}

internal object PcMigrationPrivacy {
    private val secretFragments = setOf(
        "password", "passwd", "token", "authtoken", "authorization", "oauth",
        "cookie", "session", "secret", "signature", "apikey", "credential", "jwt",
        "accesskey", "privatekey", "clientsecret",
        "androidid", "deviceid", "installationid", "advertisingid"
    )

    fun isSecretKey(key: String): Boolean {
        val normalized = key.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
        return secretFragments.any { fragment -> normalized.contains(fragment) }
    }

    /**
     * Keeps public HTTP(S) URLs while removing user-info and sensitive query or
     * fragment parameters. Malformed and non-public schemes are omitted.
     */
    fun sanitizePublicUrl(value: String): String {
        val trimmed = value.trim()
        val candidate = when {
            trimmed.startsWith("//") -> "https:$trimmed"
            "://" !in trimmed -> "https://$trimmed"
            else -> trimmed
        }
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return ""
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return ""
        val authority = uri.rawAuthority?.substringAfterLast('@') ?: return ""
        val query = filterParameters(uri.rawQuery)
        val fragment = filterParameters(uri.rawFragment, preservePlainFragment = true)
        return buildString {
            append(scheme)
            append("://")
            append(authority)
            append(uri.rawPath.orEmpty())
            if (!query.isNullOrEmpty()) append('?').append(query)
            if (!fragment.isNullOrEmpty()) append('#').append(fragment)
        }
    }

    private fun filterParameters(raw: String?, preservePlainFragment: Boolean = false): String? {
        if (raw.isNullOrEmpty()) return null
        if (preservePlainFragment && '=' !in raw && '&' !in raw) {
            return raw.takeUnless(::isSecretKey)
        }
        return raw.split('&')
            .filter { part ->
                val rawKey = part.substringBefore('=')
                !isSecretKey(rawKey)
            }
            .joinToString("&")
            .takeIf { it.isNotEmpty() }
    }
}

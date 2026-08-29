package com.example.gallery.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class PcMigrationArchiveTest {
    @Test
    fun archive_hasFixedShapeValidChecksumsAndDeterministicBytes() {
        val payload = samplePayload()

        val first = writeArchive(payload)
        val second = writeArchive(payload)

        assertArrayEquals(first, second)
        val entries = unzip(first)
        assertEquals(PcMigrationArchiveWriter.requiredEntryNames, entries.keys.toList())

        val checksumsJson = entries.getValue(PcMigrationArchiveWriter.CHECKSUMS_ENTRY)
            .toString(Charsets.UTF_8)
        PcMigrationArchiveWriter.requiredEntryNames
            .filterNot { it == PcMigrationArchiveWriter.CHECKSUMS_ENTRY }
            .forEach { name ->
                val expected = PcMigrationDigests.sha256(entries.getValue(name))
                assertTrue("Checksum missing for $name", checksumsJson.contains("\"$name\":\"$expected\""))
            }
        assertFalse(checksumsJson.contains("\"${PcMigrationArchiveWriter.CHECKSUMS_ENTRY}\":"))

        val manifest = entries.getValue(PcMigrationArchiveWriter.MANIFEST_ENTRY).toString(Charsets.UTF_8)
        assertTrue(manifest.contains("\"formatVersion\":1"))
        assertTrue(manifest.contains("\"media\":1"))
        assertTrue(manifest.contains("\"tags\":1"))
        assertTrue(manifest.contains("\"videoDownloads\":1"))
        assertTrue(manifest.contains("\"references\":2"))
        assertTrue(manifest.contains("\"bookmarks\":1"))
        assertTrue(manifest.contains("\"featureVectorsPresent\":true"))
        assertTrue(manifest.contains("\"mediaHashesPresent\":true"))
        assertFalse(manifest.contains("deviceId"))
    }

    @Test
    fun jsonLines_areStableSortedAndRelatedRecordsUseExportIds() {
        val entries = unzip(writeArchive(samplePayload()))
        val media = entries.getValue(PcMigrationArchiveWriter.MEDIA_ENTRY).toString(Charsets.UTF_8)
        val tags = entries.getValue(PcMigrationArchiveWriter.TAGS_ENTRY).toString(Charsets.UTF_8)
        val references = entries.getValue(PcMigrationArchiveWriter.REFERENCES_ENTRY).toString(Charsets.UTF_8)
        val bookmarks = entries.getValue(PcMigrationArchiveWriter.BOOKMARKS_ENTRY).toString(Charsets.UTF_8)

        assertTrue(media.contains("\"exportId\":\"media-id\""))
        assertTrue(media.contains("\"relativePath\":\"Pictures/Gallery/example.jpg\""))
        assertTrue(tags.contains("\"exportId\":\"media-id\""))
        assertFalse(tags.contains("content://"))

        val referenceLines = references.trim().lines()
        assertEquals(2, referenceLines.size)
        assertTrue(referenceLines[0].contains("\"recordType\":\"project\""))
        assertTrue(referenceLines[1].contains("\"projectExportId\":\"project-id\""))
        assertTrue(referenceLines[1].contains("\"localMediaExportId\":\"media-id\""))
        assertTrue(bookmarks.contains("\"valueType\":\"string\""))
    }

    @Test
    fun preferences_keepLegacyShapeAndExplicitTypeInformation() {
        val entries = unzip(writeArchive(samplePayload()))
        val settings = entries.getValue(PcMigrationArchiveWriter.SETTINGS_ENTRY).toString(Charsets.UTF_8)
        val favorites = entries.getValue(PcMigrationArchiveWriter.FAVORITES_ENTRY).toString(Charsets.UTF_8)

        assertTrue(settings.contains("\"settings\":{\"global_settings\""))
        assertTrue(settings.contains("\"showClock\":true"))
        assertTrue(settings.contains("\"history\":[\"a\",\"b\"]"))
        assertTrue(settings.contains("\"showClock\":\"boolean\""))
        assertTrue(settings.contains("\"history\":\"stringSet\""))
        assertTrue(favorites.contains("\"favorite_artists\""))
        assertTrue(favorites.contains("\"favorite_sites\""))
        assertTrue(favorites.contains("\"book_favorites\""))
    }

    @Test
    fun portablePath_removesDeviceRootsAndTraversal() {
        assertEquals(
            "Pictures/Gallery/example.jpg",
            PcMigrationPortablePath.build("/storage/emulated/0/Pictures/Gallery/", "example.jpg")
        )
        assertEquals(
            "DCIM/Camera/photo.jpg",
            PcMigrationPortablePath.build("/storage/ABCD-1234/DCIM/Camera/photo.jpg", "photo.jpg")
        )
        assertEquals(
            "Pictures/photo.jpg",
            PcMigrationPortablePath.build("../private/../Pictures", "photo.jpg")
        )
        assertEquals("photo.jpg", PcMigrationPortablePath.build(null, "photo.jpg"))
    }

    @Test
    fun privacyFilter_removesSecretsWithoutDamagingPublicXFragment() {
        assertTrue(PcMigrationPrivacy.isSecretKey("oauth_access_token"))
        assertTrue(PcMigrationPrivacy.isSecretKey("android_installation_id"))
        assertFalse(PcMigrationPrivacy.isSecretKey("theme_mode"))

        assertEquals(
            "https://x.com/user/status/1?quality=high#gallery-media=2",
            PcMigrationPrivacy.sanitizePublicUrl(
                "https://name:password@x.com/user/status/1?access_token=secret&quality=high#gallery-media=2"
            )
        )
        assertEquals("", PcMigrationPrivacy.sanitizePublicUrl("file:///data/private/item"))
    }

    @Test
    fun stableIds_areRepeatableAndNamespaced() {
        val first = PcMigrationIds.media("content://media/1")
        assertEquals(first, PcMigrationIds.media("content://media/1"))
        assertNotEquals(first, PcMigrationIds.bookmark("content://media/1"))
        assertNotEquals(first, PcMigrationIds.media("content://media/2"))
    }

    @Test
    fun jsonEncoder_sortsKeysAndEscapesControlCharacters() {
        assertEquals(
            "{\"a\":\"line\\nnext\",\"b\":2}",
            PcMigrationJson.encode(linkedMapOf("b" to 2, "a" to "line\nnext"))
        )
    }

    private fun writeArchive(payload: PcMigrationArchivePayload): ByteArray {
        val output = ByteArrayOutputStream()
        PcMigrationArchiveWriter.write(payload, output)
        return output.toByteArray()
    }

    private fun unzip(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun samplePayload(): PcMigrationArchivePayload = PcMigrationArchivePayload(
        androidPackageName = "com.example.gallery",
        androidAppVersion = "2.0.0",
        androidAppVersionCode = 11,
        databaseVersion = 19,
        exportedAtEpochMillis = 1_700_000_000_123,
        deviceTimezone = "Asia/Tokyo",
        settings = PcMigrationPreferenceSnapshot(
            mapOf(
                "global_settings" to mapOf(
                    "showClock" to PcMigrationPreferenceValue(PcMigrationPreferenceType.BOOLEAN, true),
                    "history" to PcMigrationPreferenceValue(
                        PcMigrationPreferenceType.STRING_SET,
                        listOf("a", "b")
                    )
                )
            )
        ),
        favorites = PcMigrationPreferenceSnapshot(
            mapOf(
                "favorite_artists" to mapOf(
                    "artists" to PcMigrationPreferenceValue(PcMigrationPreferenceType.STRING, "[]")
                ),
                "favorite_sites_prefs" to mapOf(
                    "favorite_sites" to PcMigrationPreferenceValue(PcMigrationPreferenceType.STRING, "[]")
                ),
                "book_favorites" to mapOf(
                    "/book.zip" to PcMigrationPreferenceValue(PcMigrationPreferenceType.BOOLEAN, true)
                )
            )
        ),
        media = listOf(
            PcMigrationMediaRecord(
                exportId = "media-id",
                androidUri = "content://media/external/images/media/1",
                relativePath = "Pictures/Gallery/example.jpg",
                fileName = "example.jpg",
                fileSize = 123,
                dateAdded = 1_700_000_000_000,
                dateModified = 1_700_000_001_000,
                mimeType = "image/jpeg",
                duration = 0,
                width = 1920,
                height = 1080,
                sha256 = "abc123",
                favorite = true,
                ageRating = "SFW",
                aiAnalyzed = true,
                aiModel = "model-1",
                featureVector = listOf(0.25f, 0.5f),
                deleted = false,
                deletedDate = null
            )
        ),
        tags = listOf(PcMigrationTagRecord("media-id", "landscape", 0.8f)),
        videoDownloads = listOf(
            PcMigrationVideoDownloadRecord(
                exportId = "download-id",
                sourceUrl = "https://x.com/user/status/1",
                title = "user / 1",
                androidSavePath = "content://media/external/video/media/2",
                mediaExportId = null,
                relativePath = "Movies/Gallery/video.mp4",
                fileName = "video.mp4",
                fileSize = 456,
                dateModified = 1_700_000_002_000,
                mimeType = "video/mp4",
                sha256 = null,
                downloadDate = 1_700_000_002_000,
                status = "COMPLETED"
            )
        ),
        references = listOf(
            PcMigrationReferenceRecord.Item(
                exportId = "item-id",
                projectExportId = "project-id",
                localMediaExportId = "media-id",
                androidLocalUri = "content://media/external/images/media/1",
                remoteUrl = "https://example.com/reference",
                title = "Reference",
                addedAt = 1_700_000_004_000
            ),
            PcMigrationReferenceRecord.Project(
                exportId = "project-id",
                title = "Project",
                status = "ACTIVE",
                createdAt = 1_700_000_003_000
            )
        ),
        bookmarks = listOf(
            PcMigrationBookmarkRecord(
                exportId = "bookmark-id",
                bookmarkId = "/storage/emulated/0/Books/book.zip",
                value = PcMigrationPreferenceValue(
                    PcMigrationPreferenceType.STRING,
                    "{\"title\":\"Book\",\"page\":3}"
                ),
                title = "Book",
                page = 3,
                relativePath = "Books/book.zip",
                fileName = "book.zip",
                fileSize = 789,
                dateModified = 1_700_000_005_000
            )
        )
    )
}

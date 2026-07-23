package com.example.gallery.util

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream

object GalleryBackupManager {
    private const val GLOBAL_SETTINGS_PREFS = "global_settings"
    private const val BOOK_VIEWER_PREFS = "book_viewer_settings"
    private const val VIDEO_VIEWER_PREFS = "video_viewer_settings"
    private const val MEDIA_VIEWER_PREFS = "media_viewer_settings"
    private const val APP_PREFS = "app_prefs"
    private const val FAVORITE_ARTISTS_PREFS = "favorite_artists"
    private const val FAVORITE_SITES_PREFS = "favorite_sites_prefs"
    private const val BACKUP_STATE_PREFS = "gallery_backup_state"
    private const val DEFAULT_BACKUP_URI_KEY = "default_backup_uri"
    private const val DEFAULT_BACKUP_NAME = "gallery_backup.json"
    private const val DEFAULT_BACKUP_RELATIVE_PATH = "Download/Gallery/Backups"
    private const val BACKUP_TRACE = "GalleryBackup"

    private val settingsPrefs = listOf(
        GLOBAL_SETTINGS_PREFS,
        APP_PREFS,
        MEDIA_VIEWER_PREFS,
        BOOK_VIEWER_PREFS,
        VIDEO_VIEWER_PREFS
    )

    fun backupFile(context: Context): File {
        return appScopedBackupFile(context)
    }

    private fun legacyPublicBackupFile(fileName: String): File {
        val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val folder = File(baseDir, "Gallery/Backups")
        return File(folder, fileName)
    }

    private fun appScopedBackupFile(context: Context): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: File(context.filesDir, Environment.DIRECTORY_DOCUMENTS)
        return File(File(baseDir, "Gallery/Backups"), "gallery_backup.json")
    }

    private fun readableBackupFile(context: Context): File {
        val legacyPublic = legacyPublicBackupFile(DEFAULT_BACKUP_NAME)
        return if (legacyPublic.exists()) legacyPublic else appScopedBackupFile(context)
    }

    private fun readBackupRoot(context: Context): JSONObject {
        defaultBackupUri(context)?.let { uri ->
            val root = runCatching { readBackupRootFromUri(context, uri) }
                .onFailure { error ->
                    Log.w(BACKUP_TRACE, "Default backup URI is no longer readable: $uri", error)
                }
                .getOrNull()
            if (root != null) return root
        }
        val file = readableBackupFile(context)
        require(file.exists()) { "Backup file not found: ${file.absolutePath}" }
        return parseBackupRoot(file.readText(Charsets.UTF_8), file.absolutePath)
    }

    private fun defaultBackupUri(context: Context): Uri? {
        val value = context.getSharedPreferences(BACKUP_STATE_PREFS, Context.MODE_PRIVATE)
            .getString(DEFAULT_BACKUP_URI_KEY, null)
            ?: return null
        return Uri.parse(value)
    }

    private fun readBackupRootFromUri(context: Context, uri: Uri): JSONObject {
        val content = context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: error("Unable to open backup file: $uri")
        Log.i(BACKUP_TRACE, "Backup imported from URI: $uri")
        return parseBackupRoot(content, uri.toString())
    }

    private fun parseBackupRoot(content: String, source: String): JSONObject {
        val trimmed = content.removePrefix("\uFEFF").trim()
        require(trimmed.isNotEmpty()) { "Backup file is empty: $source" }
        return runCatching { JSONObject(trimmed) }
            .getOrElse { error -> throw IllegalArgumentException("Invalid backup JSON: $source", error) }
    }

    fun exportAllToFile(context: Context): File {
        val root = createBackupRoot(context)
        val file = backupFile(context)
        file.parentFile?.mkdirs()
        file.writeText(root.toString(2), Charsets.UTF_8)
        return file
    }

    fun exportAllToDefaultLocation(context: Context): String {
        val content = createBackupRoot(context).toString(2)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val file = exportAllToFile(context)
            return file.absolutePath
        }

        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, DEFAULT_BACKUP_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, DEFAULT_BACKUP_RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create backup in Downloads")
        try {
            openBackupOutputStream(context, uri)
                ?.bufferedWriter(Charsets.UTF_8)
                ?.use { it.write(content) }
                ?: error("Unable to open backup destination: $uri")
            resolver.update(
                uri,
                android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                },
                null,
                null
            )
            context.getSharedPreferences(BACKUP_STATE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(DEFAULT_BACKUP_URI_KEY, uri.toString())
                .apply()
            Log.i(BACKUP_TRACE, "Backup exported to default URI: $uri")
            return uri.toString()
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            Log.e(BACKUP_TRACE, "Failed to export backup to default location", error)
            throw error
        }
    }

    fun exportAllToUri(context: Context, uri: Uri) {
        val content = createBackupRoot(context).toString(2)
        openBackupOutputStream(context, uri)
            ?.bufferedWriter(Charsets.UTF_8)
            ?.use { it.write(content) }
            ?: error("Unable to open backup destination: $uri")
        Log.i(BACKUP_TRACE, "Backup exported to selected URI: $uri")
    }

    private fun openBackupOutputStream(context: Context, uri: Uri): OutputStream? {
        return runCatching { context.contentResolver.openOutputStream(uri, "wt") }
            .getOrNull()
            ?: context.contentResolver.openOutputStream(uri, "w")
    }

    private fun createBackupRoot(context: Context): JSONObject {
        val root = JSONObject()
            .put("version", 1)
            .put("settings", JSONObject().apply {
                settingsPrefs.forEach { name ->
                    put(name, exportPrefs(context.getSharedPreferences(name, Context.MODE_PRIVATE)))
                }
            })
            .put("favorite_artists", exportPrefs(context.getSharedPreferences(FAVORITE_ARTISTS_PREFS, Context.MODE_PRIVATE)))
            .put("favorite_sites", exportPrefs(context.getSharedPreferences(FAVORITE_SITES_PREFS, Context.MODE_PRIVATE)))
        return root
    }

    fun importSettingsFromFile(context: Context) {
        importSettingsFromRoot(context, readBackupRoot(context))
    }

    fun importSettingsFromUri(context: Context, uri: Uri) {
        importSettingsFromRoot(context, readBackupRootFromUri(context, uri))
    }

    private fun importSettingsFromRoot(context: Context, root: JSONObject) {
        val settings = root.optJSONObject("settings") ?: root
        settingsPrefs.forEach { name ->
            settings.optJSONObject(name)?.let { json ->
                importPrefs(context.getSharedPreferences(name, Context.MODE_PRIVATE), json)
            }
        }
        root.optJSONObject("favorite_artists")?.let { json ->
            mergeFavoriteArtists(
                context.getSharedPreferences(FAVORITE_ARTISTS_PREFS, Context.MODE_PRIVATE),
                json
            )
        }
        root.optJSONObject("favorite_sites")?.let { json ->
            mergeFavoriteSites(
                context.getSharedPreferences(FAVORITE_SITES_PREFS, Context.MODE_PRIVATE),
                json
            )
        }
    }

    fun importFavoriteArtistsFromFile(context: Context) {
        val root = runCatching { readBackupRoot(context) }.getOrNull()
        val json = root?.optJSONObject("favorite_artists")
            ?: if (root != null && (root.has("artists") || root.has("custom_sites"))) {
                JSONObject().apply {
                    root.optJSONArray("artists")?.let { put("artists", it.toString()) }
                    root.optJSONArray("custom_sites")?.let { put("custom_sites", it.toString()) }
                }
            } else null
            ?: legacyPublicBackupFile("favorite_artists.json")
                .takeIf { it.exists() }
                ?.let { file ->
                    val legacy = JSONObject(file.readText(Charsets.UTF_8))
                    JSONObject().apply {
                        legacy.optJSONArray("artists")?.let { put("artists", it.toString()) }
                        legacy.optJSONArray("custom_sites")?.let { put("custom_sites", it.toString()) }
                    }
                }
        json?.let {
            mergeFavoriteArtists(context.getSharedPreferences(FAVORITE_ARTISTS_PREFS, Context.MODE_PRIVATE), it)
        } ?: error("Favorite artists backup file not found")
    }

    fun importFavoriteSitesFromFile(context: Context) {
        val root = runCatching { readBackupRoot(context) }.getOrNull()
        val json = root?.optJSONObject("favorite_sites")
            ?: legacyPublicBackupFile("favorite_sites.json")
                .takeIf { it.exists() }
                ?.let { file ->
                    JSONObject().put("favorite_sites", JSONArray(file.readText(Charsets.UTF_8)).toString())
                }
        json?.let {
            mergeFavoriteSites(context.getSharedPreferences(FAVORITE_SITES_PREFS, Context.MODE_PRIVATE), it)
        } ?: error("Favorite sites backup file not found")
    }

    private fun exportPrefs(prefs: SharedPreferences): JSONObject {
        val json = JSONObject()
        prefs.all.forEach { (key, value) ->
            when (value) {
                is Boolean -> json.put(key, value)
                is Int -> json.put(key, value)
                is Long -> json.put(key, value)
                is Float -> json.put(key, value.toDouble())
                is String -> json.put(key, value)
                is Set<*> -> json.put(key, JSONArray().apply { value.filterIsInstance<String>().forEach { item -> put(item) } })
            }
        }
        return json
    }

    private fun mergeFavoriteArtists(prefs: SharedPreferences, json: JSONObject) {
        val currentArtists = parseJsonArrayValue(prefs.getString("artists", "[]"))
        val importedArtists = parseJsonArrayValue(json.opt("artists"))
        val existingNames = mutableSetOf<String>()
        for (index in 0 until currentArtists.length()) {
            val name = currentArtists.optJSONObject(index)?.optString("name").orEmpty().normalizedKey()
            if (name.isNotEmpty()) existingNames.add(name)
        }
        for (index in 0 until importedArtists.length()) {
            val artist = importedArtists.optJSONObject(index) ?: continue
            val name = artist.optString("name").normalizedKey()
            if (name.isEmpty() || name in existingNames) continue
            currentArtists.put(artist)
            existingNames.add(name)
        }

        val currentSites = parseJsonArrayValue(prefs.getString("custom_sites", "[]"))
        val importedSites = parseJsonArrayValue(json.opt("custom_sites"))
        val existingSites = mutableSetOf<String>()
        for (index in 0 until currentSites.length()) {
            currentSites.optString(index).normalizedKey().takeIf { it.isNotEmpty() }?.let(existingSites::add)
        }
        for (index in 0 until importedSites.length()) {
            val site = importedSites.optString(index)
            val key = site.normalizedKey()
            if (key.isEmpty() || key in existingSites) continue
            currentSites.put(site)
            existingSites.add(key)
        }

        prefs.edit()
            .putString("artists", currentArtists.toString())
            .putString("custom_sites", currentSites.toString())
            .apply()
    }

    private fun mergeFavoriteSites(prefs: SharedPreferences, json: JSONObject) {
        val currentSites = parseJsonArrayValue(prefs.getString("favorite_sites", "[]"))
        val importedSites = parseJsonArrayValue(json.opt("favorite_sites"))
        val existingTitles = mutableSetOf<String>()
        val existingUrls = mutableSetOf<String>()
        for (index in 0 until currentSites.length()) {
            val site = currentSites.optJSONObject(index) ?: continue
            site.optString("name").normalizedKey().takeIf { it.isNotEmpty() }?.let(existingTitles::add)
            site.optString("url").normalizedUrlKey().takeIf { it.isNotEmpty() }?.let(existingUrls::add)
        }

        for (index in 0 until importedSites.length()) {
            val site = importedSites.optJSONObject(index) ?: continue
            val title = site.optString("name").normalizedKey()
            val url = site.optString("url").normalizedUrlKey()
            val isDuplicateTitle = title.isNotEmpty() && title in existingTitles
            val isDuplicateUrl = url.isNotEmpty() && url in existingUrls
            if (isDuplicateTitle || isDuplicateUrl) continue
            currentSites.put(site)
            if (title.isNotEmpty()) existingTitles.add(title)
            if (url.isNotEmpty()) existingUrls.add(url)
        }

        prefs.edit()
            .putString("favorite_sites", currentSites.toString())
            .apply()
    }

    private fun parseJsonArrayValue(value: Any?): JSONArray {
        return when (value) {
            is JSONArray -> value
            is String -> runCatching { JSONArray(value) }.getOrDefault(JSONArray())
            else -> JSONArray()
        }
    }

    private fun String.normalizedKey(): String = trim().lowercase()

    private fun String.normalizedUrlKey(): String {
        val trimmed = trim()
        val normalized = when {
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            "://" in trimmed -> trimmed
            trimmed.isNotEmpty() -> "https://$trimmed"
            else -> trimmed
        }
        return normalized
            .lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
    }

    private fun importPrefs(prefs: SharedPreferences, json: JSONObject) {
        val editor = prefs.edit().clear()
        json.keys().forEach { key ->
            when (val value = json.get(key)) {
                JSONObject.NULL -> Unit
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Double -> editor.putFloat(key, value.toFloat())
                is String -> editor.putString(key, value)
                is JSONArray -> {
                    val values = buildSet {
                        for (index in 0 until value.length()) {
                            val item = value.opt(index)
                            if (item is String) add(item)
                        }
                    }
                    editor.putStringSet(key, values)
                }
            }
        }
        editor.apply()
    }
}

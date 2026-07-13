package com.example.gallery.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object GalleryBackupManager {
    private const val GLOBAL_SETTINGS_PREFS = "global_settings"
    private const val BOOK_VIEWER_PREFS = "book_viewer_settings"
    private const val VIDEO_VIEWER_PREFS = "video_viewer_settings"
    private const val MEDIA_VIEWER_PREFS = "media_viewer_settings"
    private const val APP_PREFS = "app_prefs"
    private const val FAVORITE_ARTISTS_PREFS = "favorite_artists"
    private const val FAVORITE_SITES_PREFS = "favorite_sites_prefs"

    private val settingsPrefs = listOf(
        GLOBAL_SETTINGS_PREFS,
        APP_PREFS,
        MEDIA_VIEWER_PREFS,
        BOOK_VIEWER_PREFS,
        VIDEO_VIEWER_PREFS
    )

    fun backupFile(context: Context): File {
        return publicBackupFile("gallery_backup.json")
    }

    private fun publicBackupFile(fileName: String): File {
        val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val folder = File(baseDir, "Gallery/Backups")
        if (!folder.exists()) folder.mkdirs()
        return File(folder, fileName)
    }

    private fun appScopedBackupFile(context: Context): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: File(context.filesDir, Environment.DIRECTORY_DOCUMENTS)
        return File(File(baseDir, "Gallery/Backups"), "gallery_backup.json")
    }

    private fun readableBackupFile(context: Context): File {
        val primary = backupFile(context)
        return if (primary.exists()) primary else appScopedBackupFile(context)
    }

    private fun readBackupRoot(context: Context): JSONObject {
        val file = readableBackupFile(context)
        require(file.exists()) { "Backup file not found: ${file.absolutePath}" }
        val content = file.readText(Charsets.UTF_8).trim()
        require(content.isNotEmpty()) { "Backup file is empty: ${file.absolutePath}" }
        return JSONObject(content)
    }

    fun exportAllToFile(context: Context): File {
        val root = JSONObject()
            .put("version", 1)
            .put("settings", JSONObject().apply {
                settingsPrefs.forEach { name ->
                    put(name, exportPrefs(context.getSharedPreferences(name, Context.MODE_PRIVATE)))
                }
            })
            .put("favorite_artists", exportPrefs(context.getSharedPreferences(FAVORITE_ARTISTS_PREFS, Context.MODE_PRIVATE)))
            .put("favorite_sites", exportPrefs(context.getSharedPreferences(FAVORITE_SITES_PREFS, Context.MODE_PRIVATE)))
        val file = backupFile(context)
        file.writeText(root.toString(2), Charsets.UTF_8)
        return file
    }

    fun importSettingsFromFile(context: Context) {
        val root = readBackupRoot(context)
        val settings = root.optJSONObject("settings") ?: root
        settingsPrefs.forEach { name ->
            settings.optJSONObject(name)?.let { json ->
                importPrefs(context.getSharedPreferences(name, Context.MODE_PRIVATE), json)
            }
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
            ?: publicBackupFile("favorite_artists.json")
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
            ?: publicBackupFile("favorite_sites.json")
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

package com.example.gallery.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.gallery.BuildConfig
import com.example.gallery.MainActivity
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

data class AppUpdateRelease(
    val version: String,
    val title: String,
    val notes: String,
    val assetName: String,
    val assetUrl: String,
    val releaseUrl: String
)

object AppUpdateManager {
    const val EXTRA_OPEN_UPDATE = "com.example.gallery.extra.OPEN_UPDATE"
    private const val REPOSITORY = "Ksan1333/Gallery"
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$REPOSITORY/releases/latest"
    private const val PREFS = "app_update"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_NOTIFIED_VERSION = "notified_version"
    private const val CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
    private const val UPDATE_NOTIFICATION_ID = 7013
    private const val UPDATE_NOTIFICATION_CHANNEL = "app_updates"

    private val httpClient = OkHttpClient()

    fun cachedUpdate(context: Context): AppUpdateRelease? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val version = prefs.getString("version", null) ?: return null
        val assetUrl = prefs.getString("asset_url", null) ?: return null
        if (!isNewer(version, BuildConfig.VERSION_NAME)) {
            clearCachedUpdate(context)
            return null
        }
        return AppUpdateRelease(
            version = version,
            title = prefs.getString("title", version).orEmpty(),
            notes = prefs.getString("notes", "").orEmpty(),
            assetName = prefs.getString("asset_name", "update.apk").orEmpty(),
            assetUrl = assetUrl,
            releaseUrl = prefs.getString("release_url", "https://github.com/$REPOSITORY/releases").orEmpty()
        )
    }

    suspend fun checkForUpdate(context: Context, force: Boolean = false): AppUpdateRelease? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) {
            return cachedUpdate(context)
        }

        val release = fetchLatestRelease()
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
        val update = release?.takeIf { isNewer(it.version, BuildConfig.VERSION_NAME) }
        if (update == null) {
            clearCachedUpdate(context)
            return null
        }
        cacheUpdate(context, update)
        return update
    }

    suspend fun downloadApk(
        context: Context,
        release: AppUpdateRelease,
        onProgress: (Float) -> Unit = {}
    ): File {
        val request = Request.Builder()
            .url(release.assetUrl)
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "Gallery-Android-Updater")
            .build()
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(updatesDir, "gallery-${release.version}.apk")
        val partial = File(updatesDir, "gallery-${release.version}.apk.part")

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("Empty APK response")
                val totalBytes = body.contentLength()
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (totalBytes > 0L) {
                                onProgress((copied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
            if (target.exists()) target.delete()
            check(partial.renameTo(target)) { "Unable to finalize downloaded APK" }
            validateApk(context, target, release.version)
            onProgress(1f)
            return target
        } catch (error: Exception) {
            partial.delete()
            throw error
        }
    }

    /**
     * Opens the system setting used to allow APK installs from this app.
     * Returns false when the user must grant permission before the download can start.
     */
    fun requestInstallPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        return true
    }

    /** Opens Android's package installer. Android requires the user's confirmation for this final step. */
    fun installApk(context: Context, apk: File): Boolean {
        if (!requestInstallPermission(context)) return false

        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(apkUri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
        return true
    }

    /** A release is mandatory only when its semantic-version major number increases. */
    fun isMandatoryUpdate(candidateVersion: String, currentVersion: String = BuildConfig.VERSION_NAME): Boolean {
        val candidateMajor = versionParts(candidateVersion)?.firstOrNull() ?: return false
        val currentMajor = versionParts(currentVersion)?.firstOrNull() ?: return false
        return candidateMajor > currentMajor
    }

    fun shouldNotify(context: Context, release: AppUpdateRelease): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_NOTIFIED_VERSION, null) != release.version
    }

    fun markNotified(context: Context, version: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NOTIFIED_VERSION, version)
            .apply()
    }

    fun updateNotificationChannel(): String = UPDATE_NOTIFICATION_CHANNEL

    fun updateNotificationId(): Int = UPDATE_NOTIFICATION_ID

    fun updateNotificationIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_UPDATE, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    private fun fetchLatestRelease(): AppUpdateRelease? {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Gallery-Android-Updater")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) error("GitHub release check failed: HTTP ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            val assets = (0 until json.optJSONArray("assets")?.length().orZero())
                .map { index -> json.getJSONArray("assets").getJSONObject(index) }
            val asset = selectApkAsset(assets) ?: return null
            val version = normalizeVersion(json.optString("tag_name")) ?: return null
            return AppUpdateRelease(
                version = version,
                title = json.optString("name").ifBlank { version },
                notes = json.optString("body"),
                assetName = asset.optString("name"),
                assetUrl = asset.optString("browser_download_url"),
                releaseUrl = json.optString("html_url", "https://github.com/$REPOSITORY/releases")
            ).takeIf { it.assetUrl.isNotBlank() }
        }
    }

    private fun selectApkAsset(assets: List<JSONObject>): JSONObject? {
        val supportedAbis = Build.SUPPORTED_ABIS.map { it.lowercase() }
        return assets.asSequence()
            .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
            .maxByOrNull { asset ->
                val name = asset.optString("name").lowercase()
                val abiIndex = supportedAbis.indexOfFirst(name::contains)
                val abiScore = when {
                    abiIndex >= 0 -> 10_000 - abiIndex
                    name.contains("universal") -> 5_000
                    else -> 0
                }
                abiScore + if (name.contains("release")) 1 else 0
            }
    }

    private fun cacheUpdate(context: Context, release: AppUpdateRelease) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("version", release.version)
            .putString("title", release.title)
            .putString("notes", release.notes)
            .putString("asset_name", release.assetName)
            .putString("asset_url", release.assetUrl)
            .putString("release_url", release.releaseUrl)
            .apply()
    }

    private fun clearCachedUpdate(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("version")
            .remove("title")
            .remove("notes")
            .remove("asset_name")
            .remove("asset_url")
            .remove("release_url")
            .apply()
    }

    private fun validateApk(context: Context, apk: File, expectedVersion: String) {
        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            ?: error("Downloaded file is not an Android APK")
        check(packageInfo.packageName == context.packageName) {
            "APK package (${packageInfo.packageName}) does not match installed app (${context.packageName})"
        }
        check(isNewer(packageInfo.versionName.orEmpty(), BuildConfig.VERSION_NAME)) {
            "APK version is not newer than the installed app"
        }
        check(normalizeVersion(packageInfo.versionName.orEmpty()) == expectedVersion) {
            "APK version does not match the selected release"
        }
    }

    private fun isNewer(candidate: String, current: String): Boolean {
        val candidateParts = versionParts(candidate) ?: return false
        val currentParts = versionParts(current) ?: return false
        val maxSize = maxOf(candidateParts.size, currentParts.size)
        repeat(maxSize) { index ->
            val left = candidateParts.getOrElse(index) { 0 }
            val right = currentParts.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    private fun normalizeVersion(value: String): String? =
        versionParts(value)?.joinToString(".")

    private fun versionParts(value: String): List<Int>? {
        val normalized = value.trim().removePrefix("v").removePrefix("V")
        val match = Regex("^\\d+(?:\\.\\d+){0,3}").find(normalized)?.value ?: return null
        return match.split('.').map { it.toIntOrNull() ?: return null }
    }

    private fun Int?.orZero(): Int = this ?: 0
}

package com.example.gallery.util

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
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
import java.security.MessageDigest

data class AppUpdateRelease(
    val version: String,
    val title: String,
    val notes: String,
    val assetName: String,
    val assetUrl: String,
    val releaseUrl: String
)

class AppUpdateSignatureMismatchException(message: String) : IllegalStateException(message)

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
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        var lastReportedPercent = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (totalBytes > 0L) {
                                val progress = (copied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                val percent = (progress * 100).toInt()
                                if (percent != lastReportedPercent) {
                                    lastReportedPercent = percent
                                    onProgress(progress)
                                }
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
            target.delete()
            throw error
        }
    }

    /**
     * Opens the system setting used to allow APK installs from this app.
     * Returns false when the user must grant permission before the download can start.
     */
    fun requestInstallPermission(context: Context): Boolean {
        if (!hasInstallPermission(context)) {
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

    fun hasInstallPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /**
     * Builds an installer intent that reports cancellation/failure to an Activity Result launcher.
     * Android still requires the user's confirmation on the system installer screen.
     */
    @Suppress("DEPRECATION") // Required for a result-returning installer UI across the app's API 24+ range.
    fun createInstallIntent(context: Context, apk: File): Intent {
        validateSigningCompatibility(context, apk)
        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val installerIntents = listOf(
            Intent(Intent.ACTION_INSTALL_PACKAGE),
            // Some vendor package installers register ACTION_VIEW but not
            // ACTION_INSTALL_PACKAGE for content:// APKs.
            Intent(Intent.ACTION_VIEW)
        ).map { action ->
            Intent(action)
                .setDataAndType(apkUri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(Intent.EXTRA_RETURN_RESULT, true)
                .apply {
                    clipData = ClipData.newRawUri("Gallery update", apkUri)
                }
        }
        return installerIntents.firstOrNull { candidate ->
            candidate.resolveActivity(context.packageManager) != null
        } ?: error(context.getString(com.example.gallery.R.string.update_installer_unavailable))
    }

    /** Returns a localized failure description, or null when the installer reported success. */
    fun installResultError(context: Context, resultCode: Int): String? {
        if (resultCode == Activity.RESULT_OK) return null
        return if (resultCode == Activity.RESULT_CANCELED) {
            context.getString(com.example.gallery.R.string.update_install_cancelled)
        } else {
            context.getString(
                com.example.gallery.R.string.update_install_failed,
                resultCode.toString()
            )
        }
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
        val packageInfo = context.packageManager.getPackageArchiveInfo(
            apk.absolutePath,
            signingInfoFlags()
        )
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
        validateSigningCompatibility(context, packageInfo)
    }

    private fun validateSigningCompatibility(context: Context, apk: File) {
        @Suppress("DEPRECATION")
        val candidate = context.packageManager.getPackageArchiveInfo(apk.absolutePath, signingInfoFlags())
            ?: error(context.getString(com.example.gallery.R.string.update_invalid_apk))
        validateSigningCompatibility(context, candidate)
    }

    private fun validateSigningCompatibility(context: Context, candidate: PackageInfo) {
        @Suppress("DEPRECATION")
        val installed = context.packageManager.getPackageInfo(context.packageName, signingInfoFlags())
        val installedCurrent = currentSignerDigests(installed)
        val candidateCurrent = currentSignerDigests(candidate)
        val candidateHistory = signerHistoryDigests(candidate) + candidateCurrent
        val candidateHasMultipleSigners = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            candidate.signingInfo?.hasMultipleSigners() == true
        } else {
            candidateCurrent.size > 1
        }
        if (installedCurrent.isEmpty() || candidateCurrent.isEmpty()) {
            error(context.getString(com.example.gallery.R.string.update_signature_unverified))
        }
        if (
            !areSigningCertificatesCompatible(
                installedCurrent = installedCurrent,
                candidateCurrent = candidateCurrent,
                candidateHistory = candidateHistory,
                candidateHasMultipleSigners = candidateHasMultipleSigners
            )
        ) {
            throw AppUpdateSignatureMismatchException(
                context.getString(com.example.gallery.R.string.update_signature_migration_guide)
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun signingInfoFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

    @Suppress("DEPRECATION")
    private fun currentSignerDigests(packageInfo: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            packageInfo.signatures?.toList().orEmpty()
        }
        return signatures.mapTo(linkedSetOf()) { signatureDigest(it.toByteArray()) }
    }

    @Suppress("DEPRECATION")
    private fun signerHistoryDigests(packageInfo: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.let { signingInfo ->
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners?.toList().orEmpty()
                } else {
                    signingInfo.signingCertificateHistory?.toList().orEmpty()
                }
            }.orEmpty()
        } else {
            packageInfo.signatures?.toList().orEmpty()
        }
        return signatures.mapTo(linkedSetOf()) { signatureDigest(it.toByteArray()) }
    }

    private fun signatureDigest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    internal fun areSigningCertificatesCompatible(
        installedCurrent: Set<String>,
        candidateCurrent: Set<String>,
        candidateHistory: Set<String>,
        candidateHasMultipleSigners: Boolean
    ): Boolean {
        if (installedCurrent.isEmpty() || candidateCurrent.isEmpty()) return false
        return if (candidateHasMultipleSigners || installedCurrent.size > 1) {
            installedCurrent == candidateCurrent
        } else {
            candidateHistory.containsAll(installedCurrent)
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

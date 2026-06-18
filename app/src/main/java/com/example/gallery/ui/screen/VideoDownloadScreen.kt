package com.example.gallery.ui.screen

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.gallery.data.model.MediaData
import com.example.gallery.data.local.entity.VideoDownloadEntity
import com.example.gallery.ui.state.GalleryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .addInterceptor(HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    })
    .connectionSpecs(
        listOf(
            ConnectionSpec.MODERN_TLS,
            ConnectionSpec.COMPATIBLE_TLS
        )
    )
    .build()

private data class MediaUrlCandidate(
    val url: String,
    val bitrate: Int = 0,
    val contentType: String? = null,
    val isGifSource: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDownloadScreen(
    galleryState: GalleryState,
    onMenuClick: () -> Unit,
    onNavigateHome: () -> Unit = {},
    initialUrl: String? = null,
    onInitialUrlConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var urlInput by remember { mutableStateOf("") }
    var showDownloadModal by remember { mutableStateOf(false) }
    var viewerMedia by remember { mutableStateOf<MediaData?>(null) }

    val downloads by galleryState.repository.mediaDao.getAllVideoDownloads().collectAsState(initial = emptyList())
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val lifecycleOwner = LocalLifecycleOwner.current

    fun showOptionsForUrl(url: String) {
        if (url.isXStatusUrl() && (urlInput != url || !showDownloadModal)) {
            urlInput = url
            showDownloadModal = true
        }
    }

    fun showOptionsFromClipboard() {
        if (showDownloadModal || urlInput.isNotBlank()) return

        val clip = clipboardManager.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString().orEmpty()
            showOptionsForUrl(text)
        }
    }

    LaunchedEffect(initialUrl) {
        if (initialUrl != null) {
            showOptionsForUrl(initialUrl)
            onInitialUrlConsumed()
        } else if (urlInput.isBlank() && !showDownloadModal) {
            showOptionsFromClipboard()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && initialUrl == null) {
                showOptionsFromClipboard()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video Downloader", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, "メニュー", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("X (Twitter) URL") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Link, null) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.Gray
                ),
                singleLine = true
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (urlInput.isNotBlank()) {
                        showDownloadModal = true
                    } else {
                        Toast.makeText(context, "Enter a URL", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DA1F2))
            ) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("Show download options")
            }

            Spacer(Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null, tint = Color.Gray)
                    Spacer(Modifier.width(8.dp))
                    Text("Download history", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                if (downloads.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                galleryState.repository.mediaDao.clearVideoDownloadHistory()
                                Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear history", fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(downloads) { download ->
                    DownloadHistoryItem(
                        download = download,
                        onOpenInViewer = { media -> viewerMedia = media }
                    )
                }
            }
        }
    }

    viewerMedia?.let { media ->
        MediaViewerScreen(
            imageList = listOf(media),
            initialPage = 0,
            onClickedClose = { viewerMedia = null },
            galleryState = galleryState
        )
    }

    if (showDownloadModal) {
        DownloadOptionsModal(
            url = urlInput,
            galleryState = galleryState,
            onDismiss = { showDownloadModal = false },
            onNavigateHome = {
                showDownloadModal = false
                onNavigateHome()
            },
            onDownloadStart = { resolvedUrl, quality ->
                showDownloadModal = false
                startDownloadTask(context, resolvedUrl, quality, galleryState, originalUrl = urlInput)
                urlInput = ""
            }
        )
    }
}

@Composable
fun DownloadHistoryItem(
    download: VideoDownloadEntity,
    onOpenInViewer: (MediaData) -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DownloadMediaPreview(
                uri = download.savePath.takeIf { download.status == "COMPLETED" && it != "Pending..." },
                modifier = Modifier
                    .width(96.dp)
                    .height(64.dp),
                onClick = {
                    buildDownloadMediaData(context, download)?.let(onOpenInViewer)
                }
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(download.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(download.url, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(dateFormat.format(Date(download.downloadDate)), color = Color.Gray, fontSize = 11.sp)
                    Text(
                        download.status,
                        color = if (download.status == "COMPLETED") Color.Green else Color.Red,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadMediaPreview(
    uri: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0A0A0A))
            .clickable(enabled = !uri.isNullOrBlank(), onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (!uri.isNullOrBlank()) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(uri)
                    .videoFrameMillis(1000)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(28.dp)
            )
        } else {
            Text("...", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadOptionsModal(
    url: String,
    galleryState: GalleryState,
    onDismiss: () -> Unit,
    onNavigateHome: () -> Unit,
    onDownloadStart: (String, String) -> Unit
) {
    var isDuplicate by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var resolvedUrls by remember { mutableStateOf<List<MediaUrlCandidate>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedQuality by remember { mutableStateOf("High (1080p)") }
    val qualities = listOf("High (1080p)", "Medium (720p)", "Low (480p)")

    LaunchedEffect(url) {
        isLoading = true
        error = null
        resolvedUrls = emptyList()
        isDuplicate = galleryState.repository.mediaDao.isVideoDownloaded(url)

        withContext(Dispatchers.IO) {
            try {
                resolvedUrls = resolveXVideoUrls(url)
                if (resolvedUrls.isEmpty()) {
                    error = "Failed to get media URL. Check that the post is public."
                }
            } catch (e: Exception) {
                error = "Resolve error: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text("Download settings", color = Color.White) },
        text = {
            when {
                isLoading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(color = Color.Cyan)
                        Spacer(Modifier.height(8.dp))
                        Text("Resolving media...", color = Color.LightGray)
                    }
                }
                error != null -> {
                    Text(error!!, color = Color.Red)
                }
                else -> {
                    Column {
                        DownloadMediaPreview(
                            uri = resolvedUrls.firstOrNull()?.url,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        )
                        Spacer(Modifier.height(12.dp))
                        if (isDuplicate) {
                            Text("Note: this post is already downloaded.", color = Color.Yellow, fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))
                        }
                        Text("Select quality:", color = Color.LightGray)
                        qualities.forEach { quality ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedQuality = quality }
                                    .padding(vertical = 8.dp)
                            ) {
                                RadioButton(selected = selectedQuality == quality, onClick = { selectedQuality = quality })
                                Text(quality, color = Color.White)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectUrlForQuality(resolvedUrls, selectedQuality)?.let { resolvedMedia ->
                        onDownloadStart(resolvedMedia.url, selectedQuality)
                    }
                },
                enabled = !isLoading && error == null && resolvedUrls.isNotEmpty()
            ) {
                Text(if (isDuplicate) "Download again" else "Start download")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onNavigateHome) { Text("HOME", color = Color.Gray) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
            }
        }
    )
}

private fun selectUrlForQuality(urls: List<MediaUrlCandidate>, quality: String): MediaUrlCandidate? {
    if (urls.isEmpty()) return null
    return when {
        quality.startsWith("Low") -> urls.last()
        quality.startsWith("Medium") -> urls[urls.size / 2]
        else -> urls.first()
    }
}

private fun resolveXVideoUrls(statusUrl: String): List<MediaUrlCandidate> {
    if (!statusUrl.isXStatusUrl()) return listOf(MediaUrlCandidate(statusUrl))

    val normalizedUrl = if (!statusUrl.startsWith("http")) "https://$statusUrl" else statusUrl
    val baseUrl = normalizedUrl.substringBefore("?")
    val apiUrl = when {
        baseUrl.contains("x.com") -> baseUrl.replace("x.com", "api.fxtwitter.com")
        baseUrl.contains("twitter.com") -> baseUrl.replace("twitter.com", "api.fxtwitter.com")
        else -> baseUrl
    }

    return try {
        val request = Request.Builder()
            .url(apiUrl)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e("VideoDownloader", "API request failed: ${response.code} ${response.message}")
                return@use emptyList()
            }

            val body = response.body?.string() ?: return@use emptyList()
            Log.d("VideoDownloader", "API Response: $body")

            extractMediaUrlCandidates(JSONObject(body))
                .sortedWith(
                    compareByDescending<MediaUrlCandidate> {
                        it.url.isGifUrl() || it.contentType.equals("image/gif", ignoreCase = true)
                    }.thenByDescending {
                        it.isGifSource && !it.url.isMp4Url()
                    }.thenByDescending {
                        it.url.isMp4Url() || it.contentType.equals("video/mp4", ignoreCase = true)
                    }.thenByDescending { it.bitrate }
                )
                .distinctBy { it.url }
                .also { Log.d("VideoDownloader", "Resolved media URLs: $it") }
        }
    } catch (e: Exception) {
        val errorMessage = if (e is javax.net.ssl.SSLHandshakeException) {
            "SSL Handshake failed: ${e.localizedMessage}. OkHttp was used for this request."
        } else {
            "Failed to resolve X URL: ${e.localizedMessage}"
        }
        Log.e("VideoDownloader", errorMessage, e)
        emptyList()
    }
}

private fun extractMediaUrlCandidates(json: JSONObject): List<MediaUrlCandidate> {
    val tweet = json.optJSONObject("tweet") ?: json
    val mediaObjects = buildList {
        tweet.optJSONArray("media_extended")?.let { addAll(it.toJsonObjects()) }
        tweet.optJSONArray("media")?.let { addAll(it.toJsonObjects()) }
        tweet.optJSONObject("media")?.let { media ->
            media.optJSONArray("all")?.let { addAll(it.toJsonObjects()) }
            media.optJSONArray("videos")?.let { addAll(it.toJsonObjects()) }
            media.optJSONArray("photos")?.let { addAll(it.toJsonObjects()) }
        }
    }

    return mediaObjects.flatMap { media ->
        val isGifSource = media.isGifMediaObject()
        buildList {
            media.gifDirectUrls().forEach { add(MediaUrlCandidate(it, contentType = "image/gif", isGifSource = true)) }
            media.optJSONArray("variants")?.let { addAll(it.toCandidates(isGifSource)) }
            media.optJSONObject("video_info")?.optJSONArray("variants")?.let { addAll(it.toCandidates(isGifSource)) }
            media.optString("url").takeIf { it.isDirectMediaUrl() }?.let { add(MediaUrlCandidate(it, isGifSource = isGifSource)) }
            media.optString("media_url_https").takeIf { it.isDirectMediaUrl() }?.let { add(MediaUrlCandidate(it, isGifSource = isGifSource)) }
            media.optString("media_url").takeIf { it.isDirectMediaUrl() }?.let { add(MediaUrlCandidate(it, isGifSource = isGifSource)) }
            media.optString("display_url").takeIf { it.isDirectMediaUrl() }?.let { add(MediaUrlCandidate(it, isGifSource = isGifSource)) }
            media.optString("expanded_url").takeIf { it.isDirectMediaUrl() }?.let { add(MediaUrlCandidate(it, isGifSource = isGifSource)) }
            media.optString("thumbnail_url").takeIf { it.isDirectMediaUrl() }?.let { add(MediaUrlCandidate(it, isGifSource = isGifSource)) }
            media.optString("thumb").takeIf { it.isDirectMediaUrl() }?.let { add(MediaUrlCandidate(it, isGifSource = isGifSource)) }
            media.optJSONArray("urls")?.let { addAll(it.toStringCandidates(isGifSource)) }
        }
    }
}

private fun JSONObject.isGifMediaObject(): Boolean {
    return listOf("type", "media_type", "mediaType", "format", "mediaTypeName")
        .any { key -> optString(key).contains("gif", ignoreCase = true) }
}

private fun JSONObject.gifDirectUrls(): List<String> {
    val gifKeys = listOf(
        "gif_url",
        "gifUrl",
        "animated_gif_url",
        "animatedGifUrl",
        "original_gif_url",
        "originalGifUrl",
        "download_gif_url",
        "downloadGifUrl"
    )

    return gifKeys.mapNotNull { key ->
        optString(key)
            .takeIf { it.isDirectMediaUrl() && it.isGifUrl() }
    }
}

private fun JSONArray.toJsonObjects(): List<JSONObject> = buildList {
    for (index in 0 until length()) {
        optJSONObject(index)?.let { add(it) }
    }
}

private fun JSONArray.toCandidates(isGifSource: Boolean = false): List<MediaUrlCandidate> = buildList {
    for (index in 0 until length()) {
        val variant = optJSONObject(index) ?: continue
        val url = variant.optString("url").takeIf { it.isDirectMediaUrl() } ?: continue
        val contentType = variant.optString("content_type")
            .ifBlank { variant.optString("contentType") }
            .ifBlank { null }
        if (contentType == "application/x-mpegURL" || url.contains(".m3u8")) continue
        val variantIsGifSource = isGifSource || variant.isGifMediaObject()
        add(MediaUrlCandidate(url, variant.optInt("bitrate", 0), contentType, variantIsGifSource))
    }
}

private fun JSONArray.toStringCandidates(isGifSource: Boolean = false): List<MediaUrlCandidate> = buildList {
    for (index in 0 until length()) {
        val url = optString(index).takeIf { it.isDirectMediaUrl() } ?: continue
        add(MediaUrlCandidate(url, isGifSource = isGifSource))
    }
}

private fun String.isXStatusUrl(): Boolean {
    val lower = lowercase(Locale.US)
    return (lower.contains("x.com") || lower.contains("twitter.com")) && lower.contains("/status/")
}

private fun String.isDirectMediaUrl(): Boolean {
    if (isBlank()) return false
    val lower = lowercase(Locale.US)
    return lower.isMp4Url() ||
        lower.contains(".gif") ||
        lower.contains("format=gif") ||
        lower.contains(".webp") ||
        lower.contains("format=webp") ||
        lower.contains(".jpg") ||
        lower.contains(".jpeg") ||
        lower.contains("format=jpg") ||
        lower.contains("format=jpeg") ||
        lower.contains(".png") ||
        lower.contains("format=png")
}

private fun String.isMp4Url(): Boolean = lowercase(Locale.US).contains(".mp4")

private fun String.isGifUrl(): Boolean {
    val lower = lowercase(Locale.US)
    return lower.contains(".gif") || lower.contains("format=gif")
}

private fun detectMediaType(url: String, contentTypeHeader: String?): Pair<String, String> {
    val lowerUrl = url.lowercase(Locale.US)
    val contentType = contentTypeHeader
        ?.substringBefore(";")
        ?.trim()
        ?.lowercase(Locale.US)
        .orEmpty()

    return when {
        contentType == "image/gif" || lowerUrl.contains(".gif") || lowerUrl.contains("format=gif") -> "gif" to "image/gif"
        contentType == "image/webp" || lowerUrl.contains(".webp") || lowerUrl.contains("format=webp") -> "webp" to "image/webp"
        contentType == "image/png" || lowerUrl.contains(".png") || lowerUrl.contains("format=png") -> "png" to "image/png"
        contentType == "image/jpeg" ||
            lowerUrl.contains(".jpg") ||
            lowerUrl.contains(".jpeg") ||
            lowerUrl.contains("format=jpg") ||
            lowerUrl.contains("format=jpeg") -> "jpg" to "image/jpeg"
        contentType == "video/mp4" || lowerUrl.contains(".mp4") -> "mp4" to "video/mp4"
        else -> "mp4" to "video/mp4"
    }
}

fun startDownloadTask(
    context: Context,
    url: String,
    quality: String,
    galleryState: GalleryState,
    originalUrl: String? = null
) {
    val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
    scope.launch {
        val timestamp = System.currentTimeMillis()
        val checkUrl = originalUrl ?: url

        Log.i("VideoDownloader", "========================================")
        Log.i("VideoDownloader", "DOWNLOAD START")
        Log.i("VideoDownloader", "Original URL: $checkUrl")
        Log.i("VideoDownloader", "Resolved URL: $url")
        Log.i("VideoDownloader", "========================================")

        val entity = VideoDownloadEntity(
            url = checkUrl,
            title = "Media $timestamp ($quality)",
            savePath = "Pending...",
            downloadDate = timestamp,
            status = "DOWNLOADING"
        )
        galleryState.repository.mediaDao.insertVideoDownload(entity)

        try {
            val request = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Download failed: ${response.code}")
                }

                val responseBody = response.body ?: throw Exception("Response body is null")
                val (extension, mimeType) = detectMediaType(url, response.header("Content-Type"))
                val filename = "X_Media_$timestamp.$extension"

                Log.i("VideoDownloader", "Detected Extension: $extension")
                Log.i("VideoDownloader", "Detected MIME Type: $mimeType")

                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(android.provider.MediaStore.MediaColumns.DATE_ADDED, timestamp / 1000)
                    put(android.provider.MediaStore.MediaColumns.DATE_MODIFIED, timestamp / 1000)
                    put(android.provider.MediaStore.MediaColumns.DATE_TAKEN, timestamp)

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val relativePath = if (mimeType.startsWith("video/")) "Movies/Gallery" else "Pictures/Gallery"
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                val collection = if (mimeType.startsWith("video/")) {
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(collection, contentValues) ?: throw Exception("Failed to create MediaStore entry")

                resolver.openOutputStream(uri)?.use { outputStream ->
                    responseBody.byteStream().use { input ->
                        input.copyTo(outputStream)
                    }
                    outputStream.flush()
                } ?: throw Exception("Failed to open output stream")

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val updateValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                        put(android.provider.MediaStore.MediaColumns.DATE_MODIFIED, timestamp / 1000)
                        put(android.provider.MediaStore.MediaColumns.DATE_TAKEN, timestamp)
                    }
                    resolver.update(uri, updateValues, null, null)
                }

                val actualPath = try {
                    val cursor = resolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)
                    cursor?.use { if (it.moveToFirst()) it.getString(0) else uri.toString() } ?: uri.toString()
                } catch (e: Exception) {
                    uri.toString()
                }

                Log.i("VideoDownloader", "DOWNLOAD COMPLETE")
                Log.i("VideoDownloader", "Saved Path: $actualPath")
                Log.i("VideoDownloader", "========================================")

                if (!actualPath.startsWith("content://")) {
                    MediaScannerConnection.scanFile(context, arrayOf(actualPath), arrayOf(mimeType)) { _, scannedUri ->
                        Log.d("VideoDownloader", "Scan finished for $scannedUri")
                        scope.launch { galleryState.refresh() }
                    }
                } else {
                    galleryState.refresh()
                }

                galleryState.repository.mediaDao.insertVideoDownload(entity.copy(status = "COMPLETED", savePath = actualPath))

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download complete: $extension", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("VideoDownloader", "Task failed", e)
            galleryState.repository.mediaDao.insertVideoDownload(entity.copy(status = "FAILED"))
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

private fun buildDownloadMediaData(context: Context, download: VideoDownloadEntity): MediaData? {
    val path = download.savePath
    if (path.isBlank() || path == "Pending...") return null
    val uri = Uri.parse(path)
    val resolverMimeType = runCatching {
        if (path.startsWith("content://")) context.contentResolver.getType(uri) else null
    }.getOrNull()
    val mimeType = resolverMimeType ?: when {
        path.contains(".gif", ignoreCase = true) -> "image/gif"
        path.contains(".webp", ignoreCase = true) -> "image/webp"
        path.contains(".jpg", ignoreCase = true) || path.contains(".jpeg", ignoreCase = true) -> "image/jpeg"
        path.contains(".png", ignoreCase = true) -> "image/png"
        else -> "video/*"
    }
    val viewerUri = if (path.contains("://")) path else "file://$path"
    return MediaData(
        uri = viewerUri,
        dateAdded = download.downloadDate,
        mimeType = mimeType,
        fileName = download.title,
        folderName = "Downloads"
    )
}

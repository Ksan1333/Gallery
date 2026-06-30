package com.example.gallery.ui.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaScannerConnection
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.gallery.data.model.MediaData
import com.example.gallery.data.local.entity.VideoDownloadEntity
import com.example.gallery.service.GlobalOperationService
import com.example.gallery.ui.component.GalleryTopAppBar
import com.example.gallery.ui.state.GalleryState
import com.example.gallery.ui.theme.GalleryThemeTokens
import com.example.gallery.util.VideoDownloadUrlUtils
import com.squareup.gifencoder.GifEncoder
import com.squareup.gifencoder.ImageOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
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

data class MediaUrlCandidate(
    val url: String,
    val bitrate: Int = 0,
    val contentType: String? = null,
    val isGifSource: Boolean = false
) {
    val isPlayableVideo: Boolean
        get() = url.isMp4Url() || contentType.equals("video/mp4", ignoreCase = true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDownloadScreen(
    galleryState: GalleryState,
    onMenuClick: () -> Unit,
    onNavigateHome: () -> Unit = {},
    initialUrl: String? = null,
    onInitialUrlConsumed: () -> Unit = {},
    onViewerVisibleChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var urlInput by remember { mutableStateOf("") }
    var showDownloadModal by remember { mutableStateOf(false) }
    var viewerMedia by remember { mutableStateOf<MediaData?>(null) }

    val downloads by galleryState.repository.mediaDao.getAllVideoDownloads().collectAsState(initial = emptyList())
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewerMedia != null) {
        onViewerVisibleChanged(viewerMedia != null)
    }

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
            GalleryTopAppBar(
                title = "Video Downloader",
                navigationIcon = Icons.Default.Menu,
                navigationContentDescription = "メニュー",
                onNavigationClick = onMenuClick
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
                colors = ButtonDefaults.buttonColors(containerColor = GalleryThemeTokens.colors.accent)
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
                    Text("Download history", color = Color.White, fontSize = com.example.gallery.ui.AppConstants.HeaderFontSize, fontWeight = FontWeight.Bold)
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
                        Text("Clear history", fontSize = com.example.gallery.ui.AppConstants.SmallFontSize)
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
            galleryState = galleryState,
            keepNavigationBarsHidden = true
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
            onDownloadStart = { resolvedMedia, quality ->
                showDownloadModal = false
                startDownloadTask(
                    context,
                    resolvedMedia.url,
                    quality,
                    galleryState,
                    originalUrl = urlInput,
                    forceGifFromVideo = resolvedMedia.isGifSource
                )
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
        colors = CardDefaults.cardColors(containerColor = GalleryThemeTokens.colors.card)
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
                Text(download.url, color = Color.Gray, fontSize = com.example.gallery.ui.AppConstants.SmallFontSize, maxLines = 1)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(dateFormat.format(Date(download.downloadDate)), color = Color.Gray, fontSize = com.example.gallery.ui.AppConstants.ExtraSmallFontSize)
                    Text(
                        download.status,
                        color = if (download.status == "COMPLETED") Color.Green else Color.Red,
                        fontSize = com.example.gallery.ui.AppConstants.ExtraSmallFontSize
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
            .background(GalleryThemeTokens.colors.background)
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
            Text("...", color = Color.Gray, fontSize = com.example.gallery.ui.AppConstants.SmallFontSize)
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
    onDownloadStart: (MediaUrlCandidate, String) -> Unit
) {
    var isDuplicate by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var resolvedUrls by remember { mutableStateOf<List<MediaUrlCandidate>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedQuality by remember { mutableStateOf("High (1080p)") }
    val qualities = listOf("High (1080p)", "Medium (720p)", "Low (480p)")
    val isGifDownload = resolvedUrls.any { it.isGifSource }

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
        containerColor = GalleryThemeTokens.colors.surface,
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
                            Text("Note: this post is already downloaded.", color = Color.Yellow, fontSize = com.example.gallery.ui.AppConstants.SubtitleFontSize)
                            Spacer(Modifier.height(8.dp))
                        }
                        if (isGifDownload) {
                            Text("GIF post detected. Download as GIF image.", color = Color.LightGray)
                        } else {
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val selectedMedia = if (isGifDownload) {
                        selectGifUrl(resolvedUrls)
                    } else {
                        selectUrlForQuality(resolvedUrls, selectedQuality)
                    }
                    selectedMedia?.let { resolvedMedia ->
                        onDownloadStart(resolvedMedia, if (isGifDownload) "GIF" else selectedQuality)
                    }
                },
                enabled = !isLoading && error == null && resolvedUrls.isNotEmpty()
            ) {
                Text(if (isGifDownload) "Download GIF" else if (isDuplicate) "Download again" else "Start download")
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

private fun selectGifUrl(urls: List<MediaUrlCandidate>): MediaUrlCandidate? {
    return urls.firstOrNull {
        it.isGifSource && (it.url.isGifUrl() || it.contentType.equals("image/gif", ignoreCase = true))
    } ?: urls.firstOrNull { it.isGifSource }
}

private fun resolveXVideoUrls(statusUrl: String): List<MediaUrlCandidate> {
    if (!statusUrl.isXStatusUrl()) return listOf(MediaUrlCandidate(statusUrl))

    val normalizedUrl = if (!statusUrl.startsWith("http")) "https://$statusUrl" else statusUrl
    val baseUrl = normalizedUrl.substringBefore("?")
    val apiUrls = buildXApiUrls(baseUrl)

    return try {
        apiUrls.firstNotNullOfOrNull { apiUrl ->
            val request = Request.Builder()
                .url(apiUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", baseUrl)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use null
                }

                val body = response.body?.string() ?: return@use null

                extractMediaUrlCandidates(JSONObject(body))
                    .sortedWith(
                        compareByDescending<MediaUrlCandidate> {
                            it.url.isGifUrl() || it.contentType.equals("image/gif", ignoreCase = true)
                        }.thenByDescending {
                            it.isPlayableVideo
                        }.thenByDescending {
                            it.bitrate
                        }.thenBy {
                            it.url.contains("thumb", ignoreCase = true) || it.url.contains("small", ignoreCase = true)
                        }
                    )
                    .distinctBy { it.url }
                    .takeIf { it.isNotEmpty() }
            }
        }.orEmpty()
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

private fun buildXApiUrls(baseUrl: String): List<String> = VideoDownloadUrlUtils.buildXApiUrls(baseUrl)

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

    val knownMediaCandidates = mediaObjects.flatMap { media ->
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

    return knownMediaCandidates + json.collectNestedMediaCandidates()
}

private fun JSONObject.collectNestedMediaCandidates(): List<MediaUrlCandidate> = buildList {
    lateinit var visitObject: (JSONObject, Boolean) -> Unit
    lateinit var visitArray: (JSONArray, Boolean) -> Unit

    visitArray = { array, inheritedGifSource ->
        for (index in 0 until array.length()) {
            when (val child = array.opt(index)) {
                is JSONObject -> visitObject(child, inheritedGifSource)
                is String -> child.takeIf { it.isDirectMediaUrl() }?.let {
                    add(MediaUrlCandidate(it, isGifSource = inheritedGifSource))
                }
            }
        }
    }

    visitObject = { obj, inheritedGifSource ->
        val isGifSource = inheritedGifSource || obj.isGifMediaObject()
        obj.gifDirectUrls().forEach { add(MediaUrlCandidate(it, contentType = "image/gif", isGifSource = true)) }
        obj.optJSONArray("variants")?.let { addAll(it.toCandidates(isGifSource)) }
        obj.optJSONObject("video_info")?.optJSONArray("variants")?.let { addAll(it.toCandidates(isGifSource)) }
        listOf("url", "media_url_https", "media_url", "display_url", "expanded_url", "thumbnail_url", "thumb").forEach { key ->
            obj.optString(key).takeIf { it.isDirectMediaUrl() }?.let {
                add(MediaUrlCandidate(it, isGifSource = isGifSource))
            }
        }
        obj.keys().forEach { key ->
            when (val child = obj.opt(key)) {
                is JSONObject -> visitObject(child, isGifSource)
                is JSONArray -> visitArray(child, isGifSource)
            }
        }
    }

    visitObject(this@collectNestedMediaCandidates, false)
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
    return VideoDownloadUrlUtils.isXStatusUrl(this)
}

private fun String.isDirectMediaUrl(): Boolean {
    return VideoDownloadUrlUtils.isDirectMediaUrl(this)
}

private fun String.isMp4Url(): Boolean = VideoDownloadUrlUtils.isMp4Url(this)

private fun String.isGifUrl(): Boolean {
    return VideoDownloadUrlUtils.isGifUrl(this)
}

private fun detectMediaType(url: String, contentTypeHeader: String?): Pair<String, String> {
    return VideoDownloadUrlUtils.detectMediaType(url, contentTypeHeader)
}

fun startDownloadTask(
    context: Context,
    url: String,
    quality: String,
    galleryState: GalleryState,
    originalUrl: String? = null,
    forceGifFromVideo: Boolean = false
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
        val operationId = GlobalOperationService.startOperation(
            title = "動画をダウンロード中",
            canCancel = false
        )

        try {
            val request = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                .header("Accept", "video/mp4,image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .header("Referer", originalUrl ?: "https://x.com/")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Download failed: ${response.code}")
                }

                val responseBody = response.body ?: throw Exception("Response body is null")
                val totalBytes = responseBody.contentLength()
                val detected = detectMediaType(url, response.header("Content-Type"))
                val shouldTranscodeGif = forceGifFromVideo && detected.second.startsWith("video/")
                val (extension, mimeType) = if (shouldTranscodeGif) "gif" to "image/gif" else detected
                val filename = "X_Media_$timestamp.$extension"

                Log.i("VideoDownloader", "Detected Extension: $extension")
                Log.i("VideoDownloader", "Detected MIME Type: $mimeType")

                val outputBytes: ByteArray? = if (shouldTranscodeGif) {
                    val tempFile = File.createTempFile("x_gif_source_", ".mp4", context.cacheDir)
                    try {
                        tempFile.outputStream().use { output ->
                            responseBody.byteStream().use { input ->
                                copyDownloadWithProgress(
                                    input = input,
                                    output = output,
                                    totalBytes = totalBytes,
                                    operationId = operationId,
                                    progressEnd = 0.9f
                                )
                            }
                        }
            GlobalOperationService.updateProgress(0.92f, "GIFへ変換中...", operationId)
                        transcodeMp4ToGif(context, tempFile)
                    } finally {
                        tempFile.delete()
                    }
                } else {
                    null
                }

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
                    if (outputBytes != null) {
            GlobalOperationService.updateProgress(0.98f, "GIFを保存中...", operationId)
                        outputStream.write(outputBytes)
                    } else {
                        responseBody.byteStream().use { input ->
                            copyDownloadWithProgress(
                                input = input,
                                output = outputStream,
                                totalBytes = totalBytes,
                                operationId = operationId,
                                progressEnd = 0.98f
                            )
                        }
                    }
                    outputStream.flush()
                } ?: throw Exception("Failed to open output stream")

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val updateValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                        put(android.provider.MediaStore.MediaColumns.DATE_MODIFIED, timestamp / 1000)
                        put(android.provider.MediaStore.MediaColumns.DATE_TAKEN, timestamp)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            put(android.provider.MediaStore.MediaColumns.DATE_EXPIRES, null as Long?)
                        }
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
            GlobalOperationService.updateProgress(1f, "保存が完了しました", operationId)

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
        } finally {
            GlobalOperationService.finishOperation(operationId)
        }
    }
}

private suspend fun copyDownloadWithProgress(
    input: InputStream,
    output: OutputStream,
    totalBytes: Long,
    operationId: String,
    progressEnd: Float
) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val startedAt = SystemClock.elapsedRealtime()
    var downloadedBytes = 0L
    var lastUpdateAt = 0L

    while (true) {
        currentCoroutineContext().ensureActive()
        val read = input.read(buffer)
        if (read < 0) break
        output.write(buffer, 0, read)
        downloadedBytes += read

        val now = SystemClock.elapsedRealtime()
        if (now - lastUpdateAt >= 200L || (totalBytes > 0L && downloadedBytes >= totalBytes)) {
            val elapsedMs = (now - startedAt).coerceAtLeast(1L)
            val fraction = if (totalBytes > 0L) {
                (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            val bytesPerSecond = downloadedBytes * 1000L / elapsedMs
            val remainingMs = if (totalBytes > 0L && bytesPerSecond > 0L) {
                (totalBytes - downloadedBytes).coerceAtLeast(0L) * 1000L / bytesPerSecond
            } else {
                null
            }
            val status = buildString {
                append(formatDownloadSize(downloadedBytes))
                if (totalBytes > 0L) {
                    append(" / ")
                    append(formatDownloadSize(totalBytes))
                }
                append("  ")
                append(formatDownloadSize(bytesPerSecond))
                append(" /s")
                if (remainingMs != null && elapsedMs >= 500L) {
                    append("  残り ")
                    append(formatDownloadEta(remainingMs))
                } else {
                    append("  残り時間を計測中")
                }
            }
            GlobalOperationService.updateProgress(fraction * progressEnd, status, operationId)
            lastUpdateAt = now
        }
    }
}

private fun formatDownloadSize(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    return when {
        safeBytes >= 1024L * 1024L -> "%.1f MB".format(Locale.US, safeBytes / (1024f * 1024f))
        safeBytes >= 1024L -> "%.1f KB".format(Locale.US, safeBytes / 1024f)
        else -> "$safeBytes B"
    }
}

private fun formatDownloadEta(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) + 999L) / 1000L
    return if (totalSeconds < 60L) {
        "${totalSeconds}秒"
    } else {
        "${totalSeconds / 60L}分${totalSeconds % 60L}秒"
    }
}

private fun transcodeMp4ToGif(context: Context, sourceFile: File): ByteArray {
    val retriever = MediaMetadataRetriever()
    var input: FileInputStream? = null
    try {
        val patchResult = patchMp4ForAndroidExtractor(sourceFile)
        try {
            input = FileInputStream(sourceFile)
            retriever.setDataSource(input.fd)
        } catch (fdError: Exception) {
            Log.w("VideoDownloader", "FileDescriptor data source failed, retrying by path", fdError)
            input?.close()
            input = null
            retriever.setDataSource(sourceFile.absolutePath)
        }
        val rawDurationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        val durationMs = rawDurationMs?.coerceAtLeast(300L) ?: 3000L
        val videoWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val videoHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        val frames = mutableListOf<Bitmap>()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val videoFrameCount = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                ?.toIntOrNull()
                ?: 0
            if (videoFrameCount > 0) {
                val targetCount = videoFrameCount.coerceIn(1, 28)
                val step = (videoFrameCount / targetCount).coerceAtLeast(1)
                var frameIndex = 0
                while (frameIndex < videoFrameCount && frames.size < targetCount) {
                    try {
                        retriever.getFrameAtIndex(frameIndex)?.let { frame ->
                            frames.add(scaleBitmapForGif(frame))
                        }
                    } catch (e: Exception) {
                        Log.d("VideoDownloader", "getFrameAtIndex failed at index=$frameIndex", e)
                    }
                    frameIndex += step
                }
            }
        }

        if (frames.isEmpty()) {
            val frameCount = (durationMs / 160L).toInt().coerceIn(8, 28)
            for (index in 0 until frameCount) {
                val timeUs = (durationMs * 1000L * index / frameCount).coerceAtLeast(0L)
                val frame = try {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.frameAtTime
                } catch (e: Exception) {
                    null
                }
                if (frame != null) {
                    frames.add(scaleBitmapForGif(frame))
                }
            }
        }

        if (frames.isEmpty()) {
            val codecFrames = decodeGifFramesWithCodec(sourceFile)
            frames.addAll(codecFrames.frames)
            if (codecFrames.durationMs != null) {
                // Prefer the decoder duration when Retriever cannot read this X tweet_video MP4.
                val fallbackDelayMs = (codecFrames.durationMs / frames.size.coerceAtLeast(1)).coerceIn(80L, 180L)
                if (frames.isNotEmpty()) {
                    return SimpleGifEncoder.encode(frames, fallbackDelayMs.toInt())
                }
            }
        }

        if (frames.isEmpty()) {
            throw Exception(
                "GIF conversion failed: no frames (size=${sourceFile.length()}, header=${sourceFile.headerHex()}, durationMs=$rawDurationMs, width=$videoWidth, height=$videoHeight, mime=$mimeType)"
            )
        }
        val frameDelayMs = (durationMs / frames.size.coerceAtLeast(1)).coerceIn(80L, 180L)
        return SimpleGifEncoder.encode(frames, frameDelayMs.toInt())
    } finally {
        retriever.release()
        input?.close()
    }
}

private data class DecodedGifFrames(
    val frames: List<Bitmap>,
    val durationMs: Long?
)

private data class Mp4PatchResult(
    val patched: Boolean,
    val details: List<String>
)

private fun patchMp4ForAndroidExtractor(file: File): Mp4PatchResult {
    val details = mutableListOf<String>()

    try {
        val bytes = file.readBytes()
        if (bytes.size < 24 || bytes.asciiAt(4) != "ftyp") {
            return Mp4PatchResult(false, emptyList())
        }

        val majorBrand = bytes.asciiAt(8)
        val compatibleBrands = (16 until bytes.uInt32At(0).toInt().coerceAtMost(bytes.size) step 4)
            .mapNotNull { offset ->
                if (offset + 4 <= bytes.size) bytes.asciiAt(offset) else null
            }

        if (majorBrand in setOf("iso5", "iso6") || "isom" !in compatibleBrands) {
            bytes.putAsciiAt(8, "mp42")
            if (bytes.size >= 20) bytes.putAsciiAt(16, "isom")
            if (bytes.size >= 24) bytes.putAsciiAt(20, "mp41")
            details += "ftyp $majorBrand/${compatibleBrands.joinToString(",")} -> mp42/isom,mp41"
        }

        patchMp4BoxesForAndroid(bytes, 0, bytes.size, details)

        if (details.isNotEmpty()) {
            RandomAccessFile(file, "rw").use { output ->
                output.setLength(0)
                output.write(bytes)
            }
        }
    } catch (e: Exception) {
        Log.w("VideoDownloader", "GIF source MP4 patch failed; continuing with original file", e)
    }

    return Mp4PatchResult(details.isNotEmpty(), details)
}

private fun patchMp4BoxesForAndroid(
    bytes: ByteArray,
    start: Int,
    end: Int,
    details: MutableList<String>
) {
    var offset = start
    while (offset + 8 <= end) {
        val boxStart = offset
        var boxSize = bytes.uInt32At(offset)
        val boxType = bytes.asciiAt(offset + 4)
        var headerSize = 8L

        if (boxSize == 1L && offset + 16 <= end) {
            boxSize = bytes.uInt64At(offset + 8)
            headerSize = 16L
        } else if (boxSize == 0L) {
            boxSize = (end - offset).toLong()
        }

        if (boxSize < headerSize || boxSize > Int.MAX_VALUE || offset + boxSize > end) {
            return
        }

        if (boxType == "ctts" && boxSize >= 16L) {
            val version = bytes[offset + headerSize.toInt()]
            val flagsOffset = offset + headerSize.toInt() + 1
            val flags = bytes.uInt24At(flagsOffset)
            if (version.toInt() == 0 && flags != 0) {
                bytes[flagsOffset] = 0
                bytes[flagsOffset + 1] = 0
                bytes[flagsOffset + 2] = 0
                details += "ctts flags $flags -> 0 at $boxStart"
            }
        }

        val childStart = offset + headerSize.toInt() + if (boxType == "meta") 4 else 0
        val childEnd = (offset + boxSize).toInt()
        if (boxType in setOf("moov", "trak", "mdia", "minf", "stbl", "edts", "dinf", "udta", "meta")) {
            patchMp4BoxesForAndroid(bytes, childStart, childEnd, details)
        }

        offset += boxSize.toInt()
    }
}

private fun ByteArray.asciiAt(offset: Int): String {
    return if (offset >= 0 && offset + 4 <= size) {
        String(this, offset, 4, Charsets.ISO_8859_1)
    } else {
        ""
    }
}

private fun ByteArray.putAsciiAt(offset: Int, value: String) {
    val bytes = value.toByteArray(Charsets.ISO_8859_1)
    for (index in 0 until minOf(4, bytes.size)) {
        this[offset + index] = bytes[index]
    }
}

private fun ByteArray.uInt24At(offset: Int): Int {
    if (offset < 0 || offset + 3 > size) return 0
    return ((this[offset].toInt() and 0xFF) shl 16) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        (this[offset + 2].toInt() and 0xFF)
}

private fun ByteArray.uInt32At(offset: Int): Long {
    if (offset < 0 || offset + 4 > size) return 0L
    return ((this[offset].toLong() and 0xFF) shl 24) or
        ((this[offset + 1].toLong() and 0xFF) shl 16) or
        ((this[offset + 2].toLong() and 0xFF) shl 8) or
        (this[offset + 3].toLong() and 0xFF)
}

private fun ByteArray.uInt64At(offset: Int): Long {
    if (offset < 0 || offset + 8 > size) return 0L
    var value = 0L
    for (index in 0 until 8) {
        value = (value shl 8) or (this[offset + index].toLong() and 0xFF)
    }
    return value
}

private fun decodeGifFramesWithCodec(sourceFile: File, maxFrames: Int = 28): DecodedGifFrames {
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    val frames = mutableListOf<Bitmap>()
    var durationMs: Long? = null
    var decodedFrameCount = 0
    var imageNullCount = 0

    try {
        extractor.setDataSource(sourceFile.absolutePath)

        var videoTrackIndex = -1
        var videoMime: String? = null
        for (trackIndex in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (videoTrackIndex == -1 && mime.startsWith("video/")) {
                videoTrackIndex = trackIndex
                videoMime = mime
            }
        }

        if (videoTrackIndex == -1 || videoMime == null) {
            Log.w("VideoDownloader", "GIF codec fallback found no video track")
            return DecodedGifFrames(emptyList(), null)
        }

        extractor.selectTrack(videoTrackIndex)
        val videoFormat = extractor.getTrackFormat(videoTrackIndex)
        val durationUs = videoFormat.longOrNull(MediaFormat.KEY_DURATION)?.takeIf { it > 0L }
        durationMs = durationUs?.div(1000L)

        codec = createStartedVideoDecoder(videoMime, extractor, videoTrackIndex)
        val decoder = codec ?: throw Exception("GIF codec decoder was not created")
        val bufferInfo = MediaCodec.BufferInfo()
        val targetFrameCount = durationMs
            ?.let { (it / 160L).toInt().coerceIn(1, maxFrames) }
            ?: maxFrames
        val frameIntervalUs = durationUs
            ?.div(targetFrameCount.coerceAtLeast(1))
            ?.coerceAtLeast(1L)
        var nextCaptureUs = 0L
        var inputDone = false
        var outputDone = false
        var idleOutputLoops = 0
        var loopCount = 0

        while (!outputDone && frames.size < maxFrames && loopCount++ < 10_000) {
            if (!inputDone) {
                val inputIndex = decoder.dequeueInputBuffer(10_000L)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex)
                    if (inputBuffer == null) {
                        decoder.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime.coerceAtLeast(0L),
                                extractor.sampleFlags
                            )
                            extractor.advance()
                        }
                    }
                }
            }

            when (val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10_000L)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {}

                MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    idleOutputLoops++
                    if (inputDone && idleOutputLoops > 20) {
                        Log.w("VideoDownloader", "GIF codec fallback stopped after repeated empty output")
                        outputDone = true
                    }
                }

                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                    // Deprecated for modern APIs, but harmless if a device still reports it.
                }

                else -> {
                    if (outputIndex >= 0) {
                        idleOutputLoops = 0
                        val endOfStream = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        val shouldCapture = bufferInfo.size > 0 &&
                            (frameIntervalUs == null ||
                                frames.isEmpty() ||
                                bufferInfo.presentationTimeUs >= nextCaptureUs)

                        if (shouldCapture) {
                            val image = runCatching { decoder.getOutputImage(outputIndex) }
                                .getOrNull()
                            if (image == null) {
                                imageNullCount++
                            } else {
                                try {
                                    image.toBitmapOrNull()?.let { bitmap ->
                                        frames.add(scaleBitmapForGif(bitmap))
                                    }
                                } finally {
                                    image.close()
                                }
                            }

                            if (frameIntervalUs != null) {
                                while (nextCaptureUs <= bufferInfo.presentationTimeUs) {
                                    nextCaptureUs += frameIntervalUs
                                }
                            }
                        }

                        decodedFrameCount++
                        decoder.releaseOutputBuffer(outputIndex, false)
                        if (endOfStream) outputDone = true
                    }
                }
            }
        }

        return DecodedGifFrames(frames, durationMs)
    } catch (e: Exception) {
        frames.forEach { it.recycle() }
        Log.e(
            "VideoDownloader",
            "GIF codec fallback failed: decoded=$decodedFrameCount, captured=${frames.size}, " +
                "imageNull=$imageNullCount, durationMs=$durationMs",
            e
        )
        return DecodedGifFrames(emptyList(), durationMs)
    } finally {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        extractor.release()
    }
}

private fun createStartedVideoDecoder(
    mime: String,
    extractor: MediaExtractor,
    trackIndex: Int
): MediaCodec {
    var codec = MediaCodec.createDecoderByType(mime)
    val preferredFormat = extractor.getTrackFormat(trackIndex)
    preferredFormat.setInteger(
        MediaFormat.KEY_COLOR_FORMAT,
        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
    )
    try {
        codec.configure(preferredFormat, null, null, 0)
        codec.start()
        return codec
    } catch (e: Exception) {
        Log.w("VideoDownloader", "Flexible YUV decoder configure failed, retrying with source format", e)
        runCatching { codec.release() }
    }

    codec = MediaCodec.createDecoderByType(mime)
    codec.configure(extractor.getTrackFormat(trackIndex), null, null, 0)
    codec.start()
    return codec
}

private fun Image.toBitmapOrNull(): Bitmap? {
    if (format != ImageFormat.YUV_420_888) {
        Log.w("VideoDownloader", "Unexpected codec image format for GIF conversion: $format")
    }

    val nv21 = yuv420888ToNv21()
    val output = ByteArrayOutputStream()
    val compressed = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        .compressToJpeg(Rect(0, 0, width, height), 90, output)
    if (!compressed) {
        Log.w("VideoDownloader", "Failed to compress codec YUV frame for GIF conversion")
        return null
    }

    val jpegBytes = output.toByteArray()
    return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
}

private fun Image.yuv420888ToNv21(): ByteArray {
    val chromaWidth = (width + 1) / 2
    val chromaHeight = (height + 1) / 2
    val ySize = width * height
    val output = ByteArray(ySize + chromaWidth * chromaHeight * 2)
    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]
    var outputOffset = 0

    for (row in 0 until height) {
        val rowOffset = row * yPlane.rowStride
        for (col in 0 until width) {
            output[outputOffset++] = yPlane.buffer.safeGet(rowOffset + col * yPlane.pixelStride)
        }
    }

    outputOffset = ySize
    for (row in 0 until chromaHeight) {
        val uRowOffset = row * uPlane.rowStride
        val vRowOffset = row * vPlane.rowStride
        for (col in 0 until chromaWidth) {
            output[outputOffset++] = vPlane.buffer.safeGet(vRowOffset + col * vPlane.pixelStride)
            output[outputOffset++] = uPlane.buffer.safeGet(uRowOffset + col * uPlane.pixelStride)
        }
    }

    return output
}

private fun java.nio.ByteBuffer.safeGet(index: Int): Byte {
    return if (index in 0 until limit()) get(index) else 0
}

private fun MediaFormat.longOrNull(key: String): Long? {
    return if (containsKey(key)) runCatching { getLong(key) }.getOrNull() else null
}

private fun MediaFormat.intOrNull(key: String): Int? {
    return if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null
}

private fun File.headerHex(byteCount: Int = 16): String {
    return runCatching {
        inputStream().use { input ->
            val bytes = ByteArray(byteCount)
            val read = input.read(bytes)
            bytes.take(read.coerceAtLeast(0)).joinToString(" ") { "%02X".format(it) }
        }
    }.getOrDefault("unreadable")
}

private fun scaleBitmapForGif(bitmap: Bitmap): Bitmap {
    val maxSide = 360
    val largest = maxOf(bitmap.width, bitmap.height)
    if (largest <= maxSide) return bitmap
    val scale = maxSide.toFloat() / largest.toFloat()
    val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
    if (scaled !== bitmap) bitmap.recycle()
    return scaled
}

private object SimpleGifEncoder {
    fun encode(frames: List<Bitmap>, delayMs: Int): ByteArray {
        val width = frames.first().width
        val height = frames.first().height
        val output = ByteArrayOutputStream()
        try {
            val encoder = GifEncoder(output, width, height, 0)
            val options = ImageOptions().setDelay(delayMs.toLong().coerceAtLeast(20L), TimeUnit.MILLISECONDS)
            frames.forEach { frame ->
                val normalized = if (frame.width == width && frame.height == height) {
                    frame
                } else {
                    Bitmap.createScaledBitmap(frame, width, height, true)
                }
                encoder.addImage(normalized.toRgbData(), width, options)
                if (normalized !== frame) normalized.recycle()
            }
            encoder.finishEncoding()
            return output.toByteArray()
        } finally {
            frames.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    private fun Bitmap.toRgbData(): IntArray {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return IntArray(pixels.size) { index -> pixels[index] and 0x00FFFFFF }
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

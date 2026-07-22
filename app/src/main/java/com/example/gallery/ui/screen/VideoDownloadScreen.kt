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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.example.gallery.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.palette.graphics.Palette
import com.example.gallery.data.model.MediaData
import com.example.gallery.data.local.entity.VideoDownloadEntity
import com.example.gallery.service.GlobalOperationService
import com.example.gallery.ui.component.GalleryTopAppBar
import com.example.gallery.ui.state.GalleryState
import com.example.gallery.ui.theme.galleryColors
import com.example.gallery.ui.theme.GalleryThemeTokens
import com.example.gallery.util.GlobalPaletteGifEncoder
import com.example.gallery.util.VideoDownloadUrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
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
import kotlin.math.ceil
import kotlin.math.sqrt

private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .connectionSpecs(
        listOf(
            ConnectionSpec.MODERN_TLS,
            ConnectionSpec.COMPATIBLE_TLS
        )
    )
    .build()

private const val GIF_CONVERSION_MAX_SIDE_PX = 1024
private const val GIF_CONVERSION_MIN_SIDE_PX = 480
private const val GIF_CONVERSION_MIN_FPS = 6
private const val GIF_CONVERSION_MAX_FPS = 18
private const val GIF_CONVERSION_MIN_FRAMES = 2
private const val GIF_CONVERSION_MAX_FRAMES = 180
private const val GIF_CONVERSION_MAX_TOTAL_PIXELS = 36_000_000L
private const val GIF_PALETTE_VISIBLE_COLOR_COUNT = 255
private const val GIF_TRANSPARENT_COLOR_INDEX = 255
private const val GIF_PALETTE_BASE_SAMPLE_COUNT = 96_000
private const val GIF_PALETTE_MAX_SAMPLE_COUNT = 192_000
private const val GIF_PALETTE_DIFF_THRESHOLD = 36
private const val GIF_BAYER_SCALE = 3

internal enum class GifSaveFormat { MP4, GIF }

private enum class GifConversionStage(val textRes: Int) {
    ANALYZING(R.string.video_dl_gif_stage_analyzing),
    DECODING(R.string.video_dl_gif_stage_decoding),
    PALETTE(R.string.video_dl_gif_stage_palette),
    ENCODING(R.string.video_dl_gif_stage_encoding)
}

data class MediaUrlCandidate(
    val url: String,
    val bitrate: Int = 0,
    val contentType: String? = null,
    val isGifSource: Boolean = false,
    val author: String? = null,
    val tweetId: String? = null,
    val mediaKey: String = url.substringBefore('?')
) {
    val isPlayableVideo: Boolean
        get() = url.isMp4Url() || contentType.equals("video/mp4", ignoreCase = true)

    val isStillImage: Boolean
        get() = contentType?.startsWith("image/", ignoreCase = true) == true ||
            VideoDownloadUrlUtils.detectMediaType(url, null).second.startsWith("image/")

    val isDownloadableMedia: Boolean
        get() = isPlayableVideo || isStillImage
}

internal suspend fun transcodeVideoFileToGif(
    sourceFile: File,
    onProgress: (Float, Int) -> Unit = { _, _ -> }
): ByteArray = transcodeMp4ToGif(sourceFile) { progress, stage ->
    onProgress(progress, stage.textRes)
}

@Composable
fun VideoDownloadScreen(
    galleryState: GalleryState,
    onMenuClick: () -> Unit,
    initialUrl: String? = null,
    onInitialUrlConsumed: () -> Unit = {},
    onViewerVisibleChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = galleryColors
    val textSizes = GalleryThemeTokens.textSizes
    var urlInput by rememberSaveable { mutableStateOf("") }
    var showDownloadModal by rememberSaveable { mutableStateOf(false) }
    var viewerMedia by remember { mutableStateOf<MediaData?>(null) }

    val downloads by galleryState.repository.mediaDao.getAllVideoDownloads().collectAsState(initial = emptyList())
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    LocalLifecycleOwner.current

    LaunchedEffect(viewerMedia != null) {
        onViewerVisibleChanged(viewerMedia != null)
    }

    fun showOptionsForUrl(url: String) {
        if (url.isXStatusUrl() || url.isDirectMediaUrl()) {
            urlInput = url
            showDownloadModal = true
        } else {
            Toast.makeText(context, context.getString(R.string.video_dl_enter_url), Toast.LENGTH_SHORT).show()
        }
    }

    fun showOptionsFromClipboard() {
        val clip = clipboardManager.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString().orEmpty()
            if (text.isXStatusUrl() || text.isDirectMediaUrl()) {
                urlInput = text
                showDownloadModal = true
            } else {
                Toast.makeText(context, context.getString(R.string.video_dl_enter_url), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, context.getString(R.string.video_dl_enter_url), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(initialUrl) {
        if (initialUrl != null) {
            Log.d("VideoDownloader", "Consuming initial URL: $initialUrl")
            urlInput = initialUrl
            showDownloadModal = true
            // Delay consumption slightly to ensure state is settled
            kotlinx.coroutines.delay(500)
            onInitialUrlConsumed()
        }
    }

    Scaffold(
        topBar = {
            GalleryTopAppBar(
                title = stringResource(R.string.video_dl_title),
                navigationIcon = Icons.Default.Menu,
                navigationContentDescription = stringResource(R.string.drawer_menu_title),
                onNavigationClick = onMenuClick
            )
        },
        containerColor = colors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(dimensionResource(R.dimen.spacing_medium)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Link,
                contentDescription = null,
                modifier = Modifier.size(dimensionResource(R.dimen.grid_bottom_padding) * 0.64f), // 64.dp
                tint = colors.accent.copy(alpha = 0.5f)
            )

            Spacer(Modifier.height(dimensionResource(R.dimen.spacing_medium)))

            Button(
                onClick = {
                    if (initialUrl != null) {
                        showOptionsForUrl(initialUrl)
                    } else {
                        showOptionsFromClipboard()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(dimensionResource(R.dimen.header_height)),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
            ) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(dimensionResource(R.dimen.spacing_small)))
                Text(stringResource(R.string.video_dl_show_options), fontSize = textSizes.subtitle, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(dimensionResource(R.dimen.viewer_bottom_bar_height))) // 48.dp

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null, tint = colors.mutedText)
                    Spacer(Modifier.width(dimensionResource(R.dimen.spacing_small)))
                    Text(stringResource(R.string.video_dl_history), color = colors.primaryText, fontSize = textSizes.header, fontWeight = FontWeight.Bold)
                }

                if (downloads.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                galleryState.repository.mediaDao.clearVideoDownloadHistory()
                                Toast.makeText(context, context.getString(R.string.video_dl_history_cleared), Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(dimensionResource(R.dimen.icon_size_small)))
                        Spacer(Modifier.width(dimensionResource(R.dimen.spacing_tiny)))
                        Text(stringResource(R.string.video_dl_clear_history), fontSize = textSizes.small)
                    }
                }
            }

            Spacer(Modifier.height(dimensionResource(R.dimen.spacing_small)))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = dimensionResource(R.dimen.viewer_zoom_indicator_padding_top)), // 88.dp is close to 96dp
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_small))
            ) {
                items(downloads) { download ->
                    DownloadHistoryItem(
                        download = download,
                        onOpenInViewer = { media -> viewerMedia = media },
                        onDelete = {
                            scope.launch {
                                galleryState.repository.mediaDao.deleteVideoDownload(download)
                            }
                        }
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
            onDownloadStart = { selections ->
                showDownloadModal = false
                selections.forEachIndexed { index, selection ->
                    startDownloadTask(
                        context,
                        selection.media.url,
                        selection.qualityLabel,
                        galleryState,
                        originalUrl = urlInput,
                        forceGifFromVideo = selection.transcodeToGif,
                        author = selection.media.author,
                        postId = selection.media.tweetId,
                        batchIndex = index + 1,
                        batchTotal = selections.size,
                        downloadIdentity = selection.media.mediaKey
                    )
                }
                if (selections.size > 1) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.video_dl_batch_started, selections.size),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                urlInput = ""
            }
        )
    }
}

@Composable
fun DownloadHistoryItem(
    download: VideoDownloadEntity,
    onOpenInViewer: (MediaData) -> Unit,
    onDelete: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = galleryColors
    val textSizes = GalleryThemeTokens.textSizes
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    val completedStatus = stringResource(R.string.video_dl_status_completed)
    val pendingStatus = stringResource(R.string.video_dl_status_pending)
    val failedStatus = stringResource(R.string.video_dl_status_failed)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.card)
    ) {
        Row(
            modifier = Modifier.padding(dimensionResource(R.dimen.spacing_base)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DownloadMediaPreview(
                uri = download.savePath.takeIf { download.status == completedStatus && it != pendingStatus },
                modifier = Modifier
                    .width(dimensionResource(R.dimen.viewer_clock_battery_padding_top)) // 96.dp
                    .height(dimensionResource(R.dimen.icon_size_extra_large) * 1.6f), // 64.dp
                onClick = {
                    buildDownloadMediaData(context, download)?.let(onOpenInViewer)
                }
            )
            Spacer(Modifier.width(dimensionResource(R.dimen.spacing_base)))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        download.title,
                        color = colors.primaryText,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    androidx.compose.material3.IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(dimensionResource(R.dimen.icon_size_medium))
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = colors.mutedText,
                            modifier = Modifier.size(dimensionResource(R.dimen.icon_size_edit))
                        )
                    }
                }
                Spacer(Modifier.height(dimensionResource(R.dimen.spacing_micro)))
                Text(download.url, color = colors.mutedText, fontSize = textSizes.small, maxLines = 1)
                Spacer(Modifier.height(dimensionResource(R.dimen.spacing_tiny)))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(dateFormat.format(Date(download.downloadDate)), color = colors.mutedText, fontSize = textSizes.extraSmall)
                    Text(
                        download.status,
                        color = when (download.status) {
                            completedStatus -> colors.success
                            failedStatus -> colors.danger
                            else -> colors.accent
                        },
                        fontSize = textSizes.extraSmall
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
    val colors = galleryColors
    val textSizes = GalleryThemeTokens.textSizes
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(dimensionResource(R.dimen.radius_small)))
            .background(colors.background)
            .clickable(enabled = !uri.isNullOrBlank(), onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (!uri.isNullOrBlank()) {
            val context = LocalContext.current
            val mimeType = remember(uri) {
                runCatching {
                    context.contentResolver.getType(Uri.parse(uri))
                }.getOrNull() ?: VideoDownloadUrlUtils.detectMediaType(uri, null).second
            }
            val isVideo = mimeType.startsWith("video/")
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(uri)
                    .apply { if (isVideo) videoFrameMillis(1000) }
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (isVideo) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = colors.primaryText.copy(alpha = 0.85f),
                    modifier = Modifier.size(dimensionResource(R.dimen.spacing_extra_large))
                )
            }
        } else {
            Text(stringResource(R.string.video_dl_placeholder), color = colors.mutedText, fontSize = textSizes.small)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadOptionsModal(
    url: String,
    galleryState: GalleryState,
    onDismiss: () -> Unit,
    onDownloadStart: (List<MediaDownloadSelection>) -> Unit
) {
    val context = LocalContext.current
    val colors = galleryColors
    val textSizes = GalleryThemeTokens.textSizes
    var isDuplicate by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var resolvedUrls by remember { mutableStateOf<List<MediaUrlCandidate>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val selectedUrlByMediaKey = remember { mutableStateMapOf<String, String>() }
    val selectedMediaKeys = remember { mutableStateMapOf<String, Boolean>() }
    var gifSaveFormat by remember(url) { mutableStateOf(GifSaveFormat.GIF) }

    val isGifPost = resolvedUrls.any { it.isGifSource }
    val hasDirectGif = resolvedUrls.any { it.isDirectGifCandidate }
    val hasPlayableMp4 = resolvedUrls.any { it.isPlayableVideo }
    val showGifFormatChoice = isGifPost || hasDirectGif
    val downloadGroups = groupDownloadCandidates(resolvedUrls)
    val selections = buildMediaDownloadSelections(
        candidates = resolvedUrls,
        gifSaveFormat = gifSaveFormat,
        selectedUrlByMediaKey = selectedUrlByMediaKey,
        selectedMediaKeys = selectedMediaKeys.filterValues { it }.keys
    )
    val previewMedia = selections.firstOrNull()?.media

    LaunchedEffect(url) {
        isLoading = true
        error = null
        resolvedUrls = emptyList()
        selectedUrlByMediaKey.clear()
        selectedMediaKeys.clear()
        isDuplicate = galleryState.repository.mediaDao.isVideoDownloaded(url)

        val resolved = withContext(Dispatchers.IO) {
            try {
                resolveXVideoUrls(url)
            } catch (e: Exception) {
                error = context.getString(R.string.msg_error_resolve_failed, e.localizedMessage)
                emptyList()
            }
        }
        resolvedUrls = resolved
        if (resolved.isEmpty() && error == null) {
            error = context.getString(R.string.msg_error_fetch_media_url)
        }
        isLoading = false
    }

    LaunchedEffect(resolvedUrls, gifSaveFormat) {
        val availableMediaKeys = groupDownloadCandidates(resolvedUrls)
            .mapNotNull { group -> group.firstOrNull()?.mediaKey }
            .toSet()
        selectedUrlByMediaKey.keys
            .filterNot { it in availableMediaKeys }
            .forEach(selectedUrlByMediaKey::remove)
        selectedMediaKeys.keys
            .filterNot { it in availableMediaKeys }
            .forEach(selectedMediaKeys::remove)
        availableMediaKeys.forEach { mediaKey ->
            if (mediaKey !in selectedMediaKeys) {
                selectedMediaKeys[mediaKey] = true
            }
        }
        buildMediaDownloadSelections(resolvedUrls, gifSaveFormat).forEach { selection ->
            if (selection.media.mediaKey !in selectedUrlByMediaKey) {
                selectedUrlByMediaKey[selection.media.mediaKey] = selection.media.url
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(stringResource(R.string.video_dl_settings), color = colors.primaryText) },
        text = {
            when {
                isLoading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(color = colors.accent)
                        Spacer(Modifier.height(dimensionResource(R.dimen.spacing_small)))
                        Text(stringResource(R.string.video_dl_resolving), color = colors.secondaryText)
                    }
                }
                error != null -> {
                    Text(error!!, color = colors.danger)
                }
                else -> {
                    Column {
                        DownloadMediaPreview(
                            uri = previewMedia?.url,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        )
                        Spacer(Modifier.height(dimensionResource(R.dimen.spacing_base)))
                        if (isDuplicate) {
                            Text(stringResource(R.string.video_dl_already_downloaded), color = colors.accent, fontSize = textSizes.subtitle)
                            Spacer(Modifier.height(dimensionResource(R.dimen.spacing_small)))
                        }
                        if (downloadGroups.size > 1) {
                            Text(
                                stringResource(R.string.video_dl_detected_media_count, downloadGroups.size),
                                color = colors.primaryText,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(dimensionResource(R.dimen.spacing_small)))
                        }
                        if (showGifFormatChoice) {
                            Text(stringResource(R.string.video_dl_select_format), color = colors.secondaryText)
                            Spacer(Modifier.height(dimensionResource(R.dimen.spacing_tiny)))
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                GifSaveFormat.entries.forEachIndexed { index, format ->
                                    SegmentedButton(
                                        selected = gifSaveFormat == format,
                                        onClick = { gifSaveFormat = format },
                                        enabled = format != GifSaveFormat.MP4 || hasPlayableMp4,
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = index,
                                            count = GifSaveFormat.entries.size
                                        )
                                    ) {
                                        Text(format.name)
                                    }
                                }
                            }
                            Spacer(Modifier.height(dimensionResource(R.dimen.spacing_small)))
                            Text(
                                text = when {
                                    gifSaveFormat == GifSaveFormat.MP4 -> stringResource(R.string.video_dl_format_mp4_desc)
                                    hasDirectGif -> stringResource(R.string.video_dl_format_direct_gif_desc)
                                    else -> stringResource(R.string.video_dl_format_gif_desc)
                                },
                                color = colors.secondaryText
                            )
                            Spacer(Modifier.height(dimensionResource(R.dimen.spacing_small)))
                        }
                        if (downloadGroups.isNotEmpty()) {
                            if (downloadGroups.size > 1) {
                                Text(
                                    text = stringResource(R.string.video_dl_select_media),
                                    color = colors.secondaryText
                                )
                                Spacer(Modifier.height(dimensionResource(R.dimen.spacing_tiny)))
                            }
                            Box(
                                modifier = Modifier
                                    .heightIn(max = dimensionResource(R.dimen.grid_placeholder_height) * 2)
                                    .fillMaxWidth()
                            ) {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_small))
                                ) {
                                    itemsIndexed(
                                        items = downloadGroups,
                                        key = { groupIndex, group -> group.firstOrNull()?.mediaKey ?: groupIndex }
                                    ) { groupIndex, group ->
                                        val mediaKey = group.first().mediaKey
                                        val isGroupSelected = selectedMediaKeys[mediaKey] != false
                                        val directGifSelected = gifSaveFormat == GifSaveFormat.GIF &&
                                            group.any { it.isDirectGifCandidate }
                                        val variants = if (directGifSelected) {
                                            emptyList()
                                        } else {
                                            group.filter { it.isPlayableVideo }
                                                .sortedByDescending { it.bitrate }
                                        }
                                        val mediaType = when {
                                            directGifSelected -> stringResource(R.string.video_dl_media_gif)
                                            group.any { it.isStillImage } -> stringResource(R.string.video_dl_media_image)
                                            else -> null
                                        }

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(dimensionResource(R.dimen.radius_small)))
                                                .border(
                                                    width = dimensionResource(R.dimen.spacing_hairline),
                                                    color = colors.divider,
                                                    shape = RoundedCornerShape(dimensionResource(R.dimen.radius_small))
                                                )
                                                .background(colors.field)
                                                .padding(dimensionResource(R.dimen.spacing_small)),
                                            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_tiny))
                                        ) {
                                            if (downloadGroups.size > 1) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            selectedMediaKeys[mediaKey] = !isGroupSelected
                                                        }
                                                        .padding(vertical = dimensionResource(R.dimen.spacing_tiny)),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Checkbox(
                                                        checked = isGroupSelected,
                                                        onCheckedChange = { selected ->
                                                            selectedMediaKeys[mediaKey] = selected
                                                        }
                                                    )
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = context.getString(R.string.video_dl_media_number, groupIndex + 1),
                                                            color = colors.primaryText,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        mediaType?.let { type ->
                                                            Text(type, color = colors.secondaryText, fontSize = textSizes.small)
                                                        }
                                                    }
                                                }
                                            } else if (mediaType != null) {
                                                Text(mediaType, color = colors.secondaryText, fontSize = textSizes.small)
                                            }

                                            if (variants.isNotEmpty()) {
                                                Text(
                                                    text = stringResource(R.string.video_dl_select_quality),
                                                    color = colors.secondaryText,
                                                    fontSize = textSizes.small
                                                )
                                                variants.forEach { variant ->
                                                    val isVariantSelected = selectedUrlByMediaKey[mediaKey] == variant.url
                                                    val label = buildString {
                                                        if (variant.bitrate > 0) append("${variant.bitrate / 1000} kbps")
                                                        Regex("""(\d+x\d+)""").find(variant.url)?.value?.let { resolution ->
                                                            if (isNotEmpty()) append(" ")
                                                            append("($resolution)")
                                                        }
                                                        if (isEmpty()) append(variant.url.substringAfterLast('/').substringBefore('?'))
                                                    }
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(dimensionResource(R.dimen.radius_small)))
                                                            .background(
                                                                if (isVariantSelected) colors.background else colors.surfaceVariant
                                                            )
                                                            .selectable(
                                                                selected = isVariantSelected,
                                                                enabled = isGroupSelected,
                                                                role = Role.RadioButton,
                                                                onClick = { selectedUrlByMediaKey[mediaKey] = variant.url }
                                                            )
                                                            .padding(
                                                                horizontal = dimensionResource(R.dimen.spacing_small),
                                                                vertical = dimensionResource(R.dimen.spacing_tiny)
                                                            ),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        RadioButton(
                                                            selected = isVariantSelected,
                                                            onClick = null,
                                                            enabled = isGroupSelected
                                                        )
                                                        Text(
                                                            text = label,
                                                            color = if (isGroupSelected) colors.primaryText else colors.mutedText,
                                                            fontSize = textSizes.small
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
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
                    if (selections.isNotEmpty()) onDownloadStart(selections)
                },
                enabled = !isLoading && error == null && selections.isNotEmpty()
            ) {
                Text(
                    if (downloadGroups.size > 1) stringResource(R.string.video_dl_download_selected, selections.size)
                    else if (showGifFormatChoice && gifSaveFormat == GifSaveFormat.GIF) stringResource(R.string.video_dl_btn_gif)
                    else if (showGifFormatChoice) stringResource(R.string.video_dl_btn_mp4)
                    else if (isDuplicate) stringResource(R.string.video_dl_btn_again)
                    else stringResource(R.string.video_dl_btn_start)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel), color = colors.mutedText) }
        }
    )
}

internal data class MediaDownloadSelection(
    val media: MediaUrlCandidate,
    val qualityLabel: String,
    val transcodeToGif: Boolean
)

internal fun groupDownloadCandidates(
    candidates: List<MediaUrlCandidate>
): List<List<MediaUrlCandidate>> = candidates
    .filter { it.isDownloadableMedia }
    .groupBy { it.mediaKey }
    .values
    .map { group ->
        // X includes a still preview URL alongside video variants.  It is not a
        // separately downloadable item, so keep it out of the video's choices.
        if (group.any { it.isPlayableVideo }) {
            group.filterNot { it.isStillImage && !it.isDirectGifCandidate }
        } else {
            group
        }
    }
    .toList()

internal fun buildMediaDownloadSelections(
    candidates: List<MediaUrlCandidate>,
    gifSaveFormat: GifSaveFormat,
    selectedUrlByMediaKey: Map<String, String> = emptyMap(),
    selectedMediaKeys: Set<String>? = null
): List<MediaDownloadSelection> = groupDownloadCandidates(candidates)
    .filter { group ->
        selectedMediaKeys == null || group.firstOrNull()?.mediaKey?.let { it in selectedMediaKeys } == true
    }
    .mapNotNull { group ->
    val mediaKey = group.first().mediaKey
    val directGif = group.firstOrNull { it.isDirectGifCandidate }
    val playable = group.filter { it.isPlayableVideo }
    val selectedCandidate = selectedUrlByMediaKey[mediaKey]
        ?.let { selectedUrl -> group.firstOrNull { it.url == selectedUrl && it.isDownloadableMedia } }
    val selectedPlayable = selectedCandidate?.takeIf { it.isPlayableVideo }
    val bestPlayable = selectedPlayable ?: playable.maxByOrNull { it.bitrate }
    val stillImage = selectedCandidate?.takeIf { it.isStillImage && !it.isDirectGifCandidate }
        ?: group.firstOrNull { it.isStillImage && !it.isDirectGifCandidate }
    val gifSource = directGif != null || group.any { it.isGifSource }
    val selected = when {
        gifSource && gifSaveFormat == GifSaveFormat.GIF -> directGif ?: bestPlayable
        selectedCandidate != null -> selectedCandidate
        else -> bestPlayable ?: stillImage ?: directGif
    } ?: return@mapNotNull null
    val transcodeToGif = gifSource &&
        gifSaveFormat == GifSaveFormat.GIF &&
        !selected.isDirectGifCandidate &&
        selected.isPlayableVideo
    val qualityLabel = when {
        selected.isDirectGifCandidate || transcodeToGif -> "GIF"
        selected.bitrate > 0 -> "${selected.bitrate / 1000}k"
        selected.isStillImage -> "Image"
        else -> "MP4"
    }
    MediaDownloadSelection(selected, qualityLabel, transcodeToGif)
}

private val MediaUrlCandidate.isDirectGifCandidate: Boolean
    get() = url.isGifUrl() || contentType.equals("image/gif", ignoreCase = true)

private fun resolveXVideoUrls(statusUrl: String): List<MediaUrlCandidate> {
    if (!statusUrl.isXStatusUrl()) return listOf(MediaUrlCandidate(statusUrl))

    val normalizedUrl = if (!statusUrl.startsWith("http")) "https://$statusUrl" else statusUrl
    val baseUrl = normalizedUrl.substringBefore("?")
    val syndicationApiUrls = VideoDownloadUrlUtils.buildXSyndicationApiUrls(baseUrl)
    val fallbackApiUrls = buildXApiUrls(baseUrl)

    return try {
        (syndicationApiUrls + fallbackApiUrls).firstNotNullOfOrNull { apiUrl ->
            val request = Request.Builder()
                .url(apiUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Origin", "https://platform.twitter.com")
                .header("Referer", baseUrl)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use null
                }

                val body = response.body?.string() ?: return@use null
                val json = JSONObject(body)
                val tweet = json.optJSONObject("tweet") ?: json
                val author = tweet.optJSONObject("author")?.optString("screen_name")
                    ?: tweet.optJSONObject("user")?.optString("screen_name")
                    ?: tweet.optJSONObject("user")?.optString("username")
                val tweetId = tweet.optString("id_str").ifBlank { tweet.optString("id") }

                extractMediaUrlCandidates(json, author, tweetId.ifBlank { VideoDownloadUrlUtils.extractXPostId(baseUrl).orEmpty() })
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

private fun extractMediaUrlCandidates(json: JSONObject, author: String?, tweetId: String?): List<MediaUrlCandidate> {
    val tweet = json.optJSONObject("tweet") ?: json
    val mediaObjects = buildList {
        tweet.optJSONArray("media_extended")?.let { addAll(it.toJsonObjects()) }
        tweet.optJSONArray("media")?.let { addAll(it.toJsonObjects()) }
        tweet.optJSONArray("mediaDetails")?.let { addAll(it.toJsonObjects()) }
        tweet.optJSONArray("media_details")?.let { addAll(it.toJsonObjects()) }
        tweet.optJSONObject("media")?.let { media ->
            media.optJSONArray("all")?.let { addAll(it.toJsonObjects()) }
            media.optJSONArray("videos")?.let { addAll(it.toJsonObjects()) }
            media.optJSONArray("photos")?.let { addAll(it.toJsonObjects()) }
        }
    }

    val knownMediaCandidates = mediaObjects.flatMapIndexed { index, media ->
        val isGifSource = media.isGifMediaObject()
        val mediaKey = media.mediaCandidateKey(index)
        buildList {
            media.gifDirectUrls().forEach { add(MediaUrlCandidate(it, contentType = "image/gif", isGifSource = true, author = author, tweetId = tweetId, mediaKey = mediaKey)) }
            media.optJSONArray("variants")?.let { addAll(it.toCandidates(isGifSource, author, tweetId, mediaKey)) }
            media.optJSONObject("video_info")?.optJSONArray("variants")?.let { addAll(it.toCandidates(isGifSource, author, tweetId, mediaKey)) }
            media.optString("url").takeIf { it.isDirectMediaUrl() }?.let { add(MediaUrlCandidate(it, isGifSource = isGifSource || it.isLikelyXGifVideoUrl(), author = author, tweetId = tweetId, mediaKey = mediaKey)) }
            media.optString("media_url_https").takeIf { it.isDirectMediaUrl() }?.let { add(MediaUrlCandidate(it, isGifSource = isGifSource || it.isLikelyXGifVideoUrl(), author = author, tweetId = tweetId, mediaKey = mediaKey)) }
            media.optString("media_url").takeIf { it.isDirectMediaUrl() }?.let { add(MediaUrlCandidate(it, isGifSource = isGifSource || it.isLikelyXGifVideoUrl(), author = author, tweetId = tweetId, mediaKey = mediaKey)) }
            media.optString("display_url").takeIf { it.isDirectMediaUrl() }?.let { add(MediaUrlCandidate(it, isGifSource = isGifSource || it.isLikelyXGifVideoUrl(), author = author, tweetId = tweetId, mediaKey = mediaKey)) }
            media.optString("expanded_url").takeIf { it.isDirectMediaUrl() }?.let { add(MediaUrlCandidate(it, isGifSource = isGifSource || it.isLikelyXGifVideoUrl(), author = author, tweetId = tweetId, mediaKey = mediaKey)) }
            media.optString("thumbnail_url").takeIf { it.isDirectMediaUrl() }?.let { add(MediaUrlCandidate(it, isGifSource = isGifSource || it.isLikelyXGifVideoUrl(), author = author, tweetId = tweetId, mediaKey = mediaKey)) }
            media.optString("thumb").takeIf { it.isDirectMediaUrl() }?.let { add(MediaUrlCandidate(it, isGifSource = isGifSource || it.isLikelyXGifVideoUrl(), author = author, tweetId = tweetId, mediaKey = mediaKey)) }
            media.optJSONArray("urls")?.let { addAll(it.toStringCandidates(isGifSource, author, tweetId, mediaKey)) }
        }
    }

    return if (knownMediaCandidates.any { it.isDownloadableMedia }) {
        knownMediaCandidates
    } else {
        knownMediaCandidates + json.collectNestedMediaCandidates(author, tweetId)
    }
}

private fun JSONObject.mediaCandidateKey(index: Int): String {
    listOf("media_key", "mediaKey", "id_str", "id", "key").forEach { key ->
        optString(key).takeIf { it.isNotBlank() }?.let { return it }
    }
    listOf("media_url_https", "media_url", "thumbnail_url", "thumb", "url").forEach { key ->
        optString(key).takeIf { it.isNotBlank() }?.let { return it.substringBefore('?') }
    }
    return "media-$index"
}

private fun JSONObject.collectNestedMediaCandidates(author: String?, tweetId: String?): List<MediaUrlCandidate> = buildList {
    lateinit var visitObject: (JSONObject, Boolean) -> Unit
    lateinit var visitArray: (JSONArray, Boolean) -> Unit

    visitArray = { array, inheritedGifSource ->
        for (index in 0 until array.length()) {
            when (val child = array.opt(index)) {
                is JSONObject -> visitObject(child, inheritedGifSource)
                is String -> child.takeIf { it.isDirectMediaUrl() }?.let {
                    add(MediaUrlCandidate(it, isGifSource = inheritedGifSource || it.isLikelyXGifVideoUrl(), author = author, tweetId = tweetId))
                }
            }
        }
    }

    visitObject = { obj, inheritedGifSource ->
        val isGifSource = inheritedGifSource || obj.isGifMediaObject()
        val mediaKey = obj.mediaCandidateKey(obj.hashCode())
        obj.gifDirectUrls().forEach { add(MediaUrlCandidate(it, contentType = "image/gif", isGifSource = true, author = author, tweetId = tweetId, mediaKey = mediaKey)) }
        obj.optJSONArray("variants")?.let { addAll(it.toCandidates(isGifSource, author, tweetId, mediaKey)) }
        obj.optJSONObject("video_info")?.optJSONArray("variants")?.let { addAll(it.toCandidates(isGifSource, author, tweetId, mediaKey)) }
        listOf("url", "media_url_https", "media_url", "display_url", "expanded_url", "thumbnail_url", "thumb").forEach { key ->
            obj.optString(key).takeIf { it.isDirectMediaUrl() }?.let {
                add(MediaUrlCandidate(it, isGifSource = isGifSource || it.isLikelyXGifVideoUrl(), author = author, tweetId = tweetId, mediaKey = mediaKey))
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
        "downloadGifUrl",
        "gif_url_https",
        "gifUrlHttps",
        "source_gif_url",
        "sourceGifUrl",
        "raw_gif_url",
        "rawGifUrl",
        "gif"
    )

    val directUrls = mutableListOf<String>()
    gifKeys.forEach { key ->
        optString(key)
            .takeIf { it.isDirectMediaUrl() && it.isGifUrl() }
            ?.let(directUrls::add)
    }
    keys().forEach { key ->
        if (key.contains("gif", ignoreCase = true)) {
            optString(key)
                .takeIf { it.isDirectMediaUrl() && it.isGifUrl() }
                ?.let(directUrls::add)
        }
    }
    return directUrls.distinct()
}

private fun JSONArray.toJsonObjects(): List<JSONObject> = buildList {
    for (index in 0 until length()) {
        optJSONObject(index)?.let { add(it) }
    }
}

private fun JSONArray.toCandidates(
    isGifSource: Boolean = false,
    author: String? = null,
    tweetId: String? = null,
    mediaKey: String? = null
): List<MediaUrlCandidate> = buildList {
    for (index in 0 until length()) {
        val variant = optJSONObject(index) ?: continue
        val url = variant.optString("url").takeIf { it.isDirectMediaUrl() } ?: continue
        val contentType = variant.optString("content_type")
            .ifBlank { variant.optString("contentType") }
            .ifBlank { null }
        if (contentType == "application/x-mpegURL" || url.contains(".m3u8")) continue
        val variantIsGifSource = isGifSource || variant.isGifMediaObject()
        add(MediaUrlCandidate(url, variant.optInt("bitrate", 0), contentType, variantIsGifSource || url.isLikelyXGifVideoUrl(), author, tweetId, mediaKey ?: url.substringBefore('?')))
    }
}

private fun JSONArray.toStringCandidates(
    isGifSource: Boolean = false,
    author: String? = null,
    tweetId: String? = null,
    mediaKey: String? = null
): List<MediaUrlCandidate> = buildList {
    for (index in 0 until length()) {
        val url = optString(index).takeIf { it.isDirectMediaUrl() } ?: continue
        add(MediaUrlCandidate(url, isGifSource = isGifSource || url.isLikelyXGifVideoUrl(), author = author, tweetId = tweetId, mediaKey = mediaKey ?: url.substringBefore('?')))
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

private fun String.isLikelyXGifVideoUrl(): Boolean {
    return VideoDownloadUrlUtils.isLikelyXGifVideoUrl(this)
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
    forceGifFromVideo: Boolean = false,
    author: String? = null,
    postId: String? = null,
    batchIndex: Int = 1,
    batchTotal: Int = 1,
    downloadIdentity: String? = null
) {
    val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
    scope.launch {
        val timestamp = System.currentTimeMillis()
        val checkUrl = originalUrl ?: url
        val historyUrl = if (batchTotal > 1) {
            val identityHash = Integer.toUnsignedString((downloadIdentity ?: url).hashCode(), 16)
            "$checkUrl#gallery-media=$identityHash"
        } else {
            checkUrl
        }
        val pendingStatus = context.getString(R.string.video_dl_status_pending)

        val finalAuthor = author ?: originalUrl?.let { VideoDownloadUrlUtils.extractXUserId(it) }
        val finalPostId = postId ?: originalUrl?.let { VideoDownloadUrlUtils.extractXPostId(it) }

        Log.i("VideoDownloader", "========================================")
        Log.i("VideoDownloader", "DOWNLOAD START")
        Log.i("VideoDownloader", "Original URL: $checkUrl")
        Log.i("VideoDownloader", "Resolved URL: $url")
        Log.i("VideoDownloader", "Author: $finalAuthor, PostID: $finalPostId")
        Log.i("VideoDownloader", "========================================")

        val displayTitle = if (finalAuthor != null && finalPostId != null) {
            buildString {
                append("$finalAuthor / $finalPostId")
                if (batchTotal > 1) append(" ($batchIndex/$batchTotal)")
            }
        } else {
            context.getString(R.string.video_dl_media_title_format, timestamp, quality)
        }

        val entity = VideoDownloadEntity(
            url = historyUrl,
            title = displayTitle,
            savePath = pendingStatus,
            downloadDate = timestamp,
            status = context.getString(R.string.video_dl_status_downloading)
        )
        galleryState.repository.mediaDao.insertVideoDownload(entity)
        val operationId = GlobalOperationService.startOperation(
            title = if (batchTotal > 1) {
                context.getString(R.string.video_dl_batch_operation, batchIndex, batchTotal)
            } else {
                context.getString(R.string.video_dl_downloading)
            },
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

                val filename = if (finalAuthor != null && finalPostId != null) {
                    if (batchTotal > 1) {
                        "${finalAuthor}_${finalPostId}_${batchIndex}.$extension"
                    } else {
                        context.getString(R.string.video_dl_filename_twitter_format, finalAuthor, finalPostId, extension)
                    }
                } else {
                    if (batchTotal > 1) {
                        "X_Media_${timestamp}_${batchIndex}.$extension"
                    } else {
                        context.getString(R.string.video_dl_filename_format, timestamp, extension)
                    }
                }

                Log.i("VideoDownloader", "Detected Extension: $extension")
                Log.i("VideoDownloader", "Detected MIME Type: $mimeType")
                Log.i("VideoDownloader", "Final Filename: $filename")

                val outputBytes: ByteArray? = if (shouldTranscodeGif) {
                    val tempFile = File.createTempFile("x_gif_source_", ".mp4", context.cacheDir)
                    try {
                        tempFile.outputStream().use { output ->
                            responseBody.byteStream().use { input ->
                                copyDownloadWithProgress(
                                    context = context,
                                    input = input,
                                    output = output,
                                    totalBytes = totalBytes,
                                    operationId = operationId,
                                    progressEnd = 0.62f
                                )
                            }
                        }
                        GlobalOperationService.updateProgress(
                            0.63f,
                            context.getString(R.string.video_dl_gif_stage_analyzing),
                            operationId
                        )
                        transcodeMp4ToGif(tempFile) { conversionProgress, stage ->
                            val overallProgress = 0.62f + conversionProgress.coerceIn(0f, 1f) * 0.35f
                            GlobalOperationService.updateProgress(
                                overallProgress,
                                context.getString(stage.textRes),
                                operationId
                            )
                        }
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
                        val relativePath = if (mimeType.startsWith("video/")) {
                            context.getString(R.string.video_dl_rel_path_movies)
                        } else {
                            context.getString(R.string.video_dl_rel_path_pictures)
                        }
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
                        GlobalOperationService.updateProgress(0.98f, context.getString(R.string.video_dl_saving_gif), operationId)
                        outputStream.write(outputBytes)
                    } else {
                        responseBody.byteStream().use { input ->
                            copyDownloadWithProgress(
                                context = context,
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

                val updateValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DATE_ADDED, timestamp / 1000)
                    put(android.provider.MediaStore.MediaColumns.DATE_MODIFIED, timestamp / 1000)
                    put(android.provider.MediaStore.MediaColumns.DATE_TAKEN, timestamp)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                        put(android.provider.MediaStore.MediaColumns.DATE_EXPIRES, null as Long?)
                    }
                }
                resolver.update(uri, updateValues, null, null)

                val actualPath = try {
                    val cursor = resolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)
                    cursor?.use { if (it.moveToFirst()) it.getString(0) else uri.toString() } ?: uri.toString()
                } catch (_: Exception) {
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

                galleryState.repository.mediaDao.insertVideoDownload(
                    entity.copy(
                        status = context.getString(R.string.video_dl_status_completed),
                        savePath = actualPath
                    )
                )
                GlobalOperationService.updateProgress(1f, context.getString(R.string.video_dl_complete), operationId)

                if (batchTotal <= 1) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.msg_download_complete_format, extension), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VideoDownloader", "Task failed", e)
            galleryState.repository.mediaDao.insertVideoDownload(entity.copy(status = context.getString(R.string.video_dl_status_failed)))
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.msg_error_download_failed, e.localizedMessage), Toast.LENGTH_LONG).show()
            }
        } finally {
            GlobalOperationService.finishOperation(operationId)
        }
    }
}

private suspend fun copyDownloadWithProgress(
    context: Context,
    input: InputStream,
    output: OutputStream,
    totalBytes: Long,
    operationId: String,
    progressEnd: Float
) = withContext(Dispatchers.IO) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val startedAt = SystemClock.elapsedRealtime()
    var downloadedBytes = 0L
    var lastUpdateAt = 0L

    while (true) {
        ensureActive()
        val read = input.read(buffer)
        if (read < 0) break
        output.write(buffer, 0, read)
        downloadedBytes += read

        val now = SystemClock.elapsedRealtime()
        if (now - lastUpdateAt >= 200L || downloadedBytes >= totalBytes) {
            val elapsedMs = (now - startedAt).coerceAtLeast(1L)
            val fraction = if (totalBytes > 0L) {
                (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f..1f)
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
                append(formatDownloadSize(context, downloadedBytes))
                if (totalBytes > 0L) {
                    append(" / ")
                    append(formatDownloadSize(context, totalBytes))
                }
                append("  ")
                append(context.getString(R.string.video_dl_size_per_second, formatDownloadSize(context, bytesPerSecond)))
                if (remainingMs != null && elapsedMs >= 500L) {
                    append("  " + context.getString(R.string.video_dl_remaining) + " ")
                    append(formatDownloadEta(context, remainingMs))
                } else {
                    append("  " + context.getString(R.string.video_dl_calculating_time))
                }
            }
            GlobalOperationService.updateProgress(fraction * progressEnd, status, operationId)
            lastUpdateAt = now
        }
    }
}

private fun formatDownloadSize(context: Context, bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    return when {
        safeBytes >= 1024L * 1024L -> context.getString(R.string.video_dl_size_mb, safeBytes / (1024f * 1024f))
        safeBytes >= 1024L -> context.getString(R.string.video_dl_size_kb, safeBytes / 1024f)
        else -> context.getString(R.string.video_dl_size_b, safeBytes)
    }
}

private fun formatDownloadEta(context: Context, durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) + 999L) / 1000L
    return if (totalSeconds < 60L) {
        context.getString(R.string.video_dl_eta_seconds, totalSeconds)
    } else {
        context.getString(R.string.video_dl_eta_minutes_seconds, totalSeconds / 60L, totalSeconds % 60L)
    }
}

private suspend fun transcodeMp4ToGif(
    sourceFile: File,
    onProgress: (Float, GifConversionStage) -> Unit = { _, _ -> }
): ByteArray = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    val frames = mutableListOf<Bitmap>()
    var input: FileInputStream? = null
    val conversionStartedAt = SystemClock.elapsedRealtime()
    try {
        onProgress(0f, GifConversionStage.ANALYZING)
        patchMp4ForAndroidExtractor(sourceFile)
        try {
            input = FileInputStream(sourceFile)
            retriever.setDataSource(input.fd)
        } catch (fdError: Exception) {
            Log.w("VideoDownloader", "FileDescriptor data source failed, retrying by path", fdError)
            runCatching { input?.close() }
            input = null
            retriever.setDataSource(sourceFile.absolutePath)
        }
        val rawDurationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        val durationMs = rawDurationMs?.coerceAtLeast(300L) ?: 3000L
        val videoWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val videoHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        val sourceFrameRate = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            ?.toFloatOrNull()
        val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        val profile = createGifConversionProfile(
            durationMs = durationMs,
            sourceWidth = videoWidth?.toIntOrNull(),
            sourceHeight = videoHeight?.toIntOrNull(),
            sourceFrameRate = sourceFrameRate
        )
        val targetFrameCount = profile.frameCount
        var effectiveDurationMs = durationMs
        var decoderPath = "MediaCodec"

        onProgress(0.03f, GifConversionStage.DECODING)
        val codecFrames = decodeGifFramesWithCodec(sourceFile, targetFrameCount, profile.maxSidePx) { decodeProgress ->
            onProgress(0.03f + decodeProgress.coerceIn(0f, 1f) * 0.27f, GifConversionStage.DECODING)
        }
        if (codecFrames.frames.size >= minOf(2, targetFrameCount)) {
            frames.addAll(codecFrames.frames)
            effectiveDurationMs = codecFrames.durationMs ?: durationMs
            onProgress(0.50f, GifConversionStage.DECODING)
        } else {
            if (codecFrames.frames.isNotEmpty()) {
                Log.w(
                    "VideoDownloader",
                    "MediaCodec returned too few GIF frames: ${codecFrames.frames.size}/$targetFrameCount"
                )
                codecFrames.frames.forEach { if (!it.isRecycled) it.recycle() }
            }
            decoderPath = "MediaMetadataRetriever"
        }

        if (frames.isEmpty()) {
            for (index in 0 until targetFrameCount) {
                ensureActive()
                val timeUs = (durationMs * 1000L * index / targetFrameCount).coerceAtLeast(0L)
                val frame = try {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.frameAtTime
                } catch (e: Exception) {
                    null
                }
                if (frame != null) {
                    frames.add(scaleBitmapForGif(frame, profile.maxSidePx))
                }
                onProgress(
                    0.30f + (index + 1f) / targetFrameCount.toFloat() * 0.20f,
                    GifConversionStage.DECODING
                )
            }
        }

        if (frames.isEmpty()) {
            throw Exception(
                "GIF conversion failed: no frames (size=${sourceFile.length()}, header=${sourceFile.headerHex()}, durationMs=$rawDurationMs, width=$videoWidth, height=$videoHeight, mime=$mimeType)"
            )
        }

        val decodeElapsedMs = SystemClock.elapsedRealtime() - conversionStartedAt
        Log.i(
            "VideoDownloader",
            "GIF decode complete: path=$decoderPath, frames=${frames.size}/$targetFrameCount, " +
                "durationMs=$effectiveDurationMs, sourceFps=$sourceFrameRate, target=${profile.frameCount} frames/${profile.maxSidePx}px, " +
                    "size=${videoWidth}x$videoHeight, elapsedMs=$decodeElapsedMs"
        )

        onProgress(0.52f, GifConversionStage.PALETTE)
        val output = withContext(Dispatchers.Default) {
            OptimizedGifEncoder.encode(frames, effectiveDurationMs) { encodeProgress, stage ->
                onProgress(0.52f + encodeProgress.coerceIn(0f, 1f) * 0.47f, stage)
            }
        }
        onProgress(1f, GifConversionStage.ENCODING)
        Log.i(
            "VideoDownloader",
            "GIF conversion complete: bytes=${output.size}, totalMs=${SystemClock.elapsedRealtime() - conversionStartedAt}"
        )
        return@withContext output
    } finally {
        frames.forEach { if (!it.isRecycled) it.recycle() }
        runCatching { retriever.release() }
        runCatching { input?.close() }
    }
}

private data class GifConversionProfile(
    val frameCount: Int,
    val maxSidePx: Int
)

private fun createGifConversionProfile(
    durationMs: Long,
    sourceWidth: Int? = null,
    sourceHeight: Int? = null,
    sourceFrameRate: Float? = null
): GifConversionProfile {
    if (sourceWidth == null || sourceHeight == null || sourceWidth <= 0 || sourceHeight <= 0) {
        val frameCount = ((durationMs * 12L + 999L) / 1000L)
            .toInt()
            .coerceIn(GIF_CONVERSION_MIN_FRAMES, GIF_CONVERSION_MAX_FRAMES)
        return GifConversionProfile(frameCount, GIF_CONVERSION_MAX_SIDE_PX)
    }

    val largest = maxOf(sourceWidth, sourceHeight)
    val maxSidePx = when {
        durationMs <= 4_000L -> GIF_CONVERSION_MAX_SIDE_PX
        durationMs <= 10_000L -> 896
        durationMs <= 20_000L -> 768
        durationMs <= 45_000L -> 640
        else -> GIF_CONVERSION_MIN_SIDE_PX
    }.coerceAtMost(largest).coerceAtLeast(GIF_CONVERSION_MIN_SIDE_PX.coerceAtMost(largest))
    val targetFps = when {
        maxSidePx >= 896 -> 16f
        maxSidePx >= 768 -> 14f
        maxSidePx >= 640 -> 12f
        else -> 10f
    }
    val sourceLimitedFps = sourceFrameRate
        ?.takeIf { it.isFinite() && it > 0f }
        ?.coerceIn(GIF_CONVERSION_MIN_FPS.toFloat(), GIF_CONVERSION_MAX_FPS.toFloat())
        ?: targetFps
    val requestedFrames = ((durationMs * minOf(targetFps, sourceLimitedFps) + 999f) / 1000f)
        .toInt()
        .coerceIn(GIF_CONVERSION_MIN_FRAMES, GIF_CONVERSION_MAX_FRAMES)
    val scale = minOf(1f, maxSidePx.toFloat() / largest.toFloat())
    val outputWidth = (sourceWidth * scale).toLong().coerceAtLeast(1L)
    val outputHeight = (sourceHeight * scale).toLong().coerceAtLeast(1L)
    val memoryLimitedFrames = (GIF_CONVERSION_MAX_TOTAL_PIXELS / (outputWidth * outputHeight))
        .toInt()
        .coerceIn(GIF_CONVERSION_MIN_FRAMES, GIF_CONVERSION_MAX_FRAMES)
    return GifConversionProfile(minOf(requestedFrames, memoryLimitedFrames), maxSidePx)
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
            bytes.putAsciiAt(16, "isom")
            bytes.putAsciiAt(20, "mp41")
            details += "ftyp $majorBrand/${compatibleBrands.joinToString(",")} -> mp42/isom,mp41"
        }

        patchMp4BoxesForAndroid(bytes, 0, bytes.size, details)

        if (details.isNotEmpty()) {
            RandomAccessFile(file, "rw").use { output ->
                output.setLength(0)
                output.write(bytes)
            }
        }
    } catch (_: Exception) {
        Log.w("VideoDownloader", "GIF source MP4 patch failed; continuing with original file")
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

private suspend fun decodeGifFramesWithCodec(
    sourceFile: File,
    maxFrames: Int = GIF_CONVERSION_MAX_FRAMES,
    maxSidePx: Int = GIF_CONVERSION_MAX_SIDE_PX,
    onProgress: (Float) -> Unit = {}
): DecodedGifFrames = withContext(Dispatchers.IO) {
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
            return@withContext DecodedGifFrames(emptyList(), null)
        }

        extractor.selectTrack(videoTrackIndex)
        val videoFormat = extractor.getTrackFormat(videoTrackIndex)
        val durationUs = videoFormat.longOrNull(MediaFormat.KEY_DURATION)?.takeIf { it > 0L }
        durationMs = durationUs?.div(1000L)

        codec = createStartedVideoDecoder(videoMime, extractor, videoTrackIndex)
        val decoder = codec
        val bufferInfo = MediaCodec.BufferInfo()
        val targetFrameCount = maxFrames
        val frameIntervalUs = durationUs
            ?.div(targetFrameCount.coerceAtLeast(1))
            ?.coerceAtLeast(1L)
        var nextCaptureUs = 0L
        var inputDone = false
        var outputDone = false
        var idleOutputLoops = 0
        var loopCount = 0
        var lastReportedProgress = -1f

        while (!outputDone && frames.size < maxFrames && loopCount++ < 10_000) {
            ensureActive()
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

                @Suppress("DEPRECATION")
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
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
                                        frames.add(scaleBitmapForGif(bitmap, maxSidePx))
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
                        val decodeProgress = durationUs
                            ?.let { (bufferInfo.presentationTimeUs.toFloat() / it.toFloat()).coerceIn(0f, 1f) }
                            ?: (frames.size.toFloat() / targetFrameCount.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                        if (decodeProgress - lastReportedProgress >= 0.01f || endOfStream) {
                            onProgress(decodeProgress)
                            lastReportedProgress = decodeProgress
                        }
                        decoder.releaseOutputBuffer(outputIndex, false)
                        if (endOfStream) outputDone = true
                    }
                }
            }
        }

        onProgress(1f)
        DecodedGifFrames(frames, durationMs)
    } catch (e: Exception) {
        frames.forEach { it.recycle() }
        Log.e(
            "VideoDownloader",
            "GIF codec fallback failed: decoded=$decodedFrameCount, captured=${frames.size}, " +
                "imageNull=$imageNullCount, durationMs=$durationMs",
            e
        )
        DecodedGifFrames(emptyList(), durationMs)
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
        .compressToJpeg(Rect(0, 0, width, height), 100, output)
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

private fun File.headerHex(byteCount: Int = 16): String {
    return runCatching {
        inputStream().use { input ->
            val bytes = ByteArray(byteCount)
            val read = input.read(bytes)
            bytes.take(read.coerceAtLeast(0)).joinToString(" ") { "%02X".format(it) }
        }
    }.getOrDefault("unreadable")
}

private fun scaleBitmapForGif(bitmap: Bitmap, maxSidePx: Int = GIF_CONVERSION_MAX_SIDE_PX): Bitmap {
    var current = if (bitmap.config == Bitmap.Config.ARGB_8888) {
        bitmap
    } else {
        bitmap.copy(Bitmap.Config.ARGB_8888, false).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }
    val largest = maxOf(current.width, current.height)
    if (largest <= maxSidePx) return current

    val scale = maxSidePx.toFloat() / largest.toFloat()
    val targetWidth = (current.width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (current.height * scale).toInt().coerceAtLeast(1)

    while (current.width > targetWidth * 2 && current.height > targetHeight * 2) {
        val stepped = Bitmap.createScaledBitmap(
            current,
            maxOf(targetWidth, current.width / 2),
            maxOf(targetHeight, current.height / 2),
            true
        )
        if (stepped !== current) current.recycle()
        current = stepped
    }

    if (current.width == targetWidth && current.height == targetHeight) return current
    val scaled = Bitmap.createScaledBitmap(current, targetWidth, targetHeight, true)
    if (scaled !== current) current.recycle()
    return scaled
}

private data class GifFrame(
    val bitmap: Bitmap,
    val delayMs: Long
)

private data class GifFrameBounds(
    val left: Int,
    val top: Int,
    val rightExclusive: Int,
    val bottomExclusive: Int
) {
    val width: Int get() = rightExclusive - left
    val height: Int get() = bottomExclusive - top
}

private data class GifDeltaFrame(
    val colorIndices: IntArray,
    val bounds: GifFrameBounds,
    val transparent: Boolean,
    val changedPixels: Int
)

private fun prepareGifFrames(sourceFrames: List<Bitmap>, durationMs: Long): List<GifFrame> {
    require(sourceFrames.isNotEmpty()) { "GIF conversion requires at least one frame" }
    val width = sourceFrames.first().width
    val height = sourceFrames.first().height
    val totalDurationMs = durationMs.coerceAtLeast(sourceFrames.size * 20L)
    val baseDelayMs = totalDurationMs / sourceFrames.size
    val extraDelayFrames = (totalDurationMs % sourceFrames.size).toInt()
    val prepared = mutableListOf<GifFrame>()

    sourceFrames.forEachIndexed { index, source ->
        val bitmap = if (source.width == width && source.height == height) {
            source
        } else {
            Bitmap.createScaledBitmap(source, width, height, true).also {
                if (it !== source) source.recycle()
            }
        }
        val delayMs = baseDelayMs + if (index < extraDelayFrames) 1L else 0L
        prepared += GifFrame(bitmap, delayMs)
    }

    return prepared
}

private fun buildGlobalGifPalette(
    frames: List<GifFrame>,
    onProgress: (Float) -> Unit = {}
): IntArray {
    val width = frames.first().bitmap.width
    val height = frames.first().bitmap.height
    val totalPixels = width.toLong() * height.toLong() * frames.size.toLong()
    val sampleStride = ceil(
        sqrt(totalPixels.toDouble() / GIF_PALETTE_BASE_SAMPLE_COUNT.toDouble())
    ).toInt().coerceAtLeast(1)
    val sampleGridWidth = (width + sampleStride - 1) / sampleStride
    val sampleGridHeight = (height + sampleStride - 1) / sampleStride
    val previousSamples = IntArray(sampleGridWidth * sampleGridHeight) { -1 }
    val sampleBuffer = IntArray(GIF_PALETTE_MAX_SAMPLE_COUNT)
    val row = IntArray(width)
    var sampleCount = 0

    frameLoop@ for ((frameIndex, frame) in frames.withIndex()) {
        for (y in 0 until height step sampleStride) {
            frame.bitmap.getPixels(row, 0, width, 0, y, width, 1)
            val sampleRow = y / sampleStride
            for (x in 0 until width step sampleStride) {
                if (sampleCount >= sampleBuffer.size) break@frameLoop
                val rgb = row[x] and 0x00FFFFFF
                val sampleIndex = sampleRow * sampleGridWidth + x / sampleStride
                sampleBuffer[sampleCount++] = rgb or 0xFF000000.toInt()

                val previous = previousSamples[sampleIndex]
                if (previous >= 0 && rgbDistance(previous, rgb) > GIF_PALETTE_DIFF_THRESHOLD &&
                    sampleCount < sampleBuffer.size
                ) {
                    sampleBuffer[sampleCount++] = rgb or 0xFF000000.toInt()
                }
                previousSamples[sampleIndex] = rgb
            }
        }
        onProgress((frameIndex + 1f) / frames.size.toFloat())
    }
    onProgress(1f)

    if (sampleCount == 0) return fallbackGifPalette()
    val sampleWidth = minOf(512, sampleCount)
    val sampleHeight = (sampleCount + sampleWidth - 1) / sampleWidth
    val samplePixels = IntArray(sampleWidth * sampleHeight)
    System.arraycopy(sampleBuffer, 0, samplePixels, 0, sampleCount)
    for (index in sampleCount until samplePixels.size) {
        samplePixels[index] = sampleBuffer[sampleCount - 1]
    }

    val sampleBitmap = Bitmap.createBitmap(
        samplePixels,
        sampleWidth,
        sampleHeight,
        Bitmap.Config.ARGB_8888
    )
    val generated = try {
        Palette.from(sampleBitmap)
            .maximumColorCount(GIF_PALETTE_VISIBLE_COLOR_COUNT)
            .clearFilters()
            .resizeBitmapArea(samplePixels.size)
            .generate()
    } finally {
        sampleBitmap.recycle()
    }

    val colors = LinkedHashSet<Int>(GIF_PALETTE_VISIBLE_COLOR_COUNT)
    generated.swatches
        .sortedByDescending { it.population }
        .forEach { colors += it.rgb and 0x00FFFFFF }
    if (colors.isEmpty()) return fallbackGifPalette()
    if (colors.size == 1) {
        colors += if (colors.first() == 0x000000) 0xFFFFFF else 0x000000
    }
    return colors.take(GIF_PALETTE_VISIBLE_COLOR_COUNT).toIntArray()
}

private fun rgbDistance(first: Int, second: Int): Int {
    return kotlin.math.abs((first ushr 16 and 0xFF) - (second ushr 16 and 0xFF)) +
        kotlin.math.abs((first ushr 8 and 0xFF) - (second ushr 8 and 0xFF)) +
        kotlin.math.abs((first and 0xFF) - (second and 0xFF))
}

private fun fallbackGifPalette(): IntArray {
    val colors = LinkedHashSet<Int>(GIF_PALETTE_VISIBLE_COLOR_COUNT)
    for (red in 0 until 6) {
        for (green in 0 until 6) {
            for (blue in 0 until 6) {
                colors += ((red * 255 / 5) shl 16) or
                    ((green * 255 / 5) shl 8) or
                    (blue * 255 / 5)
            }
        }
    }
    for (gray in 0 until 40) {
        val value = gray * 255 / 39
        colors += (value shl 16) or (value shl 8) or value
    }
    return colors.take(GIF_PALETTE_VISIBLE_COLOR_COUNT).toIntArray()
}

private class GifPaletteMapper(
    private val palette: IntArray,
    onProgress: (Float) -> Unit = {}
) {
    private val nearestColorLut = buildNearestColorLut(palette, onProgress)

    fun map(bitmap: Bitmap): IntArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val ditherAmplitude = (6 - GIF_BAYER_SCALE).coerceIn(1, 6)

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val index = rowOffset + x
                val pixel = pixels[index]
                val centeredBayer = GIF_BAYER_4X4[(y and 3) * 4 + (x and 3)] * 2 - 15
                val offset = centeredBayer * ditherAmplitude / 2
                val red = ((pixel ushr 16 and 0xFF) + offset).coerceIn(0, 255)
                val green = ((pixel ushr 8 and 0xFF) + offset).coerceIn(0, 255)
                val blue = ((pixel and 0xFF) + offset).coerceIn(0, 255)
                val lutIndex = ((red * 31 + 127) / 255 shl 10) or
                    ((green * 31 + 127) / 255 shl 5) or
                    ((blue * 31 + 127) / 255)
                pixels[index] = nearestColorLut[lutIndex]
            }
        }
        return pixels
    }

    private fun buildNearestColorLut(colors: IntArray, onProgress: (Float) -> Unit): IntArray {
        val lookup = IntArray(32 * 32 * 32)
        for (index in lookup.indices) {
            val red = (index ushr 10 and 31) * 255 / 31
            val green = (index ushr 5 and 31) * 255 / 31
            val blue = (index and 31) * 255 / 31
            var nearestIndex = 0
            var nearestDistance = Int.MAX_VALUE
            for (colorIndex in colors.indices) {
                val color = colors[colorIndex]
                val redDelta = red - (color ushr 16 and 0xFF)
                val greenDelta = green - (color ushr 8 and 0xFF)
                val blueDelta = blue - (color and 0xFF)
                val distance = redDelta * redDelta * 2 +
                    greenDelta * greenDelta * 4 +
                    blueDelta * blueDelta
                if (distance < nearestDistance) {
                    nearestDistance = distance
                    nearestIndex = colorIndex
                }
            }
            lookup[index] = nearestIndex
            if (index and 0x3FF == 0) onProgress(index.toFloat() / lookup.size.toFloat())
        }
        onProgress(1f)
        return lookup
    }
}

private val GIF_BAYER_4X4 = intArrayOf(
    0, 8, 2, 10,
    12, 4, 14, 6,
    3, 11, 1, 9,
    15, 7, 13, 5
)

private object OptimizedGifEncoder {
    fun encode(
        sourceFrames: List<Bitmap>,
        durationMs: Long,
        onProgress: (Float, GifConversionStage) -> Unit = { _, _ -> }
    ): ByteArray {
        val startedAt = SystemClock.elapsedRealtime()
        onProgress(0.01f, GifConversionStage.PALETTE)
        val preparedFrames = prepareGifFrames(sourceFrames, durationMs)
        val width = preparedFrames.first().bitmap.width
        val height = preparedFrames.first().bitmap.height

        try {
            val palette = buildGlobalGifPalette(preparedFrames) { progress ->
                onProgress(0.02f + progress * 0.18f, GifConversionStage.PALETTE)
            }
            val mapper = GifPaletteMapper(palette) { progress ->
                onProgress(0.20f + progress * 0.15f, GifConversionStage.PALETTE)
            }
            val globalPalette = IntArray(256)
            for (index in 0 until GIF_TRANSPARENT_COLOR_INDEX) {
                globalPalette[index] = palette.getOrElse(index) { palette.last() }
            }
            globalPalette[GIF_TRANSPARENT_COLOR_INDEX] = 0
            val output = ByteArrayOutputStream()
            var previousEncodedPixels: IntArray? = null
            var encodedFrameCount = 0
            var encodedRectPixels = 0L
            var changedPixelCount = 0L
            var transparentFrameCount = 0

            output.use {
                val encoder = GlobalPaletteGifEncoder(it, width, height, globalPalette, loopCount = 0)
                for ((frameIndex, frame) in preparedFrames.withIndex()) {
                    val mappedPixels = mapper.map(frame.bitmap)
                    if (!frame.bitmap.isRecycled) frame.bitmap.recycle()
                    val deltaFrame = createGifDeltaFrame(
                        previous = previousEncodedPixels,
                        current = mappedPixels,
                        width = width,
                        height = height
                    )
                    encoder.addFrame(
                        colorIndices = deltaFrame.colorIndices,
                        width = deltaFrame.bounds.width,
                        height = deltaFrame.bounds.height,
                        left = deltaFrame.bounds.left,
                        top = deltaFrame.bounds.top,
                        delayMs = frame.delayMs,
                        transparentColorIndex = GIF_TRANSPARENT_COLOR_INDEX.takeIf { deltaFrame.transparent }
                    )
                    encodedRectPixels += deltaFrame.bounds.width.toLong() * deltaFrame.bounds.height.toLong()
                    changedPixelCount += deltaFrame.changedPixels
                    if (deltaFrame.transparent) transparentFrameCount++
                    encodedFrameCount++
                    previousEncodedPixels = mappedPixels
                    onProgress(
                        0.35f + (frameIndex + 1f) / preparedFrames.size.toFloat() * 0.65f,
                        GifConversionStage.ENCODING
                    )
                }
                encoder.finish()
            }

            val fullPixelCount = width.toLong() * height.toLong() * encodedFrameCount.coerceAtLeast(1)
            val rectPixelPermille = encodedRectPixels * 1000L / fullPixelCount
            val changedPixelPermille = changedPixelCount * 1000L / fullPixelCount
            Log.i(
                "VideoDownloader",
                "GIF encode optimized: decoded=${sourceFrames.size}, encoded=$encodedFrameCount, " +
                    "palette=${palette.size}+1, transparentFrames=$transparentFrameCount, " +
                    "rectPermille=$rectPixelPermille, changedPermille=$changedPixelPermille, " +
                    "elapsedMs=${SystemClock.elapsedRealtime() - startedAt}"
            )
            return output.toByteArray()
        } finally {
            sourceFrames.forEach { if (!it.isRecycled) it.recycle() }
            preparedFrames.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        }
    }
}

private fun createGifDeltaFrame(
    previous: IntArray?,
    current: IntArray,
    width: Int,
    height: Int
): GifDeltaFrame {
    if (previous == null || previous.size != current.size) {
        return GifDeltaFrame(
            colorIndices = current,
            bounds = GifFrameBounds(0, 0, width, height),
            transparent = false,
            changedPixels = current.size
        )
    }

    var minX = width
    var minY = height
    var maxX = -1
    var maxY = -1
    var changedPixels = 0
    for (index in current.indices) {
        if (previous[index] == current[index]) continue
        changedPixels++
        val x = index % width
        val y = index / width
        if (x < minX) minX = x
        if (x > maxX) maxX = x
        if (y < minY) minY = y
        if (y > maxY) maxY = y
    }
    if (maxX < 0) {
        return GifDeltaFrame(
            colorIndices = intArrayOf(GIF_TRANSPARENT_COLOR_INDEX),
            bounds = GifFrameBounds(0, 0, 1, 1),
            transparent = true,
            changedPixels = 0
        )
    }

    val bounds = GifFrameBounds(minX, minY, maxX + 1, maxY + 1)
    val deltaPixels = IntArray(bounds.width * bounds.height)
    for (row in 0 until bounds.height) {
        val sourceOffset = (bounds.top + row) * width + bounds.left
        val outputOffset = row * bounds.width
        for (column in 0 until bounds.width) {
            val sourceIndex = sourceOffset + column
            deltaPixels[outputOffset + column] = if (previous[sourceIndex] == current[sourceIndex]) {
                GIF_TRANSPARENT_COLOR_INDEX
            } else {
                current[sourceIndex]
            }
        }
    }
    return GifDeltaFrame(
        colorIndices = deltaPixels,
        bounds = bounds,
        transparent = true,
        changedPixels = changedPixels
    )
}

private fun buildDownloadMediaData(context: Context, download: VideoDownloadEntity): MediaData? {
    val path = download.savePath
    val pendingStatus = context.getString(R.string.video_dl_status_pending)
    if (path.isBlank() || path == pendingStatus) return null
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
        folderName = context.getString(R.string.label_downloads)
    )
}

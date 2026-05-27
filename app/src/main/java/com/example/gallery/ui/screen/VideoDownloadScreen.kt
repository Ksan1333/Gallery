package com.example.gallery.ui.screen

import android.content.Context
import android.media.MediaScannerConnection
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gallery.data.local.entity.VideoDownloadEntity
import com.example.gallery.ui.state.GalleryState
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .addInterceptor(HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    })
    .connectionSpecs(listOf(
        ConnectionSpec.MODERN_TLS,
        ConnectionSpec.COMPATIBLE_TLS
    ))
    .build()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDownloadScreen(
    galleryState: GalleryState,
    onMenuClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var urlInput by remember { mutableStateOf("") }
    var showDownloadModal by remember { mutableStateOf(false) }
    
    val downloads by galleryState.repository.mediaDao.getAllVideoDownloads().collectAsState(initial = emptyList())

    // クリップボードからの自動検知
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    
    // 画面が表示されたタイミングで一度だけ実行
    LaunchedEffect(Unit) {
        val clip = clipboardManager.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: ""
            if ((text.contains("x.com") || text.contains("twitter.com")) && text.contains("/status/")) {
                // すでに同じURLが入力されていないか、または直近のDLでないかを確認
                if (urlInput != text) {
                    urlInput = text
                    // 自動解析（モーダル表示）を開始
                    showDownloadModal = true
                }
            }
        }
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
            // URL入力エリア
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("X (Twitter) URLを入力") },
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
                        Toast.makeText(context, "URLを入力してください", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DA1F2)) // X Blue
            ) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("ダウンロードオプションを表示")
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
                    Text("ダウンロード履歴", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                
                if (downloads.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                galleryState.repository.mediaDao.clearVideoDownloadHistory()
                                Toast.makeText(context, "履歴をリセットしました", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("履歴をクリア", fontSize = 12.sp)
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(downloads) { download ->
                    DownloadHistoryItem(download)
                }
            }
        }
    }

    if (showDownloadModal) {
        DownloadOptionsModal(
            url = urlInput,
            galleryState = galleryState,
            onDismiss = { showDownloadModal = false },
            onDownloadStart = { resolvedUrl, quality ->
                showDownloadModal = false
                // 元のURL(Xポストリンク)を重複チェック用に残しつつ、解決済みURLでダウンロード開始
                startDownloadTask(context, resolvedUrl, quality, galleryState, originalUrl = urlInput)
                urlInput = ""
            }
        )
    }
}

@Composable
fun DownloadHistoryItem(download: VideoDownloadEntity) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(download.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(download.url, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(dateFormat.format(Date(download.downloadDate)), color = Color.Gray, fontSize = 11.sp)
                Text(download.status, color = if (download.status == "COMPLETED") Color.Green else Color.Red, fontSize = 11.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadOptionsModal(
    url: String,
    galleryState: GalleryState,
    onDismiss: () -> Unit,
    onDownloadStart: (String, String) -> Unit
) {
    var isDuplicate by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var resolvedUrl by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedQuality by remember { mutableStateOf("High (1080p)") }
    val qualities = listOf("High (1080p)", "Medium (720p)", "Low (480p)")

    LaunchedEffect(url) {
        isLoading = true
        error = null
        isDuplicate = galleryState.repository.mediaDao.isVideoDownloaded(url)
        
        // X/Twitterリンクを解析
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                resolvedUrl = resolveXVideoUrl(url)
                if (resolvedUrl == null) {
                    error = "動画URLの取得に失敗しました。公開アカウントのポストか確認してください。"
                }
            } catch (e: Exception) {
                error = "解析エラー: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text("ダウンロード設定", color = Color.White) },
        text = {
            if (isLoading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(color = Color.Cyan)
                    Spacer(Modifier.height(8.dp))
                    Text("動画を解析中...", color = Color.LightGray)
                }
            } else if (error != null) {
                Text(error!!, color = Color.Red)
            } else {
                Column {
                    if (isDuplicate) {
                        Text("⚠️ この動画は既にダウンロード済みです。", color = Color.Yellow, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                    }
                    Text("画質を選択してください:", color = Color.LightGray)
                    qualities.forEach { quality ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { selectedQuality = quality }.padding(vertical = 8.dp)
                        ) {
                            RadioButton(selected = (selectedQuality == quality), onClick = { selectedQuality = quality })
                            Text(quality, color = Color.White)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { resolvedUrl?.let { onDownloadStart(it, selectedQuality) } },
                enabled = !isLoading && error == null && resolvedUrl != null
            ) { 
                Text(if (isDuplicate) "再ダウンロード" else "ダウンロード開始") 
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル", color = Color.Gray) }
        }
    )
}

/**
 * X/Twitterリンクから直接の動画URLを抽出する
 */
private fun resolveXVideoUrl(statusUrl: String): String? {
    if (!statusUrl.contains("x.com") && !statusUrl.contains("twitter.com")) return statusUrl
    
    // プロトコルがない場合は追加し、クエリパラメータを除去
    val normalizedUrl = if (!statusUrl.startsWith("http")) "https://$statusUrl" else statusUrl
    val baseUrl = normalizedUrl.substringBefore("?")

    // api.fxtwitter.com を使用してメタデータを取得（vxtwitterよりSSL互換性が高い傾向にある）
    val apiUrl = when {
        baseUrl.contains("x.com") -> baseUrl.replace("x.com", "api.fxtwitter.com")
        baseUrl.contains("twitter.com") -> baseUrl.replace("twitter.com", "api.fxtwitter.com")
        else -> baseUrl
    }
        
    return try {
        val request = Request.Builder()
            .url(apiUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
            
        okHttpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                Log.d("VideoDownloader", "API Response: $body")
                val json = JSONObject(body)
                
                // fxtwitter APIのレスポンス構造を確認 (tweet object直下か、tweet内のmediaか)
                val tweet = if (json.has("tweet")) json.getJSONObject("tweet") else json
                val mediaList = tweet.optJSONArray("media_extended") ?: tweet.optJSONObject("media")?.optJSONArray("all")
                
                if (mediaList != null && mediaList.length() > 0) {
                    // 最初のメディア要素を取得
                    val firstMedia = mediaList.getJSONObject(0)
                    val videoUrl = firstMedia.optString("url")
                    
                    if (videoUrl.isNotEmpty()) {
                        Log.d("VideoDownloader", "Resolved Video URL: $videoUrl")
                        videoUrl
                    } else {
                        Log.w("VideoDownloader", "No media URL found")
                        null
                    }
                } else {
                    Log.w("VideoDownloader", "No media found in response")
                    null
                }
            } else {
                Log.e("VideoDownloader", "API request failed: ${response.code} ${response.message}")
                null
            }
        }
    } catch (e: Exception) {
        val errorMessage = if (e is javax.net.ssl.SSLHandshakeException) {
            "SSL Handshake failed: ${e.localizedMessage}. The server may require a newer TLS version or SNI. OkHttp was used for this request."
        } else {
            "Failed to resolve X URL: ${e.localizedMessage}"
        }
        Log.e("VideoDownloader", errorMessage, e)
        null
    }
}

fun startDownloadTask(context: Context, url: String, quality: String, galleryState: GalleryState, originalUrl: String? = null) {
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
    scope.launch {
        val timestamp = System.currentTimeMillis()
        val checkUrl = originalUrl ?: url
        
        // 取得したURL自体の末尾（拡張子）を見て保存形式を決定する
        val isMp4 = url.lowercase().contains(".mp4")
        val isGif = url.lowercase().contains(".gif")
        val isWebp = url.lowercase().contains(".webp")
        
        val (extension, mimeType) = when {
            isMp4 -> "mp4" to "video/mp4"
            isGif -> "gif" to "image/gif"
            isWebp -> "webp" to "image/webp"
            else -> "mp4" to "video/mp4" // デフォルト
        }
        
        Log.i("VideoDownloader", "========================================")
        Log.i("VideoDownloader", "DOWNLOAD START")
        Log.i("VideoDownloader", "Original URL: $checkUrl")
        Log.i("VideoDownloader", "Resolved URL: $url")
        Log.i("VideoDownloader", "Detected Extension: $extension")
        Log.i("VideoDownloader", "Detected MIME Type: $mimeType")
        Log.i("VideoDownloader", "========================================")
        
        val filename = "X_Media_$timestamp.$extension"

        // 状態登録
        val entity = VideoDownloadEntity(
            url = checkUrl,
            title = "Media $timestamp ($quality)",
            savePath = "Pending...",
            downloadDate = timestamp,
            status = "DOWNLOADING"
        )
        galleryState.repository.mediaDao.insertVideoDownload(entity)

        try {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(android.provider.MediaStore.MediaColumns.DATE_ADDED, timestamp / 1000)
                put(android.provider.MediaStore.MediaColumns.DATE_MODIFIED, timestamp / 1000)
                
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
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()
                    
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.byteStream()?.use { input ->
                            input.copyTo(outputStream)
                        } ?: throw Exception("Response body is null")
                        outputStream.flush()
                    } else {
                        throw Exception("Download failed: ${response.code}")
                    }
                }
            }

            // Pending状態の解除
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val updateValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(uri, updateValues, null, null)
            }

            // 確定したパスの取得
            val actualPath = try {
                val cursor = resolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)
                cursor?.use { if (it.moveToFirst()) it.getString(0) else uri.toString() } ?: uri.toString()
            } catch (e: Exception) {
                uri.toString()
            }

            Log.i("VideoDownloader", "DOWNLOAD COMPLETE")
            Log.i("VideoDownloader", "Saved Path: $actualPath")
            Log.i("VideoDownloader", "========================================")

            // メディアスキャナーに通知してOSのインデックスを更新
            if (!actualPath.startsWith("content://")) {
                MediaScannerConnection.scanFile(context, arrayOf(actualPath), arrayOf(mimeType)) { _, scannedUri ->
                    Log.d("VideoDownloader", "Scan finished for $scannedUri")
                    scope.launch { galleryState.refresh() }
                }
            } else {
                galleryState.refresh()
            }

            galleryState.repository.mediaDao.insertVideoDownload(entity.copy(status = "COMPLETED", savePath = actualPath))

            withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(context, "ダウンロード完了: $extension", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("VideoDownloader", "Task failed", e)
            galleryState.repository.mediaDao.insertVideoDownload(entity.copy(status = "FAILED"))
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(context, "失敗: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

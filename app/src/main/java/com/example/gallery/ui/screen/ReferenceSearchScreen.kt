package com.example.gallery.ui.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.gallery.data.repository.ReferenceRepository
import com.example.gallery.ui.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceSearchScreen(
    projectId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { ReferenceRepository(context) }
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf("https://www.google.com/search?q=drawing+reference&tbm=isch") }
    
    var directAddUrl by remember { mutableStateOf<String?>(null) }
    var showTutorial by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("reference_search", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("tutorial_shown", false)) {
            showTutorial = true
        }
    }

    fun markTutorialShown() {
        val prefs = context.getSharedPreferences("reference_search", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("tutorial_shown", true).apply()
        showTutorial = false
    }

    // direct add from long press on image
    LaunchedEffect(directAddUrl) {
        val url = directAddUrl
        if (url != null) {
            val success = repository.addReferenceFromUrl(projectId, url, "Web Image")
            withContext(Dispatchers.Main) {
        Toast.makeText(context, if (success) "資料に追加しました" else "追加に失敗しました", Toast.LENGTH_SHORT).show()
            }
            directAddUrl = null
        }
    }

    Scaffold(
        containerColor = AppConstants.BackgroundColor
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // シンプルな検索ヘッダー。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                }
                Spacer(Modifier.width(4.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
            placeholder = { Text("キーワード検索...", color = Color.Gray) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
                    },
                    trailingIcon = {
                        IconButton(onClick = {
                            val url = "https://www.google.com/search?q=${Uri.encode(searchQuery)}&tbm=isch"
                            webView?.loadUrl(url)
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "検索", tint = Color.White)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.Cyan,
                        unfocusedBorderColor = Color.DarkGray
                    )
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = {
                    val wv = webView
                    if (wv != null) {
                        scope.launch {
                            wv.post {
                                try {
                                    wv.invalidate()
                                    val w = wv.width.coerceAtLeast(100)
                                    val h = wv.height.coerceAtLeast(100)
                                    wv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                    val canvas = Canvas(bitmap)
                                    wv.draw(canvas)

                                    val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                                    val refDir = File(baseDir, "Gallery/References/$projectId")
                                    if (!refDir.exists()) refDir.mkdirs()
                                    val file = File(refDir, "screenshot_${System.currentTimeMillis()}.jpg")
                                    FileOutputStream(file).use { out ->
                                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                                    }
                                    scope.launch {
                                val success = repository.addLocalItemForProject(projectId, file.absolutePath, wv.url ?: "", "検索画面スクショ")
                                        withContext(Dispatchers.Main) {
                                Toast.makeText(context, if (success) "スクショを資料に追加しました" else "追加に失敗しました", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    scope.launch {
                                        withContext(Dispatchers.Main) {
                        Toast.makeText(context, "スクショに失敗しました", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "スクリーンショットを追加")
                }
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                return false
                            }
                        }
                        setOnLongClickListener { v ->
                            val wv = v as? WebView ?: return@setOnLongClickListener false
                            val hit = wv.hitTestResult
                            if (hit.type == WebView.HitTestResult.IMAGE_TYPE || hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                                val imgUrl = hit.extra
                                if (!imgUrl.isNullOrBlank()) {
                                    directAddUrl = imgUrl
                                    return@setOnLongClickListener true
                                }
                            }
                            false
                        }
                        loadUrl(currentUrl)
                        webView = this
                    }
                },
                update = { /* URL更新は検索バーで行う。 */ }
            )
        }
    }

    if (showTutorial) {
        AlertDialog(
            onDismissRequest = { markTutorialShown() },
            title = { Text("画像検索の使い方") },
            text = {
                Text(
                    "・キーワードを入力して画像検索\n" +
                    "・画像を長押しして資料に追加\n" +
                    "・右上のカメラボタンで現在の画面のスクショを資料に保存\n" +
                    "検索ページを操作して参考画像を探してください。"
                )
            },
            confirmButton = {
                Button(onClick = { markTutorialShown() }) {
                    Text("OK")
                }
            }
        )
    }
}

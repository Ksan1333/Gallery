package com.example.gallery.ui.screen

import android.os.Build
import android.os.Environment
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.rememberSaveable
import coil.compose.AsyncImage
import com.example.gallery.data.repository.BookData
import com.example.gallery.data.repository.BookRepository
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.component.BookViewer
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.gallery.data.repository.BookType
import kotlinx.coroutines.launch

@Composable
fun BookScreen(
    onViewerStateChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { BookRepository(context) }
    val scope = rememberCoroutineScope()
    var books by remember { mutableStateOf<List<BookData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    
    // 現在選択されているフォルダパス
    var selectedFolderPath by rememberSaveable { mutableStateOf<String?>(null) }
    
    // 手動の Saver を定義して Parcelize 依存を解消
    val bookSaver = Saver<BookData?, Map<String, Any?>>(
        save = { book ->
            book?.let {
                mapOf(
                    "id" to it.id,
                    "title" to it.title,
                    "path" to it.path,
                    "type" to it.type.name,
                    "pageCount" to it.pageCount,
                    "thumbnailPath" to it.thumbnailPath,
                    "folderName" to it.folderName,
                    "folderPath" to it.folderPath
                )
            } ?: emptyMap()
        },
        restore = { map ->
            if (map.isEmpty()) null else BookData(
                id = map["id"] as String,
                title = map["title"] as String,
                path = map["path"] as String,
                type = BookType.valueOf(map["type"] as String),
                pageCount = map["pageCount"] as Int,
                thumbnailPath = map["thumbnailPath"] as String?,
                folderName = map["folderName"] as String,
                folderPath = map["folderPath"] as String
            )
        }
    )

    var selectedBook by rememberSaveable(stateSaver = bookSaver) { mutableStateOf(null) }

    LaunchedEffect(selectedBook) {
        onViewerStateChanged(selectedBook != null)
    }

    // 戻るボタンの制御
    androidx.activity.compose.BackHandler(selectedBook != null || selectedFolderPath != null) {
        if (selectedBook != null) {
            selectedBook = null
        } else if (selectedFolderPath != null) {
            selectedFolderPath = null
        }
    }
    
    // 全ファイルアクセス権限のチェック
    var hasFullStorageAccess by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true // Android 10以下は標準権限でOK
            }
        )
    }

    fun refresh() {
        if (!hasFullStorageAccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return
        
        scope.launch {
            isLoading = true
            books = repository.scanBooks()
            isLoading = false
        }
    }

    // 権限設定から戻ってきたときに再チェック
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    hasFullStorageAccess = Environment.isExternalStorageManager()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasFullStorageAccess) {
        if (hasFullStorageAccess) {
            refresh()
        }
    }

    if (selectedBook != null) {
        BookViewer(
            book = selectedBook!!,
            repository = repository,
            onClose = { selectedBook = null }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppConstants.BackgroundColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(AppConstants.HeaderHeight)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "本 / Zip / Pdf",
                    color = Color.White,
                    fontSize = AppConstants.HeaderFontSize
                )
                IconButton(onClick = { refresh() }) {
                    Icon(Icons.Default.Refresh, "Scan", tint = Color.White)
                }
            }

            // パンくずリスト風の表示
            if (selectedFolderPath != null) {
                val folderName = books.find { it.folderPath == selectedFolderPath }?.folderName ?: "Unknown"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "本 > ",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { selectedFolderPath = null }
                    )
                    Text(
                        text = folderName,
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }

            if (!hasFullStorageAccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Default.Settings, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "端末内のすべてのフォルダから本を探すには、「全ファイルへのアクセス」権限が必要です。",
                            color = Color.White,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                context.startActivity(intent)
                            }
                        }) {
                            Text("設定を開いて許可する")
                        }
                    }
                }
            } else if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (books.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("本が見つかりませんでした", color = Color.Gray)
                        Text("Logcat (BookRepository) を確認してください", color = Color.DarkGray, fontSize = 10.sp)
                    }
                }
            } else {
                if (selectedFolderPath == null) {
                    // フォルダ一覧を表示
                    val folderGroups = remember(books) {
                        books.groupBy { it.folderPath }
                            .map { (path, files) ->
                                val name = files.first().folderName
                                FolderData(path, name, files.size, files.first().thumbnailPath)
                            }
                            .sortedBy { it.name }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(folderGroups) { folder ->
                            FolderItem(folder = folder, onClick = { selectedFolderPath = folder.path })
                        }
                    }
                } else {
                    // 特定のフォルダ内の本を表示
                    val folderBooks = remember(books, selectedFolderPath) {
                        books.filter { it.folderPath == selectedFolderPath }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(folderBooks) { book ->
                            BookItem(book = book, onClick = { selectedBook = book })
                        }
                    }
                }
            }
        }
    }
}

data class FolderData(
    val path: String,
    val name: String,
    val count: Int,
    val thumbnailPath: String?
)

@Composable
fun FolderItem(folder: FolderData, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
        ) {
            if (folder.thumbnailPath != null) {
                AsyncImage(
                    model = folder.thumbnailPath,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.6f // フォルダなので少し暗く
                )
            }
            // フォルダアイコンを中央に
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(48.dp).align(Alignment.Center)
            )
            // 件数バッジ
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
            ) {
                Text(
                    text = "${folder.count}",
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            folder.name,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun BookItem(book: BookData, onClick: () -> Unit) {
    var contentScale by remember { mutableStateOf(ContentScale.Crop) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
        ) {
            if (book.thumbnailPath != null) {
                AsyncImage(
                    model = book.thumbnailPath,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                    onSuccess = { state ->
                        val size = state.painter.intrinsicSize
                        if (size.width > size.height) {
                            contentScale = ContentScale.Fit
                        }
                    }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            book.title,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            "${book.pageCount} ページ",
            color = Color.Gray,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

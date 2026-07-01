package com.example.gallery.ui.screen

import android.os.Build
import android.os.Environment
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import android.widget.Toast
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.ui.input.pointer.pointerInput
import coil.compose.AsyncImage
import com.example.gallery.data.repository.BookData
import com.example.gallery.data.repository.BookRepository
import com.example.gallery.data.repository.BookType
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.screen.BookViewerScreen
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private const val BOOKMARKS_PREFS = "book_bookmarks"
private const val BOOK_VIEWER_PREFS = "book_viewer_settings"
private const val BOOK_FAVORITES_PREFS = "book_favorites"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookScreen(
    onViewerStateChanged: (Boolean) -> Unit = {},
    onMenuClick: () -> Unit = {},
    onBookmarksChanged: () -> Unit = {},
    onNavigateToBookmarks: () -> Unit = {},
    onOpenBookSettings: () -> Unit = {},
    initialJumpBookId: String? = null,
    initialJumpPage: Int = -1,
    onJumpHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { BookRepository(context) }
    val scope = rememberCoroutineScope()
    val bookSettingsPrefs = remember { context.getSharedPreferences(BOOK_VIEWER_PREFS, Context.MODE_PRIVATE) }
    val bookFavoritesPrefs = remember { context.getSharedPreferences(BOOK_FAVORITES_PREFS, Context.MODE_PRIVATE) }
    var bookSettingsVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(bookSettingsPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            bookSettingsVersion += 1
        }
        bookSettingsPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { bookSettingsPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val fileSort = bookSettingsPrefs.getString("fileSort", "NAME_ASC") ?: "NAME_ASC"
    val showThumbnail = bookSettingsPrefs.getBoolean("showThumbnail", true)
    val autoLoadOnLaunch = bookSettingsPrefs.getBoolean("autoLoadOnLaunch", true)
    val bookshelfThumbnail = bookSettingsPrefs.getString("bookshelfThumbnail", "FIRST_BOOK") ?: "FIRST_BOOK"
    val readMark = bookSettingsPrefs.getString("readMark", "NONE") ?: "NONE"
    val enableSwipeDeleteBook = bookSettingsPrefs.getBoolean("enableSwipeDeleteBook", false)
    val gridBottomPadding =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp
    var books by remember { mutableStateOf<List<BookData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedBookIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var favoriteVersion by remember { mutableIntStateOf(0) }
    var pendingDeleteBooks by remember { mutableStateOf<List<BookData>>(emptyList()) }

    // しおりからの自動遷移処理
    LaunchedEffect(initialJumpBookId, books) {
        if (initialJumpBookId != null && books.isNotEmpty()) {
            val book = books.find { it.id == initialJumpBookId }
            if (book != null) {
                // ここで直接開く
                // BookViewerScreenに開始ページを渡す必要がある
            }
        }
    }

    // 現在選択されているフォルダパス
    var selectedFolderPath by rememberSaveable { mutableStateOf<String?>(null) }

    // Store the selected book without relying on Parcelable state.
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
    var startPageForSelectedBook by remember { mutableIntStateOf(0) }

    LaunchedEffect(initialJumpBookId, books) {
        if (initialJumpBookId != null && books.isNotEmpty()) {
            val book = books.find { it.id == initialJumpBookId }
            if (book != null) {
                selectedFolderPath = book.folderPath
                startPageForSelectedBook = initialJumpPage.coerceAtLeast(0)
                selectedBook = book
                onJumpHandled()
            }
        }
    }

    LaunchedEffect(selectedBook) {
        onViewerStateChanged(selectedBook != null)
        if (selectedBook == null) {
            startPageForSelectedBook = 0
        }
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
        if (isLoading) return

        scope.launch {
            isLoading = true
            books = sortBooks(repository.scanBooks(), fileSort)
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

    LaunchedEffect(hasFullStorageAccess, autoLoadOnLaunch) {
        if (hasFullStorageAccess) {
            isLoading = true
            val cachedBooks = repository.loadCachedBooks()
            if (cachedBooks.isNotEmpty()) {
                books = sortBooks(cachedBooks, fileSort)
                isLoading = false
            }
            if (autoLoadOnLaunch || cachedBooks.isEmpty()) {
                books = sortBooks(repository.scanBooks(), fileSort)
            }
            isLoading = false
        }
    }

    LaunchedEffect(fileSort, bookSettingsVersion) {
        books = sortBooks(books, fileSort)
    }

    if (selectedBook != null) {
        val currentBook = selectedBook!!
        val siblingBooks = remember(books, currentBook.folderPath) {
            books.filter { it.folderPath == currentBook.folderPath }
        }
        val currentBookIndex = siblingBooks.indexOfFirst { it.id == currentBook.id }
        key(currentBook.id) {
            BookViewerScreen(
                book = currentBook,
                repository = repository,
                onClose = { selectedBook = null },
                onPreviousBook = siblingBooks.getOrNull(currentBookIndex - 1)?.let { previous ->
                    {
                        selectedBook = previous
                        startPageForSelectedBook = 0
                    }
                },
                onNextBook = siblingBooks.getOrNull(currentBookIndex + 1)?.let { next ->
                    {
                        selectedBook = next
                        startPageForSelectedBook = 0
                    }
                },
                onBookmarksChanged = onBookmarksChanged,
                onOpenBookSettings = onOpenBookSettings,
                initialPage = startPageForSelectedBook
            )
        }
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
                if (selectedBookIds.isNotEmpty()) {
                    IconButton(onClick = { selectedBookIds = emptySet() }) {
                        Icon(Icons.Default.Close, "選択を解除", tint = Color.White)
                    }
                    Text(
                        "${selectedBookIds.size} 件選択中",
                        color = Color.White,
                        fontSize = AppConstants.SubtitleFontSize
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        val shouldFavorite = selectedBookIds.any { id -> !bookFavoritesPrefs.getBoolean(id, false) }
                        val editor = bookFavoritesPrefs.edit()
                        selectedBookIds.forEach { id -> editor.putBoolean(id, shouldFavorite) }
                        editor.apply()
                        favoriteVersion++
                        selectedBookIds = emptySet()
                        Toast.makeText(context, if (shouldFavorite) "お気に入りに追加しました" else "お気に入りを解除しました", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Favorite, "お気に入りに追加", tint = Color.Red)
                    }
                    IconButton(onClick = {
                        pendingDeleteBooks = books.filter { it.id in selectedBookIds }
                    }) {
                        Icon(Icons.Default.Delete, "削除", tint = Color.White)
                    }
                } else {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, "メニュー", tint = Color.White)
                    }
                    Text(
                        "本 / Zip / PDF",
                        color = Color.White,
                        fontSize = AppConstants.HeaderFontSize
                    )
                    Spacer(Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, "並び替え", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            fileSortOptionsForBookScreen.forEach { (label, value) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        showSortMenu = false
                                        bookSettingsPrefs.edit().putString("fileSort", value).apply()
                                        bookSettingsVersion++
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { refresh() }, enabled = !isLoading) {
                        if (isLoading) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, "再読み込み", tint = Color.White)
                        }
                    }
                    IconButton(onClick = {
                        onNavigateToBookmarks()
                    }) {
                        Icon(Icons.Default.Bookmark, "しおり", tint = Color.Cyan)
                    }
                    IconButton(onClick = onOpenBookSettings) {
                        Icon(Icons.Default.Settings, "設定", tint = Color.White)
                    }
                }
            }
            if (isLoading && books.isNotEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
                        fontSize = com.example.gallery.ui.AppConstants.SmallFontSize,
                        modifier = Modifier.clickable { selectedFolderPath = null }
                    )
                    Text(
                        text = folderName,
                        color = Color.White,
                        fontSize = com.example.gallery.ui.AppConstants.SmallFontSize
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
            } else if (isLoading && books.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Text("本を走査中...", color = Color.White)
                    }
                }
            } else if (books.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("本が見つかりませんでした", color = Color.Gray)
                        Text("フォルダの権限や保存場所を確認してください", color = Color.DarkGray, fontSize = com.example.gallery.ui.AppConstants.TinyFontSize)
                    }
                }
            } else {
                if (selectedFolderPath == null) {
                    // フォルダ一覧を表示
                    val folderGroups = remember(books, bookshelfThumbnail, bookSettingsVersion) {
                        books.groupBy { it.folderPath }
                            .map { (path, files) ->
                                val name = files.first().folderName
                                FolderData(path, name, files.size, pickFolderThumbnail(path, files, bookshelfThumbnail, bookSettingsPrefs))
                            }
                            .sortedBy { it.name }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            top = 12.dp,
                            end = 12.dp,
                            bottom = gridBottomPadding
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(folderGroups) { folder ->
                            FolderItem(folder = folder, showThumbnail = showThumbnail, onClick = { selectedFolderPath = folder.path })
                        }
                    }
                } else {
                    // 特定のフォルダ内の本を表示
                    val folderBooks = remember(books, selectedFolderPath, fileSort, bookSettingsVersion) {
                        sortBooks(books.filter { it.folderPath == selectedFolderPath }, fileSort)
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            top = 12.dp,
                            end = 12.dp,
                            bottom = gridBottomPadding
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(folderBooks) { book ->
                            val isSelected = book.id in selectedBookIds
                            val isFavorite = bookFavoritesPrefs.getBoolean(book.id, false)
                            BookItem(
                                book = book,
                                showThumbnail = showThumbnail,
                                readMark = readMark,
                                prefs = bookSettingsPrefs,
                                isSelected = isSelected,
                                isFavorite = isFavorite,
                                enableSwipeDelete = enableSwipeDeleteBook,
                                onClick = {
                                    if (selectedBookIds.isNotEmpty()) {
                                        selectedBookIds = if (isSelected) selectedBookIds - book.id else selectedBookIds + book.id
                                    } else {
                                        selectedBook = book
                                    }
                                },
                                onLongClick = {
                                    selectedBookIds = if (isSelected) selectedBookIds - book.id else selectedBookIds + book.id
                                },
                                onDeleteRequest = { pendingDeleteBooks = listOf(book) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (pendingDeleteBooks.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingDeleteBooks = emptyList() },
            containerColor = Color.DarkGray,
            title = { Text("本を削除しますか？", color = Color.White) },
            text = {
                Text(
                    "選択した本をアプリ内のゴミ箱フォルダへ移動します。",
                    color = Color.White
                )
            },
            confirmButton = {
                Button(onClick = {
                    val targets = pendingDeleteBooks
                    scope.launch {
                        var successCount = 0
                        targets.forEach { book ->
                            if (repository.moveBookToTrash(book)) successCount++
                        }
                        books = books.filterNot { target -> targets.any { it.id == target.id } }
                        selectedBookIds = emptySet()
                        pendingDeleteBooks = emptyList()
                        Toast.makeText(context, "${successCount} 件を削除しました", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteBooks = emptyList() }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

data class FolderData(
    val path: String,
    val name: String,
    val count: Int,
    val thumbnailPath: String?
)

private val fileSortOptionsForBookScreen = listOf(
    "名前 昇順" to "NAME_ASC",
    "名前 降順" to "NAME_DESC",
    "新しい順" to "DATE_DESC",
    "古い順" to "DATE_ASC"
)

private fun sortBooks(books: List<BookData>, mode: String): List<BookData> {
    return when (mode) {
        "NAME_DESC" -> books.sortedByDescending { it.title.lowercase() }
        "DATE_DESC" -> books.sortedByDescending { File(it.path).lastModified() }
        "DATE_ASC" -> books.sortedBy { File(it.path).lastModified() }
        else -> books.sortedBy { it.title.lowercase() }
    }
}

private fun pickFolderThumbnail(
    folderPath: String,
    books: List<BookData>,
    mode: String,
    prefs: android.content.SharedPreferences
): String? {
    val selected = when (mode) {
        "LAST_BOOK" -> books.lastOrNull()
        "LATEST_BOOK" -> books.maxByOrNull { File(it.path).lastModified() }
        "LAST_READ_BOOK" -> {
            val lastReadId = prefs.getString("lastReadBookId:${folderPath.hashCode()}", null)
            books.firstOrNull { it.id == lastReadId } ?: books.firstOrNull()
        }
        else -> books.firstOrNull()
    }
    return selected?.thumbnailPath
}

@Composable
fun FolderItem(folder: FolderData, showThumbnail: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        if (showThumbnail) {
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
                        alpha = 0.6f
                    )
                }
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(48.dp).align(Alignment.Center)
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                ) {
                    Text(
                        text = "${folder.count}",
                        color = Color.White,
                        fontSize = com.example.gallery.ui.AppConstants.TinyFontSize,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Text(
            folder.name,
            color = Color.White,
            fontSize = com.example.gallery.ui.AppConstants.SmallFontSize,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun BookItem(
    book: BookData,
    showThumbnail: Boolean,
    readMark: String,
    prefs: android.content.SharedPreferences,
    isSelected: Boolean,
    isFavorite: Boolean,
    enableSwipeDelete: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    var contentScale by remember { mutableStateOf(ContentScale.Crop) }
    var dragAmountX by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(dragAmountX.roundToInt(), 0) }
            .background(if (isSelected) Color.Cyan.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(8.dp))
            .pointerInput(enableSwipeDelete, book.id) {
                if (enableSwipeDelete) {
                    detectDragGestures(
                        onDragStart = { dragAmountX = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragAmountX += dragAmount.x
                        },
                        onDragEnd = {
                            if (kotlin.math.abs(dragAmountX) > 80f) onDeleteRequest()
                            dragAmountX = 0f
                        },
                        onDragCancel = { dragAmountX = 0f }
                    )
                }
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        if (showThumbnail) {
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
                if (isFavorite) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = "お気に入り",
                        tint = Color.Red,
                        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Text(
            book.title,
            color = Color.White,
            fontSize = com.example.gallery.ui.AppConstants.SmallFontSize,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            "${book.pageCount} ページ",
            color = Color.Gray,
            fontSize = com.example.gallery.ui.AppConstants.TinyFontSize,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        val lastReadPage = prefs.getInt("lastReadPage:${book.id}", -1)
        if (readMark != "NONE" && lastReadPage >= 0) {
            val progress = ((lastReadPage + 1).toFloat() / book.pageCount.coerceAtLeast(1)).coerceIn(0f, 1f)
            val label = when (readMark) {
                "ICON" -> "既読"
                "PAGE" -> "${lastReadPage + 1}/${book.pageCount}"
                "PERCENT" -> "${(progress * 100).toInt()}%"
                else -> ""
            }
            if (readMark == "PROGRESS_BAR") {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                )
            } else if (label.isNotEmpty()) {
                Text(
                    label,
                    color = Color.Cyan,
                    fontSize = com.example.gallery.ui.AppConstants.TinyFontSize,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

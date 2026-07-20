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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
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
import androidx.compose.ui.res.dimensionResource
import com.example.gallery.R
import com.example.gallery.data.repository.BookData
import com.example.gallery.data.repository.BookRepository
import com.example.gallery.data.repository.BookType
import com.example.gallery.service.GlobalOperationService
import com.example.gallery.ui.AppRoutes
import com.example.gallery.ui.component.GalleryTopAppBar
import com.example.gallery.ui.component.OperationProgressIndicator
import com.example.gallery.ui.screen.BookViewerScreen
import com.example.gallery.ui.theme.GalleryThemeTokens
import com.example.gallery.ui.theme.galleryTypography
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
    onFolderStateChanged: (Boolean) -> Unit = {},
    initialJumpBookId: String? = null,
    initialJumpPage: Int = -1,
    onJumpHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { BookRepository(context) }
    val scope = rememberCoroutineScope()
    val bookScanTitle = stringResource(R.string.book_scan_progress_title)
    val bookScanCheckingText = stringResource(R.string.book_scan_checking_folders)
    val bookScanCompleteText = stringResource(R.string.book_scan_complete)
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
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + dimensionResource(R.dimen.viewer_clock_battery_padding_top)
    var books by remember { mutableStateOf<List<BookData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedBookIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var favoriteVersion by remember { mutableIntStateOf(0) }
    var pendingDeleteBooks by remember { mutableStateOf<List<BookData>>(emptyList()) }
    val colors = GalleryThemeTokens.colors

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
    LaunchedEffect(selectedFolderPath) {
        onFolderStateChanged(selectedFolderPath != null)
    }

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
            val operationId = GlobalOperationService.startOperation(
                title = context.getString(R.string.book_scan_progress_title),
                tag = "BOOK_SCAN",
                canCancel = false
            )
            try {
                GlobalOperationService.updateProgress(0.15f, context.getString(R.string.book_scan_checking_folders), operationId)
                books = sortBooks(repository.scanBooks(), fileSort)
                GlobalOperationService.updateProgress(1f, context.getString(R.string.book_scan_complete), operationId)
            } finally {
                GlobalOperationService.finishOperation(operationId)
                isLoading = false
            }
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
                isLoading = true
                val operationId = GlobalOperationService.startOperation(
                    title = bookScanTitle,
                    tag = "BOOK_SCAN",
                    canCancel = false
                )
                try {
                    GlobalOperationService.updateProgress(0.15f, bookScanCheckingText, operationId)
                    books = sortBooks(repository.scanBooks(), fileSort)
                    GlobalOperationService.updateProgress(1f, bookScanCompleteText, operationId)
                } finally {
                    GlobalOperationService.finishOperation(operationId)
                }
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
                onNavigateToBookmarks = onNavigateToBookmarks,
                onOpenBookSettings = onOpenBookSettings,
                initialPage = startPageForSelectedBook
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.topBar)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(dimensionResource(R.dimen.header_height))
                    .padding(horizontal = dimensionResource(R.dimen.spacing_medium)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (selectedBookIds.isNotEmpty()) {
                    IconButton(onClick = { selectedBookIds = emptySet() }) {
                        Icon(Icons.Default.Close, stringResource(R.string.btn_deselect), tint = colors.primaryText)
                    }
                    Text(
                        stringResource(R.string.trash_item_count, selectedBookIds.size),
                        style = galleryTypography.bodySecondary.copy(color = colors.primaryText)
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        val shouldFavorite = selectedBookIds.any { id -> !bookFavoritesPrefs.getBoolean(id, false) }
                        val editor = bookFavoritesPrefs.edit()
                        selectedBookIds.forEach { id -> editor.putBoolean(id, shouldFavorite) }
                        editor.apply()
                        favoriteVersion++
                        selectedBookIds = emptySet()
                        Toast.makeText(context, if (shouldFavorite) context.getString(R.string.msg_added_to_favorites) else context.getString(R.string.msg_removed_from_favorites), Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Favorite, stringResource(R.string.label_favorites), tint = colors.danger)
                    }
                    IconButton(onClick = {
                        pendingDeleteBooks = books.filter { it.id in selectedBookIds }
                    }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.btn_delete), tint = colors.primaryText)
                    }
                } else {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, stringResource(R.string.btn_open), tint = colors.primaryText)
                    }
                    Text(
                        stringResource(R.string.label_books_title),
                        style = galleryTypography.header
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        onNavigateToBookmarks()
                    }) {
                        Icon(Icons.Default.Bookmark, stringResource(R.string.nav_book_bookmarks), tint = colors.accent)
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, stringResource(R.string.label_sort), tint = colors.primaryText)
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            fileSortOptionsForBookScreen(context).forEach { (label, value) ->
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
                    IconButton(onClick = onOpenBookSettings) {
                        Icon(Icons.Default.Settings, stringResource(R.string.nav_settings), tint = colors.primaryText)
                    }
                }
            }
            if (false && isLoading && books.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimensionResource(R.dimen.spacing_base), vertical = dimensionResource(R.dimen.spacing_small)),
                    contentAlignment = Alignment.Center
                ) {
                    OperationProgressIndicator(
                        label = stringResource(R.string.book_scan_progress),
                        progress = null,
                        displayMode = "MAX",
                        minimumStyle = "BAR"
                    )
                }
            }

            // パンくずリスト風の表示
                val folderName = books.find { it.folderPath == selectedFolderPath }?.folderName ?: stringResource(R.string.label_none)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = dimensionResource(R.dimen.spacing_medium), vertical = dimensionResource(R.dimen.spacing_small)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stringResource(R.string.label_books_root)} > ",
                        style = galleryTypography.smallMuted,
                        modifier = Modifier.clickable { selectedFolderPath = null }
                    )
                    Text(
                        text = folderName,
                        style = galleryTypography.small.copy(color = colors.primaryText)
                    )
                }

            if (!hasFullStorageAccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(dimensionResource(R.dimen.spacing_extra_large))) {
                        Icon(Icons.Default.Settings, null, tint = colors.mutedText, modifier = Modifier.size(dimensionResource(R.dimen.grid_bottom_padding) * 0.64f)) // 64.dp
                        Spacer(Modifier.height(dimensionResource(R.dimen.spacing_medium)))
                        Text(
                            stringResource(R.string.msg_storage_access_desc),
                            color = colors.primaryText,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(dimensionResource(R.dimen.spacing_large)))
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
                            Text(stringResource(R.string.book_grant_permission))
                        }
                    }
                }
            } else if (isLoading && books.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = colors.primaryText)
                        Spacer(Modifier.height(dimensionResource(R.dimen.spacing_base)))
                        Text(stringResource(R.string.book_scan_progress_title), color = colors.primaryText)
                    }
                }
            }
else if (books.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.book_no_books),
                            style = galleryTypography.bodyMuted
                        )
                        Text(
                            stringResource(R.string.book_check_permission),
                            style = galleryTypography.tiny.copy(color = colors.divider.copy(alpha = 1f))
                        )
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
                            start = dimensionResource(R.dimen.spacing_base),
                            top = dimensionResource(R.dimen.spacing_base),
                            end = dimensionResource(R.dimen.spacing_base),
                            bottom = gridBottomPadding
                        ),
                        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_base)),
                        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_medium))
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
                            start = dimensionResource(R.dimen.spacing_base),
                            top = dimensionResource(R.dimen.spacing_base),
                            end = dimensionResource(R.dimen.spacing_base),
                            bottom = gridBottomPadding
                        ),
                        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_base)),
                        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_medium))
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
            containerColor = colors.surfaceVariant,
            title = { Text(stringResource(R.string.book_delete_confirm), color = colors.primaryText) },
            text = {
                Text(
                    stringResource(R.string.msg_move_books_to_trash),
                    color = colors.primaryText
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
                        Toast.makeText(context, context.getString(R.string.msg_deleted_count, successCount), Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteBooks = emptyList() }) {
                    Text(stringResource(R.string.btn_cancel))
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

private fun fileSortOptionsForBookScreen(context: Context) = listOf(
    context.getString(R.string.opt_name_asc) to "NAME_ASC",
    context.getString(R.string.opt_name_desc) to "NAME_DESC",
    context.getString(R.string.opt_date_desc) to "DATE_DESC",
    context.getString(R.string.opt_date_asc) to "DATE_ASC"
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
    val colors = GalleryThemeTokens.colors
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
                    .clip(RoundedCornerShape(dimensionResource(R.dimen.radius_medium)))
                    .background(colors.surfaceVariant)
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
                    tint = colors.primaryText.copy(alpha = 0.8f),
                    modifier = Modifier.size(dimensionResource(R.dimen.viewer_bottom_bar_height)).align(Alignment.Center)
                )
                Surface(
                    color = colors.background.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(dimensionResource(R.dimen.radius_small)),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(dimensionResource(R.dimen.spacing_tiny))
                ) {
                    Text(
                        text = "${folder.count}",
                        style = galleryTypography.tiny.copy(color = colors.primaryText),
                        modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.spacing_tiny), vertical = dimensionResource(R.dimen.spacing_micro))
                    )
                }
            }
            Spacer(Modifier.height(dimensionResource(R.dimen.spacing_tiny)))
        }
        Text(
            folder.name,
            style = galleryTypography.small.copy(color = colors.primaryText),
            maxLines = 2,
            modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.spacing_tiny))
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
    val colors = GalleryThemeTokens.colors
    var contentScale by remember { mutableStateOf(ContentScale.Crop) }
    var dragAmountX by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(dragAmountX.roundToInt(), 0) }
            .background(if (isSelected) colors.accent.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(dimensionResource(R.dimen.radius_medium)))
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
                    .clip(RoundedCornerShape(dimensionResource(R.dimen.radius_medium)))
                    .background(colors.surfaceVariant)
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
                        contentDescription = stringResource(R.string.label_favorites),
                        tint = colors.danger,
                        modifier = Modifier.align(Alignment.BottomStart).padding(dimensionResource(R.dimen.spacing_tiny)).size(dimensionResource(R.dimen.icon_size_small))
                    )
                }
            }
            Spacer(Modifier.height(dimensionResource(R.dimen.spacing_tiny)))
        }
        Text(
            book.title,
            style = galleryTypography.small.copy(color = colors.primaryText),
            maxLines = 2,
            modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.spacing_tiny))
        )
        Text(
            stringResource(R.string.msg_page_count, book.pageCount),
            style = galleryTypography.tiny,
            modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.spacing_tiny))
        )
        val lastReadPage = prefs.getInt("lastReadPage:${book.id}", -1)
        if (readMark != "NONE" && lastReadPage >= 0) {
            val progress = ((lastReadPage + 1).toFloat() / book.pageCount.coerceAtLeast(1)).coerceIn(0f, 1f)
            val label = when (readMark) {
                "ICON" -> stringResource(R.string.msg_read_status_done)
                "PAGE" -> "${lastReadPage + 1}/${book.pageCount}"
                "PERCENT" -> "${(progress * 100).toInt()}%"
                else -> ""
            }
            if (readMark == "PROGRESS_BAR") {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = dimensionResource(R.dimen.spacing_tiny)),
                    color = colors.accent,
                    trackColor = colors.divider
                )
            } else if (label.isNotEmpty()) {
                Text(
                    label,
                    style = galleryTypography.tiny.copy(color = colors.accent),
                    modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.spacing_tiny))
                )
            }
        }
    }
}

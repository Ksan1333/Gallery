package com.example.gallery.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import com.example.gallery.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.gallery.data.repository.BookData
import com.example.gallery.data.repository.BookRepository
import com.example.gallery.ui.state.GalleryState
import com.example.gallery.ui.component.GalleryGridView
import com.example.gallery.ui.component.GalleryTopAppBar
import com.example.gallery.ui.theme.GalleryThemeTokens
import kotlinx.coroutines.launch

@Composable
fun TrashScreen(
    onShowViewer: () -> Unit,
    onHideViewer: () -> Unit,
    galleryState: GalleryState,
    onMenuClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bookRepository = remember { BookRepository(context) }
    val trashMedia by galleryState.repository.getTrashMedia().collectAsState(initial = emptyList())
    var trashedBooks by remember { mutableStateOf<List<BookData>>(emptyList()) }
    val colors = GalleryThemeTokens.colors
    val textSizes = GalleryThemeTokens.textSizes

    var selectedImageIndex by remember { mutableStateOf<Int?>(null) }
    var selectedBook by remember { mutableStateOf<BookData?>(null) }
    var clearSelectionSignal by remember { mutableIntStateOf(0) }
    var isSelectionModeActive by remember { mutableStateOf(false) }
    val selectedUris = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        trashedBooks = bookRepository.loadTrashedBooks()
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            GalleryTopAppBar(
                title = stringResource(R.string.trash_title),
                navigationIcon = if (onMenuClick != null) Icons.Default.Menu else null,
                navigationContentDescription = stringResource(R.string.btn_open),
                onNavigationClick = onMenuClick,
                actions = {
                    if (isSelectionModeActive && selectedUris.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        galleryState.repository.permanentlyDelete(selectedUris.toList())
                                        clearSelectionSignal++
                                        selectedUris.clear()
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.trash_permanent_delete), color = colors.danger, fontSize = textSizes.small)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        galleryState.repository.restoreFromTrash(selectedUris.toList())
                                        clearSelectionSignal++
                                        selectedUris.clear()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.btn_restore))
                            }
                        }
                    }
                }
            )

            if (trashMedia.isEmpty() && trashedBooks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.trash_empty), color = colors.mutedText)
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    if (trashMedia.isNotEmpty()) {
                        Box(Modifier.weight(if (trashedBooks.isEmpty()) 1f else 0.62f)) {
                            GalleryGridView(
                                imageList = trashMedia,
                                onImageClick = { index, _ ->
                                    selectedImageIndex = index
                                    onShowViewer()
                                },
                                galleryState = galleryState,
                                clearSelectionSignal = clearSelectionSignal,
                                onSelectionModeChanged = { isSelectionModeActive = it },
                                onSelectionChanged = { uris ->
                                    selectedUris.clear()
                                    selectedUris.addAll(uris)
                                },
                                modifier = Modifier.fillMaxSize(),
                                isFilterEnabled = false,
                                isTrashMode = true,
                                onScrollConsumed = { }
                            )
                        }
                    }
                    if (trashedBooks.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(if (trashMedia.isEmpty()) 1f else 0.38f)
                                .background(colors.background.copy(alpha = 0.2f))
                        ) {
                            Text(
                                stringResource(R.string.nav_books),
                                color = colors.primaryText,
                                fontSize = textSizes.subtitle,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(trashedBooks) { book ->
                                    TrashBookItem(book = book, onClick = {
                                        selectedBook = book
                                        onShowViewer()
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }

        selectedImageIndex?.let { index ->
            MediaViewerScreen(
                onClickedClose = { selectedImageIndex = null; onHideViewer() },
                initialPage = index,
                imageList = trashMedia,
                galleryState = galleryState,
                onPageSelected = { selectedImageIndex = it },
                showDeleteButton = false, // ゴミ箱内なので削除ボタンの代わりに復元ボタンを表示する。
                isTrashMode = true
            )
        }

        selectedBook?.let { book ->
            BookViewerScreen(
                book = book,
                repository = bookRepository,
                onClose = {
                    selectedBook = null
                    onHideViewer()
                }
            )
        }
    }
}

@Composable
private fun TrashBookItem(book: BookData, onClick: () -> Unit) {
    val colors = GalleryThemeTokens.colors
    val textSizes = GalleryThemeTokens.textSizes
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceVariant)
        ) {
            if (book.thumbnailPath != null) {
                AsyncImage(
                    model = book.thumbnailPath,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Surface(
                color = colorResource(R.color.book_badge_color),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(stringResource(R.string.nav_books), color = colors.primaryText, fontSize = textSizes.tiny)
                }
            }
        }
        Text(
            book.title,
            color = colors.primaryText,
            fontSize = textSizes.small,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

package com.example.gallery.ui.screen

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.example.gallery.R
import com.example.gallery.data.repository.BookRepository
import com.example.gallery.ui.component.GalleryTopAppBar
import com.example.gallery.ui.theme.GalleryThemeTokens
import org.json.JSONObject

@Composable
fun BookBookmarksScreen(
    onMenuClick: () -> Unit,
    onBookClick: (String) -> Unit,
    onBookmarksChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { BookRepository(context) }
    val colors = GalleryThemeTokens.colors
    val bookmarksPrefs = remember { context.getSharedPreferences("book_bookmarks", Context.MODE_PRIVATE) }

    // キャッシュされた本の一覧を取得して、しおりに挟まれている本の詳細情報を紐付ける
    var bookmarkItems by remember { mutableStateOf<List<BookmarkFullItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun removeBookmark(id: String) {
        bookmarksPrefs.edit().remove(id).apply()
        bookmarkItems = bookmarkItems.filterNot { it.id == id }
        onBookmarksChanged()
    }

    LaunchedEffect(Unit) {
        val cachedBooks = repository.loadCachedBooks()
        val bookmarks = bookmarksPrefs.all
        bookmarkItems = bookmarks.mapNotNull { (id, data) ->
            val book = cachedBooks.find { it.id == id } ?: return@mapNotNull null
            runCatching {
                val json = JSONObject(data.toString())
                val page = json.optInt("page", 0)
                BookmarkFullItem(id, book.title, book.path, book.type, page)
            }.getOrNull()
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            GalleryTopAppBar(
                title = stringResource(R.string.nav_book_bookmarks),
                navigationIcon = Icons.Default.Menu,
                navigationContentDescription = stringResource(R.string.btn_open),
                onNavigationClick = onMenuClick,
                centered = true
            )
        },
        containerColor = colors.background
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primaryText)
            }
        } else if (bookmarkItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.bookmarks_empty), color = colors.mutedText)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(dimensionResource(R.dimen.spacing_base)),
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_base)),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_medium))
            ) {
                items(bookmarkItems) { item ->
                    BookmarkGridItem(
                        item = item,
                        repository = repository,
                        onClick = { onBookClick(item.id) },
                        onDelete = { removeBookmark(item.id) }
                    )
                }
            }
        }
    }
}

data class BookmarkFullItem(
    val id: String,
    val title: String,
    val path: String,
    val type: com.example.gallery.data.repository.BookType,
    val page: Int
)

@Composable
private fun BookmarkGridItem(
    item: BookmarkFullItem,
    repository: BookRepository,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = GalleryThemeTokens.colors
    val textSizes = GalleryThemeTokens.textSizes
    var pageBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(item) {
        val bitmap = if (item.type == com.example.gallery.data.repository.BookType.ZIP) {
            repository.getZipPage(item.path, item.page, 640)
        } else {
            repository.getPdfPage(item.path, item.page, 640)
        }
        pageBitmap = bitmap
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(dimensionResource(R.dimen.radius_medium)))
                .background(colors.surfaceVariant)
        ) {
            if (pageBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = pageBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primaryText.copy(alpha = 0.3f), modifier = Modifier.size(dimensionResource(R.dimen.icon_size_medium)))
                }
            }
            // しおりアイコン
            Icon(
                imageVector = Icons.Default.Bookmark,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.padding(dimensionResource(R.dimen.spacing_tiny)).size(dimensionResource(R.dimen.icon_size_check)).align(Alignment.TopEnd)
            )
            // ページ番号
            Surface(
                color = colors.background.copy(alpha = 0.6f),
                shape = RoundedCornerShape(dimensionResource(R.dimen.radius_small)),
                modifier = Modifier.align(Alignment.BottomStart).padding(dimensionResource(R.dimen.spacing_tiny))
            ) {
                Text(
                    text = "P.${item.page + 1}",
                    color = colors.primaryText,
                    fontSize = textSizes.tiny,
                    modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.spacing_tiny), vertical = dimensionResource(R.dimen.spacing_micro))
                )
            }
            // 削除ボタン
            IconButton(
                onClick = { onDelete() },
                modifier = Modifier.align(Alignment.BottomEnd).size(dimensionResource(R.dimen.icon_size_large)).padding(dimensionResource(R.dimen.spacing_tiny))
            ) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.btn_deselect), tint = colors.primaryText.copy(alpha = 0.7f), modifier = Modifier.size(dimensionResource(R.dimen.icon_size_edit)))
            }
        }
        Spacer(Modifier.height(dimensionResource(R.dimen.spacing_tiny)))
        Text(
            item.title,
            color = colors.primaryText,
            fontSize = textSizes.extraSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.spacing_tiny))
        )
        TextButton(
            onClick = onDelete,
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = colors.primaryText.copy(alpha = 0.75f), modifier = Modifier.size(dimensionResource(R.dimen.icon_size_deselect)))
            Spacer(Modifier.width(dimensionResource(R.dimen.spacing_tiny)))
            Text(stringResource(R.string.btn_deselect), color = colors.primaryText.copy(alpha = 0.85f), fontSize = textSizes.tiny)
        }
    }
}

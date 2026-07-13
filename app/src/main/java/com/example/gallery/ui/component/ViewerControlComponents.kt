package com.example.gallery.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.gallery.R
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.theme.GalleryThemeTokens

data class ViewerAction(
    val name: String,
    val icon: ImageVector,
    val label: String,
    val color: Color? = null
)

fun isViewerOverflowActionName(name: String): Boolean {
    return name == AppConstants.ACTION_OVERFLOW || name.contains("3")
}

@Composable
fun resolveViewerActionLabel(name: String): String {
    return when (name) {
        AppConstants.ACTION_TRASH -> stringResource(R.string.label_action_trash)
        AppConstants.ACTION_CLOSE -> stringResource(R.string.label_action_close)
        AppConstants.ACTION_SETTINGS -> stringResource(R.string.label_action_settings)
        AppConstants.ACTION_BOOKMARK -> stringResource(R.string.label_action_bookmark)
        AppConstants.ACTION_ROTATE -> stringResource(R.string.label_action_rotate)
        AppConstants.ACTION_SCREENSHOT -> stringResource(R.string.label_action_screenshot)
        AppConstants.ACTION_PREV -> stringResource(R.string.label_action_prev)
        AppConstants.ACTION_NEXT -> stringResource(R.string.label_action_next)
        AppConstants.ACTION_PLAY_PAUSE -> stringResource(R.string.label_action_play_pause)
        AppConstants.ACTION_FAVORITE -> stringResource(R.string.label_action_favorite)
        AppConstants.ACTION_SLIDESHOW -> stringResource(R.string.label_action_slideshow)
        AppConstants.ACTION_GIF -> stringResource(R.string.label_action_gif)
        AppConstants.ACTION_ASCII2D -> stringResource(R.string.label_action_ascii2d)
        AppConstants.ACTION_WALLPAPER -> stringResource(R.string.label_action_wallpaper)
        AppConstants.ACTION_THUMBNAIL -> stringResource(R.string.label_action_folder_thumbnail)
        AppConstants.ACTION_TAG -> stringResource(R.string.label_action_tag)
        AppConstants.ACTION_TOGGLE_UI -> stringResource(R.string.label_action_toggle_ui)
        AppConstants.ACTION_ZOOM -> stringResource(R.string.label_action_zoom)
        AppConstants.ACTION_SEARCH -> stringResource(R.string.label_action_search)
        "保存" -> stringResource(R.string.label_action_save)
        AppConstants.ACTION_PREV_PAGE -> stringResource(R.string.label_action_prev_page)
        AppConstants.ACTION_NEXT_PAGE -> stringResource(R.string.label_action_next_page)
        AppConstants.ACTION_PREV_BOOK -> stringResource(R.string.label_action_prev_book)
        AppConstants.ACTION_NEXT_BOOK -> stringResource(R.string.label_action_next_book)
        AppConstants.ACTION_MENU -> stringResource(R.string.label_action_menu)
        AppConstants.ACTION_OVERFLOW -> stringResource(R.string.label_3dot_menu)
        "なし" -> stringResource(R.string.label_action_none)
        else -> name
    }
}

@Composable
fun resolveViewerAction(
    name: String,
    isFavorite: Boolean = false,
    isPlaying: Boolean = false,
    isSlideshowRunning: Boolean = false,
    isBookmarked: Boolean = false,
    isGifStepping: Boolean = false
): ViewerAction? {
    val colors = GalleryThemeTokens.colors
    val trashLabel = stringResource(R.string.label_action_trash)
    val closeLabel = stringResource(R.string.label_action_close)
    val settingsLabel = stringResource(R.string.label_action_settings)
    val bookmarkLabel = stringResource(R.string.label_action_bookmark)
    val rotateLabel = stringResource(R.string.label_action_rotate)
    val screenshotLabel = stringResource(R.string.label_action_screenshot)
    val prevLabel = stringResource(R.string.label_action_prev)
    val nextLabel = stringResource(R.string.label_action_next)
    val favoriteLabel = stringResource(R.string.label_action_favorite)
    val slideshowLabel = stringResource(R.string.label_action_slideshow)
    val gifLabel = stringResource(R.string.label_action_gif)
    val ascii2dLabel = stringResource(R.string.label_action_ascii2d)
    val wallpaperLabel = stringResource(R.string.label_action_wallpaper)
    val thumbLabel = stringResource(R.string.label_action_folder_thumbnail)
    val tagLabel = stringResource(R.string.label_action_tag)

    return when (name) {
        AppConstants.ACTION_TRASH -> ViewerAction(name, Icons.Default.Delete, trashLabel, colors.danger)
        AppConstants.ACTION_CLOSE -> ViewerAction(name, Icons.Default.Close, closeLabel)
        AppConstants.ACTION_SETTINGS -> ViewerAction(name, Icons.Default.Settings, settingsLabel)
        AppConstants.ACTION_BOOKMARK -> ViewerAction(
            name,
            if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
            bookmarkLabel,
            if (isBookmarked) colors.accent else null
        )
        AppConstants.ACTION_ROTATE -> ViewerAction(name, Icons.Default.ScreenRotation, rotateLabel)
        AppConstants.ACTION_SCREENSHOT -> ViewerAction(name, Icons.Default.Screenshot, screenshotLabel)
        AppConstants.ACTION_PREV -> ViewerAction(name, Icons.AutoMirrored.Filled.ArrowBack, prevLabel)
        AppConstants.ACTION_NEXT -> ViewerAction(name, Icons.AutoMirrored.Filled.ArrowForward, nextLabel)
        AppConstants.ACTION_PLAY_PAUSE -> ViewerAction(
            name,
            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            if (isPlaying) stringResource(R.string.viewer_pause) else stringResource(R.string.viewer_play)
        )
        AppConstants.ACTION_FAVORITE -> ViewerAction(
            name,
            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            favoriteLabel,
            if (isFavorite) colors.danger else null
        )
        AppConstants.ACTION_SLIDESHOW -> ViewerAction(
            name,
            if (isSlideshowRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
            if (isSlideshowRunning) stringResource(R.string.viewer_slideshow_stop) else stringResource(R.string.viewer_slideshow_start)
        )
        AppConstants.ACTION_GIF -> ViewerAction(
            name,
            if (isGifStepping) Icons.Default.Close else Icons.Default.Collections,
            if (isGifStepping) stringResource(R.string.viewer_gif_stepping_stop) else stringResource(R.string.viewer_gif_stepping)
        )
        AppConstants.ACTION_ASCII2D -> ViewerAction(name, Icons.Default.ImageSearch, ascii2dLabel)
        AppConstants.ACTION_WALLPAPER -> ViewerAction(name, Icons.Default.Wallpaper, wallpaperLabel)
        AppConstants.ACTION_THUMBNAIL -> ViewerAction(name, Icons.Default.FolderSpecial, thumbLabel)
        AppConstants.ACTION_TAG -> ViewerAction(name, Icons.Default.LocalOffer, tagLabel)
        AppConstants.ACTION_OVERFLOW -> ViewerAction(name, Icons.Default.MoreVert, stringResource(R.string.label_3dot_menu))
        else -> null
    }
}

@Composable
fun ViewerControlBar(
    actions: List<String>,
    isFavorite: Boolean = false,
    isPlaying: Boolean = false,
    isSlideshowRunning: Boolean = false,
    isBookmarked: Boolean = false,
    isGifStepping: Boolean = false,
    onAction: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        actions.forEach { actionName ->
            val action = resolveViewerAction(
                actionName,
                isFavorite,
                isPlaying,
                isSlideshowRunning,
                isBookmarked,
                isGifStepping
            )
            if (action != null) {
                GalleryFloatingActionButton(
                    icon = action.icon,
                    tooltipDescription = action.label,
                    size = 40.dp,
                    iconSize = 22.dp,
                    onClick = { onAction(actionName) },
                    contentColor = action.color ?: GalleryThemeTokens.colors.primaryText
                )
            }
        }
    }
}

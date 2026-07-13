package com.example.gallery.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.gallery.R
import com.example.gallery.ui.AppRoutes
import com.example.gallery.ui.theme.GalleryThemeTokens

enum class TutorialTarget(val id: String, val labelRes: Int) {
    OVERVIEW("overview", R.string.tutorial_overview_title),
    HOME("home", R.string.tutorial_home_title),
    SEARCH("search", R.string.tutorial_search_title),
    FOLDERS("folders", R.string.tutorial_folders_title),
    BOOKS("books", R.string.tutorial_books_title),
    REFERENCES("references", R.string.tutorial_references_title),
    VIDEO_DOWNLOADER("video_downloader", R.string.tutorial_video_downloader_title),
    TRASH("trash", R.string.tutorial_trash_title),
    SETTINGS("settings", R.string.tutorial_settings_title)
}

private data class TutorialPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val tips: List<String> = emptyList()
)

private object TutorialCalloutTokens {
    val OverlayPadding = 18.dp
    val BubbleRadius = 8.dp
    val BubblePadding = 16.dp
    val BubbleMaxHeight = 520.dp
    val BubbleGap = 12.dp
    val HeaderGap = 10.dp
    val TipPaddingHorizontal = 12.dp
    val TipPaddingVertical = 8.dp
    val TipRadius = 8.dp
    val ButtonGap = 8.dp
    val IconSize = 34.dp
    val Elevation = 10.dp
    const val OverlayAlpha = 0.72f
    const val BubbleWidthFraction = 0.88f
}

fun allTutorialTargets(): List<TutorialTarget> = TutorialTarget.entries

fun defaultTutorialTargetIds(): Set<String> = setOf(
    TutorialTarget.HOME.id,
    TutorialTarget.SEARCH.id,
    TutorialTarget.FOLDERS.id,
    TutorialTarget.BOOKS.id,
    TutorialTarget.SETTINGS.id
)

fun tutorialTargetById(id: String?): TutorialTarget? =
    TutorialTarget.entries.firstOrNull { it.id == id }

fun tutorialTargetForRoute(route: String?): TutorialTarget? {
    return when {
        route == AppRoutes.HOME -> TutorialTarget.HOME
        route == AppRoutes.SEARCH -> TutorialTarget.SEARCH
        route == AppRoutes.FOLDERS || route == AppRoutes.FOLDERS_SELECT -> TutorialTarget.FOLDERS
        route == AppRoutes.BOOKS || route == AppRoutes.BOOK_BOOKMARKS -> TutorialTarget.BOOKS
        route == AppRoutes.REFERENCES || route?.startsWith("reference_") == true -> TutorialTarget.REFERENCES
        route == AppRoutes.VIDEO_DOWNLOADER -> TutorialTarget.VIDEO_DOWNLOADER
        route == AppRoutes.TRASH -> TutorialTarget.TRASH
        route == AppRoutes.SETTINGS -> TutorialTarget.SETTINGS
        else -> null
    }
}

@Composable
fun TutorialDialog(
    target: TutorialTarget = TutorialTarget.OVERVIEW,
    onDismiss: () -> Unit
) {
    val colors = GalleryThemeTokens.colors
    val pages = tutorialPages(target, colors)
    var page by remember(target) { mutableIntStateOf(0) }
    val current = pages[page]

    TutorialCenteredModal(
        page = page,
        pageCount = pages.size,
        current = current,
        onDismiss = onDismiss,
        onNext = { if (page < pages.lastIndex) page++ else onDismiss() }
    )
    return

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(current.icon, contentDescription = null, tint = current.color, modifier = Modifier.size(48.dp))
                Text(current.title, color = colors.primaryText, fontWeight = FontWeight.Bold, fontSize = GalleryThemeTokens.textSizes.header)
                Text(current.description, color = colors.secondaryText, textAlign = TextAlign.Center)
                current.tips.forEach { tip ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.card, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("→", color = current.color, fontWeight = FontWeight.Bold)
                        Text(tip, color = colors.primaryText, modifier = Modifier.weight(1f))
                    }
                }
                Text("${page + 1} / ${pages.size}", color = colors.mutedText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.tutorial_close))
                    }
                    Button(
                        onClick = {
                            if (page < pages.lastIndex) page++ else onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = current.color)
                    ) {
                        Text(if (page == pages.lastIndex) stringResource(R.string.tutorial_done) else stringResource(R.string.tutorial_next))
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialCenteredModal(
    page: Int,
    pageCount: Int,
    current: TutorialPage,
    onDismiss: () -> Unit,
    onNext: () -> Unit
) {
    val colors = GalleryThemeTokens.colors
    val closeLabel = stringResource(R.string.tutorial_close)
    val nextLabel = stringResource(R.string.tutorial_next)
    val doneLabel = stringResource(R.string.tutorial_done)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.68f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = colors.surface,
                shape = RoundedCornerShape(10.dp),
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(22.dp)
                        .heightIn(max = 680.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(current.icon, contentDescription = null, tint = current.color, modifier = Modifier.size(52.dp))
                    Text(current.title, color = colors.primaryText, fontWeight = FontWeight.Bold, fontSize = GalleryThemeTokens.textSizes.header, textAlign = TextAlign.Center)
                    Text(current.description, color = colors.secondaryText, textAlign = TextAlign.Center)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        current.tips.forEach { tip ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.card, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("•", color = current.color, fontWeight = FontWeight.Bold)
                                Text(tip, color = colors.primaryText, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${page + 1} / $pageCount", color = colors.mutedText)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onDismiss) { Text(closeLabel) }
                            Button(
                                onClick = onNext,
                                colors = ButtonDefaults.buttonColors(containerColor = current.color)
                            ) {
                                Text(if (page == pageCount - 1) doneLabel else nextLabel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialCalloutOverlay(
    target: TutorialTarget,
    page: Int,
    pageCount: Int,
    current: TutorialPage,
    onDismiss: () -> Unit,
    onNext: () -> Unit
) {
    val colors = GalleryThemeTokens.colors
    val pointerLabel = stringResource(tutorialPointerLabelRes(target, page))
    val closeLabel = stringResource(R.string.tutorial_close)
    val nextLabel = stringResource(R.string.tutorial_next)
    val doneLabel = stringResource(R.string.tutorial_done)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = TutorialCalloutTokens.OverlayAlpha))
                .padding(TutorialCalloutTokens.OverlayPadding)
        ) {
            Surface(
                color = colors.surface,
                shape = RoundedCornerShape(TutorialCalloutTokens.BubbleRadius),
                shadowElevation = TutorialCalloutTokens.Elevation,
                modifier = Modifier
                    .align(tutorialBubbleAlignment(target, page))
                    .fillMaxWidth(TutorialCalloutTokens.BubbleWidthFraction)
            ) {
                Column(
                    modifier = Modifier
                        .padding(TutorialCalloutTokens.BubblePadding)
                        .heightIn(max = TutorialCalloutTokens.BubbleMaxHeight)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(TutorialCalloutTokens.BubbleGap)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TutorialCalloutTokens.HeaderGap)
                    ) {
                        Icon(current.icon, contentDescription = null, tint = current.color, modifier = Modifier.size(TutorialCalloutTokens.IconSize))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(current.title, color = colors.primaryText, fontWeight = FontWeight.Bold, fontSize = GalleryThemeTokens.textSizes.subtitle)
                            Text(pointerLabel, color = current.color, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(current.description, color = colors.secondaryText)
                    current.tips.forEach { tip ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.card, RoundedCornerShape(TutorialCalloutTokens.TipRadius))
                                .padding(horizontal = TutorialCalloutTokens.TipPaddingHorizontal, vertical = TutorialCalloutTokens.TipPaddingVertical),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(TutorialCalloutTokens.ButtonGap)
                        ) {
                            Text("→", color = current.color, fontWeight = FontWeight.Bold)
                            Text(tip, color = colors.primaryText, modifier = Modifier.weight(1f))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${page + 1} / $pageCount", color = colors.mutedText)
                        Row(horizontalArrangement = Arrangement.spacedBy(TutorialCalloutTokens.ButtonGap)) {
                            TextButton(onClick = onDismiss) { Text(closeLabel) }
                            Button(
                                onClick = onNext,
                                colors = ButtonDefaults.buttonColors(containerColor = current.color)
                            ) {
                                Text(if (page == pageCount - 1) doneLabel else nextLabel)
                            }
                        }
                        if (false) {
                        Row(horizontalArrangement = Arrangement.spacedBy(TutorialCalloutTokens.ButtonGap)) {
                            TextButton(onClick = onDismiss) { Text("閉じる") }
                            Button(
                                onClick = onNext,
                                colors = ButtonDefaults.buttonColors(containerColor = current.color)
                            ) {
                                Text(if (page == pageCount - 1) "完了" else "次へ")
                            }
                        }
                        }
                    }
                }
            }
        }
    }
}

private fun tutorialBubbleAlignment(target: TutorialTarget, page: Int): Alignment {
    return when (target) {
        TutorialTarget.HOME -> if (page == 0) Alignment.TopEnd else Alignment.BottomEnd
        TutorialTarget.SEARCH -> Alignment.TopEnd
        TutorialTarget.FOLDERS -> if (page == 0) Alignment.TopStart else Alignment.BottomEnd
        TutorialTarget.BOOKS -> if (page == 0) Alignment.TopEnd else Alignment.BottomStart
        TutorialTarget.TRASH -> Alignment.TopEnd
        TutorialTarget.SETTINGS -> Alignment.TopStart
        TutorialTarget.VIDEO_DOWNLOADER -> Alignment.BottomEnd
        TutorialTarget.REFERENCES -> Alignment.BottomStart
        TutorialTarget.OVERVIEW -> Alignment.Center
    }
}

private fun tutorialPointerLabelRes(target: TutorialTarget, page: Int): Int {
    return when (target) {
        TutorialTarget.HOME -> if (page == 0) R.string.tutorial_hint_home_gallery else R.string.tutorial_hint_home_viewer
        TutorialTarget.SEARCH -> R.string.tutorial_hint_search
        TutorialTarget.FOLDERS -> if (page == 0) R.string.tutorial_hint_folders else R.string.tutorial_hint_folder_bulk_edit
        TutorialTarget.BOOKS -> if (page == 0) R.string.tutorial_hint_books_toolbar else R.string.tutorial_hint_book_viewer
        TutorialTarget.TRASH -> R.string.tutorial_hint_trash_actions
        TutorialTarget.SETTINGS -> R.string.tutorial_hint_settings
        TutorialTarget.VIDEO_DOWNLOADER -> R.string.tutorial_hint_video_downloader
        TutorialTarget.REFERENCES -> R.string.tutorial_hint_references
        TutorialTarget.OVERVIEW -> R.string.tutorial_hint_overview
    }
}

@Composable
fun TutorialSetupDialog(
    onConfirm: () -> Unit,
    onSkip: () -> Unit
) {
    val colors = GalleryThemeTokens.colors
    Dialog(onDismissRequest = onSkip) {
        Surface(color = colors.surface, shape = RoundedCornerShape(8.dp)) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = stringResource(R.string.tutorial_setup_ask),
                    color = colors.primaryText,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.tutorial_setup_sub_desc),
                    color = colors.secondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.tutorial_none))
                    }
                    Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.tutorial_show))
                    }
                }
            }
        }
    }
}

@Composable
fun TutorialChooserDialog(
    onSelect: (TutorialTarget) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = GalleryThemeTokens.colors
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = colors.surface, shape = RoundedCornerShape(8.dp)) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.tutorial_to_watch),
                    color = colors.primaryText,
                    style = MaterialTheme.typography.titleLarge
                )
                allTutorialTargets().forEach { target ->
                    TutorialTargetRow(
                        target = target,
                        checked = false,
                        showCheckbox = false,
                        onClick = { onSelect(target) }
                    )
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.tutorial_close))
                }
            }
        }
    }
}

@Composable
private fun TutorialTargetRow(
    target: TutorialTarget,
    checked: Boolean,
    onClick: () -> Unit,
    showCheckbox: Boolean = true
) {
    val colors = GalleryThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(tutorialIcon(target), contentDescription = null, tint = colors.accent)
        Text(stringResource(target.labelRes), color = colors.primaryText, modifier = Modifier.weight(1f))
        if (showCheckbox) {
            Checkbox(checked = checked, onCheckedChange = { onClick() })
        }
    }
}

@Composable
private fun tutorialPages(target: TutorialTarget, colors: com.example.gallery.ui.theme.GalleryColors): List<TutorialPage> {
    val accent = colors.accent
    val success = colors.success
    val danger = colors.danger
    val soft = colors.accentSoft
    return when (target) {
        TutorialTarget.OVERVIEW -> listOf(
            TutorialPage(
                stringResource(R.string.tutorial_welcome_title),
                stringResource(R.string.tutorial_welcome_desc),
                Icons.Default.Home,
                accent,
                listOf(stringResource(R.string.tutorial_welcome_tip1), stringResource(R.string.tutorial_welcome_tip2), stringResource(R.string.tutorial_welcome_tip3))
            ),
            TutorialPage(
                stringResource(R.string.tutorial_entrance_title),
                stringResource(R.string.tutorial_entrance_desc),
                Icons.Default.Search,
                success,
                listOf(stringResource(R.string.tutorial_entrance_tip1), stringResource(R.string.tutorial_entrance_tip2), stringResource(R.string.tutorial_entrance_tip3))
            )
        )
        TutorialTarget.HOME -> listOf(
            TutorialPage(stringResource(R.string.tutorial_home_title), stringResource(R.string.tutorial_home_desc), Icons.Default.Home, accent, listOf(stringResource(R.string.tutorial_home_tip1), stringResource(R.string.tutorial_home_tip2), stringResource(R.string.tutorial_home_tip3))),
            TutorialPage(stringResource(R.string.tutorial_viewer_title), stringResource(R.string.tutorial_viewer_desc), Icons.Default.ImageSearch, accent, listOf(stringResource(R.string.tutorial_viewer_tip1), stringResource(R.string.tutorial_viewer_tip2), stringResource(R.string.tutorial_viewer_tip3))),
            TutorialPage(stringResource(R.string.tutorial_bulk_title), stringResource(R.string.tutorial_bulk_desc), Icons.Default.AutoAwesome, success, listOf(stringResource(R.string.tutorial_bulk_tip1), stringResource(R.string.tutorial_bulk_tip2), stringResource(R.string.tutorial_bulk_tip3)))
        )
        TutorialTarget.SEARCH -> listOf(
            TutorialPage(stringResource(R.string.tutorial_search_title), stringResource(R.string.tutorial_search_desc), Icons.Default.Search, accent, listOf(stringResource(R.string.tutorial_search_tip1), stringResource(R.string.tutorial_search_tip2), stringResource(R.string.tutorial_search_tip3))),
            TutorialPage(stringResource(R.string.tutorial_tag_search_title), stringResource(R.string.tutorial_tag_search_desc), Icons.Default.AutoAwesome, success, listOf(stringResource(R.string.tutorial_tag_search_tip1), stringResource(R.string.tutorial_tag_search_tip2), stringResource(R.string.tutorial_tag_search_tip3)))
        )
        TutorialTarget.FOLDERS -> listOf(
            TutorialPage(stringResource(R.string.tutorial_folders_title), stringResource(R.string.tutorial_folders_desc), Icons.Default.Folder, soft, listOf(stringResource(R.string.tutorial_folders_tip1), stringResource(R.string.tutorial_folders_tip2), stringResource(R.string.tutorial_folders_tip3))),
            TutorialPage(stringResource(R.string.tutorial_folders_ops_title), stringResource(R.string.tutorial_folders_ops_desc), Icons.Default.Folder, accent, listOf(stringResource(R.string.tutorial_folders_ops_tip1), stringResource(R.string.tutorial_folders_ops_tip2), stringResource(R.string.tutorial_folders_ops_tip3)))
        )
        TutorialTarget.BOOKS -> listOf(
            TutorialPage(stringResource(R.string.tutorial_books_title), stringResource(R.string.tutorial_books_desc), Icons.AutoMirrored.Filled.MenuBook, danger, listOf(stringResource(R.string.tutorial_books_tip1), stringResource(R.string.tutorial_books_tip2), stringResource(R.string.tutorial_books_tip3))),
            TutorialPage(stringResource(R.string.settings_book_viewer), stringResource(R.string.tutorial_book_viewer_desc), Icons.Default.Settings, soft, listOf(stringResource(R.string.tutorial_book_viewer_tip1), stringResource(R.string.tutorial_book_viewer_tip2), stringResource(R.string.tutorial_book_viewer_tip3))),
            TutorialPage(stringResource(R.string.tutorial_book_aux_title), stringResource(R.string.tutorial_book_aux_desc), Icons.AutoMirrored.Filled.MenuBook, accent, listOf(stringResource(R.string.tutorial_book_aux_tip1), stringResource(R.string.tutorial_book_aux_tip2), stringResource(R.string.tutorial_book_aux_tip3)))
        )
        TutorialTarget.REFERENCES -> listOf(
            TutorialPage(stringResource(R.string.tutorial_references_title), stringResource(R.string.tutorial_ref_desc), Icons.Default.ImageSearch, accent, listOf(stringResource(R.string.tutorial_ref_tip1), stringResource(R.string.tutorial_ref_tip2), stringResource(R.string.tutorial_ref_tip3))),
            TutorialPage(stringResource(R.string.tutorial_ref_review_title), stringResource(R.string.tutorial_ref_review_desc), Icons.Default.ImageSearch, success, listOf(stringResource(R.string.tutorial_ref_review_tip1), stringResource(R.string.tutorial_ref_review_tip2), stringResource(R.string.tutorial_ref_review_tip3)))
        )
        TutorialTarget.VIDEO_DOWNLOADER -> listOf(
            TutorialPage(stringResource(R.string.tutorial_video_v_title), stringResource(R.string.tutorial_video_v_desc), Icons.Default.VideoLibrary, accent, listOf(stringResource(R.string.tutorial_video_v_tip1), stringResource(R.string.tutorial_video_v_tip2), stringResource(R.string.tutorial_video_v_tip3))),
            TutorialPage(stringResource(R.string.tutorial_video_downloader_title), stringResource(R.string.tutorial_video_dl_desc), Icons.Default.VideoLibrary, success, listOf(stringResource(R.string.tutorial_video_dl_tip1), stringResource(R.string.tutorial_video_dl_tip2), stringResource(R.string.tutorial_video_dl_tip3))),
            TutorialPage(stringResource(R.string.tutorial_video_dl_conf_title), stringResource(R.string.tutorial_video_dl_conf_desc), Icons.Default.VideoLibrary, accent, listOf(stringResource(R.string.tutorial_video_dl_conf_tip1), stringResource(R.string.tutorial_video_dl_conf_tip2), stringResource(R.string.tutorial_video_dl_conf_tip3)))
        )
        TutorialTarget.TRASH -> listOf(
            TutorialPage(stringResource(R.string.trash_title), stringResource(R.string.tutorial_trash_desc), Icons.Default.Delete, danger, listOf(stringResource(R.string.tutorial_trash_tip1), stringResource(R.string.tutorial_trash_tip2), stringResource(R.string.tutorial_trash_tip3)))
        )
        TutorialTarget.SETTINGS -> listOf(
            TutorialPage(stringResource(R.string.tutorial_settings_top_title), stringResource(R.string.tutorial_settings_top_desc), Icons.Default.Settings, soft, listOf(stringResource(R.string.tutorial_settings_top_tip1), stringResource(R.string.tutorial_settings_top_tip2), stringResource(R.string.tutorial_settings_top_tip3))),
            TutorialPage(stringResource(R.string.tutorial_settings_theme_title), stringResource(R.string.tutorial_settings_theme_desc), Icons.Default.Settings, accent, listOf(stringResource(R.string.tutorial_settings_theme_tip1), stringResource(R.string.tutorial_settings_theme_tip2), stringResource(R.string.tutorial_settings_theme_tip3))),
            TutorialPage(stringResource(R.string.tutorial_settings_viewer_title), stringResource(R.string.tutorial_settings_viewer_desc), Icons.Default.Settings, success, listOf(stringResource(R.string.tutorial_settings_viewer_tip1), stringResource(R.string.tutorial_settings_viewer_tip2), stringResource(R.string.tutorial_settings_viewer_tip3)))
        )
    }
}

private fun tutorialIcon(target: TutorialTarget): ImageVector = when (target) {
    TutorialTarget.OVERVIEW -> Icons.Default.Home
    TutorialTarget.HOME -> Icons.Default.Home
    TutorialTarget.SEARCH -> Icons.Default.Search
    TutorialTarget.FOLDERS -> Icons.Default.Folder
    TutorialTarget.BOOKS -> Icons.AutoMirrored.Filled.MenuBook
    TutorialTarget.REFERENCES -> Icons.Default.ImageSearch
    TutorialTarget.VIDEO_DOWNLOADER -> Icons.Default.VideoLibrary
    TutorialTarget.TRASH -> Icons.Default.Delete
    TutorialTarget.SETTINGS -> Icons.Default.Settings
}

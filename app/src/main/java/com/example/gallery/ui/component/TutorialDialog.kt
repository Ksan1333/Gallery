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

enum class TutorialTarget(val id: String, val label: String) {
    OVERVIEW("overview", "アプリ全体"),
    HOME("home", "ホーム / ギャラリー"),
    SEARCH("search", "検索"),
    FOLDERS("folders", "フォルダ"),
    BOOKS("books", "ブックビューア"),
    REFERENCES("references", "お絵描き資料"),
    VIDEO_DOWNLOADER("video_downloader", "動画DL"),
    TRASH("trash", "ゴミ箱"),
    SETTINGS("settings", "設定")
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
    val pages = remember(target, colors) {
        tutorialPages(target, colors)
    }
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
                        Text("閉じる")
                    }
                    Button(
                        onClick = {
                            if (page < pages.lastIndex) page++ else onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = current.color)
                    ) {
                        Text(if (page == pages.lastIndex) "完了" else "次へ")
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

private fun tutorialPointerLabel(target: TutorialTarget, page: Int): String {
    return when (target) {
        TutorialTarget.HOME -> if (page == 0) "ギャラリーの一覧ボタン付近" else "ビューアの操作ボタン付近"
        TutorialTarget.SEARCH -> "検索バーと条件ボタン付近"
        TutorialTarget.FOLDERS -> if (page == 0) "フォルダ一覧付近" else "右上の一括編集ボタン付近"
        TutorialTarget.BOOKS -> if (page == 0) "右上の更新・設定ボタン付近" else "ブックビューア操作エリア付近"
        TutorialTarget.TRASH -> "右上の復元・削除ボタン付近"
        TutorialTarget.SETTINGS -> "設定カードと右上ボタン付近"
        TutorialTarget.VIDEO_DOWNLOADER -> "URL入力と保存ボタン付近"
        TutorialTarget.REFERENCES -> "資料追加ボタン付近"
        TutorialTarget.OVERVIEW -> "現在の画面の主要ボタン付近"
    }
}

@Composable
fun TutorialSetupDialog(
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onSkip: () -> Unit
) {
    val colors = GalleryThemeTokens.colors
    Dialog(onDismissRequest = onSkip) {
        Surface(color = colors.surface, shape = RoundedCornerShape(8.dp)) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("チュートリアル設定", color = colors.primaryText, fontWeight = FontWeight.Bold, fontSize = GalleryThemeTokens.textSizes.header)
                Text("表示したい画面のチュートリアルを選んでください。", color = colors.secondaryText)
                allTutorialTargets().filterNot { it == TutorialTarget.OVERVIEW }.forEach { target ->
                    TutorialTargetRow(
                        target = target,
                        checked = target.id in selectedIds,
                        onClick = { onToggle(target.id) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) {
                        Text("表示しない")
                    }
                    Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                        Text("保存")
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
                Text("見るチュートリアル", color = colors.primaryText, fontWeight = FontWeight.Bold, fontSize = GalleryThemeTokens.textSizes.header)
                allTutorialTargets().forEach { target ->
                    TutorialTargetRow(
                        target = target,
                        checked = false,
                        showCheckbox = false,
                        onClick = { onSelect(target) }
                    )
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("閉じる")
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
        Text(target.label, color = colors.primaryText, modifier = Modifier.weight(1f))
        if (showCheckbox) {
            Checkbox(checked = checked, onCheckedChange = { onClick() })
        }
    }
}

private fun tutorialPages(target: TutorialTarget, colors: com.example.gallery.ui.theme.GalleryColors): List<TutorialPage> {
    val accent = colors.accent
    val success = colors.success
    val danger = colors.danger
    val soft = colors.accentSoft
    return when (target) {
        TutorialTarget.OVERVIEW -> listOf(
            TutorialPage(
                "Galleryへようこそ",
                "画像、GIF、動画、本、お絵描き資料をまとめて管理できます。",
                Icons.Default.Home,
                accent,
                listOf("左端から右へスワイプ → サイドバーを開く", "下部ナビゲーション → 主要画面へ移動", "右上の設定 → 表示や操作を自分用に調整")
            ),
            TutorialPage(
                "画面ごとの入口",
                "ホーム、フォルダ、本、動画、ゴミ箱、設定はそれぞれ役割が分かれています。",
                Icons.Default.Search,
                success,
                listOf("ホーム → すべてのメディアをまとめて見る", "フォルダ → 保存場所単位で整理", "設定 → 全体設定、テーマ、ブックビューアを調整")
            )
        )
        TutorialTarget.HOME -> listOf(
            TutorialPage("ホーム画面", "すべての画像、GIF、動画を一覧できます。", Icons.Default.Home, accent, listOf("画像をタップ → メディアビューアを開く", "長押し → 選択モードに入る", "右上の検索/並び替え → 表示を絞り込む")),
            TutorialPage("メディアビューア", "タップでコントロールパネル、ダブルタップでズーム、長押しで拡大鏡を使えます。", Icons.Default.ImageSearch, accent, listOf("三点ボタン → タグ編集、設定、ファイル情報", "上方向ドラッグ → 関連情報や詳細", "タッチインジケーターON → タップ領域の枠を表示")),
            TutorialPage("選択と一括編集", "一覧で長押しすると複数選択できます。", Icons.Default.AutoAwesome, success, listOf("右上の鉛筆アイコン → 一括タグ・評価編集", "ハート → お気に入り追加/解除", "三点メニュー → ゴミ箱移動やフォルダ移動"))
        )
        TutorialTarget.SEARCH -> listOf(
            TutorialPage("検索", "タグ、フォルダ、年齢制限、メディア種別を組み合わせて探せます。", Icons.Default.Search, accent, listOf("検索語を入力 → 名前やタグで絞り込み", "条件チップ → 必要な条件だけ追加", "結果をタップ → そのままビューアへ")),
            TutorialPage("タグ検索", "AI解析済みタグや手動タグを使って関連メディアへ移動できます。", Icons.Default.AutoAwesome, success, listOf("タグをタップ → 同じタグのメディアを表示", "GIF/動画だけ → メディア種別で絞り込み", "お気に入りだけ → 保存済み資料を素早く確認"))
        )
        TutorialTarget.FOLDERS -> listOf(
            TutorialPage("フォルダ画面", "保存場所単位でメディアを確認できます。", Icons.Default.Folder, soft, listOf("フォルダをタップ → 中身の一覧へ", "右上の並び替え → 表示順を変更", "戻る先がないとき → ホームへ戻る")),
            TutorialPage("フォルダ内の操作", "フォルダ内でも選択、移動、一括編集ができます。", Icons.Default.Folder, accent, listOf("長押し → 複数選択", "右上の鉛筆アイコン → 一括編集", "三点メニュー → フォルダ移動や削除"))
        )
        TutorialTarget.BOOKS -> listOf(
            TutorialPage("本画面", "ZIP/PDF形式の本を一覧し、しおりから続きへ移動できます。", Icons.AutoMirrored.Filled.MenuBook, danger, listOf("本をタップ → ブックビューアを開く", "右上の更新 → 本フォルダを再走査", "右上の設定 → ブックビューア設定へ")),
            TutorialPage("ブックビューア", "綴じ方向、ページ効果、スクロール表示、タップ領域を設定できます。", Icons.Default.Settings, soft, listOf("左右端タップ → 前/次ページ", "長押し → 押している間だけ拡大鏡", "タッチインジケーターON → 割り当て領域を表示")),
            TutorialPage("読書補助", "時計、バッテリー、既読マーク、スライドショーを使えます。", Icons.AutoMirrored.Filled.MenuBook, accent, listOf("既読マーク → 本一覧で読書状況を確認", "スライドショー → 三点メニューから開始", "ページめくり効果 → 設定で切り替え"))
        )
        TutorialTarget.REFERENCES -> listOf(
            TutorialPage("お絵描き資料", "イラスト用の参考資料をプロジェクト単位で集められます。", Icons.Default.ImageSearch, accent, listOf("プロジェクト作成 → テーマごとに整理", "Web検索 → 参考画像を追加", "ギャラリーから追加 → 端末内の資料をまとめる")),
            TutorialPage("資料の見返し", "必要な参考資料だけをまとめて確認できます。", Icons.Default.ImageSearch, success, listOf("詳細画面 → 資料一覧を確認", "不要な資料 → プロジェクト単位で整理", "作業中に見返す → サイドバーからすぐ移動"))
        )
        TutorialTarget.VIDEO_DOWNLOADER -> listOf(
            TutorialPage("動画ビューア", "動画画面では再生、前後移動、明るさ、音量、スクリーンショットをまとめて操作できます。", Icons.Default.VideoLibrary, accent, listOf("タップ → コントロールパネルを表示", "左右スワイプ → 前後の動画へ移動", "タッチインジケーターON → 画面割り当ての説明を表示")),
            TutorialPage("動画DL", "共有URLや入力URLから保存候補を取得します。", Icons.Default.VideoLibrary, success, listOf("URL入力後 → ダウンロード候補を表示", "GIF投稿 → GIF保存を優先", "履歴 → 保存済み候補を再確認")),
            TutorialPage("保存後の確認", "保存済み履歴からビューアで確認できます。", Icons.Default.VideoLibrary, accent, listOf("履歴サムネイルをタップ → 保存結果を確認", "重複時 → 再ダウンロード可能", "失敗時 → 候補を選び直す"))
        )
        TutorialTarget.TRASH -> listOf(
            TutorialPage("ゴミ箱", "削除済みの画像、動画、本を復元または完全削除できます。", Icons.Default.Delete, danger, listOf("右上のすべて復元 → 確認後にまとめて復元", "右上のすべて削除 → 確認後に完全削除", "本バッジ → ゴミ箱内の本を識別"))
        )
        TutorialTarget.SETTINGS -> listOf(
            TutorialPage("設定トップ", "全体設定、テーマ、ブックビューア、設定データに分けて調整します。", Icons.Default.Settings, soft, listOf("全体設定 → 起動、保護、操作、進捗表示", "テーマ → 表示テーマ、色、文字サイズ", "設定データ → まとめて保存/読み込み")),
            TutorialPage("テーマ編集", "プリセットを選んだあと、背景、文字、アクセントなどすべての色を編集できます。", Icons.Default.Settings, accent, listOf("テーマプリセット → 好みの雰囲気を適用", "各色の入力欄 → #RRGGBB / #AARRGGBBで指定", "文字サイズ → アプリ全体の直指定サイズをまとめて調整")),
            TutorialPage("ビューア設定", "長押し拡大鏡、タッチインジケーター、コントロールパネル、ページ効果を反映します。", Icons.Default.Settings, success, listOf("長押し拡大鏡 → 指を離すと元に戻る", "進捗表示 → 最大/最小/円を選択", "ブックビューア → 綴じ方向やページめくりを調整"))
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

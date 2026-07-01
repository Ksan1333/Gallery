package com.example.gallery.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.gallery.ui.AppRoutes
import com.example.gallery.ui.theme.GalleryThemeTokens

enum class TutorialTarget(val id: String, val label: String) {
    OVERVIEW("overview", "アプリ全体"),
    HOME("home", "ホーム / ギャラリー"),
    SEARCH("search", "検索"),
    FOLDERS("folders", "フォルダ"),
    BOOKS("books", "ブックビューア"),
    REFERENCES("references", "参照プロジェクト"),
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
    val pages = remember(target, colors) { tutorialPages(target, colors) }
    var page by remember(target) { mutableIntStateOf(0) }
    val current = pages[page]

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
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
            TutorialPage("Galleryへようこそ", "画像、GIF、動画、本、参照資料をまとめて管理できます。", Icons.Default.Home, accent, listOf("→ 左端スワイプでサイドバー", "→ 下部ナビで主要画面へ移動")),
            TutorialPage("作業の入口", "検索、設定、チュートリアルはサイドバーからいつでも開けます。", Icons.Default.Search, success, listOf("→ 検索は条件を重ねて絞り込み", "→ 設定は全体設定、テーマ、ブックビューアに分割"))
        )
        TutorialTarget.HOME -> listOf(
            TutorialPage("ホーム", "全メディアを一覧できます。", Icons.Default.Home, accent, listOf("→ 画像をタップでビューア", "→ 長押しで選択モード", "→ 左端から右へスワイプでサイドバー")),
            TutorialPage("メディアビューア", "タップで操作パネル、ダブルタップでズーム、上方向ドラッグで詳細を開けます。", Icons.Default.ImageSearch, accent, listOf("→ 三点ボタンからタグ編集や設定", "→ 動画/GIFはコマ送りやスクリーンショットに対応"))
        )
        TutorialTarget.SEARCH -> listOf(
            TutorialPage("検索", "タグ、フォルダ、年齢制限、メディア形式を組み合わせて検索できます。", Icons.Default.Search, accent, listOf("→ 条件を追加して結果を絞り込み", "→ 結果表示でホームへ反映")),
            TutorialPage("タグ検索", "AI解析済みのタグや手動タグを使って探せます。", Icons.Default.AutoAwesome, success, listOf("→ タグをタップして関連メディアへ", "→ メディア種別でGIF/動画だけに限定"))
        )
        TutorialTarget.FOLDERS -> listOf(
            TutorialPage("フォルダ", "フォルダ単位でメディアを整理できます。", Icons.Default.Folder, soft, listOf("→ フォルダをタップして中身へ", "→ 左上メニューでサイドバー", "→ 戻る先がなければホームへ戻ります")),
            TutorialPage("フォルダ内操作", "フォルダ内でもビューア、選択、タグ移動を使えます。", Icons.Default.Folder, accent, listOf("→ 長押しで複数選択", "→ 一括移動で整理"))
        )
        TutorialTarget.BOOKS -> listOf(
            TutorialPage("ブックビューア", "本形式のファイルを読み、表示設定を細かく調整できます。", Icons.AutoMirrored.Filled.MenuBook, danger, listOf("→ 本をタップして閲覧", "→ しおりから続きへ移動")),
            TutorialPage("読書設定", "綴じ方向、ページ効果、タップ領域、時計表示などを設定できます。", Icons.Default.Settings, soft, listOf("→ 設定 > ブックビューア", "→ タップ領域はページ送りやズームに割り当て"))
        )
        TutorialTarget.REFERENCES -> listOf(
            TutorialPage("参照プロジェクト", "イラスト用の参照資料を一時的に集められます。", Icons.Default.ImageSearch, accent, listOf("→ プロジェクトを作成", "→ Web検索やギャラリーから資料を追加")),
            TutorialPage("資料の使い方", "必要な参考資料だけをまとめて見返せます。", Icons.Default.ImageSearch, success, listOf("→ 詳細画面で一覧管理", "→ 不要な資料はプロジェクト単位で整理"))
        )
        TutorialTarget.VIDEO_DOWNLOADER -> listOf(
            TutorialPage("動画DL", "共有URLや入力URLから保存候補を取得します。", Icons.Default.VideoLibrary, success, listOf("→ URL入力後にダウンロード候補", "→ GIF投稿はGIF保存を優先")),
            TutorialPage("履歴と確認", "保存済み履歴からビューアで確認できます。", Icons.Default.VideoLibrary, accent, listOf("→ 履歴サムネイルをタップ", "→ 重複時も再ダウンロード可能"))
        )
        TutorialTarget.TRASH -> listOf(
            TutorialPage("ゴミ箱", "削除済みメディアの復元や完全削除ができます。", Icons.Default.Delete, danger, listOf("→ 復元で元の一覧へ", "→ 完全削除は取り消せません"))
        )
        TutorialTarget.SETTINGS -> listOf(
            TutorialPage("設定", "全体設定、テーマ、ブックビューアに分けて調整します。", Icons.Default.Settings, soft, listOf("→ 全体設定は動作と起動", "→ テーマは色と文字サイズ", "→ ブックビューアは読書設定")),
            TutorialPage("反映される設定", "操作、表示、パスワード、タッチインジケーターなどはビューアに反映されます。", Icons.Default.Settings, accent, listOf("→ コントロールパネル自動非表示時間", "→ 長押し拡大鏡", "→ 起動パスワード"))
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

package com.example.gallery.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import com.example.gallery.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.gallery.data.repository.MediaRepository
import com.example.gallery.ui.AppConstants
import com.example.gallery.service.GlobalOperationService
import com.example.gallery.service.TagTranslationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UnifiedMediaEditDialog(
    uris: List<String>,
    repository: MediaRepository,
    onDismiss: () -> Unit
) {
    var selectedAgeRating by rememberSaveable { mutableStateOf<String?>(null) }
    val tagCounts by repository.getAllTagsWithCounts().collectAsState(initial = emptyList())

    // タグ検索用の状態
    var tagSearchQuery by remember { mutableStateOf("") }
    val filteredTags = remember(tagCounts, tagSearchQuery) {
        tagCounts
            .filter { !it.tag.endsWith("系") }
            .filter { it.tag.contains(tagSearchQuery, ignoreCase = true) ||
                      TagTranslationService.translate(it.tag).contains(tagSearchQuery, ignoreCase = true) }
            .map { it.tag }
    }

    val selectedTags = remember { mutableStateListOf<String>() }
    var newTagName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // 進捗管理用の状態
    var isProcessing by remember { mutableStateOf(false) }

    // 初期値のロード
    LaunchedEffect(uris) {
        if (uris.size == 1) {
            val meta = repository.getMetadata(uris[0])
            selectedAgeRating = meta?.ageRating ?: "SFW"
        } else {
            // 複数選択時は「変更なし」をデフォルトにするため null
            if (selectedAgeRating == null) selectedAgeRating = null
        }
    }

    Scaffold(
        topBar = {
            GalleryTopAppBar(
                title = "一括タグ・評価編集 (${uris.size} 件)",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationContentDescription = "戻る",
                onNavigationClick = onDismiss,
                navigationEnabled = !isProcessing,
                centered = true,
                containerColor = Color.Black,
                contentColor = Color.White,
                disabledContentColor = Color.Gray,
                actions = {
                    TextButton(
                        onClick = {
                            isProcessing = true
                            scope.launch {
                                GlobalOperationService.startOperation("アイテムを更新中...")
                                val total = uris.size

                                uris.forEachIndexed { index, uri ->
                                    repository.bulkUpdateAgeRating(listOf(uri), selectedAgeRating)
                                    if (selectedTags.isNotEmpty()) {
                                        repository.bulkAddTags(listOf(uri), selectedTags.toList())
                                    }
                                    val currentProgress = (index + 1).toFloat() / total
                                    GlobalOperationService.updateProgress(currentProgress, "${index + 1} / $total")
                                    if (total > 50) delay(1)
                                }
                                GlobalOperationService.finishOperation()
                                isProcessing = false
                                onDismiss()
                            }
                        },
                        enabled = !isProcessing
                    ) {
                        Text(stringResource(R.string.btn_save), color = if (!isProcessing) Color.White else colorResource(R.color.gray))
                    }
                }
            )
        },
        containerColor = AppConstants.BackgroundColor,
        // ナビゲーションバーを考慮するために WindowInsets を設定
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {

            // 選択中の画像プレビュー
            if (!isProcessing && uris.isNotEmpty()) {
                Text(
                    text = "選択中のアイテム",
                    color = Color.White,
                    fontSize = com.example.gallery.ui.AppConstants.SmallFontSize,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 2つずつペアにして表示することで縦2段を実現
                    val pairs = uris.chunked(2)
                    items(pairs) { pair ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            pair.forEach { uri ->
                                AsyncImage(
                                    model = remember(uri) {
                                        ImageRequest.Builder(context)
                                            .data(uri)
                                            .videoFrameMillis(1000)
                                            .crossfade(true)
                                            .build()
                                    },
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(88.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            // 奇数個の場合に高さを合わせるための空き
                            if (pair.size < 2) {
                                Spacer(modifier = Modifier.size(88.dp))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // if (isProcessing) は削除。GlobalProgressOverlay が表示するため。

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                item {
                    Text(stringResource(R.string.edit_target_age), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color.White)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(null to "変更なし", "SFW" to "健全", "R15" to "R-15", "R18" to "R-18").forEach { (code, label) ->
                            if (uris.size > 1 || code != null) {
                                FilterChip(
                                    selected = selectedAgeRating == code,
                                    onClick = { if (!isProcessing) selectedAgeRating = code },
                                    label = { Text(label) },
                                    enabled = !isProcessing,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White,
                                        labelColor = Color.Gray
                                    )
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.edit_tag_settings), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color.White)

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = newTagName,
                            onValueChange = { newTagName = it },
                            placeholder = { Text(stringResource(R.string.edit_new_tag)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !isProcessing
                        )
                        IconButton(
                            onClick = {
                                if (newTagName.isNotBlank()) {
                                    if (!selectedTags.contains(newTagName)) selectedTags.add(newTagName)
                                    newTagName = ""
                                    keyboardController?.hide()
                                }
                            },
                            enabled = !isProcessing
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "追加", tint = if (!isProcessing) Color.White else colorResource(R.color.gray))
                        }
                    }

                    if (selectedTags.isNotEmpty()) {
                        Text(stringResource(R.string.edit_add_tags), fontSize = AppConstants.SmallFontSize, color = colorResource(R.color.gray))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            selectedTags.forEach { tag ->
                                InputChip(
                                    selected = true,
                                    onClick = { if (!isProcessing) selectedTags.remove(tag) },
                                    label = { Text(TagTranslationService.translate(tag)) },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    enabled = !isProcessing
                                )
                            }
                        }
                    }

                    if (filteredTags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.edit_select_existing_tags), fontSize = AppConstants.SmallFontSize, color = colorResource(R.color.gray), modifier = Modifier.weight(1f))
                            // タグ検索フィールド
                            OutlinedTextField(
                                value = tagSearchQuery,
                                onValueChange = { tagSearchQuery = it },
                                placeholder = { Text(stringResource(R.string.edit_search_tags), fontSize = AppConstants.TinyFontSize) },
                                modifier = Modifier.width(150.dp).height(40.dp),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = com.example.gallery.ui.AppConstants.SmallFontSize),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color.Cyan,
                                    unfocusedBorderColor = Color.DarkGray
                                )
                            )
                        }

                        // 高さを制限してスクロール可能にする
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .padding(vertical = 4.dp)
                                .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .verticalScroll(rememberScrollState())
                        ) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                filteredTags.forEach { tag ->
                                    FilterChip(
                                        selected = selectedTags.contains(tag),
                                        onClick = {
                                            if (!isProcessing) {
                                                if (selectedTags.contains(tag)) selectedTags.remove(tag)
                                                else selectedTags.add(tag)
                                            }
                                        },
                                        label = { Text(TagTranslationService.translate(tag), fontSize = com.example.gallery.ui.AppConstants.SmallFontSize) },
                                        enabled = !isProcessing,
                                        colors = FilterChipDefaults.filterChipColors(
                                            labelColor = if (!isProcessing) Color.White else Color.Gray,
                                            selectedLabelColor = Color.White,
                                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

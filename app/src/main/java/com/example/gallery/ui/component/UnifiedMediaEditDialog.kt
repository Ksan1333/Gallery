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
import com.example.gallery.ui.theme.GalleryThemeTokens
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
    val colors = GalleryThemeTokens.colors
    val textSizes = GalleryThemeTokens.textSizes
    var selectedAgeRating by rememberSaveable { mutableStateOf<String?>(null) }
    val tagCounts by repository.getAllTagsWithCounts().collectAsState(initial = emptyList())

    // タグ検索用の状態
    val tagSuffixGroup = stringResource(R.string.label_tag_suffix_group)
    var tagSearchQuery by remember { mutableStateOf("") }
    val filteredTags = remember(tagCounts, tagSearchQuery, tagSuffixGroup) {
        tagCounts
            .filter { !it.tag.endsWith(tagSuffixGroup) }
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
                title = stringResource(R.string.edit_bulk_title_format, uris.size),
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationContentDescription = stringResource(R.string.btn_back),
                onNavigationClick = onDismiss,
                navigationEnabled = !isProcessing,
                centered = true,
                containerColor = colors.topBar,
                contentColor = colors.primaryText,
                disabledContentColor = colors.disabled,
                actions = {
                    TextButton(
                        onClick = {
                            isProcessing = true
                            scope.launch {
                                GlobalOperationService.startOperation(context.getString(R.string.msg_updating_items))
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
                        Text(stringResource(R.string.btn_save), color = if (!isProcessing) colors.primaryText else colors.disabled)
                    }
                }
            )
        },
        containerColor = colors.background,
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
                    text = stringResource(R.string.edit_selected_items),
                    color = colors.primaryText,
                    fontSize = textSizes.small,
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
                    Text(stringResource(R.string.edit_target_age), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = colors.primaryText)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(null to stringResource(R.string.opt_no_change), "SFW" to stringResource(R.string.opt_age_sfw), "R15" to stringResource(R.string.opt_age_r15), "R18" to stringResource(R.string.opt_age_r18)).forEach { (code, label) ->
                            if (uris.size > 1 || code != null) {
                                FilterChip(
                                    selected = selectedAgeRating == code,
                                    onClick = { if (!isProcessing) selectedAgeRating = code },
                                    label = { Text(label) },
                                    enabled = !isProcessing,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.accent,
                                        selectedLabelColor = colors.background,
                                        labelColor = colors.mutedText
                                    )
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.edit_tag_settings), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = colors.primaryText)

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = newTagName,
                            onValueChange = { newTagName = it },
                            placeholder = { Text(stringResource(R.string.edit_new_tag)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !isProcessing,
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = colors.primaryText,
                                unfocusedTextColor = colors.primaryText,
                                focusedContainerColor = colors.field,
                                unfocusedContainerColor = colors.field,
                                disabledContainerColor = colors.field
                            )
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
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.btn_create), tint = if (!isProcessing) colors.primaryText else colors.disabled)
                        }
                    }

                    if (selectedTags.isNotEmpty()) {
                        Text(stringResource(R.string.edit_add_tags), fontSize = textSizes.small, color = colors.mutedText)
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
                                    enabled = !isProcessing,
                                    colors = InputChipDefaults.inputChipColors(
                                        selectedContainerColor = colors.accentSoft,
                                        selectedLabelColor = colors.accent,
                                        selectedTrailingIconColor = colors.accent
                                    )
                                )
                            }
                        }
                    }

                    if (filteredTags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.edit_select_existing_tags), fontSize = textSizes.small, color = colors.mutedText, modifier = Modifier.weight(1f))
                            // タグ検索フィールド
                            OutlinedTextField(
                                value = tagSearchQuery,
                                onValueChange = { tagSearchQuery = it },
                                placeholder = { Text(stringResource(R.string.edit_search_tags), fontSize = textSizes.tiny) },
                                modifier = Modifier.width(150.dp).height(40.dp),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = textSizes.small),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = colors.primaryText,
                                    unfocusedTextColor = colors.primaryText,
                                    focusedBorderColor = colors.accent,
                                    unfocusedBorderColor = colors.divider
                                )
                            )
                        }

                        // 高さを制限してスクロール可能にする
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .padding(vertical = 4.dp)
                                .background(colors.background.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
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
                                        label = { Text(TagTranslationService.translate(tag), fontSize = textSizes.small) },
                                        enabled = !isProcessing,
                                        colors = FilterChipDefaults.filterChipColors(
                                    labelColor = if (!isProcessing) colors.primaryText else colors.disabled,
                                    selectedLabelColor = colors.background,
                                    selectedContainerColor = colors.accent.copy(alpha = 0.7f)
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

package com.example.gallery.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.example.gallery.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.gallery.data.repository.MediaRepository
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.theme.GalleryThemeTokens
import com.example.gallery.ui.theme.GalleryAlphaTokens
import com.example.gallery.service.GlobalOperationService
import com.example.gallery.service.TagTranslationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var newTagName by remember { mutableStateOf("") }
    val visibleTags = remember(tagCounts, newTagName, tagSuffixGroup) {
        val query = newTagName.trim()
        val matchingTags = tagCounts.asSequence()
            .filter { !it.tag.endsWith(tagSuffixGroup) }
            .filter {
                query.isBlank() ||
                    TagTranslationService.matchesSearch(it.tag, query) ||
                    TagTranslationService.matchesSearch(TagTranslationService.translate(it.tag), query)
            }
            .map { it.tag }
        if (query.isBlank()) matchingTags.take(20).toList() else matchingTags.toList()
    }

    val selectedTags = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 進捗管理用の状態
    var isProcessing by remember { mutableStateOf(false) }

    // 初期値のロード
    LaunchedEffect(uris) {
        if (uris.size == 1) {
            val meta = repository.getMetadata(uris[0])
            selectedAgeRating = meta?.ageRating ?: AppConstants.RATING_SFW
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
                    FilledTonalButton(
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
                        enabled = !isProcessing,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_save))
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .imePadding(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!isProcessing && uris.isNotEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = colors.surface,
                            tonalElevation = 1.dp,
                            shadowElevation = 2.dp
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = stringResource(R.string.edit_selected_items),
                                    color = colors.primaryText,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(Modifier.height(10.dp))
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(dimensionResource(R.dimen.edit_dialog_preview_row_height)),
                                    horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_tiny))
                                ) {
                                    val pairs = uris.chunked(2)
                                    items(pairs) { pair ->
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(
                                                dimensionResource(R.dimen.spacing_tiny)
                                            )
                                        ) {
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
                                                        .size(dimensionResource(R.dimen.edit_dialog_thumbnail_size))
                                                        .clip(
                                                            RoundedCornerShape(
                                                                dimensionResource(R.dimen.radius_small)
                                                            )
                                                        ),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                            if (pair.size < 2) {
                                                Spacer(
                                                    modifier = Modifier.size(
                                                        dimensionResource(R.dimen.edit_dialog_thumbnail_size)
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

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = colors.surface,
                        tonalElevation = 1.dp,
                        shadowElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.edit_target_age),
                        color = colors.primaryText,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_small))
                    ) {
                        listOf(null to stringResource(R.string.opt_no_change), AppConstants.RATING_SFW to stringResource(R.string.opt_age_sfw), AppConstants.RATING_R15 to stringResource(R.string.opt_age_r15), AppConstants.RATING_R18 to stringResource(R.string.opt_age_r18)).forEach { (code, label) ->
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
                    }
                }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = colors.surface,
                        tonalElevation = 1.dp,
                        shadowElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.edit_tag_settings),
                        color = colors.primaryText,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = newTagName,
                            onValueChange = { newTagName = it },
                            placeholder = { Text(stringResource(R.string.edit_tag_input_placeholder)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            enabled = !isProcessing,
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = colors.primaryText,
                                unfocusedTextColor = colors.primaryText,
                                focusedContainerColor = colors.field,
                                unfocusedContainerColor = colors.field,
                                disabledContainerColor = colors.field
                            )
                        )
                        FilledIconButton(
                            onClick = {
                                if (newTagName.isNotBlank()) {
                                    if (!selectedTags.contains(newTagName)) selectedTags.add(newTagName)
                                    newTagName = ""
                                }
                            },
                            enabled = !isProcessing,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = colors.accent,
                                contentColor = colors.background,
                                disabledContainerColor = colors.field,
                                disabledContentColor = colors.disabled
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.btn_create))
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

                    if (visibleTags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.edit_select_existing_tags),
                            fontSize = textSizes.small,
                            color = colors.mutedText
                        )

                        // 高さを制限してスクロール可能にする
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = dimensionResource(R.dimen.edit_dialog_tag_list_max_height))
                                .padding(vertical = dimensionResource(R.dimen.spacing_tiny))
                                .background(colors.surfaceVariant.copy(alpha = GalleryAlphaTokens.SubtleBackground), RoundedCornerShape(12.dp))
                                .verticalScroll(rememberScrollState())
                        ) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth().padding(dimensionResource(R.dimen.spacing_small)),
                                horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_tiny))
                            ) {
                                visibleTags.forEach { tag ->
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
                                            selectedContainerColor = colors.accent.copy(alpha = GalleryAlphaTokens.Highlight)
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
    }
}

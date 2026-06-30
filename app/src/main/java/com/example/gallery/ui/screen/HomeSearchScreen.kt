package com.example.gallery.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.gallery.data.model.MediaData
import com.example.gallery.service.TagTranslationService
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.component.GalleryTopAppBar
import com.example.gallery.ui.search.filterGallerySearchResults
import com.example.gallery.ui.search.galleryStorageType
import com.example.gallery.ui.state.AgeRatingFilter
import com.example.gallery.ui.state.GallerySearchMatchMode
import com.example.gallery.ui.state.GallerySearchMediaType
import com.example.gallery.ui.state.GallerySearchStorageType
import com.example.gallery.ui.state.GalleryState
import com.example.gallery.ui.theme.GalleryThemeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class SearchTagOption(
    val tag: String,
    val count: Int
)

private object HomeSearchLabels {
    const val TITLE = "検索"
    const val QUERY_LABEL = "タグ名・フォルダ名・ファイル名"
    const val MATCH_MODE = "AND / OR"
    const val FAVORITES_ONLY = "お気に入りのみ"
    const val MEDIA_TYPE = "メディア形式"
    const val STORAGE = "保存先"
    const val AGE_RATING = "年齢制限"
    const val FOLDER = "フォルダ"
    const val TAGS = "タグ"
    const val NO_TAGS = "表示できるタグがありません"
    const val RESULT = "検索結果"
    const val CLEAR = "条件をクリア"
    const val SHOW_RESULTS = "検索"
    const val BACK = "戻る"
    const val ALL = "すべて"
}

@Composable
fun HomeSearchScreen(
    galleryState: GalleryState,
    onBack: () -> Unit,
    onShowResults: () -> Unit
) {
    val colors = GalleryThemeTokens.colors
    var allMedia by remember { mutableStateOf<List<MediaData>>(emptyList()) }

    var draftQuery by remember { mutableStateOf(galleryState.homeSearchQuery) }
    var draftMatchMode by remember { mutableStateOf(galleryState.homeSearchMatchMode) }
    var draftAgeRatings by remember { mutableStateOf(galleryState.homeSearchAgeRatings) }
    var draftFolders by remember { mutableStateOf(galleryState.homeSearchFolders) }
    var draftMediaTypes by remember { mutableStateOf(galleryState.homeSearchMediaTypes) }
    var draftStorageTypes by remember { mutableStateOf(galleryState.homeSearchStorageTypes) }
    var draftFavoritesOnly by remember { mutableStateOf(galleryState.homeSearchFavoritesOnly) }
    var draftTags by remember { mutableStateOf(galleryState.homeSearchTags) }

    fun clearDraft() {
        draftQuery = ""
        draftMatchMode = GallerySearchMatchMode.AND
        draftAgeRatings = emptySet()
        draftFolders = emptySet()
        draftMediaTypes = emptySet()
        draftStorageTypes = emptySet()
        draftFavoritesOnly = false
        draftTags = emptySet()
    }

    fun closeSearchToUnfilteredHome() {
        clearDraft()
        galleryState.clearHomeSearch()
        onBack()
    }

    fun applyDraftAndShowResults() {
        galleryState.homeSearchQuery = draftQuery
        galleryState.homeSearchMatchMode = draftMatchMode
        galleryState.homeSearchAgeRatings = draftAgeRatings
        galleryState.homeSearchFolders = draftFolders
        galleryState.homeSearchMediaTypes = draftMediaTypes
        galleryState.homeSearchStorageTypes = draftStorageTypes
        galleryState.homeSearchFavoritesOnly = draftFavoritesOnly
        galleryState.homeSearchTags = draftTags
        onShowResults()
    }

    BackHandler {
        closeSearchToUnfilteredHome()
    }

    LaunchedEffect(galleryState.refreshTrigger) {
        allMedia = withContext(Dispatchers.IO) {
            galleryState.repository.getAllMedia(forceRefresh = false)
        }
    }

    val allTagsData by galleryState.repository.getAllTagsWithUris().collectAsState(initial = emptyList())
    val metadataList by galleryState.repository.getAllMetadataSummaryFlow().collectAsState(initial = emptyList())
    val metadataMap = remember(metadataList) { metadataList.associateBy { it.uri } }
    val tagsByUri = remember(allTagsData) { allTagsData.groupBy({ it.uri }, { it.tag }) }
    val tagOptions = remember(allTagsData) {
        allTagsData
            .groupBy { it.tag }
            .mapNotNull { (tag, entries) ->
                if (tag.endsWith("系")) {
                    null
                } else {
                    SearchTagOption(
                        tag = tag,
                        count = entries.map { it.uri }.distinct().size
                    )
                }
            }
            .sortedWith(
                compareByDescending<SearchTagOption> { it.count }
                    .thenBy { TagTranslationService.translate(it.tag) }
                    .thenBy { it.tag }
            )
    }
    val folderOptions = remember(allMedia) {
        allMedia
            .map { it.folderName }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    val storageOptions = remember(allMedia) {
        GallerySearchStorageType.entries.map { type ->
            type to allMedia.count { item -> item.galleryStorageType() == type }
        }
    }
    val visibleTags = remember(tagOptions, draftQuery, draftTags) {
        val lowerQuery = draftQuery.trim().lowercase()
        val filteredTags = if (lowerQuery.isBlank()) {
            tagOptions
        } else {
            tagOptions.filter { option ->
                option.tag.lowercase().contains(lowerQuery) ||
                    TagTranslationService.translate(option.tag).lowercase().contains(lowerQuery)
            }
        }
        val optionByTag = tagOptions.associateBy { it.tag }
        (draftTags.map { optionByTag[it] ?: SearchTagOption(it, 0) } + filteredTags)
            .distinctBy { it.tag }
    }
    val resultCount = remember(
        allMedia,
        metadataMap,
        tagsByUri,
        draftQuery,
        draftTags,
        draftMatchMode,
        draftMediaTypes,
        draftFolders,
        draftStorageTypes,
        draftAgeRatings,
        draftFavoritesOnly
    ) {
        filterGallerySearchResults(
            mediaItems = allMedia,
            metadataByUri = metadataMap,
            tagsByUri = tagsByUri,
            query = draftQuery,
            selectedTags = draftTags,
            matchMode = draftMatchMode,
            selectedMediaTypes = draftMediaTypes,
            selectedFolders = draftFolders,
            selectedStorageTypes = draftStorageTypes,
            selectedAgeRatings = draftAgeRatings,
            favoritesOnly = draftFavoritesOnly
        ).size
    }

    Scaffold(
        topBar = {
            GalleryTopAppBar(
                title = HomeSearchLabels.TITLE,
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationContentDescription = HomeSearchLabels.BACK,
                onNavigationClick = ::closeSearchToUnfilteredHome,
                containerColor = colors.topBar,
                contentColor = colors.primaryText,
                actions = {
                    IconButton(onClick = ::closeSearchToUnfilteredHome) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = HomeSearchLabels.CLEAR,
                            tint = colors.primaryText
                        )
                    }
                }
            )
        },
        bottomBar = {
            SearchBottomBar(
                resultCount = resultCount,
                onClear = ::clearDraft,
                onShowResults = ::applyDraftAndShowResults
            )
        },
        containerColor = colors.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = draftQuery,
                    onValueChange = { draftQuery = it },
                    label = { Text(HomeSearchLabels.QUERY_LABEL) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (draftQuery.isNotBlank()) {
                            IconButton(onClick = { draftQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = HomeSearchLabels.CLEAR)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                SearchSection(title = HomeSearchLabels.MATCH_MODE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = draftMatchMode == GallerySearchMatchMode.AND,
                            onClick = { draftMatchMode = GallerySearchMatchMode.AND },
                            label = { Text("AND") }
                        )
                        FilterChip(
                            selected = draftMatchMode == GallerySearchMatchMode.OR,
                            onClick = { draftMatchMode = GallerySearchMatchMode.OR },
                            label = { Text("OR") }
                        )
                    }
                }
            }

            item {
                SearchSection(title = HomeSearchLabels.FAVORITES_ONLY) {
                    OutlinedButton(
                        onClick = { draftFavoritesOnly = !draftFavoritesOnly },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            if (draftFavoritesOnly) {
                                Icons.Default.Favorite
                            } else {
                                Icons.Default.FavoriteBorder
                            },
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(HomeSearchLabels.FAVORITES_ONLY)
                    }
                }
            }

            item {
                SearchSection(title = HomeSearchLabels.MEDIA_TYPE) {
                    ThreeColumnGrid(maxHeight = 72) {
                        items(GallerySearchMediaType.entries, key = { it.name }) { type ->
                            SelectableGridCell(
                                label = type.searchLabel,
                                selected = type in draftMediaTypes,
                                onClick = { draftMediaTypes = draftMediaTypes.toggle(type) }
                            )
                        }
                    }
                }
            }

            item {
                SearchSection(title = HomeSearchLabels.STORAGE) {
                    ThreeColumnGrid(maxHeight = 72) {
                        item(key = "__all_storage") {
                            SelectableGridCell(
                                label = HomeSearchLabels.ALL,
                                selected = draftStorageTypes.isEmpty(),
                                onClick = { draftStorageTypes = emptySet() }
                            )
                        }
                        items(storageOptions, key = { it.first.name }) { (type, count) ->
                            SelectableGridCell(
                                label = type.searchLabel,
                                subLabel = "${count}件",
                                selected = type in draftStorageTypes,
                                onClick = { draftStorageTypes = draftStorageTypes.toggle(type) }
                            )
                        }
                    }
                }
            }

            item {
                SearchSection(title = HomeSearchLabels.AGE_RATING) {
                    ThreeColumnGrid(maxHeight = 72) {
                        items(searchAgeRatings, key = { it.name }) { rating ->
                            SelectableGridCell(
                                label = rating.searchLabel,
                                selected = rating in draftAgeRatings,
                                onClick = { draftAgeRatings = draftAgeRatings.toggle(rating) }
                            )
                        }
                    }
                }
            }

            item {
                SearchSection(title = HomeSearchLabels.FOLDER) {
                    ThreeColumnGrid(maxHeight = 156) {
                        item(key = "__all_folders") {
                            SelectableGridCell(
                                label = HomeSearchLabels.ALL,
                                selected = draftFolders.isEmpty(),
                                onClick = { draftFolders = emptySet() }
                            )
                        }
                        items(folderOptions, key = { it }) { folder ->
                            SelectableGridCell(
                                label = folder,
                                selected = folder in draftFolders,
                                onClick = { draftFolders = draftFolders.toggle(folder) }
                            )
                        }
                    }
                }
            }

            item {
                SearchSection(title = HomeSearchLabels.TAGS) {
                    if (visibleTags.isEmpty()) {
                        Text(
                            text = HomeSearchLabels.NO_TAGS,
                            color = colors.secondaryText,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        ThreeColumnGrid(maxHeight = 360) {
                            items(visibleTags, key = { it.tag }) { option ->
                                val tag = option.tag
                                val selected = tag in draftTags
                                val translated = TagTranslationService.translate(tag)
                                SelectableGridCell(
                                    label = if (translated == tag) tag else translated,
                                    subLabel = if (translated == tag) {
                                        "${option.count}件"
                                    } else {
                                        "$tag / ${option.count}件"
                                    },
                                    selected = selected,
                                    onClick = { draftTags = draftTags.toggle(tag) }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SearchSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = GalleryThemeTokens.colors
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, color = colors.primaryText, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ThreeColumnGrid(
    maxHeight: Int,
    content: LazyGridScope.() -> Unit
) {
    val gridState = rememberLazyGridState()
    var isNestedDragActive by remember { mutableStateOf(false) }
    var consumeParentRemainder by remember { mutableStateOf(false) }
    val nestedScrollConnection = remember(gridState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && !isNestedDragActive) {
                    isNestedDragActive = true
                    val startedAtTop = gridState.firstVisibleItemIndex == 0 &&
                        gridState.firstVisibleItemScrollOffset == 0
                    consumeParentRemainder = !startedAtTop
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                return if (source == NestedScrollSource.UserInput && consumeParentRemainder) {
                    available
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val consumed = if (consumeParentRemainder) available else Velocity.Zero
                isNestedDragActive = false
                consumeParentRemainder = false
                return consumed
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val consumedVelocity = if (consumeParentRemainder) available else Velocity.Zero
                isNestedDragActive = false
                consumeParentRemainder = false
                return consumedVelocity
            }
        }
    }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                    isNestedDragActive = false
                    consumeParentRemainder = false
                }
            }
            .nestedScroll(nestedScrollConnection),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun SelectableGridCell(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    subLabel: String? = null
) {
    val colors = GalleryThemeTokens.colors
    Surface(
        color = if (selected) colors.accentSoft else colors.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (selected) colors.accent else colors.divider),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                color = if (selected) colors.accent else colors.primaryText,
                fontSize = AppConstants.SmallFontSize,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    color = colors.secondaryText,
                    fontSize = AppConstants.TinyFontSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SearchBottomBar(
    resultCount: Int,
    onClear: () -> Unit,
    onShowResults: () -> Unit
) {
    val colors = GalleryThemeTokens.colors
    Surface(
        color = colors.surface,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, colors.divider)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${HomeSearchLabels.RESULT}: ${resultCount}件",
                color = colors.accent,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(HomeSearchLabels.CLEAR)
                }
                Button(
                    onClick = onShowResults,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(HomeSearchLabels.SHOW_RESULTS)
                }
            }
        }
    }
}

private val GallerySearchMediaType.searchLabel: String
    get() = when (this) {
        GallerySearchMediaType.IMAGE -> "画像"
        GallerySearchMediaType.GIF -> "GIF"
        GallerySearchMediaType.VIDEO -> "動画"
    }

private val GallerySearchStorageType.searchLabel: String
    get() = when (this) {
        GallerySearchStorageType.INTERNAL -> "内部ストレージ"
        GallerySearchStorageType.SD_CARD -> "SDカード"
    }

private val AgeRatingFilter.searchLabel: String
    get() = when (this) {
        AgeRatingFilter.SFW -> "SFW"
        AgeRatingFilter.R15 -> "R-15"
        AgeRatingFilter.R18 -> "R-18"
        AgeRatingFilter.ALL -> "すべて"
    }

private val searchAgeRatings = listOf(
    AgeRatingFilter.SFW,
    AgeRatingFilter.R15,
    AgeRatingFilter.R18
)

private fun <T> Set<T>.toggle(value: T): Set<T> =
    if (value in this) this - value else this + value

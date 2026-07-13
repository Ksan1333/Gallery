package com.example.gallery.ui.screen

import android.content.Context
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.gallery.R
import com.example.gallery.ui.AppConstants
import com.example.gallery.data.model.MediaData
import com.example.gallery.service.TagTranslationService
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
import org.json.JSONArray
import org.json.JSONObject

private const val SEARCH_HISTORY_PREFS = "global_settings"
private const val SEARCH_HISTORY_KEY = "homeSearchHistory"
private const val SEARCH_HISTORY_LIMIT_KEY = "searchHistoryLimit"
private const val DEFAULT_SEARCH_HISTORY_LIMIT = 5

private data class SearchTagOption(
    val tag: String,
    val count: Int
)

private data class SearchHistoryEntry(
    val query: String,
    val matchMode: GallerySearchMatchMode,
    val ageRatings: Set<AgeRatingFilter>,
    val folders: Set<String>,
    val mediaTypes: Set<GallerySearchMediaType>,
    val storageTypes: Set<GallerySearchStorageType>,
    val favoritesOnly: Boolean,
    val tags: Set<String>
) {
    val isMeaningful: Boolean
        get() = query.isNotBlank() ||
            ageRatings.isNotEmpty() ||
            folders.isNotEmpty() ||
            mediaTypes.isNotEmpty() ||
            storageTypes.isNotEmpty() ||
            favoritesOnly ||
            tags.isNotEmpty()

    fun summary(context: Context): String {
        val parts = mutableListOf<String>()
        if (query.isNotBlank()) parts += query
        if (tags.isNotEmpty()) parts += tags.take(2).joinToString(prefix = "#", separator = " #")
        if (folders.isNotEmpty()) parts += folders.take(1).joinToString()
        if (mediaTypes.isNotEmpty()) parts += mediaTypes.joinToString("/") { it.searchLabel(context) }
        if (ageRatings.isNotEmpty()) parts += ageRatings.joinToString("/") { it.searchLabel(context) }
        if (storageTypes.isNotEmpty()) parts += storageTypes.joinToString("/") { it.searchLabel(context) }
        if (favoritesOnly) parts += context.getString(R.string.label_favorites)
        return parts.take(4).joinToString(" / ").ifBlank { context.getString(R.string.label_search_history_default) }
    }
}

private fun SearchHistoryEntry.toJson(): JSONObject = JSONObject().apply {
    put("query", query)
    put("matchMode", matchMode.name)
    put("ageRatings", JSONArray(ageRatings.map { it.name }))
    put("folders", JSONArray(folders.toList()))
    put("mediaTypes", JSONArray(mediaTypes.map { it.name }))
    put("storageTypes", JSONArray(storageTypes.map { it.name }))
    put("favoritesOnly", favoritesOnly)
    put("tags", JSONArray(tags.toList()))
}

private inline fun <reified T : Enum<T>> enumSetFromJson(json: JSONArray?): Set<T> {
    if (json == null) return emptySet()
    return buildSet {
        for (i in 0 until json.length()) {
            val value = json.optString(i)
            enumValues<T>().firstOrNull { it.name == value }?.let(::add)
        }
    }
}

private fun stringSetFromJson(json: JSONArray?): Set<String> {
    if (json == null) return emptySet()
    return buildSet {
        for (i in 0 until json.length()) {
            json.optString(i).takeIf(String::isNotBlank)?.let(::add)
        }
    }
}

private fun loadSearchHistory(context: Context): List<SearchHistoryEntry> {
    val prefs = context.getSharedPreferences(SEARCH_HISTORY_PREFS, Context.MODE_PRIVATE)
    val raw = prefs.getString(SEARCH_HISTORY_KEY, null) ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    SearchHistoryEntry(
                        query = item.optString("query"),
                        matchMode = runCatching {
                            GallerySearchMatchMode.valueOf(item.optString("matchMode"))
                        }.getOrDefault(GallerySearchMatchMode.AND),
                        ageRatings = enumSetFromJson(item.optJSONArray("ageRatings")),
                        folders = stringSetFromJson(item.optJSONArray("folders")),
                        mediaTypes = enumSetFromJson(item.optJSONArray("mediaTypes")),
                        storageTypes = enumSetFromJson(item.optJSONArray("storageTypes")),
                        favoritesOnly = item.optBoolean("favoritesOnly", false),
                        tags = stringSetFromJson(item.optJSONArray("tags"))
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun saveSearchHistory(
    context: Context,
    nextEntry: SearchHistoryEntry,
    limit: Int
): List<SearchHistoryEntry> {
    if (!nextEntry.isMeaningful) return loadSearchHistory(context)
    val prefs = context.getSharedPreferences(SEARCH_HISTORY_PREFS, Context.MODE_PRIVATE)
    val next = (listOf(nextEntry) + loadSearchHistory(context))
        .distinctBy { it.toJson().toString() }
        .take(limit.coerceIn(1, 10))
    val array = JSONArray()
    next.forEach { array.put(it.toJson()) }
    prefs.edit().putString(SEARCH_HISTORY_KEY, array.toString()).apply()
    return next
}

@Composable
fun HomeSearchScreen(
    galleryState: GalleryState,
    onBack: () -> Unit,
    onShowResults: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(SEARCH_HISTORY_PREFS, Context.MODE_PRIVATE) }
    val historyLimit = remember {
        prefs.getInt(SEARCH_HISTORY_LIMIT_KEY, DEFAULT_SEARCH_HISTORY_LIMIT).coerceIn(1, 10)
    }
    val colors = GalleryThemeTokens.colors
    var allMedia by remember { mutableStateOf<List<MediaData>>(emptyList()) }
    var searchHistory by remember { mutableStateOf(loadSearchHistory(context).take(historyLimit)) }

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
        searchHistory = saveSearchHistory(
            context = context,
            nextEntry = SearchHistoryEntry(
                query = draftQuery,
                matchMode = draftMatchMode,
                ageRatings = draftAgeRatings,
                folders = draftFolders,
                mediaTypes = draftMediaTypes,
                storageTypes = draftStorageTypes,
                favoritesOnly = draftFavoritesOnly,
                tags = draftTags
            ),
            limit = historyLimit
        )
        onShowResults()
    }

    fun restoreHistory(entry: SearchHistoryEntry) {
        draftQuery = entry.query
        draftMatchMode = entry.matchMode
        draftAgeRatings = entry.ageRatings
        draftFolders = entry.folders
        draftMediaTypes = entry.mediaTypes
        draftStorageTypes = entry.storageTypes
        draftFavoritesOnly = entry.favoritesOnly
        draftTags = entry.tags
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
                title = stringResource(R.string.search_title),
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationContentDescription = stringResource(R.string.btn_back),
                onNavigationClick = ::closeSearchToUnfilteredHome,
                containerColor = colors.topBar,
                contentColor = colors.primaryText,
                actions = {
                    IconButton(onClick = ::closeSearchToUnfilteredHome) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.search_clear),
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
                    label = { Text(stringResource(R.string.search_query_label)) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (draftQuery.isNotBlank()) {
                            IconButton(onClick = { draftQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.search_clear))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (searchHistory.isNotEmpty()) {
                item {
                    SearchSection(title = stringResource(R.string.search_history)) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            lazyItems(searchHistory) { entry ->
                                FilterChip(
                                    selected = false,
                                    onClick = { restoreHistory(entry) },
                                    label = {
                                        Text(
                                            text = entry.summary(context),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                SearchSection(title = stringResource(R.string.search_match_mode)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = draftMatchMode == GallerySearchMatchMode.AND,
                            onClick = { draftMatchMode = GallerySearchMatchMode.AND },
                            label = { Text(AppConstants.MATCH_AND) }
                        )
                        FilterChip(
                            selected = draftMatchMode == GallerySearchMatchMode.OR,
                            onClick = { draftMatchMode = GallerySearchMatchMode.OR },
                            label = { Text(AppConstants.MATCH_OR) }
                        )
                    }
                }
            }

            item {
                SearchSection(title = stringResource(R.string.search_favorites_only)) {
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
                        Text(stringResource(R.string.search_favorites_only))
                    }
                }
            }

            item {
                SearchSection(title = stringResource(R.string.search_media_type)) {
                    ThreeColumnGrid(maxHeight = 72) {
                        items(GallerySearchMediaType.entries, key = { it.name }) { type ->
                            SelectableGridCell(
                                label = type.searchLabel(context),
                                selected = type in draftMediaTypes,
                                onClick = { draftMediaTypes = draftMediaTypes.toggle(type) }
                            )
                        }
                    }
                }
            }

            item {
                SearchSection(title = stringResource(R.string.search_storage)) {
                    ThreeColumnGrid(maxHeight = 72) {
                        item(key = "__all_storage") {
                            SelectableGridCell(
                                label = stringResource(R.string.label_all_media),
                                selected = draftStorageTypes.isEmpty(),
                                onClick = { draftStorageTypes = emptySet() }
                            )
                        }
                        items(storageOptions, key = { it.first.name }) { (type, count) ->
                            SelectableGridCell(
                                label = type.searchLabel(context),
                                subLabel = stringResource(R.string.search_item_count_unit, count),
                                selected = type in draftStorageTypes,
                                onClick = { draftStorageTypes = draftStorageTypes.toggle(type) }
                            )
                        }
                    }
                }
            }

            item {
                SearchSection(title = stringResource(R.string.search_age_rating)) {
                    ThreeColumnGrid(maxHeight = 72) {
                        items(searchAgeRatings, key = { it.name }) { rating ->
                            SelectableGridCell(
                                label = rating.searchLabel(context),
                                selected = rating in draftAgeRatings,
                                onClick = { draftAgeRatings = draftAgeRatings.toggle(rating) }
                            )
                        }
                    }
                }
            }

            item {
                SearchSection(title = stringResource(R.string.search_folder)) {
                    ThreeColumnGrid(maxHeight = 156) {
                        item(key = "__all_folders") {
                            SelectableGridCell(
                                label = stringResource(R.string.label_all_media),
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
                SearchSection(title = stringResource(R.string.search_tags)) {
                    if (visibleTags.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_no_tags),
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
                                        stringResource(R.string.search_item_count_unit, option.count)
                                    } else {
                                        "$tag / ${stringResource(R.string.search_item_count_unit, option.count)}"
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
            Text(
                text = title,
                color = colors.primaryText,
                style = MaterialTheme.typography.titleLarge
            )
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
                style = MaterialTheme.typography.bodySmall,
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
                    style = MaterialTheme.typography.labelSmall,
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
                text = stringResource(R.string.search_result_count, resultCount),
                color = colors.accent,
                style = MaterialTheme.typography.titleLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.search_clear))
                }
                Button(
                    onClick = onShowResults,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.search_show_results))
                }
            }
        }
    }
}

private fun GallerySearchMediaType.searchLabel(context: Context): String
    = when (this) {
        GallerySearchMediaType.IMAGE -> context.getString(R.string.label_image)
        GallerySearchMediaType.GIF -> context.getString(R.string.label_gif)
        GallerySearchMediaType.VIDEO -> context.getString(R.string.label_video)
    }

private fun GallerySearchStorageType.searchLabel(context: Context): String
    = when (this) {
        GallerySearchStorageType.INTERNAL -> context.getString(R.string.label_storage_internal)
        GallerySearchStorageType.SD_CARD -> context.getString(R.string.label_storage_sdcard)
    }

private fun AgeRatingFilter.searchLabel(context: Context): String
    = when (this) {
        AgeRatingFilter.SFW -> context.getString(R.string.opt_age_sfw)
        AgeRatingFilter.R15 -> context.getString(R.string.opt_age_r15)
        AgeRatingFilter.R18 -> context.getString(R.string.opt_age_r18)
        AgeRatingFilter.ALL -> context.getString(R.string.label_search_all)
    }

private val searchAgeRatings = listOf(
    AgeRatingFilter.SFW,
    AgeRatingFilter.R15,
    AgeRatingFilter.R18
)

private fun <T> Set<T>.toggle(value: T): Set<T> =
    if (value in this) this - value else this + value

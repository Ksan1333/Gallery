package com.example.gallery.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.example.gallery.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import com.example.gallery.ui.component.GalleryTopAppBar
import com.example.gallery.ui.theme.GalleryColorTokens
import com.example.gallery.ui.theme.GalleryThemeTokens

private const val CREATOR_PREFS = "favorite_artists"
private const val CREATOR_LIST_KEY = "artists"
private const val CUSTOM_SITE_KEY = "custom_sites"

private val DEFAULT_PLATFORMS = listOf("X", "pixiv", "Support")

private data class FavoriteCreator(
    val name: String,
    val links: List<CreatorLink>
)

private data class CreatorLink(
    val platform: String,
    val url: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteArtistsScreen(
    onMenuClick: () -> Unit = {}
) {
    val colors = GalleryThemeTokens.colors
    GalleryThemeTokens.textSizes
    val creatorBackground = colors.background
    val creatorCard = colors.card
    val creatorInk = colors.primaryText
    val creatorMuted = colors.secondaryText
    val creatorAccent = colors.accent

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(CREATOR_PREFS, Context.MODE_PRIVATE) }
    var creators by remember { mutableStateOf(loadCreators(prefs).ifEmpty { emptyList() }) }
    var customSites by remember { mutableStateOf(loadCustomSites(prefs)) }
    var customSiteInput by remember { mutableStateOf("") }
    var pendingDeleteCreatorIndex by remember { mutableStateOf<Int?>(null) }
    var pendingSearch by remember { mutableStateOf<SearchTarget?>(null) }
    var activeWebSearch by remember { mutableStateOf<SearchTarget?>(null) }
    var isEditMode by remember { mutableStateOf(creators.isEmpty()) }
    val platforms = remember(customSites) { (DEFAULT_PLATFORMS + customSites).distinct() }

    fun persistCreators(next: List<FavoriteCreator>) {
        creators = next
        saveCreators(prefs, next.filter { creator ->
            creator.name.isNotBlank() || creator.links.any { it.url.isNotBlank() }
        })
    }

    fun persistCustomSites(next: List<String>) {
        customSites = next.map { it.trim() }.filter { it.isNotBlank() && it !in DEFAULT_PLATFORMS }.distinct()
        saveCustomSites(prefs, customSites)
    }

    Scaffold(
        topBar = {
            GalleryTopAppBar(
                title = stringResource(R.string.nav_fav_creators),
                navigationIcon = Icons.Default.Menu,
                navigationContentDescription = stringResource(R.string.drawer_menu_title),
                onNavigationClick = onMenuClick,
                centered = true,
                containerColor = creatorBackground,
                contentColor = creatorInk,
                actions = {
                    IconButton(onClick = { isEditMode = !isEditMode }) {
                        Icon(
                            if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = stringResource(if (isEditMode) R.string.fav_finish_editing else R.string.edit_tags),
                            tint = if (isEditMode) creatorAccent else creatorInk
                        )
                    }
                }
            )
        },
        containerColor = creatorBackground
    ) { padding ->
        if (isEditMode) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(creatorBackground),
                contentPadding = PaddingValues(dimensionResource(R.dimen.spacing_medium)),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_base))
            ) {
                itemsIndexed(creators, key = { index, _ -> index }) { creatorIndex, creator ->
                    CreatorEditCard(
                        creator = creator,
                        platforms = platforms,
                        onCreatorChange = { updated ->
                            persistCreators(creators.toMutableList().also { it[creatorIndex] = updated })
                        },
                        onDeleteCreator = {
                            pendingDeleteCreatorIndex = creatorIndex
                        },
                        onSearchRequested = { linkIndex, target ->
                            pendingSearch = SearchTarget(
                                creatorIndex = creatorIndex,
                                linkIndex = linkIndex,
                                platform = target.platform,
                                creatorName = target.creatorName,
                                initialUrl = target.initialUrl
                            )
                        }
                    )
                }

                item {
                    Button(
                        onClick = { persistCreators(creators + emptyCreator()) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = creatorAccent)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(dimensionResource(R.dimen.spacing_tiny)))
                        Text(stringResource(R.string.fav_add_creator_tab))
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = creatorCard),
                        shape = RoundedCornerShape(dimensionResource(R.dimen.radius_medium))
                    ) {
                        Column(
                            modifier = Modifier.padding(dimensionResource(R.dimen.spacing_base)),
                            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_small))
                        ) {
                            Text(stringResource(R.string.fav_user_sites), color = creatorInk, fontWeight = FontWeight.Bold)
                            customSites.forEach { site ->
                                Surface(
                                    color = colors.field,
                                    shape = RoundedCornerShape(dimensionResource(R.dimen.radius_small) + dimensionResource(R.dimen.radius_tiny)), // 6.dp
                                    border = BorderStroke(dimensionResource(R.dimen.spacing_hairline), colors.divider),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = dimensionResource(R.dimen.popup_padding_h), end = dimensionResource(R.dimen.spacing_micro), top = dimensionResource(R.dimen.spacing_tiny), bottom = dimensionResource(R.dimen.spacing_tiny)),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = site,
                                            color = creatorInk,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { persistCustomSites(customSites - site) }) {
                                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.btn_delete), tint = creatorMuted)
                                        }
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = customSiteInput,
                                    onValueChange = { customSiteInput = it },
                                    label = { Text(stringResource(R.string.fav_site_name)) },
                                    singleLine = true,
                                    colors = creatorTextFieldColors(),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    enabled = customSiteInput.isNotBlank(),
                                    onClick = {
                                        persistCustomSites(customSites + customSiteInput)
                                        customSiteInput = ""
                                    }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.fav_add_site_card), tint = creatorAccent)
                                }
                            }
                        }
                    }
                }
                item { FavoriteArtistsBackupHint() }
            }
        } else {
            CreatorDisplayScreen(
                creators = creators,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(creatorBackground)
            )
        }
    }

    pendingDeleteCreatorIndex?.let { index ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.dialog_delete_creator_title),
            text = stringResource(R.string.dialog_delete_creator_text),
            onConfirm = {
                val next = creators.toMutableList().also { it.removeAt(index) }
                persistCreators(next.ifEmpty { listOf(emptyCreator()) })
                pendingDeleteCreatorIndex = null
            },
            onDismiss = { pendingDeleteCreatorIndex = null }
        )
    }

    pendingSearch?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingSearch = null },
            containerColor = creatorCard,
            title = { Text(stringResource(R.string.fav_search_google), color = creatorInk) },
            text = {
                Text(
                    stringResource(R.string.fav_creator_search_desc),
                    color = creatorMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        activeWebSearch = target
                        pendingSearch = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = creatorAccent)
                ) { Text(stringResource(R.string.btn_search_open)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingSearch = null }) { Text(stringResource(R.string.btn_cancel), color = creatorMuted) }
            }
        )
    }

    activeWebSearch?.let { target ->
        CreatorSearchDialog(
            request = target,
            onDismiss = { activeWebSearch = null },
            onUrlPicked = { pickedUrl ->
                val updatedCreators = creators.toMutableList()
                val creator = updatedCreators.getOrNull(target.creatorIndex)
                val links = creator?.links?.toMutableList()
                if (creator != null && links != null && target.linkIndex in links.indices) {
                    links[target.linkIndex] = links[target.linkIndex].copy(url = pickedUrl)
                    updatedCreators[target.creatorIndex] = creator.copy(links = links)
                    persistCreators(updatedCreators)
                }
                activeWebSearch = null
            }
        )
    }
}

@Composable
private fun CreatorDisplayScreen(
    creators: List<FavoriteCreator>,
    modifier: Modifier = Modifier
) {
    val colors = GalleryThemeTokens.colors
    val textSizes = GalleryThemeTokens.textSizes
    val creatorCard = colors.card
    val creatorInk = colors.primaryText
    val creatorAccent = colors.accent
    val creatorField = colors.field
    val creatorMuted = colors.secondaryText

    val context = LocalContext.current
    val visibleCreators = creators.filter { it.name.isNotBlank() || it.links.any { link -> link.url.isNotBlank() } }
    if (visibleCreators.isEmpty()) {
        Column(
            modifier = modifier.padding(dimensionResource(R.dimen.spacing_medium))
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.fav_no_creators), color = creatorMuted)
            }
            FavoriteArtistsBackupHint()
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(dimensionResource(R.dimen.spacing_medium)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_base))
        ) {
            itemsIndexed(visibleCreators) { _, creator ->
            Card(
                colors = CardDefaults.cardColors(containerColor = creatorCard),
                shape = RoundedCornerShape(dimensionResource(R.dimen.radius_medium))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dimensionResource(R.dimen.spacing_base)),
                    verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.popup_padding_h))
                ) {
                    Text(
                        text = creator.name.ifBlank { stringResource(R.string.fav_untitled_creator) },
                        color = creatorInk,
                        fontWeight = FontWeight.Bold,
                        fontSize = textSizes.header,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    creator.links.filter { it.url.isNotBlank() }.forEach { link ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openUrl(context, link.url) },
                            color = creatorField,
                            shape = RoundedCornerShape(dimensionResource(R.dimen.radius_medium)),
                            border = BorderStroke(dimensionResource(R.dimen.spacing_hairline), GalleryThemeTokens.colors.divider)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.spacing_base), vertical = dimensionResource(R.dimen.popup_padding_h)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = link.platform,
                                    color = creatorAccent,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(dimensionResource(R.dimen.grid_bottom_padding) * 0.8f), // 80.dp
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = link.url,
                                    color = creatorMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = creatorAccent)
                            }
                        }
                    }
                }
            }
            }
            item { FavoriteArtistsBackupHint() }
        }
    }
}

@Composable
private fun FavoriteArtistsBackupHint() {
    Text(
        text = stringResource(R.string.fav_backup_settings_hint),
        color = GalleryThemeTokens.colors.secondaryText,
        fontSize = GalleryThemeTokens.textSizes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimensionResource(R.dimen.spacing_medium))
    )
}

@Composable
private fun CreatorEditCard(
    creator: FavoriteCreator,
    platforms: List<String>,
    onCreatorChange: (FavoriteCreator) -> Unit,
    onDeleteCreator: () -> Unit,
    onSearchRequested: (Int, SearchTarget) -> Unit
) {
    val colors = GalleryThemeTokens.colors
    GalleryThemeTokens.textSizes
    val creatorCard = colors.card
    val creatorInk = colors.primaryText
    val creatorMuted = colors.secondaryText

    val context = LocalContext.current
    var pendingDeleteLinkIndex by remember { mutableStateOf<Int?>(null) }
    Card(
        colors = CardDefaults.cardColors(containerColor = creatorCard),
        shape = RoundedCornerShape(dimensionResource(R.dimen.radius_medium))
    ) {
        Column(
            modifier = Modifier.padding(dimensionResource(R.dimen.spacing_base)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.popup_padding_h))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.fav_creator_tab), color = creatorInk, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDeleteCreator) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.btn_delete), tint = creatorMuted)
                }
            }

            OutlinedTextField(
                value = creator.name,
                onValueChange = { onCreatorChange(creator.copy(name = it)) },
                label = { Text(stringResource(R.string.fav_creator_name)) },
                singleLine = true,
                colors = creatorTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            creator.links.forEachIndexed { linkIndex, link ->
                CreatorLinkEditor(
                    platforms = platforms,
                    link = link,
                    onLinkChange = { updated ->
                        onCreatorChange(creator.copy(links = creator.links.toMutableList().also { it[linkIndex] = updated }))
                    },
                    onDelete = {
                        pendingDeleteLinkIndex = linkIndex
                    },
                    onAccess = {
                        if (link.url.isBlank()) {
                            onSearchRequested(
                                linkIndex,
                                SearchTarget(
                                    creatorIndex = -1,
                                    linkIndex = linkIndex,
                                    platform = link.platform,
                                    creatorName = creator.name,
                                    initialUrl = buildPlatformSearchUrl(link.platform, creator.name)
                                )
                            )
                        } else {
                            openUrl(context, link.url)
                        }
                    }
                )
            }

            TextButton(
                onClick = { onCreatorChange(creator.copy(links = creator.links + emptyLink(platforms))) }
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(dimensionResource(R.dimen.spacing_micro) + dimensionResource(R.dimen.spacing_micro) * 2)) // 6.dp
                Text(stringResource(R.string.fav_add_link))
            }
        }
    }

    pendingDeleteLinkIndex?.let { index ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.dialog_delete_link_title),
            text = stringResource(R.string.dialog_delete_link_text),
            onConfirm = {
                val next = creator.links.toMutableList().also { it.removeAt(index) }
                onCreatorChange(creator.copy(links = next.ifEmpty { listOf(emptyLink(platforms)) }))
                pendingDeleteLinkIndex = null
            },
            onDismiss = { pendingDeleteLinkIndex = null }
        )
    }
}

@Composable
private fun CreatorLinkEditor(
    platforms: List<String>,
    link: CreatorLink,
    onLinkChange: (CreatorLink) -> Unit,
    onDelete: () -> Unit,
    onAccess: () -> Unit
) {
    val colors = GalleryThemeTokens.colors
    val creatorAccent = colors.accent
    val creatorMuted = colors.secondaryText

    Row(verticalAlignment = Alignment.CenterVertically) {
        PlatformDropdown(
            platforms = platforms,
            selectedPlatform = link.platform.ifBlank { platforms.firstOrNull().orEmpty() },
            onPlatformSelected = { onLinkChange(link.copy(platform = it)) },
            modifier = Modifier.width(dimensionResource(R.dimen.scrollbar_label_offset) * 4.35f) // ~122.dp
        )
        Spacer(Modifier.width(dimensionResource(R.dimen.spacing_small)))
        OutlinedTextField(
            value = link.url,
            onValueChange = { onLinkChange(link.copy(url = it)) },
            label = { Text(stringResource(R.string.fav_url)) },
            singleLine = true,
            colors = creatorTextFieldColors(),
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onAccess) {
            Icon(Icons.Default.OpenInBrowser, contentDescription = stringResource(R.string.btn_open), tint = creatorAccent)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.btn_delete), tint = creatorMuted)
        }
    }
}

@Composable
private fun PlatformDropdown(
    platforms: List<String>,
    selectedPlatform: String,
    onPlatformSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = GalleryThemeTokens.colors
    val textSizes = GalleryThemeTokens.textSizes
    val creatorField = colors.field
    val creatorInk = colors.primaryText
    val creatorAccent = colors.accent

    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            color = creatorField,
            shape = RoundedCornerShape(dimensionResource(R.dimen.radius_small) + dimensionResource(R.dimen.radius_tiny)), // 6.dp
            border = BorderStroke(dimensionResource(R.dimen.spacing_hairline), colors.divider)
        ) {
            Row(
                modifier = Modifier.padding(start = dimensionResource(R.dimen.popup_padding_h), end = dimensionResource(R.dimen.spacing_tiny), top = dimensionResource(R.dimen.spacing_medium), bottom = dimensionResource(R.dimen.spacing_medium)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedPlatform,
                    color = creatorInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    fontSize = textSizes.scrollbarLabel
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = creatorAccent)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            platforms.forEach { platform ->
                DropdownMenuItem(
                    text = { Text(platform) },
                    onClick = {
                        onPlatformSelected(platform)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun creatorTextFieldColors(): TextFieldColors {
    val colors = GalleryThemeTokens.colors
    val creatorInk = colors.primaryText
    val creatorAccent = colors.accent
    val creatorMuted = colors.secondaryText
    val creatorField = colors.field

    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = creatorInk,
        unfocusedTextColor = creatorInk,
        focusedLabelColor = creatorAccent,
        unfocusedLabelColor = creatorMuted,
        cursorColor = creatorAccent,
        focusedBorderColor = creatorAccent,
        unfocusedBorderColor = colors.divider,
        focusedContainerColor = creatorField,
        unfocusedContainerColor = creatorField
    )
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = GalleryThemeTokens.colors
    val creatorCard = colors.card
    val creatorInk = colors.primaryText
    val creatorMuted = colors.secondaryText

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = creatorCard,
        title = { Text(title, color = creatorInk) },
        text = { Text(text, color = creatorMuted) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = colors.danger)
            ) { Text(stringResource(R.string.btn_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel), color = creatorMuted) }
        }
    )
}

@Composable
private fun CreatorSearchDialog(
    request: SearchTarget,
    onDismiss: () -> Unit,
    onUrlPicked: (String) -> Unit
) {
    val colors = GalleryThemeTokens.colors
    val creatorCard = colors.card
    val creatorInk = colors.primaryText
    val creatorMuted = colors.secondaryText
    var currentUrl by remember(request.initialUrl) { mutableStateOf(request.initialUrl) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val pickableUrl = extractCreatorPickedUrl(currentUrl)

    Dialog(
        onDismissRequest = {
            webView?.takeIf { it.canGoBack() }?.goBack() ?: onDismiss()
        }
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = creatorCard,
            shape = RoundedCornerShape(dimensionResource(R.dimen.radius_medium))
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dimensionResource(R.dimen.spacing_small)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.fav_creator_search_title, request.platform),
                        color = creatorInk,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_close), color = creatorMuted) }
                    TextButton(
                        enabled = pickableUrl != null,
                        onClick = { pickableUrl?.let(onUrlPicked) }
                    ) {
                        Text(stringResource(R.string.fav_use_this_url), color = if (pickableUrl != null) creatorInk else creatorMuted)
                    }
                }
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { context ->
                        WebView(context).apply {
                            webView = this
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    if (!url.isNullOrBlank()) currentUrl = url
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    webRequest: WebResourceRequest?
                                ): Boolean {
                                    if (webRequest?.isForMainFrame != true) return false
                                    currentUrl = webRequest.url.toString()
                                    return false
                                }
                            }
                            loadUrl(request.initialUrl)
                        }
                    }
                )
            }
        }
    }
}

private data class SearchTarget(
    val creatorIndex: Int,
    val linkIndex: Int,
    val platform: String,
    val creatorName: String,
    val initialUrl: String
)

private fun emptyCreator(): FavoriteCreator = FavoriteCreator("", listOf(CreatorLink("X", "")))

private fun emptyLink(platforms: List<String>): CreatorLink = CreatorLink(platforms.firstOrNull() ?: "X", "")

private fun buildPlatformSearchUrl(platform: String, creatorName: String): String {
    val encodedName = URLEncoder.encode(creatorName.trim(), "UTF-8")
    val encodedPlatform = URLEncoder.encode(platform, "UTF-8")
    return "https://www.google.com/search?q=$encodedName+$encodedPlatform"
}

private fun extractCreatorPickedUrl(rawUrl: String): String? {
    val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return null
    val host = uri.host.orEmpty().lowercase()
    if (host.contains("google.")) {
        if (uri.path == "/url") {
            val actualUrl = uri.getQueryParameter("url") ?: uri.getQueryParameter("q")
            return actualUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let(::normalizeFavoriteUrl)
        }
        return null
    }
    return rawUrl.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let(::normalizeFavoriteUrl)
}

private fun openUrl(context: Context, url: String) {
    val normalized = normalizeFavoriteUrl(url)
    if (normalized.isBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.msg_invalid_url, normalized), Toast.LENGTH_SHORT).show()
    }
}

private fun loadCreators(prefs: android.content.SharedPreferences): List<FavoriteCreator> {
    val raw = prefs.getString(CREATOR_LIST_KEY, "[]").orEmpty()
    return runCatching {
        val array = JSONArray(raw)
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val links = item.optJSONArray("links")?.let { linkArray ->
                List(linkArray.length()) { linkIndex ->
                    val link = linkArray.getJSONObject(linkIndex)
                    CreatorLink(
                        platform = link.optString("platform").trim(),
                        url = normalizeFavoriteUrl(link.optString("url"))
                    )
                }
            } ?: listOf(
                CreatorLink("Support", normalizeFavoriteUrl(item.optString("supportUrl"))),
                CreatorLink("pixiv", normalizeFavoriteUrl(item.optString("pixivUrl"))),
                CreatorLink("X", normalizeFavoriteUrl(item.optString("xUrl")))
            )

            FavoriteCreator(
                name = item.optString("name"),
                links = links.filter { it.platform.isNotBlank() }.ifEmpty { listOf(CreatorLink("X", "")) }
            )
        }
    }.getOrDefault(emptyList())
}

private fun saveCreators(prefs: android.content.SharedPreferences, creators: List<FavoriteCreator>) {
    val array = JSONArray()
    creators.forEach { creator ->
        val creatorName = creator.name.trim()
        val links = JSONArray()
        val seenLinks = mutableSetOf<String>()
        creator.links
            .map { it.copy(platform = it.platform.trim(), url = normalizeFavoriteUrl(it.url)) }
            .filter { it.platform.isNotBlank() && it.url.isNotBlank() }
            .filter { seenLinks.add("${it.platform.normalizedFavoriteKey()}|${it.url.normalizedFavoriteUrlKey()}") }
            .forEach { link ->
                links.put(
                    JSONObject()
                        .put("platform", link.platform)
                        .put("url", link.url)
                )
            }
        if (creatorName.isNotBlank() || links.length() > 0) {
            array.put(
                JSONObject()
                    .put("name", creatorName)
                    .put("links", links)
            )
        }
    }
    prefs.edit().putString(CREATOR_LIST_KEY, array.toString()).apply()
}

private fun normalizeFavoriteUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return ""
    return when {
        trimmed.startsWith("http://", ignoreCase = true) -> trimmed
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("//") -> "https:$trimmed"
        "://" in trimmed -> trimmed
        else -> "https://$trimmed"
    }
}

private fun String.normalizedFavoriteKey(): String = trim().lowercase()

private fun String.normalizedFavoriteUrlKey(): String {
    return normalizeFavoriteUrl(this)
        .lowercase()
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')
}

private fun loadCustomSites(prefs: android.content.SharedPreferences): List<String> {
    val raw = prefs.getString(CUSTOM_SITE_KEY, "[]").orEmpty()
    return runCatching {
        val array = JSONArray(raw)
        List(array.length()) { index -> array.optString(index) }
            .filter { it.isNotBlank() && it !in DEFAULT_PLATFORMS }
    }.getOrDefault(emptyList())
}

private fun saveCustomSites(prefs: android.content.SharedPreferences, sites: List<String>) {
    val array = JSONArray()
    sites.filter { it.isNotBlank() && it !in DEFAULT_PLATFORMS }.distinct().forEach { array.put(it) }
    prefs.edit().putString(CUSTOM_SITE_KEY, array.toString()).apply()
}

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import com.example.gallery.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import android.os.Environment
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.Scanner
import org.json.JSONArray
import org.json.JSONObject
import com.example.gallery.ui.component.GalleryTopAppBar
import com.example.gallery.ui.theme.GalleryColorTokens
import com.example.gallery.ui.theme.GalleryThemeTokens

private const val FAVORITE_SITES_PREFS = "favorite_sites_prefs"
private const val FAVORITE_SITES_KEY = "favorite_sites"

private data class FavoriteSite(
    val name: String = "",
    val url: String = "",
    val description: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteSitesScreen(
    onMenuClick: () -> Unit
) {
    val siteBackground = GalleryColorTokens.Dark.background
    val siteInk = GalleryColorTokens.Dark.primaryText
    val siteAccent = GalleryColorTokens.Dark.accent
    val siteCard = GalleryColorTokens.Dark.card
    val siteMuted = GalleryColorTokens.Dark.secondaryText

    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences(FAVORITE_SITES_PREFS, Context.MODE_PRIVATE)
    }
    var sites by remember {
        mutableStateOf(loadFavoriteSites(preferences).ifEmpty { emptyList() })
    }
    var isEditMode by remember { mutableStateOf(sites.isEmpty()) }
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }
    var pendingSearchIndex by remember { mutableStateOf<Int?>(null) }
    var activeSearchIndex by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    fun updateSites(next: List<FavoriteSite>) {
        sites = next
        saveFavoriteSites(
            preferences,
            sites.filter { it.name.isNotBlank() || it.url.isNotBlank() || it.description.isNotBlank() }
        )
    }

    fun getBackupFile(): File {
        val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val folder = File(baseDir, "Gallery/Backups")
        if (!folder.exists()) folder.mkdirs()
        return File(folder, "favorite_sites.json")
    }

    fun exportData() {
        if (sites.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.msg_no_data_backup), Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch(Dispatchers.IO) {
            runCatching {
                val file = getBackupFile()
                FileOutputStream(file).use { stream ->
                    val array = JSONArray()
                    sites.forEach { site ->
                        if (site.name.isNotBlank() || site.url.isNotBlank()) {
                            array.put(
                                JSONObject()
                                    .put("name", site.name)
                                    .put("url", site.url)
                                    .put("description", site.description)
                            )
                        }
                    }
                    stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                        writer.write(array.toString(2))
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.msg_saved_to, getBackupFile().absolutePath), Toast.LENGTH_LONG).show()
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.msg_save_failed, it.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun importData() {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val file = getBackupFile()
                if (!file.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.msg_backup_not_found), Toast.LENGTH_SHORT).show()
                    }
                    return@runCatching
                }
                file.inputStream().use { stream ->
                    val content = stream.bufferedReader(Charsets.UTF_8).readText()
                    val array = JSONArray(content)
                    val newSites = mutableListOf<FavoriteSite>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val url = obj.optString("url")
                        if (url.isBlank()) continue

                        newSites.add(
                            FavoriteSite(
                                name = obj.optString("name"),
                                url = url,
                                description = obj.optString("description")
                            )
                        )
                    }
                    if (newSites.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            val currentUrls = sites.map { it.url }.toSet()
                            val uniqueNewSites = newSites.filter { it.url !in currentUrls }
                            if (uniqueNewSites.isNotEmpty()) {
                                updateSites(sites + uniqueNewSites)
                            }
                            Toast.makeText(context, context.getString(R.string.msg_loaded), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.msg_no_new_sites), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.msg_load_failed, it.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            GalleryTopAppBar(
                title = stringResource(R.string.nav_fav_sites),
                navigationIcon = Icons.Default.Menu,
                navigationContentDescription = stringResource(R.string.drawer_menu_title),
                onNavigationClick = onMenuClick,
                centered = true,
                containerColor = siteBackground,
                contentColor = siteInk,
                actions = {
                    IconButton(onClick = { exportData() }) {
                        Icon(Icons.Default.Download, contentDescription = stringResource(R.string.btn_save), tint = siteInk)
                    }
                    IconButton(onClick = { importData() }) {
                        Icon(Icons.Default.Upload, contentDescription = stringResource(R.string.btn_load), tint = siteInk)
                    }
                    IconButton(onClick = { isEditMode = !isEditMode }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.edit_tags),
                            tint = if (isEditMode) siteAccent else siteInk
                        )
                    }
                }
            )
        },
        containerColor = siteBackground
    ) { padding ->
        if (isEditMode) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(siteBackground),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(sites, key = { index, _ -> index }) { index, site ->
                    FavoriteSiteEditCard(
                        site = site,
                        onChange = { updated ->
                            updateSites(sites.toMutableList().also { it[index] = updated })
                        },
                        onDelete = { pendingDeleteIndex = index },
                        onAccess = {
                            if (site.url.isBlank()) {
                                pendingSearchIndex = index
                            } else {
                                openFavoriteSite(context, site.url)
                            }
                        }
                    )
                }
                item {
                    Button(
                        onClick = { updateSites(sites + FavoriteSite()) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = siteAccent)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.fav_add_site_card))
                    }
                }
            }
        } else {
            FavoriteSitesDisplay(
                sites = sites,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }

    pendingDeleteIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { pendingDeleteIndex = null },
            containerColor = siteCard,
            title = { Text(stringResource(R.string.fav_delete_site_card), color = siteInk) },
            text = { Text(stringResource(R.string.fav_delete_site_confirm), color = siteMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        updateSites(sites.toMutableList().also { it.removeAt(index) })
                        pendingDeleteIndex = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GalleryThemeTokens.colors.danger)
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteIndex = null }) {
                    Text(stringResource(R.string.btn_cancel), color = siteMuted)
                }
            }
        )
    }

    pendingSearchIndex?.let { index ->
        val site = sites.getOrNull(index)
        if (site != null) {
            AlertDialog(
                onDismissRequest = { pendingSearchIndex = null },
                containerColor = siteCard,
                title = { Text(stringResource(R.string.fav_search_site_google), color = siteInk) },
                text = {
                    Text(
                        stringResource(R.string.fav_site_search_desc),
                        color = siteMuted
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            activeSearchIndex = index
                            pendingSearchIndex = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = siteAccent)
                    ) {
                        Text(stringResource(R.string.btn_search_open))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingSearchIndex = null }) {
                        Text(stringResource(R.string.btn_cancel), color = siteMuted)
                    }
                }
            )
        }
    }

    activeSearchIndex?.let { index ->
        val site = sites.getOrNull(index)
        if (site != null) {
            FavoriteSiteSearchDialog(
                siteName = site.name,
                onDismiss = { activeSearchIndex = null },
                onUrlPicked = { url ->
                    updateSites(sites.toMutableList().also { list ->
                        list[index] = list[index].copy(url = url)
                    })
                    activeSearchIndex = null
                }
            )
        }
    }
}

@Composable
private fun FavoriteSitesDisplay(
    sites: List<FavoriteSite>,
    modifier: Modifier = Modifier
) {
    val siteBackground = GalleryColorTokens.Dark.background
    val siteMuted = GalleryColorTokens.Dark.secondaryText
    val siteCard = GalleryColorTokens.Dark.card
    val siteInk = GalleryColorTokens.Dark.primaryText
    val siteAccent = GalleryColorTokens.Dark.accent
    val siteField = GalleryColorTokens.Dark.field

    val context = LocalContext.current
    val visibleSites = sites.filter {
        it.name.isNotBlank() || it.url.isNotBlank() || it.description.isNotBlank()
    }

    if (visibleSites.isEmpty()) {
        Box(modifier = modifier.background(siteBackground), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.fav_no_sites), color = siteMuted)
        }
        return
    }

    LazyColumn(
        modifier = modifier.background(siteBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(visibleSites, key = { index, site -> "${site.url}_$index" }) { _, site ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = site.url.isNotBlank()) { openFavoriteSite(context, site.url) },
                colors = CardDefaults.cardColors(containerColor = siteCard),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, GalleryThemeTokens.colors.divider)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = site.name.ifBlank { stringResource(R.string.fav_untitled_creator) },
                            color = siteInk,
                            fontSize = com.example.gallery.ui.AppConstants.HeaderFontSize,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (site.url.isNotBlank()) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = siteAccent)
                        }
                    }
                    if (site.description.isNotBlank()) {
                        Text(site.description, color = siteMuted, fontSize = com.example.gallery.ui.AppConstants.SubtitleFontSize)
                    }
                    if (site.url.isNotBlank()) {
                        Surface(color = siteField, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = site.url,
                                color = siteAccent,
                                fontSize = com.example.gallery.ui.AppConstants.SmallFontSize,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteSiteEditCard(
    site: FavoriteSite,
    onChange: (FavoriteSite) -> Unit,
    onDelete: () -> Unit,
    onAccess: () -> Unit
) {
    val siteCard = GalleryColorTokens.Dark.card
    val siteInk = GalleryColorTokens.Dark.primaryText
    val siteAccent = GalleryColorTokens.Dark.accent

    Card(
        colors = CardDefaults.cardColors(containerColor = siteCard),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, GalleryThemeTokens.colors.divider)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.fav_site_card), color = siteInk, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onAccess) {
                    Icon(
                        Icons.Default.OpenInBrowser,
                        contentDescription = stringResource(R.string.btn_open),
                        tint = siteAccent
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.btn_delete), tint = MaterialTheme.colorScheme.error)
                }
            }
            OutlinedTextField(
                value = site.name,
                onValueChange = { onChange(site.copy(name = it)) },
                label = { Text(stringResource(R.string.fav_site_name)) },
                singleLine = true,
                colors = favoriteSiteTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = site.url,
                onValueChange = { onChange(site.copy(url = it)) },
                label = { Text(stringResource(R.string.fav_url)) },
                singleLine = true,
                colors = favoriteSiteTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = site.description,
                onValueChange = { onChange(site.copy(description = it)) },
                label = { Text(stringResource(R.string.fav_description)) },
                minLines = 3,
                maxLines = 6,
                colors = favoriteSiteTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FavoriteSiteSearchDialog(
    siteName: String,
    onDismiss: () -> Unit,
    onUrlPicked: (String) -> Unit
) {
    val siteCard = GalleryColorTokens.Dark.card
    val siteInk = GalleryColorTokens.Dark.primaryText
    val siteMuted = GalleryColorTokens.Dark.secondaryText

    val defaultLabel = stringResource(R.string.label_site)
    val initialUrl = remember(siteName, defaultLabel) {
        "https://www.google.com/search?q=${Uri.encode(siteName.ifBlank { defaultLabel })}"
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = siteCard,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.fav_search_site_google),
                        color = siteInk,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.btn_close), color = siteMuted)
                    }
                }
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    if (request?.isForMainFrame != true) return false
                                    val targetUrl = extractGoogleResultUrl(request.url.toString())
                                    if (targetUrl != null && targetUrl != initialUrl) {
                                        onUrlPicked(targetUrl)
                                        return true
                                    }
                                    return false
                                }
                            }
                            loadUrl(initialUrl)
                        }
                    }
                )
            }
        }
    }
}

private fun extractGoogleResultUrl(rawUrl: String): String? {
    val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return null
    val host = uri.host.orEmpty().lowercase()
    if (host.contains("google.")) {
        if (uri.path == "/url") {
            return uri.getQueryParameter("url") ?: uri.getQueryParameter("q")
        }
        return null
    }
    return rawUrl.takeIf { it.startsWith("http://") || it.startsWith("https://") }
}

@Composable
private fun favoriteSiteTextFieldColors(): TextFieldColors {
    val siteInk = GalleryColorTokens.Dark.primaryText
    val siteField = GalleryColorTokens.Dark.field
    val siteAccent = GalleryColorTokens.Dark.accent
    val siteMuted = GalleryColorTokens.Dark.secondaryText

    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = siteInk,
        unfocusedTextColor = siteInk,
        focusedContainerColor = siteField,
        unfocusedContainerColor = siteField,
        cursorColor = siteAccent,
        focusedBorderColor = siteAccent,
        unfocusedBorderColor = GalleryThemeTokens.colors.divider,
        focusedLabelColor = siteAccent,
        unfocusedLabelColor = siteMuted
    )
}

private fun openFavoriteSite(context: Context, rawUrl: String) {
    if (rawUrl.isBlank()) return
    val url = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
        rawUrl
    } else {
        "https://$rawUrl"
    }
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private fun loadFavoriteSites(preferences: android.content.SharedPreferences): List<FavoriteSite> {
    return runCatching {
        val array = JSONArray(preferences.getString(FAVORITE_SITES_KEY, "[]"))
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            FavoriteSite(
                name = item.optString("name"),
                url = item.optString("url"),
                description = item.optString("description")
            )
        }
    }.getOrDefault(emptyList())
}

private fun saveFavoriteSites(
    preferences: android.content.SharedPreferences,
    sites: List<FavoriteSite>
) {
    val array = JSONArray()
    sites.forEach { site ->
        array.put(
            JSONObject()
                .put("name", site.name)
                .put("url", site.url)
                .put("description", site.description)
        )
    }
    preferences.edit().putString(FAVORITE_SITES_KEY, array.toString()).apply()
}

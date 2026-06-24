package com.example.gallery.ui.screen

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import java.net.URLEncoder

private const val CREATOR_PREFS = "favorite_artists"
private const val CREATOR_LIST_KEY = "artists"
private const val CUSTOM_SITE_KEY = "custom_sites"

private val defaultPlatforms = listOf("X", "pixiv", "Support")
private val creatorBackground = Color(0xFF11100F)
private val creatorCard = Color(0xFF1D1A18)
private val creatorInk = Color(0xFFF4EFE8)
private val creatorMuted = Color(0xFFB8ADA2)
private val creatorAccent = Color(0xFFD28A5E)
private val creatorField = Color(0xFF28231F)

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
    onMenuClick: () -> Unit = {},
    onNavigateHome: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(CREATOR_PREFS, Context.MODE_PRIVATE) }
    var creators by remember { mutableStateOf(loadCreators(prefs).ifEmpty { emptyList() }) }
    var customSites by remember { mutableStateOf(loadCustomSites(prefs)) }
    var customSiteInput by remember { mutableStateOf("") }
    var pendingDeleteCreatorIndex by remember { mutableStateOf<Int?>(null) }
    var pendingSearch by remember { mutableStateOf<SearchTarget?>(null) }
    var activeWebSearch by remember { mutableStateOf<SearchTarget?>(null) }
    var isEditMode by remember { mutableStateOf(creators.isEmpty()) }
    val platforms = remember(customSites) { (defaultPlatforms + customSites).distinct() }

    fun persistCreators(next: List<FavoriteCreator>) {
        creators = next
        saveCreators(prefs, next.filter { creator ->
            creator.name.isNotBlank() || creator.links.any { it.url.isNotBlank() }
        })
    }

    fun persistCustomSites(next: List<String>) {
        customSites = next.map { it.trim() }.filter { it.isNotBlank() && it !in defaultPlatforms }.distinct()
        saveCustomSites(prefs, customSites)
    }

    fun getBackupFile(): File {
        // 共有ストレージのルートではなく、アプリ専用の外部ストレージを使用するか、
        // ユーザーに分かりやすい場所（Documentsなど）にフォルダを作成する。
        // ここでは、権限の問題を回避しつつユーザーが見つけやすい「Documents/Gallery/Backups」を試みます。
        val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val folder = File(baseDir, "Gallery/Backups")
        if (!folder.exists()) folder.mkdirs()
        return File(folder, "favorite_artists.json")
    }

    fun exportData() {
        if (creators.isEmpty() && customSites.isEmpty()) {
            Toast.makeText(context, "データがないためバックアップできません", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch(Dispatchers.IO) {
            runCatching {
                val file = getBackupFile()
                FileOutputStream(file).use { stream ->
                    val root = JSONObject()
                    val artistsArray = JSONArray()
                    creators.forEach { c ->
                        val links = JSONArray()
                        c.links.forEach { l ->
                            links.put(JSONObject().put("platform", l.platform).put("url", l.url))
                        }
                        artistsArray.put(JSONObject().put("name", c.name).put("links", links))
                    }
                    root.put("artists", artistsArray)
                    val sitesArray = JSONArray()
                    customSites.forEach { s -> sitesArray.put(s) }
                    root.put("custom_sites", sitesArray)

                    OutputStreamWriter(stream).use { writer ->
                        writer.write(root.toString(2))
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "保存しました: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "保存に失敗しました: ${it.message}", Toast.LENGTH_LONG).show()
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
                        Toast.makeText(context, "バックアップファイルが見つかりません", Toast.LENGTH_SHORT).show()
                    }
                    return@runCatching
                }
                file.inputStream().use { stream ->
                    val content = Scanner(stream).useDelimiter("\\A").next()
                    val root = JSONObject(content)
                    
                    val artistsArray = root.optJSONArray("artists")
                    val newArtists = mutableListOf<FavoriteCreator>()
                    if (artistsArray != null) {
                        for (i in 0 until artistsArray.length()) {
                            val obj = artistsArray.getJSONObject(i)
                            val name = obj.optString("name")
                            if (name.isBlank()) continue
                            
                            val linksArr = obj.optJSONArray("links")
                            val links = mutableListOf<CreatorLink>()
                            if (linksArr != null) {
                                for (j in 0 until linksArr.length()) {
                                    val l = linksArr.getJSONObject(j)
                                    links.add(CreatorLink(l.optString("platform"), l.optString("url")))
                                }
                            }
                            newArtists.add(FavoriteCreator(name, links))
                        }
                    }

                    val sitesArray = root.optJSONArray("custom_sites")
                    val newSites = mutableListOf<String>()
                    if (sitesArray != null) {
                        for (i in 0 until sitesArray.length()) {
                            newSites.add(sitesArray.getString(i))
                        }
                    }

                    withContext(Dispatchers.Main) {
                        if (newArtists.isNotEmpty()) {
                            val currentNames = creators.map { it.name }.toSet()
                            val uniqueNewArtists = newArtists.filter { it.name !in currentNames }
                            if (uniqueNewArtists.isNotEmpty()) {
                                persistCreators(creators + uniqueNewArtists)
                            }
                        }
                        if (newSites.isNotEmpty()) {
                            val currentSites = customSites.toSet()
                            val uniqueNewSites = newSites.filter { it !in currentSites }
                            if (uniqueNewSites.isNotEmpty()) {
                                persistCustomSites(customSites + uniqueNewSites)
                            }
                        }
                        Toast.makeText(context, "読み込みました", Toast.LENGTH_SHORT).show()
                    }
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "読み込みに失敗しました: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Creators", color = creatorInk) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = creatorInk)
                    }
                },
                actions = {
                    IconButton(onClick = { exportData() }) {
                        Icon(Icons.Default.Download, contentDescription = "Export", tint = creatorInk)
                    }
                    IconButton(onClick = { importData() }) {
                        Icon(Icons.Default.Upload, contentDescription = "Import", tint = creatorInk)
                    }
                    IconButton(onClick = { isEditMode = !isEditMode }) {
                        Icon(Icons.Default.Edit, contentDescription = "Toggle edit", tint = if (isEditMode) creatorAccent else creatorInk)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = creatorBackground)
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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        Spacer(Modifier.width(6.dp))
                        Text("Add creator tab")
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = creatorCard),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("User sites", color = creatorInk, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = customSiteInput,
                                    onValueChange = { customSiteInput = it },
                                    label = { Text("Site name") },
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
                                    Icon(Icons.Default.Add, contentDescription = "Add site", tint = creatorAccent)
                                }
                            }
                        }
                    }
                }
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
            title = "Creator tabを削除",
            text = "このCreator tabと中のリンクを削除します。",
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
            title = { Text("Googleで検索してURLを入力", color = creatorInk) },
            text = {
                Text(
                    "URLが空なのでWebViewでGoogle検索を開きます。最初に開いたページのURLをこのリンク欄へ自動入力します。URLが入っている場合は外部ブラウザで開きます。",
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
                ) { Text("検索を開く") }
            },
            dismissButton = {
                TextButton(onClick = { pendingSearch = null }) { Text("Cancel", color = creatorMuted) }
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
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(creators.filter { it.name.isNotBlank() || it.links.any { link -> link.url.isNotBlank() } }) { _, creator ->
            Card(
                colors = CardDefaults.cardColors(containerColor = creatorCard),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = creator.name.ifBlank { "Untitled creator" },
                        color = creatorInk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    creator.links.filter { it.url.isNotBlank() }.forEach { link ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openUrl(context, link.url) },
                            color = creatorField,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF4B4038))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = link.platform,
                                    color = creatorAccent,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(80.dp),
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
    }
}

@Composable
private fun CreatorEditCard(
    creator: FavoriteCreator,
    platforms: List<String>,
    onCreatorChange: (FavoriteCreator) -> Unit,
    onDeleteCreator: () -> Unit,
    onSearchRequested: (Int, SearchTarget) -> Unit
) {
    val context = LocalContext.current
    var pendingDeleteLinkIndex by remember { mutableStateOf<Int?>(null) }
    Card(
        colors = CardDefaults.cardColors(containerColor = creatorCard),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Creator tab", color = creatorInk, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDeleteCreator) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete creator", tint = creatorMuted)
                }
            }

            OutlinedTextField(
                value = creator.name,
                onValueChange = { onCreatorChange(creator.copy(name = it)) },
                label = { Text("Creator name") },
                singleLine = true,
                colors = creatorTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            creator.links.forEachIndexed { linkIndex, link ->
                CreatorLinkEditor(
                    platforms = platforms,
                    link = link,
                    creatorName = creator.name,
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
                Spacer(Modifier.width(6.dp))
                Text("Add link")
            }
        }
    }

    pendingDeleteLinkIndex?.let { index ->
        ConfirmDeleteDialog(
            title = "リンクを削除",
            text = "このリンク欄を削除します。",
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
    creatorName: String,
    onLinkChange: (CreatorLink) -> Unit,
    onDelete: () -> Unit,
    onAccess: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PlatformDropdown(
            platforms = platforms,
            selectedPlatform = link.platform.ifBlank { platforms.firstOrNull().orEmpty() },
            onPlatformSelected = { onLinkChange(link.copy(platform = it)) },
            modifier = Modifier.width(122.dp)
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = link.url,
            onValueChange = { onLinkChange(link.copy(url = it)) },
            label = { Text("URL") },
            singleLine = true,
            colors = creatorTextFieldColors(),
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onAccess) {
            Icon(Icons.Default.OpenInBrowser, contentDescription = if (link.url.isBlank()) "Search $creatorName" else "Open URL", tint = creatorAccent)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete link", tint = creatorMuted)
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
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            color = creatorField,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, Color.Gray)
        ) {
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedPlatform,
                    color = creatorInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    fontSize = 13.sp
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
private fun creatorTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = creatorInk,
    unfocusedTextColor = creatorInk,
    focusedLabelColor = creatorAccent,
    unfocusedLabelColor = creatorMuted,
    cursorColor = creatorAccent,
    focusedBorderColor = creatorAccent,
    unfocusedBorderColor = Color(0xFF6B5A4D),
    focusedContainerColor = creatorField,
    unfocusedContainerColor = creatorField
)

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = creatorCard,
        title = { Text(title, color = creatorInk) },
        text = { Text(text, color = creatorMuted) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB5483A))
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = creatorMuted) }
        }
    )
}

@Composable
private fun CreatorSearchDialog(
    request: SearchTarget,
    onDismiss: () -> Unit,
    onUrlPicked: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = creatorCard,
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
                        "${request.platform} search",
                        color = creatorInk,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text("Close", color = creatorMuted) }
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
                                var initialPageLoaded = false
                                
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    if (url == request.initialUrl) {
                                        initialPageLoaded = true
                                    }
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    webRequest: WebResourceRequest?
                                ): Boolean {
                                    val targetUrl = webRequest?.url?.toString().orEmpty()
                                    // 最初のGoogle検索結果ページが表示された後、最初に別のURLへ遷移しようとした時にそのURLを取得する
                                    if (
                                        initialPageLoaded &&
                                        webRequest?.isForMainFrame == true &&
                                        targetUrl.isNotBlank() &&
                                        targetUrl != request.initialUrl
                                    ) {
                                        onUrlPicked(targetUrl)
                                        return true
                                    }
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

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
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
                        platform = link.optString("platform"),
                        url = link.optString("url")
                    )
                }
            } ?: listOf(
                CreatorLink("Support", item.optString("supportUrl")),
                CreatorLink("pixiv", item.optString("pixivUrl")),
                CreatorLink("X", item.optString("xUrl"))
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
        val links = JSONArray()
        creator.links
            .filter { it.platform.isNotBlank() && it.url.isNotBlank() }
            .forEach { link ->
                links.put(
                    JSONObject()
                        .put("platform", link.platform)
                        .put("url", link.url)
                )
            }
        if (creator.name.isNotBlank() || links.length() > 0) {
            array.put(
                JSONObject()
                    .put("name", creator.name)
                    .put("links", links)
            )
        }
    }
    prefs.edit().putString(CREATOR_LIST_KEY, array.toString()).apply()
}

private fun loadCustomSites(prefs: android.content.SharedPreferences): List<String> {
    val raw = prefs.getString(CUSTOM_SITE_KEY, "[]").orEmpty()
    return runCatching {
        val array = JSONArray(raw)
        List(array.length()) { index -> array.optString(index) }
            .filter { it.isNotBlank() && it !in defaultPlatforms }
    }.getOrDefault(emptyList())
}

private fun saveCustomSites(prefs: android.content.SharedPreferences, sites: List<String>) {
    val array = JSONArray()
    sites.filter { it.isNotBlank() && it !in defaultPlatforms }.distinct().forEach { array.put(it) }
    prefs.edit().putString(CUSTOM_SITE_KEY, array.toString()).apply()
}

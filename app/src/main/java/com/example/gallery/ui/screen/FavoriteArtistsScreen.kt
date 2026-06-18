package com.example.gallery.ui.screen

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
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
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

private const val CREATOR_PREFS = "favorite_artists"
private const val CREATOR_LIST_KEY = "artists"
private const val CUSTOM_SITE_KEY = "custom_sites"

private val defaultPlatforms = listOf("X", "pixiv", "Support")

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
    val prefs = remember { context.getSharedPreferences(CREATOR_PREFS, Context.MODE_PRIVATE) }
    var creators by remember { mutableStateOf(loadCreators(prefs)) }
    var customSites by remember { mutableStateOf(loadCustomSites(prefs)) }
    val platforms = remember(customSites) { defaultPlatforms + customSites }

    var creatorName by remember { mutableStateOf("") }
    var selectedPlatform by remember { mutableStateOf(defaultPlatforms.first()) }
    var urlInput by remember { mutableStateOf("") }
    var linkDrafts by remember { mutableStateOf<List<CreatorLink>>(emptyList()) }
    var customSiteInput by remember { mutableStateOf("") }
    var webSearch by remember { mutableStateOf<WebSearchRequest?>(null) }

    fun persistCreators(next: List<FavoriteCreator>) {
        creators = next
        saveCreators(prefs, next)
    }

    fun persistCustomSites(next: List<String>) {
        customSites = next.distinct().filter { it.isNotBlank() }
        saveCustomSites(prefs, customSites)
    }

    fun clearForm() {
        creatorName = ""
        selectedPlatform = defaultPlatforms.first()
        urlInput = ""
        linkDrafts = emptyList()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("お気に入りクリエイター", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateHome) {
                        Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("クリエイターを追加", color = Color.White, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = creatorName,
                            onValueChange = { creatorName = it },
                            label = { Text("名前") },
                            singleLine = true,
                            colors = creatorTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        CreatorLinkEditor(
                            platforms = platforms,
                            selectedPlatform = selectedPlatform,
                            onPlatformSelected = {
                                selectedPlatform = it
                                urlInput = linkDrafts.firstOrNull { link -> link.platform == it }?.url.orEmpty()
                            },
                            url = urlInput,
                            onUrlChange = { urlInput = it },
                            onAccess = {
                                if (urlInput.isBlank()) {
                                    webSearch = WebSearchRequest(
                                        initialUrl = buildPlatformSearchUrl(selectedPlatform, creatorName),
                                        platform = selectedPlatform
                                    )
                                } else {
                                    openUrl(context, urlInput)
                                }
                            }
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = selectedPlatform.isNotBlank() && urlInput.isNotBlank(),
                                onClick = {
                                    linkDrafts = (linkDrafts.filterNot { it.platform == selectedPlatform } +
                                        CreatorLink(selectedPlatform, urlInput.trim()))
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("リンク追加")
                            }
                            TextButton(onClick = { clearForm() }) {
                                Text("クリア", color = Color.Gray)
                            }
                        }

                        linkDrafts.forEach { link ->
                            CreatorLinkRow(
                                link = link,
                                onOpen = { openUrl(context, link.url) },
                                onDelete = { linkDrafts = linkDrafts.filterNot { it == link } }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                enabled = creatorName.isNotBlank(),
                                onClick = {
                                    val finalLinks = mergeDraftLink(linkDrafts, selectedPlatform, urlInput)
                                    persistCreators(
                                        creators + FavoriteCreator(
                                            name = creatorName.trim(),
                                            links = finalLinks
                                        )
                                    )
                                    clearForm()
                                }
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("保存")
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Text("サイトを追加", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = customSiteInput,
                                onValueChange = { customSiteInput = it },
                                label = { Text("サイト名") },
                                singleLine = true,
                                colors = creatorTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                enabled = customSiteInput.isNotBlank(),
                                onClick = {
                                    persistCustomSites(customSites + customSiteInput.trim())
                                    selectedPlatform = customSiteInput.trim()
                                    customSiteInput = ""
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add site", tint = Color.White)
                            }
                        }
                    }
                }
            }

            items(creators, key = { it.name + it.links.joinToString { link -> link.platform + link.url } }) { creator ->
                FavoriteCreatorCard(
                    creator = creator,
                    onDelete = { persistCreators(creators.filterNot { it == creator }) }
                )
            }
        }
    }

    webSearch?.let { request ->
        CreatorSearchDialog(
            request = request,
            onDismiss = { webSearch = null },
            onUrlPicked = { pickedUrl ->
                selectedPlatform = request.platform
                urlInput = pickedUrl
                webSearch = null
            }
        )
    }
}

@Composable
private fun CreatorLinkEditor(
    platforms: List<String>,
    selectedPlatform: String,
    onPlatformSelected: (String) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    onAccess: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PlatformDropdown(
            platforms = platforms,
            selectedPlatform = selectedPlatform,
            onPlatformSelected = onPlatformSelected,
            modifier = Modifier.width(118.dp)
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text("URL") },
            singleLine = true,
            colors = creatorTextFieldColors(),
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onAccess) {
            Icon(Icons.Default.OpenInBrowser, contentDescription = "Open or search", tint = Color.White)
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
            color = Color(0xFF101010),
            shape = RoundedCornerShape(6.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray)
        ) {
            Text(
                text = selectedPlatform,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 16.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
private fun FavoriteCreatorCard(
    creator: FavoriteCreator,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    creator.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.LightGray)
                }
            }
            creator.links.forEach { link ->
                CreatorLinkRow(
                    link = link,
                    onOpen = { openUrl(context, link.url) },
                    onDelete = null
                )
            }
        }
    }
}

@Composable
private fun CreatorLinkRow(
    link: CreatorLink,
    onOpen: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(link.platform, color = Color.Cyan, modifier = Modifier.width(76.dp), fontSize = 12.sp)
        Text(link.url, color = Color.LightGray, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, modifier = Modifier.weight(1f))
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete link", tint = Color.Gray)
            }
        }
    }
}

@Composable
private fun CreatorSearchDialog(
    request: WebSearchRequest,
    onDismiss: () -> Unit,
    onUrlPicked: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
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
                        "${request.platform} 検索",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) {
                        Text("閉じる", color = Color.LightGray)
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
                                private var initialPageFinished = false

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    initialPageFinished = true
                                    super.onPageFinished(view, url)
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, webRequest: WebResourceRequest?): Boolean {
                                    val targetUrl = webRequest?.url?.toString().orEmpty()
                                    if (
                                        initialPageFinished &&
                                        webRequest?.isForMainFrame == true &&
                                        shouldCapturePickedUrl(searchRequest = request, url = targetUrl)
                                    ) {
                                        onUrlPicked(targetUrl)
                                        return true
                                    }
                                    return false
                                }
                            }
                            loadUrl(request.initialUrl)
                        }
                    },
                    update = { webView ->
                        if (webView.url != request.initialUrl) {
                            webView.loadUrl(request.initialUrl)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun creatorTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = Color.Cyan,
    unfocusedLabelColor = Color.LightGray,
    cursorColor = Color.Cyan,
    focusedBorderColor = Color.Cyan,
    unfocusedBorderColor = Color.Gray,
    focusedContainerColor = Color(0xFF101010),
    unfocusedContainerColor = Color(0xFF101010)
)

private data class WebSearchRequest(
    val initialUrl: String,
    val platform: String
)

private fun mergeDraftLink(links: List<CreatorLink>, platform: String, url: String): List<CreatorLink> {
    if (url.isBlank()) return links
    return links.filterNot { it.platform == platform } + CreatorLink(platform, url.trim())
}

private fun buildPlatformSearchUrl(platform: String, creatorName: String): String {
    val encodedName = URLEncoder.encode(creatorName.trim(), "UTF-8")
    val encodedPlatform = URLEncoder.encode(platform, "UTF-8")
    return when (platform) {
        "X" -> "https://x.com/search?q=$encodedName&src=typed_query&f=user"
        "pixiv" -> "https://www.pixiv.net/search_user.php?s_mode=s_usr&nick=$encodedName"
        "Support" -> "https://www.google.com/search?q=$encodedName+FANBOX+Fantia+Ci-en+Patreon"
        else -> "https://www.google.com/search?q=$encodedName+$encodedPlatform"
    }
}

private fun shouldCapturePickedUrl(searchRequest: WebSearchRequest, url: String): Boolean {
    if (!url.startsWith("http")) return false
    val lower = url.lowercase()
    return when (searchRequest.platform) {
        "X" -> (lower.contains("x.com/") || lower.contains("twitter.com/")) && !lower.contains("/search")
        "pixiv" -> lower.contains("pixiv.net/") && !lower.contains("search_user.php")
        "Support" -> !lower.contains("google.") || !lower.contains("/search")
        else -> !lower.contains("google.") || !lower.contains("/search")
    }
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
                links = links.filter { it.platform.isNotBlank() && it.url.isNotBlank() }
            )
        }.filter { it.name.isNotBlank() }
    }.getOrDefault(emptyList())
}

private fun saveCreators(prefs: android.content.SharedPreferences, creators: List<FavoriteCreator>) {
    val array = JSONArray()
    creators.forEach { creator ->
        val links = JSONArray()
        creator.links.forEach { link ->
            links.put(
                JSONObject()
                    .put("platform", link.platform)
                    .put("url", link.url)
            )
        }
        array.put(
            JSONObject()
                .put("name", creator.name)
                .put("links", links)
        )
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

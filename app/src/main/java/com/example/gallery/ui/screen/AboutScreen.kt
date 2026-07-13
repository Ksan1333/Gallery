package com.example.gallery.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.example.gallery.ui.component.GalleryTopAppBar
import com.example.gallery.ui.theme.GalleryThemeTokens
import androidx.compose.ui.res.stringResource
import com.example.gallery.R
import com.example.gallery.BuildConfig

class ChangelogViewModel : ViewModel() {
    var changelogText by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun fetchChangelog(context: Context) {
        if (isLoading) return
        isLoading = true
        error = null

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    context.assets.open("CHANGELOG.md").use { inputStream ->
                        inputStream.bufferedReader(Charsets.UTF_8).readText()
                    }
                }
                changelogText = result
            } catch (e: Exception) {
                error = e.message ?: context.getString(R.string.error_fetch_data)
            } finally {
                isLoading = false
            }
        }
    }
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    viewModel: ChangelogViewModel = viewModel()
) {
    val context = LocalContext.current
    val colors = GalleryThemeTokens.colors
    val textSizes = GalleryThemeTokens.textSizes
    LaunchedEffect(Unit) {
        viewModel.fetchChangelog(context)
    }

    Scaffold(
        topBar = {
            GalleryTopAppBar(
                title = stringResource(R.string.about_title),
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationContentDescription = stringResource(R.string.btn_back),
                onNavigationClick = onBack
            )
        },
        containerColor = colors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colors.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.app_name), color = colors.primaryText, fontSize = textSizes.title, fontWeight = FontWeight.Bold)
                    Text("Version ${BuildConfig.VERSION_NAME}", color = colors.mutedText, fontSize = textSizes.subtitle)
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(stringResource(R.string.about_changelog), color = colors.accent, fontSize = textSizes.header, fontWeight = FontWeight.Bold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.mutedText.copy(alpha = 0.3f))

            when {
                viewModel.isLoading -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accent)
                    }
                }
                viewModel.error != null -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(viewModel.error!!, color = colors.danger, fontSize = textSizes.subtitle)
                        Button(
                            onClick = { viewModel.fetchChangelog(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.card)
                        ) {
                            Text(stringResource(R.string.btn_retry))
                        }
                    }
                }
                viewModel.changelogText != null -> {
                    ExpandableChangelog(viewModel.changelogText!!)
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(stringResource(R.string.about_dev_info), color = colors.accent, fontSize = textSizes.header, fontWeight = FontWeight.Bold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.mutedText.copy(alpha = 0.3f))

            Text(stringResource(R.string.about_dev_desc), color = colors.secondaryText, fontSize = textSizes.subtitle)

            Spacer(Modifier.height(48.dp))
        }
    }
}

private data class ChangelogSection(
    val title: String,
    val body: String
)

private fun parseChangelogSections(text: String, context: Context): List<ChangelogSection> {
    val sections = mutableListOf<ChangelogSection>()
    val defaultTitle = context.getString(R.string.about_changelog_default)
    var currentTitle = defaultTitle
    val body = StringBuilder()
    text.lines().forEach { line ->
        if (line.startsWith("## ")) {
            if (body.isNotBlank() || sections.isEmpty()) {
                sections += ChangelogSection(currentTitle, body.toString().trim())
            }
            currentTitle = line.removePrefix("## ").trim()
            body.clear()
        } else if (!line.startsWith("# ")) {
            body.appendLine(line)
        }
    }
    if (body.isNotBlank() || sections.isEmpty()) {
        sections += ChangelogSection(currentTitle, body.toString().trim())
    }
    return sections.filter { it.body.isNotBlank() || it.title != defaultTitle }
}

@Composable
fun ExpandableChangelog(text: String) {
    val context = LocalContext.current
    val sections = remember(text) { parseChangelogSections(text, context) }
    var expandedTitle by remember(sections) { mutableStateOf(sections.firstOrNull()?.title) }
    val colors = GalleryThemeTokens.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        sections.forEach { section ->
            val expanded = expandedTitle == section.title
            Surface(
                color = colors.card.copy(alpha = 0.45f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedTitle = if (expanded) null else section.title }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(section.title, color = colors.primaryText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = colors.accent)
                    }
                    if (expanded) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            MarkdownText(section.body)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownText(text: String) {
    val colors = GalleryThemeTokens.colors
    val textSizes = GalleryThemeTokens.textSizes
    Column {
        text.lines().forEach { line ->
            when {
                line.startsWith("# ") -> {
                    Text(
                        text = line.removePrefix("# "),
                        color = colors.primaryText,
                        fontSize = textSizes.header,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                line.startsWith("## ") -> {
                    Text(
                        text = line.removePrefix("## "),
                        color = colors.accent,
                        fontSize = textSizes.header,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                }
                line.startsWith("### ") -> {
                    Text(
                        text = line.removePrefix("### "),
                        color = colors.primaryText,
                        fontSize = textSizes.body,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    Row(modifier = Modifier.padding(start = 8.dp).padding(vertical = 2.dp)) {
                        Text("• ", color = colors.accent)
                        Text(
                            text = line.substring(2),
                            color = colors.secondaryText,
                            fontSize = textSizes.subtitle
                        )
                    }
                }
                line.isBlank() -> {
                    Spacer(Modifier.height(4.dp))
                }
                else -> {
                    Text(
                        text = line,
                        color = colors.secondaryText,
                        fontSize = textSizes.subtitle,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

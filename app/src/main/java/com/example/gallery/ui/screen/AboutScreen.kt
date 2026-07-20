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
import com.example.gallery.ui.theme.galleryTypography
import androidx.compose.ui.res.dimensionResource
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
                .padding(dimensionResource(R.dimen.spacing_medium))
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(dimensionResource(R.dimen.grid_bottom_padding) * 0.64f) // 64.dp
                    )
                    Spacer(Modifier.height(dimensionResource(R.dimen.spacing_small)))
                    Text(stringResource(R.string.app_name), style = galleryTypography.title)
                    Text("Version ${BuildConfig.VERSION_NAME}", style = galleryTypography.bodySecondary)
                }
            }

            Spacer(Modifier.height(dimensionResource(R.dimen.spacing_extra_large)))

            Text(stringResource(R.string.about_changelog), style = galleryTypography.header.copy(color = colors.accent))
            HorizontalDivider(modifier = Modifier.padding(vertical = dimensionResource(R.dimen.spacing_small)), color = colors.mutedText.copy(alpha = 0.3f))

            when {
                viewModel.isLoading -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(dimensionResource(R.dimen.spacing_large)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accent)
                    }
                }
                viewModel.error != null -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(dimensionResource(R.dimen.spacing_medium)),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(viewModel.error!!, style = galleryTypography.bodySecondary.copy(color = colors.danger))
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

            Spacer(Modifier.height(dimensionResource(R.dimen.spacing_extra_large)))

            Text(stringResource(R.string.about_dev_info), style = galleryTypography.header.copy(color = colors.accent))
            HorizontalDivider(modifier = Modifier.padding(vertical = dimensionResource(R.dimen.spacing_small)), color = colors.mutedText.copy(alpha = 0.3f))

            Text(stringResource(R.string.about_dev_desc), style = galleryTypography.bodySecondary)

            Spacer(Modifier.height(dimensionResource(R.dimen.viewer_bottom_bar_height)))
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
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_small))) {
        sections.forEach { section ->
            val expanded = expandedTitle == section.title
            Surface(
                color = colors.card.copy(alpha = 0.45f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(dimensionResource(R.dimen.radius_medium)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedTitle = if (expanded) null else section.title }
                            .padding(horizontal = dimensionResource(R.dimen.spacing_base), vertical = dimensionResource(R.dimen.popup_padding_h)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(section.title, color = colors.primaryText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = colors.accent)
                    }
                    if (expanded) {
                        Column(modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.spacing_base), vertical = dimensionResource(R.dimen.spacing_small))) {
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
    Column {
        text.lines().forEach { line ->
            when {
                line.startsWith("# ") -> {
                    Text(
                        text = line.removePrefix("# "),
                        style = galleryTypography.header,
                        modifier = Modifier.padding(vertical = dimensionResource(R.dimen.spacing_small))
                    )
                }
                line.startsWith("## ") -> {
                    Text(
                        text = line.removePrefix("## "),
                        style = galleryTypography.header.copy(color = colors.accent),
                        modifier = Modifier.padding(top = dimensionResource(R.dimen.spacing_medium), bottom = dimensionResource(R.dimen.spacing_tiny))
                    )
                }
                line.startsWith("### ") -> {
                    Text(
                        text = line.removePrefix("### "),
                        style = galleryTypography.body.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = dimensionResource(R.dimen.spacing_small), bottom = dimensionResource(R.dimen.spacing_tiny))
                    )
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    Row(modifier = Modifier.padding(start = dimensionResource(R.dimen.spacing_small)).padding(vertical = dimensionResource(R.dimen.spacing_micro))) {
                        Text("• ", color = colors.accent)
                        Text(
                            text = line.substring(2),
                            style = galleryTypography.bodySecondary
                        )
                    }
                }
                line.isBlank() -> {
                    Spacer(Modifier.height(dimensionResource(R.dimen.spacing_tiny)))
                }
                else -> {
                    Text(
                        text = line,
                        style = galleryTypography.bodySecondary,
                        modifier = Modifier.padding(vertical = dimensionResource(R.dimen.spacing_micro))
                    )
                }
            }
        }
    }
}

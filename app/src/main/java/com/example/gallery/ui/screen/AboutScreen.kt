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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.gallery.util.AppUpdateManager
import com.example.gallery.util.AppUpdateRelease

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

class AppUpdateViewModel : ViewModel() {
    var release by mutableStateOf<AppUpdateRelease?>(null)
        private set
    var isChecking by mutableStateOf(false)
        private set
    var isDownloading by mutableStateOf(false)
        private set
    var downloadProgress by mutableFloatStateOf(0f)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var needsInstallPermission by mutableStateOf(false)
        private set

    fun load(context: Context) {
        release = AppUpdateManager.cachedUpdate(context)
        check(context)
    }

    fun check(context: Context) {
        if (isChecking || isDownloading) return
        isChecking = true
        error = null
        needsInstallPermission = false
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { AppUpdateManager.checkForUpdate(context, force = true) }
            }.onSuccess { latest ->
                release = latest
            }.onFailure { cause ->
                error = cause.localizedMessage ?: cause.javaClass.simpleName
            }
            isChecking = false
        }
    }

    fun downloadAndInstall(context: Context) {
        val target = release ?: return
        if (isDownloading) return
        if (!AppUpdateManager.requestInstallPermission(context)) {
            needsInstallPermission = true
            return
        }
        isDownloading = true
        downloadProgress = 0f
        error = null
        needsInstallPermission = false
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    AppUpdateManager.downloadApk(context, target) { progress ->
                        downloadProgress = progress
                    }
                }
            }.onSuccess { apk ->
                needsInstallPermission = !AppUpdateManager.installApk(context, apk)
            }.onFailure { cause ->
                error = cause.localizedMessage ?: cause.javaClass.simpleName
            }
            isDownloading = false
        }
    }
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    viewModel: ChangelogViewModel = viewModel(),
    updateViewModel: AppUpdateViewModel = viewModel()
) {
    val context = LocalContext.current
    val colors = GalleryThemeTokens.colors
    LaunchedEffect(Unit) {
        viewModel.fetchChangelog(context)
        updateViewModel.load(context)
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

            Text(stringResource(R.string.about_updates), style = galleryTypography.header.copy(color = colors.accent))
            HorizontalDivider(modifier = Modifier.padding(vertical = dimensionResource(R.dimen.spacing_small)), color = colors.mutedText.copy(alpha = 0.3f))

            when {
                updateViewModel.isChecking && updateViewModel.release == null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            strokeWidth = dimensionResource(R.dimen.spacing_micro),
                            modifier = Modifier.size(dimensionResource(R.dimen.icon_size_medium))
                        )
                        Spacer(Modifier.width(dimensionResource(R.dimen.spacing_small)))
                        Text(stringResource(R.string.update_checking), style = galleryTypography.bodySecondary)
                    }
                }
                updateViewModel.release != null -> {
                    val release = updateViewModel.release!!
                    Text(
                        stringResource(R.string.update_available, release.version),
                        style = galleryTypography.body.copy(color = colors.primaryText, fontWeight = FontWeight.Bold)
                    )
                    if (release.notes.isNotBlank()) {
                        Text(
                            release.notes,
                            style = galleryTypography.bodySecondary,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = dimensionResource(R.dimen.spacing_tiny))
                        )
                    }
                    Spacer(Modifier.height(dimensionResource(R.dimen.spacing_small)))
                    Button(
                        onClick = { updateViewModel.downloadAndInstall(context) },
                        enabled = !updateViewModel.isDownloading,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(dimensionResource(R.dimen.spacing_tiny)))
                        Text(
                            if (updateViewModel.isDownloading) {
                                stringResource(R.string.update_downloading, (updateViewModel.downloadProgress * 100).toInt())
                            } else {
                                stringResource(R.string.update_download_and_install)
                            }
                        )
                    }
                    if (updateViewModel.isDownloading) {
                        LinearProgressIndicator(
                            progress = { updateViewModel.downloadProgress },
                            color = colors.accent,
                            trackColor = colors.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = dimensionResource(R.dimen.spacing_small))
                        )
                    }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.update_up_to_date), style = galleryTypography.bodySecondary, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { updateViewModel.check(context) }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(dimensionResource(R.dimen.spacing_tiny)))
                            Text(stringResource(R.string.update_check_now))
                        }
                    }
                }
            }
            updateViewModel.error?.let { error ->
                Text(
                    stringResource(R.string.update_error, error),
                    style = galleryTypography.small.copy(color = colors.danger),
                    modifier = Modifier.padding(top = dimensionResource(R.dimen.spacing_small))
                )
            }
            if (updateViewModel.needsInstallPermission) {
                Text(
                    stringResource(R.string.update_allow_install_source),
                    style = galleryTypography.small.copy(color = colors.warning),
                    modifier = Modifier.padding(top = dimensionResource(R.dimen.spacing_small))
                )
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

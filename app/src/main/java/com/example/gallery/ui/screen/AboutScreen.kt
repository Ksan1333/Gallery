package com.example.gallery.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
                error = e.message ?: "データの取得に失敗しました"
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
    LaunchedEffect(Unit) {
        viewModel.fetchChangelog(context)
    }

    Scaffold(
        topBar = {
            GalleryTopAppBar(
                title = "アプリ情報",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationContentDescription = "戻る",
                onNavigationClick = onBack
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Color.Cyan,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Gallery App", color = Color.White, fontSize = com.example.gallery.ui.AppConstants.TitleFontSize, fontWeight = FontWeight.Bold)
                    Text("Version ${BuildConfig.VERSION_NAME}", color = Color.Gray, fontSize = com.example.gallery.ui.AppConstants.SubtitleFontSize)
                }
            }

            Spacer(Modifier.height(32.dp))

            Text("更新履歴", color = Color.Cyan, fontSize = com.example.gallery.ui.AppConstants.HeaderFontSize, fontWeight = FontWeight.Bold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.3f))

            when {
                viewModel.isLoading -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.Cyan)
                    }
                }
                viewModel.error != null -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(viewModel.error!!, color = Color.Red, fontSize = com.example.gallery.ui.AppConstants.SubtitleFontSize)
                        Button(
                            onClick = { viewModel.fetchChangelog(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                        ) {
                            Text("再試行")
                        }
                    }
                }
                viewModel.changelogText != null -> {
                    MarkdownText(viewModel.changelogText!!)
                }
            }

            Spacer(Modifier.height(32.dp))

            Text("開発情報", color = Color.Cyan, fontSize = com.example.gallery.ui.AppConstants.HeaderFontSize, fontWeight = FontWeight.Bold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.3f))

            Text("Android Studio AI Assistant によって開発を支援しています。", color = Color.LightGray, fontSize = com.example.gallery.ui.AppConstants.SubtitleFontSize)

            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
fun MarkdownText(text: String) {
    Column {
        text.lines().forEach { line ->
            when {
                line.startsWith("# ") -> {
                    Text(
                        text = line.removePrefix("# "),
                        color = Color.White,
                        fontSize = com.example.gallery.ui.AppConstants.HeaderFontSize,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                line.startsWith("## ") -> {
                    Text(
                        text = line.removePrefix("## "),
                        color = Color.Cyan,
                        fontSize = com.example.gallery.ui.AppConstants.HeaderFontSize,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                }
                line.startsWith("### ") -> {
                    Text(
                        text = line.removePrefix("### "),
                        color = Color.White,
                        fontSize = com.example.gallery.ui.AppConstants.BodyFontSize,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    Row(modifier = Modifier.padding(start = 8.dp).padding(vertical = 2.dp)) {
                        Text("• ", color = Color.Cyan)
                        Text(
                            text = line.substring(2),
                            color = Color.LightGray,
                            fontSize = com.example.gallery.ui.AppConstants.SubtitleFontSize
                        )
                    }
                }
                line.isBlank() -> {
                    Spacer(Modifier.height(4.dp))
                }
                else -> {
                    Text(
                        text = line,
                        color = Color.LightGray,
                        fontSize = com.example.gallery.ui.AppConstants.SubtitleFontSize,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import java.io.BufferedReader
import java.io.InputStreamReader

class ChangelogViewModel : ViewModel() {
    var changelogText by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val viewModelJob = SupervisorJob()
    private val viewModelScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    fun fetchChangelog(context: Context) {
        if (isLoading) return
        isLoading = true
        error = null
        
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    context.assets.open("CHANGELOG.md").use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { reader ->
                            reader.readText()
                        }
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

@OptIn(ExperimentalMaterial3Api::class)
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
            TopAppBar(
                title = { Text("アプリ情報", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
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
                    Text("Gallery App", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Version 1.0.0", color = Color.Gray, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(32.dp))

            Text("更新履歴", color = Color.Cyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                        Text(viewModel.error!!, color = Color.Red, fontSize = 14.sp)
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

            Text("開発情報", color = Color.Cyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.3f))
            
            Text("Android Studio AI Assistant によって開発を支援しています。", color = Color.LightGray, fontSize = 14.sp)
            
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
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                line.startsWith("## ") -> {
                    Text(
                        text = line.removePrefix("## "),
                        color = Color.Cyan,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                }
                line.startsWith("### ") -> {
                    Text(
                        text = line.removePrefix("### "),
                        color = Color.White,
                        fontSize = 16.sp,
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
                            fontSize = 14.sp
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
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}


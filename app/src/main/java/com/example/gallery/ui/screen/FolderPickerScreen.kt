package com.example.gallery.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.GalleryState
import com.example.gallery.ui.component.CategoryCard
import com.example.gallery.ui.component.CategoryData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerScreen(
    galleryState: GalleryState,
    onFolderSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var folderData by remember { mutableStateOf<List<CategoryData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    fun loadFolders() {
        scope.launch(Dispatchers.IO) {
            isLoading = true
            val allMedia = galleryState.repository.getAllMedia()
            val categories = allMedia.groupBy { it.folderName }
                .map { (name, images) ->
                    CategoryData(
                        id = name,
                        title = name,
                        count = images.size,
                        thumbnail = images.firstOrNull()?.uri
                    )
                }.sortedBy { it.title }

            withContext(Dispatchers.Main) {
                folderData = categories
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadFolders() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("移動先を選択", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateFolderDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "新規フォルダ", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = AppConstants.BackgroundColor
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(folderData, key = { it.id }) { category ->
                        CategoryCard(data = category, onClick = { onFolderSelected(category.id) })
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("新規フォルダ作成") },
            text = {
                Column {
                    Text("DCIM直下に作成されます", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("フォルダ名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            scope.launch {
                                if (galleryState.repository.createFolderUnderDCIM(newFolderName)) {
                                    onFolderSelected(newFolderName)
                                }
                                showCreateFolderDialog = false
                            }
                        }
                    }
                ) { Text("作成して選択") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("キャンセル") }
            }
        )
    }
}

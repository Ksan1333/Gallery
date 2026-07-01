package com.example.gallery.ui.screen

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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import com.example.gallery.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.state.GalleryState
import com.example.gallery.ui.component.GalleryTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            GalleryTopAppBar(
                title = "フォルダを選択",
                navigationIcon = Icons.Default.ArrowBack,
                navigationContentDescription = "戻る",
                onNavigationClick = onBack,
                centered = true,
                actions = {
                    IconButton(onClick = { showCreateFolderDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "新規フォルダ", tint = Color.White)
                    }
                }
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
            title = { Text(stringResource(R.string.folder_create_new)) },
            text = {
                Column {
                    Text(stringResource(R.string.folder_dcim_desc), fontSize = AppConstants.SmallFontSize, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text(stringResource(R.string.folder_name)) },
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
                ) { Text(stringResource(R.string.btn_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}

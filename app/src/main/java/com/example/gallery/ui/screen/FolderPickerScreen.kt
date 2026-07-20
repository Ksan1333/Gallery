package com.example.gallery.ui.screen

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import com.example.gallery.R
import androidx.compose.ui.unit.dp
import com.example.gallery.ui.state.GalleryState
import com.example.gallery.ui.component.GalleryTopAppBar
import com.example.gallery.ui.theme.GalleryThemeTokens
import com.example.gallery.util.FolderGroupStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FolderPickerScreen(
    galleryState: GalleryState,
    onFolderSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = GalleryThemeTokens.colors
    val textSizes = GalleryThemeTokens.textSizes
    val scope = rememberCoroutineScope()

    val globalPrefs = remember(context) { context.getSharedPreferences("global_settings", Context.MODE_PRIVATE) }
    val folderGroups = remember(globalPrefs) { FolderGroupStore.load(globalPrefs) }
    val folderOrders by galleryState.repository.getAllFolderOrders().collectAsState(initial = emptyList())
    val orderMap = remember(folderOrders) { folderOrders.associate { it.id to it.position } }

    var baseCategories by remember { mutableStateOf<List<CategoryData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    val displayedCategories = remember(baseCategories, folderGroups, orderMap) {
        val sorted = baseCategories.sortedWith(compareBy({ orderMap[it.id] ?: Int.MAX_VALUE }, { it.title }))
        buildFolderGroupCategories(sorted, folderGroups)
    }

    fun loadFolders() {
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isLoading = true }
            val allMedia = galleryState.repository.getAllMedia()
            val categories = allMedia.groupBy { it.folderName }
                .map { (name, images) ->
                    CategoryData(
                        id = name,
                        title = name,
                        count = images.size,
                        thumbnail = images.firstOrNull()?.uri
                    )
                }

            withContext(Dispatchers.Main) {
                baseCategories = categories
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadFolders() }

    Scaffold(
        topBar = {
            GalleryTopAppBar(
                title = stringResource(R.string.folder_picker_title),
                navigationIcon = Icons.Default.ArrowBack,
                navigationContentDescription = stringResource(R.string.btn_back),
                onNavigationClick = onBack,
                centered = true,
                actions = {
                    IconButton(onClick = { showCreateFolderDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = stringResource(R.string.folder_create_new), tint = colors.primaryText)
                    }
                }
            )
        },
        containerColor = colors.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = colors.primaryText)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayedCategories, key = { it.id }) { category ->
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
                    Text(stringResource(R.string.folder_dcim_desc), fontSize = textSizes.small, color = colors.mutedText)
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

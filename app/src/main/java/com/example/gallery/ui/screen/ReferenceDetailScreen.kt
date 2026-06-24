package com.example.gallery.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.gallery.data.local.entity.ReferenceItemEntity
import com.example.gallery.data.local.entity.ReferenceProjectEntity
import com.example.gallery.data.model.MediaData
import com.example.gallery.data.repository.ReferenceRepository
import com.example.gallery.ui.AppConstants
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceDetailScreen(
    projectId: Long,
    onBack: () -> Unit,
    onAddClick: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { ReferenceRepository(context) }
    val items by repository.getItemsForProjectFlow(projectId).collectAsState(initial = emptyList())
    var project by remember { mutableStateOf<ReferenceProjectEntity?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(projectId) {
        project = repository.getAllProjectsFlow().first().find { it.id == projectId }
    }

    var showFinishConfirm by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(project?.title ?: "詳細", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = Color.White)
                    }
                },
                actions = {
                    if (project?.status == "ACTIVE") {
                        IconButton(onClick = {
                            showFinishConfirm = true
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "完了にする", tint = Color.White)
                        }
                    } else if (project?.status == "FINISHED") {
                        IconButton(onClick = {
                            scope.launch {
                                project?.let { 
                                    repository.updateProject(it.copy(status = "ACTIVE"))
                                    project = it.copy(status = "ACTIVE")
                                }
                            }
                        }) {
                            Icon(Icons.Default.Brush, contentDescription = "進行中に戻す", tint = Color.Cyan)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black)
            )
        },
        floatingActionButton = {
            if (project?.status == "ACTIVE") {
                FloatingActionButton(
                    onClick = onAddClick,
                    containerColor = Color.Cyan,
                    contentColor = Color.Black
                ) {
                    Icon(Icons.Default.Add, contentDescription = "資料を追加")
                }
            }
        },
        containerColor = AppConstants.BackgroundColor
    ) { padding ->
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("資料が登録されていません", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(items) { index, item ->
                    ReferenceItemCard(
                        item = item,
                        onClick = { selectedIndex = index },
                        onDelete = { scope.launch { repository.deleteItem(item) } },
                        onSave = if (item.localUri == null) {
                            { scope.launch { repository.downloadItemToLocal(item) } }
                        } else null
                    )
                }
            }
        }
    }

    if (showFinishConfirm) {
        AlertDialog(
            onDismissRequest = { showFinishConfirm = false },
            title = { Text("プロジェクトを完了しますか？") },
            text = { Text("プロジェクトを完了にします。スクショは残しますが、DLした画像は削除され、URLをもとに代替表示します。") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            project?.let { repository.finishProject(it) }
                            project = project?.copy(status = "FINISHED")
                            showFinishConfirm = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan, contentColor = Color.Black)
                ) {
                    Text("完了する")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishConfirm = false }) {
                    Text("キャンセル", color = Color.Gray)
                }
            }
        )
    }

    selectedIndex?.let { idx ->
        if (items.isNotEmpty() && idx in items.indices) {
            val mediaList = items.map { refItem ->
                MediaData(
                    uri = refItem.localUri ?: refItem.remoteUrl,
                    dateAdded = refItem.addedAt,
                    fileName = refItem.title.ifBlank { "資料" }
                )
            }
            MediaViewerScreen(
                imageList = mediaList,
                initialPage = idx,
                onClickedClose = { selectedIndex = null },
                galleryState = null,
                showDeleteButton = false,
                onPageSelected = { selectedIndex = it }
            )
        }
    }
}

@Composable
private fun ReferenceItemCard(
    item: ReferenceItemEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onSave: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1A18)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(Color.DarkGray)
            ) {
                AsyncImage(
                    model = item.localUri ?: item.remoteUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (item.localUri == null) {
                    Icon(
                        Icons.Default.Public,
                        contentDescription = "URL参照",
                        tint = Color.Cyan.copy(alpha = 0.7f),
                        modifier = Modifier.padding(8.dp).align(Alignment.TopEnd).size(20.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title.ifBlank { "無題" },
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if (onSave != null) {
                    IconButton(onClick = onSave, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Download, contentDescription = "保存", tint = Color.Cyan, modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "削除", tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}



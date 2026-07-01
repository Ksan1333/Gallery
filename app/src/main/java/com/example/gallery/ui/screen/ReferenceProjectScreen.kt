package com.example.gallery.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import com.example.gallery.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gallery.data.local.entity.ReferenceProjectEntity
import com.example.gallery.data.repository.ReferenceRepository
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.component.GalleryTopAppBar
import com.example.gallery.ui.theme.GalleryThemeTokens
import kotlinx.coroutines.launch

@Composable
fun ReferenceProjectScreen(
    onMenuClick: () -> Unit,
    onProjectClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { ReferenceRepository(context) }
    val projects by repository.getAllProjectsFlow().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newProjectTitle by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            GalleryTopAppBar(
                title = "お絵描き資料",
                navigationIcon = Icons.Default.Menu,
                navigationContentDescription = "メニュー",
                onNavigationClick = onMenuClick,
                centered = true
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = GalleryThemeTokens.colors.accent,
                contentColor = GalleryThemeTokens.colors.background
            ) {
                Icon(Icons.Default.Add, contentDescription = "プロジェクトを追加")
            }
        },
        containerColor = AppConstants.BackgroundColor
    ) { padding ->
        if (projects.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.ref_no_projects), color = colorResource(R.color.gray))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(projects) { project ->
                    ReferenceProjectCard(
                        project = project,
                        onClick = { onProjectClick(project.id) },
                        onDelete = { scope.launch { repository.deleteProject(project) } }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("プロジェクトを作成") },
            text = {
                OutlinedTextField(
                    value = newProjectTitle,
                    onValueChange = { newProjectTitle = it },
                    label = { Text("プロジェクト名（例: エルフの描き方）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProjectTitle.isNotBlank()) {
                            scope.launch {
                                repository.createProject(newProjectTitle)
                                newProjectTitle = ""
                                showCreateDialog = false
                            }
                        }
                    },
                    enabled = newProjectTitle.isNotBlank()
                ) {
                Text("作成")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                Text("キャンセル")
                }
            }
        )
    }
}

@Composable
private fun ReferenceProjectCard(
    project: ReferenceProjectEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = GalleryThemeTokens.colors.card),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (project.status == "FINISHED") Icons.Default.Check else Icons.Default.Brush,
                contentDescription = null,
                tint = if (project.status == "FINISHED") Color.Gray else Color.Cyan,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.title,
                    color = Color.White,
                    fontSize = com.example.gallery.ui.AppConstants.HeaderFontSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (project.status == "FINISHED") "完了" else "進行中",
                    color = if (project.status == "FINISHED") Color.Gray else Color.Green,
                    fontSize = com.example.gallery.ui.AppConstants.SmallFontSize
                )
            }
            IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "削除", tint = Color.Gray)
            }
        }
    }
}

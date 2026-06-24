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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gallery.data.local.entity.ReferenceProjectEntity
import com.example.gallery.data.repository.ReferenceRepository
import com.example.gallery.ui.AppConstants
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
            CenterAlignedTopAppBar(
                title = { Text("お絵描き資料", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "メニュー", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = Color.Cyan,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "新規プロジェクト")
            }
        },
        containerColor = AppConstants.BackgroundColor
    ) { padding ->
        if (projects.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("プロジェクトがありません", color = Color.Gray)
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
            title = { Text("新規プロジェクト") },
            text = {
                OutlinedTextField(
                    value = newProjectTitle,
                    onValueChange = { newProjectTitle = it },
                    label = { Text("プロジェクト名 (例: エルフの描き方)") },
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1A18)),
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
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (project.status == "FINISHED") "完了" else "進行中",
                    color = if (project.status == "FINISHED") Color.Gray else Color.Green,
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "削除", tint = Color.Gray)
            }
        }
    }
}

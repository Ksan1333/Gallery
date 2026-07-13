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
    val colors = GalleryThemeTokens.colors

    Scaffold(
        topBar = {
            GalleryTopAppBar(
                title = stringResource(R.string.nav_references),
                navigationIcon = Icons.Default.Menu,
                navigationContentDescription = stringResource(R.string.btn_open),
                onNavigationClick = onMenuClick,
                centered = true
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = colors.accent,
                contentColor = colors.background
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.ref_create_project))
            }
        },
        containerColor = colors.background
    ) { padding ->
        if (projects.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.ref_no_projects), color = colors.mutedText)
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
            title = { Text(stringResource(R.string.ref_create_title)) },
            text = {
                OutlinedTextField(
                    value = newProjectTitle,
                    onValueChange = { newProjectTitle = it },
                    label = { Text(stringResource(R.string.ref_project_name_hint)) },
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
                Text(stringResource(R.string.btn_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                Text(stringResource(R.string.btn_cancel))
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
    val colors = GalleryThemeTokens.colors
    val textSizes = GalleryThemeTokens.textSizes
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = colors.card),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (project.status == AppConstants.STATUS_FINISHED) Icons.Default.Check else Icons.Default.Brush,
                contentDescription = null,
                tint = if (project.status == AppConstants.STATUS_FINISHED) colors.mutedText else colors.accent,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.title,
                    color = colors.primaryText,
                    fontSize = textSizes.header,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (project.status == AppConstants.STATUS_FINISHED) stringResource(R.string.ref_status_done) else stringResource(R.string.ref_status_active),
                    color = if (project.status == AppConstants.STATUS_FINISHED) colors.mutedText else colors.success,
                    fontSize = textSizes.small
                )
            }
            IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.btn_delete), tint = colors.mutedText)
            }
        }
    }
}

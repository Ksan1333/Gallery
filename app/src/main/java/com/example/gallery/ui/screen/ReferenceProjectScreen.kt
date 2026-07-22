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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.example.gallery.R
import com.example.gallery.data.local.entity.ReferenceItemEntity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gallery.data.local.entity.ReferenceProjectEntity
import com.example.gallery.data.repository.ReferenceRepository
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.component.GalleryTopAppBar
import com.example.gallery.ui.theme.GalleryThemeTokens
import coil.compose.AsyncImage
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
    var projectToFinish by remember { mutableStateOf<ReferenceProjectEntity?>(null) }
    var projectToDelete by remember { mutableStateOf<ReferenceProjectEntity?>(null) }
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
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null
                    )
                },
                text = { Text(stringResource(R.string.ref_create_project)) },
                containerColor = colors.accent,
                contentColor = colors.background
            )
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
                contentPadding = PaddingValues(dimensionResource(R.dimen.spacing_medium)),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_base))
            ) {
                items(projects, key = { it.id }) { project ->
                    val referenceItems by repository
                        .getItemsForProjectFlow(project.id)
                        .collectAsState(initial = emptyList())
                    ReferenceProjectCard(
                        project = project,
                        referenceItems = referenceItems,
                        onClick = { onProjectClick(project.id) },
                        onStatusChange = {
                            if (project.status == AppConstants.STATUS_ACTIVE) {
                                projectToFinish = project
                            } else {
                                scope.launch {
                                    repository.updateProject(project.copy(status = AppConstants.STATUS_ACTIVE))
                                }
                            }
                        },
                        onDelete = { projectToDelete = project }
                    )
                }
            }
        }
    }

    projectToFinish?.let { project ->
        AlertDialog(
            onDismissRequest = { projectToFinish = null },
            title = { Text(stringResource(R.string.ref_complete_project_title)) },
            text = { Text(stringResource(R.string.ref_complete_project_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.finishProject(project)
                            projectToFinish = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.background
                    )
                ) {
                    Text(stringResource(R.string.btn_complete))
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToFinish = null }) {
                    Text(stringResource(R.string.btn_cancel), color = colors.mutedText)
                }
            }
        )
    }

    projectToDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text(stringResource(R.string.ref_delete_project_title)) },
            text = { Text(stringResource(R.string.ref_delete_project_text, project.title)) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.deleteProject(project)
                            projectToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.danger)
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text(stringResource(R.string.btn_cancel), color = colors.mutedText)
                }
            }
        )
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
    referenceItems: List<ReferenceItemEntity>,
    onClick: () -> Unit,
    onStatusChange: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = GalleryThemeTokens.colors
    val textSizes = GalleryThemeTokens.textSizes
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = colors.card),
        shape = RoundedCornerShape(dimensionResource(R.dimen.radius_medium))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimensionResource(R.dimen.spacing_medium)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_medium))
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = if (project.status == AppConstants.STATUS_FINISHED) Icons.Default.Check else Icons.Default.Brush,
                    contentDescription = null,
                    tint = if (project.status == AppConstants.STATUS_FINISHED) colors.mutedText else colors.accent,
                    modifier = Modifier.size(dimensionResource(R.dimen.icon_size_large))
                )
                Spacer(Modifier.width(dimensionResource(R.dimen.spacing_small)))
                Text(
                    text = project.title,
                    modifier = Modifier.weight(1f),
                    color = colors.primaryText,
                    fontSize = textSizes.body,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ReferencePreviewRow(referenceItems)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (project.status == AppConstants.STATUS_FINISHED) stringResource(R.string.ref_status_done) else stringResource(R.string.ref_status_active),
                    color = if (project.status == AppConstants.STATUS_FINISHED) colors.mutedText else colors.success,
                    fontSize = textSizes.small
                )
                Row {
                    IconButton(onClick = onStatusChange) {
                        Icon(
                            imageVector = if (project.status == AppConstants.STATUS_ACTIVE) Icons.Default.Check else Icons.Default.Brush,
                            contentDescription = stringResource(
                                if (project.status == AppConstants.STATUS_ACTIVE) {
                                    R.string.ref_mark_complete
                                } else {
                                    R.string.ref_resume_project
                                }
                            ),
                            tint = if (project.status == AppConstants.STATUS_ACTIVE) colors.accent else colors.mutedText
                        )
                    }
                    IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.btn_delete), tint = colors.mutedText)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferencePreviewRow(referenceItems: List<ReferenceItemEntity>) {
    val colors = GalleryThemeTokens.colors
    val previewItems = referenceItems.take(4)
    val thumbnailSize = 56.dp

    if (previewItems.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(thumbnailSize)
                .clip(RoundedCornerShape(dimensionResource(R.dimen.radius_small)))
                .background(colors.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.ref_no_references),
                color = colors.mutedText,
                fontSize = GalleryThemeTokens.textSizes.small
            )
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_small)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        previewItems.forEach { item ->
            AsyncImage(
                model = item.localUri ?: item.remoteUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(thumbnailSize)
                    .clip(RoundedCornerShape(dimensionResource(R.dimen.radius_small)))
                    .background(colors.surfaceVariant)
            )
        }
        if (referenceItems.size > previewItems.size) {
            Text(
                text = "...",
                color = colors.mutedText,
                fontSize = GalleryThemeTokens.textSizes.body,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.spacing_small))
            )
        }
    }
}

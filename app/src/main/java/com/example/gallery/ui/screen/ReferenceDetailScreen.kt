package com.example.gallery.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.gallery.R
import com.example.gallery.data.local.entity.ReferenceProjectEntity
import com.example.gallery.data.model.MediaData
import com.example.gallery.data.repository.ReferenceRepository
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.component.GalleryGridView
import com.example.gallery.ui.component.GalleryTopAppBar
import com.example.gallery.ui.state.GalleryState
import com.example.gallery.ui.theme.GalleryThemeTokens
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun ReferenceDetailScreen(
    projectId: Long,
    galleryState: GalleryState,
    onBack: () -> Unit,
    onAddClick: () -> Unit,
    onGalleryAddClick: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { ReferenceRepository(context) }
    val items by repository.getItemsForProjectFlow(projectId).collectAsState(initial = emptyList())
    var project by remember { mutableStateOf<ReferenceProjectEntity?>(null) }
    val scope = rememberCoroutineScope()
    val colors = GalleryThemeTokens.colors

    LaunchedEffect(projectId) {
        project = repository.getAllProjectsFlow().first().find { it.id == projectId }
    }

    var showFinishConfirm by remember { mutableStateOf(false) }
    var showAddChoices by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val defaultRefTitle = stringResource(R.string.ref_default_item_name)
    val mediaList = remember(items, defaultRefTitle) {
        items.map { refItem ->
            MediaData(
                uri = refItem.localUri ?: refItem.remoteUrl,
                dateAdded = refItem.addedAt,
                fileName = refItem.title.ifBlank { defaultRefTitle }
            )
        }
    }

    Scaffold(
        topBar = {
            GalleryTopAppBar(
                title = project?.title ?: stringResource(R.string.ref_detail_title),
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationContentDescription = stringResource(R.string.btn_back),
                onNavigationClick = onBack,
                centered = true,
                actions = {
                    if (project?.status == AppConstants.STATUS_ACTIVE) {
                        IconButton(onClick = { showFinishConfirm = true }) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.ref_mark_complete), tint = colors.primaryText)
                        }
                    } else if (project?.status == AppConstants.STATUS_FINISHED) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    project?.let {
                                        val updated = it.copy(status = AppConstants.STATUS_ACTIVE)
                                        repository.updateProject(updated)
                                        project = updated
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Brush, contentDescription = stringResource(R.string.ref_resume_project), tint = colors.accent)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (project?.status == AppConstants.STATUS_ACTIVE) {
                FloatingActionButton(
                    onClick = { showAddChoices = true },
                    containerColor = colors.accent,
                    contentColor = colors.background
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.ref_add_reference))
                }
            }
        },
        containerColor = colors.background
    ) { padding ->
        if (mediaList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.ref_no_references), color = colors.mutedText)
            }
        } else {
            GalleryGridView(
                imageList = mediaList,
                onImageClick = { index, _ -> selectedIndex = index },
                galleryState = galleryState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                isFilterEnabled = false,
                showTopSection = false
            )
        }
    }

    if (showFinishConfirm) {
        AlertDialog(
            onDismissRequest = { showFinishConfirm = false },
            title = { Text(stringResource(R.string.ref_complete_project_title)) },
            text = { Text(stringResource(R.string.ref_complete_project_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            project?.let { repository.finishProject(it) }
                            project = project?.copy(status = AppConstants.STATUS_FINISHED)
                            showFinishConfirm = false
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
                TextButton(onClick = { showFinishConfirm = false }) {
                    Text(stringResource(R.string.btn_cancel), color = colors.mutedText)
                }
            }
        )
    }

    if (showAddChoices) {
        AlertDialog(
            onDismissRequest = { showAddChoices = false },
            title = { Text(stringResource(R.string.ref_add_reference)) },
            text = { Text(stringResource(R.string.ref_add_method_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        showAddChoices = false
                        onGalleryAddClick()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.background
                    )
                ) {
                    Text(stringResource(R.string.ref_from_gallery))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddChoices = false
                        onAddClick()
                    }
                ) {
                    Text(stringResource(R.string.ref_from_web), color = colors.mutedText)
                }
            }
        )
    }

    selectedIndex?.let { index ->
        if (index in mediaList.indices) {
            MediaViewerScreen(
                imageList = mediaList,
                initialPage = index,
                onClickedClose = { selectedIndex = null },
                galleryState = null,
                showDeleteButton = false,
                onPageSelected = { selectedIndex = it }
            )
        }
    }
}

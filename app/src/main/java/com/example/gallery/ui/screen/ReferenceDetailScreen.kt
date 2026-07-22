package com.example.gallery.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val colors = GalleryThemeTokens.colors

    LaunchedEffect(projectId) {
        project = repository.getAllProjectsFlow().first().find { it.id == projectId }
    }

    var showAddChoices by remember { mutableStateOf(false) }
    var showUrlInput by remember { mutableStateOf(false) }
    var urlToAdd by remember { mutableStateOf("") }
    var isAddingUrl by remember { mutableStateOf(false) }
    var showUrlLoadFailure by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
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
                navigationIcon = Icons.Default.Close,
                navigationContentDescription = stringResource(R.string.btn_close),
                onNavigationClick = onBack,
                centered = true
            )
        },
        floatingActionButton = {
            if (project?.status == AppConstants.STATUS_ACTIVE) {
                ExtendedFloatingActionButton(
                    onClick = { showAddChoices = true },
                    containerColor = colors.accent,
                    contentColor = colors.background,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.ref_add_reference)) }
                )
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
                showTopSection = false,
                selectionEnabled = false
            )
        }
    }

    if (showAddChoices) {
        ModalBottomSheet(
            onDismissRequest = { showAddChoices = false },
            containerColor = colors.surface
        ) {
            Text(
                text = stringResource(R.string.ref_add_reference),
                color = colors.primaryText,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.ref_from_web)) },
                leadingContent = {
                    Icon(Icons.Default.Language, contentDescription = null, tint = colors.accent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        showAddChoices = false
                        onAddClick()
                    }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.ref_from_url)) },
                leadingContent = {
                    Icon(Icons.Default.Link, contentDescription = null, tint = colors.accent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        showAddChoices = false
                        showUrlInput = true
                    }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.ref_from_gallery)) },
                leadingContent = {
                    Icon(Icons.Default.Image, contentDescription = null, tint = colors.accent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        showAddChoices = false
                        onGalleryAddClick()
                    }
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showUrlInput) {
        AlertDialog(
            onDismissRequest = { if (!isAddingUrl) showUrlInput = false },
            title = { Text(stringResource(R.string.ref_from_url), color = colors.primaryText) },
            text = {
                OutlinedTextField(
                    value = urlToAdd,
                    onValueChange = { urlToAdd = it },
                    label = { Text(stringResource(R.string.ref_url_hint)) },
                    singleLine = true,
                    enabled = !isAddingUrl,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = urlToAdd.isNotBlank() && !isAddingUrl,
                    onClick = {
                        isAddingUrl = true
                        scope.launch {
                            val success = repository.addReferenceFromUrl(projectId, urlToAdd.trim())
                            isAddingUrl = false
                            if (success) {
                                urlToAdd = ""
                                showUrlInput = false
                            } else {
                                showUrlInput = false
                                showUrlLoadFailure = true
                            }
                        }
                    }
                ) {
                    if (isAddingUrl) CircularProgressIndicator() else Text(stringResource(R.string.ref_btn_decide))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlInput = false }, enabled = !isAddingUrl) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showUrlLoadFailure) {
        AlertDialog(
            onDismissRequest = { showUrlLoadFailure = false },
            title = { Text(stringResource(R.string.ref_url_add_failed_title), color = colors.primaryText) },
            text = { Text(stringResource(R.string.ref_url_add_failed_message), color = colors.primaryText) },
            confirmButton = {
                Button(onClick = { showUrlLoadFailure = false }) {
                    Text(stringResource(R.string.btn_ok))
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

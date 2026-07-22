package com.example.gallery.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.example.gallery.R
import com.example.gallery.data.model.MediaData
import com.example.gallery.data.repository.ReferenceRepository
import com.example.gallery.ui.component.GalleryGridView
import com.example.gallery.ui.state.GalleryState
import com.example.gallery.ui.theme.GalleryThemeTokens
import kotlinx.coroutines.launch

@Composable
fun ReferenceGalleryPickerScreen(
    projectId: Long,
    galleryState: GalleryState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val referenceRepository = remember { ReferenceRepository(context) }
    val scope = rememberCoroutineScope()
    val colors = GalleryThemeTokens.colors
    var media by remember { mutableStateOf<List<MediaData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(galleryState.refreshTrigger) {
        isLoading = true
        media = galleryState.repository.getAllMedia().filter { !it.isVideo }
        isLoading = false
    }

    fun confirmSelection() {
        val selectedMedia = media.filter { it.uri in selectedUris }
        if (selectedMedia.isEmpty() || isSaving) return
        isSaving = true
        scope.launch {
            var successCount = 0
            selectedMedia.forEach { selected ->
                val success = referenceRepository.addLocalItemForProject(
                    projectId = projectId,
                    localPath = selected.uri,
                    remoteUrl = "gallery://${selected.uri}",
                    title = selected.fileName.ifBlank { context.getString(R.string.ref_default_title) }
                )
                if (success) successCount++
            }
            Toast.makeText(
                context,
                if (successCount > 0) context.getString(R.string.ref_msg_added_format, successCount) else context.getString(R.string.ref_msg_add_failed),
                Toast.LENGTH_SHORT
            ).show()
            isSaving = false
            if (successCount > 0) onBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = colors.accent
            )
        } else {
            GalleryGridView(
                imageList = media,
                onImageClick = { _, _ -> },
                galleryState = galleryState,
                modifier = Modifier.fillMaxSize(),
                title = stringResource(R.string.ref_picker_title),
                onBackClick = onBack,
                backIcon = Icons.Default.Close,
                backContentDescription = stringResource(R.string.btn_close),
                isFilterEnabled = false,
                showTopSection = true,
                selectOnTap = true,
                onSelectionChanged = { selectedUris = it.toSet() },
                onSelectionModeChanged = { isSelectionMode ->
                    if (!isSelectionMode) selectedUris = emptySet()
                },
                showScrollbarWhenSelecting = true,
                topBarActions = {
                    if (selectedUris.isNotEmpty()) {
                        Text(stringResource(R.string.ref_items_selected, selectedUris.size), color = colors.primaryText)
                        Spacer(modifier = Modifier.width(dimensionResource(R.dimen.spacing_small)))
                    }
                    Button(
                        enabled = selectedUris.isNotEmpty() && !isSaving,
                        onClick = ::confirmSelection
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = colors.primaryText)
                        } else {
                            Text(stringResource(R.string.ref_btn_confirm))
                        }
                    }
                }
            )
        }
    }
}

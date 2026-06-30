package com.example.gallery.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
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
                title = "ギャラリーから選択",
                onBackClick = onBack,
                isFilterEnabled = false,
                showTopSection = true,
                selectOnTap = true,
                onSelectionChanged = { selectedUris = it.toSet() },
                onSelectionModeChanged = { isSelectionMode ->
                    if (!isSelectionMode) selectedUris = emptySet()
                },
                topBarActions = {
                    if (selectedUris.isNotEmpty()) {
                        Text("${selectedUris.size}件選択中", color = colors.primaryText)
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            enabled = !isSaving,
                            onClick = {
                                val selectedMedia = media.filter { it.uri in selectedUris }
                                if (selectedMedia.isEmpty()) return@Button
                                isSaving = true
                                scope.launch {
                                    var successCount = 0
                                    selectedMedia.forEach { selected ->
                                        val success = referenceRepository.addLocalItemForProject(
                                            projectId = projectId,
                                            localPath = selected.uri,
                                            remoteUrl = "gallery://${selected.uri}",
                                            title = selected.fileName.ifBlank { "ギャラリー画像" }
                                        )
                                        if (success) successCount++
                                    }
                                    Toast.makeText(
                                        context,
                                        if (successCount > 0) "${successCount}件追加しました" else "追加に失敗しました",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    isSaving = false
                                    if (successCount > 0) onBack()
                                }
                            }
                        ) {
                            Text("決定")
                        }
                    }
                }
            )
        }
    }
}

package com.example.gallery.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.gallery.data.model.MediaData
import com.example.gallery.ui.component.GalleryGridView
import com.example.gallery.ui.state.GalleryState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationScreen(
    galleryState: GalleryState,
    onShowViewer: () -> Unit,
    onHideViewer: () -> Unit,
    onMenuClick: () -> Unit
) {
    var recommendedMedia by remember { mutableStateOf<List<MediaData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        recommendedMedia = galleryState.repository.getRecommendations(100)
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("おすすめ", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
            } else if (recommendedMedia.isEmpty()) {
                Text("おすすめのデータがまだありません。計測モードで画像を鑑賞してください。", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
            } else {
                GalleryGridView(
                    imageList = recommendedMedia,
                    galleryState = galleryState,
                    onImageClick = { index, list ->
                        galleryState.lastViewedUri = list[index].uri
                        onShowViewer()
                    }
                )
            }
        }
    }
}

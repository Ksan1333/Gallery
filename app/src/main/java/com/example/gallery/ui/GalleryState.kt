package com.example.gallery.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.gallery.data.local.GalleryDatabase
import com.example.gallery.data.repository.MediaRepository
import com.example.gallery.data.repository.ColorTaggingService
import com.example.gallery.ui.component.GroupingMode
import com.example.gallery.ui.component.MediaTypeFilter

enum class GalleryViewMode { FOLDER, MYLIST, COLOR }

class GalleryState(context: Context) {
    private val database = GalleryDatabase.getDatabase(context)
    
    var isMockMode by mutableStateOf(false)
        private set

    fun toggleMockMode() {
        isMockMode = !isMockMode
    }

    val repository: MediaRepository = MediaRepository(
        context,
        database.mediaDao(),
        this
    )
    val colorTaggingService: ColorTaggingService = ColorTaggingService(
        context,
        database.mediaDao()
    )
    val aiTaggingService: com.example.gallery.data.repository.AiTaggingService = com.example.gallery.data.repository.AiTaggingService(
        context,
        database.mediaDao()
    )

    // 永続化を無効化しつつ、セッション内で維持される状態
    var groupingMode by mutableStateOf(GroupingMode.NONE)
    var mediaTypeFilter by mutableStateOf(MediaTypeFilter.ALL)
    var ageRatingFilter by mutableStateOf(com.example.gallery.ui.component.AgeRatingFilter.SFW)
    var deviceFilter by mutableStateOf(com.example.gallery.ui.component.DeviceFilter.ALL)
    var galleryViewMode by mutableStateOf(GalleryViewMode.FOLDER)

    val currentColumnIndex: Int = 4 // 3列
}

@Composable
fun rememberGalleryState(context: Context): GalleryState {
    return remember { GalleryState(context) }
}

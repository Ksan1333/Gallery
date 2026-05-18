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

enum class GalleryViewMode { FOLDER, MYLIST, COLOR }
enum class GroupingMode { NONE, DAY, MONTH }
enum class MediaTypeFilter { ALL, IMAGE, VIDEO, GIF }
enum class AgeRatingFilter { ALL, SFW, R15, R18 }
enum class DeviceFilter { ALL, SMARTPHONE, PC }

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
    var ageRatingFilter by mutableStateOf(AgeRatingFilter.SFW)
    var deviceFilter by mutableStateOf(DeviceFilter.ALL)
    var galleryViewMode by mutableStateOf(GalleryViewMode.FOLDER)

    val currentColumnIndex: Int = 4 // 3列
}

@Composable
fun rememberGalleryState(context: Context): GalleryState {
    return remember { GalleryState(context) }
}

package com.example.gallery.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.gallery.data.local.GalleryDatabase
import com.example.gallery.data.repository.MediaRepository
import com.example.gallery.data.repository.ColorTaggingService
import com.example.gallery.data.repository.AiTaggingService

enum class GalleryViewMode { FOLDER, MYLIST, COLOR }
enum class GroupingMode { NONE, DAY, MONTH, YEAR, STORAGE }
enum class MediaTypeFilter { ALL, IMAGE, VIDEO, GIF }
enum class AgeRatingFilter { ALL, SFW, R15, R18 }
enum class DeviceFilter { ALL, SMARTPHONE, PC }
enum class SortMode { DATE_ADDED, SIZE, NAME }

class GalleryState(context: Context) {
    private val database = GalleryDatabase.getDatabase(context)
    
    var isMockMode by mutableStateOf(false)
        private set

    fun toggleMockMode() {
        isMockMode = !isMockMode
        repository.clearMockData()
    }

    fun refreshTriggerFlow(): kotlinx.coroutines.flow.Flow<Int> = androidx.compose.runtime.snapshotFlow { refreshTrigger }

    val repository: MediaRepository = MediaRepository(context, database.mediaDao(), this)
    val colorTaggingService: ColorTaggingService = ColorTaggingService(context, repository)
    val aiTaggingService: AiTaggingService = AiTaggingService(context, repository)

    var groupingMode by mutableStateOf(GroupingMode.NONE)
    var mediaTypeFilter by mutableStateOf(MediaTypeFilter.ALL)
    var ageRatingFilter by mutableStateOf(AgeRatingFilter.SFW)
    var deviceFilter by mutableStateOf(DeviceFilter.ALL)
    var galleryViewMode by mutableStateOf(GalleryViewMode.FOLDER)
    var sortMode by mutableStateOf(SortMode.DATE_ADDED)
    var isAscending by mutableStateOf(false)
    var videoSeekInterval by mutableIntStateOf(10)

    var urisToMove by mutableStateOf<List<String>>(emptyList())
    var selectedFolderForMove by mutableStateOf("")

    var refreshTrigger by mutableIntStateOf(0)
        private set

    // 操作状態
    var isZooming by mutableStateOf(false)
    var lastViewedUri by mutableStateOf<String?>(null)

    fun refresh() {
        refreshTrigger++
    }

    val currentColumnIndex: Int = 4
}

@Composable
fun rememberGalleryState(context: Context): GalleryState {
    return remember { GalleryState(context) }
}

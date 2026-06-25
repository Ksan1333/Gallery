package com.example.gallery.ui.state

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.gallery.data.local.GalleryDatabase
import com.example.gallery.data.repository.MediaRepository
import com.example.gallery.data.service.AiTaggingService
import com.example.gallery.data.service.VectorSearchService

enum class GalleryViewMode { FOLDER, MYLIST, TRASH }
enum class GroupingMode { NONE, DAY, MONTH, YEAR, STORAGE }
enum class MediaTypeFilter { ALL, IMAGE, VIDEO, GIF }
enum class AgeRatingFilter { ALL, SFW, R15, R18 }
enum class DeviceFilter { ALL, SMARTPHONE, PC }
enum class SortMode { DATE_ADDED, SIZE, NAME }

class GalleryState(context: Context) {
    private val database = GalleryDatabase.getDatabase(context)
    
    init {
        com.example.gallery.service.TagTranslationService.init(context)
    }

    var navController: androidx.navigation.NavHostController? = null

    fun refreshTriggerFlow(): kotlinx.coroutines.flow.Flow<Int> = androidx.compose.runtime.snapshotFlow { refreshTrigger }

    val repository: MediaRepository = MediaRepository(context, database.mediaDao(), this)
    val aiTaggingService: AiTaggingService = AiTaggingService(context, repository)
    val vectorSearchService: VectorSearchService = VectorSearchService(context, repository)

    var groupingMode by mutableStateOf(GroupingMode.NONE)
    var mediaTypeFilter by mutableStateOf(MediaTypeFilter.ALL)
    var ageRatingFilter by mutableStateOf(AgeRatingFilter.ALL)
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
    var isSelectionMode by mutableStateOf(false)
    var isMeasureModeActive by mutableStateOf(false)
    var lastViewedUri by mutableStateOf<String?>(null)
    var hasHomeGalleryScrollPosition by mutableStateOf(false)
    var homeGalleryScrollIndex by mutableIntStateOf(0)
    var homeGalleryScrollOffset by mutableIntStateOf(0)
    var homeGalleryScrollUri by mutableStateOf<String?>(null)

    fun refresh() {
        refreshTrigger++
    }

    val currentColumnIndex: Int = 4
}

@Composable
fun rememberGalleryState(context: Context): GalleryState {
    return remember { GalleryState(context) }
}

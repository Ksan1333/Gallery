package com.example.gallery.ui.state

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.gallery.data.local.GalleryDatabase
import com.example.gallery.data.model.MediaData
import com.example.gallery.data.repository.MediaRepository
import com.example.gallery.data.service.AiTaggingService
import com.example.gallery.data.service.VectorSearchService

enum class GalleryViewMode { FOLDER, TRASH, VIDEO }
enum class GroupingMode { NONE, DAY, MONTH, YEAR, STORAGE }
enum class MediaTypeFilter { ALL, IMAGE, VIDEO, GIF }
enum class AgeRatingFilter { ALL, SFW, R15, R18 }
enum class DeviceFilter { ALL, SMARTPHONE, PC }
enum class SortMode { DATE_ADDED, SIZE, NAME }
enum class GallerySearchMatchMode { AND, OR }
enum class GallerySearchMediaType { IMAGE, GIF, VIDEO }
enum class GallerySearchStorageType { INTERNAL, SD_CARD }

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
    var videoHomeRequestToken by mutableIntStateOf(0)
        private set
    var cachedVideoItems by mutableStateOf<List<MediaData>?>(null)
    var cachedVideoRefreshTrigger by mutableIntStateOf(-1)

    var urisToMove by mutableStateOf<List<String>>(emptyList())
    var selectedFolderForMove by mutableStateOf("")

    var refreshTrigger by mutableIntStateOf(0)
        private set

    // 操作状態
    var isZooming by mutableStateOf(false)
    var isMediaViewerOpen by mutableStateOf(false)
    var isSelectionMode by mutableStateOf(false)
    var lastViewedUri by mutableStateOf<String?>(null)
    var activeMediaViewerList by mutableStateOf<List<MediaData>>(emptyList())
    var activeMediaViewerIndex by mutableStateOf<Int?>(null)
    var hasHomeGalleryScrollPosition by mutableStateOf(false)
    var homeGalleryScrollIndex by mutableIntStateOf(0)
    var homeGalleryScrollOffset by mutableIntStateOf(0)
    var homeGalleryScrollUri by mutableStateOf<String?>(null)
    var pendingHomeSearchTag by mutableStateOf<String?>(null)
    var homeSearchQuery by mutableStateOf("")
    var homeSearchMatchMode by mutableStateOf(GallerySearchMatchMode.AND)
    var homeSearchAgeRatings by mutableStateOf<Set<AgeRatingFilter>>(emptySet())
    var homeSearchFolders by mutableStateOf<Set<String>>(emptySet())
    var homeSearchMediaTypes by mutableStateOf<Set<GallerySearchMediaType>>(emptySet())
    var homeSearchStorageTypes by mutableStateOf<Set<GallerySearchStorageType>>(emptySet())
    var homeSearchFavoritesOnly by mutableStateOf(false)
    var homeSearchTags by mutableStateOf<Set<String>>(emptySet())
    var homeFavoritesOnly by mutableStateOf(false)

    val isHomeSearchActive: Boolean
        get() = homeSearchQuery.isNotBlank() ||
            homeSearchTags.isNotEmpty() ||
            homeSearchFolders.isNotEmpty() ||
            homeSearchAgeRatings.isNotEmpty() ||
            homeSearchMediaTypes.isNotEmpty() ||
            homeSearchStorageTypes.isNotEmpty() ||
            homeSearchFavoritesOnly

    fun clearHomeSearch() {
        homeSearchQuery = ""
        homeSearchMatchMode = GallerySearchMatchMode.AND
        homeSearchAgeRatings = emptySet()
        homeSearchFolders = emptySet()
        homeSearchMediaTypes = emptySet()
        homeSearchStorageTypes = emptySet()
        homeSearchFavoritesOnly = false
        homeSearchTags = emptySet()
    }

    fun refresh() {
        refreshTrigger++
        cachedVideoItems = null
        cachedVideoRefreshTrigger = -1
    }

    fun requestVideoHome() {
        videoHomeRequestToken++
    }

    val currentColumnIndex: Int = 4
}

@Composable
fun rememberGalleryState(context: Context): GalleryState {
    return remember { GalleryState(context) }
}

package com.example.gallery.ui

object AppRoutes {
    const val HOME = "home"
    const val FOLDERS = "folders"
    const val BOOKS = "books"
    const val VIDEOS = "videos"
    const val TRASH = "trash"
    const val REFERENCES = "references"
    const val VIDEO_DOWNLOADER = "video_downloader"
    const val FAVORITE_ARTISTS = "favorite_artists"
    const val FAVORITE_SITES = "favorite_sites"
    const val SETTINGS = "settings"
    const val MEDIA_VIEWER_SETTINGS = "settings/media_viewer"
    const val BOOK_VIEWER_SETTINGS = "settings/book_viewer"
    const val VIDEO_VIEWER_SETTINGS = "settings/video_viewer"
    const val SEARCH = "search"
    const val ABOUT = "about"
    const val MASS_EDIT = "mass_edit"
    const val BULK_MOVE_SELECTION = "bulk_move_selection"
    const val FOLDERS_SELECT = "folders_select"
    const val BOOK_BOOKMARKS = "book_bookmarks"
    const val REFERENCE_DETAIL_PATTERN = "reference_detail/{projectId}"
    const val REFERENCE_SEARCH_PATTERN = "reference_search/{projectId}"
    const val ANALYSIS_PATTERN = "analysis/{type}/{periodDays}"

    fun referenceDetail(projectId: Long) = "reference_detail/$projectId"
    fun referenceSearch(projectId: Long) = "reference_search/$projectId"
    fun analysis(
        type: String = AppDefaults.ANALYSIS_TYPE_AI_TAGGING,
        periodDays: Int = AppDefaults.ANALYSIS_PERIOD_ALL
    ) = "analysis/$type/$periodDays"
}

object AppDefaults {
    const val ANALYSIS_TYPE_AI_TAGGING = "AI_TAGGING"
    const val ANALYSIS_PERIOD_ALL = -1
    const val AI_ANALYSIS_SPEED_MODE_KEY = "aiAnalysisSpeedMode"
    const val AI_ANALYSIS_SPEED_BALANCED = "BALANCED"
    const val AI_ANALYSIS_SPEED_FAST = "FAST"
    const val AI_ANALYSIS_SPEED_ACCURACY = "ACCURACY"
    const val AI_TAGGER_MODEL_KEY = "aiTaggerModel"
    const val AI_TAGGER_MODEL_NORMAL = "NORMAL"
    const val AI_TAGGER_MODEL_HIGH = "HIGH"
    const val CONTROL_PANEL_AUTO_HIDE_MS = 3200
    const val SELECTION_LONG_PRESS_MS = 500
    const val DRAWER_WIDTH_DP = 260
    const val DRAWER_ITEM_HEIGHT_DP = 44
    const val DRAWER_EDGE_HIT_WIDTH_DP = 25

    fun normalizedAiTaggerModel(model: String?): String {
        return when (model) {
            AI_TAGGER_MODEL_HIGH -> AI_TAGGER_MODEL_HIGH
            else -> AI_TAGGER_MODEL_NORMAL
        }
    }

    fun aiTaggerModelRank(model: String?): Int {
        return when (normalizedAiTaggerModel(model)) {
            AI_TAGGER_MODEL_HIGH -> 2
            AI_TAGGER_MODEL_NORMAL -> 1
            else -> 0
        }
    }
}

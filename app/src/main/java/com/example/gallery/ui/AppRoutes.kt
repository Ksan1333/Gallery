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
    const val RECOMMENDATIONS = "recommendations"
    const val GOOGLE_PHOTOS = "google_photos"
    const val SETTINGS = "settings"
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
    fun analysis(type: String = AppDefaults.ANALYSIS_TYPE_AI_TAGGING, periodDays: Int = AppDefaults.ANALYSIS_PERIOD_ALL) =
        "analysis/$type/$periodDays"
}

object AppText {
    const val APP_NAME = "Gallery"
    const val DRAWER_TITLE = "ギャラリーメニュー"
    const val DRAWER_BASIC = "基本機能"
    const val DRAWER_TOOLS = "便利機能"
    const val DRAWER_INFO = "情報"
    const val HOME = "ホーム"
    const val ALL_MEDIA = "すべて"
    const val FOLDERS = "フォルダ"
    const val BOOKS = "本"
    const val VIDEOS = "動画"
    const val TRASH = "ゴミ箱"
    const val VIDEO_DOWNLOAD = "動画DL"
    const val FAVORITE_ARTISTS = "お気に入りクリエイター"
    const val FAVORITE_SITES = "項目"
    const val REFERENCES = "お絵描き資料"
    const val SETTINGS = "項目"
    const val ABOUT = "このアプリについて"
    const val GUIDE = "項目"
    const val SEARCH = "検索"
    const val ANALYSIS = "分析"
    const val FAVORITES = "項目"
}

object AppDefaults {
    const val ANALYSIS_TYPE_AI_TAGGING = "AI_TAGGING"
    const val ANALYSIS_PERIOD_ALL = -1
    const val DRAWER_WIDTH_DP = 260
    const val DRAWER_ITEM_HEIGHT_DP = 44
    const val DRAWER_EDGE_HIT_WIDTH_DP = 25
}

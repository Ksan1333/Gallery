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
    const val BOOK_VIEWER_SETTINGS = "settings/book_viewer"
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

object AppText {
    const val APP_NAME = "Gallery"
    const val DRAWER_TITLE = "ギャラリーメニュー"
    const val DRAWER_BASIC = "基本機能"
    const val DRAWER_TOOLS = "便利機能"
    const val DRAWER_INFO = "設定"
    const val HOME = "ホーム"
    const val ALL_MEDIA = "すべて"
    const val FOLDERS = "フォルダ"
    const val BOOKS = "本"
    const val VIDEOS = "動画"
    const val TRASH = "ゴミ箱"
    const val VIDEO_DOWNLOAD = "動画DL"
    const val FAVORITE_ARTISTS = "お気に入りクリエイター"
    const val FAVORITE_SITES = "お気に入りサイト"
    const val REFERENCES = "お絵描き資料"
    const val SETTINGS = "設定"
    const val ABOUT = "このアプリについて"
    const val GUIDE = "チュートリアル"
    const val SEARCH = "検索"
    const val ANALYSIS = "分析"
    const val FAVORITES = "お気に入り"
    const val SORT = "並び替え"
    const val DATE_ADDED = "追加日"
    const val SIZE = "サイズ"
    const val NAME = "名前"
    const val ASCENDING = "昇順"
    const val DESCENDING = "降順"
    const val FAVORITES_ONLY = "お気に入りのみ"
    const val CANCEL = "キャンセル"
    const val OK = "OK"
    const val UNLOCK = "解除"
    const val MOVING_ITEMS = "アイテムを移動中..."
    const val INTERRUPT = "中断"
    const val OTHER_TASKS_IN_PROGRESS_PREFIX = "ほか"
    const val OTHER_TASKS_IN_PROGRESS_SUFFIX = "件のタスクが進行中..."
}

object AppDefaults {
    const val ANALYSIS_TYPE_AI_TAGGING = "AI_TAGGING"
    const val ANALYSIS_PERIOD_ALL = -1
    const val CONTROL_PANEL_AUTO_HIDE_MS = 3200
    const val DRAWER_WIDTH_DP = 260
    const val DRAWER_ITEM_HEIGHT_DP = 44
    const val DRAWER_EDGE_HIT_WIDTH_DP = 25
}

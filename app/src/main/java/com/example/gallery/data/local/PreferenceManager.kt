package com.example.gallery.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class PreferenceManager(context: Context) {
    private val appPrefs: SharedPreferences = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    private val globalPrefs: SharedPreferences = context.getSharedPreferences(GLOBAL_SETTINGS_PREFS, Context.MODE_PRIVATE)
    private val bookViewerPrefs: SharedPreferences = context.getSharedPreferences(BOOK_VIEWER_PREFS, Context.MODE_PRIVATE)
    private val bookBookmarksPrefs: SharedPreferences = context.getSharedPreferences(BOOK_BOOKMARKS_PREFS, Context.MODE_PRIVATE)

    companion object {
        private const val APP_PREFS = "app_prefs"
        private const val GLOBAL_SETTINGS_PREFS = "global_settings"
        private const val BOOK_VIEWER_PREFS = "book_viewer_settings"
        private const val BOOK_BOOKMARKS_PREFS = "book_bookmarks"

        // Keys
        const val THEME_MODE = "theme_mode"
        const val TEXT_SCALE = "text_scale"
        const val STARTUP_SCREEN = "startupScreen"
        const val CUSTOM_PALETTE_ENABLED = "custom_palette_enabled"
        const val CUSTOM_PALETTE_PREFIX = "custom_palette_"
        const val TUTORIAL_SETUP_DONE = "tutorial_setup_done"

        // Global Settings Keys
        const val SEARCH_HISTORY = "homeSearchHistory"
        const val SEARCH_HISTORY_LIMIT = "searchHistoryLimit"
        const val LOW_MEMORY_MODE = "lowMemoryMode"
        const val CONTROL_PANEL_AUTO_HIDE = "controlPanelAutoHideMs"
        const val TOUCH_INDICATOR = "touchIndicator"
        const val TAP_ZONE_LAYOUT = "tapZoneLayout"
        const val SHOW_CLOCK_BATTERY = "showClockBattery"
        const val SIMILAR_IMAGE_GROUPING = "similarImageGroupingEnabled"
        const val SIMILAR_IMAGE_THRESHOLD = "similarImageThreshold"
        const val FOLDER_GROUPS_DATA = "folderGroupsData"
        const val EDGE_SWIPE_FOR_DRAWER = "edgeSwipeForDrawer"
        const val RECOMMENDATION_PANEL_SIZE = "recommendationPanelSize"
    }

    // App Prefs
    fun getString(key: String, defaultValue: String? = null): String? = appPrefs.getString(key, defaultValue)
    fun setString(key: String, value: String) = appPrefs.edit { putString(key, value) }

    fun getFloat(key: String, defaultValue: Float = 1f): Float {
        return when (val value = appPrefs.all[key]) {
            is Float -> value
            is Int -> value.toFloat()
            is Long -> value.toFloat()
            is Double -> value.toFloat()
            is String -> value.toFloatOrNull() ?: defaultValue
            else -> defaultValue
        }
    }
    fun setFloat(key: String, value: Float) = appPrefs.edit { putFloat(key, value) }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean = appPrefs.getBoolean(key, defaultValue)
    fun setBoolean(key: String, value: Boolean) = appPrefs.edit { putBoolean(key, value) }

    fun getInt(key: String, defaultValue: Int = 0): Int = appPrefs.getInt(key, defaultValue)
    fun setInt(key: String, value: Int) = appPrefs.edit { putInt(key, value) }

    fun remove(key: String) = appPrefs.edit { remove(key) }
    fun clearAppPrefs() = appPrefs.edit { clear() }

    // Global Prefs
    fun getGlobalString(key: String, defaultValue: String? = null): String? = globalPrefs.getString(key, defaultValue)
    fun setGlobalString(key: String, value: String) = globalPrefs.edit { putString(key, value) }
    
    fun getGlobalBoolean(key: String, defaultValue: Boolean = false): Boolean = globalPrefs.getBoolean(key, defaultValue)
    fun setGlobalBoolean(key: String, value: Boolean) = globalPrefs.edit { putBoolean(key, value) }

    fun getGlobalInt(key: String, defaultValue: Int = 0): Int = globalPrefs.getInt(key, defaultValue)
    fun setGlobalInt(key: String, value: Int) = globalPrefs.edit { putInt(key, value) }
    
    fun clearGlobalPrefs() = globalPrefs.edit { clear() }

    // Book Viewer Prefs
    fun getBookViewerString(key: String, defaultValue: String? = null): String? = bookViewerPrefs.getString(key, defaultValue)
    fun setBookViewerString(key: String, value: String) = bookViewerPrefs.edit { putString(key, value) }
    
    fun getBookViewerBoolean(key: String, defaultValue: Boolean = false): Boolean = bookViewerPrefs.getBoolean(key, defaultValue)
    fun setBookViewerBoolean(key: String, value: Boolean) = bookViewerPrefs.edit { putBoolean(key, value) }
    
    fun clearBookViewerPrefs() = bookViewerPrefs.edit { clear() }

    // Book Bookmarks Prefs
    fun getBookmark(id: String): String? = bookBookmarksPrefs.getString(id, null)
    fun setBookmark(id: String, data: String) = bookBookmarksPrefs.edit { putString(id, data) }
    fun removeBookmark(id: String) = bookBookmarksPrefs.edit { remove(id) }
    fun getAllBookmarks(): Map<String, *> = bookBookmarksPrefs.all
}

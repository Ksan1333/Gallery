package com.example.gallery.service

import android.content.Context
import android.util.Log
import com.example.gallery.ui.AppConstants
import com.example.gallery.data.local.GalleryDatabase
import java.io.File
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

object TagTranslationService {
    private const val TAG = "TagTranslationService"
    private var database: GalleryDatabase? = null
    private val manualOverrides = ConcurrentHashMap<String, String>()

    fun init(context: Context) {
        if (database == null) {
            database = GalleryDatabase.getDatabase(context)
        }
        
        loadManualOverrides(context)
    }

    private fun loadManualOverrides(context: Context) {
        try {
            // 1. Assetsフォルダ（プロジェクトファイル）から読み込み
            val assetFileName = "tag_overrides.json"
            val assets = context.assets.list("") ?: emptyArray()
            
            manualOverrides.clear()
            
            if (assets.contains(assetFileName)) {
                context.assets.open(assetFileName).bufferedReader().use {
                    val json = JSONObject(it.readText())
                    json.keys().forEach { key ->
                        manualOverrides[key] = json.getString(key)
                    }
                }
                Log.d(TAG, "Loaded ${manualOverrides.size} overrides from assets")
            }

            // 2. 内部ストレージ（実行時の上書き用）からも読み込み（あれば）
            val file = File(context.filesDir, assetFileName)
            if (file.exists()) {
                val json = JSONObject(file.readText())
                json.keys().forEach { key ->
                    manualOverrides[key] = json.getString(key)
                }
                Log.d(TAG, "Merged overrides from internal storage")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load manual overrides", e)
        }
    }

    fun translate(text: String): String {
        val cleanText = text.replace("_", " ")
        
        // 0. ユーザー設定（マニュアル）を最優先
        manualOverrides[text]?.let { return it }
        manualOverrides[cleanText]?.let { return it }

        // 自動翻訳は削除されたため、そのまま返す
        return cleanText
    }

    fun close() {
        // Nothing to close now that ML Kit is gone
    }
}

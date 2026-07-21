package com.example.gallery.service

import android.content.Context
import android.util.Log
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
            manualOverrides.clear()
            
            // 1. Assetsフォルダから読み込み (tag.json -> tag_overrides.json の順で上書き)
            val assetFiles = listOf("tag.json", "tag_overrides.json", "tag_override.json")
            val assets = context.assets.list("") ?: emptyArray()
            
            assetFiles.forEach { assetFileName ->
                if (assets.contains(assetFileName)) {
                    try {
                        context.assets.open(assetFileName).bufferedReader(Charsets.UTF_8).use {
                            val json = JSONObject(it.readText())
                            json.keys().forEach { key ->
                                manualOverrides[key] = json.getString(key)
                            }
                        }
                        Log.d(TAG, "Loaded overrides from assets: $assetFileName")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load $assetFileName from assets", e)
                    }
                }
            }

            // 2. 外部ストレージ/内部ストレージからも読み込み (さらに上書き)
            val externalFiles = listOf(
                File(context.filesDir, "tag.json"),
                File(context.filesDir, "tag_overrides.json"),
                File(context.filesDir, "tag_override.json"),
                File("/sdcard/Android/data/${context.packageName}/files/tag.json"),
                File("/sdcard/Android/data/${context.packageName}/files/tag_overrides.json")
            )

            externalFiles.forEach { file ->
                if (file.exists()) {
                    try {
                        val json = JSONObject(file.readText(Charsets.UTF_8))
                        json.keys().forEach { key ->
                            manualOverrides[key] = json.getString(key)
                        }
                        Log.d(TAG, "Merged overrides from file: ${file.absolutePath}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse $file", e)
                    }
                }
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

    /**
     * Normalizes Japanese kana so that hiragana input also matches katakana tags
     * (and vice versa), while keeping ordinary text searches case-insensitive.
     */
    fun matchesSearch(text: String, query: String): Boolean {
        val normalizedQuery = normalizeKanaForSearch(query)
        return normalizedQuery.isBlank() || normalizeKanaForSearch(text).contains(normalizedQuery)
    }

    private fun normalizeKanaForSearch(value: String): String = buildString(value.length) {
        value.forEach { character ->
            append(
                if (character in '\u3041'..'\u3096') {
                    (character.code + 0x60).toChar()
                } else {
                    character
                }
            )
        }
    }.lowercase()

    fun close() {
        // Nothing to close now that ML Kit is gone
    }
}

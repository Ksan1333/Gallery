package com.example.gallery.service

import android.content.Context
import android.util.Log
import com.example.gallery.ui.AppConstants
import com.example.gallery.data.local.GalleryDatabase
import com.example.gallery.data.local.entity.TagTranslationEntity
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import java.io.File
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

object TagTranslationService {
    private const val TAG = "TagTranslationService"
    private var translator: Translator? = null
    private var database: GalleryDatabase? = null
    private val cache = ConcurrentHashMap<String, String>()
    private var isModelDownloaded = false
    private val manualOverrides = ConcurrentHashMap<String, String>()

    fun init(context: Context) {
        if (database == null) {
            database = GalleryDatabase.getDatabase(context)
        }
        
        loadManualOverrides(context)

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.JAPANESE)
            .build()
        translator = Translation.getClient(options)

        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()
        
        translator?.downloadModelIfNeeded(conditions)
            ?.addOnSuccessListener {
                isModelDownloaded = true
                Log.d(TAG, "Translation model downloaded")
            }
            ?.addOnFailureListener { e ->
                Log.e(TAG, "Failed to download translation model", e)
            }
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

    suspend fun translate(text: String): String {
        val cleanText = text.replace("_", " ")
        
        // 0. ユーザー設定（マニュアル）を最優先
        manualOverrides[text]?.let { return it }
        manualOverrides[cleanText]?.let { return it }

        // 1. ハードコードされたマップを確認
        AppConstants.TagTranslationMap[text]?.let { return it }
        AppConstants.TagTranslationMap[cleanText]?.let { return it }

        // 2. メモリキャッシュを確認
        cache[cleanText]?.let { return it }

        // 3. データベースを確認 (永続化)
        val dbResult = database?.mediaDao()?.getTagTranslation(cleanText)
        if (dbResult != null) {
            cache[cleanText] = dbResult
            return dbResult
        }

        // 4. ML Kit で翻訳試行
        if (isModelDownloaded && translator != null) {
            try {
                val result = translator!!.translate(cleanText).await()
                if (result.isNotBlank()) {
                    cache[cleanText] = result
                    // データベースに保存
                    database?.mediaDao()?.insertTagTranslation(TagTranslationEntity(cleanText, result))
                    return result
                }
            } catch (e: Exception) {
                Log.w(TAG, "Translation failed for: $cleanText", e)
            }
        }

        return cleanText
    }

    fun close() {
        translator?.close()
    }

    /**
     * AIモデルが持つすべてのタグとその現在の翻訳をJSON形式で出力する。
     * tag_overrides.json を一括作成するためのベースとして利用可能。
     */
    suspend fun exportAllAiTagsToJson(context: Context): String = withContext(Dispatchers.IO) {
        try {
            val tagsFile = com.example.gallery.util.ModelDownloader.getDanbooruTagsFile(context)
            if (!tagsFile.exists()) return@withContext "{ \"error\": \"Tags file not found\" }"
            
            val allTags = tagsFile.readLines()
                .drop(1)
                .map { line ->
                    val parts = line.split(",")
                    if (parts.size >= 2) parts[1] else ""
                }
                .filter { it.isNotEmpty() }
            
            val json = JSONObject()
            // 順序を維持するために JSONArray を使うか、JSONObject に順番に追加
            allTags.forEach { tag ->
                val translated = translate(tag)
                json.put(tag, translated)
            }
            
            json.toString(2)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export all AI tags", e)
            "{ \"error\": \"${e.localizedMessage}\" }"
        }
    }

    /**
     * 現在登録されているすべてのタグとその翻訳をYAML形式で出力する。
     * ユーザーが tag_overrides.json を作成するための雛形として利用可能。
     */
    suspend fun exportTagsToYaml(context: Context): String = withContext(Dispatchers.IO) {
        val dao = database?.mediaDao() ?: return@withContext ""
        val tags = dao.getAllTags().first()
        val builder = StringBuilder()
        
        builder.append("# Gallery Tag Translation Export\n")
        builder.append("# JSON format for tag_overrides.json:\n")
        builder.append("# {\n")
        
        tags.forEachIndexed { index, tag ->
            val translated = translate(tag)
            builder.append("  \"$tag\": \"$translated\"")
            if (index < tags.size - 1) builder.append(",")
            builder.append("\n")
        }
        
        builder.append("}\n")
        builder.toString()
    }
}

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
import java.util.concurrent.ConcurrentHashMap

object TagTranslationService {
    private const val TAG = "TagTranslationService"
    private var translator: Translator? = null
    private var database: GalleryDatabase? = null
    private val cache = ConcurrentHashMap<String, String>()
    private var isModelDownloaded = false

    fun init(context: Context) {
        if (database == null) {
            database = GalleryDatabase.getDatabase(context)
        }

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

    suspend fun translate(text: String): String {
        val cleanText = text.replace("_", " ")
        
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
}

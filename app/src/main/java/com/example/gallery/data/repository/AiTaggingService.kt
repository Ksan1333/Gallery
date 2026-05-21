package com.example.gallery.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.gallery.data.local.entity.MediaMetadataEntity
import com.example.gallery.data.local.entity.TagEntity
import com.example.gallery.ui.MediaData
import com.example.gallery.util.ModelDownloader
import com.example.gallery.ui.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import java.util.Collections

class AiTaggingService(
    private val context: Context,
    private val repository: MediaRepository,
) {
    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var tags: List<String> = emptyList()
    private val threshold = 0.60f // 60%以上に設定
    
    // バッファの再利用
    private val imgDataBuffer = FloatBuffer.allocate(1 * 448 * 448 * 3)

    init {
        try {
            val modelFile = ModelDownloader.getDanbooruModelFile(context)
            val tagsFile = ModelDownloader.getDanbooruTagsFile(context)
            
            if (modelFile.exists() && tagsFile.exists()) {
                ortEnv = OrtEnvironment.getEnvironment()
                
                // NNAPI (ハードウェアアクセラレーション) を有効化
                val options = OrtSession.SessionOptions().apply {
                    addConfigEntry("session.load_model_format", "ONNX")
                    try {
                        addNnapi() // Androidのハードウェア加速
                    } catch (e: Exception) {
                        Log.w("AiTaggingService", "NNAPI not available, falling back to CPU", e)
                    }
                }
                ortSession = ortEnv?.createSession(modelFile.absolutePath, options)
                
                // selected_tags.csv の読み込み
                // format: tag_id,name,category,count
                tags = tagsFile.readLines()
                    .drop(1) // ヘッダーをスキップ
                    .map { line ->
                        val parts = line.split(",")
                        if (parts.size >= 2) parts[1] else ""
                    }
                    .filter { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            Log.e("AiTaggingService", "Failed to initialize SmilingWolf Tagger", e)
        }
    }

    suspend fun analyzeSingle(media: MediaData) = withContext(Dispatchers.IO) {
        if (media.isVideo || ortSession == null || tags.isEmpty()) return@withContext

        try {
            val bitmap = decodeBitmap(media.uri) ?: return@withContext
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 448, 448, true) // WD Tagger v3 は通常448x448
            
            val scores = runInference(resizedBitmap)
            
            val detectedTags = mutableMapOf<String, Float>()
            var ageRating = "SFW"

            // SmilingWolfのタグは非常に多いため、カテゴリ等も考慮可能だが、一旦簡易的に
            // ratingタグ(general, sensitive, questionable, explicit)が含まれている
            val ratingTags = mapOf(
                "rating:general" to "SFW",
                "rating:sensitive" to "SFW",
                "rating:questionable" to "R15",
                "rating:explicit" to "R18"
            )

            scores.forEachIndexed { index, score ->
                if (score >= threshold) {
                    val tagName = tags.getOrNull(index) ?: return@forEachIndexed
                    
                    // 1. 年齢制限の判定
                    if (ratingTags.containsKey(tagName)) {
                        val currentRating = ratingTags[tagName]!!
                        if (ageRating == "SFW") ageRating = currentRating
                        else if (ageRating == "R15" && currentRating == "R18") ageRating = "R18"
                    } else {
                        // 2. センシティブワードによる自動年齢制限
                        val cleanTagName = tagName.replace("_", " ")
                        if (AppConstants.R18Keywords.any { cleanTagName.contains(it, ignoreCase = true) }) {
                            ageRating = "R18"
                        } else if (ageRating != "R18" && AppConstants.R15Keywords.any { cleanTagName.contains(it, ignoreCase = true) }) {
                            ageRating = "R15"
                        }

                        // 3. 日本語翻訳 (マップにない場合はアンダースコアを除去してそのまま)
                        val translatedTag = AppConstants.TagTranslationMap[tagName] ?: cleanTagName
                        detectedTags[translatedTag] = score
                    }
                }
            }

            // タグの保存
            detectedTags.forEach { (tagName, score) ->
                repository.saveTag(TagEntity(media.uri, tagName, score))
            }

            repository.updateAiAnalysisResult(
                uri = media.uri,
                ageRating = ageRating,
                isAiAnalyzed = true
            )
            resizedBitmap.recycle()
            if (bitmap != resizedBitmap) bitmap.recycle()
        } catch (e: Exception) {
            Log.e("AiTaggingService", "Error in SmilingWolf analysis: ${media.uri}", e)
        }
    }

    private fun runInference(bitmap: Bitmap): FloatArray {
        imgDataBuffer.rewind()

        val pixels = IntArray(448 * 448)
        bitmap.getPixels(pixels, 0, 448, 0, 0, 448, 448)

        for (i in 0 until 448 * 448) {
            val pixel = pixels[i]
            imgDataBuffer.put((pixel shr 16 and 0xFF).toFloat())
            imgDataBuffer.put((pixel shr 8 and 0xFF).toFloat())
            imgDataBuffer.put((pixel and 0xFF).toFloat())
        }
        imgDataBuffer.rewind()

        val inputTensor = OnnxTensor.createTensor(ortEnv, imgDataBuffer, longArrayOf(1, 448, 448, 3))
        val output = ortSession?.run(Collections.singletonMap("input", inputTensor))
        
        val result = output?.get(0)?.value as? Array<FloatArray>
        inputTensor.close()
        output?.close()

        return result?.get(0) ?: FloatArray(0)
    }

    private fun decodeBitmap(uriString: String): Bitmap? {
        return try {
            val context = context
            val uri = Uri.parse(uriString)
            
            // 1. サイズだけを取得して inSampleSize を計算
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            // WD Tagger v3 は 448x448 なので、それに近いサイズまで縮小してデコード
            var inSampleSize = 1
            if (options.outHeight > 448 || options.outWidth > 448) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / inSampleSize >= 448 && halfWidth / inSampleSize >= 448) {
                    inSampleSize *= 2
                }
            }

            // 2. 実際にデコード
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }
        } catch (_: Exception) { null }
    }

    fun close() {
        ortSession?.close()
        ortEnv?.close()
    }
}

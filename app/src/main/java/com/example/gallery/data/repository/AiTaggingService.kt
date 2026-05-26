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
import com.example.gallery.service.TagTranslationService
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
        // 起動時の初期化を停止。必要時に ensureInitialized() を呼ぶ
    }

    fun ensureInitialized() {
        if (ortSession != null) return

        // 翻訳サービスの初期化
        TagTranslationService.init(context)

        try {
            val modelFile = ModelDownloader.getDanbooruModelFile(context)
            val tagsFile = ModelDownloader.getDanbooruTagsFile(context)
            
            if (modelFile.exists() && tagsFile.exists()) {
                Log.d("AiTaggingService", "Initializing ONNX session with model: ${modelFile.absolutePath} (size: ${modelFile.length()})")
                if (ortEnv == null) {
                    ortEnv = OrtEnvironment.getEnvironment()
                }
                
                val options = OrtSession.SessionOptions().apply {
                    addConfigEntry("session.load_model_format", "ONNX")
                    try {
                        // ONNX Runtime Android では NNAPI が不安定な場合があるため、一旦無効化してCPUで確実性を優先
                        // addNnapi() 
                    } catch (e: Exception) {
                        Log.w("AiTaggingService", "NNAPI init failed", e)
                    }
                }
                ortSession = ortEnv?.createSession(modelFile.absolutePath, options)
                Log.d("AiTaggingService", "ONNX session created. Input names: ${ortSession?.inputNames}")
                
                tags = tagsFile.readLines()
                    .drop(1)
                    .map { line ->
                        val parts = line.split(",")
                        if (parts.size >= 2) parts[1] else ""
                    }
                    .filter { it.isNotEmpty() }
                Log.d("AiTaggingService", "Loaded ${tags.size} tags")
            } else {
                Log.w("AiTaggingService", "Model or tags file missing")
            }
        } catch (e: Exception) {
            Log.e("AiTaggingService", "CRITICAL: Failed to initialize Tagger session", e)
        }
    }

    suspend fun analyzeSingle(media: MediaData): List<String> = withContext(Dispatchers.IO) {
        if (ortSession == null) {
            ensureInitialized()
        }
        
        if (media.isVideo || ortSession == null || tags.isEmpty()) {
            Log.d("AiTaggingService", "Skipping analysis for ${media.uri}: isVideo=${media.isVideo}, sessionNull=${ortSession == null}, tagsEmpty=${tags.isEmpty()}")
            return@withContext emptyList()
        }

        try {
            val bitmap = decodeBitmap(media.uri) ?: return@withContext emptyList()
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 448, 448, true)
            
            Log.d("AiTaggingService", "Running inference for ${media.uri}")
            val scores = runInference(resizedBitmap)
            
            if (scores.isEmpty()) {
                Log.w("AiTaggingService", "No scores returned for ${media.uri}")
                return@withContext emptyList()
            }

            val detectedTags = mutableMapOf<String, Float>()
            var ageRating = "SFW"

            val ratingScores = mutableMapOf<String, Float>()
            val ratingMap = mapOf(
                "rating:general" to "SFW",
                "rating:sensitive" to "SFW",
                "rating:questionable" to "R15",
                "rating:explicit" to "R18"
            )

            scores.forEachIndexed { index, score ->
                if (score >= 0.05f) { // 判定のために少し低めから見る
                    val tagName = tags.getOrNull(index) ?: return@forEachIndexed
                    
                    if (ratingMap.containsKey(tagName)) {
                        ratingScores[tagName] = score
                    }

                    if (score >= threshold && !ratingMap.containsKey(tagName)) {
                        val cleanTagName = tagName // アンダースコアを含んだまま比較する (AppConstantsと合わせる)
                        if (AppConstants.R18Keywords.any { cleanTagName.contains(it, ignoreCase = true) }) {
                            ageRating = "R18"
                        } else if (ageRating != "R18" && AppConstants.R15Keywords.any { cleanTagName.contains(it, ignoreCase = true) }) {
                            ageRating = "R15"
                        }

                        detectedTags[tagName] = score
                    }
                }
            }

            val maxRatingTag = ratingScores.maxByOrNull { it.value }?.key
            if (maxRatingTag != null && ratingScores[maxRatingTag]!! > 0.3f) {
                val modelRating = ratingMap[maxRatingTag]!!
                // レベルの格上げロジック: R18 > R15 > SFW
                when (modelRating) {
                    "R18" -> ageRating = "R18"
                    "R15" -> if (ageRating != "R18") ageRating = "R15"
                    "SFW" -> { /* キーワード判定でR15/R18になっていればそれを優先 */ }
                }
            }

            Log.d("AiTaggingService", "Detected ${detectedTags.size} tags for ${media.uri}, ageRating=$ageRating")
            detectedTags.forEach { (tagName, score) ->
                repository.saveTag(TagEntity(media.uri, tagName, score))
            }

            repository.updateAiAnalysisResult(
                uri = media.uri,
                ageRating = ageRating,
                isAiAnalyzed = true,
                folderName = media.folderName
            )
            resizedBitmap.recycle()
            if (bitmap != resizedBitmap) bitmap.recycle()
            
            // 検出されたタグ（翻訳済み）をリストにして返す
            return@withContext detectedTags.keys.map { TagTranslationService.translate(it) }
        } catch (e: Exception) {
            Log.e("AiTaggingService", "Error in SmilingWolf analysis: ${media.uri}", e)
            return@withContext emptyList()
        }
    }

    private fun runInference(bitmap: Bitmap): FloatArray {
        imgDataBuffer.rewind()

        val pixels = IntArray(448 * 448)
        bitmap.getPixels(pixels, 0, 448, 0, 0, 448, 448)

        // SmilingWolf WD Tagger v3 ONNX (HuggingFace)
        val inputName = ortSession?.inputNames?.firstOrNull() ?: "input"
        val inputInfo = ortSession?.inputInfo?.get(inputName)
        val shape = inputInfo?.info?.let { (it as? ai.onnxruntime.TensorInfo)?.shape }
        
        Log.d("AiTaggingService", "Inference: input=$inputName, targetShape=${shape?.contentToString()}")

        // WD Tagger v3 ViT usually expects [1, 448, 448, 3] (NHWC)
        // RGB values 0.0 - 255.0
        // 動的軸 (-1) を考慮して判定
        val isNHWC = shape != null && shape.size == 4 && (shape[3] == 3L || shape[3] == -1L)

        if (isNHWC) {
            // NHWC
            for (i in 0 until 448 * 448) {
                val pixel = pixels[i]
                imgDataBuffer.put((pixel shr 16 and 0xFF).toFloat()) // R
                imgDataBuffer.put((pixel shr 8 and 0xFF).toFloat())  // G
                imgDataBuffer.put((pixel and 0xFF).toFloat())       // B
            }
        } else {
            // NCHW [1, 3, 448, 448]
            for (i in 0 until 448 * 448) imgDataBuffer.put((pixels[i] shr 16 and 0xFF).toFloat()) // R
            for (i in 0 until 448 * 448) imgDataBuffer.put((pixels[i] shr 8 and 0xFF).toFloat())  // G
            for (i in 0 until 448 * 448) imgDataBuffer.put((pixels[i] and 0xFF).toFloat())       // B
        }
        
        imgDataBuffer.rewind()
        // 実際の入力テンソルの形状を決定（動的軸 -1 を 1 に置き換える）
        val tensorShape = shape?.let { s ->
            LongArray(s.size) { i -> if (s[i] == -1L) 1L else s[i] }
        } ?: longArrayOf(1, 448, 448, 3)
        
        return try {
            val inputTensor = OnnxTensor.createTensor(ortEnv, imgDataBuffer, tensorShape)
            val output = ortSession?.run(Collections.singletonMap(inputName, inputTensor))
            val result = output?.get(0)?.value as? Array<FloatArray>
            inputTensor.close()
            output?.close()
            result?.get(0) ?: FloatArray(0)
        } catch (e: Exception) {
            Log.e("AiTaggingService", "Inference run failed", e)
            FloatArray(0)
        }
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
        TagTranslationService.close()
    }
}

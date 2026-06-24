package com.example.gallery.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import com.example.gallery.data.local.entity.TagEntity
import com.example.gallery.data.model.MediaData
import com.example.gallery.data.repository.MediaRepository
import com.example.gallery.util.ModelDownloader
import com.example.gallery.ui.AppConstants
import com.example.gallery.service.TagTranslationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private var inputName: String = "input"
    private var tensorShape: LongArray = longArrayOf(1, INPUT_SIZE.toLong(), INPUT_SIZE.toLong(), 3)
    private var isNHWC: Boolean = true
    private val mutex = Mutex()
    private val threshold = 0.60f // 60%以上に設定
    
    // バッファの再利用
    private val imgDataBuffer = FloatBuffer.allocate(1 * INPUT_SIZE * INPUT_SIZE * 3)
    private val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)

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
                if (ortEnv == null) {
                    ortEnv = OrtEnvironment.getEnvironment()
                }
                
                val threadCount = Runtime.getRuntime().availableProcessors().coerceIn(1, 2)
                val options = OrtSession.SessionOptions().apply {
                    addConfigEntry("session.load_model_format", "ONNX")
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
                    setInterOpNumThreads(1)
                    setIntraOpNumThreads(threadCount)
                    setMemoryPatternOptimization(true)
                    try {
                        addXnnpack(mapOf("intra_op_num_threads" to threadCount.toString()))
                    } catch (e: Exception) {
                        Log.w("AiTaggingService", "XNNPACK init failed; falling back to default CPU", e)
                    }
                }
                ortSession = ortEnv?.createSession(modelFile.absolutePath, options)
                cacheInputMetadata()

                tags = tagsFile.readLines()
                    .drop(1)
                    .map { line ->
                        val parts = line.split(",")
                        if (parts.size >= 2) parts[1] else ""
                    }
                    .filter { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            Log.e("AiTaggingService", "CRITICAL: Failed to initialize Tagger session", e)
        }
    }

    private fun cacheInputMetadata() {
        val session = ortSession ?: return
        inputName = session.inputNames.firstOrNull() ?: "input"
        val shape = session.inputInfo[inputName]?.info
            ?.let { it as? TensorInfo }
            ?.shape
        isNHWC = shape != null && shape.size == 4 && (shape[3] == 3L || shape[3] == -1L)
        tensorShape = shape?.let { inputShape ->
            LongArray(inputShape.size) { index ->
                if (inputShape[index] == -1L) 1L else inputShape[index]
            }
        } ?: longArrayOf(1, INPUT_SIZE.toLong(), INPUT_SIZE.toLong(), 3)
    }

    suspend fun analyzeSingle(media: MediaData): List<String> = withContext(Dispatchers.IO) {
        if (ortSession == null) {
            ensureInitialized()
        }

        if (media.isVideo || ortSession == null || tags.isEmpty()) {
            return@withContext emptyList()
        }

        mutex.withLock {
            var bitmap: Bitmap? = null
            var resizedBitmap: Bitmap? = null

            try {
                val decodedBitmap = decodeBitmap(media.uri) ?: return@withLock emptyList()
                bitmap = decodedBitmap
                resizedBitmap = if (decodedBitmap.width == INPUT_SIZE && decodedBitmap.height == INPUT_SIZE) {
                    decodedBitmap
                } else {
                    Bitmap.createScaledBitmap(decodedBitmap, INPUT_SIZE, INPUT_SIZE, false)
                }

                val scores = runInference(resizedBitmap)

                if (scores.isEmpty()) {
                    Log.w("AiTaggingService", "No scores returned for ${media.uri}")
                    return@withLock emptyList()
                }

                val detectedTags = ArrayList<Pair<String, Float>>(MAX_SAVED_TAGS * 2)
                var ageRating = "SFW"

                val ratingScores = mutableMapOf<String, Float>()

                scores.forEachIndexed { index, score ->
                    if (score >= 0.05f) {
                        val tagName = tags.getOrNull(index) ?: return@forEachIndexed

                        if (RATING_MAP.containsKey(tagName)) {
                            ratingScores[tagName] = score
                        }

                        if (score >= threshold && !RATING_MAP.containsKey(tagName)) {
                            if (AppConstants.R18Keywords.any { tagName.contains(it, ignoreCase = true) }) {
                                ageRating = "R18"
                            } else if (ageRating != "R18" && AppConstants.R15Keywords.any { tagName.contains(it, ignoreCase = true) }) {
                                ageRating = "R15"
                            }

                            detectedTags.add(tagName to score)
                        }
                    }
                }

                val maxRatingTag = ratingScores.maxByOrNull { it.value }?.key
                if (maxRatingTag != null && ratingScores[maxRatingTag]!! > 0.3f) {
                    when (RATING_MAP[maxRatingTag]!!) {
                        "R18" -> ageRating = "R18"
                        "R15" -> if (ageRating != "R18") ageRating = "R15"
                        "SFW" -> Unit
                    }
                }

                val savedTags = detectedTags
                    .sortedByDescending { it.second }
                    .take(MAX_SAVED_TAGS)
                    .map { (tagName, score) -> TagEntity(media.uri, tagName, score) }

                repository.saveAiAnalysisResult(
                    uri = media.uri,
                    ageRating = ageRating,
                    isAiAnalyzed = true,
                    folderName = media.folderName,
                    tags = savedTags
                )

                savedTags.take(3).map { TagTranslationService.translate(it.tag) }
            } catch (e: Exception) {
                Log.e("AiTaggingService", "Error in SmilingWolf analysis: ${media.uri}", e)
                emptyList()
            } finally {
                resizedBitmap?.recycle()
                if (bitmap != null && bitmap !== resizedBitmap) bitmap.recycle()
            }
        }
    }

    private fun runInference(bitmap: Bitmap): FloatArray {
        imgDataBuffer.rewind()

        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        // SmilingWolf WD Tagger v3 ONNX (HuggingFace)
        // Input metadata is cached at session creation; avoid querying ONNX metadata per image.
        
        // WD Tagger v3 ViT usually expects [1, 448, 448, 3] (NHWC)
        // RGB values 0.0 - 255.0
        // 動的軸 (-1) を考慮して判定
        if (isNHWC) {
            // NHWC
            for (i in 0 until INPUT_PIXEL_COUNT) {
                val pixel = pixels[i]
                imgDataBuffer.put((pixel shr 16 and 0xFF).toFloat()) // R
                imgDataBuffer.put((pixel shr 8 and 0xFF).toFloat())  // G
                imgDataBuffer.put((pixel and 0xFF).toFloat())       // B
            }
        } else {
            // NCHW [1, 3, 448, 448]
            for (i in 0 until INPUT_PIXEL_COUNT) imgDataBuffer.put((pixels[i] shr 16 and 0xFF).toFloat()) // R
            for (i in 0 until INPUT_PIXEL_COUNT) imgDataBuffer.put((pixels[i] shr 8 and 0xFF).toFloat())  // G
            for (i in 0 until INPUT_PIXEL_COUNT) imgDataBuffer.put((pixels[i] and 0xFF).toFloat())       // B
        }
        
        imgDataBuffer.rewind()
        // 実際の入力テンソルの形状を決定（動的軸 -1 を 1 に置き換える）
        return try {
            val inputTensor = OnnxTensor.createTensor(ortEnv, imgDataBuffer, tensorShape)
            val output = ortSession?.run(Collections.singletonMap(inputName, inputTensor))
            val result = output?.get(0)?.value
            inputTensor.close()
            output?.close()
            when (result) {
                is Array<*> -> {
                    val first = result.firstOrNull()
                    when (first) {
                        is FloatArray -> first
                        is Array<*> -> first.firstOrNull() as? FloatArray ?: FloatArray(0)
                        else -> FloatArray(0)
                    }
                }
                is FloatArray -> result
                else -> FloatArray(0)
            }
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
            if (options.outHeight > INPUT_SIZE || options.outWidth > INPUT_SIZE) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / inSampleSize >= INPUT_SIZE && halfWidth / inSampleSize >= INPUT_SIZE) {
                    inSampleSize *= 2
                }
            }

            // 2. 実際にデコード
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }
        } catch (_: Exception) { null }
    }

    companion object {
        private const val INPUT_SIZE = 448
        private const val INPUT_PIXEL_COUNT = INPUT_SIZE * INPUT_SIZE
        private const val MAX_SAVED_TAGS = 40
        private val RATING_MAP = mapOf(
            "rating:general" to "SFW",
            "rating:sensitive" to "SFW",
            "rating:questionable" to "R15",
            "rating:explicit" to "R18"
        )
    }

    fun close() {
        ortSession?.close()
        ortEnv?.close()
        TagTranslationService.close()
    }
}

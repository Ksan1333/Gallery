package com.example.gallery.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.gallery.data.model.MediaData
import com.example.gallery.data.repository.MediaRepository
import com.example.gallery.util.ModelDownloader
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder.ImageEmbedderOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class VectorSearchService(
    private val context: Context,
    private val repository: MediaRepository
) {
    private var imageEmbedder: ImageEmbedder? = null
    private val mutex = Mutex()

    init {
        // 起動時の初期化を停止。必要時に ensureInitialized() を呼ぶ
    }

    fun ensureInitialized() {
        if (imageEmbedder != null) return

        try {
            val modelFile = ModelDownloader.getVectorModelFile(context)
            if (modelFile.exists()) {
                val baseOptionsBuilder = BaseOptions.builder()
                    .setModelAssetPath(modelFile.absolutePath)

                // まずGPUを試す。失敗したらCPUにフォールバック
                try {
                    val baseOptions = baseOptionsBuilder.setDelegate(com.google.mediapipe.tasks.core.Delegate.GPU).build()
                    val options = ImageEmbedderOptions.builder()
                        .setBaseOptions(baseOptions)
                        .setL2Normalize(true)
                        .setQuantize(false)
                        .build()
                    imageEmbedder = ImageEmbedder.createFromOptions(context, options)
                } catch (e: Exception) {
                    Log.w("VectorSearchService", "GPU not available for ImageEmbedder, falling back to CPU", e)
                    val baseOptions = baseOptionsBuilder.setDelegate(com.google.mediapipe.tasks.core.Delegate.CPU).build()
                    val options = ImageEmbedderOptions.builder()
                        .setBaseOptions(baseOptions)
                        .setL2Normalize(true)
                        .setQuantize(false)
                        .build()
                    imageEmbedder = ImageEmbedder.createFromOptions(context, options)
                }
            } else {
                Log.w("VectorSearchService", "Model file not found at ${modelFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e("VectorSearchService", "Failed to initialize ImageEmbedder", e)
        }
    }

    suspend fun analyzeSingle(media: MediaData) = withContext(Dispatchers.IO) {
        if (media.isVideo) return@withContext
        
        mutex.withLock {
            if (imageEmbedder == null) {
                ensureInitialized()
            }
            
            val embedder = imageEmbedder
            if (embedder == null) {
                Log.w("VectorSearchService", "Skipping analysis for ${media.uri}: ImageEmbedder is null (model might be missing)")
                return@withLock
            }

            try {
                val bitmap = decodeBitmap(media.uri) ?: run {
                    Log.w("VectorSearchService", "Failed to decode bitmap for ${media.uri}")
                    return@withLock
                }
                
                val mpImage = BitmapImageBuilder(bitmap).build()
                val result = embedder.embed(mpImage)
                
                // ImageEmbedderResult -> EmbeddingResult (via embeddingResult()) -> List<Embedding> -> float[]
                val embedResult = result.embeddingResult()
                val embedding = embedResult.embeddings().firstOrNull()
                val vector: FloatArray? = embedding?.floatEmbedding()

                if (vector != null) {
                    repository.updateFeatureVector(media.uri, vector, folderName = media.folderName)
                } else {
                    Log.w("VectorSearchService", "Failed to extract vector for ${media.uri}: result is null or empty")
                }
                bitmap.recycle()
            } catch (e: Exception) {
                Log.e("VectorSearchService", "Error analyzing vector for ${media.uri}", e)
            }
        }
    }

    private fun decodeBitmap(uriString: String): Bitmap? {
        return try {
            val context = context
            val uri = Uri.parse(uriString)

            // 1. サイズだけを取得
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            // MobileNetV3 (ImageEmbedder) は通常 224x224
            var inSampleSize = 1
            if (options.outHeight > 224 || options.outWidth > 224) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / inSampleSize >= 224 && halfWidth / inSampleSize >= 224) {
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
        imageEmbedder?.close()
    }

    companion object {
        fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
            // MediaPipeでL2Normalizeを有効にしている場合、ドット積だけで類似度が出る
            var dotProduct = 0f
            val size = minOf(v1.size, v2.size)
            for (i in 0 until size) {
                dotProduct += v1[i] * v2[i]
            }
            return dotProduct.coerceIn(-1f, 1f)
        }
    }
}

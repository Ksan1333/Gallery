package com.example.gallery.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.gallery.data.local.entity.MediaMetadataEntity
import com.example.gallery.ui.MediaData
import com.example.gallery.util.ModelDownloader
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder.ImageEmbedderOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class VectorSearchService(
    private val context: Context,
    private val repository: MediaRepository
) {
    private var imageEmbedder: ImageEmbedder? = null

    init {
        try {
            val modelFile = ModelDownloader.getModelFile(context)
            if (modelFile.exists()) {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath(modelFile.absolutePath)
                    .build()
                val options = ImageEmbedderOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setL2Normalize(true) // 類似度計算のために正規化を有効にする
                    .setQuantize(false)
                    .build()
                imageEmbedder = ImageEmbedder.createFromOptions(context, options)
            }
        } catch (e: Exception) {
            Log.e("VectorSearchService", "Failed to initialize ImageEmbedder", e)
        }
    }

    suspend fun analyzeSingle(media: MediaData) = withContext(Dispatchers.IO) {
        if (media.isVideo || imageEmbedder == null) return@withContext

        try {
            val bitmap = decodeBitmap(media.uri) ?: return@withContext
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = imageEmbedder?.embed(mpImage)
            
            val embedding = result?.embeddingResult()?.embeddings()?.firstOrNull()
            val vector = embedding?.floatEmbedding()

            if (vector != null) {
                val current = repository.getMetadata(media.uri)
                repository.saveMetadata(
                    MediaMetadataEntity(
                        uri = media.uri,
                        isFavorite = current?.isFavorite ?: false,
                        colorComposition = current?.colorComposition,
                        ageRating = current?.ageRating ?: "SFW",
                        isAiAnalyzed = current?.isAiAnalyzed ?: false,
                        featureVector = vector,
                        folderName = current?.folderName ?: media.folderName
                    )
                )
            }
            bitmap.recycle()
        } catch (e: Exception) {
            Log.e("VectorSearchService", "Error analyzing vector for ${media.uri}", e)
        }
    }

    private fun decodeBitmap(uriString: String): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                // MediaPipe MobileNetV3は224x224程度の入力で十分
                inSampleSize = 2
            }
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) { null }
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

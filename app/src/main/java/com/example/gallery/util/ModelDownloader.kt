package com.example.gallery.util

import android.content.Context
import android.util.Log
import com.example.gallery.ui.AppDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object ModelDownloader {
    private const val TAG = "ModelDownloader"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .connectionSpecs(
            listOf(
                ConnectionSpec.MODERN_TLS,
                ConnectionSpec.COMPATIBLE_TLS
            )
        )
        .build()

    private const val VECTOR_MODEL_URL = "https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_small/float32/latest/mobilenet_v3_small.tflite"
    private const val VECTOR_MODEL_FILENAME = "image_embedder.tflite"

    private val NORMAL_TAGGER_SPEC = TaggerModelSpec(
        id = AppDefaults.AI_TAGGER_MODEL_NORMAL,
        modelUrl = "https://huggingface.co/SmilingWolf/wd-v1-4-moat-tagger-v2/resolve/main/model.onnx",
        modelFilename = "tagger_fast_moat_v2.onnx",
        tagsUrl = "https://huggingface.co/SmilingWolf/wd-v1-4-moat-tagger-v2/resolve/main/selected_tags.csv",
        tagsFilename = "selected_tags_moat_v2.csv",
        minModelSize = 250_000_000L,
        minTagsSize = 5_000L
    )

    private val HIGH_TAGGER_SPEC = TaggerModelSpec(
        id = AppDefaults.AI_TAGGER_MODEL_HIGH,
        modelUrl = "https://huggingface.co/SmilingWolf/wd-v1-4-swinv2-tagger-v2/resolve/main/model.onnx",
        modelFilename = "tagger_high_swinv2_v2.onnx",
        tagsUrl = "https://huggingface.co/SmilingWolf/wd-v1-4-swinv2-tagger-v2/resolve/main/selected_tags.csv",
        tagsFilename = "selected_tags_high_swinv2_v2.csv",
        minModelSize = 400_000_000L,
        minTagsSize = 5_000L
    )

    data class TaggerModelSpec(
        val id: String,
        val modelUrl: String,
        val modelFilename: String,
        val tagsUrl: String,
        val tagsFilename: String,
        val minModelSize: Long,
        val minTagsSize: Long
    )

    fun getVectorModelFile(context: Context): File = File(context.filesDir, VECTOR_MODEL_FILENAME)

    fun currentTaggerModelId(context: Context): String {
        val prefs = context.getSharedPreferences("global_settings", Context.MODE_PRIVATE)
        return AppDefaults.normalizedAiTaggerModel(
            prefs.getString(AppDefaults.AI_TAGGER_MODEL_KEY, AppDefaults.AI_TAGGER_MODEL_NORMAL)
        )
    }

    fun taggerSpec(modelId: String?): TaggerModelSpec {
        return when (AppDefaults.normalizedAiTaggerModel(modelId)) {
            AppDefaults.AI_TAGGER_MODEL_HIGH -> HIGH_TAGGER_SPEC
            else -> NORMAL_TAGGER_SPEC
        }
    }

    fun getDanbooruModelFile(context: Context, modelId: String? = null): File {
        return File(context.filesDir, taggerSpec(modelId).modelFilename)
    }

    fun getDanbooruTagsFile(context: Context, modelId: String? = null): File {
        return File(context.filesDir, taggerSpec(modelId).tagsFilename)
    }

    fun isVectorModelValid(context: Context): Boolean {
        return getVectorModelFile(context).let { it.exists() && it.length() > 500_000L }
    }

    fun isTaggerModelValid(context: Context, modelId: String = currentTaggerModelId(context)): Boolean {
        val spec = taggerSpec(modelId)
        return getDanbooruModelFile(context, spec.id).let { it.exists() && it.length() > spec.minModelSize } &&
            getDanbooruTagsFile(context, spec.id).let { it.exists() && it.length() > spec.minTagsSize }
    }

    fun areModelsValid(context: Context): Boolean {
        return isVectorModelValid(context) && isTaggerModelValid(context)
    }

    suspend fun downloadAllModels(
        context: Context,
        onProgress: (Float) -> Unit,
        taggerModelId: String = currentTaggerModelId(context)
    ): Boolean = withContext(Dispatchers.IO) {
        val taggerSpec = taggerSpec(taggerModelId)
        val tasks = listOf(
            DownloadTask(VECTOR_MODEL_URL, getVectorModelFile(context), 0.2f, 1_000_000L),
            DownloadTask(taggerSpec.modelUrl, getDanbooruModelFile(context, taggerSpec.id), 0.7f, taggerSpec.minModelSize),
            DownloadTask(taggerSpec.tagsUrl, getDanbooruTagsFile(context, taggerSpec.id), 0.1f, taggerSpec.minTagsSize)
        )

        var totalProgress = 0f
        for (task in tasks) {
            if (task.file.exists() && task.file.length() >= task.minExpectedSize) {
                totalProgress += task.weight
                onProgress(totalProgress)
                continue
            } else if (task.file.exists()) {
                Log.w(TAG, "Model file exists but seems too small (${task.file.length()}), re-downloading...")
                task.file.delete()
            }

            val success = downloadFile(task.url, task.file) { taskProgress ->
                onProgress(totalProgress + (taskProgress * task.weight))
            }

            if (!success) {
                Log.e(TAG, "Failed to download model: ${task.file.name}")
                return@withContext false
            }
            totalProgress += task.weight
            onProgress(totalProgress)
        }
        true
    }

    private data class DownloadTask(
        val url: String,
        val file: File,
        val weight: Float,
        val minExpectedSize: Long
    )

    private suspend fun downloadFile(urlStr: String, file: File, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(urlStr)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Server returned error: ${response.code}")
                    return@withContext false
                }

                val body = response.body ?: return@withContext false
                val totalSize = body.contentLength()

                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (totalSize > 0) {
                                onProgress(totalRead.toFloat() / totalSize)
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download $urlStr", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}, message: ${e.message}")
            if (file.exists()) file.delete()
            false
        }
    }
}

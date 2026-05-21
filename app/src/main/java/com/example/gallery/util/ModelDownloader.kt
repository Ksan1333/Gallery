package com.example.gallery.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

object ModelDownloader {
    private const val TAG = "ModelDownloader"
    
    // 1. Vector Search Model (MobileNetV3)
    private const val VECTOR_MODEL_URL = "https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_small/float32/latest/mobilenet_v3_small.tflite"
    private const val VECTOR_MODEL_FILENAME = "image_embedder.tflite"

    // 2. SmilingWolf WD Tagger Model & Tags
    // モバイル向けに最適化されたONNXモデル
    private const val DANBOORU_MODEL_URL = "https://huggingface.co/SmilingWolf/wd-vit-tagger-v3/resolve/main/model.onnx"
    private const val DANBOORU_MODEL_FILENAME = "model.onnx"
    private const val DANBOORU_TAGS_URL = "https://huggingface.co/SmilingWolf/wd-vit-tagger-v3/resolve/main/selected_tags.csv"
    private const val DANBOORU_TAGS_FILENAME = "selected_tags.csv"

    fun getVectorModelFile(context: Context): File = File(context.filesDir, VECTOR_MODEL_FILENAME)
    fun getDanbooruModelFile(context: Context): File = File(context.filesDir, DANBOORU_MODEL_FILENAME)
    fun getDanbooruTagsFile(context: Context): File = File(context.filesDir, DANBOORU_TAGS_FILENAME)

    suspend fun downloadAllModels(context: Context, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val tasks = listOf(
            DownloadTask(VECTOR_MODEL_URL, getVectorModelFile(context), 0.2f),
            DownloadTask(DANBOORU_MODEL_URL, getDanbooruModelFile(context), 0.7f),
            DownloadTask(DANBOORU_TAGS_URL, getDanbooruTagsFile(context), 0.1f)
        )

        var totalProgress = 0f
        for (task in tasks) {
            if (task.file.exists() && task.file.length() > 1024) { // サイズチェックも追加
                Log.d(TAG, "Model already exists: ${task.file.name}")
                totalProgress += task.weight
                onProgress(totalProgress)
                continue
            }

            Log.d(TAG, "Starting download: ${task.url}")
            val success = downloadFile(task.url, task.file) { taskProgress ->
                onProgress(totalProgress + (taskProgress * task.weight))
            }
            
            if (!success) {
                Log.e(TAG, "Failed to download model: ${task.file.name}")
                return@withContext false
            }
            Log.d(TAG, "Download finished: ${task.file.name}")
            totalProgress += task.weight
            onProgress(totalProgress)
        }
        true
    }

    private data class DownloadTask(val url: String, val file: File, val weight: Float)

    private suspend fun downloadFile(urlStr: String, file: File, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Downloading: $urlStr")
            val connection = URL(urlStr).openConnection()
            connection.connect()
            val totalSize = connection.contentLength
            
            connection.getInputStream().use { input ->
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
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download $urlStr", e)
            // 詳細なエラー情報をログに残す
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}, message: ${e.message}")
            if (file.exists()) file.delete()
            false
        }
    }
}

package com.example.gallery.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

object ModelDownloader {
    private const val TAG = "ModelDownloader"
    // GoogleのMediaPipe公式MobileNetV3モデルURL
    private const val MODEL_URL = "https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_small/float32/latest/mobilenet_v3_small.tflite"
    private const val MODEL_FILENAME = "image_embedder.tflite"

    fun getModelFile(context: Context): File {
        return File(context.filesDir, MODEL_FILENAME)
    }

    suspend fun downloadIfNeeded(context: Context, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val modelFile = getModelFile(context)
        if (modelFile.exists()) {
            return@withContext true
        }

        try {
            Log.d(TAG, "Downloading AI model from $MODEL_URL")
            val connection = URL(MODEL_URL).openConnection()
            connection.connect()
            val totalSize = connection.contentLength
            
            connection.getInputStream().use { input ->
                modelFile.outputStream().use { output ->
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
            Log.d(TAG, "AI model downloaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download model", e)
            if (modelFile.exists()) modelFile.delete()
            false
        }
    }
}

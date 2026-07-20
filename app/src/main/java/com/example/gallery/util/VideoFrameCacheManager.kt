package com.example.gallery.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

object VideoFrameCacheManager {
    private const val TAG = "VideoFrameCache"
    private val cachedFrames = Collections.synchronizedList(mutableListOf<Bitmap>())
    private var currentUri: String? = null
    private var currentDuration: Long = 0L
    private val preparationId = AtomicInteger(0)

    suspend fun prepareCache(context: Context, uri: String, providedDurationMs: Long = 0L) {
        if (currentUri == uri && cachedFrames.isNotEmpty()) return
        
        clearCache()
        val myId = preparationId.get()
        currentUri = uri

        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                val videoUri = Uri.parse(uri)
                context.contentResolver.openAssetFileDescriptor(videoUri, "r")?.use { afd ->
                    retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                } ?: retriever.setDataSource(context, videoUri)

                val duration = if (providedDurationMs > 0) providedDurationMs 
                               else retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                currentDuration = duration
                
                if (duration <= 0) {
                    Log.e(TAG, "Invalid duration for $uri")
                    return@withContext
                }

                val interval = duration / 25
                for (i in 0 until 25) {
                    if (myId != preparationId.get()) return@withContext
                    
                    val timeUs = (i * interval) * 1000
                    // Use OPTION_CLOSEST_SYNC for speed (keyframes)
                    val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (frame != null) {
                        val scale = 360f / frame.height.coerceAtLeast(1).toFloat()
                        if (scale < 1.0f) {
                            val scaled = Bitmap.createScaledBitmap(
                                frame,
                                (frame.width * scale).toInt(),
                                360,
                                true
                            )
                            cachedFrames.add(scaled)
                            if (scaled != frame) frame.recycle()
                        } else {
                            cachedFrames.add(frame)
                        }
                    }
                }
                Log.d(TAG, "Cache prepared: ${cachedFrames.size} frames for $uri")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prepare cache", e)
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    fun getFrameAt(positionMs: Long): Bitmap? {
        synchronized(cachedFrames) {
            if (cachedFrames.isEmpty() || currentDuration <= 0) return null
            val index = ((positionMs.toFloat() / currentDuration.toFloat()) * 24).toInt().coerceIn(0, cachedFrames.size - 1)
            return cachedFrames.getOrNull(index)
        }
    }

    fun clearCache() {
        preparationId.incrementAndGet()
        synchronized(cachedFrames) {
            cachedFrames.forEach { it.recycle() }
            cachedFrames.clear()
        }
        currentUri = null
        currentDuration = 0L
    }
}

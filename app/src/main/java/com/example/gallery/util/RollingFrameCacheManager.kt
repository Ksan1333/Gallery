package com.example.gallery.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

object RollingFrameCacheManager {
    private const val TAG = "RollingFrameCache"
    private val cachedFrames = ConcurrentHashMap<Int, Bitmap>() // index 0-9
    private var centerTimeMs: Long = 0
    private var currentUri: String? = null
    private val preparationId = AtomicInteger(0)
    private const val WINDOW_MS = 60_000L // 1 minute window
    private const val FRAME_COUNT = 10
    private const val INTERVAL_MS = WINDOW_MS / FRAME_COUNT

    suspend fun prepareRollingCache(context: Context, uri: String, startTimeMs: Long) {
        if (currentUri == uri && abs(centerTimeMs - startTimeMs) < INTERVAL_MS && cachedFrames.isNotEmpty()) {
            return
        }

        clearRollingCache()
        val myId = preparationId.get()
        currentUri = uri
        centerTimeMs = startTimeMs

        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                val videoUri = Uri.parse(uri)
                context.contentResolver.openAssetFileDescriptor(videoUri, "r")?.use { afd ->
                    retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                } ?: retriever.setDataSource(context, videoUri)

                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                
                val windowStart = (startTimeMs - WINDOW_MS / 2).coerceAtLeast(0L)
                
                for (i in 0 until FRAME_COUNT) {
                    if (myId != preparationId.get()) return@withContext
                    
                    val targetTimeMs = (windowStart + i * INTERVAL_MS).coerceAtMost(duration)
                    // Use OPTION_CLOSEST_SYNC for speed (keyframes)
                    val frame = retriever.getFrameAtTime(targetTimeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (frame != null) {
                        // Resizing to 720p height for "High Quality" feedback
                        val scale = 720f / frame.height.coerceAtLeast(1).toFloat()
                        if (scale < 1.0f) {
                            val scaled = Bitmap.createScaledBitmap(
                                frame,
                                (frame.width * scale).toInt(),
                                720,
                                true
                            )
                            cachedFrames[i] = scaled
                            if (scaled != frame) frame.recycle()
                        } else {
                            cachedFrames[i] = frame
                        }
                    }
                }
                Log.d(TAG, "Rolling cache prepared: ${cachedFrames.size} high-res frames around $startTimeMs ms")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prepare rolling cache", e)
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    fun getRollingFrameAt(positionMs: Long): Bitmap? {
        if (cachedFrames.isEmpty()) return null
        
        val windowStart = centerTimeMs - WINDOW_MS / 2
        val offsetMs = positionMs - windowStart
        
        if (offsetMs < 0 || offsetMs > WINDOW_MS) return null
        
        val index = (offsetMs / INTERVAL_MS).toInt().coerceIn(0, FRAME_COUNT - 1)
        return cachedFrames[index]
    }

    fun clearRollingCache() {
        preparationId.incrementAndGet()
        cachedFrames.values.forEach { it.recycle() }
        cachedFrames.clear()
        currentUri = null
        centerTimeMs = 0
    }
}

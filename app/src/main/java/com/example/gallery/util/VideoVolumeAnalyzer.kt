package com.example.gallery.util

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.sqrt

/** A short PCM sample used to decide whether a video's track is unusually loud. */
data class VideoVolumeAnalysis(
    val rms: Float,
    val peak: Float
) {
    val shouldReduce: Boolean
        get() = VideoVolumeAnalyzer.shouldReduce(rms, peak)
}

/**
 * Performs a bounded, cached audio scan for the currently opened video.
 *
 * This deliberately samples only the first few seconds. It keeps opening a video
 * responsive while still detecting the common case of an exported track whose
 * overall level is much higher than normal media. A failed/unsupported decode is
 * treated as unknown and never changes the user's volume.
 */
object VideoVolumeAnalyzer {
    private const val SAMPLE_WINDOW_US = 8_000_000L
    private const val ANALYSIS_TIMEOUT_MS = 3_000L
    private const val TARGET_RMS = 0.14f
    private const val LOUD_RMS_THRESHOLD = 0.22f
    private const val PEAK_RMS_THRESHOLD = 0.12f
    private const val PEAK_THRESHOLD = 0.98f

    private val cache = ConcurrentHashMap<String, VideoVolumeAnalysis>()

    suspend fun analyze(context: Context, uriString: String): VideoVolumeAnalysis? {
        cache[uriString]?.let { return it }
        val result = withContext(Dispatchers.IO) {
            withTimeoutOrNull(ANALYSIS_TIMEOUT_MS) {
                runCatching { decodePcm(context, uriString) }.getOrNull()
            }
        } ?: return null
        cache[uriString] = result
        return result
    }

    internal fun shouldReduce(rms: Float, peak: Float): Boolean {
        // RMS is the primary measure. A near-clipped track with a high average
        // is also considered loud, even when its RMS is just below the threshold.
        return rms >= LOUD_RMS_THRESHOLD ||
            (peak >= PEAK_THRESHOLD && rms >= PEAK_RMS_THRESHOLD)
    }

    internal fun targetRms(): Float = TARGET_RMS

    private fun decodePcm(context: Context, uriString: String): VideoVolumeAnalysis? {
        val extractor = MediaExtractor()
        var assetFileDescriptor: android.content.res.AssetFileDescriptor? = null
        var codec: MediaCodec? = null
        try {
            val uri = Uri.parse(uriString)
            assetFileDescriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
            if (assetFileDescriptor != null) {
                extractor.setDataSource(
                    assetFileDescriptor!!.fileDescriptor,
                    assetFileDescriptor!!.startOffset,
                    assetFileDescriptor!!.length
                )
            } else {
                extractor.setDataSource(context, uri, null)
            }

            val audioTrackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return null
            val format = extractor.getTrackFormat(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }
            val endTimeUs = if (durationUs > 0L) {
                minOf(durationUs, SAMPLE_WINDOW_US)
            } else {
                SAMPLE_WINDOW_US
            }

            extractor.selectTrack(audioTrackIndex)
            extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var sumSquares = 0.0
            var sampleCount = 0L
            var peak = 0f

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000L)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
                        inputBuffer.clear()
                        val sampleTimeUs = extractor.sampleTime
                        if (sampleTimeUs < 0L || sampleTimeUs >= endTimeUs) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                maxOf(sampleTimeUs, 0L),
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    maxOf(sampleTimeUs, 0L),
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> if (inputDone) {
                        // Give the decoder one more chance to flush after EOS.
                        continue
                    }
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val encoding = if (codec.outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                codec.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            } else {
                                AudioFormat.ENCODING_PCM_16BIT
                            }
                            if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                val samples = outputBuffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
                                while (samples.hasRemaining()) {
                                    val value = samples.get().coerceIn(-1f, 1f)
                                    val absolute = abs(value)
                                    sumSquares += value.toDouble() * value.toDouble()
                                    peak = maxOf(peak, absolute)
                                    sampleCount++
                                }
                            } else {
                                val samples = outputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                                while (samples.hasRemaining()) {
                                    val value = samples.get().toFloat() / Short.MAX_VALUE
                                    val absolute = abs(value)
                                    sumSquares += value.toDouble() * value.toDouble()
                                    peak = maxOf(peak, absolute)
                                    sampleCount++
                                }
                            }
                        }
                        outputDone = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            if (sampleCount == 0L) return null
            return VideoVolumeAnalysis(
                rms = sqrt(sumSquares / sampleCount.toDouble()).toFloat(),
                peak = peak
            )
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
            runCatching { assetFileDescriptor?.close() }
        }
    }
}

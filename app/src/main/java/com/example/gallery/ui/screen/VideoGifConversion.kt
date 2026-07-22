package com.example.gallery.ui.screen

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import com.example.gallery.R
import com.example.gallery.service.GlobalOperationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

internal fun launchVideoGifConversion(
    scope: CoroutineScope,
    context: Context,
    videoUri: String,
    onCompleted: suspend () -> Unit = {}
) {
    val operationTag = "video_gif_${videoUri.hashCode()}"
    if (GlobalOperationService.getOperation(operationTag) != null) return

    scope.launch {
        val operationId = GlobalOperationService.startOperation(
            title = context.getString(R.string.video_gif_conversion_title),
            tag = operationTag
        )
        try {
            convertVideoUriToGif(context, videoUri) { progress, textRes ->
                GlobalOperationService.updateProgress(
                    progress.coerceIn(0f, 1f),
                    context.getString(textRes),
                    operationId
                )
            }
            onCompleted()
            Toast.makeText(context, context.getString(R.string.msg_video_gif_conversion_complete), Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.msg_video_gif_conversion_failed, error.localizedMessage ?: error.javaClass.simpleName),
                Toast.LENGTH_LONG
            ).show()
        } finally {
            GlobalOperationService.finishOperation(operationId)
        }
    }
}

private suspend fun convertVideoUriToGif(
    context: Context,
    sourceUri: String,
    onProgress: (Float, Int) -> Unit
): Uri = withContext(Dispatchers.IO) {
    val sourceFile = File.createTempFile("gallery_gif_source_", ".mp4", context.cacheDir)
    try {
        onProgress(0.01f, R.string.video_dl_gif_stage_analyzing)
        openVideoInputStream(context, sourceUri).use { input ->
            sourceFile.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
            }
        }

        val gifBytes = transcodeVideoFileToGif(sourceFile) { progress, textRes ->
            onProgress(0.08f + progress.coerceIn(0f, 1f) * 0.88f, textRes)
        }
        onProgress(0.97f, R.string.video_dl_saving_gif)
        saveGifToMediaStore(context, sourceUri, gifBytes)
    } finally {
        sourceFile.delete()
    }
}

private fun openVideoInputStream(context: Context, source: String): InputStream {
    val uri = Uri.parse(source)
    return when (uri.scheme) {
        ContentResolver.SCHEME_CONTENT -> context.contentResolver.openInputStream(uri)
        "file" -> uri.path?.let(::FileInputStream)
        else -> FileInputStream(source)
    } ?: error("Unable to open video: $source")
}

private fun saveGifToMediaStore(context: Context, sourceUri: String, bytes: ByteArray): Uri {
    val timestamp = System.currentTimeMillis()
    val sourceName = Uri.parse(sourceUri).lastPathSegment
        ?.substringBeforeLast('.')
        ?.take(60)
        ?.ifBlank { null }
        ?: "video"
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "${sourceName}_${timestamp}.gif")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/gif")
        put(MediaStore.MediaColumns.DATE_ADDED, timestamp / 1000L)
        put(MediaStore.MediaColumns.DATE_MODIFIED, timestamp / 1000L)
        put(MediaStore.MediaColumns.DATE_TAKEN, timestamp)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, context.getString(R.string.video_dl_rel_path_pictures))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    val resolver = context.contentResolver
    val outputUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("Unable to create GIF output")
    try {
        resolver.openOutputStream(outputUri)?.use { it.write(bytes) }
            ?: error("Unable to write GIF output")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(
                outputUri,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                    put(MediaStore.MediaColumns.DATE_ADDED, timestamp / 1000L)
                    put(MediaStore.MediaColumns.DATE_MODIFIED, timestamp / 1000L)
                    put(MediaStore.MediaColumns.DATE_TAKEN, timestamp)
                },
                null,
                null
            )
        }
        return outputUri
    } catch (error: Exception) {
        resolver.delete(outputUri, null, null)
        throw error
    }
}

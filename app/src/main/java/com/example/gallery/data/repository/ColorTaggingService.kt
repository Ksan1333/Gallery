package com.example.gallery.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.palette.graphics.Palette
import android.util.Log
import com.example.gallery.data.local.entity.MediaMetadataEntity
import com.example.gallery.data.local.entity.TagEntity
import com.example.gallery.ui.MediaData
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ColorTaggingService(
    private val context: Context,
    private val repository: MediaRepository
) {
    suspend fun performColorTagging(allMedia: List<MediaData>, onProgress: (Int, Int) -> Unit) = withContext(Dispatchers.IO) {
        val allMetadata = repository.getAllMetadata()
        val analyzedUris = allMetadata.filter { it.colorComposition != null }.map { it.uri }.toSet()
        val mediaToProcess = allMedia.filter { !it.isVideo && it.uri !in analyzedUris }

        if (mediaToProcess.isEmpty()) {
            onProgress(0, 0)
            return@withContext
        }

        mediaToProcess.forEachIndexed { index, media ->
            kotlinx.coroutines.yield()
            onProgress(index + 1, mediaToProcess.size)
            processSingleMedia(media)
        }
    }

    suspend fun processSingleMedia(media: MediaData) = withContext(Dispatchers.IO) {
        if (media.uri.startsWith("mock://")) {
            mockAnalyze(media)
            return@withContext
        }
        try {
            val bitmap = decodeSampledBitmapFromUri(context, Uri.parse(media.uri), 200, 200)
            if (bitmap != null) {
                val palette = Palette.from(bitmap).generate()
                getColorNameFromHsl(palette.dominantSwatch?.hsl)?.let { repository.saveTag(TagEntity(media.uri, it)) }

                val composition = calculateColorComposition(palette)
                val json = JSONObject().apply { composition.forEach { (k, v) -> put(k, v.toDouble()) } }
                
                val skinRatio = composition.getOrDefault("ピンク系", 0f) + composition.getOrDefault("オレンジ系", 0f)
                val colorAgeRating = when { skinRatio > 0.6f -> "R18"; skinRatio > 0.4f -> "R15"; else -> "SFW" }

                val current = repository.getMetadata(media.uri)
                val finalAgeRating = if (current?.ageRating != null && current.ageRating != "SFW") current.ageRating else colorAgeRating

                repository.saveMetadata(
                    MediaMetadataEntity(
                        uri = media.uri,
                        isFavorite = current?.isFavorite ?: false,
                        colorComposition = json.toString(),
                        ageRating = finalAgeRating,
                        folderName = current?.folderName ?: media.folderName
                    )
                )
            } else {
                val current = repository.getMetadata(media.uri)
                repository.saveMetadata(MediaMetadataEntity(uri = media.uri, isFavorite = current?.isFavorite ?: false, colorComposition = "{}", folderName = current?.folderName ?: media.folderName))
            }
        } catch (e: Exception) { Log.e("ColorTaggingService", "Error processing ${media.uri}", e) }
    }

    private suspend fun mockAnalyze(media: MediaData) {
        delay(100)
        val i = media.uri.substringAfter("mock://picsum/").substringBefore("?").toIntOrNull() ?: 0
        val colors = listOf("レッド系", "ブルー系", "グリーン系", "イエロー系", "ブラック系", "ホワイト系")
        val color = colors[i % colors.size]
        repository.saveTag(TagEntity(media.uri, color))
        val current = repository.getMetadata(media.uri)
        repository.saveMetadata(
            MediaMetadataEntity(
                uri = media.uri,
                isFavorite = current?.isFavorite ?: false,
                colorComposition = "{\"$color\": 1.0}",
                ageRating = current?.ageRating ?: "SFW",
                folderName = current?.folderName ?: media.folderName
            )
        )
    }

    private fun calculateColorComposition(palette: Palette): Map<String, Float> {
        val swatches = palette.swatches
        if (swatches.isEmpty()) return emptyMap()
        val totalPopulation = swatches.sumOf { it.population }.toFloat()
        val composition = mutableMapOf<String, Float>()
        swatches.forEach { swatch ->
            val colorName = getColorNameFromHsl(swatch.hsl)
            composition[colorName] = composition.getOrDefault(colorName, 0f) + (swatch.population / totalPopulation)
        }
        return composition
    }

    private fun getColorNameFromHsl(hsl: FloatArray?): String {
        if (hsl == null) return "その他"
        val h = hsl[0]; val s = hsl[1]; val l = hsl[2]
        return when {
            l < 0.15f -> "ブラック系"; l > 0.85f -> "ホワイト系"; s < 0.15f -> "グレー系"
            h in 0.0..20.0 || h in 335.0..360.0 -> "レッド系"; h in 20.0..50.0 -> "オレンジ系"; h in 50.0..70.0 -> "イエロー系"
            h in 70.0..160.0 -> "グリーン系"; h in 160.0..250.0 -> "ブルー系"; h in 250.0..300.0 -> "パープル系"; h in 300.0..335.0 -> "ピンク系"
            else -> "その他"
        }
    }

    private fun decodeSampledBitmapFromUri(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: Exception) { null }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2; val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) inSampleSize *= 2
        }
        return inSampleSize
    }
}

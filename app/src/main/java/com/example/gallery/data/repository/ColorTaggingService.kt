package com.example.gallery.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.palette.graphics.Palette
import android.util.Log
import com.example.gallery.data.local.dao.MediaDao
import com.example.gallery.data.local.entity.MediaMetadataEntity
import com.example.gallery.data.local.entity.TagEntity
import com.example.gallery.ui.MediaData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ColorTaggingService(
    private val context: Context,
    private val mediaDao: MediaDao
) {
    suspend fun performColorTagging(
        allMedia: List<MediaData>,
        onProgress: (Int, Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        Log.d("ColorTaggingService", "Starting performColorTagging. Input media count: ${allMedia.size}")
        
        // 色組成(MetadataEntity)がまだないものを解析対象とする
        val allMetadata = mediaDao.getAllMetadata()
        val analyzedUris = allMetadata.filter { it.colorComposition != null }.map { it.uri }.toSet()
        
        val imagesOnly = allMedia.filter { !it.isVideo }
        val mediaToProcess = imagesOnly.filter { it.uri !in analyzedUris }
        
        Log.d("ColorTaggingService", "Images count: ${imagesOnly.size}, Already analyzed: ${analyzedUris.size}, To process: ${mediaToProcess.size}")

        if (mediaToProcess.isEmpty()) {
            Log.d("ColorTaggingService", "No new media to process.")
            onProgress(0, 0)
            return@withContext
        }

        mediaToProcess.forEachIndexed { index, media ->
            kotlinx.coroutines.yield() // 中断ポイントを追加
            onProgress(index + 1, mediaToProcess.size)
            processSingleMedia(media)
            
            if (index % 20 == 0) {
                Log.d("ColorTaggingService", "Analyzed ${index + 1}/${mediaToProcess.size}")
            }
        }
    }

    suspend fun processSingleMedia(media: MediaData) = withContext(Dispatchers.IO) {
        try {
            val bitmap = decodeSampledBitmapFromUri(context, Uri.parse(media.uri), 200, 200)
            if (bitmap != null) {
                val palette = Palette.from(bitmap).generate()
                
                val colorTag = determineColorTag(palette)
                if (colorTag != null) {
                    Log.v("ColorTaggingService", "Tagging ${media.uri} with $colorTag")
                    mediaDao.insertTag(TagEntity(media.uri, colorTag))
                }

                val composition = calculateColorComposition(palette)
                val json = JSONObject()
                composition.forEach { (name, ratio) -> json.put(name, ratio.toDouble()) }
                
                // 肌色が多い場合の簡易的な年齢制限（目安）
                val skinRatio = composition.getOrDefault("ピンク系", 0f) + composition.getOrDefault("オレンジ系", 0f)
                val colorAgeRating = when {
                    skinRatio > 0.6f -> "R18"
                    skinRatio > 0.4f -> "R15"
                    else -> "SFW"
                }

                val existingMetadata = mediaDao.getMetadata(media.uri)
                // 既に SFW 以外（手動設定など）になっている場合は上書きしない
                val finalAgeRating = if (existingMetadata?.ageRating != null && existingMetadata.ageRating != "SFW") {
                    existingMetadata.ageRating
                } else {
                    colorAgeRating
                }

                mediaDao.insertMetadata(
                    MediaMetadataEntity(
                        uri = media.uri,
                        isFavorite = existingMetadata?.isFavorite ?: false,
                        colorComposition = json.toString(),
                        ageRating = finalAgeRating,
                        folderName = existingMetadata?.folderName ?: media.folderName
                    )
                )
            } else {
                Log.e("ColorTaggingService", "Failed to decode bitmap for: ${media.uri}")
                // デコードに失敗した場合も、解析済みとして扱う（無限ループ回避）
                val existingMetadata = mediaDao.getMetadata(media.uri)
                mediaDao.insertMetadata(
                    MediaMetadataEntity(
                        uri = media.uri,
                        isFavorite = existingMetadata?.isFavorite ?: false,
                        colorComposition = "{}", // 空の組成を保存
                        folderName = existingMetadata?.folderName ?: media.folderName
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("ColorTaggingService", "Error processing ${media.uri}", e)
        }
    }

    private fun calculateColorComposition(palette: Palette): Map<String, Float> {
        val swatches = palette.swatches
        if (swatches.isEmpty()) return emptyMap()

        val totalPopulation = swatches.sumOf { it.population }.toFloat()
        val composition = mutableMapOf<String, Float>()

        swatches.forEach { swatch ->
            val colorName = getColorNameFromHsl(swatch.hsl)
            val currentRatio = composition.getOrDefault(colorName, 0f)
            composition[colorName] = currentRatio + (swatch.population / totalPopulation)
        }

        return composition
    }

    private fun getColorNameFromHsl(hsl: FloatArray): String {
        val h = hsl[0]
        val s = hsl[1]
        val l = hsl[2]

        return when {
            l < 0.15f -> "ブラック系"
            l > 0.85f -> "ホワイト系"
            s < 0.15f -> "グレー系"
            h in 0.0..20.0 || h in 335.0..360.0 -> "レッド系"
            h in 20.0..50.0 -> "オレンジ系"
            h in 50.0..70.0 -> "イエロー系"
            h in 70.0..160.0 -> "グリーン系"
            h in 160.0..250.0 -> "ブルー系"
            h in 250.0..300.0 -> "パープル系"
            h in 300.0..335.0 -> "ピンク系"
            else -> "その他"
        }
    }

    private fun determineColorTag(palette: Palette): String? {
        val dominantSwatch = palette.dominantSwatch ?: return null
        return getColorNameFromHsl(dominantSwatch.hsl)
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
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}

package com.example.gallery.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Size
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object ThumbnailUtils {
    private const val TAG = "ThumbnailUtils"
    private const val THUMB_SIZE = 256 // サムネイルのサイズ（px）

    fun getThumbnailFile(context: Context, uri: String): File {
        val cacheDir = File(context.cacheDir, "custom_thumbnails")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        // URIをハッシュ化してファイル名にする
        val hash = MessageDigest.getInstance("MD5")
            .digest(uri.toByteArray())
            .joinToString("") { "%02x".format(it) }
        
        return File(cacheDir, "thumb_$hash.webp")
    }

    fun generateThumbnailIfMissing(context: Context, uriString: String): Boolean {
        if (uriString.startsWith("http")) return false

        val file = getThumbnailFile(context, uriString)
        if (file.exists()) return true

        return try {
            val uri = Uri.parse(uriString)
            val mimeType = context.contentResolver.getType(uri) ?: ""
            val isVideo = mimeType.startsWith("video/")

            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    context.contentResolver.loadThumbnail(uri, Size(THUMB_SIZE, THUMB_SIZE), null)
                } catch (e: Exception) {
                    // loadThumbnailが失敗した場合のフォールバック
                    if (isVideo) {
                        getVideoFrame(context, uri)
                    } else {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val original = BitmapFactory.decodeStream(inputStream)
                        inputStream?.close()
                        original?.let {
                            ThumbnailUtils.extractThumbnail(it, THUMB_SIZE, THUMB_SIZE)
                        }
                    }
                }
            } else {
                // 旧バージョン用
                if (isVideo) {
                    getVideoFrame(context, uri)
                } else {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val original = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    original?.let {
                        ThumbnailUtils.extractThumbnail(it, THUMB_SIZE, THUMB_SIZE)
                    }
                }
            }

            if (bitmap != null) {
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 75, out)
                }
                bitmap.recycle()
                true
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate thumbnail for $uriString", e)
            false
        }
    }

    private fun getVideoFrame(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            // content:// URI の場合、ParcelFileDescriptor を使用するのが最も確実
            val pfd = try {
                context.contentResolver.openFileDescriptor(uri, "r")
            } catch (e: Exception) {
                null
            }

            if (pfd != null) {
                pfd.use {
                    retriever.setDataSource(it.fileDescriptor)
                }
            } else {
                // フォールバック: 直接 URI を渡す（一部のデバイスや URI スキームで必要）
                retriever.setDataSource(context, uri)
            }
            
            // XのGIF(短い動画)対策: 1秒地点(1000000us)だと動画が終わっている可能性があるため、
            // まずは0秒(先頭)を、失敗した場合は closest で取得を試みる
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get video frame for $uri: ${e.message}")
            null
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
    }
}

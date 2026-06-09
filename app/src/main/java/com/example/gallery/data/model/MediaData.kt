package com.example.gallery.data.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import android.os.Bundle

@Immutable
data class MediaData(
    val uri: String,
    val dateAdded: Long,
    val mimeType: String? = null,
    val duration: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val fileSize: Long = 0,
    val fileName: String = "",
    val folderName: String = "" // フォルダ名を保持
) {
    val id: Long get() = uri.substringAfterLast("/").toLongOrNull() ?: -1L
    val isGif: Boolean get() = mimeType == "image/gif" || uri.lowercase().endsWith(".gif") || uri.contains("gif", ignoreCase = true)
    val isVideo: Boolean get() = mimeType?.startsWith("video") == true || uri.contains("video", ignoreCase = true)
    val isPortrait: Boolean get() = height > width && width > 0
    val isLandscape: Boolean get() = width > height && height > 0

    fun toBundle(): Bundle = Bundle().apply {
        putString("uri", uri)
        putLong("dateAdded", dateAdded)
        putString("mimeType", mimeType)
        putLong("duration", duration)
        putInt("width", width)
        putInt("height", height)
        putLong("fileSize", fileSize)
        putString("fileName", fileName)
        putString("folderName", folderName)
    }

    companion object {
        fun fromBundle(bundle: Bundle): MediaData = MediaData(
            uri = bundle.getString("uri") ?: "",
            dateAdded = bundle.getLong("dateAdded"),
            mimeType = bundle.getString("mimeType"),
            duration = bundle.getLong("duration"),
            width = bundle.getInt("width"),
            height = bundle.getInt("height"),
            fileSize = bundle.getLong("fileSize"),
            fileName = bundle.getString("fileName") ?: "",
            folderName = bundle.getString("folderName") ?: ""
        )

        // Listを保存・復元するためのSaver
        val ListOnlySaver: Saver<List<MediaData>, *> = Saver<List<MediaData>, ArrayList<Bundle>>(
            save = { list ->
                val arrayList = ArrayList<Bundle>(list.size)
                list.forEach { arrayList.add(it.toBundle()) }
                arrayList
            },
            restore = { arrayList ->
                arrayList.map { fromBundle(it) }
            }
        )

        val ListSaver: Saver<MutableState<List<MediaData>>, *> = Saver<MutableState<List<MediaData>>, ArrayList<Bundle>>(
            save = { state ->
                val list = state.value
                val arrayList = ArrayList<Bundle>(list.size)
                list.forEach { arrayList.add(it.toBundle()) }
                arrayList
            },
            restore = { arrayList ->
                mutableStateOf(arrayList.map { fromBundle(it) })
            }
        )
    }
}

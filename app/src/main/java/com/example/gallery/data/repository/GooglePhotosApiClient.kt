package com.example.gallery.data.repository

import com.example.gallery.data.model.MediaData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class GooglePhotoItem(
    val id: String,
    val mediaData: MediaData,
    val thumbnailUrl: String
)

class GooglePhotosApiClient(
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    suspend fun listMediaItems(
        accessToken: String,
        pageToken: String? = null,
        pageSize: Int = 50
    ): GooglePhotosPage = withContext(Dispatchers.IO) {
        val url = buildString {
            append("https://photoslibrary.googleapis.com/v1/mediaItems?pageSize=")
            append(pageSize.coerceIn(1, 100))
            if (!pageToken.isNullOrBlank()) {
                append("&pageToken=")
                append(pageToken)
            }
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Google Photos API ${response.code}: $body")
            }

            val json = JSONObject(body)
            val items = json.optJSONArray("mediaItems")
            val mediaItems = buildList {
                if (items != null) {
                    for (index in 0 until items.length()) {
                        val item = items.optJSONObject(index) ?: continue
                        val baseUrl = item.optString("baseUrl")
                        if (baseUrl.isBlank()) continue

                        val mimeType = item.optString("mimeType").takeIf { it.isNotBlank() }
                        val metadata = item.optJSONObject("mediaMetadata")
                        val width = metadata?.optString("width")?.toIntOrNull() ?: 0
                        val height = metadata?.optString("height")?.toIntOrNull() ?: 0
                        val creationTime = metadata?.optString("creationTime")
                        val dateAdded = parseGooglePhotosTimeMillis(creationTime)
                        val isVideo = mimeType?.startsWith("video") == true
                        val contentUrl = if (isVideo) "$baseUrl=dv" else "$baseUrl=w2048-h2048"

                        add(
                            GooglePhotoItem(
                                id = item.optString("id"),
                                thumbnailUrl = "$baseUrl=w360-h360-c",
                                mediaData = MediaData(
                                    uri = contentUrl,
                                    dateAdded = dateAdded,
                                    mimeType = mimeType,
                                    width = width,
                                    height = height,
                                    fileName = item.optString("filename"),
                                    folderName = "Google Photos"
                                )
                            )
                        )
                    }
                }
            }

            GooglePhotosPage(
                items = mediaItems,
                nextPageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
            )
        }
    }
}

data class GooglePhotosPage(
    val items: List<GooglePhotoItem>,
    val nextPageToken: String?
)

private fun parseGooglePhotosTimeMillis(raw: String?): Long {
    if (raw.isNullOrBlank()) return System.currentTimeMillis()
    return runCatching {
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        formatter.timeZone = java.util.TimeZone.getTimeZone("UTC")
        formatter.parse(raw)?.time ?: System.currentTimeMillis()
    }.recoverCatching {
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        formatter.timeZone = java.util.TimeZone.getTimeZone("UTC")
        formatter.parse(raw)?.time ?: System.currentTimeMillis()
    }.getOrDefault(System.currentTimeMillis())
}

package com.example.gallery.util

import java.util.Locale

object VideoDownloadUrlUtils {
    private val xStatusIdRegex = Regex("""/status/(\d+)""")

    fun isXStatusUrl(value: String): Boolean {
        val lower = value.lowercase(Locale.US)
        return (lower.contains("x.com") || lower.contains("twitter.com")) && lower.contains("/status/")
    }

    fun buildXApiUrls(baseUrl: String): List<String> {
        val urlCandidates = mutableListOf<String>()
        listOf("api.fxtwitter.com", "api.vxtwitter.com", "api.fixupx.com").forEach { host ->
            urlCandidates += when {
                baseUrl.contains("x.com") -> baseUrl.replace("x.com", host)
                baseUrl.contains("twitter.com") -> baseUrl.replace("twitter.com", host)
                else -> baseUrl
            }
        }
        xStatusIdRegex.find(baseUrl)?.groupValues?.getOrNull(1)?.let { statusId ->
            urlCandidates += "https://api.fxtwitter.com/status/$statusId"
            urlCandidates += "https://api.vxtwitter.com/status/$statusId"
            urlCandidates += "https://api.fixupx.com/status/$statusId"
        }
        return urlCandidates.distinct()
    }

    fun isDirectMediaUrl(value: String): Boolean {
        if (value.isBlank()) return false
        val lower = value.lowercase(Locale.US)
        return isMp4Url(lower) ||
            lower.contains(".gif") ||
            lower.contains("format=gif") ||
            lower.contains(".webp") ||
            lower.contains("format=webp") ||
            lower.contains(".jpg") ||
            lower.contains(".jpeg") ||
            lower.contains("format=jpg") ||
            lower.contains("format=jpeg") ||
            lower.contains(".png") ||
            lower.contains("format=png")
    }

    fun isMp4Url(value: String): Boolean = value.lowercase(Locale.US).contains(".mp4")

    fun isGifUrl(value: String): Boolean {
        val lower = value.lowercase(Locale.US)
        return lower.contains(".gif") || lower.contains("format=gif")
    }

    fun isLikelyXGifVideoUrl(value: String): Boolean {
        val lower = value.lowercase(Locale.US)
        return lower.contains("video.twimg.com/tweet_video/") ||
            lower.contains("/tweet_video/") ||
            lower.contains("animated_gif")
    }

    fun detectMediaType(url: String, contentTypeHeader: String?): Pair<String, String> {
        val lowerUrl = url.lowercase(Locale.US)
        val contentType = contentTypeHeader
            ?.substringBefore(";")
            ?.trim()
            ?.lowercase(Locale.US)
            .orEmpty()

        return when {
            contentType == "image/gif" || lowerUrl.contains(".gif") || lowerUrl.contains("format=gif") -> "gif" to "image/gif"
            contentType == "image/webp" || lowerUrl.contains(".webp") || lowerUrl.contains("format=webp") -> "webp" to "image/webp"
            contentType == "image/png" || lowerUrl.contains(".png") || lowerUrl.contains("format=png") -> "png" to "image/png"
            contentType == "image/jpeg" ||
                lowerUrl.contains(".jpg") ||
                lowerUrl.contains(".jpeg") ||
                lowerUrl.contains("format=jpg") ||
                lowerUrl.contains("format=jpeg") -> "jpg" to "image/jpeg"
            contentType == "video/mp4" || lowerUrl.contains(".mp4") -> "mp4" to "video/mp4"
            else -> "mp4" to "video/mp4"
        }
    }
}

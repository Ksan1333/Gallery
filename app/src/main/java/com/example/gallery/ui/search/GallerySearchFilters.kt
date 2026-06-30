package com.example.gallery.ui.search

import com.example.gallery.data.local.entity.MediaMetadataSummary
import com.example.gallery.data.model.MediaData
import com.example.gallery.service.TagTranslationService
import com.example.gallery.ui.state.AgeRatingFilter
import com.example.gallery.ui.state.GallerySearchMatchMode
import com.example.gallery.ui.state.GallerySearchMediaType
import com.example.gallery.ui.state.GallerySearchStorageType

private val searchTokenSeparator = Regex("[\\s,、]+")

fun splitGallerySearchQuery(query: String): List<String> =
    query
        .split(searchTokenSeparator)
        .map { it.trim() }
        .filter { it.isNotBlank() }

fun filterGallerySearchResults(
    mediaItems: List<MediaData>,
    metadataByUri: Map<String, MediaMetadataSummary>,
    tagsByUri: Map<String, List<String>>,
    query: String,
    selectedTags: Set<String>,
    matchMode: GallerySearchMatchMode,
    selectedMediaTypes: Set<GallerySearchMediaType>,
    selectedFolders: Set<String>,
    selectedStorageTypes: Set<GallerySearchStorageType> = emptySet(),
    selectedAgeRatings: Set<AgeRatingFilter>,
    favoritesOnly: Boolean
): List<MediaData> {
    val tokens = splitGallerySearchQuery(query)
    val hasTextOrTagCondition = tokens.isNotEmpty() || selectedTags.isNotEmpty()

    return mediaItems.filter { item ->
        val metadata = metadataByUri[item.uri]
        val itemTags = tagsByUri[item.uri].orEmpty()

        val mediaTypeMatches = selectedMediaTypes.isEmpty() || selectedMediaTypes.any { type ->
            when (type) {
                GallerySearchMediaType.IMAGE -> !item.isVideo && !item.isGif
                GallerySearchMediaType.GIF -> item.isGif
                GallerySearchMediaType.VIDEO -> item.isVideo
            }
        }
        val ageMatches = selectedAgeRatings.isEmpty() ||
            selectedAgeRatings.any { rating -> metadata?.ageRating == rating.name }
        val folderName = item.folderName.ifBlank { metadata?.folderName.orEmpty() }
        val folderMatches = selectedFolders.isEmpty() || folderName in selectedFolders
        val storageMatches = selectedStorageTypes.isEmpty() || item.galleryStorageType() in selectedStorageTypes
        val favoriteMatches = !favoritesOnly || metadata?.isFavorite == true
        val contentMatches = if (!hasTextOrTagCondition) {
            true
        } else {
            val tokenMatches = matchTokens(tokens, item, metadata, itemTags, matchMode)
            val tagMatches = matchSelectedTags(selectedTags, itemTags, matchMode)
            when (matchMode) {
                GallerySearchMatchMode.AND -> tokenMatches && tagMatches
                GallerySearchMatchMode.OR -> tokenMatches || tagMatches
            }
        }

        mediaTypeMatches && ageMatches && folderMatches && storageMatches && favoriteMatches && contentMatches
    }
}

fun MediaData.galleryStorageType(): GallerySearchStorageType {
    val normalizedUri = uri.lowercase()
    val mediaVolume = normalizedUri
        .substringAfter("content://media/", missingDelimiterValue = "")
        .substringBefore("/")

    return when {
        mediaVolume.isBlank() ||
            mediaVolume == "external" ||
            mediaVolume == "external_primary" ||
            mediaVolume == "internal" -> GallerySearchStorageType.INTERNAL
        normalizedUri.startsWith("file:///storage/emulated/") ||
            normalizedUri.startsWith("/storage/emulated/") -> GallerySearchStorageType.INTERNAL
        else -> GallerySearchStorageType.SD_CARD
    }
}

private fun matchTokens(
    tokens: List<String>,
    item: MediaData,
    metadata: MediaMetadataSummary?,
    itemTags: List<String>,
    matchMode: GallerySearchMatchMode
): Boolean {
    if (tokens.isEmpty()) return matchMode == GallerySearchMatchMode.AND

    fun matchesToken(token: String): Boolean =
        item.fileName.contains(token, ignoreCase = true) ||
            metadata?.fileName?.contains(token, ignoreCase = true) == true ||
            item.folderName.contains(token, ignoreCase = true) ||
            metadata?.folderName?.contains(token, ignoreCase = true) == true ||
            itemTags.any { tag ->
                tag.contains(token, ignoreCase = true) ||
                    TagTranslationService.translate(tag).contains(token, ignoreCase = true)
            }

    return when (matchMode) {
        GallerySearchMatchMode.AND -> tokens.all(::matchesToken)
        GallerySearchMatchMode.OR -> tokens.any(::matchesToken)
    }
}

private fun matchSelectedTags(
    selectedTags: Set<String>,
    itemTags: List<String>,
    matchMode: GallerySearchMatchMode
): Boolean {
    if (selectedTags.isEmpty()) return matchMode == GallerySearchMatchMode.AND

    return when (matchMode) {
        GallerySearchMatchMode.AND -> selectedTags.all { it in itemTags }
        GallerySearchMatchMode.OR -> selectedTags.any { it in itemTags }
    }
}

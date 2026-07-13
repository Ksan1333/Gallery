package com.example.gallery.util

data class AdjacentSimilarityGroup<T>(
    val items: List<T>,
    val minimumSimilarity: Float
)

fun <T> buildAdjacentSimilarityGroups(
    items: List<T>,
    threshold: Float,
    similarity: (previous: T, current: T) -> Float?
): List<AdjacentSimilarityGroup<T>> {
    if (items.size < 2) return emptyList()

    val safeThreshold = threshold.coerceIn(-1f, 1f)
    val groups = ArrayList<AdjacentSimilarityGroup<T>>()
    var currentItems = arrayListOf(items.first())
    var currentMinimum = 1f

    fun finishGroup() {
        if (currentItems.size >= 2) {
            groups += AdjacentSimilarityGroup(currentItems.toList(), currentMinimum)
        }
    }

    items.drop(1).forEach { item ->
        val adjacentSimilarity = similarity(currentItems.last(), item)
        if (adjacentSimilarity != null && adjacentSimilarity >= safeThreshold) {
            currentItems += item
            currentMinimum = minOf(currentMinimum, adjacentSimilarity)
        } else {
            finishGroup()
            currentItems = arrayListOf(item)
            currentMinimum = 1f
        }
    }
    finishGroup()
    return groups
}

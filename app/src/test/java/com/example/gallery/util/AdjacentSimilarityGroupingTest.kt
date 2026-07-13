package com.example.gallery.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdjacentSimilarityGroupingTest {
    @Test
    fun chainsAllAdjacentItemsWithoutCountLimit() {
        val items = (1..32).toList()

        val groups = buildAdjacentSimilarityGroups(items, threshold = 0.6f) { _, _ -> 0.61f }

        assertEquals(1, groups.size)
        assertEquals(items, groups.single().items)
        assertEquals(0.61f, groups.single().minimumSimilarity, 0.0001f)
    }

    @Test
    fun includesSimilarityExactlyAtSixtyPercent() {
        val groups = buildAdjacentSimilarityGroups(listOf("old", "new"), 0.6f) { _, _ -> 0.6f }

        assertEquals(listOf("old", "new"), groups.single().items)
    }

    @Test
    fun splitsAtFirstAdjacentPairBelowThreshold() {
        val scores = mapOf((1 to 2) to 0.8f, (2 to 3) to 0.59f, (3 to 4) to 0.7f)

        val groups = buildAdjacentSimilarityGroups(listOf(1, 2, 3, 4), 0.6f) { previous, current ->
            scores[previous to current]
        }

        assertEquals(listOf(listOf(1, 2), listOf(3, 4)), groups.map { it.items })
    }

    @Test
    fun missingVectorBreaksTheChain() {
        val groups = buildAdjacentSimilarityGroups(listOf(1, 2, 3), 0.6f) { previous, _ ->
            if (previous == 1) null else 0.9f
        }

        assertEquals(1, groups.size)
        assertEquals(listOf(2, 3), groups.single().items)
        assertTrue(groups.single().minimumSimilarity >= 0.6f)
    }
}

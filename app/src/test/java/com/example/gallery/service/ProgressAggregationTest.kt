package com.example.gallery.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressAggregationTest {
    @Test
    fun selectRepresentativeProgressOperationUsesHighestProgress() {
        val slow = OperationState(id = "slow", title = "Slow", progress = 0.25f)
        val fast = OperationState(id = "fast", title = "Fast", progress = 0.82f)
        val middle = OperationState(id = "middle", title = "Middle", progress = 0.5f)

        assertEquals(fast, selectRepresentativeProgressOperation(listOf(slow, fast, middle)))
    }

    @Test
    fun selectRepresentativeProgressOperationReturnsNullForEmptyList() {
        assertNull(selectRepresentativeProgressOperation(emptyList()))
    }
}

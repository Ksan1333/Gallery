package com.example.gallery.ui.component

data class TapZoneSpec(
    val id: String,
    val row: Int,
    val column: Int,
    val rowSpan: Int = 1,
    val columnSpan: Int = 1
)

fun tapZoneCountForLayout(layout: String?, fallback: Int = 3): Int {
    return when (layout) {
        "ELEVEN", "ELEVEN_SPLIT" -> 11
        "SEVEN", "SEVEN_SPLIT" -> 7
        "FIVE", "FIVE_SPLIT" -> 5
        "FOUR", "FOUR_SPLIT" -> 4
        "THREE", "THREE_SPLIT" -> 3
        else -> normalizeTapZoneCount(fallback)
    }
}

fun normalizeTapZoneCount(zoneCount: Int): Int {
    return when (zoneCount) {
        11 -> 11
        7 -> 7
        5 -> 5
        4 -> 4
        else -> 3
    }
}

fun tapZoneSpecs(zoneCount: Int): List<TapZoneSpec> {
    return when (normalizeTapZoneCount(zoneCount)) {
        11 -> listOf(
            TapZoneSpec("top_start", 0, 0, columnSpan = 2),
            TapZoneSpec("top_center", 0, 2),
            TapZoneSpec("top_end", 0, 3, columnSpan = 2),
            TapZoneSpec("left_upper", 1, 0, rowSpan = 2),
            TapZoneSpec("left_lower", 3, 0),
            TapZoneSpec("center", 1, 1, rowSpan = 3, columnSpan = 3),
            TapZoneSpec("right_upper", 1, 4, rowSpan = 2),
            TapZoneSpec("right_lower", 3, 4),
            TapZoneSpec("bottom_start", 4, 0, columnSpan = 2),
            TapZoneSpec("bottom_center", 4, 2),
            TapZoneSpec("bottom_end", 4, 3, columnSpan = 2)
        )
        7 -> listOf(
            TapZoneSpec("top_start", 0, 0, columnSpan = 3),
            TapZoneSpec("top_end", 0, 3, columnSpan = 2),
            TapZoneSpec("left", 1, 0, rowSpan = 3),
            TapZoneSpec("center", 1, 1, rowSpan = 3, columnSpan = 3),
            TapZoneSpec("right", 1, 4, rowSpan = 3),
            TapZoneSpec("bottom_start", 4, 0, columnSpan = 3),
            TapZoneSpec("bottom_end", 4, 3, columnSpan = 2)
        )
        5 -> listOf(
            TapZoneSpec("top", 0, 0, columnSpan = 5),
            TapZoneSpec("left", 1, 0, rowSpan = 3),
            TapZoneSpec("center", 1, 1, rowSpan = 3, columnSpan = 3),
            TapZoneSpec("right", 1, 4, rowSpan = 3),
            TapZoneSpec("bottom", 4, 0, columnSpan = 5)
        )
        4 -> listOf(
            TapZoneSpec("left", 0, 0, rowSpan = 5),
            TapZoneSpec("center", 1, 1, rowSpan = 3, columnSpan = 3),
            TapZoneSpec("bottom", 4, 1, columnSpan = 3),
            TapZoneSpec("right", 0, 4, rowSpan = 5)
        )
        else -> listOf(
            TapZoneSpec("left", 0, 0, rowSpan = 5),
            TapZoneSpec("center", 1, 1, rowSpan = 3, columnSpan = 3),
            TapZoneSpec("right", 0, 4, rowSpan = 5)
        )
    }
}

fun tapZoneIndexAt(
    zoneCount: Int,
    width: Float,
    height: Float,
    x: Float,
    y: Float
): Int {
    val specs = tapZoneSpecs(zoneCount)
    val cellWidth = width.coerceAtLeast(1f) / 5f
    val cellHeight = height.coerceAtLeast(1f) / 5f
    val safeX = x.coerceIn(0f, width.coerceAtLeast(1f))
    val safeY = y.coerceIn(0f, height.coerceAtLeast(1f))
    return specs.indexOfFirst { spec ->
        val left = spec.column * cellWidth
        val top = spec.row * cellHeight
        val right = left + spec.columnSpan * cellWidth
        val bottom = top + spec.rowSpan * cellHeight
        safeX >= left && safeX <= right && safeY >= top && safeY <= bottom
    }.takeIf { it >= 0 } ?: (specs.indexOfFirst { it.id == "center" }.takeIf { it >= 0 } ?: 0)
}

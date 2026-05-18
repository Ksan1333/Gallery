package com.example.gallery.ui.component

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.gallery.ui.MediaData
import com.example.gallery.ui.AgeRatingFilter
import kotlinx.coroutines.isActive
import kotlin.random.Random

@Composable
fun FloatingRandomImage(
    mediaList: List<MediaData>,
    ageRatingFilter: AgeRatingFilter = AgeRatingFilter.SFW,
    metadataMap: Map<String, com.example.gallery.data.local.entity.MediaMetadataEntity> = emptyMap()
) {
    if (mediaList.isEmpty()) return

    // 年齢制限で厳格にフィルタリング
    val filteredList = remember(mediaList, ageRatingFilter, metadataMap) {
        mediaList.filter { item ->
            val meta = metadataMap[item.uri]
            val rating = meta?.ageRating ?: "SFW"
            when (ageRatingFilter) {
                AgeRatingFilter.ALL -> true
                AgeRatingFilter.SFW -> rating == "SFW"
                AgeRatingFilter.R15 -> rating == "R15"
                AgeRatingFilter.R18 -> rating == "R18"
            }
        }
    }

    if (filteredList.isEmpty()) return

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val maxHeightPx = with(LocalDensity.current) { maxHeight.toPx() }

        // 複数の画像を浮かせる
        repeat(3) { index ->
            key(index) {
                BouncingFloatingImage(
                    filteredList = filteredList,
                    maxWidthPx = maxWidthPx,
                    maxHeightPx = maxHeightPx
                )
            }
        }
    }
}

@Composable
fun BouncingFloatingImage(
    filteredList: List<MediaData>,
    maxWidthPx: Float,
    maxHeightPx: Float
) {
    var currentMedia by remember { mutableStateOf(filteredList.randomOrNull()) }
    val sizeDp = remember { Random.nextInt(120, 200).dp }
    val sizePx = with(LocalDensity.current) { sizeDp.toPx() }

    var posX by remember { mutableFloatStateOf(Random.nextFloat() * (maxWidthPx - sizePx)) }
    var posY by remember { mutableFloatStateOf(Random.nextFloat() * (maxHeightPx - sizePx)) }
    var velX by remember { mutableFloatStateOf((Random.nextFloat() * 2f + 1f) * if (Random.nextBoolean()) 1f else -1f) }
    var velY by remember { mutableFloatStateOf((Random.nextFloat() * 2f + 1f) * if (Random.nextBoolean()) 1f else -1f) }
    var rotation by remember { mutableFloatStateOf(Random.nextFloat() * 360f) }

    LaunchedEffect(filteredList) {
        val infiniteTransition = 1000 // Dummy to trigger effect
        while (isActive) {
            withFrameMillis { _ ->
                posX += velX
                posY += velY
                rotation += 0.2f

                var bounced = false
                if (posX <= 0f) {
                    posX = 0f
                    velX = -velX
                    bounced = true
                } else if (posX + sizePx >= maxWidthPx) {
                    posX = maxWidthPx - sizePx
                    velX = -velX
                    bounced = true
                }

                if (posY <= 0f) {
                    posY = 0f
                    velY = -velY
                    bounced = true
                } else if (posY + sizePx >= maxHeightPx) {
                    posY = maxHeightPx - sizePx
                    velY = -velY
                    bounced = true
                }

                if (bounced) {
                    // 壁にぶつかったら別の画像に切り替え
                    currentMedia = filteredList.randomOrNull()
                }
            }
        }
    }

    currentMedia?.let { media ->
        Surface(
            modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset(posX.toInt(), posY.toInt()) }
                .size(sizeDp)
                .graphicsLayer { rotationZ = rotation }
                .clip(RoundedCornerShape(16.dp)),
            color = Color.Black.copy(alpha = 0.3f),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Image(
                painter = rememberAsyncImagePainter(media.uri),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

package com.example.gallery.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.gallery.ui.MediaData
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun FloatingRandomImage(mediaList: List<MediaData>) {
    if (mediaList.isEmpty()) return

    val context = LocalContext.current
    val imageSize = 150.dp

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidthValue = maxWidth.value
        val screenHeightValue = maxHeight.value
        val imageSizeValue = imageSize.value

        var currentMedia by remember { mutableStateOf(mediaList.random()) }
        
        // 初期位置を画面全体のランダムな位置に（はみ出さないように制限）
        var posX by remember { mutableFloatStateOf(Random.nextFloat() * (screenWidthValue - imageSizeValue).coerceAtLeast(0f)) }
        var posY by remember { mutableFloatStateOf(Random.nextFloat() * (screenHeightValue - imageSizeValue).coerceAtLeast(0f)) }
        
        // 速度(dp/frame)
        var velX by remember { mutableFloatStateOf(if (Random.nextBoolean()) 2.5f else -2.5f) }
        var velY by remember { mutableFloatStateOf(if (Random.nextBoolean()) 2.5f else -2.5f) }

        LaunchedEffect(mediaList, screenWidthValue, screenHeightValue) {
            while (true) {
                posX += velX
                posY += velY

                var bounced = false
                
                // 左端・右端の判定
                if (posX <= 0f) {
                    posX = 0f
                    velX = -velX
                    bounced = true
                } else if (posX >= screenWidthValue - imageSizeValue) {
                    posX = screenWidthValue - imageSizeValue
                    velX = -velX
                    bounced = true
                }
                
                // 上端・下端の判定
                if (posY <= 0f) {
                    posY = 0f
                    velY = -velY
                    bounced = true
                } else if (posY >= screenHeightValue - imageSizeValue) {
                    posY = screenHeightValue - imageSizeValue
                    velY = -velY
                    bounced = true
                }

                if (bounced) {
                    currentMedia = mediaList.random()
                }
                
                delay(16) // 約60fps
            }
        }

        Image(
            painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(context)
                    .data(currentMedia.uri)
                    .crossfade(true)
                    .build()
            ),
            contentDescription = null,
            modifier = Modifier
                .offset(x = posX.dp, y = posY.dp)
                .size(imageSize)
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
    }
}

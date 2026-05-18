package com.example.gallery.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

@Composable
fun TooltipWrapper(
    description: String,
    modifier: Modifier = Modifier,
    showExternally: Boolean = false,
    content: @Composable () -> Unit
) {
    // 外部からのトリガーか、内部での状態保持
    var showTooltipLocal by remember { mutableStateOf(false) }
    val isVisible = showExternally || showTooltipLocal

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        content()

        if (isVisible) {
            Popup(
                alignment = Alignment.BottomCenter,
                offset = androidx.compose.ui.unit.IntOffset(0, -100), // 下部に表示（ナビゲーションバーより少し上）
                properties = PopupProperties(
                    focusable = false, 
                    dismissOnClickOutside = true,
                    clippingEnabled = false,
                    usePlatformDefaultWidth = false // タップイベントの吸い込み防止のため
                )
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp) // 画面下端からの余白
                ) {
                    Text(
                        text = description,
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            LaunchedEffect(isVisible) {
                if (isVisible) {
                    delay(2000)
                    showTooltipLocal = false
                    // 外部からの場合は呼び出し元で制御してもらうが、
                    // 2秒後に消える挙動を維持したい。
                }
            }
        }
    }
}

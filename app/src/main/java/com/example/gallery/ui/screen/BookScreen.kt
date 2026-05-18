package com.example.gallery.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.gallery.ui.AppConstants

@Composable
fun BookScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppConstants.BackgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "本 / Zip / Pdf",
                color = Color.White,
                fontSize = AppConstants.HeaderFontSize
            )
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "ZipやPdfの読み取り機能（準備中）",
                color = Color.Gray,
                fontSize = AppConstants.BodyFontSize
            )
        }
    }
}

package com.example.gallery.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.gallery.ui.AppConstants

@Composable
fun PinterestScreen() {
    var isLoggedIn by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppConstants.BackgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black) // タイトル背景を黒に
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(AppConstants.HeaderHeight)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Pinterest",
                color = Color.White,
                fontSize = AppConstants.HeaderFontSize
            )
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Pinterest連携",
                    color = Color.White,
                    fontSize = AppConstants.TitleFontSize
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (!isLoggedIn) {
                    Button(onClick = { isLoggedIn = true }) {
                        Text("Pinterestにログイン")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "ログインして保存した画像を表示します",
                        color = Color.Gray,
                        fontSize = AppConstants.SmallFontSize
                    )
                } else {
                    Text(
                        text = "Pinterestから取得した画像がここに表示されます",
                        color = Color.White,
                        fontSize = AppConstants.BodyFontSize
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { isLoggedIn = false }) {
                        Text("ログアウト")
                    }
                }
            }
        }
    }
}

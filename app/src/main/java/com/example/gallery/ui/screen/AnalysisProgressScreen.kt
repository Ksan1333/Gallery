package com.example.gallery.ui.screen

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.GalleryState
import com.example.gallery.service.GlobalOperationService
import com.example.gallery.service.AnalysisService

@Composable
fun AnalysisProgressScreen(
    galleryState: GalleryState,
    analysisType: String = "AI_TAGGING",
    periodDays: Int = -1,
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isProcessing by GlobalOperationService.isProcessing.collectAsState()
    val isCancelRequested by GlobalOperationService.isCancelRequested.collectAsState()

    // フォアグラウンドサービスを開始
    LaunchedEffect(Unit) {
        if (!isProcessing) {
            AnalysisService.start(context, analysisType, periodDays)
        }
    }

    // キャンセル要求を監視して戻る
    LaunchedEffect(isCancelRequested) {
        if (isCancelRequested) {
            onCancel()
        }
    }

    // 処理完了を監視して戻る
    LaunchedEffect(isProcessing) {
        // 解析中から非解析中に変わった（＝完了した）場合
        if (!isProcessing) {
            onComplete()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(AppConstants.BackgroundColor)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = Color.White)
            Spacer(Modifier.height(16.dp))
            Text("バックグラウンドで解析を実行中です...", color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text("このまま他の画面へ移動しても解析は継続されます。", color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onComplete) {
                Text("ギャラリーに戻る")
            }
        }
    }
}

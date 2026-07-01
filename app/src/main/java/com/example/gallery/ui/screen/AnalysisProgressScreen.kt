package com.example.gallery.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import com.example.gallery.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.gallery.ui.AppConstants
import com.example.gallery.service.GlobalOperationService
import com.example.gallery.service.AnalysisService

@Composable
fun AnalysisProgressScreen(
    analysisType: String = "AI_TAGGING",
    periodDays: Int = -1,
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isProcessing by GlobalOperationService.isProcessing(analysisType).collectAsState(initial = false)
    val isCancelRequested by GlobalOperationService.isCancelRequested(analysisType).collectAsState(initial = false)

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
    var hasStarted by remember { mutableStateOf(false) }
    LaunchedEffect(isProcessing) {
        if (isProcessing) {
            hasStarted = true
        }
        // 解析中から非解析中に変わった（＝完了した）場合
        if (hasStarted && !isProcessing) {
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
            Text(stringResource(R.string.analysis_bg_running), color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.analysis_keep_moving), color = colorResource(R.color.gray), fontSize = AppConstants.SmallFontSize)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onComplete) {
                Text(stringResource(R.string.analysis_back_to_gallery))
            }
        }
    }
}

package com.example.gallery.ui.screen

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.GalleryState
import com.example.gallery.ui.MediaData
import com.example.gallery.ui.AgeRatingFilter
import com.example.gallery.data.local.entity.MediaMetadataEntity
import com.example.gallery.service.GlobalOperationService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

@Composable
fun AnalysisProgressScreen(
    galleryState: GalleryState,
    analysisType: String = "AI_TAGGING", // "AI_TAGGING", "COLOR_VECTOR", "AUTO_RATING"
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var analysisJob by remember { mutableStateOf<Job?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    var isFinished by remember { mutableStateOf(false) }
    var processedCount by remember { mutableIntStateOf(0) }
    var totalCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        analysisJob = scope.launch {
            try {
                val operationTitle = when(analysisType) {
                    "AI_TAGGING" -> "AIタグ解析中..."
                    "COLOR_VECTOR" -> "カラーベクトル解析中..."
                    "AUTO_RATING" -> "年齢制限自動振分中..."
                    else -> "処理中..."
                }
                GlobalOperationService.startOperation(operationTitle, tag = analysisType)

                // 解析に必要なモデルを確認・ダウンロード
                if (analysisType == "AI_TAGGING" || analysisType == "COLOR_VECTOR") {
                    val downloadSuccess = com.example.gallery.util.ModelDownloader.downloadAllModels(context) { progress ->
                        GlobalOperationService.updateProgress(progress * 0.1f, "AIモデルを準備中...") // 最初の10%をダウンロードに割り当て
                    }
                    
                    if (!downloadSuccess) {
                        Log.e("AnalysisScreen", "Failed to download models")
                        GlobalOperationService.finishOperation()
                        // 失敗メッセージを表示するために少し待つ
                        delay(500)
                        onComplete()
                        return@launch
                    }
                    
                    // ダウンロード後にサービスを確実に初期化
                    if (analysisType == "AI_TAGGING") {
                        galleryState.aiTaggingService.ensureInitialized()
                    } else {
                        galleryState.vectorSearchService.ensureInitialized()
                    }
                }

                val allMedia = galleryState.repository.getAllMedia()
                val imageList = allMedia.filter { !it.isVideo }
                if (imageList.isEmpty()) { 
                    GlobalOperationService.finishOperation()
                    onComplete()
                    return@launch 
                }

                val allMetadataNow = galleryState.repository.getAllMetadataSummary()
                val metaMap = allMetadataNow.associateBy { it.uri }

                val targetList = imageList.filter { item ->
                    val rating = metaMap[item.uri]?.ageRating ?: "SFW"
                    
                    // 現在の年齢制限フィルタに合わせて対象を絞り込む
                    val matchesFilter = when (galleryState.ageRatingFilter) {
                        AgeRatingFilter.ALL -> true
                        AgeRatingFilter.SFW -> rating == "SFW"
                        AgeRatingFilter.R15 -> rating == "R15"
                        AgeRatingFilter.R18 -> rating == "R18"
                    }
                    if (!matchesFilter) return@filter false

                    val meta = metaMap[item.uri]
                    when (analysisType) {
                        "AI_TAGGING" -> (meta == null || !meta.isAiAnalyzed)
                        "COLOR_VECTOR" -> (meta == null || !meta.hasFeatureVector)
                        "AUTO_RATING" -> true // 全て(またはタグがあるもの)を対象
                        else -> true
                    }
                }
                totalCount = targetList.size

                if (targetList.isEmpty()) { 
                    GlobalOperationService.finishOperation()
                    onComplete()
                    return@launch 
                }

                targetList.forEachIndexed { index, media ->
                    if (analysisJob?.isCancelled == true) return@launch
                    val fileName = media.uri.substringAfterLast("/")
                    val currentProgress = (index + 1).toFloat() / targetList.size.toFloat()
                    GlobalOperationService.updateProgress(currentProgress, "$fileName (${index + 1} / ${targetList.size})")

                    when (analysisType) {
                        "AI_TAGGING" -> {
                            if (galleryState.isMockMode) {
                                delay(100)
                                val rating = if (galleryState.ageRatingFilter == AgeRatingFilter.ALL) "SFW" else galleryState.ageRatingFilter.name
                                galleryState.repository.updateAiAnalysisResult(media.uri, rating, true)
                            } else {
                                galleryState.aiTaggingService.analyzeSingle(media)
                            }
                        }
                        "COLOR_VECTOR" -> {
                            if (galleryState.isMockMode) {
                                delay(50)
                                galleryState.repository.updateFeatureVector(media.uri, floatArrayOf(0.1f, 0.2f, 0.3f))
                            } else {
                                galleryState.vectorSearchService.analyzeSingle(media)
                            }
                        }
                        "AUTO_RATING" -> {
                            // 既存のタグを取得して自動判定
                            val tags = galleryState.repository.getTagsForMedia(media.uri).first().map { it.tag }
                            var newAgeRating = "SFW"
                            
                            // センシティブワードによる自動判定
                            tags.forEach { tag ->
                                val cleanTag = tag.replace("_", " ")
                                if (AppConstants.R18Keywords.any { cleanTag.contains(it, ignoreCase = true) }) {
                                    newAgeRating = "R18"
                                } else if (newAgeRating != "R18" && AppConstants.R15Keywords.any { cleanTag.contains(it, ignoreCase = true) }) {
                                    newAgeRating = "R15"
                                }
                            }
                            
                            val meta = metaMap[media.uri]
                            if (meta == null || meta.ageRating != newAgeRating) {
                                galleryState.repository.bulkUpdateAgeRating(listOf(media.uri), newAgeRating)
                            }
                            delay(10)
                        }
                    }
                    processedCount = index + 1
                    // 負荷軽減のための冷却期間
                    if (index % 10 == 0) delay(100)
                }
                galleryState.refresh() // 完了後にUIを更新
                GlobalOperationService.finishOperation()
                isFinished = true
            } catch (e: Exception) { 
                Log.e("AnalysisScreen", "Error", e)
                galleryState.refresh()
                GlobalOperationService.finishOperation()
                onComplete() 
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(AppConstants.BackgroundColor)) {
        if (!isFinished) {
            IconButton(onClick = { 
                analysisJob?.cancel()
                GlobalOperationService.finishOperation()
                onCancel() // 呼び出し元で popBackStack() される
            }, modifier = Modifier.align(Alignment.TopEnd).padding(top = 100.dp, end = 16.dp).windowInsetsPadding(WindowInsets.statusBars)) {
                Icon(Icons.Default.Close, "キャンセル", tint = Color.White)
            }

            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(16.dp))
                Text("解析中...", color = Color.White)
                Text("${processedCount} / ${totalCount}", color = Color.Gray, fontSize = 12.sp)
            }
        } else {
            Card(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color.Green, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("解析完了", color = Color.White, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("${processedCount} 件のアイテムを処理しました", color = Color.LightGray)
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onComplete,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

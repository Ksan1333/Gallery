package com.example.gallery.ui.screen

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
                    val downloadSuccess = com.example.gallery.util.ModelDownloader.downloadAllModels(context) {}
                    if (!downloadSuccess) {
                        Log.e("AnalysisScreen", "Failed to download models")
                        GlobalOperationService.finishOperation()
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
                Log.d("AnalysisScreen", "Total images to consider: ${imageList.size}")

                if (imageList.isEmpty()) { 
                    Log.d("AnalysisScreen", "No images found for analysis")
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
                Log.d("AnalysisScreen", "Target items for $analysisType: ${targetList.size}")

                if (targetList.isEmpty()) { 
                    Log.d("AnalysisScreen", "No targets found for $analysisType")
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
                    // 負荷軽減のための冷却期間
                    if (index % 10 == 0) delay(100)
                }
                galleryState.refresh() // 完了後にUIを更新
                GlobalOperationService.finishOperation()
                onComplete()
            } catch (e: Exception) { 
                Log.e("AnalysisScreen", "Error", e)
                galleryState.refresh()
                GlobalOperationService.finishOperation()
                onComplete() 
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(AppConstants.BackgroundColor)) {
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
            Text("解析準備中...", color = Color.White)
        }
    }
}

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

@Composable
fun AnalysisProgressScreen(
    galleryState: GalleryState,
    analysisType: String = "AI_TAGGING", // "AI_TAGGING" or "COLOR_VECTOR"
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var analysisJob by remember { mutableStateOf<Job?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        analysisJob = scope.launch {
            try {
                val operationTitle = if (analysisType == "AI_TAGGING") "AIタグ解析中..." else "カラーベクトル解析中..."
                GlobalOperationService.startOperation(operationTitle, tag = analysisType)

                if (analysisType == "AI_TAGGING") {
                    com.example.gallery.util.ModelDownloader.downloadAllModels(context) {}
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
                    if (analysisType == "AI_TAGGING") {
                        (meta == null || !meta.isAiAnalyzed)
                    } else {
                        (meta == null || !meta.hasFeatureVector)
                    }
                }

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

                    if (analysisType == "AI_TAGGING") {
                        if (galleryState.isMockMode) {
                            delay(100)
                            val rating = if (galleryState.ageRatingFilter == AgeRatingFilter.ALL) "SFW" else galleryState.ageRatingFilter.name
                            galleryState.repository.updateAiAnalysisResult(media.uri, rating, true)
                        } else {
                            galleryState.aiTaggingService.analyzeSingle(media)
                        }
                    } else {
                        if (galleryState.isMockMode) {
                            delay(50)
                            galleryState.repository.updateFeatureVector(media.uri, floatArrayOf(0.1f, 0.2f, 0.3f))
                        } else {
                            galleryState.vectorSearchService.analyzeSingle(media)
                        }
                    }
                }
                GlobalOperationService.finishOperation()
                onComplete()
            } catch (e: Exception) { 
                Log.e("AnalysisScreen", "Error", e)
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

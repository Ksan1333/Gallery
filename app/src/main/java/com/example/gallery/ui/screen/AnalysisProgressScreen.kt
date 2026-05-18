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
import com.example.gallery.ui.component.FloatingRandomImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AnalysisProgressScreen(
    galleryState: GalleryState,
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var progressText by remember { mutableStateOf("0 / 0") }
    var currentFileName by remember { mutableStateOf("") }
    var analyzedMediaList by remember { mutableStateOf<List<MediaData>>(emptyList()) }
    val scope = rememberCoroutineScope()
    var taggingJob by remember { mutableStateOf<Job?>(null) }

    val allMetadata by galleryState.repository.getAllMetadataFlow().collectAsState(initial = emptyList())
    val metadataMap = remember(allMetadata) { allMetadata.associateBy { it.uri } }
    
    // 過去に分析済みの画像（現在のフィルタに一致するもの）
    var pastAnalyzedMedia by remember { mutableStateOf<List<MediaData>>(emptyList()) }
    LaunchedEffect(allMetadata, galleryState.ageRatingFilter) {
        val filteredUris = allMetadata.filter { meta ->
            val rating = meta.ageRating ?: "SFW"
            when (galleryState.ageRatingFilter) {
                AgeRatingFilter.ALL -> true
                AgeRatingFilter.SFW -> rating == "SFW"
                AgeRatingFilter.R15 -> rating == "R15"
                AgeRatingFilter.R18 -> rating == "R18"
            }
        }.map { it.uri }.toSet()
        
        // 過去に分析済みのものを抽出
        pastAnalyzedMedia = galleryState.repository.getAllMedia().filter { 
            it.uri in filteredUris && (metadataMap[it.uri]?.isAiAnalyzed == true || metadataMap[it.uri]?.colorComposition != null)
        }
    }

    LaunchedEffect(Unit) {
        taggingJob = scope.launch {
            try {
                val allMedia = galleryState.repository.getAllMedia()
                val imageList = allMedia.filter { !it.isVideo }
                
                if (imageList.isEmpty()) {
                    onComplete()
                    return@launch
                }

                val allMetadataNow = galleryState.repository.getAllMetadata()
                val metaMap = allMetadataNow.associateBy { it.uri }
                
                // 現在の年齢制限フィルタに一致するもののみを対象にする
                val filteredByAge = imageList.filter { item ->
                    val rating = metaMap[item.uri]?.ageRating ?: "SFW"
                    when (galleryState.ageRatingFilter) {
                        AgeRatingFilter.ALL -> true
                        AgeRatingFilter.SFW -> rating == "SFW"
                        AgeRatingFilter.R15 -> rating == "R15"
                        AgeRatingFilter.R18 -> rating == "R18"
                    }
                }

                val targetList = filteredByAge.filter { 
                    val meta = metaMap[it.uri]
                    meta?.isAiAnalyzed != true || meta?.colorComposition == null
                }
                
                if (targetList.isEmpty()) {
                    android.util.Log.d("AnalysisScreen", "No new media to analyze in current filter.")
                    onComplete()
                    return@launch
                }

                targetList.forEachIndexed { index, media ->
                    if (taggingJob?.isCancelled == true) return@launch
                    
                    currentFileName = media.uri.substringAfterLast("/")
                    
                    // AI解析が未完了なら実行
                    val meta = galleryState.repository.getMetadata(media.uri)
                    if (meta?.isAiAnalyzed != true) {
                        galleryState.aiTaggingService.analyzeSingle(media)
                    }
                    
                    // カラー解析が未完了なら実行
                    if (meta?.colorComposition == null) {
                        galleryState.colorTaggingService.processSingleMedia(media)
                    }

                    // 10件ごとにUI更新（負荷軽減）
                    if (index % 10 == 0 || index == targetList.size - 1) {
                        progress = (index + 1).toFloat() / targetList.size.toFloat()
                        progressText = "${index + 1} / ${targetList.size}"
                    }
                    
                    if (index % 20 == 0 || index == targetList.size - 1) {
                        analyzedMediaList = targetList.take(index + 1)
                    }
                }
                onComplete()
            } catch (e: Exception) {
                Log.e("AnalysisScreen", "Error during analysis", e)
                onComplete()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppConstants.BackgroundColor)
    ) {
        if (analyzedMediaList.isNotEmpty() || pastAnalyzedMedia.isNotEmpty()) {
            // 今分析したものと、過去に分析済みのものを混ぜて表示
            val displayMediaList = remember(analyzedMediaList, pastAnalyzedMedia) {
                (analyzedMediaList + pastAnalyzedMedia).distinctBy { it.uri }
            }
            
            FloatingRandomImage(
                mediaList = displayMediaList,
                ageRatingFilter = galleryState.ageRatingFilter,
                metadataMap = metadataMap
            )
        }

        IconButton(
            onClick = {
                taggingJob?.cancel()
                onCancel()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Icon(Icons.Default.Close, contentDescription = "キャンセル", tint = Color.White)
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White,
                    strokeWidth = 6.dp,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 20.sp
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("ライブラリを最適化中...", color = Color.White, fontSize = 18.sp)
            Text(currentFileName, color = Color.LightGray, fontSize = 12.sp, maxLines = 1)
            Text(progressText, color = Color.Gray, fontSize = 14.sp)
            
            Spacer(modifier = Modifier.height(32.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = Color.Cyan,
                trackColor = Color.Gray.copy(alpha = 0.3f)
            )
        }
    }
}

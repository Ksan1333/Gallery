package com.example.gallery.service

import android.content.Context
import android.util.Log
import com.example.gallery.data.repository.AiTaggingService
import com.example.gallery.data.repository.ColorTaggingService
import com.example.gallery.data.repository.VectorSearchService
import com.example.gallery.data.repository.MediaRepository
import com.example.gallery.util.ModelDownloader
import com.example.gallery.util.ThumbnailUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThumbnailGenerationService {
    private const val TAG = "ThumbnailGenService"
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    private val _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _statusText = MutableStateFlow("")
    val statusText = _statusText.asStateFlow()

    private val _currentCount = MutableStateFlow(0)
    val currentCount = _currentCount.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount = _totalCount.asStateFlow()

    fun startGenerating(context: Context, repository: MediaRepository) {
        if (_isProcessing.value) return

        job = serviceScope.launch {
            _isProcessing.value = true
            try {
                // 1. モデルの自動ダウンロード
                _statusText.value = "AIモデルを準備中..."
                val modelLoaded = ModelDownloader.downloadIfNeeded(context) { downProgress ->
                    _progress.value = downProgress * 0.1f // 最初の10%をダウンロードに割り当て
                }
                
                if (!modelLoaded) {
                    _statusText.value = "AIモデルの準備に失敗しました。一部機能が制限されます。"
                    delay(2000)
                }

                val aiService = AiTaggingService(context, repository)
                val colorService = ColorTaggingService(context, repository)
                val vectorService = VectorSearchService(context, repository)
                
                val allMedia = repository.getAllMedia(forceRefresh = true)
                val total = allMedia.size
                _totalCount.value = total
                Log.d(TAG, "Starting media analysis for $total items")

                allMedia.forEachIndexed { index, media ->
                    if (!isActive) return@launch
                    _currentCount.value = index + 1
                    
                    val currentProgress = 0.1f + ((index + 1).toFloat() / total * 0.9f)
                    val stage = if (!media.isVideo) " (サムネイル/AI/ベクトル分析中)" else " (サムネイル生成中)"
                    _statusText.value = "解析中: ${index + 1} / $total$stage"
                    
                    // 1. サムネイル生成
                    ThumbnailUtils.generateThumbnailIfMissing(context, media.uri)
                    
                    // 2. AI分析 & カラー分析 & ベクトル分析（画像のみ）
                    if (!media.isVideo) {
                        val metadata = repository.getMetadata(media.uri)
                        if (metadata?.isAiAnalyzed != true) {
                            aiService.analyzeSingle(media)
                        }
                        if (metadata?.colorComposition == null) {
                            colorService.processSingleMedia(media)
                        }
                        if (metadata?.featureVector == null) {
                            vectorService.analyzeSingle(media)
                        }
                    }
                    
                    _progress.value = currentProgress
                }
                Log.d(TAG, "Media analysis completed")
                _statusText.value = "すべての解析が完了しました"
                vectorService.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error in media analysis", e)
                _statusText.value = "解析中にエラーが発生しました"
            } finally {
                _isProcessing.value = false
                _progress.value = 1.0f
            }
        }
    }

    fun stop() {
        job?.cancel()
        _isProcessing.value = false
    }
}

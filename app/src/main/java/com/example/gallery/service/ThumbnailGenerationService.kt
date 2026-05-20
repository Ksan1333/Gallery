package com.example.gallery.service

import android.content.Context
import android.util.Log
import com.example.gallery.data.repository.AiTaggingService
import com.example.gallery.data.repository.ColorTaggingService
import com.example.gallery.data.repository.MediaRepository
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
                val aiService = AiTaggingService(context, repository)
                val colorService = ColorTaggingService(context, repository)
                
                val allMedia = repository.getAllMedia(forceRefresh = true)
                val total = allMedia.size
                _totalCount.value = total
                Log.d(TAG, "Starting media analysis for $total items")

                allMedia.forEachIndexed { index, media ->
                    if (!isActive) return@launch
                    _currentCount.value = index + 1
                    
                    val stage = if (!media.isVideo) " (サムネイル/AI/カラー分析中)" else " (サムネイル生成中)"
                    _statusText.value = "解析中: ${index + 1} / $total$stage"
                    
                    // 1. サムネイル生成
                    ThumbnailUtils.generateThumbnailIfMissing(context, media.uri)
                    
                    // 2. AI分析 & カラー分析（画像のみ）
                    if (!media.isVideo) {
                        // 既に分析済みかチェック
                        val metadata = repository.getMetadata(media.uri)
                        if (metadata?.isAiAnalyzed != true) {
                            aiService.analyzeSingle(media)
                        }
                        if (metadata?.colorComposition == null) {
                            colorService.processSingleMedia(media)
                        }
                    }
                    
                    _progress.value = (index + 1).toFloat() / total
                }
                Log.d(TAG, "Media analysis completed")
                _statusText.value = "すべての解析が完了しました"
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

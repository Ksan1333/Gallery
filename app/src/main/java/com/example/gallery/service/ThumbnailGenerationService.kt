package com.example.gallery.service

import android.content.Context
import android.util.Log
import com.example.gallery.data.repository.AiTaggingService
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

    // 下記の StateFlow は下位互換性/内部管理用に残すが、外部表示は GlobalOperationService を使う
    private val _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _isStartupTasksCompleted = MutableStateFlow(false)
    val isStartupTasksCompleted = _isStartupTasksCompleted.asStateFlow()

    private var hasInitialCheckDone = false

    fun startGenerating(context: Context, repository: MediaRepository) {
        if (_isProcessing.value || hasInitialCheckDone) return

        job = serviceScope.launch(Dispatchers.Default) {
            _isProcessing.value = true
            _isStartupTasksCompleted.value = false
            try {
                // 初回だけ全スキャン
                val allMedia = repository.getAllMedia(forceRefresh = false)
                val allMetadata = repository.getAllMetadataSummary().associateBy { it.uri }
                
                // サムネイルがない、またはベクトル分析がないものを対象にする
                val targetList = allMedia.filter { 
                    !ThumbnailUtils.getThumbnailFile(context, it.uri).exists() ||
                    allMetadata[it.uri]?.hasFeatureVector != true
                }
                
                val total = targetList.size
                if (total == 0) {
                    Log.d(TAG, "All thumbnails and vectors already exist")
                    hasInitialCheckDone = true
                    _isStartupTasksCompleted.value = true
                    return@launch
                }

                Log.d(TAG, "Starting background tasks for $total items")
                GlobalOperationService.startOperation("バックグラウンド準備中...", tag = "STARTUP_TASKS")

                targetList.forEachIndexed { index, media ->
                    if (!isActive) return@launch
                    
                    val currentProgress = (index + 1).toFloat() / total
                    if (index % 50 == 0 || index == total - 1) {
                        GlobalOperationService.updateProgress(currentProgress, "準備中: ${index + 1} / $total")
                    }
                    
                    // サムネイル作成
                    ThumbnailUtils.generateThumbnailIfMissing(context, media.uri)

                    // ベクトル分析がまだの場合はバックグラウンドで行う
                    val meta = repository.getMetadata(media.uri)
                    if (meta == null || meta.featureVector == null) {
                        repository.galleryState?.vectorSearchService?.analyzeSingle(media)
                    }

                    _progress.value = currentProgress
                    
                    // 負荷軽減のための冷却
                    if (index % 5 == 0) delay(10)
                }
                Log.d(TAG, "Thumbnail generation check completed")
                hasInitialCheckDone = true
                _isStartupTasksCompleted.value = true
                GlobalOperationService.updateProgress(1.0f, "サムネイルの準備が完了しました")
            } catch (e: Exception) {
                Log.e(TAG, "Error in thumbnail generation", e)
            } finally {
                _isProcessing.value = false
                _progress.value = 1.0f
                GlobalOperationService.finishOperation()
            }
        }
    }

    fun stop() {
        job?.cancel()
        _isProcessing.value = false
    }
}

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

    private var hasInitialCheckDone = false

    fun startGenerating(context: Context, repository: MediaRepository) {
        if (_isProcessing.value || hasInitialCheckDone) return

        job = serviceScope.launch(Dispatchers.Default) {
            _isProcessing.value = true
            GlobalOperationService.startOperation("サムネイルを確認中...", tag = "THUMBNAIL_GEN")
            try {
                // 初回だけ全スキャン
                val allMedia = repository.getAllMedia(forceRefresh = false)
                
                // すでにサムネイルが存在するものを除外して対象を絞り込む
                val targetList = allMedia.filter { 
                    !ThumbnailUtils.getThumbnailFile(context, it.uri).exists()
                }
                
                val total = targetList.size
                if (total == 0) {
                    Log.d(TAG, "All thumbnails already exist")
                    hasInitialCheckDone = true
                    return@launch
                }

                Log.d(TAG, "Starting thumbnail generation for $total missing items")

                targetList.forEachIndexed { index, media ->
                    if (!isActive) return@launch
                    
                    val currentProgress = (index + 1).toFloat() / total
                    if (index % 50 == 0 || index == total - 1) {
                        GlobalOperationService.updateProgress(currentProgress, "サムネイル作成中: ${index + 1} / $total")
                    }
                    
                    ThumbnailUtils.generateThumbnailIfMissing(context, media.uri)
                    _progress.value = currentProgress
                }
                Log.d(TAG, "Thumbnail generation check completed")
                hasInitialCheckDone = true
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

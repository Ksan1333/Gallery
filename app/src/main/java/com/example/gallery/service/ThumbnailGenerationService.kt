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
        if (_isProcessing.value) return
        if (hasInitialCheckDone) {
            _isStartupTasksCompleted.value = true
            return
        }

        job = serviceScope.launch(Dispatchers.IO) { // UIをブロックしないように IO スレッドを明示
            _isProcessing.value = true
            _isStartupTasksCompleted.value = false
            try {
                Log.d(TAG, "Starting cleanup and media scan...")
                // まず軽いクリーンアップ
                repository.mediaDao.cleanupAgeRatingTags()
                
                // ファイルの実在チェック
                repository.cleanupDeletedFiles()
                
                // 全スキャン
                val allMedia = repository.getAllMedia(forceRefresh = true)
                val allMetadata = repository.getAllMetadataSummary().associateBy { it.uri }
                
                // サムネイルがない、またはベクトル分析がないものを対象にする
                val targetList = allMedia.filter { 
                    !ThumbnailUtils.getThumbnailFile(context, it.uri).exists() ||
                    (!it.isVideo && allMetadata[it.uri]?.hasFeatureVector != true)
                }
                
                val total = targetList.size
                if (total == 0) {
                    Log.d(TAG, "All thumbnails and vectors already exist")
                    hasInitialCheckDone = true
                    _isStartupTasksCompleted.value = true
                    return@launch
                }

                Log.d(TAG, "Starting background tasks for $total items")
                // ここで確実に Operation を開始
                withContext(Dispatchers.Main) {
                    GlobalOperationService.startOperation("バックグラウンド準備中...", tag = "STARTUP_TASKS")
                }

                // ベクトル検索用の軽量モデル (1MB) がない場合はダウンロードを試みる
                if (!ModelDownloader.getVectorModelFile(context).exists()) {
                    Log.d(TAG, "Vector model missing, attempting background download...")
                    ModelDownloader.downloadAllModels(context) { progress ->
                        GlobalOperationService.updateProgress(progress * 0.1f, "AIモデル準備中...")
                    }
                }

                // 実行前にベクトル検索サービスの初期化を試みる
                repository.galleryState?.vectorSearchService?.ensureInitialized()

                targetList.forEachIndexed { index, media ->
                    if (!isActive) return@launch
                    
                    val currentProgress = (index + 1).toFloat() / total
                    if (index % 20 == 0 || index == total - 1) {
                        GlobalOperationService.updateProgress(currentProgress, "準備中: ${index + 1} / $total")
                    }
                    
                    // サムネイル作成
                    ThumbnailUtils.generateThumbnailIfMissing(context, media.uri)

                    // ベクトル分析 (画像のみ)
                    if (!media.isVideo) {
                        // DBの状態を再確認して、本当に未分析な場合のみ実行
                        val currentMeta = repository.mediaDao.getMetadata(media.uri)
                        if (currentMeta?.featureVector == null) {
                            Log.d(TAG, "Analyzing vector for ${media.uri} during startup")
                            repository.galleryState?.vectorSearchService?.analyzeSingle(media)
                        }
                    }

                    _progress.value = currentProgress
                    
                    // CPU負荷を抑える
                    if (index % 10 == 0) delay(10)
                }
                Log.d(TAG, "Startup tasks completed successfully")
                hasInitialCheckDone = true
                _isStartupTasksCompleted.value = true
                GlobalOperationService.updateProgress(1.0f, "準備が完了しました")
                delay(1000)
            } catch (e: Exception) {
                Log.e(TAG, "Error in startup tasks", e)
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

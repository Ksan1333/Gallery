package com.example.gallery.service

import android.content.Context
import android.util.Log
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

    private val _isStartupTasksCompleted = MutableStateFlow(false)
    val isStartupTasksCompleted = _isStartupTasksCompleted.asStateFlow()

    private var hasInitialCheckDone = false
    private val sessionAttemptedUris = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun startGenerating(context: Context, repository: MediaRepository, force: Boolean = false) {
        if (_isProcessing.value && !force) return
        if (hasInitialCheckDone && !force) {
            _isStartupTasksCompleted.value = true
            return
        }

        job?.cancel()
        
        job = serviceScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            _isStartupTasksCompleted.value = false
            try {
                Log.d(TAG, "Starting startup tasks... (force=$force)")
                val allMedia = repository.getAllMedia(forceRefresh = false)
                val allMetadata = repository.getAllMetadataSummary().associateBy { it.uri }

                if (!isActive) return@launch

                // 1. サムネイル作成が必要なアイテム
                val thumbTargets = allMedia.filter { media ->
                    !sessionAttemptedUris.contains(media.uri) && 
                    !ThumbnailUtils.getThumbnailFile(context, media.uri).exists()
                }

                // 2. ベクトル解析が必要なアイテム (画像のみ)
                val vectorTargets = allMedia.filter { media ->
                    (!media.isVideo && 
                    !sessionAttemptedUris.contains(media.uri + "_vector") &&
                    allMetadata[media.uri]?.hasFeatureVector != true)
                }

                val totalThumb = thumbTargets.size
                val totalVector = vectorTargets.size
                
                if (totalThumb == 0 && totalVector == 0) {
                    Log.d(TAG, "No pending startup tasks")
                    hasInitialCheckDone = true
                    _isStartupTasksCompleted.value = true
                    return@launch
                }

                val opId = "STARTUP_TASKS"
                withContext(Dispatchers.Main) {
                    GlobalOperationService.startOperation(
                        "起動時タスク実行中...",
                        tag = opId,
                        canCancel = false,
                    )
                }

                // フェーズ1: サムネイル作成
                if (totalThumb > 0) {
                    thumbTargets.forEachIndexed { index, media ->
                        if (!isActive) return@launch
                        val currentProgress = (index + 1).toFloat() / totalThumb.toFloat()
                        if (index % 5 == 0 || index == totalThumb - 1) {
                            GlobalOperationService.updateProgress(currentProgress * 0.5f, "サムネイル作成中: ${index + 1} / $totalThumb", opId)
                        }
                        ThumbnailUtils.generateThumbnailIfMissing(context, media.uri)
                        sessionAttemptedUris.add(media.uri)
                        if (index % 10 == 0) delay(10)
                    }
                }

                // フェーズ2: ベクトル解析 (モデルが存在する場合のみ)
                if (totalVector > 0 && ModelDownloader.isVectorModelValid(context)) {
                    repository.galleryState?.vectorSearchService?.ensureInitialized()
                    vectorTargets.forEachIndexed { index, media ->
                        if (!isActive) return@launch
                        val currentProgress = (index + 1).toFloat() / totalVector.toFloat()
                        if (index % 5 == 0 || index == totalVector - 1) {
                            GlobalOperationService.updateProgress(0.5f + (currentProgress * 0.5f), "解析中: ${index + 1} / $totalVector", opId)
                        }
                        repository.galleryState?.vectorSearchService?.analyzeSingle(media)
                        sessionAttemptedUris.add(media.uri + "_vector")
                        if (index % 5 == 0) delay(15)
                    }
                }

                Log.d(TAG, "Startup tasks completed successfully")
                hasInitialCheckDone = true
                _isStartupTasksCompleted.value = true
                GlobalOperationService.updateProgress(1.0f, "準備完了", opId)
                delay(800)
            } catch (e: Exception) {
                Log.e(TAG, "Error in startup tasks", e)
            } finally {
                _isProcessing.value = false
                GlobalOperationService.finishOperation("STARTUP_TASKS")
            }
        }
    }

    fun stop() {
        job?.cancel()
        _isProcessing.value = false
    }
}

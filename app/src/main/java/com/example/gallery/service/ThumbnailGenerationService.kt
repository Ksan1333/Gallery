package com.example.gallery.service

import android.content.Context
import android.util.Log
import com.example.gallery.data.repository.MediaRepository
import com.example.gallery.util.ModelDownloader
import com.example.gallery.util.ThumbnailUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ThumbnailGenerationService {
    private const val TAG = "ThumbnailGenService"
    private const val OP_ID = "THUMBNAIL_GENERATION"
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    private val _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _isStartupTasksCompleted = MutableStateFlow(false)
    val isStartupTasksCompleted = _isStartupTasksCompleted.asStateFlow()

    private val sessionAttemptedUris = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun startGenerating(context: Context, repository: MediaRepository, force: Boolean = false) {
        if (_isProcessing.value && !force) return

        job?.cancel()
        job = serviceScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            _isStartupTasksCompleted.value = false
            _progress.value = 0f

            try {
                Log.d(TAG, "Checking startup tasks... (force=$force)")
                val allMedia = repository.getAllMedia(forceRefresh = false)
                val allMetadata = repository.getAllMetadataSummary().associateBy { it.uri }
                if (!isActive) return@launch

                val thumbTargets = allMedia.filter { media ->
                    val metadata = allMetadata[media.uri]
                    val thumbnailFileExists = ThumbnailUtils.getThumbnailFile(context, media.uri).exists()
                    !sessionAttemptedUris.contains(media.uri) &&
                        metadata?.hasThumbnail != true &&
                        !thumbnailFileExists
                }

                val vectorTargets = if (ModelDownloader.isVectorModelValid(context)) {
                    allMedia.filter { media ->
                        !media.isVideo &&
                            !sessionAttemptedUris.contains("${media.uri}_vector") &&
                            allMetadata[media.uri]?.hasFeatureVector != true
                    }
                } else {
                    emptyList()
                }

                val totalTasks = thumbTargets.size + vectorTargets.size
                if (totalTasks == 0) {
                    Log.d(TAG, "No pending startup tasks")
                    _isStartupTasksCompleted.value = true
                    _progress.value = 1f
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    GlobalOperationService.startOperation(
                        "起動タスク実行中...",
                        tag = OP_ID,
                        canCancel = false,
                    )
                    GlobalOperationService.updateProgress(0f, "準備中...", OP_ID)
                }

                var completedTasks = 0
                thumbTargets.forEachIndexed { index, media ->
                    if (!isActive) return@launch
                    ThumbnailUtils.generateThumbnailIfMissing(context, media.uri)
                    repository.mediaDao.updateHasThumbnail(media.uri, true)
                    sessionAttemptedUris.add(media.uri)
                    completedTasks++
                    updateStartupProgress(
                        completedTasks = completedTasks,
                        totalTasks = totalTasks,
                        text = "サムネイル作成中: ${index + 1} / ${thumbTargets.size}",
                    )
                    if (index % 10 == 0) delay(10)
                }

                if (vectorTargets.isNotEmpty()) {
                    repository.galleryState?.vectorSearchService?.ensureInitialized()
                    vectorTargets.forEachIndexed { index, media ->
                        if (!isActive) return@launch
                        repository.galleryState?.vectorSearchService?.analyzeSingle(media)
                        sessionAttemptedUris.add("${media.uri}_vector")
                        completedTasks++
                        updateStartupProgress(
                            completedTasks = completedTasks,
                            totalTasks = totalTasks,
                            text = "解析中: ${index + 1} / ${vectorTargets.size}",
                        )
                        if (index % 5 == 0) delay(15)
                    }
                }

                Log.d(TAG, "Startup tasks completed successfully")
                _isStartupTasksCompleted.value = true
                _progress.value = 1f
                GlobalOperationService.updateProgress(1.0f, "準備完了", OP_ID)
                delay(800)
            } catch (e: Exception) {
                Log.e(TAG, "Error in startup tasks", e)
            } finally {
                _isProcessing.value = false
                GlobalOperationService.finishOperation(OP_ID)
            }
        }
    }

    private fun updateStartupProgress(completedTasks: Int, totalTasks: Int, text: String) {
        val progress = (completedTasks.toFloat() / totalTasks.toFloat()).coerceIn(0f, 1f)
        _progress.value = progress
        GlobalOperationService.updateProgress(progress, text, OP_ID)
    }

    fun stop() {
        job?.cancel()
        _isProcessing.value = false
    }
}

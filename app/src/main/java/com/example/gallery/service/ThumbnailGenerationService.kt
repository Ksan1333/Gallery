package com.example.gallery.service

import android.content.Context
import android.util.Log
import com.example.gallery.R
import com.example.gallery.data.model.MediaData
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ThumbnailGenerationService {
    private const val TAG = "ThumbnailGenService"
    private const val OP_ID = "THUMBNAIL_GENERATION"
    private const val STARTUP_WORK_TRACE = "GALLERY_STARTUP_WORK_TRACE"
    private const val TARGET_DISCOVERY_IDLE_CHECK_INTERVAL = 64
    private const val PROGRESS_PUBLISH_INTERVAL = 25
    private const val FOREGROUND_IDLE_GRACE_MS = 800L
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    private val _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _isStartupTasksCompleted = MutableStateFlow(false)
    val isStartupTasksCompleted = _isStartupTasksCompleted.asStateFlow()

    private val foregroundScrollActive = MutableStateFlow(false)

    private val sessionAttemptedUris = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun setForegroundScrollActive(active: Boolean) {
        foregroundScrollActive.value = active
    }

    private suspend fun waitForForegroundIdle() {
        if (!foregroundScrollActive.value) return
        val waitStartedAt = System.currentTimeMillis()
        Log.d(STARTUP_WORK_TRACE, "paused_for_scroll")
        do {
            foregroundScrollActive.filter { !it }.first()
            delay(FOREGROUND_IDLE_GRACE_MS)
        } while (foregroundScrollActive.value)
        Log.d(
            STARTUP_WORK_TRACE,
            "resumed_after_scroll waitMs=${System.currentTimeMillis() - waitStartedAt} " +
                "idleGraceMs=$FOREGROUND_IDLE_GRACE_MS"
        )
    }

    fun startGenerating(context: Context, repository: MediaRepository, force: Boolean = false) {
        if (_isProcessing.value && !force) return

        job?.cancel()
        job = serviceScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            _isStartupTasksCompleted.value = false
            _progress.value = 0f

            try {
                // Initial scrolls have priority over opportunistic thumbnail/vector generation.
                waitForForegroundIdle()
                val targetDiscoveryStartedAt = System.currentTimeMillis()
                val allMedia = repository.getAllMedia(forceRefresh = false)
                val allMetadata = repository.getAllMetadataSummary().associateBy { it.uri }
                if (!isActive) return@launch

                val thumbTargets = mutableListOf<MediaData>()
                for (index in allMedia.indices) {
                    val media = allMedia[index]
                    // File existence checks can be expensive on a large library. Yield at a
                    // bounded interval so a scroll that starts during discovery takes priority.
                    if (index > 0 && index % TARGET_DISCOVERY_IDLE_CHECK_INTERVAL == 0) {
                        waitForForegroundIdle()
                    }
                    val metadata = allMetadata[media.uri]
                    val thumbnailFileExists = ThumbnailUtils.getThumbnailFile(context, media.uri).exists()
                    val needsThumbnail = !sessionAttemptedUris.contains(media.uri) &&
                        metadata?.hasThumbnail != true &&
                        metadata?.startupThumbnailAttempted != true &&
                        !thumbnailFileExists
                    if (needsThumbnail) {
                        thumbTargets += media
                    }
                }

                val vectorTargets = mutableListOf<MediaData>()
                if (ModelDownloader.isVectorModelValid(context)) {
                    for (index in allMedia.indices) {
                        val media = allMedia[index]
                        if (index > 0 && index % TARGET_DISCOVERY_IDLE_CHECK_INTERVAL == 0) {
                            waitForForegroundIdle()
                        }
                        val needsVector = !media.isVideo &&
                            !sessionAttemptedUris.contains("${media.uri}_vector") &&
                            allMetadata[media.uri]?.startupVectorAttempted != true &&
                            allMetadata[media.uri]?.hasFeatureVector != true
                        if (needsVector) {
                            vectorTargets += media
                        }
                    }
                }

                val totalTasks = thumbTargets.size + vectorTargets.size
                Log.d(
                    STARTUP_WORK_TRACE,
                    "targets thumb=${thumbTargets.size} vector=${vectorTargets.size} total=$totalTasks " +
                        "discoveryMs=${System.currentTimeMillis() - targetDiscoveryStartedAt}"
                )
                if (totalTasks == 0) {
                    _isStartupTasksCompleted.value = true
                    _progress.value = 1f
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    GlobalOperationService.startOperation(
                        context.getString(R.string.msg_startup_tasks),
                        tag = OP_ID,
                        canCancel = false,
                    )
                    GlobalOperationService.updateProgress(0f, context.getString(R.string.msg_initializing), OP_ID)
                }

                var completedTasks = 0
                thumbTargets.forEachIndexed { index, media ->
                    if (!isActive) return@launch
                    waitForForegroundIdle()
                    repository.mediaDao.updateStartupThumbnailAttempted(media.uri, true)
                    ThumbnailUtils.generateThumbnailIfMissing(context, media.uri)
                    repository.mediaDao.updateHasThumbnail(media.uri, true)
                    sessionAttemptedUris.add(media.uri)
                    completedTasks++
                    if (shouldPublishStartupProgress(completedTasks, totalTasks)) {
                        updateStartupProgress(
                            completedTasks = completedTasks,
                            totalTasks = totalTasks,
                            text = context.getString(R.string.msg_generating_thumbnails, index + 1, thumbTargets.size),
                        )
                    }
                    if (index % 10 == 0) delay(10)
                }

                if (vectorTargets.isNotEmpty()) {
                    repository.galleryState?.vectorSearchService?.ensureInitialized()
                    vectorTargets.forEachIndexed { index, media ->
                        if (!isActive) return@launch
                        waitForForegroundIdle()
                        repository.mediaDao.updateStartupVectorAttempted(media.uri, true)
                        repository.galleryState?.vectorSearchService?.analyzeSingle(media)
                        sessionAttemptedUris.add("${media.uri}_vector")
                        completedTasks++
                        if (shouldPublishStartupProgress(completedTasks, totalTasks)) {
                            updateStartupProgress(
                                completedTasks = completedTasks,
                                totalTasks = totalTasks,
                                text = context.getString(R.string.msg_analyzing_vectors, index + 1, vectorTargets.size),
                            )
                        }
                        if (index % 5 == 0) delay(15)
                    }
                }
                _isStartupTasksCompleted.value = true
                _progress.value = 1f
                GlobalOperationService.updateProgress(1.0f, context.getString(R.string.msg_ready), OP_ID)
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

    private fun shouldPublishStartupProgress(completedTasks: Int, totalTasks: Int): Boolean {
        return completedTasks == 1 ||
            completedTasks == totalTasks ||
            completedTasks % PROGRESS_PUBLISH_INTERVAL == 0
    }

    fun stop() {
        job?.cancel()
        _isProcessing.value = false
    }
}

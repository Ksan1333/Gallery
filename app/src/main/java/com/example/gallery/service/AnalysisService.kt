package com.example.gallery.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.gallery.R
import com.example.gallery.GalleryApplication
import com.example.gallery.MainActivity
import com.example.gallery.ui.AppConstants
import com.example.gallery.data.local.entity.MediaMetadataSummary
import com.example.gallery.ui.AppDefaults
import com.example.gallery.util.ModelDownloader
import kotlinx.coroutines.*

class AnalysisService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var analysisJob: Job? = null
    private var isModelDownloadActive = false

    companion object {
        const val CHANNEL_ID = "AnalysisChannel"
        const val NOTIFICATION_ID = 1001
        private const val ACTION_CANCEL = "com.example.gallery.action.CANCEL_ANALYSIS"
        private const val FAST_NORMAL_COOLDOWN_MS = 0L
        private const val FAST_LIGHT_COOLDOWN_MS = 250L
        private const val FAST_MODERATE_COOLDOWN_MS = 1_000L
        private const val FAST_LEGACY_COOLDOWN_MS = 120L
        private const val BALANCED_NORMAL_COOLDOWN_MS = 100L
        private const val BALANCED_LIGHT_COOLDOWN_MS = 500L
        private const val BALANCED_MODERATE_COOLDOWN_MS = 1_500L
        private const val BALANCED_LEGACY_COOLDOWN_MS = 250L
        private const val ACCURACY_NORMAL_COOLDOWN_MS = 250L
        private const val ACCURACY_LIGHT_COOLDOWN_MS = 750L
        private const val ACCURACY_MODERATE_COOLDOWN_MS = 1_750L
        private const val ACCURACY_LEGACY_COOLDOWN_MS = 500L
        private const val THERMAL_RECHECK_MS = 10_000L
        
        fun start(context: Context, type: String, periodDays: Int = -1) {
            val intent = Intent(context, AnalysisService::class.java).apply {
                putExtra("type", type)
                putExtra("periodDays", periodDays)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context, type: String = AppConstants.OP_AI_TAGGING) {
            GlobalOperationService.requestCancel(type)
            val intent = Intent(context, AnalysisService::class.java).apply {
                action = ACTION_CANCEL
                putExtra("type", type)
            }
            context.startService(intent)
        }
    }

    private data class CooldownProfile(
        val legacyMs: Long,
        val normalMs: Long,
        val lightMs: Long,
        val moderateMs: Long
    )

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = intent?.getStringExtra("type") ?: AppConstants.OP_AI_TAGGING
        if (intent?.action == ACTION_CANCEL) {
            GlobalOperationService.requestCancel(type)
            if (!isModelDownloadActive) {
                analysisJob?.cancel()
                GlobalOperationService.finishOperation(type)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
            return START_NOT_STICKY
        }

        val periodDays = intent?.getIntExtra("periodDays", -1) ?: -1
        
        val notification = createNotification(getString(R.string.analysis_preparing), 0f)
        startForeground(NOTIFICATION_ID, notification)

        startAnalysis(type, periodDays)
        
        return START_NOT_STICKY
    }

    private fun startAnalysis(type: String, periodDays: Int) {
        val galleryState = (application as GalleryApplication).galleryState
        
        analysisJob?.cancel()
        analysisJob = serviceScope.launch {
            try {
                val operationTitle = when(type) {
                    AppConstants.OP_AI_TAGGING -> getString(R.string.analysis_tagging)
                    AppConstants.OP_COLOR_VECTOR -> getString(R.string.analysis_color_vector)
                    AppConstants.OP_AUTO_RATING -> getString(R.string.analysis_rating)
                    else -> getString(R.string.analysis_processing)
                }
                val opId = GlobalOperationService.startOperation(operationTitle, tag = type, periodDays = periodDays)

                // 必要なモデルが欠けているかチェックする。
                val needsTagger = type == AppConstants.OP_AI_TAGGING
                val needsVector = type == AppConstants.OP_COLOR_VECTOR || type == AppConstants.OP_AUTO_RATING
                val selectedTaggerModel = if (needsTagger) {
                    ModelDownloader.currentTaggerModelId(this@AnalysisService)
                } else {
                    AppDefaults.AI_TAGGER_MODEL_NORMAL
                }
                
                val modelMissing = (needsTagger && !ModelDownloader.isTaggerModelValid(this@AnalysisService, selectedTaggerModel)) ||
                                 (needsVector && !ModelDownloader.isVectorModelValid(this@AnalysisService))

                if (modelMissing) {
                    isModelDownloadActive = true
                    GlobalOperationService.updateProgress(0f, getString(R.string.analysis_downloading_model), opId)
                    val success = ModelDownloader.downloadAllModels(
                        context = this@AnalysisService,
                        onProgress = { progress ->
                            GlobalOperationService.updateProgress(progress, getString(R.string.analysis_downloading_model), opId)
                            updateNotification("${getString(R.string.analysis_downloading_model)} ${(progress * 100).toInt()}%", progress)
                        },
                        taggerModelId = selectedTaggerModel
                    )
                    isModelDownloadActive = false
                    if (!success) {
                        GlobalOperationService.finishOperation(opId)
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(this@AnalysisService, getString(R.string.analysis_model_error), android.widget.Toast.LENGTH_LONG).show()
                        }
                        stopSelf()
                        return@launch
                    }
                    if (GlobalOperationService.isCanceled(opId)) {
                        return@launch
                    }
                }

                // 実行前にサービスを初期化する。
                if (needsTagger) galleryState.aiTaggingService.ensureInitialized(selectedTaggerModel)
                if (needsVector) galleryState.vectorSearchService.ensureInitialized()

                val allMedia = galleryState.repository.getAllMedia(forceRefresh = true)
                val imageList = allMedia.filter { !it.isVideo }
                
                if (imageList.isEmpty()) {
                    GlobalOperationService.finishOperation(opId)
                    return@launch
                }

                val allMetadataNow = galleryState.repository.getAllMetadataSummary()
                val metaMap = allMetadataNow.associateBy { it.uri }

                val startTime = if (periodDays == -1) 0L else System.currentTimeMillis() - (periodDays.toLong() * 24 * 3600 * 1000)

                val targetList = imageList.filter { item ->
                    if (periodDays != -1 && item.dateAdded < startTime) return@filter false
                    val meta = metaMap[item.uri]
                    when (type) {
                        "AI_TAGGING" -> shouldAnalyzeWithTagger(meta, selectedTaggerModel)
                        "COLOR_VECTOR" -> (meta == null || !meta.hasFeatureVector)
                        else -> true
                    }
                }

                if (targetList.isEmpty()) {
                    GlobalOperationService.finishOperation(opId)
                    return@launch
                }

                targetList.forEachIndexed { index, media ->
                    if (!isActive || GlobalOperationService.isCanceled(opId)) {
                         return@launch
                    }

                    val progressBeforeItem = index.toFloat() / targetList.size.toFloat()
                    if (!awaitThermalBudget(type, opId, progressBeforeItem)) {
                        return@launch
                    }
                    
                    val currentProgress = (index + 1).toFloat() / targetList.size.toFloat()
                    val fileName = media.uri.substringAfterLast("/")
                    
                    val resultTags = when (type) {
                        "AI_TAGGING" -> galleryState.aiTaggingService.analyzeSingle(media, selectedTaggerModel)
                        "COLOR_VECTOR" -> {
                            galleryState.vectorSearchService.analyzeSingle(media)
                            emptyList<String>()
                        }
                        else -> emptyList<String>()
                    }

                    val tagSummary = if (resultTags.isNotEmpty()) {
                        ": " + resultTags.take(3).joinToString(", ") + (if (resultTags.size > 3) "..." else "")
                    } else ""

                    val progressText = "$fileName (${index + 1} / ${targetList.size})$tagSummary"
                    GlobalOperationService.updateProgress(currentProgress, progressText, opId)
                    if (index == 0 || index == targetList.lastIndex || (index + 1) % 5 == 0) {
                        updateNotification(progressText, currentProgress)
                    }
                }
                galleryState.refresh()
            } catch (e: CancellationException) {
                Log.i("AnalysisService", "Analysis canceled: $type")
            } catch (e: Exception) {
                Log.e("AnalysisService", "Analysis failed", e)
            } finally {
                isModelDownloadActive = false
                GlobalOperationService.finishOperation(type)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
    }

    private fun shouldAnalyzeWithTagger(
        metadata: MediaMetadataSummary?,
        targetModelId: String
    ): Boolean {
        if (metadata == null || !metadata.isAiAnalyzed) return true
        return AppDefaults.aiTaggerModelRank(metadata.aiAnalysisModel) < AppDefaults.aiTaggerModelRank(targetModelId)
    }

    private suspend fun awaitThermalBudget(type: String, operationId: String, progress: Float): Boolean {
        val cooldownProfile = currentCooldownProfile()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (cooldownProfile.legacyMs > 0L) {
                delay(cooldownProfile.legacyMs)
            }
            return currentCoroutineContext().isActive && !GlobalOperationService.isCanceled(operationId)
        }

        val powerManager = getSystemService(PowerManager::class.java)
        var thermalStatus = powerManager.currentThermalStatus
        if (thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
            Log.w("AnalysisService", "Pausing $type because thermal status is ${thermalStatusName(thermalStatus)}")
            while (
                currentCoroutineContext().isActive &&
                !GlobalOperationService.isCanceled(operationId) &&
                thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
            ) {
                val pauseText = getString(R.string.analysis_paused_heat)
                GlobalOperationService.updateProgress(progress, pauseText, operationId)
                updateNotification(pauseText, progress)
                delay(THERMAL_RECHECK_MS)
                thermalStatus = powerManager.currentThermalStatus
            }
            if (!currentCoroutineContext().isActive || GlobalOperationService.isCanceled(operationId)) {
                return false
            }
            Log.i("AnalysisService", "Resuming $type at thermal status ${thermalStatusName(thermalStatus)}")
        }

        val cooldownMs = when {
            thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE -> cooldownProfile.moderateMs
            thermalStatus >= PowerManager.THERMAL_STATUS_LIGHT -> cooldownProfile.lightMs
            else -> cooldownProfile.normalMs
        }
        if (cooldownMs > 0L) {
            delay(cooldownMs)
        }
        return currentCoroutineContext().isActive && !GlobalOperationService.isCanceled(operationId)
    }

    private fun currentCooldownProfile(): CooldownProfile {
        val prefs = getSharedPreferences("global_settings", Context.MODE_PRIVATE)
        return when (
            prefs.getString(
                AppDefaults.AI_ANALYSIS_SPEED_MODE_KEY,
                AppDefaults.AI_ANALYSIS_SPEED_BALANCED
            )
        ) {
            AppDefaults.AI_ANALYSIS_SPEED_FAST -> CooldownProfile(
                legacyMs = FAST_LEGACY_COOLDOWN_MS,
                normalMs = FAST_NORMAL_COOLDOWN_MS,
                lightMs = FAST_LIGHT_COOLDOWN_MS,
                moderateMs = FAST_MODERATE_COOLDOWN_MS
            )
            AppDefaults.AI_ANALYSIS_SPEED_ACCURACY -> CooldownProfile(
                legacyMs = ACCURACY_LEGACY_COOLDOWN_MS,
                normalMs = ACCURACY_NORMAL_COOLDOWN_MS,
                lightMs = ACCURACY_LIGHT_COOLDOWN_MS,
                moderateMs = ACCURACY_MODERATE_COOLDOWN_MS
            )
            else -> CooldownProfile(
                legacyMs = BALANCED_LEGACY_COOLDOWN_MS,
                normalMs = BALANCED_NORMAL_COOLDOWN_MS,
                lightMs = BALANCED_LIGHT_COOLDOWN_MS,
                moderateMs = BALANCED_MODERATE_COOLDOWN_MS
            )
        }
    }

    private fun thermalStatusName(status: Int): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "unsupported"
        return when (status) {
            PowerManager.THERMAL_STATUS_NONE -> "none"
            PowerManager.THERMAL_STATUS_LIGHT -> "light"
            PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
            else -> "unknown($status)"
        }
    }

    private fun updateNotification(text: String, progress: Float) {
        val notification = createNotification(text, progress)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(text: String, progress: Float): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.analysis_notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setProgress(100, (progress * 100).toInt(), false)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.analysis_notification_channel), NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

}

package com.example.gallery.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.gallery.GalleryApplication
import com.example.gallery.MainActivity
import com.example.gallery.util.ModelDownloader
import kotlinx.coroutines.*

class AnalysisService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var analysisJob: Job? = null

    companion object {
        const val CHANNEL_ID = "AnalysisChannel"
        const val NOTIFICATION_ID = 1001
        
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
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = intent?.getStringExtra("type") ?: "AI_TAGGING"
        val periodDays = intent?.getIntExtra("periodDays", -1) ?: -1
        
        val notification = createNotification("解析準備中...", 0f)
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
                    "AI_TAGGING" -> "AIタグ解析中..."
                    "COLOR_VECTOR" -> "カラーベクトル解析中..."
                    "AUTO_RATING" -> "年齢制限自動振分中..."
                    else -> "処理中..."
                }
                val opId = GlobalOperationService.startOperation(operationTitle, tag = type, periodDays = periodDays)

                // 必要なモデルが欠けているかチェック
                val needsTagger = type == "AI_TAGGING"
                val needsVector = type == "COLOR_VECTOR" || type == "AUTO_RATING"
                
                val modelMissing = (needsTagger && !ModelDownloader.isTaggerModelValid(this@AnalysisService)) ||
                                 (needsVector && !ModelDownloader.isVectorModelValid(this@AnalysisService))

                if (modelMissing) {
                    GlobalOperationService.updateProgress(0f, "AIモデルをダウンロード中...", opId)
                    val success = ModelDownloader.downloadAllModels(this@AnalysisService) { progress ->
                        GlobalOperationService.updateProgress(progress, "AIモデルをダウンロード中...", opId)
                        updateNotification("AIモデルをダウンロード中... ${(progress * 100).toInt()}%", progress)
                    }
                    if (!success) {
                        GlobalOperationService.finishOperation(opId)
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(this@AnalysisService, "AIモデルの準備に失敗しました。通信環境を確認してください。", android.widget.Toast.LENGTH_LONG).show()
                        }
                        stopSelf()
                        return@launch
                    }
                }

                // 実行前にサービスの初期化
                if (needsTagger) galleryState.aiTaggingService.ensureInitialized()
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
                        "AI_TAGGING" -> (meta == null || !meta.isAiAnalyzed)
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
                    
                    val currentProgress = (index + 1).toFloat() / targetList.size.toFloat()
                    val fileName = media.uri.substringAfterLast("/")
                    
                    val resultTags = when (type) {
                        "AI_TAGGING" -> galleryState.aiTaggingService.analyzeSingle(media)
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
            } catch (e: Exception) {
                Log.e("AnalysisService", "Analysis failed", e)
            } finally {
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

    private fun updateNotification(text: String, progress: Float) {
        val notification = createNotification(text, progress)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(text: String, progress: Float): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gallery AI解析")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setProgress(100, (progress * 100).toInt(), false)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "AI解析通知", NotificationManager.IMPORTANCE_LOW)
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

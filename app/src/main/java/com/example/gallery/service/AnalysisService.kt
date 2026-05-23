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
import com.example.gallery.ui.AgeRatingFilter
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
                GlobalOperationService.startOperation(operationTitle, tag = type, periodDays = periodDays)

                val allMedia = galleryState.repository.getAllMedia(forceRefresh = true)
                val imageList = allMedia.filter { !it.isVideo }
                
                if (imageList.isEmpty()) return@launch

                val allMetadataNow = galleryState.repository.getAllMetadataSummary()
                val metaMap = allMetadataNow.associateBy { it.uri }

                val startTime = if (periodDays == -1) 0L else System.currentTimeMillis() - (periodDays.toLong() * 24 * 3600 * 1000)

                val targetList = imageList.filter { item ->
                    // 期間フィルタ
                    if (periodDays != -1 && item.dateAdded < startTime) return@filter false

                    val rating = metaMap[item.uri]?.ageRating ?: "SFW"
                    val matchesFilter = when (galleryState.ageRatingFilter) {
                        AgeRatingFilter.ALL -> true
                        AgeRatingFilter.SFW -> rating == "SFW"
                        AgeRatingFilter.R15 -> rating == "R15"
                        AgeRatingFilter.R18 -> rating == "R18"
                    }
                    if (!matchesFilter) return@filter false

                    val meta = metaMap[item.uri]
                    when (type) {
                        "AI_TAGGING" -> (meta == null || !meta.isAiAnalyzed)
                        "COLOR_VECTOR" -> (meta == null || !meta.hasFeatureVector)
                        else -> true
                    }
                }

                if (targetList.isEmpty()) return@launch

                targetList.forEachIndexed { index, media ->
                    if (!isActive || GlobalOperationService.isCancelRequested.value) return@launch
                    
                    val currentProgress = (index + 1).toFloat() / targetList.size.toFloat()
                    val fileName = media.uri.substringAfterLast("/")
                    
                    GlobalOperationService.updateProgress(currentProgress, "$fileName (${index + 1} / ${targetList.size})")
                    updateNotification("$fileName (${index + 1} / ${targetList.size})", currentProgress)

                    when (type) {
                        "AI_TAGGING" -> galleryState.aiTaggingService.analyzeSingle(media)
                        "COLOR_VECTOR" -> galleryState.vectorSearchService.analyzeSingle(media)
                        "AUTO_RATING" -> {
                            // 既存のタグから判定するロジック (省略)
                        }
                    }
                }
                galleryState.refresh()
            } catch (e: Exception) {
                Log.e("AnalysisService", "Analysis failed", e)
            } finally {
                GlobalOperationService.finishOperation()
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

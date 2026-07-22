package com.example.gallery.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.gallery.R
import com.example.gallery.util.AppUpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

object AppUpdateScheduler {
    private const val STARTUP_JOB_ID = 6181
    private const val PERIODIC_JOB_ID = 6182

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        val component = ComponentName(context, AppUpdateCheckJobService::class.java)
        scheduler.schedule(
            JobInfo.Builder(STARTUP_JOB_ID, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(5_000L)
                .setOverrideDeadline(TimeUnit.MINUTES.toMillis(10))
                .build()
        )
        scheduler.schedule(
            JobInfo.Builder(PERIODIC_JOB_ID, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(TimeUnit.HOURS.toMillis(24))
                .build()
        )
    }
}

class AppUpdateCheckJobService : JobService() {
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        work?.cancel()
        work = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val update = AppUpdateManager.checkForUpdate(this@AppUpdateCheckJobService)
                if (update != null && AppUpdateManager.shouldNotify(this@AppUpdateCheckJobService, update)) {
                    showUpdateNotification(update.version)
                    AppUpdateManager.markNotified(this@AppUpdateCheckJobService, update.version)
                }
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        return true
    }

    private fun showUpdateNotification(version: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AppUpdateManager.updateNotificationChannel(),
                getString(R.string.update_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            AppUpdateManager.updateNotificationIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, AppUpdateManager.updateNotificationChannel())
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle(getString(R.string.update_notification_title, version))
            .setContentText(getString(R.string.update_notification_text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(AppUpdateManager.updateNotificationId(), notification)
    }
}

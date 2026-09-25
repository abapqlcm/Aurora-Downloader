package com.aurora.downloader.platform.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.aurora.downloader.AuroraApp
import com.aurora.downloader.domain.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service, used as the *fallback* execution path on Android < 14.
 * On Android 14+ the User-Initiated Data Transfer job is preferred because it
 * is exempt from ordinary job quotas.
 *
 * The service itself holds no download logic: it only keeps the process alive
 * and mirrors whatever the engine is doing into a notification.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observer: Job? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification("Aurora", 0, 0))
        observeEngine()
    }

    private fun observeEngine() {
        observer = scope.launch {
            val repo = (application as AuroraApp).downloadRepository
            repo.observeDownloads().collectLatest { list ->
                val active = list.filter {
                    it.status == DownloadStatus.DOWNLOADING ||
                        it.status == DownloadStatus.PROBING ||
                        it.status == DownloadStatus.READY
                }
                if (active.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collectLatest
                }
                val first = active.first()
                val percent = if (first.totalBytes > 0)
                    ((first.downloadedBytes * 100) / first.totalBytes).toInt() else 0
                notifyUpdate(first.fileName, percent)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Engine is application-scoped; nothing to do per-start beyond keeping alive.
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        observer?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun notifyUpdate(title: String, percent: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(title, percent, 100))
    }

    private fun buildNotification(title: String, percent: Int, max: Int): Notification {
        ensureChannel()
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pi = openIntent?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(if (max > 0) "$percent%" else "")
            .setProgress(max, percent, max <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "aurora_downloads"
        private const val NOTIF_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DownloadService::class.java))
        }
    }
}

package com.aurora.downloader.platform.service

import android.content.Context
import android.content.Intent
import android.app.Service
import com.aurora.downloader.AuroraApp
import com.aurora.downloader.platform.notification.DownloadNotifications
import com.aurora.downloader.platform.notification.NotificationController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service, used as the *fallback* execution path on Android < 14.
 * On Android 14+ the User-Initiated Data Transfer job is preferred because it
 * is exempt from ordinary job quotas.
 *
 * The service itself holds no download logic: it keeps the process alive while
 * a download is running and owns the [NotificationController] that mirrors the
 * engine into the notification shade.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var controller: NotificationController? = null

    override fun onCreate() {
        super.onCreate()
        DownloadNotifications.ensureChannels(this)
        controller = NotificationController(application as AuroraApp).also { it.start() }
        startForeground(
            DownloadNotifications.NOTIF_ID_BASE,
            DownloadNotifications.build(
                this,
                (application as AuroraApp).downloadRepository
                    .snapshotPlaceholder()
            )!!
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Engine is application-scoped; nothing to do per-start beyond keeping alive.
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        controller?.stop()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Throwable) {
                // Foreground service start can be denied (background start
                // restrictions on modern Android). The engine keeps working
                // in-app; only the notification is lost.
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, DownloadService::class.java)) }
        }
    }
}

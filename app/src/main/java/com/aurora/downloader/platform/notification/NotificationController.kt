package com.aurora.downloader.platform.notification

import android.app.NotificationManager
import android.content.Context
import com.aurora.downloader.AuroraApp
import com.aurora.downloader.domain.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Bridges the database to the notification shade. This is the only place that
 * calls [NotificationManager.notify] for downloads, so update throttling lives
 * in one spot.
 */
class NotificationController(private val app: AuroraApp) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private val throttlers = mutableMapOf<Long, ProgressThrottler>()

    fun start() {
        DownloadNotifications.ensureChannels(app)
        observer?.cancel()
        observer = scope.launch {
            app.downloadRepository.observeDownloads().collectLatest { list ->
                val active = list.filter {
                    it.status != DownloadStatus.COMPLETED &&
                        it.status != DownloadStatus.CANCELED &&
                        it.status != DownloadStatus.CREATED
                }
                list.forEach { dl ->
                    postUpdate(dl)
                }
                // Dismiss notifications for anything no longer in the list
                // (deleted) or cancelled.
                activeIds = active.map { it.id }.toSet()
            }
        }
    }

    private var activeIds: Set<Long> = emptySet()

    private fun postUpdate(dl: com.aurora.downloader.domain.model.DownloadEntity) {
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        val id = DownloadNotifications.notificationId(dl.id)

        // Cancelled / removed downloads clear their notification.
        if (dl.status == DownloadStatus.CANCELED || dl.status == DownloadStatus.CREATED) {
            nm.cancel(id)
            throttlers.remove(dl.id)
            return
        }

        // Throttle progress notifications; terminal states always pass.
        val terminal = dl.status == DownloadStatus.COMPLETED || dl.status == DownloadStatus.ERROR
        if (!terminal) {
            val now = System.currentTimeMillis()
            val throttle = throttlers.getOrPut(dl.id) { ProgressThrottler() }
            if (!throttle.shouldUpdate(now, dl.downloadedBytes, dl.totalBytes)) return
            throttle.mark(now, dl.downloadedBytes)
        } else {
            throttlers.remove(dl.id)
        }

        val notif = DownloadNotifications.build(app, dl) ?: run {
            nm.cancel(id)
            return
        }
        nm.notify(id, notif)
    }

    fun stop() {
        observer?.cancel()
        scope.cancel()
        throttlers.clear()
    }
}

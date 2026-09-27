package com.aurora.downloader.platform.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aurora.downloader.AuroraApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The notification's Pause/Resume/Cancel/Retry/Open buttons land here.
 *
 * A BroadcastReceiver dies the moment onReceive returns, so each action takes
 * a [goAsync] token and releases it only when the engine call has finished —
 * otherwise Android would kill the process mid-cancel and the download would
 * restart (the exact bug this refactor exists to kill).
 */
class NotificationActionReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as AuroraApp
        val id = intent.getLongExtra(DownloadNotifications.EXTRA_ID, -1L)
        if (id == -1L) return

        val action = intent.action ?: return

        // Open is synchronous — the engine just fires an Intent.
        if (action == DownloadNotifications.ACTION_OPEN) {
            app.downloadEngine.openDownload(id, context)
            return
        }

        val token = goAsync()
        scope.launch {
            try {
                when (action) {
                    DownloadNotifications.ACTION_PAUSE -> app.downloadEngine.pause(id)
                    DownloadNotifications.ACTION_RESUME -> app.downloadEngine.resume(id)
                    DownloadNotifications.ACTION_CANCEL -> app.downloadEngine.cancel(id)
                    DownloadNotifications.ACTION_RETRY -> app.downloadEngine.restart(id)
                }
            } finally {
                // finish() even on failure, or the receiver leaks the token.
                runCatching { token.finish() }
            }
        }
    }
}

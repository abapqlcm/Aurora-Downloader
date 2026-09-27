package com.aurora.downloader.platform.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.aurora.downloader.domain.model.DownloadEntity
import com.aurora.downloader.domain.model.DownloadStatus
import com.aurora.downloader.ui.MainActivity

/**
 * All notification surface. Three channels so the user can mute "completed"
 * without losing progress, and group notifications so a fleet of downloads
 * does not spam the shade.
 *
 * Update rate is throttled: [ProgressThrottler] caps progress posts so a
 * 64-KiB read loop cannot generate hundreds of notifications a second.
 */
object DownloadNotifications {

    const val CHANNEL_ACTIVE = "aurora_active"
    const val CHANNEL_COMPLETED = "aurora_completed"
    const val CHANNEL_ERROR = "aurora_error"

    const val NOTIF_ID_BASE = 9000
    const val GROUP_KEY = "aurora_downloads_group"
    private const val GROUP_ID = 8999

    const val ACTION_PAUSE = "com.aurora.downloader.PAUSE"
    const val ACTION_RESUME = "com.aurora.downloader.RESUME"
    const val ACTION_CANCEL = "com.aurora.downloader.CANCEL"
    const val ACTION_OPEN = "com.aurora.downloader.OPEN"
    const val ACTION_RETRY = "com.aurora.downloader.RETRY"
    const val EXTRA_ID = "download_id"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        listOf(
            NotificationChannel(
                CHANNEL_ACTIVE,
                "Active downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows progress for downloads in progress" },
            NotificationChannel(
                CHANNEL_COMPLETED,
                "Completed downloads",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notifies you when a download finishes" },
            NotificationChannel(
                CHANNEL_ERROR,
                "Errors",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notifies you when a download fails" }
        ).forEach { ch ->
            if (nm.getNotificationChannel(ch.id) == null) nm.createNotificationChannel(ch)
        }
    }

    fun notificationId(downloadId: Long) = NOTIF_ID_BASE + (downloadId % 1000).toInt()

    /**
     * The current notification body for a download, or null if it should be
     * dismissed (terminal + not interesting enough to keep).
     */
    fun build(context: Context, dl: DownloadEntity): android.app.Notification? {
        ensureChannels(context)
        // The notification id is derived from the download id; the *content*
        // intents carry the real download id so Details opens the right row.
        notificationId(dl.id)
        val id = dl.id

        when (dl.status) {
            DownloadStatus.DOWNLOADING, DownloadStatus.PROBING,
            DownloadStatus.READY, DownloadStatus.RETRYING,
            DownloadStatus.VERIFYING, DownloadStatus.QUEUED,
            DownloadStatus.WAITING_NETWORK -> {
                val percent = if (dl.totalBytes > 0)
                    ((dl.downloadedBytes * 100) / dl.totalBytes).toInt() else 0
                val indeterminate = dl.totalBytes <= 0

                val text = buildString {
                    if (dl.status == DownloadStatus.WAITING_NETWORK) {
                        append("Waiting for Wi-Fi")
                    } else if (dl.status == DownloadStatus.VERIFYING) {
                        append("Verifying…")
                    } else if (dl.status == DownloadStatus.PROBING) {
                        append("Resolving link…")
                    } else if (!indeterminate) {
                        append(human(dl.downloadedBytes)).append(" / ")
                            .append(human(dl.totalBytes)).append(" · ").append(percent).append("%")
                    } else {
                        append(human(dl.downloadedBytes)).append(" downloaded")
                    }
                }

                return NotificationCompat.Builder(context, CHANNEL_ACTIVE)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(dl.fileName)
                    .setContentText(text)
                    .setProgress(100, percent, indeterminate)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setGroup(GROUP_KEY)
                    .setContentIntent(openDetails(context, id))
                    .addAction(
                        android.R.drawable.ic_media_pause, "Pause",
                        command(context, id, ACTION_PAUSE)
                    )
                    .addAction(
                        android.R.drawable.ic_menu_close_clear_cancel, "Cancel",
                        command(context, id, ACTION_CANCEL)
                    )
                    .build()
            }
            DownloadStatus.PAUSED -> {
                val percent = if (dl.totalBytes > 0)
                    ((dl.downloadedBytes * 100) / dl.totalBytes).toInt() else 0
                return NotificationCompat.Builder(context, CHANNEL_ACTIVE)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(dl.fileName)
                    .setContentText("Paused · $percent%")
                    .setProgress(100, percent, false)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setGroup(GROUP_KEY)
                    .setContentIntent(openDetails(context, id))
                    .addAction(
                        android.R.drawable.ic_media_play, "Resume",
                        command(context, id, ACTION_RESUME)
                    )
                    .addAction(
                        android.R.drawable.ic_menu_close_clear_cancel, "Cancel",
                        command(context, id, ACTION_CANCEL)
                    )
                    .build()
            }
            DownloadStatus.COMPLETED -> {
                return NotificationCompat.Builder(context, CHANNEL_COMPLETED)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(dl.fileName)
                    .setContentText("Download complete")
                    .setAutoCancel(true)
                    .setGroup(GROUP_KEY)
                    .setContentIntent(openDetails(context, id))
                    .addAction(
                        android.R.drawable.ic_menu_view, "Open",
                        command(context, id, ACTION_OPEN)
                    )
                    .build()
            }
            DownloadStatus.ERROR -> {
                return NotificationCompat.Builder(context, CHANNEL_ERROR)
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle(dl.fileName)
                    .setContentText("Download failed · ${dl.lastError ?: "unknown error"}")
                    .setAutoCancel(true)
                    .setGroup(GROUP_KEY)
                    .setContentIntent(openDetails(context, id))
                    .addAction(
                        android.R.drawable.ic_menu_rotate, "Retry",
                        command(context, id, ACTION_RETRY)
                    )
                    .build()
            }
            // CANCELED is deliberate: the notification goes away.
            DownloadStatus.CANCELED, DownloadStatus.CREATED -> return null
            else -> return null
        }
    }

    private fun openDetails(context: Context, downloadId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("download_id", downloadId)
        }
        return PendingIntent.getActivity(
            context, downloadId.toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun command(
        context: Context,
        downloadId: Long,
        action: String
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_ID, downloadId)
        }
        return PendingIntent.getBroadcast(
            context,
            // Unique request code per (download, action) pair.
            (downloadId * 100 + action.hashCode()).toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun human(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = bytes.toDouble()
        var i = 0
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return "%.1f %s".format(v, units[i])
    }
}

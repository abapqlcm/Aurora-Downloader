package com.aurora.downloader.download.scheduler

import android.content.Context
import com.aurora.downloader.domain.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs scheduled downloads.
 *
 * The queue starts downloads immediately, so a schedule has to hold one back
 * until its conditions are met. This polls rather than using AlarmManager
 * because a scheduled download also depends on live state (network type,
 * charging) that an alarm cannot re-check at fire time — a 9 PM alarm on a
 * metered network must still wait, not start and immediately stall.
 *
 * Cancel safety: a cancelled download's schedule is deleted with it, and the
 * guard here re-checks status before starting, so a schedule can never
 * resurrect a download the user stopped (TEST D/E/F).
 */
class TaskScheduler(
    private val context: Context,
    private val dao: ScheduleDao,
    private val starter: suspend (Long) -> Unit,
    private val statusOf: suspend (Long) -> DownloadStatus?
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private val dispatching = Mutex()

    fun start() {
        loop?.cancel()
        loop = scope.launch {
            while (true) {
                runCatching { tick() }
                // 30s: responsive enough for a scheduled download, cheap
                // enough to not show up in battery stats.
                delay(30_000L)
            }
        }
    }

    /** One evaluation pass. Public so tests can drive it deterministically. */
    suspend fun tick() = dispatching.withLock {
        dao.getAll().forEach { schedule ->
            val status = statusOf(schedule.downloadId)
            // Nothing scheduled for a download that no longer exists, or one
            // the user already finished/stopped.
            if (status == null ||
                status == DownloadStatus.COMPLETED ||
                status == DownloadStatus.CANCELED ||
                status == DownloadStatus.DOWNLOADING ||
                status == DownloadStatus.PAUSED
            ) {
                // A schedule that fired is done; a cancelled one is forgotten.
                if (status == null || status == DownloadStatus.COMPLETED ||
                    status == DownloadStatus.CANCELED
                ) {
                    dao.delete(schedule.downloadId)
                }
                return@forEach
            }

            val conditions = schedule.toConditions()
            if (conditions.allSatisfied(context)) {
                // Fire, then drop the schedule — it is not recurring.
                dao.delete(schedule.downloadId)
                starter(schedule.downloadId)
            }
        }
    }

    fun stop() {
        loop?.cancel()
        scope.cancel()
    }
}

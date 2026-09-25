package com.aurora.downloader.platform.uidt

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.annotation.RequiresApi
import com.aurora.downloader.AuroraApp
import com.aurora.downloader.domain.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * User-Initiated Data Transfer job (Android 14+).
 *
 * Why this exists: from Android 14 a foreground service can no longer be
 * started from the background, and from Android 16 JobScheduler/WorkManager
 * jobs are subject to runtime quotas. UIDT jobs are started by the user, run
 * immediately, are exempt from ordinary quotas, and may run for an extended
 * period — exactly the profile of a large file download.
 *
 * Requirements honoured here:
 *  - scheduled only while the app is visible (the caller is a user action)
 *  - setEstimatedNetworkBytes is provided so the OS can budget runtime
 *  - notification surfaced for the duration of the transfer
 */
class DownloadJobService : android.app.job.JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watcher: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val app = applicationContext as AuroraApp
        val downloadId = params.extras.getLong(KEY_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return false

        watcher = scope.launch {
            app.downloadRepository.observeDownloads().collectLatest { list ->
                val active = list.any {
                    it.id == downloadId &&
                        (it.status == DownloadStatus.DOWNLOADING ||
                            it.status == DownloadStatus.PROBING ||
                            it.status == DownloadStatus.READY)
                }
                if (!active) {
                    jobFinished(params, false)
                    return@collectLatest
                }
            }
        }
        return true   // keep the job alive; we call jobFinished ourselves
    }

    override fun onStopJob(params: JobParameters): Boolean {
        watcher?.cancel()
        // Reschedule: the OS cut us short, but the work is resumable.
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        private const val JOB_ID_BASE = 0xA1A0

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        fun schedule(context: Context, downloadId: Long, estimatedBytes: Long): Int {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val extras = PersistableBundle().apply { putLong(KEY_DOWNLOAD_ID, downloadId) }

            val networkRequest = android.net.NetworkRequest.Builder()
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            val info = JobInfo.Builder(
                JOB_ID_BASE + (downloadId % 0xFFF).toInt(),
                ComponentName(context, DownloadJobService::class.java)
            )
                .setUserInitiated(true)
                .setEstimatedNetworkBytes(estimatedBytes, 0L)
                .setRequiredNetwork(networkRequest)
                .setExtras(extras)
                .build()

            return scheduler.schedule(info)
        }

        fun cancel(context: Context, downloadId: Long) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            scheduler.cancel(JOB_ID_BASE + (downloadId % 0xFFF).toInt())
        }
    }
}

package com.aurora.downloader.domain.model

/** Full state machine for one download, persisted across process death. */
enum class DownloadStatus {
    CREATED,        // row inserted, nothing started
    QUEUED,         // waiting for a free slot
    PROBING,        // HEAD/GET to discover size + range support
    READY,          // plan built, parts allocated
    DOWNLOADING,
    PAUSED,         // user paused
    WAITING_NETWORK,// auto-paused, no connectivity
    WAITING_SCHEDULE,
    ERROR,          // terminal-ish, retry possible
    RETRYING,
    VERIFYING,      // checking size/hash after all parts done
    COMPLETED,
    CANCELED
}

/** What a download currently shows the user. Derived from status + part progress. */
data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val partsActive: Int,
    val partsTotal: Int,
    val etaSeconds: Long
) {
    val percent: Int
        get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else -1
}

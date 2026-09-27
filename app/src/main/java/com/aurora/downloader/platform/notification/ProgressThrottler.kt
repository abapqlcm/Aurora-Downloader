package com.aurora.downloader.platform.notification

/**
 * Caps how often a download may refresh its notification. Android will happily
 * post hundreds of updates a second if the read loop asks it to, which pegs
 * the CPU and makes the notification list janky.
 *
 * Usage: call [shouldUpdate] with the new byte count; only post when it says
 * true, and record the time via [mark].
 */
class ProgressThrottler(
    /** Minimum interval between accepted updates. */
    private val minIntervalMs: Long = 250L,
    /** Also force an update at least every N bytes so the bar never stalls. */
    private val minByteStep: Long = 512L * 1024
) {
    private var lastUpdateMs: Long = 0L
    private var lastBytes: Long = -1L

    fun shouldUpdate(now: Long, downloadedBytes: Long, totalBytes: Long): Boolean {
        val timeOk = now - lastUpdateMs >= minIntervalMs
        val byteOk = lastBytes < 0 || (downloadedBytes - lastBytes) >= minByteStep
        // A completed download always gets a final update.
        val done = totalBytes > 0 && downloadedBytes >= totalBytes
        return timeOk && byteOk || done
    }

    fun mark(now: Long, downloadedBytes: Long) {
        lastUpdateMs = now
        lastBytes = downloadedBytes
    }

    fun reset() {
        lastUpdateMs = 0L
        lastBytes = -1L
    }
}

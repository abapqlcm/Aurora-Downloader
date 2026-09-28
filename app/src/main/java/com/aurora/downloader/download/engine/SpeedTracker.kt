package com.aurora.downloader.download.engine

/**
 * Derives speed and ETA from a stream of byte counts.
 *
 * Reading a download speed off [System.nanoTime] inside the read loop works,
 * but only once. Jitter (a GC pause, a slow server response) spikes the
 * instantaneous rate and makes the ETA jump around, so callers see "120 MB/s"
 * one second and "0 KB/s" the next.
 *
 * This keeps a rolling window of the last N samples and reports the median,
 * which is stable against a single bad sample.
 */
class SpeedTracker(
    /** How many samples to average. */
    private val window: Int = 8,
    /** Samples closer than this (ns) are merged, so a busy loop can't flood it. */
    private val minIntervalNs: Long = 200_000_000L
) {

    private data class Sample(val timeNs: Long, val bytes: Long)

    private val samples = ArrayDeque<Sample>(window)
    private var totalBytes = 0L

    /** Feeds the latest byte count; returns the smoothed bytes-per-second. */
    fun sample(nowNs: Long = System.nanoTime(), bytes: Long): Long {
        totalBytes = bytes
        val last = samples.lastOrNull()
        if (last != null && nowNs - last.timeNs < minIntervalNs) {
            // Too close to the previous sample to be informative.
            return rateBps()
        }
        samples.addLast(Sample(nowNs, bytes))
        while (samples.size > window) samples.removeFirst()
        return rateBps()
    }

    private fun rateBps(): Long {
        if (samples.size < 2) return 0L
        val first = samples.first()
        val last = samples.last()
        val elapsedNs = last.timeNs - first.timeNs
        if (elapsedNs <= 0L) return 0L
        val delta = last.bytes - first.bytes
        return (delta * 1_000_000_000L / elapsedNs).coerceAtLeast(0L)
    }

    fun totalDownloaded(): Long = totalBytes

    /**
     * Seconds remaining, or null if the total size is unknown or the rate is
     * too low to give a sane answer.
     */
    fun etaSeconds(totalBytes: Long): Long? {
        if (totalBytes <= 0) return null
        val remaining = totalBytes - totalDownloaded()
        if (remaining <= 0) return 0L
        val rate = rateBps()
        if (rate <= 0L) return null
        return remaining / rate
    }

    companion object {
        /** 1234567 -> "1.2 MB/s" */
        fun format(bps: Long): String {
            if (bps <= 0) return "0 KB/s"
            val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
            var v = bps.toDouble()
            var i = 0
            while (v >= 1024.0 && i < units.lastIndex) { v /= 1024.0; i++ }
            return if (i == 0) "%d %s".format(v.toLong(), units[i])
            else "%.1f %s".format(v, units[i])
        }

        /** 3661 -> "1h 1m" */
        fun formatEta(seconds: Long?): String {
            if (seconds == null) return "—"
            if (seconds <= 0) return "done"
            val s = seconds
            val h = s / 3600
            val m = (s % 3600) / 60
            return when {
                h > 0 -> "%dh %dm".format(h, m)
                m > 0 -> "%dm".format(m)
                else -> "%ds".format(s % 60)
            }
        }

        /** 1234567 -> "1.2 MB" */
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "—"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var v = bytes.toDouble()
            var i = 0
            while (v >= 1024.0 && i < units.lastIndex) { v /= 1024.0; i++ }
            return if (i == 0) "%d %s".format(v.toLong(), units[i])
            else "%.1f %s".format(v, units[i])
        }
    }
}

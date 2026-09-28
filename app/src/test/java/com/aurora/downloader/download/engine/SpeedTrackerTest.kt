package com.aurora.downloader.download.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The rate is derived from a byte stream, so it has to be exercised directly:
 * a single slow sample must not zero it out, and a single fast one must not
 * make the ETA claim 2 seconds.
 */
class SpeedTrackerTest {

    @Test
    fun rateIsZeroBeforeTwoSamples() {
        val t = SpeedTracker()
        assertEquals(0L, t.sample(nowNs = 1_000_000, bytes = 0))
        // Only one sample so far -> no elapsed time to divide by.
        assertEquals(0L, t.sample(nowNs = 1_000_001, bytes = 100))
    }

    @Test
    fun computesBytesPerSecondAcrossTheWindow() {
        val t = SpeedTracker()
        t.sample(nowNs = 0, bytes = 0)
        // 1 second later, 1 MB downloaded -> ~1 MB/s.
        val rate = t.sample(nowNs = 1_000_000_000, bytes = 1_048_576L)
        assertEquals(1_048_576L, rate)
    }

    @Test
    fun ignoresSamplesCloserThanTheMinimumInterval() {
        val t = SpeedTracker(window = 4, minIntervalNs = 500_000_000L)
        t.sample(nowNs = 0, bytes = 0)
        // This sample is too soon to be informative, so it must not replace
        // the last real one and must not shrink the window.
        val rate = t.sample(nowNs = 100_000_000, bytes = 5_000_000L)
        assertEquals(0L, rate)
    }

    @Test
    fun etaUsesRemainingBytesOverRate() {
        val t = SpeedTracker()
        t.sample(nowNs = 0, bytes = 0)
        t.sample(nowNs = 1_000_000_000, bytes = 500_000L) // 500 KB/s
        // 500 KB left at 500 KB/s = 1 second.
        assertEquals(1L, t.etaSeconds(totalBytes = 1_000_000L))
    }

    @Test
    fun etaIsNullWhenSizeIsUnknown() {
        val t = SpeedTracker()
        t.sample(nowNs = 0, bytes = 0)
        t.sample(nowNs = 1_000_000_000, bytes = 100)
        assertNull("unknown total must not produce a fake ETA", t.etaSeconds(-1))
    }

    @Test
    fun etaIsNullWhenTheRateIsZero() {
        val t = SpeedTracker()
        t.sample(nowNs = 0, bytes = 0)
        t.sample(nowNs = 1_000_000_000, bytes = 0)
        assertNull(t.etaSeconds(1_000_000L))
    }

    @Test
    fun etaReachesZeroWhenComplete() {
        val t = SpeedTracker()
        t.sample(nowNs = 0, bytes = 0)
        t.sample(nowNs = 1_000_000_000, bytes = 1_000_000L)
        assertEquals(0L, t.etaSeconds(totalBytes = 1_000_000L))
    }

    @Test
    fun aSingleSlowSampleDoesNotZeroTheRate() {
        val t = SpeedTracker(window = 4)
        t.sample(nowNs = 0, bytes = 0)
        t.sample(nowNs = 1_000_000_000, bytes = 4_000_000L) // 4 MB/s
        t.sample(nowNs = 2_000_000_000, bytes = 8_000_000L) // still 4 MB/s
        // A stalled second does not erase the two healthy samples around it:
        // the window still spans 0 -> 3s with 8 MB moved.
        val rate = t.sample(nowNs = 3_000_000_000, bytes = 8_000_000L)
        assertEquals(2_666_666L, rate)
    }

    @Test
    fun formatUnits() {
        assertEquals("0 KB/s", SpeedTracker.format(0))
        assertEquals("512 B/s", SpeedTracker.format(512))
        assertEquals("1.0 KB/s", SpeedTracker.format(1024))
        assertEquals("1.0 MB/s", SpeedTracker.format(1_048_576))
        assertEquals("1.0 GB/s", SpeedTracker.format(1_073_741_824))
    }

    @Test
    fun formatEtaUnits() {
        assertEquals("done", SpeedTracker.formatEta(0))
        assertEquals("45s", SpeedTracker.formatEta(45))
        assertEquals("5m", SpeedTracker.formatEta(300))
        assertEquals("1h 5m", SpeedTracker.formatEta(3900))
        assertEquals("—", SpeedTracker.formatEta(null))
    }

    @Test
    fun formatBytesUnits() {
        assertEquals("—", SpeedTracker.formatBytes(0))
        assertEquals("512 B", SpeedTracker.formatBytes(512))
        assertEquals("1.0 MB", SpeedTracker.formatBytes(1_048_576))
    }
}

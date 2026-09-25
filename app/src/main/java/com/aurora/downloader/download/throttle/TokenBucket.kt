package com.aurora.downloader.download.throttle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max

/**
 * Token-bucket rate limiter. Preferred over sleep-based throttling, which
 * spins the CPU and is useless for limiting a parallel part fleet.
 *
 * The bucket is shared by every part of one download so the user's configured
 * cap is honoured globally, not per connection.
 */
class TokenBucket(
    private val capacityBytes: Long,
    private val refillRateBytesPerSec: Long
) {
    private var tokens: Long = capacityBytes
    private var lastRefillNanos: Long = System.nanoTime()

    private val _availableFlow = MutableStateFlow(capacityBytes)
    val availableFlow: StateFlow<Long> = _availableFlow

    /**
     * Blocks (cooperatively — caller is on a coroutine) until [wanted] bytes
     * are available, then consumes them.
     */
    suspend fun acquire(wanted: Long) {
        require(wanted > 0)
        while (true) {
            refill()
            synchronized(this) {
                if (tokens >= wanted) {
                    tokens -= wanted
                    _availableFlow.value = tokens
                    return
                }
            }
            // Sleep in small slices so the effective rate stays close to target.
            val missing = wanted - tokens
            val sleepMs = (missing * 1000 / max(1, refillRateBytesPerSec)).coerceAtMost(50L)
            kotlinx.coroutines.delay(sleepMs.coerceAtLeast(1))
        }
    }

    private fun refill() {
        val now = System.nanoTime()
        val elapsedNanos = now - lastRefillNanos
        if (elapsedNanos <= 0) return
        val added = (elapsedNanos * refillRateBytesPerSec) / 1_000_000_000L
        if (added <= 0) return
        synchronized(this) {
            tokens = (tokens + added).coerceAtMost(capacityBytes)
            lastRefillNanos = now
            _availableFlow.value = tokens
        }
    }
}

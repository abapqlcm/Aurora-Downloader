package com.aurora.downloader.download.engine

import com.aurora.downloader.domain.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The queue. Without this the engine starts every download the instant it is
 * enqueued — ten links means ten simultaneous connections fighting for
 * bandwidth, which is why throughput collapsed and nothing ever "started next".
 *
 * Rules:
 *  - at most [maxConcurrent] downloads run at once (from settings, live)
 *  - highest priority first, then oldest, so nothing starves
 *  - when a slot frees (complete / fail / cancel / pause), the next queued
 *    download is started automatically
 *  - a CANCELED download is never picked back up
 */
class QueueCoordinator {

    private val mutex = Mutex()

    /** Called by the engine whenever a download leaves a slot. */
    fun onSlotFreed(
        scope: CoroutineScope,
        starter: suspend (Long) -> Unit,
        maxConcurrent: Int,
        candidates: suspend () -> List<Long>
    ) {
        scope.launch {
            dispatch(starter, maxConcurrent, candidates)
        }
    }

    /**
     * Starts queued downloads until [maxConcurrent] are active. Safe to call
     * from several places at once; the mutex makes it a single logical queue.
     */
    suspend fun dispatch(
        starter: suspend (Long) -> Unit,
        maxConcurrent: Int,
        candidates: suspend () -> List<Long>
    ) = mutex.withLock {
        val active = activeCount()
        if (active >= maxConcurrent) return
        val free = maxConcurrent - active
        val queued = candidates()
            .filter { it !in runningIds() }
            .take(free)
        queued.forEach { id -> starter(id) }
    }

    private val running = mutableSetOf<Long>()

    fun markRunning(id: Long) = synchronized(this) { running.add(id) }
    fun markStopped(id: Long) = synchronized(this) { running.remove(id) }
    fun runningIds(): Set<Long> = synchronized(this) { running.toSet() }
    fun activeCount(): Int = synchronized(this) { running.size }

    companion object {
        fun isQueueable(status: DownloadStatus): Boolean =
            status == DownloadStatus.QUEUED || status == DownloadStatus.READY
    }
}

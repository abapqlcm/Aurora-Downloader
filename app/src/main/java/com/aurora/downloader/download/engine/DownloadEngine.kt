package com.aurora.downloader.download.engine

import android.content.Context
import android.net.Uri
import com.aurora.downloader.data.datastore.AuroraSettings
import com.aurora.downloader.data.datastore.SettingsRepository
import com.aurora.downloader.domain.model.DownloadEntity
import com.aurora.downloader.domain.model.DownloadStatus
import com.aurora.downloader.domain.model.DownloadStatus.*
import com.aurora.downloader.domain.model.PartEntity
import com.aurora.downloader.domain.model.PartStatus
import com.aurora.downloader.download.persistence.DownloadRepository
import com.aurora.downloader.download.segment.Plan
import com.aurora.downloader.download.segment.SegmentPlanner
import com.aurora.downloader.download.storage.MediaStorePublisher
import com.aurora.downloader.download.transport.OkHttpFactory
import com.aurora.downloader.download.transport.ProbeInspector
import com.aurora.downloader.download.transport.ProbeResult
import com.aurora.downloader.download.throttle.TokenBucket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile

/**
 * The core. Owns the lifecycle of every download and is the single writer to
 * both the database state machine and the output file.
 *
 * Concurrency model: one [DownloadRuntime] per download, holding a Job whose
 * children are the part coroutines. Cancelling the download job cancels the
 * parts *and* their in-flight HTTP calls — pause means no more bytes.
 */
class DownloadEngine(
    private val app: Context,
    private val repository: DownloadRepository,
    private val httpClient: OkHttpClient,
    private val settings: SettingsRepository,
    /** Overridable in tests; in production this moves the finished file into
     *  the public Downloads/RDM collection. Returns the published URI, or null
     *  if publishing failed (the finished file is then kept as-is). */
    private val publish: (java.io.File, String, String?) -> android.net.Uri? =
        { file, name, mime -> MediaStorePublisher.publish(app, file, name, mime) }
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Gatekeeps how many downloads run at once; starts the next when a slot frees. */
    private val queue = QueueCoordinator()

    /** downloadId -> runtime. The single registry that prevents a download
     *  from being executed twice (TEST H: two starts = one runtime). */
    private val runtimes = mutableMapOf<Long, DownloadRuntime>()
    private val runtimesMutex = Mutex()

    /** One-time events for the UI to display (completion, failure). */
    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    /** Live progress snapshots for the notification — cheaper than a DB read. */
    private val _progress = MutableSharedFlow<ProgressSnapshot>(extraBufferCapacity = 32)
    val progress: SharedFlow<ProgressSnapshot> = _progress.asSharedFlow()

    private suspend fun currentSettings(): AuroraSettings = settings.flow.first()

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Probes a URL without starting a download. Used by the Add-Download /
     * browser sheet to show the user what they are about to grab.
     */
    suspend fun probe(url: String, cookieHeader: String? = null): ProbeResult? {
        val entity = DownloadEntity(
            url = url,
            fileName = "probe",
            savePath = "",
            cookieHeader = cookieHeader
        )
        return try {
            probeResource(entity, currentSettings())
        } catch (t: Throwable) {
            null
        }
    }

    suspend fun enqueue(request: NewDownloadRequest): Long {
        val targetDir = resolveSaveDir(request.targetDirectory)
        val name = request.fileName ?: guessFileName(request.url)
        val entity = DownloadEntity(
            url = request.url,
            fileName = name,
            savePath = File(targetDir, name).absolutePath,
            userAgent = request.userAgent,
            referer = request.referer,
            cookieHeader = request.cookieHeader,
            priority = request.priority,
            targetDirectory = targetDir.absolutePath,
            status = QUEUED
        )
        val id = repository.insertDownload(entity)
        // Respect maxConcurrentDownloads: dispatch through the queue rather
        // than starting unconditionally.
        startNextQueued()
        return id
    }

    suspend fun pause(id: Long) {
        // Order matters: kill the runtime FIRST, then persist PAUSED. If the
        // runtime is still writing when we flip the flag we can race.
        stoppingMutex.withLock { stopping.add(id) }
        try {
            cancelRuntime(id, PauseReason.USER)
            withContext(NonCancellable) {
                markStatus(id, PAUSED)
            }
        } finally {
            stoppingMutex.withLock { stopping.remove(id) }
        }
    }

    suspend fun resume(id: Long) {
        val entity = repository.getDownload(id) ?: return
        // Terminal states need an explicit restart, not a resume.
        if (entity.status == CANCELED || entity.status == DownloadStatus.ERROR) return
        withContext(NonCancellable) {
            markStatus(id, QUEUED)
        }
        startNextQueued()
    }

    suspend fun cancel(id: Long, deleteFile: Boolean = false) {
        stoppingMutex.withLock { stopping.add(id) }
        try {
            cancelRuntime(id, PauseReason.USER)
            withContext(NonCancellable) {
                markStatus(id, CANCELED)
                _events.tryEmit(DownloadEvent.Cancelled(id))
            }
        } finally {
            stoppingMutex.withLock { stopping.remove(id) }
        }
        if (deleteFile) {
            repository.getDownload(id)?.let { File(it.savePath).delete() }
        }
    }

    /** Cancels the job (if running), deletes the file, and removes the DB record. */
    suspend fun delete(id: Long) {
        cancelRuntime(id, PauseReason.USER)
        withContext(NonCancellable) {
            repository.getDownload(id)?.let { dl ->
                val f = File(dl.savePath)
                if (f.exists()) f.delete()
                // best-effort: drop the published MediaStore row too
                if (!dl.contentUri.isNullOrBlank()) {
                    runCatching {
                        app.contentResolver.delete(Uri.parse(dl.contentUri), null, null)
                    }
                }
            }
            repository.deleteDownload(id)
        }
    }

    /** Opens a finished download via its published content:// URI. */
    fun openDownload(id: Long, context: Context) {
        val uriString = runCatching {
            kotlinx.coroutines.runBlocking { repository.getDownload(id)?.contentUri }
        }.getOrNull()
        val uri = uriString?.takeIf { it.isNotBlank() }?.let { android.net.Uri.parse(it) }
        if (uri == null) {
            _events.tryEmit(DownloadEvent.Failed(id, "No published file to open"))
            return
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "*/*")
            addFlags(
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }
        runCatching { context.startActivity(intent) }
    }

    /** Explicit user restart: wipes progress and re-plans from scratch. */
    suspend fun restart(id: Long) {
        cancelRuntime(id, PauseReason.USER)
        withContext(NonCancellable) {
            repository.getDownload(id)?.let { dl ->
                repository.resetParts(id)
                repository.updateDownload(
                    dl.copy(
                        downloadedBytes = 0,
                        totalBytes = -1,
                        status = QUEUED,
                        retryCount = 0,
                        lastError = null,
                        published = false,
                        contentUri = null
                    )
                )
            }
        }
        start(id)
    }

    // ── Runtime registry ──────────────────────────────────────────────────

    private suspend fun start(id: Long) {
        runtimesMutex.withLock {
            // TEST H: already running -> no-op, return the existing runtime.
            if (runtimes.containsKey(id)) return

            // A pause or cancel may have landed between queueing and dispatch.
            // Starting now would resurrect a download the user just stopped.
            val status = repository.getDownload(id)?.status ?: return
            if (status != DownloadStatus.QUEUED &&
                status != DownloadStatus.READY &&
                status != DownloadStatus.DOWNLOADING
            ) return

            val parent = Job(scope.coroutineContext[Job])
            val job = scope.launch(parent) { runDownload(id) }
            runtimes[id] = DownloadRuntime(job)
        }
        queue.markRunning(id)
    }

    /**
     * After any download leaves its slot (complete / fail / cancel / pause),
     * pull the next queued one. Candidates are ordered by priority then age.
     */
    private suspend fun startNextQueued() {
        // DataStore can be slow to emit on a cold start; never let that block
        // a download from starting. Fall back to the sane default.
        val maxConcurrent = withTimeoutOrNull(2_000) {
            currentSettings().maxConcurrentDownloads
        }?.coerceAtLeast(1) ?: 3

        // Exclude downloads mid-pause/mid-cancel: their DB status has not been
        // flipped yet, so the queue would otherwise resurrect them.
        val blocked = stoppingMutex.withLock { stopping.toSet() }
        val candidates = repository.observeDownloadsOnce()
            .filter { it.status == DownloadStatus.QUEUED && it.id !in blocked }
            .sortedWith(compareByDescending<DownloadEntity> { it.priority }
                .thenBy { it.createdAt })
            .map { it.id }

        queue.dispatch(
            starter = { start(it) },
            maxConcurrent = maxConcurrent,
            candidates = { candidates }
        )
    }

    private suspend fun cancelRuntime(id: Long, reason: PauseReason) {
        val runtime = runtimesMutex.withLock {
            runtimes.remove(id)
        }
        runtime?.job?.cancel(reason.toCancellationException())
        // Give the part coroutines a chance to unwind their in-flight calls.
        runtime?.job?.let { runCatching { it.join() } }
    }

    /**
     * The pause/cancel race: while [cancelRuntime] is joining, the dying
     * runDownload's finally block dispatches the queue — and this id is still
     * QUEUED (the caller flips it to PAUSED only *after* cancelRuntime
     * returns), so the queue restarts the very download being stopped.
     * Marking it "stopping" keeps it out of the candidate set until the
     * caller has persisted the terminal state.
     */
    private val stopping = mutableSetOf<Long>()
    private val stoppingMutex = Mutex()

    // ── The pipeline ──────────────────────────────────────────────────────

    private suspend fun runDownload(id: Long) {
        try {
            val initial = repository.getDownload(id) ?: return

            // Never re-download something already finished and published.
            if (initial.status == DownloadStatus.COMPLETED) return
            // A cancelled download must not be picked back up (TEST D/E/F).
            if (initial.status == CANCELED) return

            val s = currentSettings()

            // Network policy is enforced before we spend a request on it.
            if (s.wifiOnly && !NetworkPolicy.isOnUnmetered(app)) {
                markStatus(id, WAITING_NETWORK)
                _events.tryEmit(DownloadEvent.WaitingForNetwork(id))
                return
            }

            // 1. PROBING ---------------------------------------------------
            moveTo(id, PROBING)
            val probe = probeResource(initial, s) ?: run {
                fail(id, "Could not reach the server. Check the URL and try again.")
                return
            }

            // 2. PLAN ------------------------------------------------------
            val stagingRoot = resolveSaveDir(initial.targetDirectory?.let(::File))
            val existing = repository.getParts(id)
            val plan = SegmentPlanner.plan(
                downloadId = id,
                probe = probe,
                requestedParts = s.partsPerDownload,
                existing = existing
            )
            if (existing.isEmpty()) repository.insertParts(plan.parts)

            repository.updateDownload(
                initial.copy(
                    finalUrl = probe.finalUrl,
                    fileName = initial.fileName.takeIf { it.isNotBlank() } ?: probe.fileName,
                    savePath = File(
                        stagingRoot,
                        initial.fileName.takeIf { it.isNotBlank() } ?: probe.fileName
                    ).absolutePath,
                    mimeType = probe.mimeType,
                    totalBytes = probe.totalBytes,
                    supportsRange = probe.supportsRange,
                    etag = probe.etag,
                    partCount = plan.partCount,
                    status = READY
                )
            )

            // 3. DOWNLOAD --------------------------------------------------
            moveTo(id, DOWNLOADING)
            val bucket = if (s.speedLimitEnabled && s.speedLimitKBps > 0) {
                TokenBucket(
                    capacityBytes = s.speedLimitKBps * 1024L,
                    refillRateBytesPerSec = s.speedLimitKBps * 1024L
                )
            } else null

            executeParts(id, plan, probe, bucket)

            // Refresh the aggregate so the card shows 100% before we flip to
            // VERIFYING.
            repository.setProgress(id, repository.writtenTotal(id))

            // 4. VERIFY ----------------------------------------------------
            moveTo(id, VERIFYING)
            verify(repository.getDownload(id)!!)

            // 5. COMPLETE --------------------------------------------------
            repository.updateDownload(
                repository.getDownload(id)!!.copy(
                    status = COMPLETED,
                    updatedAt = System.currentTimeMillis()
                )
            )
            _events.tryEmit(DownloadEvent.Completed(id))
        } catch (c: kotlinx.coroutines.CancellationException) {
            // Pause/cancel: the DB flag was already set by the caller, but a
            // part may have flipped the row to RUNNING — honour the reason.
            val terminal = when ((c as? DownloadCancellation)?.reason) {
                PauseReason.USER -> repository.getDownload(id)?.status ?: CANCELED
                PauseReason.SYSTEM -> CANCELED
                null -> repository.getDownload(id)?.status ?: CANCELED
            }
            if (terminal != PAUSED && terminal != CANCELED) {
                withContext(NonCancellable) { markStatus(id, CANCELED) }
            }
            throw c
        } catch (t: Throwable) {
            handleFailure(id, t)
        } finally {
            runtimesMutex.withLock { runtimes.remove(id) }
            queue.markStopped(id)
            // The freed slot lets the next queued download start.
            runCatching { startNextQueued() }
        }
    }

    private suspend fun probeResource(
        entity: DownloadEntity,
        s: AuroraSettings
    ): ProbeResult? = withContext(Dispatchers.IO) {
        val client = OkHttpFactory.perDownload(
            base = httpClient,
            cookieHeader = entity.cookieHeader,
            userAgent = entity.userAgent ?: s.customUserAgent,
            referer = entity.referer
        )

        // Ranged GET of the first byte. This is the definitive range test:
        // servers that advertise Accept-Ranges but ignore it return 200 here.
        val request = Request.Builder()
            .url(entity.url)
            .apply {
                if (entity.etag != null) header("If-Range", entity.etag)
                header("Range", "bytes=0-0")
            }
            .build()

        val response = client.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) {
                return@withContext if (it.code == 416) {
                    ProbeResult(
                        finalUrl = it.request.url.toString(),
                        supportsRange = false,
                        totalBytes = it.body?.contentLength() ?: -1L,
                        etag = null,
                        lastModified = null,
                        mimeType = null,
                        fileName = guessFileName(it.request.url.toString())
                    )
                } else null
            }
            ProbeInspector.fromResponse(it, entity.url)
        }
    }

    /**
     * Runs the part fleet inside a [coroutineScope]: the launched parts are
     * *children* of this call, so cancelling the download job cancels every
     * part and, via [runPart]'s hook, every in-flight HTTP call.
     */
    private suspend fun executeParts(
        id: Long,
        plan: Plan,
        probe: ProbeResult,
        bucket: TokenBucket?
    ) = coroutineScope {
        val parts = repository.getParts(id)
        val targetFile = File(repository.getDownload(id)!!.savePath)
        targetFile.parentFile?.mkdirs()

        // One tracker per download: samples arrive from every part, but the
        // rate is about the whole file.
        val speedTracker = SpeedTracker()

        // Pre-allocate the whole file so the filesystem reserves space once
        // and parts can seek without racing each other.
        if (probe.totalBytes > 0) {
            RandomAccessFile(targetFile, "rw").use { it.setLength(probe.totalBytes) }
        }

        val jobs = parts.map { part ->
            launch {   // child of this coroutineScope — cancellation propagates
                runPart(
                    downloadId = id,
                    part = part,
                    probe = probe,
                    file = targetFile,
                    bucket = bucket,
                    tracker = speedTracker
                )
            }
        }
        jobs.forEach { it.join() }

        // Any part that did not finish means the download as a whole failed.
        // Re-throw as a plain error so handleFailure handles retries, not the
        // caller's cancellation path.
        val failed = repository.getParts(id).count { it.status != PartStatus.DONE }
        if (failed > 0) error("$failed part(s) failed")
    }

    private suspend fun runPart(
        downloadId: Long,
        part: PartEntity,
        probe: ProbeResult,
        file: File,
        bucket: TokenBucket?,
        tracker: SpeedTracker
    ) {
        val entity = repository.getDownload(downloadId) ?: return
        val client = OkHttpFactory.perDownload(
            base = httpClient,
            cookieHeader = entity.cookieHeader,
            userAgent = entity.userAgent,
            referer = entity.referer
        )

        val resumeFrom = part.start + part.written
        val endByte = if (part.end < 0) null else part.end
        if (endByte != null && resumeFrom > endByte) {
            repository.markPartDone(part.id, part.written)
            return
        }

        val rangeHeader = if (endByte == null) {
            "bytes=$resumeFrom-"
        } else {
            "bytes=$resumeFrom-$endByte"
        }

        val request = Request.Builder()
            .url(probe.finalUrl)
            .apply {
                if (probe.etag != null) header("If-Range", probe.etag)
                header("Range", rangeHeader)
            }
            .build()

        repository.markPartRunning(part.id)

        // The critical pause hook: cancelling the coroutine cancels the HTTP
        // call, which unblocks the read loop immediately instead of letting a
        // paused download silently keep fetching bytes.
        val call = client.newCall(request)
        val job = kotlin.coroutines.coroutineContext[Job]
        job?.invokeOnCompletion { cause -> if (cause != null) runCatching { call.cancel() } }

        try {
            val response = call.execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    // HTTP-code-aware failure so retries can be selective.
                    throw HttpFailure(resp.code, "Part HTTP ${resp.code}")
                }
                val body = resp.body ?: error("Empty body for part ${part.partIndex}")

                // A server that ignores Range returns 200 with the *whole*
                // body. Honouring the offset would corrupt the file, so this
                // part is invalid — re-probe instead of appending blindly.
                if (resp.code == 200 && part.start > 0) {
                    throw HttpFailure(
                        resp.code,
                        "Server ignored Range request (200 instead of 206)"
                    )
                }

                val raf = RandomAccessFile(file, "rw")
                raf.use {
                    it.seek(resumeFrom)
                    val source = body.source()
                    val sinkBuffer = okio.Buffer()
                    var totalThisCall = 0L
                    val chunk = 64 * 1024
                    var lastEmit = 0L
                    while (true) {
                        val read = source.read(sinkBuffer, chunk.toLong())
                        if (read == -1L) break
                        val bytes = sinkBuffer.readByteArray(read)
                        it.write(bytes)
                        totalThisCall += read
                        val newWritten = part.written + totalThisCall
                        repository.setPartProgress(part.id, newWritten)
                        // Throttle the aggregate + notification feed to ~4 Hz.
                        if (newWritten - lastEmit > 256 * 1024) {
                            lastEmit = newWritten
                            val totalNow = repository.writtenTotal(downloadId)
                            repository.setProgress(downloadId, totalNow)
                            val rate = tracker.sample(bytes = totalNow)
                            _progress.tryEmit(
                                ProgressSnapshot(
                                    downloadId = downloadId,
                                    downloadedBytes = totalNow,
                                    totalBytes = entity.totalBytes,
                                    speedBps = rate,
                                    etaSeconds = tracker.etaSeconds(
                                        entity.totalBytes
                                    )
                                )
                            )
                        }
                        bucket?.acquire(read)
                    }
                    repository.markPartDone(part.id, part.written + totalThisCall)
                }
            }
        } catch (c: kotlinx.coroutines.CancellationException) {
            // The call was cancelled by pause: persist what we have and let
            // the caller record the terminal state.
            repository.setPartProgress(part.id, part.written)
            throw c
        }
    }

    private suspend fun verify(entity: DownloadEntity) {
        val file = File(entity.savePath)
        if (!file.exists()) error("Output file missing after download")

        // Size check is the cheap, always-available verification.
        if (entity.totalBytes > 0 && file.length() != entity.totalBytes) {
            error("Size mismatch: expected ${entity.totalBytes}, got ${file.length()}")
        }

        // Move the finished file out of app-private storage into the public
        // Downloads/RDM collection so the user can actually see and open it.
        val publishedUri = withContext(Dispatchers.IO) {
            try { publish(file, entity.fileName, entity.mimeType) }
            // Publishing is best-effort; never fail a download that
            // already succeeded on disk.
            catch (t: Throwable) { null }
        }
        if (publishedUri != null) {
            repository.updateDownload(
                entity.copy(
                    contentUri = publishedUri.toString(),
                    published = true,
                    updatedAt = System.currentTimeMillis()
                )
            )
            file.delete()
        }
    }

    // ── Error handling / retries ──────────────────────────────────────────

    private suspend fun handleFailure(id: Long, t: Throwable) {
        val entity = repository.getDownload(id) ?: return
        if (entity.status == CANCELED || entity.status == PAUSED) return

        val httpCode = (t as? HttpFailure)?.code
        // Not every failure deserves a retry (TEST: 404 must not loop).
        if (httpCode != null && !shouldRetry(httpCode)) {
            fail(id, t.message ?: "HTTP $httpCode")
            return
        }

        if (entity.retryCount < entity.maxRetries) {
            repository.updateDownload(
                entity.copy(
                    status = RETRYING,
                    retryCount = entity.retryCount + 1,
                    lastError = t.message
                )
            )
            kotlinx.coroutines.delay(retryBackoff(entity.retryCount))
            // The user may have cancelled during the backoff — don't resurrect.
            val now = repository.getDownload(id) ?: return
            if (now.status == CANCELED || now.status == PAUSED) return
            start(id)
        } else {
            fail(id, t.message ?: "Unknown error")
        }
    }

    private fun shouldRetry(code: Int): Boolean = when (code) {
        408, 425, 429, 500, 502, 503, 504 -> true
        // Auth and missing resources are not transient.
        400, 401, 403, 404, 405, 410 -> false
        else -> true
    }

    private fun retryBackoff(attempt: Int): Long {
        // Exponential backoff with jitter to avoid a retry thundering herd
        // against the same host.
        val base = 2_000L * (1L shl attempt.coerceAtMost(5))
        val jitter = (base * 0.2 * Math.random()).toLong()
        return (base + jitter).coerceAtMost(60_000L)
    }

    private suspend fun fail(id: Long, message: String) {
        val entity = repository.getDownload(id) ?: return
        repository.updateDownload(
            entity.copy(status = ERROR, lastError = message)
        )
        _events.tryEmit(DownloadEvent.Failed(id, message))
    }

    private suspend fun moveTo(id: Long, status: DownloadStatus) {
        repository.setStatus(id, status)
    }

    /** Sets the status, skipping invalid transitions (state machine guard). */
    private suspend fun markStatus(id: Long, target: DownloadStatus) {
        val current = repository.getDownload(id)?.status ?: return
        if (!isValidTransition(current, target)) return
        repository.setStatus(id, target)
    }

    private fun isValidTransition(from: DownloadStatus, to: DownloadStatus): Boolean {
        if (from == to) return false
        return when (to) {
            // A finished download is only re-run via restart(), which resets
            // the row first, so these direct jumps are invalid.
            QUEUED -> from != COMPLETED && from != CANCELED
            DOWNLOADING -> from == PROBING || from == READY || from == RETRYING
            // A finished download is terminal. Pausing or cancelling something
            // that already completed drags it backwards out of COMPLETED.
            PAUSED, CANCELED -> from != COMPLETED
            else -> true
        }
    }

    private fun guessFileName(url: String): String =
        url.substringAfterLast('/').substringBefore('?').ifBlank { "download.bin" }

    private fun resolveSaveDir(requested: File?): File {
        // Downloads run into app-private storage: the engine needs
        // RandomAccessFile seek-per-part, which content:// URIs cannot offer.
        // On completion MediaStorePublisher moves the file into the public
        // Downloads/RDM folder.
        val base = requested
            ?: File(app.getExternalFilesDir(null) ?: app.filesDir, "staging")
        if (!base.exists()) base.mkdirs()
        return base
    }
}

/** One running download: the parent job whose children are the part jobs. */
private class DownloadRuntime(val job: Job)

enum class PauseReason { USER, SYSTEM }

private class DownloadCancellation(val reason: PauseReason) : kotlinx.coroutines.CancellationException("download paused/cancelled") {
    // CancellationException fills the stack trace on every serialization;
    // this one is expected control flow, not an error to report.
    override fun fillInStackTrace(): Throwable = this
}

private fun PauseReason.toCancellationException() = DownloadCancellation(this)

/** A non-fatal HTTP failure carrying the status code for retry decisions. */
class HttpFailure(val code: Int, message: String) : Exception(message)

/** Engine-side progress sample, throttled to keep the DB and UI calm. */
data class ProgressSnapshot(
    val downloadId: Long,
    val downloadedBytes: Long,
    val totalBytes: Long,
    /** Smoothed instantaneous rate, bytes/sec. */
    val speedBps: Long = 0L,
    /** Seconds remaining, or null when the size or rate is unknown. */
    val etaSeconds: Long? = null
)

sealed interface DownloadEvent {
    data class Completed(val id: Long) : DownloadEvent
    data class Failed(val id: Long, val message: String) : DownloadEvent
    data class Cancelled(val id: Long) : DownloadEvent
    data class WaitingForNetwork(val id: Long) : DownloadEvent
}

data class NewDownloadRequest(
    val url: String,
    val fileName: String? = null,
    val userAgent: String? = null,
    val referer: String? = null,
    val cookieHeader: String? = null,
    val priority: Int = 0,
    val targetDirectory: File? = null
)

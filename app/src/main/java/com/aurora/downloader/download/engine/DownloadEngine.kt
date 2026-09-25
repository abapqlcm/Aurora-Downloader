package com.aurora.downloader.download.engine

import android.content.Context
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile

/**
 * The core. Owns the lifecycle of every download and is the single writer to
 * both the database state machine and the output file.
 *
 * Concurrency model: a fixed pool of part coroutines per download (fixed, not
 * adaptive), and a fixed number of simultaneous downloads. Both are user
 * settings, not runtime guesses.
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
    private val runningDownloads = mutableMapOf<Long, Job>()
    private val runningMutex = Mutex()

    /** One-time events for the UI to display (completion, failure). */
    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    private suspend fun currentSettings(): AuroraSettings = settings.flow.first()

    // ── Public API ────────────────────────────────────────────────────────

    suspend fun enqueue(request: NewDownloadRequest): Long {
        val targetDir = resolveSaveDir(request)
        val entity = DownloadEntity(
            url = request.url,
            fileName = request.fileName ?: guessFileName(request.url),
            savePath = File(targetDir, request.fileName ?: guessFileName(request.url)).absolutePath,
            userAgent = request.userAgent,
            referer = request.referer,
            cookieHeader = request.cookieHeader,
            priority = request.priority,
            status = QUEUED
        )
        val id = repository.insertDownload(entity)
        schedule(id)
        return id
    }

    suspend fun pause(id: Long) {
        repository.setStatus(id, PAUSED)
        cancelRunning(id)
    }

    suspend fun resume(id: Long) {
        repository.setStatus(id, QUEUED)
        schedule(id)
    }

    suspend fun cancel(id: Long, deleteFile: Boolean = false) {
        cancelRunning(id)
        repository.setStatus(id, CANCELED)
        if (deleteFile) {
            repository.getDownload(id)?.let { File(it.savePath).delete() }
        }
    }

    suspend fun restart(id: Long) {
        cancelRunning(id)
        repository.getDownload(id)?.let { dl ->
            repository.resetParts(id)
            repository.updateDownload(
                dl.copy(
                    downloadedBytes = 0,
                    totalBytes = -1,
                    status = QUEUED,
                    retryCount = 0,
                    lastError = null
                )
            )
            schedule(id)
        }
    }

    // ── Scheduling ────────────────────────────────────────────────────────

    private suspend fun schedule(id: Long) {
        runningMutex.withLock {
            if (runningDownloads.containsKey(id)) return
            val job = scope.launch { runDownload(id) }
            runningDownloads[id] = job
        }
    }

    private suspend fun cancelRunning(id: Long) {
        runningMutex.withLock {
            runningDownloads.remove(id)?.cancel()
        }
    }

    // ── The pipeline ──────────────────────────────────────────────────────

    private suspend fun runDownload(id: Long) {
        try {
            val initial = repository.getDownload(id) ?: return

            // Never re-download something already finished and published.
            if (initial.status == DownloadStatus.COMPLETED) return

            val s = currentSettings()

            // 1. PROBING ---------------------------------------------------
            moveTo(id, PROBING)
            val probe = probeResource(initial, s) ?: run {
                fail(id, "Probe failed (no response body or unreachable)")
                return
            }

            // 2. PLAN ------------------------------------------------------
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
                    fileName = probe.fileName,
                    savePath = File(resolveSaveDir(null), probe.fileName).absolutePath,
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

            // 4. VERIFY ----------------------------------------------------
            moveTo(id, VERIFYING)
            val entity = repository.getDownload(id)!!
            verify(entity)

            // 5. COMPLETE --------------------------------------------------
            repository.updateDownload(
                entity.copy(status = COMPLETED, updatedAt = System.currentTimeMillis())
            )
            _events.tryEmit(DownloadEvent.Completed(id))
        } catch (t: Throwable) {
            handleFailure(id, t)
        } finally {
            runningMutex.withLock { runningDownloads.remove(id) }
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
                    // Range not satisfiable: file already complete or server quirk.
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

    private suspend fun executeParts(
        id: Long,
        plan: Plan,
        probe: ProbeResult,
        bucket: TokenBucket?
    ) {
        val parts = repository.getParts(id)
        val targetFile = File(repository.getDownload(id)!!.savePath)
        targetFile.parentFile?.mkdirs()

        // Pre-allocate the whole file so the filesystem reserves space once
        // and parts can seek without racing each other. java.io.File has no
        // setLength, so open+close a RandomAccessFile to do the allocation.
        if (probe.totalBytes > 0) {
            RandomAccessFile(targetFile, "rw").use { it.setLength(probe.totalBytes) }
        }

        val jobs = parts.map { part ->
            scope.launch {
                runPart(
                    downloadId = id,
                    part = part,
                    probe = probe,
                    file = targetFile,
                    bucket = bucket
                )
            }
        }
        jobs.forEach { it.join() }

        // Refresh the aggregate progress so the UI row and the DB agree —
        // part rows are updated continuously, but the download row was only
        // written during planning.
        repository.setProgress(id, repository.writtenTotal(id))

        // Any part that did not finish means the download as a whole failed.
        val failed = repository.getParts(id).count { it.status != PartStatus.DONE }
        if (failed > 0) error("$failed part(s) failed")
    }

    private suspend fun runPart(
        downloadId: Long,
        part: PartEntity,
        probe: ProbeResult,
        file: File,
        bucket: TokenBucket?
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

        val response = client.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) error("Part HTTP ${resp.code}")
            val body = resp.body ?: error("Empty body for part ${part.partIndex}")

            // RandomAccessFile lets each part seek to its own offset in the
            // shared pre-allocated file; writes never overlap.
            val raf = RandomAccessFile(file, "rw")
            raf.use {
                it.seek(resumeFrom)
                val source = body.source()
                val sinkBuffer = okio.Buffer()
                var totalThisCall = 0L
                val chunk = 64 * 1024
                while (true) {
                    val read = source.read(sinkBuffer, chunk.toLong())
                    if (read == -1L) break
                    val bytes = sinkBuffer.readByteArray(read)
                    it.write(bytes)
                    totalThisCall += read
                    val newWritten = part.written + totalThisCall
                    repository.setPartProgress(part.id, newWritten)
                    bucket?.acquire(read)
                }
                repository.markPartDone(part.id, part.written + totalThisCall)
            }
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

    private fun indexInMediaStore(entity: DownloadEntity) {
        // Legacy path — replaced by MediaStorePublisher, kept as a fallback
        // for devices where the publisher returns null.
        try {
            @Suppress("DEPRECATION")
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, entity.fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, entity.mimeType ?: "*/*")
                put(android.provider.MediaStore.MediaColumns.DATA, entity.savePath)
            }
            app.contentResolver.insert(
                android.provider.MediaStore.Files.getContentUri("external"),
                values
            )
        } catch (_: Exception) {
            // Indexing is best-effort; the download itself succeeded.
        }
    }

    // ── Error handling / retries ──────────────────────────────────────────

    private suspend fun handleFailure(id: Long, t: Throwable) {
        val entity = repository.getDownload(id) ?: return
        if (entity.status == CANCELED || entity.status == PAUSED) return

        if (entity.retryCount < entity.maxRetries) {
            repository.updateDownload(
                entity.copy(
                    status = RETRYING,
                    retryCount = entity.retryCount + 1,
                    lastError = t.message
                )
            )
            kotlinx.coroutines.delay(retryBackoff(entity.retryCount))
            schedule(id)
        } else {
            fail(id, t.message ?: "Unknown error")
        }
    }

    private fun retryBackoff(attempt: Int): Long {
        val base = 2_000L * (1L shl attempt.coerceAtMost(5))
        return base.coerceAtMost(60_000L)
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

    private fun guessFileName(url: String): String =
        url.substringAfterLast('/').substringBefore('?').ifBlank { "download.bin" }

    private fun resolveSaveDir(request: NewDownloadRequest?): File {
        // Downloads run into app-private storage: the engine needs
        // RandomAccessFile seek-per-part, which content:// URIs cannot offer.
        // On completion MediaStorePublisher moves the file into the public
        // Downloads/RDM folder.
        val base = request?.targetDirectory
            ?: File(app.getExternalFilesDir(null) ?: app.filesDir, "staging")
        if (!base.exists()) base.mkdirs()
        return base
    }
}

sealed interface DownloadEvent {
    data class Completed(val id: Long) : DownloadEvent
    data class Failed(val id: Long, val message: String) : DownloadEvent
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

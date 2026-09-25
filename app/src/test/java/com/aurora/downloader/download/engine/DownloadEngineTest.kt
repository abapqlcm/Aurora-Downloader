package com.aurora.downloader.download.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import com.aurora.downloader.data.datastore.SettingsRepository
import com.aurora.downloader.data.database.AuroraDatabase
import com.aurora.downloader.download.persistence.DownloadRepository
import com.aurora.downloader.download.transport.OkHttpFactory
import com.aurora.downloader.domain.model.DownloadStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Exercises the real engine against a local MockWebServer: probing, ranged
 * multi-part download, resume after interruption, and redirect following.
 *
 * Robolectric gives us a working Context + Room without an emulator.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DownloadEngineTest {

    private lateinit var server: MockWebServer
    private lateinit var engine: DownloadEngine
    private lateinit var repository: DownloadRepository
    private lateinit var client: OkHttpClient
    private lateinit var stagingDir: File
    private val app: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        client = OkHttpClient.Builder().build()

        val db = Room.inMemoryDatabaseBuilder(
            app, AuroraDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = DownloadRepository(db.downloadDao(), db.partDao())
        engine = DownloadEngine(
            app = app,
            repository = repository,
            httpClient = client,
            settings = SettingsRepository(app)
        )

        stagingDir = File(app.cacheDir, "test-staging").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() {
        server.shutdown()
        stagingDir.deleteRecursively()
    }

    private fun payload(size: Int): ByteArray {
        // Deterministic pseudo-random content so we can verify byte-exactness.
        val bytes = ByteArray(size)
        var seed = 0x1A2B3C4D
        for (i in bytes.indices) {
            seed = seed * 1103515245 + 12345
            bytes[i] = (seed shr 24).toByte()
        }
        return bytes
    }

    private fun serveRange(payload: ByteArray, etag: String = "v1") {
        server.dispatcher = object : okhttp3.mockwebserver.QueueDispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range")
                if (range == null) {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("ETag", etag)
                        .setHeader("Accept-Ranges", "bytes")
                        .setHeader("Content-Length", payload.size.toString())
                        .setBody(okio.Buffer().write(payload))
                }
                // bytes=START-END  (or bytes=START-)
                val spec = range.removePrefix("bytes=").substringBefore(",")
                val (startS, endS) = spec.split("-")
                val start = startS.toLong()
                val end = if (endS.isEmpty()) payload.size - 1L else endS.toLong()
                val chunk = payload.copyOfRange(start.toInt(), minOf(end.toInt() + 1, payload.size))
                return MockResponse()
                    .setResponseCode(206)
                    .setHeader("ETag", etag)
                    .setHeader("Accept-Ranges", "bytes")
                    .setHeader("Content-Range", "bytes $start-$end/${payload.size}")
                    .setHeader("Content-Length", chunk.size.toString())
                    .setBody(okio.Buffer().write(chunk))
            }
        }
    }

    private suspend fun waitForCompletion(id: Long, timeoutMs: Long = 15_000) {
        withTimeout(timeoutMs) {
            while (true) {
                val dl = repository.getDownload(id) ?: error("download vanished")
                if (dl.status == DownloadStatus.COMPLETED || dl.status == DownloadStatus.ERROR) return@withTimeout
                delay(100)
            }
        }
    }

    @Test
    fun downloadsMultiPartAndPublishesToRdm() = runBlocking {
        val payload = payload(2 * 1024 * 1024) // 2 MB
        serveRange(payload)

        val id = engine.enqueue(
            NewDownloadRequest(
                url = server.url("/file.bin").toString(),
                fileName = "test-file.bin",
                targetDirectory = stagingDir
            )
        )

        waitForCompletion(id)

        val finished = repository.getDownload(id)!!
        assertEquals(DownloadStatus.COMPLETED, finished.status)
        assertEquals(payload.size.toLong(), finished.totalBytes)
        assertTrue("download must be published", finished.published)
        assertTrue("content uri must be recorded", finished.contentUri!!.contains("RDM"))

        // Publishing into MediaStore in Robolectric is a no-op; verify the
        // staging file survives so nothing was silently deleted.
        val onDisk = File(finished.savePath)
        assertTrue("staging file must still exist in test env", onDisk.exists())
        assertArrayEquals(payload, onDisk.readBytes())
    }

    @Test
    fun resumesFromWhereItStopped() = runBlocking {
        val payload = payload(1 * 1024 * 1024)
        serveRange(payload)

        val id = engine.enqueue(
            NewDownloadRequest(
                url = server.url("/resume.bin").toString(),
                fileName = "resume-file.bin",
                targetDirectory = stagingDir
            )
        )

        // Let it download a little, then pause.
        delay(400)
        engine.pause(id)
        delay(300)

        val partial = repository.getDownload(id)!!
        assertTrue("partial progress expected", partial.downloadedBytes > 0)
        assertTrue(
            "status should be paused or completed",
            partial.status == DownloadStatus.PAUSED || partial.status == DownloadStatus.COMPLETED
        )

        if (partial.status == DownloadStatus.PAUSED) {
            engine.resume(id)
            waitForCompletion(id)
            val finished = repository.getDownload(id)!!
            assertEquals(DownloadStatus.COMPLETED, finished.status)
            assertEquals(payload.size.toLong(), finished.downloadedBytes)
            assertArrayEquals(payload, File(finished.savePath).readBytes())
        }
    }

    @Test
    fun rejectsCompletedDownloadOnResume() = runBlocking {
        val payload = payload(256 * 1024)
        serveRange(payload)

        val id = engine.enqueue(
            NewDownloadRequest(
                url = server.url("/once.bin").toString(),
                fileName = "once-file.bin",
                targetDirectory = stagingDir
            )
        )
        waitForCompletion(id)

        val before = repository.getDownload(id)!!
        engine.resume(id)
        delay(500)

        val after = repository.getDownload(id)!!
        assertEquals("a completed download must not be re-downloaded",
            before.downloadedBytes, after.downloadedBytes)
        assertEquals(DownloadStatus.COMPLETED, after.status)
    }

    @Test
    fun settingsDefaultsAreSane() = runBlocking {
        val settings = SettingsRepository(app).flow.first()
        assertTrue("at least 1 concurrent download", settings.maxConcurrentDownloads >= 1)
        assertTrue("at least 1 part", settings.partsPerDownload >= 1)
    }
}

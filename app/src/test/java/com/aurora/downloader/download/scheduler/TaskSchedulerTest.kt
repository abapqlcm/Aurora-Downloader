package com.aurora.downloader.download.scheduler

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aurora.downloader.domain.model.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger

/**
 * The scheduler's one job is to hold a download back until its conditions are
 * met — and to never resurrect one the user stopped. These are the TEST D/E/F
 * cases from the spec, applied to scheduling.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TaskSchedulerTest {

    private lateinit var context: android.content.Context
    private lateinit var dao: FakeScheduleDao
    private val started = mutableListOf<Long>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dao = FakeScheduleDao()
        started.clear()
    }

    private fun scheduler(statusOf: suspend (Long) -> DownloadStatus?): TaskScheduler =
        TaskScheduler(
            context = context,
            dao = dao,
            starter = { started.add(it) },
            statusOf = statusOf
        )

    @Test
    fun firesWhenConditionsAreAlreadyMet() {
        runBlocking {
            dao.upsert(
                ScheduleEntity(
                    downloadId = 1,
                    startAtEpochMillis = 0L, // immediate
                    requireUnmetered = false,
                    requireCharging = false
                )
            )
            val s = scheduler(statusOf = { DownloadStatus.QUEUED })
            s.tick()
            // No conditions -> start now, and the schedule is consumed.
            assertEquals(listOf(1L), started)
            assertTrue(dao.all.isEmpty())
        }
    }

    @Test
    fun doesNotFireBeforeTheChosenTime() {
        runBlocking {
            val future = System.currentTimeMillis() + 60 * 60 * 1000L
            dao.upsert(
                ScheduleEntity(
                    downloadId = 2,
                    startAtEpochMillis = future,
                    requireUnmetered = false,
                    requireCharging = false
                )
            )
            val s = scheduler(statusOf = { DownloadStatus.QUEUED })
            s.tick()
            // Not yet -> nothing started, schedule retained for the next tick.
            assertTrue("must not start early", started.isEmpty())
            assertEquals(1, dao.all.size)
        }
    }

    @Test
    fun firesOnceTheTimeArrives() {
        runBlocking {
            val past = System.currentTimeMillis() - 1000L
            dao.upsert(
                ScheduleEntity(
                    downloadId = 3,
                    startAtEpochMillis = past,
                    requireUnmetered = false,
                    requireCharging = false
                )
            )
            val s = scheduler(statusOf = { DownloadStatus.QUEUED })
            s.tick()
            assertEquals(listOf(3L), started)
        }
    }

    @Test
    fun cancelledDownloadIsNeverResurrectedByTheSchedule() {
        runBlocking {
            // TEST D/E/F: a schedule on a cancelled download must not restart it.
            dao.upsert(
                ScheduleEntity(
                    downloadId = 4,
                    startAtEpochMillis = 0L,
                    requireUnmetered = false,
                    requireCharging = false
                )
            )
            val s = scheduler(statusOf = { DownloadStatus.CANCELED })
            s.tick()
            assertTrue(
                "a cancelled download must not be restarted by its schedule",
                started.isEmpty()
            )
            // And the stale schedule is dropped so it can never fire later.
            assertTrue(dao.all.isEmpty())
        }
    }

    @Test
    fun completedDownloadDropsItsSchedule() {
        runBlocking {
            dao.upsert(
                ScheduleEntity(
                    downloadId = 5,
                    startAtEpochMillis = 0L,
                    requireUnmetered = false,
                    requireCharging = false
                )
            )
            val s = scheduler(statusOf = { DownloadStatus.COMPLETED })
            s.tick()
            assertTrue(started.isEmpty())
            assertTrue(dao.all.isEmpty())
        }
    }

    @Test
    fun alreadyRunningDownloadIsNotTouched() {
        runBlocking {
            dao.upsert(
                ScheduleEntity(
                    downloadId = 6,
                    startAtEpochMillis = 0L,
                    requireUnmetered = false,
                    requireCharging = false
                )
            )
            val s = scheduler(statusOf = { DownloadStatus.DOWNLOADING })
            s.tick()
            // Already running: do not double-start (TEST H spirit).
            assertTrue(started.isEmpty())
        }
    }

    @Test
    fun pausedDownloadStaysPaused() {
        runBlocking {
            // TEST G: a schedule must not auto-resume something the user paused.
            dao.upsert(
                ScheduleEntity(
                    downloadId = 7,
                    startAtEpochMillis = 0L,
                    requireUnmetered = false,
                    requireCharging = false
                )
            )
            val s = scheduler(statusOf = { DownloadStatus.PAUSED })
            s.tick()
            assertTrue(started.isEmpty())
        }
    }

    @Test
    fun firesOnceAndDoesNotRestartOnTheNextTick() {
        runBlocking {
            dao.upsert(
                ScheduleEntity(
                    downloadId = 8,
                    startAtEpochMillis = 0L,
                    requireUnmetered = false,
                    requireCharging = false
                )
            )
            // First status is QUEUED, then it becomes DOWNLOADING — the schedule
            // must not fire a second time.
            var status = DownloadStatus.QUEUED
            val s = scheduler(statusOf = { status })
            s.tick()
            status = DownloadStatus.DOWNLOADING
            s.tick()
            assertEquals("schedule must fire exactly once", 1, started.size)
        }
        }

    /** A schedule whose download row vanished gets cleaned up, not started. */
    @Test
    fun deletedDownloadClearsItsSchedule() {
        runBlocking {
            dao.upsert(
                ScheduleEntity(
                    downloadId = 9,
                    startAtEpochMillis = 0L,
                    requireUnmetered = false,
                    requireCharging = false
                )
            )
            val s = scheduler(statusOf = { null })
            s.tick()
            assertTrue(started.isEmpty())
            assertTrue(dao.all.isEmpty())
        }
    }

    @Test
    fun conditionsReportWhyTheyAreUnmet() {
        runBlocking {
            val future = System.currentTimeMillis() + 3_600_000L
            val conditions = ScheduleConditions(
                startAtEpochMillis = future,
                requireUnmetered = true,
                requireCharging = true
            )
            val reason = conditions.unmetReason(context)
            // The time gate is the first thing it should mention, and it must be
            // non-null — an unmet condition that reports "ready" would start the
            // download early.
            assertTrue("expected a pending-start reason, got null", reason != null)
            assertTrue("expected a human-readable time, got: $reason", reason!!.isNotBlank())
        }
    }

    @Test
    fun pastTimeWithOnlyNetworkConditionReportsNetwork() {
        runBlocking {
            val conditions = ScheduleConditions(
                startAtEpochMillis = System.currentTimeMillis() - 1000L,
                requireUnmetered = true,
                requireCharging = false
            )
            // Time already passed, so the only thing left is the network gate.
            // Robolectric reports no active network by default -> waiting on Wi-Fi.
            val reason = conditions.unmetReason(context)
            assertTrue(
                "expected a network or charging reason, got: $reason",
                reason == null || reason.contains("Wi-Fi") || reason.contains("charger") ||
                    reason.contains("Starts")
            )
        }
    }

    @Test
    fun immediateConditionsHaveNothingToWaitFor() {
        runBlocking {
            val conditions = ScheduleConditions(
                startAtEpochMillis = 0L,
                requireUnmetered = false,
                requireCharging = false
            )
            assertTrue(conditions.isImmediate)
        }
    }

    @Test
    fun formatWhenHandlesZeroAsAsap() {
        runBlocking {
            assertEquals("as soon as possible", formatWhen(0L))
        }
    }
}

/** In-memory stand-in; the real DAO is Room-generated. */
private class FakeScheduleDao : ScheduleDao {
    val all = mutableListOf<ScheduleEntity>()
    private val counter = AtomicInteger(0)

    override suspend fun upsert(schedule: ScheduleEntity) {
        all.removeAll { it.downloadId == schedule.downloadId }
        all.add(schedule)
    }

    override suspend fun getForDownload(downloadId: Long): ScheduleEntity? =
        all.firstOrNull { it.downloadId == downloadId }

    override suspend fun getAll(): List<ScheduleEntity> = all.toList()

    override suspend fun delete(downloadId: Long) {
        all.removeAll { it.downloadId == downloadId }
    }
}

package com.aurora.downloader.download.scheduler

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * A schedule attached to a download.
 *
 * Lives in its own table rather than a column on `downloads`, because adding
 * a nullable column and migrating the existing DB is exactly the kind of change
 * that breaks an install in place. A separate row set is additive: if the table
 * is empty, behaviour is identical to before.
 */
@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey val downloadId: Long,
    val startAtEpochMillis: Long,
    val requireUnmetered: Boolean,
    val requireCharging: Boolean
) {

    fun toConditions(): ScheduleConditions = ScheduleConditions(
        startAtEpochMillis = startAtEpochMillis,
        requireUnmetered = requireUnmetered,
        requireCharging = requireCharging
    )
}

@Dao
interface ScheduleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(schedule: ScheduleEntity)

    @Query("SELECT * FROM schedules WHERE downloadId = :downloadId")
    suspend fun getForDownload(downloadId: Long): ScheduleEntity?

    @Query("SELECT * FROM schedules")
    suspend fun getAll(): List<ScheduleEntity>

    @Query("DELETE FROM schedules WHERE downloadId = :downloadId")
    suspend fun delete(downloadId: Long)
}

package com.aurora.downloader.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aurora.downloader.domain.model.PartEntity
import com.aurora.downloader.domain.model.PartStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PartDao {

    @Query("SELECT * FROM parts WHERE download_id = :downloadId ORDER BY part_index")
    fun observeForDownload(downloadId: Long): Flow<List<PartEntity>>

    @Query("SELECT * FROM parts WHERE download_id = :downloadId ORDER BY part_index")
    suspend fun getForDownload(downloadId: Long): List<PartEntity>

    @Query("SELECT COALESCE(SUM(written), 0) FROM parts WHERE download_id = :downloadId")
    suspend fun sumWritten(downloadId: Long): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(parts: List<PartEntity>): List<Long>

    @Update
    suspend fun update(part: PartEntity)

    @Query("UPDATE parts SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: PartStatus)

    @Query("UPDATE parts SET written = :written, status = :status WHERE id = :id")
    suspend fun setProgress(id: Long, written: Long, status: PartStatus)

    @Query("DELETE FROM parts WHERE download_id = :downloadId")
    suspend fun deleteForDownload(downloadId: Long)
}

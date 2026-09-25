package com.aurora.downloader.download.persistence

import com.aurora.downloader.data.database.DownloadDao
import com.aurora.downloader.data.database.PartDao
import com.aurora.downloader.domain.model.DownloadEntity
import com.aurora.downloader.domain.model.DownloadStatus
import com.aurora.downloader.domain.model.PartEntity
import com.aurora.downloader.domain.model.PartStatus
import kotlinx.coroutines.flow.Flow

/**
 * The only path the engine and UI use to touch download state. Keeping it in
 * one place means a crash mid-part leaves the DB in a state that is always
 * resumable, never corrupt.
 */
class DownloadRepository(
    private val downloads: DownloadDao,
    private val parts: PartDao
) {
    fun observeDownloads(): Flow<List<DownloadEntity>> = downloads.observeAll()
    fun observeDownload(id: Long): Flow<DownloadEntity?> = downloads.observeById(id)
    fun observeParts(id: Long): Flow<List<PartEntity>> = parts.observeForDownload(id)

    suspend fun getDownload(id: Long): DownloadEntity? = downloads.getById(id)
    suspend fun getParts(id: Long): List<PartEntity> = parts.getForDownload(id)
    suspend fun writtenTotal(id: Long): Long = parts.sumWritten(id)

    suspend fun insertDownload(entity: DownloadEntity): Long = downloads.insert(entity)
    suspend fun updateDownload(entity: DownloadEntity) = downloads.update(entity)
    suspend fun setStatus(id: Long, status: DownloadStatus) = downloads.setStatus(id, status)
    suspend fun insertParts(list: List<PartEntity>) = parts.insertAll(list)
    suspend fun resetParts(id: Long) = parts.deleteForDownload(id)

    /**
     * Marks a part running WITHOUT touching [PartEntity.written] — the written
     * offset is the resume record and must survive status transitions.
     */
    suspend fun markPartRunning(partId: Long) =
        parts.setStatus(partId, PartStatus.RUNNING)

    suspend fun markPartDone(partId: Long, written: Long) =
        parts.setProgress(partId, written, PartStatus.DONE)

    suspend fun setPartProgress(partId: Long, written: Long) =
        parts.setProgress(partId, written, PartStatus.RUNNING)
}

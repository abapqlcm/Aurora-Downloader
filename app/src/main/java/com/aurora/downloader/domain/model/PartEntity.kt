package com.aurora.downloader.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One byte-range slice of a download. Its persisted [start]/[end]/[written]
 * triple is the crash-recovery record: on restart, every part resumes from
 * [written] instead of restarting the file.
 */
@Entity(
    tableName = "parts",
    foreignKeys = [
        ForeignKey(
            entity = DownloadEntity::class,
            parentColumns = ["id"],
            childColumns = ["download_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("download_id")]
)
data class PartEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "download_id")
    val downloadId: Long,

    @ColumnInfo(name = "part_index")
    val partIndex: Int,

    /** Inclusive, absolute byte offset in the final file. */
    @ColumnInfo(name = "start")
    val start: Long,

    /** Inclusive, absolute byte offset. -1 means "to end of file". */
    @ColumnInfo(name = "end")
    val end: Long,

    @ColumnInfo(name = "written")
    val written: Long = 0L,

    @ColumnInfo(name = "status")
    val status: PartStatus = PartStatus.PENDING,

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0
)

enum class PartStatus { PENDING, RUNNING, DONE, FAILED }

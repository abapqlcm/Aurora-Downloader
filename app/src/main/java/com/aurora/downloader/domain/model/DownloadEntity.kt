package com.aurora.downloader.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One download job. Deliberately holds everything needed to resume after a
 * hard kill: the URL, the resolved filename, the byte plan, and retry counters.
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(name = "final_url")
    val finalUrl: String? = null,          // after redirects

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "save_path")
    val savePath: String,                  // absolute, inside app-specific or SAF tree

    @ColumnInfo(name = "mime_type")
    val mimeType: String? = null,

    @ColumnInfo(name = "total_bytes")
    val totalBytes: Long = -1L,            // -1 = unknown (no Content-Length)

    @ColumnInfo(name = "downloaded_bytes")
    val downloadedBytes: Long = 0L,

    @ColumnInfo(name = "supports_range")
    val supportsRange: Boolean = false,

    @ColumnInfo(name = "etag")
    val etag: String? = null,              // for If-Range on resume

    @ColumnInfo(name = "part_count")
    val partCount: Int = 1,

    @ColumnInfo(name = "status")
    val status: DownloadStatus = DownloadStatus.CREATED,

    @ColumnInfo(name = "priority")
    val priority: Int = 0,                 // higher = sooner

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "max_retries")
    val maxRetries: Int = 5,

    @ColumnInfo(name = "last_error")
    val lastError: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "user_agent")
    val userAgent: String? = null,

    @ColumnInfo(name = "referer")
    val referer: String? = null,

    /** Serialized cookie header captured from the browser, without which most
     * real-world downloads fail (the single most-missed requirement). */
    @ColumnInfo(name = "cookie_header")
    val cookieHeader: String? = null
)

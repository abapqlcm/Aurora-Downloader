package com.aurora.downloader.download.segment

import com.aurora.downloader.domain.model.PartEntity
import com.aurora.downloader.domain.model.PartStatus
import com.aurora.downloader.download.transport.ProbeResult
import kotlin.math.max
import kotlin.math.min

/**
 * Turns a probe result into concrete byte slices.
 *
 * Deliberately NOT adaptive: the part count is decided once, from the file
 * size, and stays fixed for the life of the download. Mid-download
 * re-planning sounds clever but in practice discards already-paid TCP
 * warm-up and can trip per-connection rate limits on shared hosts.
 */
object SegmentPlanner {

    /** Floor below which multipart is pointless overhead. */
    private const val MULTIPART_MIN_BYTES = 2L * 1024 * 1024   // 2 MiB

    /** Hard ceiling, server-side rate limits make anything above this worse. */
    private const val MAX_PARTS = 16

    fun plan(
        downloadId: Long,
        probe: ProbeResult,
        requestedParts: Int,
        existing: List<PartEntity> = emptyList()
    ): Plan {
        // No range support or unknown size -> single stream, progress is indeterminate.
        if (!probe.isResumable) {
            return Plan(
                partCount = 1,
                parts = existing.ifEmpty {
                    listOf(
                        PartEntity(
                            downloadId = downloadId,
                            partIndex = 0,
                            start = 0,
                            end = -1,
                            status = PartStatus.PENDING
                        )
                    )
                }
            )
        }

        val total = probe.totalBytes
        if (total < MULTIPART_MIN_BYTES || requestedParts <= 1) {
            return singlePart(downloadId, total, existing)
        }

        // Scale parts to file size: a 5 MiB file does not get 16 connections.
        val sizeBased = (total / (512L * 1024)).toInt()     // one part per 512 KiB
        val partCount = min(MAX_PARTS, max(1, min(requestedParts, sizeBased)))

        if (existing.isNotEmpty()) {
            return Plan(partCount = existing.size, parts = existing)
        }

        val stride = total / partCount
        val parts = ArrayList<PartEntity>(partCount)
        var offset = 0L
        for (i in 0 until partCount) {
            val end = if (i == partCount - 1) total - 1 else offset + stride - 1
            parts += PartEntity(
                downloadId = downloadId,
                partIndex = i,
                start = offset,
                end = end,
                status = PartStatus.PENDING
            )
            offset = end + 1
        }
        return Plan(partCount = partCount, parts = parts)
    }

    private fun singlePart(
        downloadId: Long,
        total: Long,
        existing: List<PartEntity>
    ): Plan {
        val parts = existing.ifEmpty {
            listOf(
                PartEntity(
                    downloadId = downloadId,
                    partIndex = 0,
                    start = 0,
                    end = if (total > 0) total - 1 else -1,
                    status = PartStatus.PENDING
                )
            )
        }
        return Plan(partCount = 1, parts = parts)
    }
}

data class Plan(val partCount: Int, val parts: List<PartEntity>)

package com.aurora.downloader.download.transport

import okhttp3.Response

/**
 * Parses `Content-Disposition` per RFC 6266, including the RFC 5987
 * `filename*=UTF-8''...` extended form that nearly every non-ASCII site uses.
 * ADM-grade managers must handle both or they save Persian/Chinese filenames
 * as garbage.
 */
object ContentDispositionParser {

    private val qsPattern = Regex("""(?:^|;)\s*filename\s*=\s*("[^"]*"|[^;]*)""", RegexOption.IGNORE_CASE)

    fun parse(header: String?, fallbackUrl: String): String {
        if (header.isNullOrBlank()) return urlFallback(fallbackUrl)

        // RFC 5987 extended form wins if present.
        val extended = Regex(
            """filename\*\s*=\s*([^']+)'([^']*)'(.+?)\s*(?:;|$)""",
            RegexOption.IGNORE_CASE
        ).find(header)
        if (extended != null) {
            val charset = extended.groupValues[1].ifBlank { "UTF-8" }
            val encoded = extended.groupValues[3].trim()
            return try {
                java.net.URLDecoder.decode(encoded, charset)
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?: urlFallback(fallbackUrl)
            } catch (_: Exception) {
                urlFallback(fallbackUrl)
            }
        }

        val plain = qsPattern.find(header)?.groupValues?.get(1)
            ?.trim()
            ?.trim('"')
            ?.ifBlank { null }
        return plain ?: urlFallback(fallbackUrl)
    }

    private fun urlFallback(url: String): String {
        val raw = url.substringAfterLast('/', "").substringBefore('?')
        return when {
            raw.isBlank() -> "download.bin"
            raw.length > 200 -> raw.takeLast(200)
            else -> raw
        }
    }
}

/**
 * Parsed result of the initial probe request. Everything the segment planner
 * needs to decide whether multipart is even possible.
 */
data class ProbeResult(
    val finalUrl: String,
    val supportsRange: Boolean,
    val totalBytes: Long,          // -1 if unknown
    val etag: String?,
    val lastModified: String?,
    val mimeType: String?,
    val fileName: String
) {
    val isResumable: Boolean get() = supportsRange && totalBytes > 0
}

/**
 * Inspects a server response to decide the download strategy. Two facts matter:
 *  - `Accept-Ranges: bytes`  -> we can split
 *  - `Content-Range` on a ranged probe -> confirms it actually honors ranges
 */
object ProbeInspector {

    fun fromResponse(response: Response, originalUrl: String): ProbeResult {
        val body = response.body
        val total = body?.contentLength() ?: -1L

        val headerAcceptsRanges = response.header("Accept-Ranges")
            ?.equals("bytes", ignoreCase = true) == true

        // A ranged probe returns 206 + Content-Range: bytes 0-0/12345
        val contentRange = response.header("Content-Range")
        val rangedTotal = contentRange?.let {
            Regex("""bytes\s+\d+-\d+/(\d+)""", RegexOption.IGNORE_CASE)
                .find(it)?.groupValues?.get(1)?.toLongOrNull()
        }

        val supportsRange = headerAcceptsRanges || rangedTotal != null
        val resolvedTotal = rangedTotal ?: total

        val disposition = response.header("Content-Disposition")
        val fileName = ContentDispositionParser.parse(disposition, response.request.url.toString())

        return ProbeResult(
            finalUrl = response.request.url.toString(),
            supportsRange = supportsRange,
            totalBytes = resolvedTotal,
            etag = response.header("ETag"),
            lastModified = response.header("Last-Modified"),
            mimeType = body?.contentType()?.toString(),
            fileName = fileName
        )
    }
}

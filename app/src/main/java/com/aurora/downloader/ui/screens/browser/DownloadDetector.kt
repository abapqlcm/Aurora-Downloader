package com.aurora.downloader.ui.screens.browser

import android.webkit.MimeTypeMap
import java.util.Locale

/**
 * Decides whether a URL is *probably a file* rather than a page.
 *
 * `onDownloadStart` alone is not enough: WebView navigates straight to PDFs,
 * ZIPs, APKs and MP4s on many sites without ever calling the download
 * listener, and JS-driven downloads (`blob:`, `window.location`) bypass it
 * entirely. This is the layer that catches those.
 */
object DownloadDetector {

    /** File extensions that should never be rendered as a page. */
    private val FILE_EXTENSIONS = setOf(
        // archives
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "apk", "aab", "jar",
        // documents
        "pdf", "epub", "mobi", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "csv", "rtf", "txt", "odt", "ods", "odp",
        // media
        "mp3", "mp4", "m4a", "m4v", "mkv", "webm", "avi", "mov", "wmv",
        "flv", "aac", "ogg", "opus", "wav", "flac",
        // images (large ones are usually "save this")
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "heic", "tiff",
        // disk / firmware
        "iso", "img", "bin", "dmg", "exe", "msi", "deb", "rpm", "torrent"
    )

    /** MIME prefixes that mean "binary payload", not "document to render". */
    private val BINARY_MIME_PREFIXES = listOf(
        "application/octet-stream",
        "application/zip",
        "application/x-",
        "application/vnd.",
        "application/pdf",
        "application/java-archive",
        "application/vnd.android",
        "video/",
        "audio/",
        "image/"
    )

    /**
     * True when this URL points at a file the user would want to save.
     * Query strings and fragments are stripped before the extension check.
     */
    fun looksLikeFile(url: String?, mimeType: String? = null): Boolean {
        if (url.isNullOrBlank()) return false

        // blob: and data: URLs are JS-generated downloads.
        if (url.startsWith("blob:") || url.startsWith("data:")) return true

        // Explicit attachment marker in the URL itself is rare, but decisive.
        val lower = url.lowercase(Locale.US)
        if (lower.contains("attachment") || lower.contains("download=1") ||
            lower.contains("export=download")
        ) return true

        val mime = mimeType?.lowercase(Locale.US)
        if (!mime.isNullOrBlank() &&
            BINARY_MIME_PREFIXES.any { mime.startsWith(it) }
        ) return true

        val path = url.substringBefore('#').substringBefore('?')
        val ext = path.substringAfterLast('.', "")
            .lowercase(Locale.US)
        if (ext.isBlank() || ext.length > 5) return false
        return ext in FILE_EXTENSIONS
    }

    /** Best-effort MIME guess from the extension, used when the server is silent. */
    fun guessMime(url: String?): String? {
        if (url == null) return null
        val ext = url.substringBefore('#').substringBefore('?')
            .substringAfterLast('.', "")
            .lowercase(Locale.US)
        if (ext.isBlank()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    /** A readable filename for the sheet, taken from the URL path. */
    fun guessName(url: String?): String {
        if (url == null) return "download.bin"
        val raw = url.substringBefore('#').substringBefore('?')
            .substringAfterLast('/')
        val decoded = runCatching {
            java.net.URLDecoder.decode(raw, "UTF-8")
        }.getOrDefault(raw)
        return decoded.takeIf { it.isNotBlank() && it.contains('.') }
            ?: "download.bin"
    }
}

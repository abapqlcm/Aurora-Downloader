package com.aurora.downloader.download.storage

import java.io.File
import java.net.URLDecoder
import java.util.Locale

/**
 * Turns a URL + server headers into a filename that is safe to write.
 *
 * Priority, per the spec: Content-Disposition > URL path > MIME > fallback.
 * Every step has to survive Unicode and Persian text, which `guessFileName`
 * in the engine did not handle — it left percent-encoded names in place.
 */
object FileNameResolver {

    /**
     * Extracts the filename a server suggested, or null when it did not.
     * Handles the RFC 6266 forms `filename="..."` and `filename*=UTF-8''...`,
     * which is what real servers actually send.
     */
    fun fromContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null

        // RFC 5987 extended form: filename*=UTF-8''Na%C3%AFve%20file.txt
        val extended = Regex(
            """filename\*\s*=\s*(?:UTF-8|utf-8)''(?<value>[^;]+)""",
            RegexOption.IGNORE_CASE
        ).find(header)?.groupValues?.get(1)?.trim()
        if (!extended.isNullOrBlank()) {
            return safe(decode(extended))
        }

        // Plain form, quoted or not: filename="file.zip" or filename=file.zip
        val plain = Regex(
            """filename\s*=\s*(?:"([^"]*)"|([^;]+))""",
            RegexOption.IGNORE_CASE
        ).find(header)
        plain?.let { match ->
            // Group 1 is the quoted value, group 2 the unquoted one.
            val quoted = match.groupValues[1].takeIf { it.isNotBlank() }
            val unquoted = match.groupValues[2].trim().takeIf { v -> v.isNotBlank() }
            val value = quoted ?: unquoted
            if (!value.isNullOrBlank()) return safe(decode(value))
        }
        return null
    }

    /** The last path segment, decoded. */
    fun fromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val path = url.substringBefore('#').substringBefore('?')
        val raw = path.substringAfterLast('/').trim()
        if (raw.isBlank()) return null
        return decode(raw).takeIf { it.isNotBlank() }
    }

    /**
     * Maps a MIME type to an extension when the URL had none:
     * `application/zip` + "report" -> "report.zip".
     */
    fun withMimeExtension(name: String, mimeType: String?): String {
        if (mimeType.isNullOrBlank()) return name
        if (name.contains('.')) return name
        val ext = mimeToExtension(mimeType.lowercase(Locale.US)) ?: return name
        return "$name.$ext"
    }

    /**
     * Guarantees the name cannot escape the destination directory and cannot
     * be a Windows-reserved name. Without this a crafted name like
     * `../../evil` writes outside the download folder.
     */
    fun safe(name: String): String {
        if (name.isBlank()) return "download.bin"
        // A name with a path separator is not a name; keep only the leaf.
        val leaf = name.replace('\\', '/')
            .substringAfterLast('/')
            .trim()
        if (leaf.isBlank() || leaf == "." || leaf == "..") return "download.bin"
        val cleaned = leaf.trimEnd('.', ' ', '\t')
        if (cleaned.isBlank()) return "download.bin"
        // Reserved device names on Windows / FAT media.
        val base = cleaned.substringBefore('.')
        if (base.uppercase(Locale.US) in RESERVED_NAMES) return "_$cleaned"
        if (cleaned.length > 180) return cleaned.take(180)
        return cleaned
    }

    /**
     * Avoids clobbering an existing file: `file.zip`, `file (1).zip`,
     * `file (2).zip`... The engine writes into app-private staging where two
     * downloads of the same URL would otherwise share one output file and
     * corrupt each other.
     */
    fun deduplicate(directory: File, name: String): String {
        if (!directory.exists()) return name
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        val extension = if (ext.isEmpty()) "" else ".$ext"

        var candidate = name
        var index = 1
        // Cap so a pathological loop cannot spin forever.
        while (File(directory, candidate).exists() && index < 1000) {
            candidate = "$base ($index)$extension"
            index++
        }
        return candidate
    }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private val RESERVED_NAMES = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    )

    private fun mimeToExtension(mime: String): String? = when (mime) {
        "application/zip" -> "zip"
        "application/x-rar-compressed" -> "rar"
        "application/x-7z-compressed" -> "7z"
        "application/x-tar", "application/gzip" -> "tar"
        "application/pdf" -> "pdf"
        "application/vnd.android.package-archive" -> "apk"
        "application/java-archive" -> "jar"
        "application/epub+zip" -> "epub"
        "application/msword" -> "doc"
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
        "application/vnd.ms-excel" -> "xls"
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
        "application/vnd.ms-powerpoint" -> "ppt"
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
        "application/octet-stream" -> "bin"
        "text/plain" -> "txt"
        "text/csv" -> "csv"
        "text/html" -> "html"
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/svg+xml" -> "svg"
        "audio/mpeg" -> "mp3"
        "audio/mp4", "audio/x-m4a" -> "m4a"
        "audio/ogg" -> "ogg"
        "audio/wav" -> "wav"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "video/x-msvideo" -> "avi"
        "video/quicktime" -> "mov"
        "application/x-iso9660-image" -> "iso"
        else -> null
    }
}

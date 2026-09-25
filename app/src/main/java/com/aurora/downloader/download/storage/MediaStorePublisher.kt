package com.aurora.downloader.download.storage

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Makes a finished download visible to the user.
 *
 * The engine downloads into app-private storage (it needs RandomAccessFile
 * seek-per-part, which content:// URIs cannot give us). Once a download is
 * verified, we publish it into the public Downloads collection under a
 * dedicated "RDM" folder and delete the private copy.
 *
 * Two paths, by API level:
 *  - 29+: MediaStore.Downloads + RELATIVE_PATH — no storage permission needed,
 *    the user sees the file in every file manager and gallery.
 *  - 24-28: write straight to the public Downloads/RDM directory, then ask the
 *    MediaScanner to index it (needs WRITE_EXTERNAL_STORAGE, declared in the
 *    manifest with maxSdkVersion 28).
 */
object MediaStorePublisher {

    const val FOLDER = "RDM"

    fun publish(context: Context, source: File, fileName: String, mimeType: String?): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                publishViaMediaStore(context, source, fileName, mimeType)
            } else {
                publishViaLegacyPath(context, source, fileName, mimeType)
            }
        } catch (t: Throwable) {
            // Publishing is best-effort; never fail a download that already
            // succeeded on disk. The private copy stays behind as a fallback.
            null
        }
    }

    private fun publishViaMediaStore(
        context: Context,
        source: File,
        fileName: String,
        mimeType: String?
    ): Uri? {
        val resolver = context.contentResolver

        // Insert a pending row so nothing else picks up a half-written file.
        val pending = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType ?: "*/*")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL),
            pending
        ) ?: return null

        resolver.openOutputStream(uri, "w")?.use { out ->
            source.inputStream().use { it.copyTo(out, bufferSize = 512 * 1024) }
        } ?: return null

        // Flip the row to visible.
        val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
        resolver.update(uri, done, null, null)
        return uri
    }

    private fun publishViaLegacyPath(
        context: Context,
        source: File,
        fileName: String,
        mimeType: String?
    ): Uri? {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            FOLDER
        )
        if (!dir.exists()) dir.mkdirs()

        val target = File(dir, fileName)
        source.inputStream().use { input ->
            target.outputStream().use { input.copyTo(it, bufferSize = 512 * 1024) }
        }

        // Index the new file so gallery/file apps pick it up immediately.
        val mime = mimeType ?: "*/*"
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime)) { _, _ -> }
        return Uri.fromFile(target)
    }
}

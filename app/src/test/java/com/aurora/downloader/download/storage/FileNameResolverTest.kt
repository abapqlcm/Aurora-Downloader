package com.aurora.downloader.download.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Filenames are the highest-trust surface in a downloader: a crafted name is
 * a path-traversal vector, and a duplicate name silently corrupts the file
 * another download is writing. These cover the spec section 21 cases.
 */
class FileNameResolverTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // --- Content-Disposition -------------------------------------------------

    @Test
    fun parsesQuotedFilename() {
        val name = FileNameResolver.fromContentDisposition(
            """attachment; filename="final_report.zip""""
        )
        assertEquals("final_report.zip", name)
    }

    @Test
    fun parsesUnquotedFilename() {
        val name = FileNameResolver.fromContentDisposition("attachment; filename=report.zip")
        assertEquals("report.zip", name)
    }

    @Test
    fun parsesRfc5987ExtendedFilename() {
        // Percent-encoded UTF-8, the form CDNs use for non-ASCII names.
        val name = FileNameResolver.fromContentDisposition(
            "attachment; filename*=UTF-8''%D9%81%D8%A7%DB%8C%D9%84%20%D9%85%D9%86.zip"
        )
        assertEquals("فایل من.zip", name)
    }

    @Test
    fun prefersExtendedOverPlainFilename() {
        val name = FileNameResolver.fromContentDisposition(
            """attachment; filename="x.zip"; filename*=UTF-8''%D9%81%D8%A7%DB%8C%D9%84.zip"""
        )
        assertEquals("فایل.zip", name)
    }

    @Test
    fun returnsNullWhenNoFilenamePresent() {
        assertNull(FileNameResolver.fromContentDisposition("inline"))
        assertNull(FileNameResolver.fromContentDisposition(null))
        assertNull(FileNameResolver.fromContentDisposition(""))
    }

    @Test
    fun handlesFilenameCaseInsensitively() {
        val name = FileNameResolver.fromContentDisposition(
            """Attachment; FILENAME="caps.zip""""
        )
        assertEquals("caps.zip", name)
    }

    // --- URL fallback --------------------------------------------------------

    @Test
    fun fallsBackToUrlPathSegment() {
        val name = FileNameResolver.fromUrl("https://example.com/files/big_file.zip")
        assertEquals("big_file.zip", name)
    }

    @Test
    fun stripsQueryAndFragmentFromUrlPath() {
        val name = FileNameResolver.fromUrl(
            "https://example.com/file.zip?token=[REDACTED]"
        )
        assertEquals("file.zip", name)
    }

    @Test
    fun decodesPercentEncodedUrlName() {
        val name = FileNameResolver.fromUrl(
            "https://example.com/%D9%81%D8%A7%DB%8C%D9%84.txt"
        )
        assertEquals("فایل.txt", name)
    }

    @Test
    fun returnsNullForUrlWithNoName() {
        assertNull(FileNameResolver.fromUrl("https://example.com/"))
        assertNull(FileNameResolver.fromUrl(null))
        assertNull(FileNameResolver.fromUrl(""))
    }

    // --- MIME extension ------------------------------------------------------

    @Test
    fun addsExtensionFromMimeWhenMissing() {
        assertEquals("report.zip", FileNameResolver.withMimeExtension("report", "application/zip"))
    }

    @Test
    fun keepsExistingExtensionEvenWithMime() {
        assertEquals("report.tar", FileNameResolver.withMimeExtension("report.tar", "application/zip"))
    }

    @Test
    fun ignoresUnknownMime() {
        assertEquals("report", FileNameResolver.withMimeExtension("report", "application/x-unknown"))
    }

    @Test
    fun mapsVideoAndAudioMimes() {
        assertEquals("movie.mp4", FileNameResolver.withMimeExtension("movie", "video/mp4"))
        assertEquals("song.mp3", FileNameResolver.withMimeExtension("song", "audio/mpeg"))
    }

    // --- safety --------------------------------------------------------------

    @Test
    fun blocksPathTraversal() {
        val name = FileNameResolver.safe("../../etc/passwd")
        assertEquals("passwd", name)
    }

    @Test
    fun blocksBackslashTraversal() {
        val name = FileNameResolver.safe("..\\..\\evil.txt")
        assertEquals("evil.txt", name)
    }

    @Test
    fun blocksDirectoryAliasNames() {
        assertEquals("download.bin", FileNameResolver.safe(".."))
        assertEquals("download.bin", FileNameResolver.safe("."))
        assertEquals("download.bin", FileNameResolver.safe(""))
    }

    @Test
    fun doesNotEndWithDotOrSpace() {
        // Trailing dots/spaces get dropped by some filesystems, which would
        // break the "open file" action.
        assertNotEquals("file.", FileNameResolver.safe("file."))
        assertNotEquals("file ", FileNameResolver.safe("file "))
    }

    @Test
    fun prefixesReservedDeviceNames() {
        // Reserved names on FAT/Windows media must not be used verbatim.
        val name = FileNameResolver.safe("AUX")
        assertEquals("_AUX", name)
        assertEquals("_CON.txt", FileNameResolver.safe("CON.txt"))
    }

    @Test
    fun truncatesAbsurdlyLongNames() {
        val long = "a".repeat(500) + ".bin"
        val safe = FileNameResolver.safe(long)
        assertTrue(
            "expected truncation to <=180, got ${safe.length}",
            safe.length <= 180
        )
    }

    // --- dedup ---------------------------------------------------------------

    @Test
    fun keepsTheNameWhenFileDoesNotExistYet() {
        val dir = tmp.newFolder("dl")
        val name = FileNameResolver.deduplicate(dir, "file.zip")
        assertEquals("file.zip", name)
    }

    @Test
    fun appendsCounterForExistingFile() {
        val dir = tmp.newFolder("dl")
        File(dir, "file.zip").writeText("existing")
        val name = FileNameResolver.deduplicate(dir, "file.zip")
        assertEquals("file (1).zip", name)
    }

    @Test
    fun keepsCountingUntilFree() {
        val dir = tmp.newFolder("dl")
        File(dir, "file.zip").writeText("a")
        File(dir, "file (1).zip").writeText("b")
        File(dir, "file (2).zip").writeText("c")
        val name = FileNameResolver.deduplicate(dir, "file.zip")
        assertEquals("file (3).zip", name)
    }

    @Test
    fun deduplicatesExtensionlessFiles() {
        val dir = tmp.newFolder("dl")
        File(dir, "README").writeText("a")
        val name = FileNameResolver.deduplicate(dir, "README")
        assertEquals("README (1)", name)
    }

    @Test
    fun dedupNeverReturnsAnExistingName() {
        val dir = tmp.newFolder("dl")
        val created = mutableListOf<File>()
        // Simulate 40 downloads all asking for the same name: each call must
        // return a name that is not already on disk, and the caller "writes"
        // it before asking again.
        repeat(40) {
            val name = FileNameResolver.deduplicate(dir, "x.zip")
            assertFalse("dedup returned an existing name: $name", File(dir, name).exists())
            created += File(dir, name).apply { writeText("block") }
        }
        // And they must all be distinct — a loop repeating a name would
        // silently overwrite a previous download.
        val names = created.map { it.name }
        assertEquals(
            "dedup produced duplicate candidates",
            names.size,
            names.toSet().size
        )
    }

    @Test
    fun dedupToleratesMissingDirectory() {
        val ghost = File(tmp.root, "does-not-exist")
        assertEquals("file.zip", FileNameResolver.deduplicate(ghost, "file.zip"))
    }
}

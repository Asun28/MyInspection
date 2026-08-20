package nz.myinspection.core.media

import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PendingPhotoAssetCleanupTest {
    @Test
    fun `cleanup readopts only an active row with the pending photo id and exact relative path`() = inTempDir { root ->
        val relPath = "photos/property/inspection/photo-1.jpg"
        val asset = File(root, relPath).also {
            assertTrue(it.parentFile!!.mkdirs())
            it.writeText("evidence")
        }
        PendingPhotoLease.acquire(asset).closeAfter(PendingPhotoLeaseDisposition.RETAIN)

        val deleted = mutableListOf<String>()
        val result = PendingPhotoAssetCleanup(
            mediaRoot = root,
            findPhoto = { photoId ->
                if (photoId == "photo-1") PendingPhotoReference(relPath = relPath, active = true) else null
            },
            deleter = OrphanFileDeleter { path -> deleted += path; File(root, path).delete() },
        ).run()

        assertEquals(listOf(relPath), result.readopted, "an active exact row proves the asset was adopted")
        assertTrue(asset.isFile, "readoption must remove only the marker, never a live JPEG")
        assertFalse(File(asset.parentFile, "photo-1.jpg.pending").exists())
        assertTrue(deleted.isEmpty(), "the deleter must not run for an active exact row")
    }

    @Test
    fun `cleanup deletes an exact path when its photo id is no longer active`() = inTempDir { root ->
        val relPath = "photos/property/inspection/photo-inactive.jpg"
        val asset = asset(root, relPath)
        val marker = File(asset.parentFile, "photo-inactive.jpg.pending")
        PendingPhotoLease.acquire(asset).closeAfter(PendingPhotoLeaseDisposition.RETAIN)

        val result = PendingPhotoAssetCleanup(
            mediaRoot = root,
            findPhoto = { photoId ->
                if (photoId == "photo-inactive") PendingPhotoReference(relPath, active = false) else null
            },
            deleter = OrphanFileDeleter { path -> File(root, path).delete() || !File(root, path).exists() },
        ).run()

        assertEquals(listOf(relPath), result.deleted, "soft-deleted rows must not readopt a pending JPEG")
        assertFalse(asset.exists())
        assertFalse(marker.exists())
    }

    @Test
    fun `cleanup deletes an unadopted JPEG before its marker`() = inTempDir { root ->
        val relPath = "photos/property/inspection/photo-2.jpg"
        val asset = File(root, relPath).also {
            assertTrue(it.parentFile!!.mkdirs())
            it.writeText("untracked")
        }
        val marker = File(asset.parentFile, "photo-2.jpg.pending")
        PendingPhotoLease.acquire(asset).closeAfter(PendingPhotoLeaseDisposition.RETAIN)

        val result = PendingPhotoAssetCleanup(
            mediaRoot = root,
            findPhoto = { null },
            deleter = OrphanFileDeleter { path -> File(root, path).delete() || !File(root, path).exists() },
        ).run()

        assertEquals(listOf(relPath), result.deleted)
        assertFalse(asset.exists(), "the worker must remove the unadopted JPEG")
        assertFalse(marker.exists(), "the marker may disappear only after asset cleanup succeeds")
    }

    @Test
    fun `cleanup treats an active row at a different relative path as unadopted`() = inTempDir { root ->
        val relPath = "photos/property/inspection/photo-3.jpg"
        val asset = asset(root, relPath)
        PendingPhotoLease.acquire(asset).closeAfter(PendingPhotoLeaseDisposition.RETAIN)

        val result = PendingPhotoAssetCleanup(
            mediaRoot = root,
            findPhoto = { photoId ->
                if (photoId == "photo-3") {
                    PendingPhotoReference("photos/property/inspection/other-photo.jpg", active = true)
                } else {
                    null
                }
            },
            deleter = OrphanFileDeleter { path -> File(root, path).delete() || !File(root, path).exists() },
        ).run()

        assertEquals(listOf(relPath), result.deleted, "the worker must require the exact DB rel_path, not only photo id")
        assertFalse(asset.exists())
        assertFalse(File(asset.parentFile, "photo-3.jpg.pending").exists())
    }

    @Test
    fun `cleanup clears a marker when the unadopted JPEG is already absent`() = inTempDir { root ->
        val relPath = "photos/property/inspection/photo-4.jpg"
        val asset = File(root, relPath).also { assertTrue(it.parentFile!!.mkdirs()) }
        val marker = File(asset.parentFile, "photo-4.jpg.pending")
        PendingPhotoLease.acquire(asset).closeAfter(PendingPhotoLeaseDisposition.RETAIN)

        val result = PendingPhotoAssetCleanup(
            mediaRoot = root,
            findPhoto = { null },
            deleter = OrphanFileDeleter { path -> !File(root, path).exists() },
        ).run()

        assertEquals(listOf(relPath), result.deleted, "a missing target is already clean and must not strand its marker")
        assertFalse(marker.exists())
    }

    @Test
    fun `cleanup rejects an invalid sidecar shape without touching either file`() = inTempDir { root ->
        val directory = File(root, "photos/property/inspection").also { assertTrue(it.mkdirs()) }
        val asset = File(directory, " .jpg").also { it.writeText("must stay") }
        val marker = File(directory, " .jpg.pending").also { it.writeText("") }
        val calls = mutableListOf<String>()

        val result = PendingPhotoAssetCleanup(
            mediaRoot = root,
            findPhoto = { error("an invalid marker must not query the database") },
            deleter = OrphanFileDeleter { path -> calls += path; true },
        ).run()

        assertEquals(listOf("photos/property/inspection/ .jpg.pending"), result.rejected)
        assertTrue(asset.isFile)
        assertTrue(marker.isFile)
        assertTrue(calls.isEmpty(), "the shape gate must precede physical deletion")
    }

    @Test
    fun `cleanup retains asset and marker when deletion reports failure`() = inTempDir { root ->
        val relPath = "photos/property/inspection/photo-5.jpg"
        val asset = asset(root, relPath)
        val marker = File(asset.parentFile, "photo-5.jpg.pending")
        PendingPhotoLease.acquire(asset).closeAfter(PendingPhotoLeaseDisposition.RETAIN)

        val result = PendingPhotoAssetCleanup(
            mediaRoot = root,
            findPhoto = { null },
            deleter = OrphanFileDeleter { false },
        ).run()

        assertEquals(listOf(FailedDeletion(relPath, cause = null)), result.failed)
        assertTrue(asset.isFile, "marker removal must not run before a failed asset deletion")
        assertTrue(marker.isFile)
    }

    @Test
    fun `cleanup retries a marker that is still locked by an ingest`() = inTempDir { root ->
        val relPath = "photos/property/inspection/photo-6.jpg"
        val asset = asset(root, relPath)
        val marker = File(asset.parentFile, "photo-6.jpg.pending")
        val ingestLease = PendingPhotoLease.acquire(asset)
        val calls = mutableListOf<String>()
        try {
            val result = PendingPhotoAssetCleanup(
                mediaRoot = root,
                findPhoto = { null },
                deleter = OrphanFileDeleter { path -> calls += path; true },
            ).run()

            val failure = result.failed.single()
            assertEquals(relPath, failure.relPath)
            assertTrue(failure.cause is IOException, "a nonblocking lock miss is an environmental retry, not deletion")
            assertTrue(asset.isFile)
            assertTrue(marker.isFile)
            assertTrue(calls.isEmpty(), "worker must never delete beside a live ingest lease")
        } finally {
            ingestLease.closeAfter(PendingPhotoLeaseDisposition.RETAIN)
        }
    }

    @Test
    fun `cleanup can finish a resolving marker after its JPEG has been readopted`() = inTempDir { root ->
        val relPath = "photos/property/inspection/photo-7.jpg"
        val asset = asset(root, relPath)
        val marker = File(asset.parentFile, "photo-7.jpg.pending").also { it.writeBytes(byteArrayOf(1)) }

        val result = PendingPhotoAssetCleanup(
            mediaRoot = root,
            findPhoto = { photoId ->
                if (photoId == "photo-7") PendingPhotoReference(relPath, active = true) else null
            },
            deleter = OrphanFileDeleter { error("an active sidecar must only be readopted") },
        ).run()

        assertEquals(listOf(relPath), result.readopted)
        assertTrue(asset.isFile)
        assertFalse(marker.exists(), "the recovery worker must be allowed to complete the resolving handoff")
    }

    @Test
    fun `cleanup fails closed before descending into an alias or canonical path outside the media root`() = inTempDir { root ->
        val photos = File(root, "photos").also { assertTrue(it.mkdirs()) }.canonicalFile
        val outside = kotlin.io.path.createTempDirectory("td14-outside-").toFile().canonicalFile
        val alias = File(photos, "inspection-alias")
        val listed = mutableListOf<File>()
        try {
            assertFailsWith<IllegalStateException> {
                PendingPhotoAssetCleanup.withListedChildren(
                    mediaRoot = root,
                    findPhoto = { error("no outside entry may reach DB lookup") },
                    deleter = OrphanFileDeleter { error("no outside entry may reach deletion") },
                    listChildren = { directory ->
                        listed += directory.canonicalFile
                        when (directory.canonicalFile) {
                            photos -> arrayOf(outside, alias)
                            else -> error("cleanup descended outside media root: ${directory.path}")
                        }
                    },
                    readAttributes = { file ->
                        when (file.canonicalFile) {
                            root.canonicalFile, photos, outside -> TestAttributes(directory = true)
                            alias.canonicalFile -> TestAttributes(directory = true, symbolicLink = true)
                            else -> error("unexpected attribute read: ${file.path}")
                        }
                    },
                ).run()
            }

            assertEquals(listOf(photos), listed, "canonical containment must be checked before every descent")
        } finally {
            outside.deleteRecursively()
        }
    }

    @Test
    fun `cleanup fails closed before canonicalizing an aliased media root`() = inTempDir { root ->
        var listed = false

        assertFailsWith<IllegalStateException> {
            PendingPhotoAssetCleanup.withListedChildren(
                mediaRoot = root,
                findPhoto = { error("aliased root must not reach DB lookup") },
                deleter = OrphanFileDeleter { error("aliased root must not reach deletion") },
                listChildren = {
                    listed = true
                    error("aliased root must not be scanned")
                },
                readAttributes = { file ->
                    if (file.canonicalFile == root.canonicalFile) {
                        TestAttributes(directory = true, symbolicLink = true)
                    } else {
                        error("aliased root must reject before child attribute reads")
                    }
                },
            ).run()
        }

        assertFalse(listed)
    }

    @Test
    fun `unexpected DB lookup failure preserves its primary and retains the marker for recovery`() = inTempDir { root ->
        val relPath = "photos/property/inspection/photo-8.jpg"
        val asset = asset(root, relPath)
        val marker = File(asset.parentFile, "photo-8.jpg.pending")
        PendingPhotoLease.acquire(asset).closeAfter(PendingPhotoLeaseDisposition.RETAIN)
        val primary = IllegalStateException("lookup contract violated")

        val thrown = assertFailsWith<IllegalStateException> {
            PendingPhotoAssetCleanup(
                mediaRoot = root,
                findPhoto = { throw primary },
                deleter = OrphanFileDeleter { error("deleter must not run after lookup failure") },
            ).run()
        }

        assertSame(primary, thrown)
        assertTrue(asset.isFile)
        assertTrue(marker.isFile, "unexpected failures must retain the only durable recovery record")
    }

    @Test
    fun `unexpected deleter primary retains the marker and suppresses its lease close failure`() = inTempDir { root ->
        val relPath = "photos/property/inspection/photo-9.jpg"
        val asset = asset(root, relPath)
        val marker = File(asset.parentFile, "photo-9.jpg.pending")
        PendingPhotoLease.acquire(asset).closeAfter(PendingPhotoLeaseDisposition.RETAIN)
        val primary = IllegalStateException("deleter contract violated")
        val closeFailure = IllegalStateException("lease close failed")

        val thrown = assertFailsWith<IllegalStateException> {
            PendingPhotoAssetCleanup.withLeaseOpener(
                mediaRoot = root,
                findPhoto = { null },
                deleter = OrphanFileDeleter { throw primary },
                openLease = { ThrowingLease(closeFailure) },
            ).run()
        }

        assertSame(primary, thrown)
        assertEquals(listOf(closeFailure), primary.suppressed.toList())
        assertTrue(marker.isFile)
    }

    private fun asset(root: File, relPath: String): File = File(root, relPath).also {
        assertTrue(it.parentFile!!.mkdirs())
        it.writeText("evidence")
    }

    private fun inTempDir(block: (File) -> Unit) {
        val root = kotlin.io.path.createTempDirectory("td14-pending-cleanup-").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    private class TestAttributes(
        private val directory: Boolean = false,
        private val symbolicLink: Boolean = false,
    ) : java.nio.file.attribute.BasicFileAttributes {
        override fun isRegularFile(): Boolean = false
        override fun isDirectory(): Boolean = directory
        override fun isSymbolicLink(): Boolean = symbolicLink
        override fun isOther(): Boolean = false
        override fun size(): Long = 0L
        override fun fileKey(): Any? = null
        override fun lastModifiedTime() = java.nio.file.attribute.FileTime.fromMillis(0)
        override fun lastAccessTime() = java.nio.file.attribute.FileTime.fromMillis(0)
        override fun creationTime() = java.nio.file.attribute.FileTime.fromMillis(0)
    }

    private class ThrowingLease(
        private val closeFailure: Throwable,
    ) : PendingPhotoLeaseHandle {
        override fun closeAfter(disposition: PendingPhotoLeaseDisposition): Boolean = error("closeAfter must not run after primary")

        override fun close() {
            throw closeFailure
        }
    }
}

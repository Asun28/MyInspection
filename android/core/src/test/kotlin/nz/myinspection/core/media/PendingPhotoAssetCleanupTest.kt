package nz.myinspection.core.media

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.testng.SkipException

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
    fun `cleanup removes an empty crash marker when publish never created the JPEG`() = inTempDir { root ->
        val relPath = "photos/property/inspection/photo-empty-crash.jpg"
        val markerRelPath = "$relPath.pending"
        val marker = File(root, markerRelPath).also {
            assertTrue(it.parentFile!!.mkdirs())
            assertTrue(it.createNewFile())
        }

        val result = PendingPhotoAssetCleanup(
            mediaRoot = root,
            findPhoto = { null },
            deleter = OrphanFileDeleter { error("an absent JPEG needs no asset deletion") },
        ).run()

        assertEquals(listOf(relPath), result.deleted)
        assertFalse(marker.exists(), "a pre-publish crash must not strand an empty marker forever")
    }

    @Test
    fun `cleanup removes a truncated crash marker after an exact active row adopted the JPEG`() = inTempDir { root ->
        val relPath = "photos/property/inspection/photo-truncated-adopted.jpg"
        val asset = asset(root, relPath)
        val marker = File(root, "$relPath.pending").also { it.writeText("MIP1:N:0123") }

        val result = PendingPhotoAssetCleanup(
            mediaRoot = root,
            findPhoto = { PendingPhotoReference(relPath, active = true) },
            deleter = OrphanFileDeleter { error("an adopted JPEG must not be deleted") },
        ).run()

        assertEquals(listOf(relPath), result.readopted)
        assertTrue(asset.isFile)
        assertFalse(marker.exists(), "a post-record crash marker must be recoverable from the exact active row")
    }

    @Test
    fun `cleanup fails a malformed marker closed when an unadopted JPEG still exists`() = inTempDir { root ->
        val relPath = "photos/property/inspection/photo-malformed-ambiguous.jpg"
        val markerRelPath = "$relPath.pending"
        val asset = asset(root, relPath)
        val marker = File(root, markerRelPath).also { it.writeText("MIP1:R:broken") }

        val result = PendingPhotoAssetCleanup(
            mediaRoot = root,
            findPhoto = { null },
            deleter = OrphanFileDeleter { error("ambiguous persisted state must not delete the JPEG") },
        ).run()

        assertEquals(listOf(markerRelPath), result.rejected)
        assertTrue(asset.isFile)
        assertTrue(marker.isFile)
        assertTrue(result.failed.isEmpty(), "malformed persisted state is a failure, not an endless retry")
    }

    @Test
    fun `cleanup classifies an exact-length corrupt marker without a Windows locked-channel retry`() = inTempDir { root ->
        val relPath = "photos/property/inspection/photo-corrupt-exact-length.jpg"
        val markerRelPath = "$relPath.pending"
        val asset = asset(root, relPath)
        val marker = File(root, markerRelPath).also { it.writeText("x".repeat(39)) }

        val result = PendingPhotoAssetCleanup(
            mediaRoot = root,
            findPhoto = { null },
            deleter = OrphanFileDeleter { error("corrupt persisted state must not delete the JPEG") },
        ).run()

        assertEquals(listOf(markerRelPath), result.rejected)
        assertTrue(result.failed.isEmpty())
        assertTrue(asset.isFile)
        assertTrue(marker.isFile)
    }

    @Test
    fun `cleanup retries rather than clearing an empty marker still locked by its creator`() = inTempDir { root ->
        val relPath = "photos/property/inspection/photo-empty-live.jpg"
        val marker = File(root, "$relPath.pending").also {
            assertTrue(it.parentFile!!.mkdirs())
            assertTrue(it.createNewFile())
        }

        java.nio.channels.FileChannel.open(marker.toPath(), READ, WRITE).use { channel ->
            channel.lock().use {
                val result = PendingPhotoAssetCleanup(
                    mediaRoot = root,
                    findPhoto = { null },
                    deleter = OrphanFileDeleter { error("a live creator owns this marker") },
                ).run()

                val failure = result.failed.single()
                assertEquals(relPath, failure.relPath)
                assertTrue(failure.cause?.message.orEmpty().contains("locked"))
                assertTrue(marker.isFile)
                assertTrue(result.rejected.isEmpty())
            }
        }
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
        val marker = File(asset.parentFile, "photo-7.jpg.pending")
        val lease = PendingPhotoLease.acquire(asset)
        java.io.RandomAccessFile(marker, "rw").use {
            assertFalse(lease.closeAfter(PendingPhotoLeaseDisposition.RECORDED))
        }

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
    fun `cleanup rejects a marker swapped to a symlink after scanning without touching its referent`() =
        inTempDir { root ->
            val relPath = "photos/property/inspection/photo-swapped-marker.jpg"
            val asset = asset(root, relPath)
            val marker = File(asset.parentFile, "photo-swapped-marker.jpg.pending")
            PendingPhotoLease.acquire(asset).closeAfter(PendingPhotoLeaseDisposition.RETAIN)
            val referent = File(root, "swapped-marker-referent").also { it.writeText("do-not-touch") }
            val result = PendingPhotoAssetCleanup.withLeaseOpener(
                mediaRoot = root,
                findPhoto = { PendingPhotoReference(relPath, active = true) },
                deleter = OrphanFileDeleter { error("an active row must not delete") },
                openLease = { scannedMarker, expectedIdentity ->
                    assertTrue(Files.deleteIfExists(scannedMarker.toPath()))
                    createSymlinkOrSkip(scannedMarker, referent)
                    PendingPhotoLease.openExisting(scannedMarker, expectedIdentity)
                },
            ).run()

            val failure = result.failed.single()
            assertEquals(relPath, failure.relPath)
            assertTrue(failure.cause is IOException)
            assertEquals("do-not-touch", referent.readText())
            assertTrue(Files.isSymbolicLink(marker.toPath()))
            assertTrue(result.readopted.isEmpty())
        }

    @Test
    fun `cleanup binds the scanned marker identity to its later no-follow open`() = inTempDir { root ->
        val relPath = "photos/property/inspection/photo-swapped-regular.jpg"
        val asset = asset(root, relPath)
        val marker = File(asset.parentFile, "photo-swapped-regular.jpg.pending")
        PendingPhotoLease.acquire(asset).closeAfter(PendingPhotoLeaseDisposition.RETAIN)
        val replacementTarget = File(root, "replacement.jpg")
        PendingPhotoLease.acquireWithDurability(
            replacementTarget,
            forceMarker = { it.force(true) },
            syncParentDirectory = {},
            newToken = { ByteArray(16) { 0x55 } },
        ).closeAfter(PendingPhotoLeaseDisposition.RETAIN)
        val replacement = File(replacementTarget.parentFile, "replacement.jpg.pending")

        val result = PendingPhotoAssetCleanup.withLeaseOpener(
            mediaRoot = root,
            findPhoto = { error("identity mismatch must fail before DB lookup") },
            deleter = OrphanFileDeleter { error("identity mismatch must fail before deletion") },
            openLease = { scannedMarker, expectedIdentity ->
                Files.move(replacement.toPath(), scannedMarker.toPath(), REPLACE_EXISTING)
                PendingPhotoLease.openExisting(scannedMarker, expectedIdentity)
            },
        ).run()

        val failure = result.failed.single()
        assertEquals(relPath, failure.relPath)
        assertTrue(failure.cause is IOException)
        assertTrue(asset.isFile)
        assertTrue(marker.isFile)
    }

    @Test
    fun `cleanup retains the token identity captured during enumeration when a later candidate is replaced`() =
        inTempDir { root ->
            val triggerRelPath = "photos/property/inspection/a-trigger.jpg"
            val victimRelPath = "photos/property/inspection/z-victim.jpg"
            val trigger = asset(root, triggerRelPath)
            val victim = File(root, victimRelPath).also { it.writeText("evidence") }
            PendingPhotoLease.acquireWithDurability(
                trigger,
                forceMarker = { it.force(true) },
                syncParentDirectory = {},
                newToken = { ByteArray(16) { 0x11 } },
            ).closeAfter(PendingPhotoLeaseDisposition.RETAIN)
            PendingPhotoLease.acquireWithDurability(
                victim,
                forceMarker = { it.force(true) },
                syncParentDirectory = {},
                newToken = { ByteArray(16) { 0x22 } },
            ).closeAfter(PendingPhotoLeaseDisposition.RETAIN)
            val victimMarker = File(victim.parentFile, "z-victim.jpg.pending")
            val replacementTarget = File(root, "replacement.jpg")
            PendingPhotoLease.acquireWithDurability(
                replacementTarget,
                forceMarker = { it.force(true) },
                syncParentDirectory = {},
                newToken = { ByteArray(16) { 0x33 } },
            ).closeAfter(PendingPhotoLeaseDisposition.RETAIN)
            val replacementMarker = File(root, "replacement.jpg.pending")
            val replacementBytes = replacementMarker.readBytes()
            var swapped = false

            val result = PendingPhotoAssetCleanup(
                mediaRoot = root,
                findPhoto = { photoId ->
                    when (photoId) {
                        "a-trigger" -> {
                            Files.move(replacementMarker.toPath(), victimMarker.toPath(), REPLACE_EXISTING)
                            swapped = true
                            PendingPhotoReference(triggerRelPath, active = true)
                        }
                        "z-victim" -> error("a replaced token must fail before the second DB lookup")
                        else -> null
                    }
                },
                deleter = OrphanFileDeleter { error("active or replaced candidates must not delete") },
            ).run()

            assertTrue(swapped)
            assertEquals(listOf(triggerRelPath), result.readopted)
            val failure = result.failed.single()
            assertEquals(victimRelPath, failure.relPath)
            assertTrue(failure.cause is IOException)
            assertTrue(victimMarker.readBytes().contentEquals(replacementBytes))
        }

    @Test
    fun `cleanup rejects a Windows junction marker parent without enumerating its referent`() = inTempDir { root ->
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            throw SkipException("Windows junction evidence runs only on Windows")
        }
        val property = File(root, "photos/property").also { assertTrue(it.mkdirs()) }
        val referent = kotlin.io.path.createTempDirectory("td14-junction-referent-").toFile()
        val asset = File(referent, "photo.jpg")
        PendingPhotoLease.acquire(asset).closeAfter(PendingPhotoLeaseDisposition.RETAIN)
        val junction = File(property, "inspection")
        val process = ProcessBuilder("cmd", "/c", "mklink", "/J", junction.path, referent.path)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (process.waitFor() != 0) {
            referent.deleteRecursively()
            throw SkipException("Windows junctions are unavailable: $output")
        }
        try {
            assertFailsWith<IllegalStateException> {
                PendingPhotoAssetCleanup(
                    mediaRoot = root,
                    findPhoto = { error("junction contents must never reach DB lookup") },
                    deleter = OrphanFileDeleter { error("junction contents must never reach deletion") },
                ).run()
            }
            assertTrue(File(referent, "photo.jpg.pending").isFile)
        } finally {
            Files.deleteIfExists(junction.toPath())
            referent.deleteRecursively()
        }
    }

    @Test
    fun `cleanup rejects a JPEG symlink and never invokes a deleter that would follow it`() = inTempDir { root ->
        val relPath = "photos/property/inspection/photo-linked-asset.jpg"
        val asset = File(root, relPath).also { assertTrue(it.parentFile!!.mkdirs()) }
        val protectedAsset = File(asset.parentFile, "protected.jpg").also { it.writeText("protected-evidence") }
        createSymlinkOrSkip(asset, protectedAsset)
        val marker = File(asset.parentFile, "photo-linked-asset.jpg.pending")
        PendingPhotoLease.acquire(asset).closeAfter(PendingPhotoLeaseDisposition.RETAIN)
        var deleteCalls = 0

        val result = PendingPhotoAssetCleanup(
            mediaRoot = root,
            findPhoto = { null },
            deleter = OrphanFileDeleter { path ->
                deleteCalls += 1
                val followed = File(root, path).canonicalFile
                followed.delete() || !followed.exists()
            },
        ).run()

        assertEquals(listOf(relPath), result.rejected)
        assertEquals(0, deleteCalls, "the shape-valid alias must fail before any follow-capable deleter")
        assertEquals("protected-evidence", protectedAsset.readText())
        assertTrue(Files.isSymbolicLink(asset.toPath()))
        assertTrue(marker.isFile, "unsafe state must retain its recovery marker")
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
    fun `environment primary with unknown lease close stays a path-level failure and fails its report closed`() =
        inTempDir { root ->
            val relPath = "photos/property/inspection/photo-mixed-failure.jpg"
            val asset = asset(root, relPath)
            val marker = File(asset.parentFile, "photo-mixed-failure.jpg.pending")
            PendingPhotoLease.acquire(asset).closeAfter(PendingPhotoLeaseDisposition.RETAIN)
            val primary = IOException("database temporarily unavailable")
            val closeFailure = IllegalStateException("lease close contract violated")

            val pending = PendingPhotoAssetCleanup.withLeaseOpener(
                mediaRoot = root,
                findPhoto = { throw primary },
                deleter = OrphanFileDeleter { error("deleter must not run after lookup failure") },
                openLease = { _, _ -> ThrowingLease(closeFailure) },
            ).run()

            val failed = pending.failed.single()
            assertEquals(relPath, failed.relPath)
            assertSame(primary, failed.cause)
            assertEquals(listOf(closeFailure), primary.suppressed.toList())
            assertTrue(marker.isFile)

            val report = PhotoOrphanCleanupReport.from(
                pending = pending,
                softDelete = CleanupResult(emptyList(), emptyList(), emptyList()),
            )
            assertEquals(PhotoOrphanCleanupDecision.FAILURE, report.decision)
            val issue = report.issues().single()
            assertEquals(PhotoOrphanCleanupBucket.PENDING, issue.bucket)
            assertEquals(PhotoOrphanCleanupIssueResult.FAILED, issue.result)
            assertEquals(relPath, issue.path)
            assertSame(primary, issue.cause)
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
                openLease = { _, _ -> ThrowingLease(closeFailure) },
            ).run()
        }

        assertSame(primary, thrown)
        assertEquals(listOf(closeFailure), primary.suppressed.toList())
        assertTrue(marker.isFile)
    }

    @Test
    fun `unexpected lease finalization failure after readoption propagates fail closed`() = inTempDir { root ->
        val relPath = "photos/property/inspection/photo-close-unknown.jpg"
        val asset = asset(root, relPath)
        val marker = File(asset.parentFile, "photo-close-unknown.jpg.pending")
        PendingPhotoLease.acquire(asset).closeAfter(PendingPhotoLeaseDisposition.RETAIN)
        val closeFailure = IllegalStateException("lease finalizer contract violated")

        val thrown = assertFailsWith<IllegalStateException> {
            PendingPhotoAssetCleanup.withLeaseOpener(
                mediaRoot = root,
                findPhoto = { PendingPhotoReference(relPath, active = true) },
                deleter = OrphanFileDeleter { error("readoption must not delete") },
                openLease = { _, _ -> ThrowingFinalizingLease(closeFailure) },
            ).run()
        }

        assertSame(closeFailure, thrown)
        assertTrue(marker.isFile, "unknown finalization failure must retain recovery evidence")
    }

    @Test
    fun `environment lease finalization failure after readoption remains a retry with its cause`() = inTempDir { root ->
        val relPath = "photos/property/inspection/photo-close-io.jpg"
        val asset = asset(root, relPath)
        val marker = File(asset.parentFile, "photo-close-io.jpg.pending")
        PendingPhotoLease.acquire(asset).closeAfter(PendingPhotoLeaseDisposition.RETAIN)
        val closeFailure = IOException("marker filesystem unavailable")

        val result = PendingPhotoAssetCleanup.withLeaseOpener(
            mediaRoot = root,
            findPhoto = { PendingPhotoReference(relPath, active = true) },
            deleter = OrphanFileDeleter { error("readoption must not delete") },
            openLease = { _, _ -> ThrowingFinalizingLease(closeFailure) },
        ).run()

        val failed = result.failed.single()
        assertEquals(relPath, failed.relPath)
        assertSame(closeFailure, failed.cause)
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

    private fun createSymlinkOrSkip(link: File, target: File) = try {
        Files.createSymbolicLink(link.toPath(), target.toPath())
        Unit
    } catch (failure: UnsupportedOperationException) {
        throw SkipException("symbolic links are unavailable on this platform", failure)
    } catch (failure: IOException) {
        throw SkipException("symbolic links are unavailable on this host", failure)
    } catch (failure: SecurityException) {
        throw SkipException("symbolic links are not permitted on this host", failure)
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

    private class ThrowingFinalizingLease(
        private val closeFailure: Throwable,
    ) : PendingPhotoLeaseHandle {
        override fun closeAfter(disposition: PendingPhotoLeaseDisposition): Boolean {
            throw closeFailure
        }

        override fun close() = Unit
    }
}

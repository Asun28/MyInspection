package nz.myinspection.core.media

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.testng.SkipException

class PendingPhotoLeaseTest {
    @Test
    fun `ingest rejects a pre-existing marker symlink without writing or deleting its referent`() = inTempDir { root ->
        val target = File(root, "photos/property/inspection/photo-linked-marker.jpg").also {
            assertTrue(it.parentFile!!.mkdirs())
        }
        val referent = File(root, "marker-referent").also { it.writeBytes(byteArrayOf()) }
        val marker = File(target.parentFile, "photo-linked-marker.jpg.pending")
        createSymlinkOrSkip(marker, referent)

        val failure = runCatching {
            PendingPhotoLease.acquire(target).closeAfter(PendingPhotoLeaseDisposition.RECORDED)
        }.exceptionOrNull()

        assertTrue(failure is IOException, "a marker link must fail before a lease can mutate it")
        assertTrue(Files.isSymbolicLink(marker.toPath()))
        assertEquals(0L, referent.length(), "the linked file must never receive the resolving sentinel")
    }

    @Test
    fun `recovery rejects content outside the fixed version state and token format`() = inTempDir { root ->
        val marker = File(root, "photos/property/inspection/photo-invalid.jpg.pending").also {
            assertTrue(it.parentFile!!.mkdirs())
            it.writeBytes(byteArrayOf(2))
        }

        val outcome = runCatching { PendingPhotoLease.openExisting(marker) }
        outcome.getOrNull()?.closeAfter(PendingPhotoLeaseDisposition.RETAIN)
        val thrown = outcome.exceptionOrNull()

        assertTrue(thrown is IOException)
        assertTrue(thrown.message.orEmpty().contains("content"))
        assertEquals(listOf<Byte>(2), marker.readBytes().toList())
    }

    @Test
    fun `legacy empty marker is retained and rejected instead of being adopted or rewritten`() = inTempDir { root ->
        val marker = File(root, "photos/property/inspection/photo-empty.jpg.pending").also {
            assertTrue(it.parentFile!!.mkdirs())
            assertTrue(it.createNewFile())
        }

        val thrown = assertFailsWith<IOException> { PendingPhotoLease.openExisting(marker) }

        assertTrue(thrown.message.orEmpty().contains("content"))
        assertEquals(0L, marker.length())
    }

    @Test
    fun `new marker persists the injected 128-bit token in its fixed normal-state format`() = inTempDir { root ->
        val target = File(root, "photos/property/inspection/photo-token.jpg").also {
            assertTrue(it.parentFile!!.mkdirs())
        }
        val marker = File(target.parentFile, "photo-token.jpg.pending")

        val lease = PendingPhotoLease.acquireWithDurability(
            target = target,
            forceMarker = { it.force(true) },
            syncParentDirectory = {},
            newToken = { ByteArray(16) { it.toByte() } },
        )

        lease.closeAfter(PendingPhotoLeaseDisposition.RETAIN)
        assertEquals("MIP1:N:000102030405060708090a0b0c0d0e0f", marker.readText())
    }

    @Test
    fun `recorded close retains a path replacement with a different valid marker token`() = inTempDir { root ->
        val parent = File(root, "photos/property/inspection").also { assertTrue(it.mkdirs()) }
        val target = File(parent, "photo-original.jpg")
        val replacementTarget = File(parent, "photo-replacement.jpg")
        val marker = File(parent, "photo-original.jpg.pending")
        val replacementMarker = File(parent, "photo-replacement.jpg.pending")
        val original = PendingPhotoLease.acquireWithDurability(
            target,
            forceMarker = { it.force(true) },
            syncParentDirectory = {},
            newToken = { ByteArray(16) { 0x11 } },
        )
        PendingPhotoLease.acquireWithDurability(
            replacementTarget,
            forceMarker = { it.force(true) },
            syncParentDirectory = {},
            newToken = { ByteArray(16) { 0x22 } },
        ).closeAfter(PendingPhotoLeaseDisposition.RETAIN)
        val replacementBytes = replacementMarker.readBytes()
        Files.move(replacementMarker.toPath(), marker.toPath(), REPLACE_EXISTING)

        assertFalse(original.closeAfter(PendingPhotoLeaseDisposition.RECORDED))
        assertTrue(marker.isFile, "a replaced path is not the lease's marker and must be retained")
        assertTrue(marker.readBytes().contentEquals(replacementBytes))
    }

    @Test
    fun `acquire forces the marker then syncs its parent while the lease is exclusive`() = inTempDir { root ->
        val target = File(root, "photos/property/inspection/photo-durable.jpg").also {
            assertTrue(it.parentFile!!.mkdirs())
        }
        val marker = File(target.parentFile, "photo-durable.jpg.pending")
        val events = mutableListOf<String>()

        val lease = PendingPhotoLease.acquireWithDurability(
            target = target,
            forceMarker = { channel ->
                events += "force-marker"
                channel.force(true)
            },
            syncParentDirectory = { parent ->
                events += "sync-parent"
                assertEquals(target.parentFile!!.canonicalFile, parent.canonicalFile)
                assertTrue(marker.isFile, "the new directory entry must exist before its parent is synced")
                val competing = runCatching { PendingPhotoLease.acquire(target) }
                competing.getOrNull()?.closeAfter(PendingPhotoLeaseDisposition.RETAIN)
                assertTrue(
                    competing.exceptionOrNull() is IOException,
                    "the parent sync must run before return while the marker remains exclusively leased",
                )
            },
        )

        assertEquals(listOf("force-marker", "sync-parent"), events)
        lease.closeAfter(PendingPhotoLeaseDisposition.RETAIN)
    }

    @Test
    fun `acquire syncs the complete photo directory chain deepest first after forcing the marker`() = inTempDir { root ->
        val durabilityRoot = File(root, "existing-root").also { assertTrue(it.mkdir()) }
        val target = File(durabilityRoot, "media/photos/property/inspection/photo-chain.jpg")
        val events = mutableListOf<String>()

        val lease = PendingPhotoLease.acquireWithDurability(
            target = target,
            durabilityRoot = durabilityRoot,
            forceMarker = { channel ->
                events += "force-marker"
                channel.force(true)
            },
            syncParentDirectory = { directory ->
                events += durabilityRoot.toPath().relativize(directory.toPath()).toString().replace('\\', '/')
            },
        )

        assertEquals(
            listOf(
                "force-marker",
                "media/photos/property/inspection",
                "media/photos/property",
                "media/photos",
                "media",
                "",
            ),
            events,
        )
        lease.closeAfter(PendingPhotoLeaseDisposition.RETAIN)
    }

    @Test
    fun `parent directory sync failure aborts acquire with its cause and releases the marker`() = inTempDir { root ->
        val target = File(root, "photos/property/inspection/photo-sync-fails.jpg").also {
            assertTrue(it.parentFile!!.mkdirs())
        }
        val failure = IOException("directory fsync failed")

        val thrown = assertFailsWith<IOException> {
            PendingPhotoLease.acquireWithDurability(
                target = target,
                forceMarker = { it.force(true) },
                syncParentDirectory = { throw failure },
            )
        }

        assertSame(failure, thrown)
        PendingPhotoLease.acquire(target).closeAfter(PendingPhotoLeaseDisposition.RETAIN)
    }

    @Test
    fun `completed compensated result survives JPEG parent sync failure and retains a released marker`() = inTempDir { root ->
        val target = File(root, "photos/property/inspection/photo-compensated-sync.jpg").also {
            assertTrue(it.parentFile!!.mkdirs())
        }
        val marker = File(target.parentFile, "photo-compensated-sync.jpg.pending")
        val syncFailure = IOException("compensated JPEG parent fsync failed")
        var reportedFailure: Throwable? = null

        val result = VerifiedAssetWorkflow.encodeStagePublishRecord(
            target = target,
            input = Unit,
            encoder = StreamEncoder { _, output -> output.write(byteArrayOf(1, 2, 3)) },
            plan = { "compensated" },
            shouldPublish = { true },
            publicationLease = {
                val heldLease = PendingPhotoLease.acquire(target)
                object : PublicationLease<String> {
                    private var disposition = PendingPhotoLeaseDisposition.RETAIN

                    override fun finish(result: String) {
                        disposition = PendingPhotoLeaseDisposition.REJECTED_WITHOUT_ORPHAN
                    }

                    override fun close() {
                        heldLease.closeAfterAssetDeletion(disposition) { throw syncFailure }
                    }

                    override fun onCompletedCleanupFailure(failure: Throwable) {
                        reportedFailure = failure
                    }
                }
            },
            publish = { _, _ -> Unit },
            record = { "compensated" },
        )

        assertEquals("compensated", result, "a completed DB result must not be replaced by cleanup durability")
        assertSame(syncFailure, reportedFailure)
        assertTrue(marker.isFile, "failed delete durability must retain recovery evidence")
        PendingPhotoLease.acquire(target).closeAfter(PendingPhotoLeaseDisposition.RETAIN)
    }

    @Test
    fun `environment marker finalization failure is preserved and releases the lease for recovery`() = inTempDir { root ->
        val target = File(root, "photos/property/inspection/photo-finalize-io.jpg").also {
            assertTrue(it.parentFile!!.mkdirs())
        }
        val failure = IOException("marker write failed")
        val lease = PendingPhotoLease.acquireWithDurability(
            target = target,
            forceMarker = { it.force(true) },
            syncParentDirectory = {},
            finalizeMarker = { throw failure },
        )

        val thrown = assertFailsWith<IOException> {
            lease.closeAfter(PendingPhotoLeaseDisposition.RECORDED)
        }

        assertSame(failure, thrown)
        PendingPhotoLease.acquire(target).closeAfter(PendingPhotoLeaseDisposition.RETAIN)
    }

    @Test
    fun `unknown marker finalization failure propagates fail closed and releases the lease`() = inTempDir { root ->
        val target = File(root, "photos/property/inspection/photo-finalize-unknown.jpg").also {
            assertTrue(it.parentFile!!.mkdirs())
        }
        val failure = IllegalStateException("marker finalizer contract violated")
        val lease = PendingPhotoLease.acquireWithDurability(
            target = target,
            forceMarker = { it.force(true) },
            syncParentDirectory = {},
            finalizeMarker = { throw failure },
        )

        val thrown = assertFailsWith<IllegalStateException> {
            lease.closeAfter(PendingPhotoLeaseDisposition.RECORDED)
        }

        assertSame(failure, thrown)
        PendingPhotoLease.acquire(target).closeAfter(PendingPhotoLeaseDisposition.RETAIN)
    }

    @Test
    fun `acquire keeps one durable sidecar exclusively locked until a recorded result clears it`() = inTempDir { root ->
        val target = File(root, "photos/property/inspection/photo-1.jpg").also {
            assertTrue(it.parentFile!!.mkdirs())
        }
        val marker = File(target.parentFile, "photo-1.jpg.pending")

        val first = PendingPhotoLease.acquire(target)
        assertTrue(marker.isFile, "the marker must exist before any publisher can use the target")
        assertFailsWith<IOException>("another ingest must not share the same marker lease") {
            PendingPhotoLease.acquire(target)
        }

        first.closeAfter(PendingPhotoLeaseDisposition.RETAIN)
        val initialMarker = marker.readBytes()
        assertEquals(39, initialMarker.size, "the marker stores a fixed version, state and 128-bit token")
        assertTrue(marker.isFile, "a failed or unfinished record must leave its recovery marker behind")
        assertTrue(
            marker.readBytes().contentEquals(initialMarker),
            "reopening an existing normal marker must never truncate or rewrite its token",
        )

        val reopened = PendingPhotoLease.acquire(target)
        reopened.closeAfter(PendingPhotoLeaseDisposition.RECORDED)
        assertFalse(marker.exists(), "a successfully recorded photo must no longer be recoverable as an orphan")
    }

    @Test
    fun `acquire rejects a normal marker that becomes resolving before its exclusive lock`() = inTempDir { root ->
        val target = File(root, "photos/property/inspection/photo-lock-race.jpg").also {
            assertTrue(it.parentFile!!.mkdirs())
        }
        PendingPhotoLease.acquire(target).closeAfter(PendingPhotoLeaseDisposition.RETAIN)

        val outcome = runCatching {
            PendingPhotoLease.acquireWithDurability(
                target = target,
                forceMarker = { it.force(true) },
                syncParentDirectory = {},
                beforeExistingMarkerLock = { channel ->
                    channel.write(ByteBuffer.wrap(byteArrayOf('R'.code.toByte())), 5L)
                    channel.force(true)
                },
            )
        }
        outcome.getOrNull()?.closeAfter(PendingPhotoLeaseDisposition.RETAIN)

        val failure = outcome.exceptionOrNull()
        assertTrue(failure is IOException)
        assertTrue(
            failure.message.orEmpty().contains("changed after lock"),
            "a contender must fail specifically because its locked marker state was revalidated: $failure",
        )
    }

    @Test
    fun `confirmed compensation clears the marker but an untracked rejection retains it`() = inTempDir { root ->
        val target = File(root, "photos/property/inspection/photo-2.jpg").also {
            assertTrue(it.parentFile!!.mkdirs())
        }
        val marker = File(target.parentFile, "photo-2.jpg.pending")

        PendingPhotoLease.acquire(target).closeAfter(PendingPhotoLeaseDisposition.REJECTED_WITHOUT_ORPHAN)
        assertFalse(marker.exists(), "a confirmed compensation leaves no JPEG for recovery and must clear the marker")

        PendingPhotoLease.acquire(target).closeAfter(PendingPhotoLeaseDisposition.RETAIN)
        assertTrue(marker.isFile, "a compensation failure must remain visible to the recovery worker")
    }

    @Test
    fun `a resolving marker rejects a new ingest until the recovery worker clears it`() = inTempDir { root ->
        val target = File(root, "photos/property/inspection/photo-3.jpg").also {
            assertTrue(it.parentFile!!.mkdirs())
        }
        val marker = File(target.parentFile, "photo-3.jpg.pending")
        PendingPhotoLease.acquire(target).closeAfter(PendingPhotoLeaseDisposition.RETAIN)
        val lease = PendingPhotoLease.acquire(target)
        RandomAccessFile(marker, "rw").use {
            assertFalse(lease.closeAfter(PendingPhotoLeaseDisposition.RECORDED))
        }

        assertFailsWith<IOException>("a fresh ingest must not publish through a marker being resolved") {
            PendingPhotoLease.acquire(target)
        }
        assertTrue(marker.readText().startsWith("MIP1:R:"), "the resolving state must retain the same durable token")
    }

    @Test
    fun `marker delete failure leaves a resolving sidecar without replacing the recorded outcome`() = inTempDir { root ->
        val target = File(root, "photos/property/inspection/photo-4.jpg").also {
            assertTrue(it.parentFile!!.mkdirs())
        }
        val marker = File(target.parentFile, "photo-4.jpg.pending")
        val lease = PendingPhotoLease.acquire(target)

        RandomAccessFile(marker, "rw").use {
            assertFalse(
                lease.closeAfter(PendingPhotoLeaseDisposition.RECORDED),
                "a cleanup delete failure must report retained recovery state, not throw over the completed record",
            )
            assertTrue(marker.isFile)
            assertTrue(marker.length() > 0L, "the handoff sentinel blocks a racing ingest while deletion is pending")
            assertFailsWith<IOException> { PendingPhotoLease.acquire(target) }
        }
    }

    private fun inTempDir(block: (File) -> Unit) {
        val root = kotlin.io.path.createTempDirectory("td14-lease-").toFile()
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
}

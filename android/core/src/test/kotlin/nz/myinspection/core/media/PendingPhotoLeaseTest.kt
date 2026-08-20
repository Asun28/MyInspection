package nz.myinspection.core.media

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PendingPhotoLeaseTest {
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
        assertEquals(0L, marker.length(), "initial marker creation must be an empty durable sidecar")
        assertFailsWith<IOException>("another ingest must not share the same marker lease") {
            PendingPhotoLease.acquire(target)
        }

        first.closeAfter(PendingPhotoLeaseDisposition.RETAIN)
        assertTrue(marker.isFile, "a failed or unfinished record must leave its recovery marker behind")
        assertEquals(0L, marker.length(), "reopening an existing normal marker must never truncate or rewrite it")

        val reopened = PendingPhotoLease.acquire(target)
        reopened.closeAfter(PendingPhotoLeaseDisposition.RECORDED)
        assertFalse(marker.exists(), "a successfully recorded photo must no longer be recoverable as an orphan")
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
        marker.writeBytes(byteArrayOf(1))

        assertFailsWith<IOException>("a fresh ingest must not publish through a marker being resolved") {
            PendingPhotoLease.acquire(target)
        }
        assertEquals(1L, marker.length(), "the resolving state must remain durable for the worker")
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
}

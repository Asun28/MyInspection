package nz.myinspection.core.media

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

/** The only outcomes that may remove a pending sidecar after an ingest attempt. */
enum class PendingPhotoLeaseDisposition(internal val clearsMarker: Boolean) {
    RETAIN(false),
    RECORDED(true),
    REJECTED_WITHOUT_ORPHAN(true),
}

/** Internal recovery port so cleanup tests can exercise close-primary suppression without a platform file fault. */
internal interface PendingPhotoLeaseHandle : AutoCloseable {
    fun closeAfter(disposition: PendingPhotoLeaseDisposition): Boolean
}

/**
 * A durable `<photoId>.jpg.pending` sidecar and its non-blocking exclusive file lock.
 *
 * The sidecar is deliberately retained after an interrupted ingest so the periodic cleanup worker can decide whether
 * the JPEG was ever adopted. Re-opening an existing sidecar is intentional and never truncates it: retrying the same
 * photo id stays idempotent, while the lock prevents two live callers from publishing beside each other.
 */
class PendingPhotoLease private constructor(
    val marker: File,
    private val channel: FileChannel,
    private val lock: FileLock,
    private val finalizeMarker: (FileChannel) -> Unit,
) : PendingPhotoLeaseHandle {
    companion object {
        private val RESOLVING_MARKER = byteArrayOf(1)

        fun acquire(target: File): PendingPhotoLease = acquire(target) {}

        fun acquire(target: File, syncParentDirectory: (File) -> Unit): PendingPhotoLease =
            acquireWithDurability(
                target = target,
                forceMarker = { channel -> channel.force(true) },
                syncParentDirectory = syncParentDirectory,
                finalizeMarker = ::writeResolvingMarker,
            )

        internal fun acquireWithDurability(
            target: File,
            forceMarker: (FileChannel) -> Unit,
            syncParentDirectory: (File) -> Unit,
            finalizeMarker: (FileChannel) -> Unit = ::writeResolvingMarker,
        ): PendingPhotoLease {
            val parent = checkNotNull(target.parentFile) { "photo target has no parent: ${target.path}" }
            if (!parent.exists() && !parent.mkdirs() && !parent.isDirectory) {
                throw IOException("could not create photo parent: ${parent.path}")
            }
            return open(
                marker = File(parent, "${target.name}.pending"),
                createIfMissing = true,
                forceMarker = forceMarker,
                syncParentDirectory = syncParentDirectory,
                finalizeMarker = finalizeMarker,
            )
        }

        /** Recovery may take a marker already marked for removal; a fresh ingest must not. */
        internal fun openExisting(marker: File): PendingPhotoLease =
            open(
                marker = marker,
                createIfMissing = false,
                allowResolvingMarker = true,
                forceMarker = { channel -> channel.force(true) },
                syncParentDirectory = {},
                finalizeMarker = ::writeResolvingMarker,
            )

        private fun open(
            marker: File,
            createIfMissing: Boolean,
            allowResolvingMarker: Boolean = false,
            forceMarker: (FileChannel) -> Unit,
            syncParentDirectory: (File) -> Unit,
            finalizeMarker: (FileChannel) -> Unit,
        ): PendingPhotoLease {
            if (createIfMissing && !marker.createNewFile() && !marker.isFile) {
                throw IOException("pending photo marker is not a file: ${marker.path}")
            }
            if (!marker.isFile) throw IOException("pending photo marker is missing: ${marker.path}")

            var channel: FileChannel? = null
            var lock: FileLock? = null
            try {
                channel = RandomAccessFile(marker, "rw").channel
                forceMarker(channel)
                lock = try {
                    channel.tryLock()
                } catch (_: OverlappingFileLockException) {
                    null
                }
                if (lock == null) throw IOException("pending photo marker is locked: ${marker.path}")
                if (!marker.isFile) throw IOException("pending photo marker disappeared while acquiring: ${marker.path}")
                if (!allowResolvingMarker && channel.size() != 0L) {
                    throw IOException("pending photo marker is resolving: ${marker.path}")
                }
                syncParentDirectory(checkNotNull(marker.parentFile))
                return PendingPhotoLease(marker, channel, lock, finalizeMarker)
            } catch (primary: Throwable) {
                closeQuietly(lock, channel, primary)
                throw primary
            }
        }

        private fun closeQuietly(lock: FileLock?, channel: FileChannel?, primary: Throwable) {
            try {
                lock?.release()
            } catch (cleanupFailure: Throwable) {
                primary.addSuppressed(cleanupFailure)
            }
            try {
                channel?.close()
            } catch (cleanupFailure: Throwable) {
                primary.addSuppressed(cleanupFailure)
            }
        }

        private fun writeResolvingMarker(channel: FileChannel) {
            channel.truncate(0)
            channel.position(0)
            val marker = ByteBuffer.wrap(RESOLVING_MARKER)
            while (marker.hasRemaining()) {
                check(channel.write(marker) > 0) { "could not write pending marker handoff" }
            }
            channel.force(true)
        }
    }

    /**
     * Marks removal durably while still locked, then releases the lock and deletes the marker. The marker's temporary
     * nonempty state makes a new ingest retry instead of acquiring it during the unlock/delete gap; recovery workers
     * may open that state and finish cleanup. Expected filesystem failures retain recovery state for the caller to
     * retry; unknown failures propagate fail closed.
     */
    override fun closeAfter(disposition: PendingPhotoLeaseDisposition): Boolean {
        if (!disposition.clearsMarker) {
            close()
            return true
        }
        var primary: Throwable? = null
        try {
            finalizeMarker(channel)
        } catch (failure: Throwable) {
            primary = failure
        }
        try {
            close()
        } catch (closeFailure: Throwable) {
            val failure = primary
            if (failure == null) primary = closeFailure else failure.addSuppressed(closeFailure)
        }
        primary?.let { throw it }
        return deleteIfPresent(marker)
    }

    override fun close() {
        var primary: Throwable? = null
        try {
            if (lock.isValid) lock.release()
        } catch (failure: Throwable) {
            primary = failure
        }
        try {
            channel.close()
        } catch (failure: Throwable) {
            val existing = primary
            if (existing == null) primary = failure else existing.addSuppressed(failure)
        }
        primary?.let { throw it }
    }

    private fun deleteIfPresent(file: File): Boolean = file.delete() || !file.exists()

}

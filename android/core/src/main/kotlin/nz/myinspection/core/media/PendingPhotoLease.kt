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
) : PendingPhotoLeaseHandle {
    companion object {
        private val RESOLVING_MARKER = byteArrayOf(1)

        fun acquire(target: File): PendingPhotoLease {
            val parent = checkNotNull(target.parentFile) { "photo target has no parent: ${target.path}" }
            if (!parent.exists() && !parent.mkdirs() && !parent.isDirectory) {
                throw IOException("could not create photo parent: ${parent.path}")
            }
            return open(File(parent, "${target.name}.pending"), createIfMissing = true)
        }

        /** Recovery may take a marker already marked for removal; a fresh ingest must not. */
        internal fun openExisting(marker: File): PendingPhotoLease =
            open(marker, createIfMissing = false, allowResolvingMarker = true)

        private fun open(marker: File, createIfMissing: Boolean): PendingPhotoLease =
            open(marker, createIfMissing, allowResolvingMarker = false)

        private fun open(
            marker: File,
            createIfMissing: Boolean,
            allowResolvingMarker: Boolean,
        ): PendingPhotoLease {
            if (createIfMissing && !marker.createNewFile() && !marker.isFile) {
                throw IOException("pending photo marker is not a file: ${marker.path}")
            }
            if (!marker.isFile) throw IOException("pending photo marker is missing: ${marker.path}")

            var channel: FileChannel? = null
            var lock: FileLock? = null
            try {
                channel = RandomAccessFile(marker, "rw").channel
                channel.force(true)
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
                return PendingPhotoLease(marker, channel, lock)
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
    }

    /**
     * Marks removal durably while still locked, then releases the lock and deletes the marker. The marker's temporary
     * nonempty state makes a new ingest retry instead of acquiring it during the unlock/delete gap; recovery workers
     * may open that state and finish cleanup. A marker cleanup failure is deliberately non-fatal and leaves recovery
     * state behind.
     */
    override fun closeAfter(disposition: PendingPhotoLeaseDisposition): Boolean {
        if (!disposition.clearsMarker) {
            close()
            return true
        }
        if (!markResolving()) {
            close()
            return false
        }
        close()
        return deleteIfPresent(marker)
    }

    private fun markResolving(): Boolean = try {
        channel.truncate(0)
        channel.position(0)
        val marker = ByteBuffer.wrap(RESOLVING_MARKER)
        while (marker.hasRemaining()) {
            check(channel.write(marker) > 0) { "could not write pending marker handoff" }
        }
        channel.force(true)
        true
    } catch (_: Throwable) {
        false
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

    private fun deleteIfPresent(file: File): Boolean = try {
        file.delete() || !file.exists()
    } catch (_: SecurityException) {
        false
    }

}

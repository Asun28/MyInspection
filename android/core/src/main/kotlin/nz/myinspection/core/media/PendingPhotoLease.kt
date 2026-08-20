package nz.myinspection.core.media

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.StandardCharsets.US_ASCII
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.BasicFileAttributes
import java.security.SecureRandom

internal enum class PendingPhotoMarkerState(val code: Char) {
    NORMAL('N'),
    RESOLVING('R'),
}

internal data class PendingPhotoMarkerIdentity(
    val state: PendingPhotoMarkerState,
    val tokenHex: String,
) {
    fun resolving(): PendingPhotoMarkerIdentity = copy(state = PendingPhotoMarkerState.RESOLVING)
}

internal class PendingPhotoMarkerFormatException(message: String) : IOException(message)

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
 * A newly-created marker contains a fixed version/state header and a random 128-bit token. That persistent token binds
 * scan, channel open and final path deletion even on file systems whose Java provider exposes no stable `fileKey`.
 * Existing normal markers are intentionally reopened without truncation so retrying the same photo id is idempotent;
 * a resolving marker is recovery-only. The lock prevents two live callers from publishing beside each other.
 */
class PendingPhotoLease private constructor(
    val marker: File,
    private val identity: PendingPhotoMarkerIdentity,
    private val channel: FileChannel,
    private val lock: FileLock,
    private val finalizeMarker: (FileChannel) -> Unit,
) : PendingPhotoLeaseHandle {
    companion object {
        private const val MARKER_PREFIX = "MIP1:"
        private const val TOKEN_BYTES = 16
        private const val TOKEN_HEX_LENGTH = TOKEN_BYTES * 2
        private const val MARKER_LENGTH = 39
        private val secureRandom = SecureRandom()

        internal fun acquire(target: File): PendingPhotoLease = acquire(target) {}

        internal fun acquire(target: File, syncParentDirectory: (File) -> Unit): PendingPhotoLease =
            acquire(target, checkNotNull(target.parentFile), syncParentDirectory)

        fun acquire(
            target: File,
            durabilityRoot: File,
            syncParentDirectory: (File) -> Unit,
        ): PendingPhotoLease =
            acquireWithDurability(
                target = target,
                forceMarker = { channel -> channel.force(true) },
                syncParentDirectory = syncParentDirectory,
                durabilityRoot = durabilityRoot,
            )

        internal fun acquireWithDurability(
            target: File,
            forceMarker: (FileChannel) -> Unit,
            syncParentDirectory: (File) -> Unit,
            durabilityRoot: File = checkNotNull(target.parentFile),
            finalizeMarker: ((FileChannel) -> Unit)? = null,
            newToken: () -> ByteArray = ::randomToken,
            beforeExistingMarkerLock: (FileChannel) -> Unit = {},
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
                durabilityRoot = durabilityRoot,
                finalizeMarker = finalizeMarker,
                newToken = newToken,
                expectedIdentity = null,
                beforeExistingMarkerLock = beforeExistingMarkerLock,
            )
        }

        /** Recovery may take a marker already marked for removal; a fresh ingest must not. */
        internal fun openExisting(
            marker: File,
            expectedIdentity: PendingPhotoMarkerIdentity? = null,
        ): PendingPhotoLease =
            open(
                marker = marker,
                createIfMissing = false,
                allowResolvingMarker = true,
                forceMarker = { channel -> channel.force(true) },
                syncParentDirectory = {},
                durabilityRoot = checkNotNull(marker.parentFile),
                finalizeMarker = null,
                newToken = ::randomToken,
                expectedIdentity = expectedIdentity,
                beforeExistingMarkerLock = {},
            )

        /** Scan-bound identity is content-backed because Windows' default JDK provider returns a null fileKey. */
        internal fun scanIdentity(marker: File): PendingPhotoMarkerIdentity =
            readPathIdentity(marker, allowResolvingMarker = true)
                ?: throw IOException("pending photo marker disappeared while scanning: ${marker.path}")

        internal fun confirmMalformed(marker: File, channel: FileChannel) {
            try {
                readChannelIdentity(marker, channel, allowResolvingMarker = true)
            } catch (_: PendingPhotoMarkerFormatException) {
                return
            }
            throw IOException("pending photo marker changed after malformed scan: ${marker.path}")
        }

        private fun open(
            marker: File,
            createIfMissing: Boolean,
            allowResolvingMarker: Boolean = false,
            forceMarker: (FileChannel) -> Unit,
            syncParentDirectory: (File) -> Unit,
            durabilityRoot: File,
            finalizeMarker: ((FileChannel) -> Unit)?,
            newToken: () -> ByteArray,
            expectedIdentity: PendingPhotoMarkerIdentity?,
            beforeExistingMarkerLock: (FileChannel) -> Unit,
        ): PendingPhotoLease {
            val path = marker.toPath()
            var identityBeforeOpen = readPathIdentity(marker, allowResolvingMarker)
            if (identityBeforeOpen == null && !createIfMissing) {
                throw IOException("pending photo marker is missing: ${marker.path}")
            }
            if (expectedIdentity != null && identityBeforeOpen != expectedIdentity) {
                throw IOException("pending photo marker changed since scan: ${marker.path}")
            }

            var channel: FileChannel? = null
            var lock: FileLock? = null
            try {
                var createdIdentity: PendingPhotoMarkerIdentity? = null
                if (identityBeforeOpen == null) {
                    val token = newToken()
                    require(token.size == TOKEN_BYTES) { "pending marker token must be 128 bits" }
                    createdIdentity = PendingPhotoMarkerIdentity(PendingPhotoMarkerState.NORMAL, token.toLowerHex())
                    try {
                        channel = FileChannel.open(path, READ, WRITE, CREATE_NEW, NOFOLLOW_LINKS)
                        lock = acquireExclusiveLock(marker, channel)
                        writeNewMarker(channel, createdIdentity)
                    } catch (_: FileAlreadyExistsException) {
                        createdIdentity = null
                        identityBeforeOpen = readPathIdentity(marker, allowResolvingMarker)
                            ?: throw IOException("pending photo marker raced with creation: ${marker.path}")
                        channel = FileChannel.open(path, READ, WRITE, NOFOLLOW_LINKS)
                    }
                } else {
                    channel = FileChannel.open(path, READ, WRITE, NOFOLLOW_LINKS)
                }

                forceMarker(channel)
                val openedIdentity = readChannelIdentity(marker, channel, allowResolvingMarker)
                val expectedOpenedIdentity = createdIdentity ?: identityBeforeOpen
                if (openedIdentity != expectedOpenedIdentity || (expectedIdentity != null && openedIdentity != expectedIdentity)) {
                    throw IOException("pending photo marker changed while acquiring: ${marker.path}")
                }
                if (createdIdentity == null) {
                    if (readPathIdentity(marker, allowResolvingMarker) != openedIdentity) {
                        throw IOException("pending photo marker path changed while acquiring: ${marker.path}")
                    }
                    beforeExistingMarkerLock(channel)
                    lock = acquireExclusiveLock(marker, channel)
                    val lockedIdentity = readChannelIdentity(marker, channel, allowResolvingMarker = true)
                    if (lockedIdentity != openedIdentity ||
                        (!allowResolvingMarker && lockedIdentity.state == PendingPhotoMarkerState.RESOLVING) ||
                        (expectedIdentity != null && lockedIdentity != expectedIdentity)
                    ) {
                        throw IOException("pending photo marker changed after lock: ${marker.path}")
                    }
                } else {
                    val createdAttributes = readMarkerAttributes(marker)
                        ?: throw IOException("pending photo marker disappeared while acquiring: ${marker.path}")
                    requireRegularMarker(marker, createdAttributes)
                }
                syncDirectoryChain(checkNotNull(marker.parentFile), durabilityRoot, syncParentDirectory)
                val markerFinalizer = finalizeMarker ?: { heldChannel: FileChannel ->
                    writeResolvingState(heldChannel)
                    heldChannel.force(true)
                }
                return PendingPhotoLease(marker, openedIdentity, channel, checkNotNull(lock), markerFinalizer)
            } catch (primary: Throwable) {
                closeQuietly(lock, channel, primary)
                throw primary
            }
        }

        private fun syncDirectoryChain(
            deepest: File,
            durabilityRoot: File,
            syncDirectory: (File) -> Unit,
        ) {
            val root = durabilityRoot.canonicalFile
            var current = deepest.canonicalFile
            if (current != root && !current.toPath().startsWith(root.toPath())) {
                throw IOException("photo directory is outside durability root: ${current.path}")
            }
            while (true) {
                syncDirectory(current)
                if (current == root) return
                current = current.parentFile
                    ?: throw IOException("durability root is not an ancestor of photo directory: ${root.path}")
            }
        }

        private fun readPathIdentity(
            marker: File,
            allowResolvingMarker: Boolean,
        ): PendingPhotoMarkerIdentity? {
            val attributes = readMarkerAttributes(marker) ?: return null
            requireRegularMarker(marker, attributes)
            return FileChannel.open(marker.toPath(), READ, NOFOLLOW_LINKS).use { channel ->
                readChannelIdentity(marker, channel, allowResolvingMarker)
            }
        }

        private fun readMarkerAttributes(marker: File): BasicFileAttributes? = try {
            Files.readAttributes(marker.toPath(), BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        } catch (_: NoSuchFileException) {
            null
        }

        private fun requireRegularMarker(marker: File, attributes: BasicFileAttributes) {
            if (!attributes.isRegularFile || attributes.isSymbolicLink || attributes.isOther) {
                throw PendingPhotoMarkerFormatException("pending photo marker is not a regular no-follow file: ${marker.path}")
            }
        }

        private fun readChannelIdentity(
            marker: File,
            channel: FileChannel,
            allowResolvingMarker: Boolean,
        ): PendingPhotoMarkerIdentity {
            if (channel.size() != MARKER_LENGTH.toLong()) {
                throw PendingPhotoMarkerFormatException("pending photo marker has invalid content: ${marker.path}")
            }
            val buffer = ByteBuffer.allocate(MARKER_LENGTH)
            var offset = 0L
            while (buffer.hasRemaining()) {
                val read = channel.read(buffer, offset)
                if (read <= 0) throw PendingPhotoMarkerFormatException("pending photo marker has invalid content: ${marker.path}")
                offset += read
            }
            val content = String(buffer.array(), US_ASCII)
            if (!content.startsWith(MARKER_PREFIX) || content[6] != ':') {
                throw PendingPhotoMarkerFormatException("pending photo marker has invalid content: ${marker.path}")
            }
            val state = when (content[5]) {
                PendingPhotoMarkerState.NORMAL.code -> PendingPhotoMarkerState.NORMAL
                PendingPhotoMarkerState.RESOLVING.code -> PendingPhotoMarkerState.RESOLVING
                else -> throw PendingPhotoMarkerFormatException("pending photo marker has invalid state: ${marker.path}")
            }
            if (state == PendingPhotoMarkerState.RESOLVING && !allowResolvingMarker) {
                throw PendingPhotoMarkerFormatException("pending photo marker is being resolved: ${marker.path}")
            }
            val tokenHex = content.substring(7)
            if (tokenHex.length != TOKEN_HEX_LENGTH || tokenHex.any { it !in '0'..'9' && it !in 'a'..'f' }) {
                throw PendingPhotoMarkerFormatException("pending photo marker has invalid token: ${marker.path}")
            }
            return PendingPhotoMarkerIdentity(state, tokenHex)
        }

        private fun writeNewMarker(channel: FileChannel, identity: PendingPhotoMarkerIdentity) {
            val bytes = "$MARKER_PREFIX${identity.state.code}:${identity.tokenHex}".toByteArray(US_ASCII)
            check(bytes.size == MARKER_LENGTH) { "pending marker format changed unexpectedly" }
            check(channel.size() == 0L) { "new pending marker was not empty" }
            writeFully(channel, ByteBuffer.wrap(bytes), offset = 0L)
        }

        private fun writeResolvingState(channel: FileChannel) {
            writeFully(channel, ByteBuffer.wrap(byteArrayOf(PendingPhotoMarkerState.RESOLVING.code.code.toByte())), offset = 5L)
        }

        private fun writeFully(channel: FileChannel, buffer: ByteBuffer, offset: Long) {
            var position = offset
            while (buffer.hasRemaining()) {
                val written = channel.write(buffer, position)
                check(written > 0) { "could not write pending marker handoff" }
                position += written
            }
        }

        private fun acquireExclusiveLock(marker: File, channel: FileChannel): FileLock {
            val acquired = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            return acquired ?: throw IOException("pending photo marker is locked: ${marker.path}")
        }

        private fun deleteVerifiedMarker(marker: File, expected: PendingPhotoMarkerIdentity): Boolean {
            val found = try {
                readPathIdentity(marker, allowResolvingMarker = true)
            } catch (_: PendingPhotoMarkerFormatException) {
                return false
            }
            if (found == null) return true
            if (found != expected) return false
            return marker.delete() || readMarkerAttributes(marker) == null
        }

        private fun randomToken(): ByteArray = ByteArray(TOKEN_BYTES).also(secureRandom::nextBytes)

        private fun ByteArray.toLowerHex(): String = buildString(size * 2) {
            for (byte in this@toLowerHex) {
                val value = byte.toInt() and 0xff
                append("0123456789abcdef"[value ushr 4])
                append("0123456789abcdef"[value and 0x0f])
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
     * Marks removal durably while still locked, releases the lock, then reopens the path without following links and
     * deletes it only if the same token is still in resolving state. A replacement is retained and reported as false.
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
        return deleteVerifiedMarker(marker, identity.resolving())
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
}

package nz.myinspection.app.platform

import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Envelope files in the credential-encrypted no-backup secret-envelope directory. Every call asks [policy] for that
 * directory, so the path boundary is checked each time. [replace] writes a temporary file there, syncs it and moves it
 * atomically over the envelope, so a failure leaves the previous envelope in place. Every ordinary exception becomes an
 * [EnvelopeStoreException] with one fixed message and no cause or suppressed exception; an Error propagates unchanged.
 */
class LocalSecretEnvelopeStore internal constructor(
    private val policy: AppStoragePolicy,
    private val files: EnvelopeFileOperations,
) : SecretEnvelopeFiles {
    constructor(policy: AppStoragePolicy) : this(policy, PlatformEnvelopeFileOperations)

    override fun read(purpose: SecretPurpose): ByteArray? {
        val file = stage(EnvelopeStoreException.Stage.LOCATE) { envelopeFile(purpose) }
        return stage(EnvelopeStoreException.Stage.READ) {
            try {
                Files.newInputStream(file.toPath()).use { input ->
                    val buffer = ByteArray(MAX_ENVELOPE_BYTES + 1)
                    var count = 0
                    while (count < buffer.size) {
                        val read = input.read(buffer, count, buffer.size - count)
                        if (read < 0) break
                        count += read
                    }
                    buffer.copyOf(count)
                }
            } catch (_: NoSuchFileException) {
                null
            }
        }
    }

    override fun replace(purpose: SecretPurpose, envelope: ByteArray) {
        val target = stage(EnvelopeStoreException.Stage.LOCATE) { envelopeFile(purpose) }
        val temporary = stage(EnvelopeStoreException.Stage.WRITE) { files.createTemporary(checkNotNull(target.parentFile)) }
        try {
            stage(EnvelopeStoreException.Stage.WRITE) {
                FileOutputStream(temporary).use { output ->
                    output.write(envelope)
                    files.sync(output.fd)
                }
            }
            stage(EnvelopeStoreException.Stage.MOVE) {
                files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (failure: EnvelopeStoreException) {
            val cleaned = try {
                files.delete(temporary.toPath())
                true
            } catch (_: Exception) {
                false
            }
            throw EnvelopeStoreException(failure.stage, cleanupFailed = !cleaned)
        }
    }

    private fun envelopeFile(purpose: SecretPurpose): File =
        File(policy.location(SecureStorageNamespace.SECRET_ENVELOPE).directory, "${purpose.label}.envelope")
}

/**
 * A store failure. The message is fixed and there is no cause or suppressed exception, so no path or file name leaves the
 * store; [stage] and [cleanupFailed] are closed, path-free codes a caller can report through its own diagnostics.
 * WRITE covers the directory, the temporary file, its bytes and its sync; MOVE is the atomic swap.
 */
internal class EnvelopeStoreException(
    val stage: Stage,
    val cleanupFailed: Boolean = false,
) : IOException(STORE_FAILED) {
    enum class Stage { LOCATE, READ, WRITE, MOVE }
}

/** The file operations a test records (the sync and the move options) or makes fail; the platform object is tested directly. */
internal interface EnvelopeFileOperations {
    /** Creates [directory] if needed and an unpredictably named temporary file in it. */
    fun createTemporary(directory: File): File

    fun sync(descriptor: FileDescriptor)

    fun move(source: Path, target: Path, vararg options: CopyOption)

    fun delete(path: Path)
}

internal object PlatformEnvelopeFileOperations : EnvelopeFileOperations {
    override fun createTemporary(directory: File): File {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) throw IOException()
        return File.createTempFile("envelope-", ".tmp", directory)
    }

    override fun sync(descriptor: FileDescriptor) = descriptor.sync()

    override fun move(source: Path, target: Path, vararg options: CopyOption) {
        Files.move(source, target, *options)
    }

    override fun delete(path: Path) {
        Files.deleteIfExists(path)
    }
}

/** Runs [block]; an ordinary exception becomes an [EnvelopeStoreException] for [stage], an inner one passes unchanged. */
private inline fun <T> stage(stage: EnvelopeStoreException.Stage, block: () -> T): T = try {
    block()
} catch (failure: EnvelopeStoreException) {
    throw failure
} catch (_: Exception) {
    throw EnvelopeStoreException(stage)
}

private const val STORE_FAILED = "secret envelope store failed"

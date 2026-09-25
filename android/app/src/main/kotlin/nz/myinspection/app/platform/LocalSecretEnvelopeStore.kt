package nz.myinspection.app.platform

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption

/**
 * Envelope files in the credential-encrypted no-backup secret-envelope directory. Every call asks [policy] for that
 * directory, so the path boundary is checked each time. [replace] moves a synced temporary file over the envelope, so
 * a failure leaves the previous envelope in place. Failures carry one fixed message and no cause, never a path.
 */
class LocalSecretEnvelopeStore(private val policy: AppStoragePolicy) : SecretEnvelopeFiles {
    override fun read(purpose: SecretPurpose): ByteArray? = redacted {
        try {
            Files.newInputStream(envelopeFile(purpose).toPath()).use { input ->
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

    override fun replace(purpose: SecretPurpose, envelope: ByteArray) {
        redacted {
            val target = envelopeFile(purpose)
            val directory = checkNotNull(target.parentFile)
            if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) throw IOException()
            val temporary = File.createTempFile("envelope-", ".tmp", directory)
            try {
                FileOutputStream(temporary).use { output ->
                    output.write(envelope)
                    output.fd.sync()
                }
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                temporary.delete()
            }
        }
    }

    private fun envelopeFile(purpose: SecretPurpose): File =
        File(policy.location(SecureStorageNamespace.SECRET_ENVELOPE).directory, "${purpose.label}.envelope")
}

private inline fun <T> redacted(block: () -> T): T = try {
    block()
} catch (_: Exception) {
    throw IOException(STORE_FAILED)
}

private const val STORE_FAILED = "secret envelope store failed"

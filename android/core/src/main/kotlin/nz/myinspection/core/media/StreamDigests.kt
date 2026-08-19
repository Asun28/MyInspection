package nz.myinspection.core.media

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** A SHA-256 digest and exact byte count for content that crossed a stream boundary. */
data class StreamDigest(val sha256: String, val sizeBytes: Long)

/**
 * Bounded stream helpers for media staging. [writeAndClose] owns the supplied output: a caller may publish only
 * after it returns, which means the writer closed successfully. [verify] is deliberately a second bounded read of
 * the closed temporary file, proving that the bytes persisted to disk still match the first-pass digest and size.
 */
object StreamDigests {
    private const val VERIFY_BUFFER_BYTES = 16 * 1024

    fun writeAndClose(output: OutputStream, producer: (OutputStream) -> Unit): StreamDigest =
        writeAndCloseWith(output, producer) { digest, sizeBytes ->
            StreamDigest(sha256 = ContentHash.hex(digest.digest()), sizeBytes = sizeBytes)
        }

    /** Test seam for a failure after the writer has closed but before a staged file can be verified or published. */
    internal fun writeAndCloseWith(
        output: OutputStream,
        producer: (OutputStream) -> Unit,
        finish: (MessageDigest, Long) -> StreamDigest,
    ): StreamDigest {
        val digest = MessageDigest.getInstance("SHA-256")
        val counted = CountingDigestOutputStream(output, digest)
        var primary: Throwable? = null
        try {
            producer(counted)
        } catch (failure: Throwable) {
            primary = failure
            throw failure
        } finally {
            try {
                counted.close()
            } catch (closeFailure: Throwable) {
                val failure = primary
                if (failure == null) throw closeFailure
                failure.addSuppressed(closeFailure)
            }
        }
        return finish(digest, counted.sizeBytes)
    }

    /** The caller owns [input] and closes it after this bounded verification read. */
    fun verify(input: InputStream, expected: StreamDigest) {
        val actual = digest(input)
        check(actual.sizeBytes == expected.sizeBytes) {
            "stream size changed after write: expected ${expected.sizeBytes}, actual ${actual.sizeBytes}"
        }
        check(actual.sha256 == expected.sha256) {
            "stream SHA-256 changed after write"
        }
    }

    private fun digest(input: InputStream): StreamDigest {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(VERIFY_BUFFER_BYTES)
        var sizeBytes = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            sizeBytes += read
            digest.update(buffer, 0, read)
        }
        return StreamDigest(sha256 = ContentHash.hex(digest.digest()), sizeBytes = sizeBytes)
    }

    private class CountingDigestOutputStream(
        private val target: OutputStream,
        private val digest: MessageDigest,
    ) : OutputStream() {
        var sizeBytes = 0L
            private set

        override fun write(value: Int) {
            target.write(value)
            digest.update(value.toByte())
            sizeBytes += 1
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            target.write(buffer, offset, length)
            digest.update(buffer, offset, length)
            sizeBytes += length
        }

        override fun flush() = target.flush()

        override fun close() = target.close()
    }
}

package nz.myinspection.app.platform

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The secrets the box holds. The label names the envelope file and is part of the key alias and the associated data. */
enum class SecretPurpose(val label: String) {
    BACKUP_PASSPHRASE("backup-passphrase"),
    REMEDIATION_API_KEY("remediation-api-key"),
}

/** Keys by alias. The Android implementation keeps them in AndroidKeyStore (T1-LOCAL-DATA-SECURITY). */
interface SecretKeyPort {
    /** The key under [alias], or null when there is none. */
    fun existingKey(alias: String): SecretKey?

    /**
     * The key under [alias], created when there is none. An existing key that can encrypt is returned, never replaced;
     * one that cannot may be replaced while the device is unlocked (AndroidSecretKeys does). The previous envelope then no
     * longer opens, which is acceptable because seal runs when the user supplies the secret again.
     */
    fun keyForSeal(alias: String): SecretKey
}

fun interface DeviceUnlockPort {
    fun isUnlocked(): Boolean
}

/**
 * Envelope bytes by purpose. [read] returns null when there is no envelope and at most [MAX_ENVELOPE_BYTES] + 1 bytes;
 * [replace] swaps the envelope in one step, so a failed replace leaves the previous envelope as it was.
 */
interface SecretEnvelopeFiles {
    fun read(purpose: SecretPurpose): ByteArray?
    fun replace(purpose: SecretPurpose, envelope: ByteArray)
}

enum class SecretSealResult { STORED, NEEDS_UNLOCK, UNAVAILABLE }

enum class SecretFailureReason {
    ENVELOPE_MISSING, ENVELOPE_UNREADABLE, ENVELOPE_CORRUPT, VERSION_UNSUPPORTED, KEY_MISSING, KEY_UNUSABLE, AUTHENTICATION_FAILED,
}

sealed interface SecretOpenResult {
    class Opened internal constructor(val value: SecretChars) : SecretOpenResult {
        override fun toString(): String = "SecretOpenResult.Opened"
    }

    /** Retryable: the device is not unlocked. */
    data object NeedsUnlock : SecretOpenResult

    /** The stored envelope cannot be opened; the user has to enter the secret again. */
    data class NeedsPassphrase(val reason: SecretFailureReason) : SecretOpenResult
}

/** Opened characters. The caller closes the holder, which zero-fills them. */
class SecretChars internal constructor(val chars: CharArray) : AutoCloseable {
    override fun close() = chars.fill('\u0000')

    override fun toString(): String = "SecretChars"
}

/**
 * Seals short secrets with AES-GCM under a key per purpose and version. The envelope is the version byte, the 12-byte
 * nonce the cipher provider generated and the ciphertext with its 16-byte tag; the associated data binds a fixed
 * domain string, the version and the purpose. No failure returns plaintext or touches the stored envelope.
 */
class LocalSecretBox internal constructor(
    private val keys: SecretKeyPort,
    private val unlock: DeviceUnlockPort,
    private val files: SecretEnvelopeFiles,
    private val sealVersion: Int,
    readableVersions: Set<Int>,
    private val newCipher: () -> Cipher,
) {
    constructor(
        keys: SecretKeyPort,
        unlock: DeviceUnlockPort,
        files: SecretEnvelopeFiles,
        sealVersion: Int = 1,
        readableVersions: Set<Int> = setOf(1),
    ) : this(keys, unlock, files, sealVersion, readableVersions, { Cipher.getInstance(TRANSFORMATION) })

    private val readableVersions = readableVersions.toSet()

    init {
        require(sealVersion in this.readableVersions && this.readableVersions.all { it in 1..255 }) { "invalid key versions" }
    }

    /** Rejects an empty, oversized or malformed [plaintext] with a fixed message; the caller keeps and clears it. */
    fun seal(purpose: SecretPurpose, plaintext: CharArray): SecretSealResult {
        require(plaintext.size in 1..MAX_SECRET_CHARS) { REJECTED_INPUT }
        return withUtf8Bytes(plaintext) { utf8, length -> sealUtf8(purpose, utf8, length) }
    }

    fun open(purpose: SecretPurpose): SecretOpenResult {
        if (!isUnlocked()) return SecretOpenResult.NeedsUnlock
        val envelope = try {
            files.read(purpose)
        } catch (_: Exception) {
            return failure(SecretFailureReason.ENVELOPE_UNREADABLE)
        } ?: return needsPassphrase(SecretFailureReason.ENVELOPE_MISSING)
        if (envelope.size < HEADER_BYTES + TAG_BYTES || envelope.size > MAX_ENVELOPE_BYTES) {
            return needsPassphrase(SecretFailureReason.ENVELOPE_CORRUPT)
        }
        val version = envelope[0].toInt() and 0xFF
        if (version !in readableVersions) return needsPassphrase(SecretFailureReason.VERSION_UNSUPPORTED)
        val plaintext = try {
            val key = keys.existingKey(secretKeyAlias(purpose, version)) ?: return needsPassphrase(SecretFailureReason.KEY_MISSING)
            val cipher = newCipher()
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, envelope, 1, NONCE_BYTES))
            cipher.updateAAD(associatedData(purpose, version))
            cipher.doFinal(envelope, HEADER_BYTES, envelope.size - HEADER_BYTES)
        } catch (_: AEADBadTagException) {
            return needsPassphrase(SecretFailureReason.AUTHENTICATION_FAILED)
        } catch (_: Exception) {
            return failure(SecretFailureReason.KEY_UNUSABLE)
        }
        return try {
            SecretOpenResult.Opened(secretCharsFromUtf8(plaintext))
        } catch (_: CharacterCodingException) {
            needsPassphrase(SecretFailureReason.ENVELOPE_CORRUPT)
        }
    }

    private fun sealUtf8(purpose: SecretPurpose, utf8: ByteArray, length: Int): SecretSealResult {
        if (!isUnlocked()) return SecretSealResult.NEEDS_UNLOCK
        return try {
            val cipher = newCipher()
            cipher.init(Cipher.ENCRYPT_MODE, keys.keyForSeal(secretKeyAlias(purpose, sealVersion)))
            val nonce: ByteArray? = cipher.iv
            if (nonce == null || nonce.size != NONCE_BYTES) return SecretSealResult.UNAVAILABLE
            cipher.updateAAD(associatedData(purpose, sealVersion))
            files.replace(purpose, byteArrayOf(sealVersion.toByte()) + nonce + cipher.doFinal(utf8, 0, length))
            SecretSealResult.STORED
        } catch (_: Exception) {
            if (isUnlocked()) SecretSealResult.UNAVAILABLE else SecretSealResult.NEEDS_UNLOCK
        }
    }

    /** A key, cipher or read failure the device lock can explain is retryable; otherwise the secret has to be re-entered. */
    private fun failure(reason: SecretFailureReason): SecretOpenResult =
        if (isUnlocked()) needsPassphrase(reason) else SecretOpenResult.NeedsUnlock

    private fun needsPassphrase(reason: SecretFailureReason) = SecretOpenResult.NeedsPassphrase(reason)

    private fun isUnlocked(): Boolean = try {
        unlock.isUnlocked()
    } catch (_: Exception) {
        false
    }
}

internal fun secretKeyAlias(purpose: SecretPurpose, version: Int): String = "myinspection.secret.${purpose.label}.v$version"

private fun associatedData(purpose: SecretPurpose, version: Int): ByteArray =
    DOMAIN + byteArrayOf(0, version.toByte()) + purpose.label.toByteArray(Charsets.US_ASCII)

/**
 * Encodes [chars] as UTF-8 into a worst-case-sized array (three bytes per UTF-16 unit), runs [block] on it and the encoded
 * length, and zero-fills it afterwards. A new encoder reports malformed input, so a lone surrogate is rejected.
 */
internal fun <R> withUtf8Bytes(chars: CharArray, block: (ByteArray, Int) -> R): R {
    val bytes = ByteArray(3 * chars.size)
    try {
        val target = ByteBuffer.wrap(bytes)
        val encoder = Charsets.UTF_8.newEncoder()
        require(!encoder.encode(CharBuffer.wrap(chars), target, true).isError && !encoder.flush(target).isError) { REJECTED_INPUT }
        return block(bytes, target.position())
    } finally {
        bytes.fill(0)
    }
}

/**
 * Decodes [bytes] strictly into [scratch] and zero-fills both, whether decoding succeeds or not. [scratch] is a parameter
 * only so a test can see it cleared; a decode that does not consume every byte into it counts as a failure.
 */
internal fun secretCharsFromUtf8(bytes: ByteArray, scratch: CharBuffer = CharBuffer.allocate(bytes.size)): SecretChars {
    try {
        val decoder = Charsets.UTF_8.newDecoder()
        val decoded = decoder.decode(ByteBuffer.wrap(bytes), scratch, true)
        if (!decoded.isUnderflow || decoder.flush(scratch).isError) throw CharacterCodingException()
        return SecretChars(scratch.array().copyOf(scratch.position()))
    } finally {
        scratch.array().fill('\u0000')
        bytes.fill(0)
    }
}

internal const val MAX_SECRET_CHARS = 1024
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val NONCE_BYTES = 12
private const val TAG_BYTES = 16
private const val HEADER_BYTES = 1 + NONCE_BYTES

/** A version byte, a nonce, the largest UTF-8 encoding of a secret and the tag. */
internal const val MAX_ENVELOPE_BYTES = HEADER_BYTES + 3 * MAX_SECRET_CHARS + TAG_BYTES

private const val REJECTED_INPUT = "secret input rejected"
private val DOMAIN = "nz.myinspection.local-secret-box".toByteArray(Charsets.US_ASCII)

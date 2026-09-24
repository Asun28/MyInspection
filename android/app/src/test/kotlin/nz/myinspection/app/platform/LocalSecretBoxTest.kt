package nz.myinspection.app.platform

import java.io.IOException
import java.nio.charset.CharacterCodingException
import java.security.AlgorithmParameters
import java.security.Key
import java.security.KeyStoreException
import java.security.Provider
import java.security.SecureRandom
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher
import javax.crypto.CipherSpi
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.*
import nz.myinspection.app.platform.SecretFailureReason.*

class LocalSecretBoxTest {
    @Test
    fun `a sealed envelope holds only the version byte, the provider nonce and the ciphertext with its tag`() {
        SecretPurpose.entries.forEach { purpose ->
            val files = MemoryFiles()
            val box = box(files = files)
            assertEquals(SecretSealResult.STORED, box.sealSample(purpose), purpose.name)

            val envelope = files.stored.getValue(purpose)
            assertEquals(29 + sampleUtf8.size, envelope.size, purpose.name)
            assertEquals(1, envelope[0].toInt(), purpose.name)
            assertFalse(envelope.containsRun(sampleUtf8), purpose.name)
            assertEquals(setOf(purpose), files.stored.keys, purpose.name)
            assertEquals(sample, openedText(box.open(purpose)), purpose.name)
        }
    }

    @Test
    fun `two seals of the same plaintext use different nonces and different ciphertexts`() {
        val files = MemoryFiles()
        val box = box(files = files)
        box.sealSample()
        val first = files.envelope
        box.sealSample()
        val second = files.envelope

        assertFalse(first.copyOfRange(1, 13).contentEquals(second.copyOfRange(1, 13)), "nonce reused")
        assertFalse(first.copyOfRange(13, first.size).contentEquals(second.copyOfRange(13, second.size)), "ciphertext repeated")
    }

    @Test
    fun `seal passes no IV, stores the provider's nonce and refuses one that is not twelve bytes`() {
        listOf(12 to SecretSealResult.STORED, 16 to SecretSealResult.UNAVAILABLE).forEach { (size, expected) ->
            val spi = RecordingSpi(ByteArray(size) { 7 })
            val files = MemoryFiles()
            val box = LocalSecretBox(SoftwareKeys(), ScriptedUnlock(true), files, 1, setOf(1)) { RecordingCipher(spi) }

            assertEquals(expected, box.sealSample(), "nonce $size")
            assertEquals(null, spi.parameters, "nonce $size")
            assertEquals(if (size == 12) 1 else 0, files.writes, "nonce $size")
            if (size == 12) assertContentEquals(ByteArray(12) { 7 }, files.envelope.copyOfRange(1, 13))
        }
    }

    @Test
    fun `a flipped bit in the nonce, the ciphertext or the tag fails authentication and leaves the envelope unchanged`() {
        val files = MemoryFiles()
        val box = box(files = files)
        box.sealSample()
        val original = files.envelope

        mapOf("nonce" to 6, "ciphertext" to 13, "tag" to original.size - 1).forEach { (part, index) ->
            val tampered = original.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            files.envelope = tampered
            assertEquals(needs(AUTHENTICATION_FAILED), box.open(backup), part)
            assertContentEquals(tampered, files.envelope, part)
        }
    }

    @Test
    fun `a version outside the readable set is refused before any key lookup`() {
        val keys = SoftwareKeys()
        val files = MemoryFiles()
        val box = box(keys, files)
        box.sealSample()
        files.envelope[0] = 7
        keys.requested.clear()

        assertEquals(needs(VERSION_UNSUPPORTED), box.open(backup))
        assertEquals(emptyList(), keys.requested)
        assertEquals(1, files.writes)
    }

    @Test
    fun `a v1 envelope relabelled to the readable v2 fails authentication under the v2 key`() {
        val keys = SoftwareKeys()
        val files = MemoryFiles()
        box(keys, files, version = 2, readable = setOf(1, 2)).sealSample()
        val box = box(keys, files, readable = setOf(1, 2))
        box.sealSample()
        files.envelope[0] = 2

        assertEquals(needs(AUTHENTICATION_FAILED), box.open(backup))
    }

    @Test
    fun `envelopes shorter than header and tag or longer than the cap are corrupt, and a raw plaintext file never opens`() {
        val files = MemoryFiles()
        val box = box(files = files)
        box.sealSample()
        val valid = files.envelope

        listOf(1, 28, 0, MAX_ENVELOPE_BYTES + 1).forEach { size ->
            files.envelope = ByteArray(size).also { if (size > 0) it[0] = 1 }
            assertEquals(needs(ENVELOPE_CORRUPT), box.open(backup), "size $size")
        }
        files.envelope = valid.copyOf(29)
        assertEquals(needs(AUTHENTICATION_FAILED), box.open(backup), "size 29")
        listOf(sampleUtf8, byteArrayOf(1) + sampleUtf8).forEach { raw ->
            files.envelope = raw
            assertIs<SecretOpenResult.NeedsPassphrase>(box.open(backup), "raw ${raw.size}")
        }
        assertEquals(1, files.writes)
    }

    @Test
    fun `each purpose and version asks the key port for its own alias, and open follows the envelope version`() {
        val keys = SoftwareKeys()
        val files = MemoryFiles()
        val expected = listOf(1, 2).flatMap { version ->
            val sealer = box(keys, files, version = version, readable = setOf(1, 2))
            val opener = box(keys, files, version = 3 - version, readable = setOf(1, 2))
            SecretPurpose.entries.flatMap { purpose ->
                sealer.sealSample(purpose)
                openedText(opener.open(purpose))
                List(2) { "myinspection.secret.${purpose.label}.v$version" }
            }
        }

        assertEquals(expected, keys.requested)
        assertEquals(4, keys.keys.size)
    }

    @Test
    fun `under one shared key an envelope moved to the other purpose or relabelled v2 fails authentication`() {
        val files = MemoryFiles()
        val box = box(SoftwareKeys(shared = newAesKey()), files, readable = setOf(1, 2))
        box.sealSample()
        files.stored[SecretPurpose.REMEDIATION_API_KEY] = files.envelope.copyOf()

        assertEquals(needs(AUTHENTICATION_FAILED), box.open(SecretPurpose.REMEDIATION_API_KEY), "purpose")
        files.envelope[0] = 2
        assertEquals(needs(AUTHENTICATION_FAILED), box.open(backup), "version")
    }

    @Test
    fun `a golden envelope pins the layout, the alias and the associated data`() {
        val key = newAesKey()
        val files = MemoryFiles().also { it.stored[SecretPurpose.REMEDIATION_API_KEY] = goldenEnvelope(key, "remediation-api-key", sampleUtf8) }
        val keys = SoftwareKeys().also { it.keys["myinspection.secret.remediation-api-key.v1"] = key }

        assertEquals(sample, openedText(box(keys, files).open(SecretPurpose.REMEDIATION_API_KEY)))
        assertEquals(0, files.writes)
    }

    @Test
    fun `an authenticated envelope whose plaintext is not UTF-8 is corrupt`() {
        val key = newAesKey()
        val files = MemoryFiles().also { it.envelope = goldenEnvelope(key, "backup-passphrase", byteArrayOf(0x61, 0xFF.toByte())) }
        val keys = SoftwareKeys().also { it.keys["myinspection.secret.backup-passphrase.v1"] = key }

        assertEquals(needs(ENVELOPE_CORRUPT), box(keys, files).open(backup))
        assertEquals(0, files.writes)
    }

    @Test
    fun `open while locked returns NeedsUnlock without touching keys or files`() {
        listOf(ScriptedUnlock(false), DeviceUnlockPort { throw IllegalStateException("probe failed") }).forEach { unlock ->
            val keys = SoftwareKeys()
            val files = MemoryFiles()

            assertSame(SecretOpenResult.NeedsUnlock, box(keys, files, unlock).open(backup))
            assertEquals(emptyList(), keys.requested)
            assertEquals(0, files.reads)
        }
    }

    @Test
    fun `a missing envelope needs the passphrase`() {
        assertEquals(needs(ENVELOPE_MISSING), box().open(backup))
    }

    @Test
    fun `a read failure is retryable only when the device is now locked`() {
        val files = MemoryFiles().also { it.readFailure = IOException("/data/42 Example St") }

        assertEquals(needs(ENVELOPE_UNREADABLE), box(files = files).open(backup))
        assertSame(SecretOpenResult.NeedsUnlock, box(files = files, unlock = ScriptedUnlock(true, false)).open(backup))
    }

    @Test
    fun `a missing key needs the passphrase and keeps the envelope`() {
        val keys = SoftwareKeys()
        val files = MemoryFiles()
        val box = box(keys, files)
        box.sealSample()
        val envelope = files.envelope.copyOf()
        keys.keys.clear()

        assertEquals(needs(KEY_MISSING), box.open(backup))
        assertContentEquals(envelope, files.envelope)
    }

    @Test
    fun `a key-port failure or a key the cipher refuses is retryable only when the device is now locked`() {
        val files = MemoryFiles().also { box(files = it).sealSample() }
        val failing = SoftwareKeys().also { it.existingFailure = KeyStoreException("alias myinspection") }
        val refused = SoftwareKeys().also { it.keys["myinspection.secret.backup-passphrase.v1"] = SecretKeySpec(ByteArray(7), "AES") }

        listOf(failing, refused).forEachIndexed { index, keys ->
            assertEquals(needs(KEY_UNUSABLE), box(keys, files).open(backup), "case $index")
            assertSame(SecretOpenResult.NeedsUnlock, box(keys, files, ScriptedUnlock(true, false)).open(backup), "case $index")
        }
        assertEquals(1, files.writes)
    }

    @Test
    fun `seal while locked touches neither keys nor files`() {
        val keys = SoftwareKeys()
        val files = MemoryFiles()

        assertEquals(SecretSealResult.NEEDS_UNLOCK, box(keys, files, ScriptedUnlock(false)).sealSample())
        assertEquals(emptyList(), keys.requested)
        assertEquals(0, files.writes)
    }

    @Test
    fun `a failed seal keeps the previous envelope and is retryable only when the device is now locked`() {
        val keys = SoftwareKeys()
        val files = MemoryFiles().also { box(keys, it).sealSample() }
        val previous = files.envelope.copyOf()

        listOf<() -> Unit>(
            { keys.sealFailure = KeyStoreException("keystore") },
            { keys.sealFailure = null; files.writeFailure = IOException("/data/42 Example St") },
            { files.writeFailure = null; keys.keys["myinspection.secret.backup-passphrase.v1"] = SecretKeySpec(ByteArray(7), "AES") },
        ).forEachIndexed { index, arrange ->
            arrange()
            assertEquals(SecretSealResult.UNAVAILABLE, box(keys, files).seal(backup, "replacement".toCharArray()), "case $index")
            assertEquals(SecretSealResult.NEEDS_UNLOCK, box(keys, files, ScriptedUnlock(true, false)).seal(backup, "replacement".toCharArray()), "case $index locked")
            assertContentEquals(previous, files.envelope, "case $index")
        }
    }

    @Test
    fun `an Error from any port propagates with its identity`() {
        val fatal = OutOfMemoryError("fatal")
        val sealed = MemoryFiles().also { box(files = it).sealSample() }
        val cases = listOf<Pair<String, () -> Unit>>(
            "unlock" to { box(files = sealed, unlock = { throw fatal }).open(backup) },
            "read" to { box(files = MemoryFiles().also { it.readFailure = fatal }).open(backup) },
            "key open" to { box(SoftwareKeys().also { it.existingFailure = fatal }, sealed).open(backup) },
            "key seal" to { box(SoftwareKeys().also { it.sealFailure = fatal }).sealSample() },
            "write" to { box(files = MemoryFiles().also { it.writeFailure = fatal }).sealSample() },
        )
        cases.forEach { (name, action) -> assertSame(fatal, assertFailsWith<OutOfMemoryError>(name) { action() }, name) }
    }

    @Test
    fun `the UTF-8 bytes built for seal are zero after the helper returns or throws`() {
        var captured = ByteArray(0)
        withUtf8Bytes(sample.toCharArray()) { bytes, length ->
            assertContentEquals(sampleUtf8, bytes.copyOf(length))
            captured = bytes
        }
        assertTrue(captured.all { it == 0.toByte() }, "success path")

        assertFailsWith<IllegalStateException> {
            withUtf8Bytes(sample.toCharArray()) { bytes, _ ->
                captured = bytes
                throw IllegalStateException("cipher failed")
            }
        }
        assertTrue(captured.all { it == 0.toByte() }, "throwing path")
    }

    @Test
    fun `decrypted bytes are zero after decoding succeeds or fails`() {
        val decrypted = sampleUtf8.copyOf()
        assertEquals(sample, String(secretCharsFromUtf8(decrypted).chars))
        assertTrue(decrypted.all { it == 0.toByte() }, "success path")

        val malformed = byteArrayOf(0x61, 0xFF.toByte())
        assertFailsWith<CharacterCodingException> { secretCharsFromUtf8(malformed) }
        assertTrue(malformed.all { it == 0.toByte() }, "failure path")
    }

    @Test
    fun `closing the opened characters zero-fills them and no holder prints them`() {
        val box = box(files = MemoryFiles())
        box.sealSample()
        val opened = assertIs<SecretOpenResult.Opened>(box.open(backup))
        val chars = opened.value.chars

        assertFalse(opened.toString().contains("correct"))
        assertFalse(opened.value.toString().contains("correct"))
        opened.value.close()
        assertTrue(chars.all { it == '\u0000' })
    }

    @Test
    fun `empty, oversized and malformed secrets are rejected without echoing them`() {
        val box = box()
        val loneSurrogate = charArrayOf('p', 'w', 0xD800.toChar(), 'z')

        listOf(CharArray(0), CharArray(MAX_SECRET_CHARS + 1) { 'q' }, loneSurrogate).forEach { input ->
            val thrown = assertFailsWith<IllegalArgumentException> { box.seal(backup, input) }
            assertEquals("secret input rejected", thrown.message)
        }
        val largest = CharArray(MAX_SECRET_CHARS) { 0x7535.toChar() }
        assertEquals(SecretSealResult.STORED, box.seal(backup, largest))
        assertEquals(String(largest), openedText(box.open(backup)))
    }
}

private val backup = SecretPurpose.BACKUP_PASSPHRASE
private val sample: String = buildString {
    append("correct horse ")
    listOf(0x2713, 0x20, 0x7535, 0x6C60, 0x20, 0x1F40E).forEach { appendCodePoint(it) }
}
private val sampleUtf8: ByteArray = sample.toByteArray(Charsets.UTF_8)

private fun box(
    keys: SecretKeyPort = SoftwareKeys(),
    files: SecretEnvelopeFiles = MemoryFiles(),
    unlock: DeviceUnlockPort = ScriptedUnlock(true),
    version: Int = 1,
    readable: Set<Int> = setOf(1),
) = LocalSecretBox(keys, unlock, files, version, readable)

private fun LocalSecretBox.sealSample(purpose: SecretPurpose = backup) = seal(purpose, sample.toCharArray())

private fun needs(reason: SecretFailureReason) = SecretOpenResult.NeedsPassphrase(reason)

private fun openedText(result: SecretOpenResult): String = assertIs<SecretOpenResult.Opened>(result).value.use { String(it.chars) }

private fun newAesKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

/** A v1 envelope built with a fixed nonce and the documented associated data, independently of the box. */
private fun goldenEnvelope(key: SecretKey, label: String, plaintext: ByteArray): ByteArray {
    val nonce = ByteArray(12) { (it + 1).toByte() }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
    cipher.updateAAD("nz.myinspection.local-secret-box".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 1) + label.toByteArray(Charsets.US_ASCII))
    return byteArrayOf(1) + nonce + cipher.doFinal(plaintext)
}

private fun ByteArray.containsRun(run: ByteArray): Boolean =
    (0..size - run.size).any { start -> run.indices.all { this[start + it] == run[it] } }

private class SoftwareKeys(private val shared: SecretKey? = null) : SecretKeyPort {
    val keys = linkedMapOf<String, SecretKey>()
    val requested = mutableListOf<String>()
    var existingFailure: Throwable? = null
    var sealFailure: Throwable? = null

    override fun existingKey(alias: String): SecretKey? {
        requested += alias
        existingFailure?.let { throw it }
        return shared ?: keys[alias]
    }

    override fun keyForSeal(alias: String): SecretKey {
        requested += alias
        sealFailure?.let { throw it }
        return shared ?: keys.getOrPut(alias) { newAesKey() }
    }
}

private class MemoryFiles : SecretEnvelopeFiles {
    val stored = linkedMapOf<SecretPurpose, ByteArray>()
    var readFailure: Throwable? = null
    var writeFailure: Throwable? = null
    var reads = 0
    var writes = 0

    /** The stored backup-passphrase envelope itself, so tests can edit it in place. */
    var envelope: ByteArray
        get() = stored.getValue(backup)
        set(value) { stored[backup] = value }

    override fun read(purpose: SecretPurpose): ByteArray? {
        reads += 1
        readFailure?.let { throw it }
        return stored[purpose]?.copyOf()
    }

    override fun replace(purpose: SecretPurpose, envelope: ByteArray) {
        writes += 1
        writeFailure?.let { throw it }
        stored[purpose] = envelope.copyOf()
    }
}

/** Answers the given states in order and then keeps answering the last one. */
private class ScriptedUnlock(vararg states: Boolean) : DeviceUnlockPort {
    private val answers = ArrayDeque(states.toList())

    override fun isUnlocked(): Boolean = if (answers.size > 1) answers.removeFirst() else answers.first()
}

/** A GCM-shaped cipher double: reports [iv] as the provider nonce, accepts associated data, records init parameters. */
private class RecordingSpi(private val iv: ByteArray) : CipherSpi() {
    var parameters: Any? = null

    override fun engineSetMode(mode: String?) = Unit
    override fun engineSetPadding(padding: String?) = Unit
    override fun engineGetBlockSize() = 16
    override fun engineGetOutputSize(inputLen: Int) = inputLen + 16
    override fun engineGetIV() = iv
    override fun engineGetParameters(): AlgorithmParameters? = null
    override fun engineInit(opmode: Int, key: Key?, random: SecureRandom?) = Unit
    override fun engineInit(opmode: Int, key: Key?, params: AlgorithmParameterSpec?, random: SecureRandom?) { parameters = params }
    override fun engineInit(opmode: Int, key: Key?, params: AlgorithmParameters?, random: SecureRandom?) { parameters = params }
    override fun engineUpdate(input: ByteArray?, offset: Int, length: Int) = ByteArray(0)
    override fun engineUpdate(input: ByteArray?, offset: Int, length: Int, output: ByteArray?, outputOffset: Int) = 0
    override fun engineDoFinal(input: ByteArray?, offset: Int, length: Int) = ByteArray(length + 16)
    override fun engineDoFinal(input: ByteArray?, offset: Int, length: Int, output: ByteArray?, outputOffset: Int) = 0
    override fun engineUpdateAAD(src: ByteArray?, offset: Int, length: Int) = Unit
}

private class RecordingCipher(spi: RecordingSpi) : Cipher(spi, object : Provider("RecordingGcm", 1.0, "test double") {}, "AES/GCM/NoPadding")

/*
 * R4 receipt: 41/41 observable single-point mutants killed; M31 is the one survivor and cannot be observed (below).
 * Each mutant compiled, its named test failed with java.lang.AssertionError, and the production file was restored and
 * its SHA-256 re-checked before the next mutant.
 * Production SHA-256 (LocalSecretBox.kt): CE9BABCBE6E8F5A52AE31DDE7DCE6EE6A098A9485C2EFCBC32FA04912BB5CAFF
 * Pre-receipt test SHA-256: 0C26FA71F49AB95762C293B714B629FE03BA2802BB8639B06CC4C899A4D8A38A; this file is those bytes plus
 * this comment. Per mutant: cmd /c android\gradlew.bat -p android --offline --no-build-cache -q :app:testDebugUnitTest
 * --tests nz.myinspection.app.platform.LocalSecretBoxTest. Evidence: _local/local-secret-box/r4/{results.jsonl,Mxx.log}.
 * seal passes no IV...: M02 nonce-size check removed, M40 caller-supplied random IV
 * two seals of the same plaintext...: M01 caller-supplied fixed IV
 * a sealed envelope holds only...: M04 AAD dropped on seal
 * a golden envelope pins...: M03 AAD dropped on open, M07 domain string changed
 * under one shared key...: M05 purpose dropped from the AAD, M06 version fixed in the AAD
 * each purpose and version asks...: M08 purpose / M09 version dropped from the alias, M39 open uses the seal version
 * a version outside the readable set...: M10 readable-version check removed
 * envelopes shorter than header and tag...: M11 short check removed, M12 cap check removed, M41 open rewrites a corrupt
 *   envelope
 * a read failure is retryable...: M13 no lock re-check, M14 reason swapped
 * a missing envelope...: M15 reason swapped; a missing key...: M16 reason swapped
 * a key-port failure or a key the cipher refuses...: M17 no lock re-check
 * a flipped bit...: M18 tag failure mapped to KEY_UNUSABLE; an authenticated envelope...not UTF-8: M19 reason swapped
 * open while locked...: M20 pre-check removed, M21 a throwing unlock probe read as unlocked
 * seal while locked...: M22 pre-check removed
 * a failed seal keeps the previous envelope...: M23 failure reported STORED, M24 no lock re-check
 * an Error from any port...: M25-M28 open-crypto, seal, read and unlock catches widened to Throwable
 * the UTF-8 bytes built for seal...: M29 zero-fill removed
 * decrypted bytes are zero...: M30 zero-fill removed, M38 malformed input accepted
 * closing the opened characters...: M32 close is a no-op, M33/M34 Opened/SecretChars toString print the characters
 * empty, oversized and malformed secrets...: M35 malformed input accepted, M36 length cap removed, M37 empty accepted,
 *   M42 cap check off by one
 * Survivor M31: removing the zero-fill of the decoder's intermediate CharBuffer changes nothing a test can see, because
 * that buffer never leaves secretCharsFromUtf8. The fill stays as best-effort hygiene.
 */
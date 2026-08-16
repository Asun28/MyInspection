package nz.myinspection.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * SHA-256 over raw bytes. Vectors below are independently computed (System.Security.Cryptography.SHA256,
 * not copied from this implementation) so a wrong digest — not just "some 64-hex-char string" — fails.
 */
class ContentHashTest {
    @Test
    fun `sha256Hex of the empty byte array matches the published empty-string vector`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ContentHash.sha256Hex(ByteArray(0)),
        )
    }

    @Test
    fun `sha256Hex of ascii bytes abc matches the NIST published vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ContentHash.sha256Hex("abc".toByteArray(Charsets.US_ASCII)),
        )
    }

    @Test
    fun `sha256Hex is deterministic and lowercase hex, and one flipped byte changes the digest`() {
        val bytes = "hello world".toByteArray(Charsets.UTF_8)
        val first = ContentHash.sha256Hex(bytes)
        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", first)
        assertEquals(first, ContentHash.sha256Hex(bytes), "same bytes must hash identically across calls")
        assertEquals(64, first.length)
        assertEquals(first, first.lowercase(), "hex output must be lowercase (schema comparisons rely on it)")

        val flipped = bytes.copyOf().also { it[0] = it[0].inc() }
        assertNotEquals(first, ContentHash.sha256Hex(flipped), "flipping one byte must change the digest")
    }
}

package nz.myinspection.app.platform

import java.security.KeyStoreException
import java.security.ProviderException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.fail

class AndroidSecretKeysTest {
    @Test
    fun `a missing seal key is created and a key that can encrypt is kept`() {
        assertEquals("new", chooseSealKey<String>(null, { fail("usability asked") }, true, { "new" }, { fail("replaced") }))
        listOf(true, false).forEach { unlocked ->
            assertEquals("old", chooseSealKey("old", { true }, unlocked, { fail("created") }, { fail("replaced") }), "unlocked=$unlocked")
        }
    }

    @Test
    fun `a key that cannot encrypt is replaced only while the device is unlocked`() {
        assertEquals("fresh", chooseSealKey("broken", { false }, true, { fail("created") }, { "fresh" }))
        assertEquals("broken", chooseSealKey("broken", { false }, false, { fail("created") }, { fail("replaced while locked") }))
    }

    @Test
    fun `keystore failures become one fixed message without a cause and an Error keeps its identity`() {
        listOf(
            KeyStoreException("alias myinspection.secret.backup-passphrase.v1"),
            ProviderException("provider detail"),
            IllegalArgumentException("argument detail"),
        ).forEach { failure ->
            val thrown = assertFailsWith<IllegalStateException>(failure.javaClass.name) { keystoreCall<Unit> { throw failure } }
            assertEquals("android keystore unavailable", thrown.message, failure.javaClass.name)
            assertNull(thrown.cause, failure.javaClass.name)
        }
        val fatal = OutOfMemoryError("fatal")
        assertSame(fatal, assertFailsWith<OutOfMemoryError> { keystoreCall<Unit> { throw fatal } })
        assertEquals(7, keystoreCall { 7 })
    }
}

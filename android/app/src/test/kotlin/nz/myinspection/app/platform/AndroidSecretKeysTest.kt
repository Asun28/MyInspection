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
        assertEquals("new", chooseSealKey<String>({ null }, { fail("usability asked") }, true, { "new" }, { fail("replaced") }))
        listOf(true, false).forEach { unlocked ->
            assertEquals("old", chooseSealKey({ "old" }, { true }, unlocked, { fail("created") }, { fail("replaced") }), "unlocked=$unlocked")
        }
    }

    @Test
    fun `a key that cannot encrypt is replaced only while the device is unlocked`() {
        assertEquals("fresh", chooseSealKey({ "broken" }, { false }, true, { fail("created") }, { "fresh" }))
        assertEquals("broken", chooseSealKey({ "broken" }, { false }, false, { fail("created") }, { fail("replaced while locked") }))
    }

    @Test
    fun `an entry that cannot be loaded is replaced only while the device is unlocked`() {
        val unloadable = KeyStoreException("alias myinspection.secret.backup-passphrase.v1")
        val replaced = runCatching { chooseSealKey<String>({ throw unloadable }, { fail("usability asked") }, true, { fail("created") }, { "fresh" }) }
        assertEquals("fresh", replaced.getOrNull(), "an unloadable entry was not replaced while unlocked")
        val thrown = assertFailsWith<KeyStoreException> {
            chooseSealKey<String>({ throw unloadable }, { fail("usability asked") }, false, { fail("created") }, { fail("replaced while locked") })
        }
        assertSame(unloadable, thrown)
    }

    @Test
    fun `the adapter redacts failures at each entry point and reports the unlock state it reads`() {
        val adapter = AndroidSecretKeys(
            { throw KeyStoreException("alias myinspection.secret.backup-passphrase.v1") },
            { throw SecurityException("user detail") },
        )
        listOf<Pair<String, () -> Unit>>(
            "existingKey" to { adapter.existingKey("probe") },
            "keyForSeal" to { adapter.keyForSeal("probe") },
            "isUnlocked" to { adapter.isUnlocked() },
        ).forEach { (name, call) ->
            val thrown = assertFailsWith<IllegalStateException>(name) { call() }
            assertEquals("android keystore unavailable", thrown.message, name)
            assertNull(thrown.cause, name)
        }
        listOf(true, false).forEach { state ->
            assertEquals(state, AndroidSecretKeys({ fail("keystore opened") }, { state }).isUnlocked(), "state=$state")
        }
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

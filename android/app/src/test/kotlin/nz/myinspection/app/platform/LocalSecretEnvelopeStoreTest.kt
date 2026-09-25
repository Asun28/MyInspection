package nz.myinspection.app.platform

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalSecretEnvelopeStoreTest {
    @Test
    fun `replace writes under the no-backup secret-envelope directory and leaves no temporary file`() = withStoragePaths { f ->
        val noBackup = f.dir("ce/no_backup")
        val store = LocalSecretEnvelopeStore(AppStoragePolicy(EnvelopeEnvironment(noBackup, f)))
        val directory = noBackup.resolve("secret-envelope")

        store.replace(SecretPurpose.BACKUP_PASSPHRASE, byteArrayOf(1, 2, 3))
        assertTrue(Files.isRegularFile(directory.resolve("backup-passphrase.envelope")), "envelope file name")
        val held = Files.createLink(noBackup.resolve("held"), directory.resolve("backup-passphrase.envelope"))
        store.replace(SecretPurpose.BACKUP_PASSPHRASE, byteArrayOf(4, 5))
        assertContentEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(held), "the old file was rewritten in place")
        Files.delete(held)
        store.replace(SecretPurpose.REMEDIATION_API_KEY, byteArrayOf(6))

        assertEquals(setOf("backup-passphrase.envelope", "remediation-api-key.envelope"), names(directory))
        assertContentEquals(byteArrayOf(4, 5), Files.readAllBytes(directory.resolve("backup-passphrase.envelope")))
        assertContentEquals(byteArrayOf(4, 5), store.read(SecretPurpose.BACKUP_PASSPHRASE))
        assertContentEquals(byteArrayOf(6), store.read(SecretPurpose.REMEDIATION_API_KEY))
        clear(directory)
    }

    @Test
    fun `read returns null for an absent envelope and at most the cap plus one bytes`() = withStoragePaths { f ->
        val noBackup = f.dir("ce/no_backup")
        val store = LocalSecretEnvelopeStore(AppStoragePolicy(EnvelopeEnvironment(noBackup, f)))
        val absent = runCatching { store.read(SecretPurpose.BACKUP_PASSPHRASE) }
        assertTrue(absent.isSuccess, "reading an absent envelope raised")
        assertNull(absent.getOrNull())

        store.replace(SecretPurpose.BACKUP_PASSPHRASE, ByteArray(MAX_ENVELOPE_BYTES + 50) { 1 })

        assertEquals(MAX_ENVELOPE_BYTES + 1, store.read(SecretPurpose.BACKUP_PASSPHRASE)?.size)
        clear(noBackup.resolve("secret-envelope"))
    }

    @Test
    fun `a failed move fails with a fixed message, deletes its temporary file and leaves the target intact`() = withStoragePaths { f ->
        val noBackup = f.dir("ce/no_backup")
        val store = LocalSecretEnvelopeStore(AppStoragePolicy(EnvelopeEnvironment(noBackup, f)))
        val blocking = f.dir("ce/no_backup/secret-envelope/backup-passphrase.envelope")
        val directory = noBackup.resolve("secret-envelope")

        assertRedacted(assertFailsWith<IOException> { store.replace(SecretPurpose.BACKUP_PASSPHRASE, byteArrayOf(9)) })

        assertEquals(setOf("backup-passphrase.envelope"), names(directory))
        assertEquals(setOf(".marker"), names(blocking))
    }

    @Test
    fun `each call asks the policy again, so a directory that now leaves the root is refused`() = withStoragePaths { f ->
        val noBackup = f.dir("ce/no_backup")
        val store = LocalSecretEnvelopeStore(AppStoragePolicy(EnvelopeEnvironment(noBackup, f)))
        store.replace(SecretPurpose.BACKUP_PASSPHRASE, byteArrayOf(1))
        clear(noBackup.resolve("secret-envelope"))
        f.alias("ce/no_backup/secret-envelope", f.dp)
        val outside = names(f.dp)

        assertRedacted(assertFailsWith<IOException> { store.read(SecretPurpose.BACKUP_PASSPHRASE) })
        assertRedacted(assertFailsWith<IOException> { store.replace(SecretPurpose.BACKUP_PASSPHRASE, byteArrayOf(2)) })
        assertEquals(outside, names(f.dp))
    }

    private fun assertRedacted(thrown: IOException) {
        assertEquals("secret envelope store failed", thrown.message)
        assertNull(thrown.cause)
    }

    private fun names(directory: Path): Set<String> =
        Files.list(directory).use { entries -> entries.map { it.fileName.toString() }.toList().toSet() }

    /** Removes the files a test created under a fixture directory the fixture does not own. */
    private fun clear(directory: Path) {
        Files.list(directory).use { entries -> entries.toList() }.forEach { Files.delete(it) }
        Files.delete(directory)
    }
}

private class EnvelopeEnvironment(noBackup: Path, fixture: StoragePathFixture) : AppStorageEnvironment {
    override val isDeviceProtectedStorage = false
    override val appDataDir: File = fixture.ce.toFile()
    override val deviceProtectedDataDir: File = fixture.dp.toFile()
    override val noBackupFilesDir: File = noBackup.toFile()
    override val appSpecificExternalMediaDir: File? = null

    override fun credentialEncryptedContext(): AppStorageEnvironment = this

    override fun appSpecificExternalMediaState(directory: File) = ExternalMediaVolumeState.UNMOUNTED

    override fun isAppSpecificExternalMediaWritable(directory: File) = false

    override fun usableBytes(directory: File) = 0L
}

/*
 * R4 receipt: 9/9 single-point mutants killed. Each compiled, its named test failed with java.lang.AssertionError, and
 * the production file was restored and its SHA-256 re-checked before the next mutant.
 * Production SHA-256 (LocalSecretEnvelopeStore.kt): 727473DFA5335D8D5192BA59EC436B75A7ACCCE7F721F4013514F39EC0B5791B
 * Test file the batch ran against: lines 1-106 of this file, i.e. every line before the blank line above this comment.
 * `head -n 106 android/app/src/test/kotlin/nz/myinspection/app/platform/LocalSecretEnvelopeStoreTest.kt | sha256sum`
 * prints dc6b3005cdf5666db50ccb56958356ddfbd32ad000dfb1594c233d7cc59fd2d6 (LF line endings, per .gitattributes).
 * Per mutant: cmd /c android\gradlew.bat -p android --offline --no-build-cache -q :app:testDebugUnitTest
 * --tests nz.myinspection.app.platform.LocalSecretEnvelopeStoreTest. Evidence: _local/local-secret-store/r4/.
 * replace writes under...: S05 envelope file name changed, S06 in-place write instead of temp file and move
 * read returns null...: S03 read cap enlarged, S04 absent read raising
 * a failed move...: S01 temporary-file cleanup removed, S07 redaction removed, S08 original exception kept as the cause
 * each call asks the policy again...: S02 location cached at construction, S09 redaction narrowed to IOException
 */
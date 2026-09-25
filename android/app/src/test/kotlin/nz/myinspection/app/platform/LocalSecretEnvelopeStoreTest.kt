package nz.myinspection.app.platform

import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.io.SyncFailedException
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LocalSecretEnvelopeStoreTest {
    @Test
    fun `replace writes under the no-backup secret-envelope directory and leaves no temporary file`() = withStoragePaths { f ->
        val noBackup = f.dir("ce/no_backup")
        val store = LocalSecretEnvelopeStore(AppStoragePolicy(EnvelopeEnvironment(noBackup, f)))
        val directory = noBackup.resolve("secret-envelope")

        val first = runCatching { store.replace(SecretPurpose.BACKUP_PASSPHRASE, byteArrayOf(1, 2, 3)) }
        assertTrue(first.isSuccess, "replace creates the missing secret-envelope directory")
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
    fun `a failed move or read fails with a fixed message and its stage, deletes its temporary file and leaves the target intact`() = withStoragePaths { f ->
        val noBackup = f.dir("ce/no_backup")
        val store = LocalSecretEnvelopeStore(AppStoragePolicy(EnvelopeEnvironment(noBackup, f)))
        val blocking = f.dir("ce/no_backup/secret-envelope/backup-passphrase.envelope")
        val directory = noBackup.resolve("secret-envelope")

        val thrown = assertFailsWith<EnvelopeStoreException> { store.replace(SecretPurpose.BACKUP_PASSPHRASE, byteArrayOf(9)) }
        assertRedacted(thrown)
        assertEquals(EnvelopeStoreException.Stage.MOVE, thrown.stage)
        assertFalse(thrown.cleanupFailed)
        val unreadable = assertFailsWith<EnvelopeStoreException> { store.read(SecretPurpose.BACKUP_PASSPHRASE) }
        assertRedacted(unreadable)
        assertEquals(EnvelopeStoreException.Stage.READ, unreadable.stage)

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

        listOf<() -> Unit>({ store.read(SecretPurpose.BACKUP_PASSPHRASE) }, { store.replace(SecretPurpose.BACKUP_PASSPHRASE, byteArrayOf(2)) }).forEach {
            val thrown = assertFailsWith<EnvelopeStoreException> { it() }
            assertRedacted(thrown)
            assertEquals(EnvelopeStoreException.Stage.LOCATE, thrown.stage)
        }
        assertEquals(outside, names(f.dp))
    }

    @Test
    fun `replace syncs the temporary file and then moves it atomically over the envelope`() = withStoragePaths { f ->
        val noBackup = f.dir("ce/no_backup")
        val operations = RecordingOperations(watch = noBackup.resolve("secret-envelope"))
        val store = LocalSecretEnvelopeStore(AppStoragePolicy(EnvelopeEnvironment(noBackup, f)), operations)

        store.replace(SecretPurpose.BACKUP_PASSPHRASE, byteArrayOf(1, 2))

        assertEquals(listOf("sync", "move"), operations.events)
        assertTrue(operations.syncedOpenFile, "sync got the open temporary file")
        assertContentEquals(byteArrayOf(1, 2), operations.syncedBytes, "sync ran after the bytes were written")
        assertEquals(setOf<CopyOption>(StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING), operations.moveOptions)
        assertContentEquals(byteArrayOf(1, 2), store.read(SecretPurpose.BACKUP_PASSPHRASE))
        clear(noBackup.resolve("secret-envelope"))
    }

    @Test
    fun `a failed write and a failed cleanup report their closed stage and never a path, and an Error keeps its identity`() = withStoragePaths { f ->
        val noBackup = f.dir("ce/no_backup")
        val directory = noBackup.resolve("secret-envelope")
        fun storeWith(operations: RecordingOperations) =
            LocalSecretEnvelopeStore(AppStoragePolicy(EnvelopeEnvironment(noBackup, f)), operations)
        val pathFailure = IOException("/data/42 Example St/secret-envelope")

        val syncFailed = assertFailsWith<EnvelopeStoreException> {
            storeWith(RecordingOperations(syncFailure = pathFailure)).replace(SecretPurpose.BACKUP_PASSPHRASE, byteArrayOf(1))
        }
        assertRedacted(syncFailed)
        assertEquals(EnvelopeStoreException.Stage.WRITE, syncFailed.stage)
        assertFalse(syncFailed.cleanupFailed)
        assertEquals(emptySet(), names(directory), "the temporary file was deleted")
        val notCreated = assertFailsWith<EnvelopeStoreException> {
            storeWith(RecordingOperations(createFailure = pathFailure)).replace(SecretPurpose.BACKUP_PASSPHRASE, byteArrayOf(1))
        }
        assertRedacted(notCreated)
        assertEquals(EnvelopeStoreException.Stage.WRITE, notCreated.stage)
        assertFalse(notCreated.cleanupFailed)

        val stuck = assertFailsWith<EnvelopeStoreException> {
            storeWith(RecordingOperations(moveFailure = pathFailure, deleteFailure = IllegalStateException(pathFailure.message)))
                .replace(SecretPurpose.BACKUP_PASSPHRASE, byteArrayOf(2))
        }
        assertRedacted(stuck)
        assertEquals(EnvelopeStoreException.Stage.MOVE, stuck.stage)
        assertTrue(stuck.cleanupFailed)
        assertEquals(1, names(directory).size, "the temporary file the failed cleanup left")
        val fatal = OutOfMemoryError("fatal")
        assertSame(fatal, assertFailsWith<OutOfMemoryError> { storeWith(RecordingOperations(syncFailure = fatal)).replace(SecretPurpose.BACKUP_PASSPHRASE, byteArrayOf(3)) })
        clear(directory)
    }

    @Test
    fun `the platform operations fail loudly instead of reporting success`() = withStoragePaths { f ->
        assertFailsWith<SyncFailedException> { PlatformEnvelopeFileOperations.sync(FileDescriptor()) }
        assertFailsWith<IOException> { PlatformEnvelopeFileOperations.delete(f.dir("occupied")) }
        val source = f.file("source", "x")
        val empty = Files.createDirectory(f.root.resolve("empty"))
        val moved = runCatching { PlatformEnvelopeFileOperations.move(source, empty, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        assertTrue(moved.isFailure, "an atomic move replaced a directory")
        assertTrue(Files.isRegularFile(source) && Files.isDirectory(empty))
        Files.delete(empty)
    }

    private fun assertRedacted(thrown: IOException) {
        assertEquals("secret envelope store failed", thrown.message)
        assertNull(thrown.cause)
        assertEquals(0, thrown.suppressed.size)
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

/** Records the seam calls and then performs the real operation, unless told to fail. */
private class RecordingOperations(
    private val watch: Path? = null,
    private val createFailure: Exception? = null,
    private val syncFailure: Throwable? = null,
    private val moveFailure: Exception? = null,
    private val deleteFailure: Exception? = null,
) : EnvelopeFileOperations {
    val events = mutableListOf<String>()
    var moveOptions = emptySet<CopyOption>()
    var syncedOpenFile = false
    var syncedBytes: ByteArray? = null

    override fun createTemporary(directory: File): File {
        createFailure?.let { throw it }
        return PlatformEnvelopeFileOperations.createTemporary(directory)
    }

    override fun sync(descriptor: FileDescriptor) {
        events += "sync"
        syncedOpenFile = descriptor.valid()
        syncedBytes = watch?.let { directory -> Files.list(directory).use { it.toList() }.single { it.toString().endsWith(".tmp") }.let(Files::readAllBytes) }
        syncFailure?.let { throw it }
        PlatformEnvelopeFileOperations.sync(descriptor)
    }

    override fun move(source: Path, target: Path, vararg options: CopyOption) {
        events += "move"
        moveOptions = options.toSet()
        moveFailure?.let { throw it }
        PlatformEnvelopeFileOperations.move(source, target, *options)
    }

    override fun delete(path: Path) {
        events += "delete"
        deleteFailure?.let { throw it }
        PlatformEnvelopeFileOperations.delete(path)
    }
}

/*
 * R4 receipt: 23/23 single-point mutants killed. Each compiled, its named test failed with java.lang.AssertionError, and
 * the production file was restored and its SHA-256 re-checked before the next mutant.
 * Production SHA-256 (LocalSecretEnvelopeStore.kt): 0E7E827E85C0C37AC2317EB03CBDBBFA750E503DDD73790FBAC22E5E52A91174
 * Test file the batch ran against: lines 1-226 of this file, i.e. every line before the blank line above this comment.
 * `head -n 226 android/app/src/test/kotlin/nz/myinspection/app/platform/LocalSecretEnvelopeStoreTest.kt | sha256sum`
 * prints 6875879efb22e321518878f6f3c40e182b39df0c1129365e68e643db4a4b4773 (LF line endings, per .gitattributes).
 * Per mutant: cmd /c android\gradlew.bat -p android --offline --no-build-cache -q :app:testDebugUnitTest
 * --tests nz.myinspection.app.platform.LocalSecretEnvelopeStoreTest. Evidence: _local/local-secret-store/r4/.
 * replace writes under...: S05 envelope file name changed, S06 in-place write instead of temp file and move,
 *   P04 missing directory no longer created
 * read returns null...: S03 read cap enlarged, S04 absent read raising
 * a failed move or read...: S07 redaction removed, S08 original exception kept as the cause, S13 temporary-file cleanup
 *   removed, S14 MOVE stage labelled WRITE, S15 READ stage labelled LOCATE
 * each call asks the policy again...: S02 location cached at construction, S09 redaction narrowed to IOException
 * replace syncs the temporary file...: S10 sync removed, S11 ATOMIC_MOVE dropped, S21 sync before the write
 * a failed write and a failed cleanup...: S12 cleanup failure not reported, S16/S17 WRITE stages labelled MOVE,
 *   S19 cleanup catch narrowed to IOException, S20 stage catch widened to Throwable
 * the platform operations fail loudly...: P01 sync removed, P02 ATOMIC_MOVE dropped, P03 unchecked File.delete
 */
package nz.myinspection.app.platform

import java.io.File
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppStoragePolicyTest {
    @Test
    fun `each protected category uses its own credential encrypted no-backup subdirectory`() {
        val root = File(
            createTempDirectory("credential-encrypted").toFile(),
            "42 Example St/Jane Tenant/secret",
        )
        val policy = AppStoragePolicy(FakeStorageEnvironment(noBackup = root))

        val expectedDirectories = mapOf(
            SecureStorageNamespace.DATABASE to "database",
            SecureStorageNamespace.SETTINGS to "settings",
            SecureStorageNamespace.RECEIPTS to "receipts",
            SecureStorageNamespace.SECRET_ENVELOPE to "secret-envelope",
            SecureStorageNamespace.RESTORE_JOURNAL to "restore-journal",
            SecureStorageNamespace.STAGING_METADATA to "staging",
        )
        expectedDirectories.forEach { (namespace, subdirectory) ->
            val location = policy.location(namespace)
            val rootLocation = assertIs<StorageRoot.CredentialEncryptedNoBackup>(location.root, namespace.name)
            assertEquals(File(root, subdirectory).canonicalFile, location.directory.canonicalFile, namespace.name)
            assertEquals(root.canonicalFile, rootLocation.directory.canonicalFile, namespace.name)
            assertNoSensitiveText(location, rootLocation)
        }
    }

    @Test
    fun `device protected environment converts to credential encrypted before a protected route is exposed`() {
        val deviceRoot = createTempDirectory("device-protected").toFile()
        val credentialRoot = File(
            createTempDirectory("credential-encrypted").toFile(),
            "42 Example St/Jane Tenant/secret",
        )
        val credential = FakeStorageEnvironment(noBackup = credentialRoot)
        // Using any one unconverted value (no-backup directory, app root or DP root) makes the boundary refuse.
        val deviceProtected = FakeStorageEnvironment(
            noBackup = deviceRoot,
            deviceProtected = true,
            credentialEnvironment = credential,
            appDataDir = deviceRoot,
            deviceProtectedDataDir = credential.appDataDir,
        )
        val converted = runCatching {
            AppStoragePolicy(deviceProtected).location(SecureStorageNamespace.SECRET_ENVELOPE)
        }

        assertTrue(converted.isSuccess, "credential conversion must expose a protected route")
        val location = converted.getOrThrow()
        val root = assertIs<StorageRoot.CredentialEncryptedNoBackup>(location.root)
        assertEquals(1, deviceProtected.credentialEnvironmentRequests)
        assertEquals(File(credentialRoot, "secret-envelope").canonicalFile, location.directory.canonicalFile)
        assertEquals(credentialRoot.canonicalFile, root.directory.canonicalFile)
        assertNoSensitiveText(location, root)
    }

    @Test
    fun `environment that remains device protected is rejected without exposing its path`() {
        val sensitiveRoot = File(createTempDirectory("storage-policy").toFile(), "42 Example St/Jane Tenant/secret")
        val deviceProtected = FakeStorageEnvironment(noBackup = sensitiveRoot, deviceProtected = true)
        val thrown = assertFailsWith<IllegalStateException> { AppStoragePolicy(deviceProtected) }

        assertNoSensitiveText(thrown, thrown.message ?: "")
    }

    @Test
    fun `credential conversion probe and no-backup lookup failures are rejected without preserving sensitive exceptions`() {
        val root = createTempDirectory("storage-policy").toFile()
        val sensitiveFailure = IllegalStateException("cannot inspect 42 Example St Jane Tenant Authorization Bearer secret")
        val failures = listOf(
            FakeStorageEnvironment(noBackup = root, deviceProtected = true, credentialEnvironmentFailure = sensitiveFailure),
            FakeStorageEnvironment(noBackup = root, deviceProtected = true, credentialEnvironment = FakeStorageEnvironment(noBackup = root, deviceProtectedFailure = sensitiveFailure)),
            FakeStorageEnvironment(noBackup = root, noBackupFailure = sensitiveFailure),
            FakeStorageEnvironment(noBackup = root, noBackupFailure = IOException(sensitiveFailure.message)),
            FakeStorageEnvironment(noBackup = root, deviceProtected = true, credentialEnvironmentFailure = SecurityException(sensitiveFailure.message)),
        )
        failures.forEach { environment ->
            val thrown = assertFailsWith<IllegalStateException> { AppStoragePolicy(environment) }
            assertNoSensitiveText(thrown, thrown.message ?: "", thrown.cause?.toString() ?: "")
            assertEquals(null, thrown.cause)
        }
    }

    @Test
    fun `fatal no-backup lookup error propagates with its identity`() {
        val root = createTempDirectory("storage-policy").toFile()
        val fatal = OutOfMemoryError("sentinel")
        val environment = FakeStorageEnvironment(noBackup = root, noBackupFailure = fatal)

        assertSame(fatal, assertFailsWith<OutOfMemoryError> { AppStoragePolicy(environment) })
    }

    @Test
    fun `fatal media probe error propagates with its identity`() {
        val root = createTempDirectory("storage-policy").toFile()
        val fatal = ThreadDeath()
        val environment = FakeStorageEnvironment(
            noBackup = root,
            externalMedia = File(root, "external"),
            usableBytesFailure = fatal,
        )

        assertSame(fatal, assertFailsWith<ThreadDeath> { AppStoragePolicy(environment).mediaLocation(1L) })
    }

    @Test
    fun `media returns unavailable for missing unmounted and read-only app-specific external volumes`() {
        val root = createTempDirectory("storage-policy").toFile()
        val sensitiveExternal = File(root, "Android/data/nz.myinspection.app/files/42 Example St/Jane Tenant/secret")
        val missingVolumeDirectory = File(root, "missing-volume-directory")
        assertFalse(missingVolumeDirectory.exists())
        val unavailableEnvironments = listOf(
            "missing directory" to FakeStorageEnvironment(noBackup = root, externalMedia = null),
            "non-null absent path reported unwritable" to FakeStorageEnvironment(
                noBackup = root,
                externalMedia = missingVolumeDirectory,
                appSpecificExternalMediaWritable = false,
            ),
            "unmounted volume" to FakeStorageEnvironment(
                noBackup = root,
                externalMedia = sensitiveExternal,
                externalVolumeState = ExternalMediaVolumeState.UNMOUNTED,
            ),
            "read-only volume" to FakeStorageEnvironment(
                noBackup = root,
                externalMedia = sensitiveExternal,
                externalVolumeState = ExternalMediaVolumeState.MOUNTED_READ_ONLY,
            ),
            "mounted but unwritable directory" to FakeStorageEnvironment(
                noBackup = root,
                externalMedia = sensitiveExternal,
                appSpecificExternalMediaWritable = false,
            ),
        )

        unavailableEnvironments.forEach { (label, environment) ->
            val result = AppStoragePolicy(environment).mediaLocation(requestedBytes = 1L)

            assertEquals(MediaStorageLocation.Unavailable, result, label)
            assertNoSensitiveText(result)
        }
        listOf("", " ").forEach { blankPath ->
            val delegate = FakeStorageEnvironment(noBackup = root, externalMedia = File(blankPath))
            val probes = mutableListOf<String>()
            val environment = object : AppStorageEnvironment by delegate {
                override fun appSpecificExternalMediaState(directory: File): ExternalMediaVolumeState {
                    probes += "state"
                    return delegate.appSpecificExternalMediaState(directory)
                }
                override fun isAppSpecificExternalMediaWritable(directory: File): Boolean {
                    probes += "writable"
                    return delegate.isAppSpecificExternalMediaWritable(directory)
                }
                override fun usableBytes(directory: File): Long {
                    probes += "space"
                    return delegate.usableBytes(directory)
                }
            }
            assertEquals(MediaStorageLocation.Unavailable, AppStoragePolicy(environment).mediaLocation(1L), "blank external path")
            assertTrue(probes.isEmpty(), "blank external path must be rejected before probes: $probes")
        }
    }

    @Test
    fun `media reports insufficient space below request and permits equal or greater usable bytes without a shared fallback`() {
        val root = createTempDirectory("storage-policy").toFile()
        val external = File(root, "Android/data/nz.myinspection.app/files/42 Example St/Jane Tenant/secret")
        val lowSpace = AppStoragePolicy(FakeStorageEnvironment(noBackup = root, externalMedia = external, usableBytes = 99L))
        val exactSpace = AppStoragePolicy(FakeStorageEnvironment(noBackup = root, externalMedia = external, usableBytes = 100L))
        val ampleSpace = AppStoragePolicy(FakeStorageEnvironment(noBackup = root, externalMedia = external, usableBytes = 101L))

        val insufficient = lowSpace.mediaLocation(requestedBytes = 100L)
        val available = assertIs<MediaStorageLocation.Available>(exactSpace.mediaLocation(requestedBytes = 100L))
        val ampleAvailable = assertIs<MediaStorageLocation.Available>(ampleSpace.mediaLocation(requestedBytes = 100L))

        val rootLocation = assertIs<StorageRoot.AppSpecificExternalMedia>(available.root)

        assertEquals(MediaStorageLocation.InsufficientSpace, insufficient)
        assertEquals(external.canonicalFile, available.root.directory.canonicalFile)
        assertEquals(external.canonicalFile, ampleAvailable.root.directory.canonicalFile)
        assertNoSensitiveText(insufficient, available, available.root, rootLocation, ampleAvailable, ampleAvailable.root)
    }

    @Test
    fun `each media probe failure closes unavailable without preserving its path-bearing exception`() {
        val root = createTempDirectory("storage-policy").toFile()
        val sensitiveExternal = File(root, "Android/data/nz.myinspection.app/files/42 Example St/Jane Tenant/secret")
        val message = "cannot inspect $sensitiveExternal Authorization Bearer secret"
        val delegate = FakeStorageEnvironment(noBackup = root, externalMedia = sensitiveExternal)
        listOf(IllegalStateException(message), IOException(message), SecurityException(message)).forEach { failure ->
            val environments = mapOf(
                "directory" to object : AppStorageEnvironment by delegate {
                    override val appSpecificExternalMediaDir: File? get() = throw failure
                },
                "state" to object : AppStorageEnvironment by delegate {
                    override fun appSpecificExternalMediaState(directory: File): ExternalMediaVolumeState = throw failure
                },
                "writable" to object : AppStorageEnvironment by delegate {
                    override fun isAppSpecificExternalMediaWritable(directory: File): Boolean = throw failure
                },
                "space" to FakeStorageEnvironment(noBackup = root, externalMedia = sensitiveExternal, usableBytesFailure = failure),
            )
            environments.forEach { (label, environment) ->
                val probed = runCatching { AppStoragePolicy(environment).mediaLocation(requestedBytes = 1L) }
                val case = "$label ${failure.javaClass.simpleName}"

                assertTrue(probed.isSuccess, "$case must close to an unavailable result")
                assertEquals(MediaStorageLocation.Unavailable, probed.getOrThrow(), case)
                assertNoSensitiveText(probed.getOrThrow())
            }
        }
    }

    @Test
    fun `roots the boundary refuses close with a fixed message and no cause`() = withStoragePaths { f ->
        // Rows 0 and 1 lie inside their app root, so only the DP exclusion refuses them; row 0 only after real resolution.
        val environments = listOf(
            f.environment(f.alias("ce/no_backup", f.dir("dp/tenant-secret")), appDataDir = f.root),
            f.environment(f.dp.resolve("42 Example St/Jane Tenant/secret"), appDataDir = f.dp),
            f.environment(f.external.resolve("no_backup")),
            f.environment(f.ce),
        )
        environments.forEachIndexed { index, environment ->
            val thrown = assertFailsWith<IllegalStateException>("root $index") { AppStoragePolicy(environment) }
            assertEquals("credential-encrypted storage unavailable", thrown.message, "root $index")
            assertNull(thrown.cause, "root $index")
            assertNoSensitiveText(thrown)
        }
    }

    @Test
    fun `the protected root is the real directory the boundary saved`() = withStoragePaths { f ->
        val real = f.dir("ce/real_no_backup")
        val alias = f.alias("ce/no_backup", real)
        val policy = AppStoragePolicy(f.environment(alias))
        f.retarget(alias, f.dp)

        val location = runCatching { policy.location(SecureStorageNamespace.DATABASE) }.getOrNull()

        assertEquals(real.toFile(), location?.root?.directory)
        assertEquals(real.resolve("database").toFile(), location?.directory)
    }

    @Test
    fun `each location call returns the directory the boundary checked`() = withStoragePaths { f ->
        val noBackup = f.dir("ce/no_backup")
        val policy = AppStoragePolicy(f.environment(noBackup))
        assertEquals(noBackup.resolve("database").toFile(), policy.location(SecureStorageNamespace.DATABASE).directory)
        val settingsTarget = f.dir("ce/no_backup/elsewhere")
        f.alias("ce/no_backup/settings", settingsTarget)
        f.alias("ce/no_backup/database", f.dir("dp/tenant-secret"))
        f.alias("ce/no_backup/receipts", f.external)

        assertEquals(settingsTarget.toFile(), policy.location(SecureStorageNamespace.SETTINGS).directory)
        assertEquals(noBackup.resolve("staging").toFile(), policy.location(SecureStorageNamespace.STAGING_METADATA).directory)
        listOf(SecureStorageNamespace.DATABASE, SecureStorageNamespace.RECEIPTS).forEach { namespace ->
            val thrown = assertFailsWith<IllegalStateException>(namespace.name) { policy.location(namespace) }
            assertEquals("credential-encrypted storage unavailable", thrown.message, namespace.name)
            assertNull(thrown.cause, namespace.name)
        }
    }

    private fun StoragePathFixture.environment(noBackup: Path, appDataDir: Path = ce): AppStorageEnvironment =
        FakeStorageEnvironment(noBackup = noBackup.toFile(), appDataDir = appDataDir.toFile(), deviceProtectedDataDir = dp.toFile())

    private fun assertNoSensitiveText(vararg values: Any) {
        val prohibited = listOf("42 Example St", "Jane Tenant", "Authorization", "Bearer secret")
        values.forEach { value ->
            prohibited.forEach { text -> assertFalse(value.toString().contains(text), "leaked '$text' from $value") }
        }
    }
}

private class FakeStorageEnvironment(
    private val noBackup: File,
    private val externalMedia: File? = null,
    private val usableBytes: Long = 1_000L,
    private val deviceProtected: Boolean = false,
    private val deviceProtectedFailure: Throwable? = null,
    private val credentialEnvironment: AppStorageEnvironment? = null,
    private val credentialEnvironmentFailure: Throwable? = null,
    private val externalVolumeState: ExternalMediaVolumeState = ExternalMediaVolumeState.MOUNTED,
    private val appSpecificExternalMediaWritable: Boolean = true,
    private val noBackupFailure: Throwable? = null,
    private val usableBytesFailure: Throwable? = null,
    override val appDataDir: File = noBackup.parentFile ?: noBackup,
    override val deviceProtectedDataDir: File = File(appDataDir.parentFile ?: appDataDir, "device-protected"),
) : AppStorageEnvironment {
    var credentialEnvironmentRequests = 0
        private set

    override val isDeviceProtectedStorage: Boolean
        get() {
            deviceProtectedFailure?.let { throw it }
            return deviceProtected
        }
    override val noBackupFilesDir: File
        get() {
            noBackupFailure?.let { throw it }
            return noBackup
        }
    override val appSpecificExternalMediaDir: File? = externalMedia

    override fun credentialEncryptedContext(): AppStorageEnvironment {
        credentialEnvironmentRequests += 1
        credentialEnvironmentFailure?.let { throw it }
        return credentialEnvironment ?: this
    }

    override fun appSpecificExternalMediaState(directory: File): ExternalMediaVolumeState {
        check(directory == externalMedia)
        return externalVolumeState
    }

    override fun isAppSpecificExternalMediaWritable(directory: File): Boolean {
        check(directory == externalMedia)
        return appSpecificExternalMediaWritable
    }

    override fun usableBytes(directory: File): Long {
        check(directory == externalMedia)
        usableBytesFailure?.let { throw it }
        return usableBytes
    }
}

/*
 * R4 receipt (policy over StoragePathBoundary): 45/45 single-point mutants killed. For each, the test report was
 * produced (it compiled), its named test below failed, the failures included java.lang.AssertionError, and the
 * production file was restored and its SHA-256 re-checked before the next mutant.
 * Production SHA-256: D302EB195A4184F7352F62E8509913B8CF55173E0DD6FCEC47CACCDD43682BB5
 * Pre-receipt test SHA-256: 4CAD2C2422064CDFCE3EBBC705573701A02F236120171C65468DA72D0333050C; this file is those bytes plus this appended comment.
 * Per mutant: cmd /c android\gradlew.bat -p android --offline --no-daemon --no-build-cache -q
 *   :app:testDebugUnitTest --tests nz.myinspection.app.platform.AppStoragePolicyTest
 * Evidence: _local/storage-policy/successor-r4-5/{results.jsonl,Mxx.log}. Earlier batches ran on prior revisions and
 * verify nothing here: successor-r4 left M10 alive (every DP row there also lay outside its app root, so containment
 * alone refused it); R3 rounds 1 and 2 then found M36-M45 absent; successor-r4-4 was stopped during its first mutant.
 * Direct path obligations (blank roots, normalization, real aliases, DP exclusion, error classes) moved with their
 * tests to StoragePathBoundaryTest. The superseded fd1dd18c receipt (37/37, IDs M01-M42 with M07-M11 transferred to
 * the Android card) pinned the removed canonicalFile code.
 * each protected category uses its own credential encrypted no-backup subdirectory:
 *   M01-M06 one route each, M31 root text, M32 location text, M35 root returned as the location
 * device protected environment converts to credential encrypted...: M07 no conversion,
 *   M39-M41 candidate, app root or DP root read from the unconverted environment
 * environment that remains device protected...: M08 DP environment accepted after conversion
 * credential conversion probe and no-backup lookup failures...: M18 original exception kept as cause,
 *   M42/M43 root catch narrowed to RuntimeException / IllegalStateException
 * fatal no-backup lookup error propagates with its identity: M19 root catch widened to Throwable
 * roots the boundary refuses close with a fixed message and no cause:
 *   M09 app root widened to its parent, M10 DP exclusion moved to a missing child, M17 refused root throws NPE
 * the protected root is the real directory the boundary saved: M11 raw root kept, M12 child anchored on raw root
 * each location call returns the directory the boundary checked:
 *   M13 unchecked path returned, M14 refused child falls back, M15 message varies, M16 cause attached
 * media returns unavailable...: M20 missing directory, M21 whitespace path, M22 read-only, M23 writable guard
 * media reports insufficient space...: M24 equal refused, M25 low accepted, M26 ample refused, M30 root directory,
 *   M33 root text, M34 available text, M36-M38 space, state or writability probed on another directory
 * each media probe failure closes unavailable...: M27 reported as low space, M28 failure escapes,
 *   M44/M45 media catch narrowed to RuntimeException / IllegalStateException
 * fatal media probe error propagates with its identity: M29 media catch widened to Throwable
 */

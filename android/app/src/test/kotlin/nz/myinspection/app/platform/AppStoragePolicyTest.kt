package nz.myinspection.app.platform

import java.io.File
import java.io.IOException
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppStoragePolicyTest {
    @Test
    fun `each protected category uses its own credential encrypted no-backup subdirectory`() = withStoragePaths { f ->
        val root = f.dir("ce/42 Example St/Jane Tenant/secret").toFile()
        val policy = AppStoragePolicy(FakeStorageEnvironment(
            noBackup = root,
            appDataDir = f.ce.toFile(),
            deviceProtectedDataDir = f.dp.toFile(),
        ))

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
            assertEquals(File(root, subdirectory), location.directory, namespace.name)
            assertEquals(root, rootLocation.directory, namespace.name)
            assertNoSensitiveText(location, rootLocation)
        }
    }

    @Test
    fun `device protected environment converts to credential encrypted before a protected route is exposed`() = withStoragePaths { f ->
        val credentialRoot = f.dir("ce/42 Example St/Jane Tenant/secret").toFile()
        val credential = FakeStorageEnvironment(
            noBackup = credentialRoot,
            appDataDir = f.ce.toFile(),
            deviceProtectedDataDir = f.dp.toFile(),
        )
        val deviceProtected = FakeStorageEnvironment(
            noBackup = f.dp.toFile(),
            deviceProtected = true,
            credentialEnvironment = credential,
        )
        val converted = runCatching {
            AppStoragePolicy(deviceProtected).location(SecureStorageNamespace.SECRET_ENVELOPE)
        }

        assertTrue(converted.isSuccess, "credential conversion must expose a protected route")
        val location = converted.getOrThrow()
        val root = assertIs<StorageRoot.CredentialEncryptedNoBackup>(location.root)
        assertEquals(1, deviceProtected.credentialEnvironmentRequests)
        assertEquals(File(credentialRoot, "secret-envelope"), location.directory)
        assertEquals(credentialRoot, root.directory)
        assertNoSensitiveText(location, root)
    }

    @Test
    fun `environment that remains device protected is rejected without exposing its path`() = withStoragePaths { f ->
        val sensitiveRoot = f.dir("dp/42 Example St/Jane Tenant/secret").toFile()
        val deviceProtected = FakeStorageEnvironment(noBackup = sensitiveRoot, deviceProtected = true)

        assertFixedFailure { AppStoragePolicy(deviceProtected) }
    }

    @Test
    fun `credential environment and boundary lookup failures use a fixed error without a cause`() = withStoragePaths { f ->
        val root = f.dir("ce/no-backup").toFile()
        val sensitiveFailure = IllegalStateException("cannot inspect 42 Example St Jane Tenant Authorization Bearer secret")
        val failedPath = object : File(root.path) {
            override fun toPath(): Path = throw IOException("42 Example St Jane Tenant Authorization Bearer secret")
        }
        val failures = listOf(
            FakeStorageEnvironment(noBackup = root, deviceProtected = true, credentialEnvironmentFailure = sensitiveFailure),
            FakeStorageEnvironment(
                noBackup = root,
                deviceProtected = true,
                credentialEnvironment = FakeStorageEnvironment(noBackup = root, deviceProtectedFailure = sensitiveFailure),
            ),
            FakeStorageEnvironment(noBackup = root, noBackupFailure = sensitiveFailure),
            FakeStorageEnvironment(noBackup = failedPath),
        )
        failures.forEach { environment -> assertFixedFailure { AppStoragePolicy(environment) } }
    }

    @Test
    fun `policy rejects an actual device protected root with a fixed failure`() = withStoragePaths { f ->
        val environment = FakeStorageEnvironment(
            noBackup = f.dir("dp/42 Example St/Jane Tenant/secret").toFile(),
            appDataDir = f.dp.toFile(),
            deviceProtectedDataDir = f.dp.toFile(),
        )

        assertFixedFailure { AppStoragePolicy(environment) }
    }

    @Test
    fun `policy returns checked root and child after source alias retarget`() = withStoragePaths { f ->
        val savedRoot = f.dir("ce/no-backup")
        val source = f.alias("ce/source", savedRoot)
        val policy = AppStoragePolicy(FakeStorageEnvironment(
            noBackup = source.toFile(), appDataDir = f.ce.toFile(), deviceProtectedDataDir = f.dp.toFile(),
        ))
        f.retarget(source, f.external)
        val checkedChild = f.dir("ce/no-backup/checked")
        f.alias("ce/no-backup/settings", checkedChild)

        val location = policy.location(SecureStorageNamespace.SETTINGS)
        val root = assertIs<StorageRoot.CredentialEncryptedNoBackup>(location.root)
        assertEquals(savedRoot.toFile(), root.directory)
        assertEquals(checkedChild.toFile(), location.directory)
        assertNoSensitiveText(location, root)
    }

    @Test
    fun `policy rejects escaped children and saved root replacements with a fixed failure`() {
        listOf("child alias", "saved existing root", "saved missing root").forEach { vector ->
            withStoragePaths { f ->
                val savedRoot = if (vector == "saved missing root") f.ce.resolve("no-backup") else f.dir("ce/no-backup")
                val policy = AppStoragePolicy(FakeStorageEnvironment(
                    noBackup = savedRoot.toFile(), appDataDir = f.ce.toFile(), deviceProtectedDataDir = f.dp.toFile(),
                ))
                when (vector) {
                    "child alias" -> f.alias("ce/no-backup/settings", f.external)
                    "saved existing root" -> {
                        f.park(savedRoot, "parked-root")
                        f.alias("ce/no-backup", f.external)
                    }
                    "saved missing root" -> f.alias("ce/no-backup", f.external)
                }

                assertFixedFailure(vector) { policy.location(SecureStorageNamespace.SETTINGS) }
            }
        }
    }

    @Test
    fun `fatal no-backup lookup and boundary root errors propagate with their identity`() = withStoragePaths { f ->
        val root = f.dir("ce/no-backup").toFile()
        val fatal = OutOfMemoryError("sentinel")
        val environment = FakeStorageEnvironment(noBackup = root, noBackupFailure = fatal)

        assertSame(fatal, assertFailsWith<OutOfMemoryError> { AppStoragePolicy(environment) })
        val failedPath = object : File(root.path) {
            override fun toPath(): Path = throw fatal
        }
        assertSame(fatal, assertFailsWith<OutOfMemoryError> {
            AppStoragePolicy(FakeStorageEnvironment(noBackup = failedPath))
        })
    }

    @Test
    fun `fatal media probe error propagates with its identity`() = withStoragePaths { f ->
        val root = f.dir("ce/no-backup").toFile()
        val fatal = ThreadDeath()
        val environment = FakeStorageEnvironment(
            noBackup = root,
            externalMedia = f.external.toFile(),
            usableBytesFailure = fatal,
        )

        assertSame(fatal, assertFailsWith<ThreadDeath> { AppStoragePolicy(environment).mediaLocation(1L) })
    }

    @Test
    fun `media returns unavailable for missing unmounted and read-only app-specific external volumes`() = withStoragePaths { f ->
        val root = f.dir("ce/no-backup").toFile()
        val sensitiveExternal = File(f.external.toFile(), "Android/data/nz.myinspection.app/files/42 Example St/Jane Tenant/secret")
        val missingVolumeDirectory = File(f.external.toFile(), "missing-volume-directory")
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
    fun `media reports insufficient space below request and permits equal or greater usable bytes without a shared fallback`() = withStoragePaths { f ->
        val root = f.dir("ce/no-backup").toFile()
        val external = File(f.external.toFile(), "Android/data/nz.myinspection.app/files/42 Example St/Jane Tenant/secret")
        val lowSpace = AppStoragePolicy(FakeStorageEnvironment(noBackup = root, externalMedia = external, usableBytes = 99L))
        val exactSpace = AppStoragePolicy(FakeStorageEnvironment(noBackup = root, externalMedia = external, usableBytes = 100L))
        val ampleSpace = AppStoragePolicy(FakeStorageEnvironment(noBackup = root, externalMedia = external, usableBytes = 101L))

        val insufficient = lowSpace.mediaLocation(requestedBytes = 100L)
        val available = assertIs<MediaStorageLocation.Available>(exactSpace.mediaLocation(requestedBytes = 100L))
        val ampleAvailable = assertIs<MediaStorageLocation.Available>(ampleSpace.mediaLocation(requestedBytes = 100L))
        val rootLocation = assertIs<StorageRoot.AppSpecificExternalMedia>(available.root)

        assertEquals(MediaStorageLocation.InsufficientSpace, insufficient)
        assertEquals(external, available.root.directory)
        assertEquals(external, ampleAvailable.root.directory)
        assertNoSensitiveText(insufficient, available, available.root, rootLocation, ampleAvailable, ampleAvailable.root)
    }

    @Test
    fun `media state probe failure closes unavailable without preserving its path-bearing exception`() = withStoragePaths { f ->
        val root = f.dir("ce/no-backup").toFile()
        val sensitiveExternal = File(f.external.toFile(), "Android/data/nz.myinspection.app/files/42 Example St/Jane Tenant/secret")
        val environment = FakeStorageEnvironment(
            noBackup = root,
            externalMedia = sensitiveExternal,
            usableBytesFailure = IllegalStateException("cannot inspect $sensitiveExternal Authorization Bearer secret"),
        )

        val probed = runCatching { AppStoragePolicy(environment).mediaLocation(requestedBytes = 1L) }
        assertTrue(probed.isSuccess, "media probe failure must close to an unavailable result")
        val result = probed.getOrThrow()

        assertEquals(MediaStorageLocation.Unavailable, result)
        assertNoSensitiveText(result)
    }

    private fun assertFixedFailure(label: String = "credential failure", action: () -> Unit) {
        val thrown = assertFailsWith<IllegalStateException>(label, action)
        assertEquals("credential-encrypted storage unavailable", thrown.message, label)
        assertEquals(null, thrown.cause, label)
        assertNoSensitiveText(thrown, thrown.message ?: "", thrown.cause?.toString() ?: "")
    }

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
        usableBytesFailure?.let { throw it }
        return usableBytes
    }
}

/*
 * R4: 30/30 final-pin mutants killed; compile 0/test 1; named java.lang.AssertionError; 12 primaries.
 * Production SHA-256: C031E65298966162B726D77EE7034509B72CB96BE3519590D8756E57EDBB13B7
 * Executable test snapshot SHA-256: D06827F962CB7858B36F3A052DB138FDF64C09A71F551B8EF794C4274A8290D6; final test differs only by this receipt.
 * DoD: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
 * Compile each: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:compileDebugUnitTestKotlin
 * Test each: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest --tests nz.myinspection.app.platform.AppStoragePolicyTest
 * Evidence: _local/storage-policy-remote/{mutation-plan.json,mutations/,final-mutation-audit.json,final-receipt.json}; IDs map old obligations in manifest.
 * Verified: compile 0, test 1, primary AssertionError, exact byte restore; PRM13 must hit the non-null absent-path label.
 * Restored full DoD and executable-prefix equality are recorded in final-receipt.json; no old snapshot is reused.
 * PRM10 -> AppStoragePolicyTest#credential environment and boundary lookup failures use a fixed error without a cause
 * PRM07, PRM09 -> AppStoragePolicyTest#device protected environment converts to credential encrypted before a protected route is exposed
 * PRM01, PRM02, PRM03, PRM04, PRM05, PRM06, PRM19, PRM21 -> AppStoragePolicyTest#each protected category uses its own credential encrypted no-backup subdirectory
 * PRM08 -> AppStoragePolicyTest#environment that remains device protected is rejected without exposing its path
 * PRM24 -> AppStoragePolicyTest#fatal media probe error propagates with its identity
 * PRM25 -> AppStoragePolicyTest#fatal no-backup lookup and boundary root errors propagate with their identity
 * PRM14, PRM15, PRM16, PRM20, PRM22 -> AppStoragePolicyTest#media reports insufficient space below request and permits equal or greater usable bytes without a shared fallback
 * PRM11, PRM12, PRM13, PRM26 -> AppStoragePolicyTest#media returns unavailable for missing unmounted and read-only app-specific external volumes
 * PRM17, PRM18 -> AppStoragePolicyTest#media state probe failure closes unavailable without preserving its path-bearing exception
 * PRM23 -> AppStoragePolicyTest#policy rejects an actual device protected root with a fixed failure
 * PRM27, PRM30 -> AppStoragePolicyTest#policy rejects escaped children and saved root replacements with a fixed failure
 * PRM28, PRM29 -> AppStoragePolicyTest#policy returns checked root and child after source alias retarget
 */

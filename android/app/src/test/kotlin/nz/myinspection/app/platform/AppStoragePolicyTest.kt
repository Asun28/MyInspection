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
        // Using any one unconverted value (no-backup directory, app root or DP root) makes the boundary refuse.
        val deviceProtected = FakeStorageEnvironment(
            noBackup = f.dp.toFile(),
            deviceProtected = true,
            credentialEnvironment = credential,
            appDataDir = f.dp.toFile(),
            deviceProtectedDataDir = f.ce.toFile(),
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
            FakeStorageEnvironment(noBackup = root, noBackupFailure = IOException(sensitiveFailure.message)),
            FakeStorageEnvironment(noBackup = root, deviceProtected = true, credentialEnvironmentFailure = SecurityException(sensitiveFailure.message)),
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
    fun `policy rejects roots outside the app root or equal to it with a fixed failure`() = withStoragePaths { f ->
        listOf(f.external.resolve("no-backup"), f.ce).forEach { noBackup ->
            val environment = FakeStorageEnvironment(
                noBackup = noBackup.toFile(), appDataDir = f.ce.toFile(), deviceProtectedDataDir = f.dp.toFile(),
            )

            assertFixedFailure(noBackup.fileName.toString()) { AppStoragePolicy(environment) }
        }
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
    fun `each media probe failure closes unavailable without preserving its path-bearing exception`() = withStoragePaths { f ->
        val root = f.dir("ce/no-backup").toFile()
        val sensitiveExternal = File(f.external.toFile(), "Android/data/nz.myinspection.app/files/42 Example St/Jane Tenant/secret")
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
        check(directory == externalMedia)
        usableBytesFailure?.let { throw it }
        return usableBytes
    }
}

/*
 * R4 receipt (T1-APP-STORAGE-POLICY-TESTS): a fresh named set of 44 single-point mutants over the unchanged production
 * file. Before this change the previous test file (SHA-256 659ECCD74F90864270B3F83F7E4FAE379FD0DC51D47BC2224E711420DC7684E2)
 * killed 36/44; O09, O36, O40, O41 and O42-O45 survived. On this file all 44/44 are killed: each compiled, its named
 * test below failed, the failures included java.lang.AssertionError, and the production file was restored and its
 * SHA-256 re-checked before the next mutant.
 * Production SHA-256: C031E65298966162B726D77EE7034509B72CB96BE3519590D8756E57EDBB13B7 (unchanged by this card)
 * Pre-receipt test SHA-256: 8AADCB138B0652B7D021E0C7D0D6E7EA873B374E35F044144262CDB0907322B1; this file is those bytes plus this appended comment.
 * Per mutant: cmd /c android\gradlew.bat -p android --offline --no-daemon --no-build-cache -q
 *   :app:testDebugUnitTest --tests nz.myinspection.app.platform.AppStoragePolicyTest
 * Evidence (local, not committed): _local/storage-policy-tests/{before,after}/{results.jsonl,Oxx.log}. The previous
 * 30-mutant receipt (PRM01-PRM30) pinned the previous test bytes; its definitions are not in this repository.
 * each protected category uses its own credential encrypted no-backup subdirectory:
 *   O01-O06 one route each, O31 root text, O32 location text, O35 root returned as the location
 * device protected environment converts to credential encrypted...: O07 no conversion,
 *   O39-O41 candidate, app root or DP root read from the unconverted environment
 * environment that remains device protected...: O08 DP environment accepted after conversion
 * credential environment and boundary lookup failures...: O18 original exception kept as cause,
 *   O42/O43 constructor catch narrowed to RuntimeException / IllegalStateException
 * policy rejects an actual device protected root...: O10 DP exclusion moved to a missing child
 * policy rejects roots outside the app root or equal to it...: O09 app root widened to its parent
 * policy returns checked root and child after source alias retarget: O11 raw root kept, O12 child anchored on raw root,
 *   O13 unchecked path returned
 * policy rejects escaped children and saved root replacements...: O14 refused child falls back, O15 message varies,
 *   O16 cause attached
 * fatal no-backup lookup and boundary root errors...: O19 constructor catch widened to Throwable
 * fatal media probe error propagates with its identity: O29 media catch widened to Throwable
 * media returns unavailable...: O20 missing directory, O21 whitespace path, O22 read-only, O23 writable guard
 * media reports insufficient space...: O24 equal refused, O25 low accepted, O26 ample refused, O30 root directory,
 *   O33 root text, O34 available text, O36-O38 space, state or writability probed on another directory
 * each media probe failure closes unavailable...: O27 reported as low space, O28 failure escapes,
 *   O44/O45 media catch narrowed to RuntimeException / IllegalStateException
 * (O17 is not used: create is wrapped in checkNotNull inside the catch, so replacing it with !! is an equivalent
 *   mutant; the NullPointerException is caught and refused exactly like the IllegalStateException.)
 */

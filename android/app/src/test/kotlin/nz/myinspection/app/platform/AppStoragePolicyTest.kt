package nz.myinspection.app.platform

import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
        val deviceProtected = FakeStorageEnvironment(
            noBackup = deviceRoot,
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
        )
        failures.forEach { environment ->
            val thrown = assertFailsWith<IllegalStateException> { AppStoragePolicy(environment) }
            assertNoSensitiveText(thrown, thrown.message ?: "", thrown.cause?.toString() ?: "")
            assertEquals(null, thrown.cause)
        }
    }

    @Test
    fun `credential no-backup candidate must be a normalized child of a non-device-protected app root`() {
        val base = createTempDirectory("storage-policy").toFile()
        val credentialRoot = File(base, "credential-encrypted").apply { mkdir() }
        val deviceProtectedRoot = File(base, "device-protected").apply { mkdir() }
        val candidates = listOf(
            "normalized credential child" to (File(credentialRoot, "nested/../metadata") to true),
            "app root itself" to (credentialRoot to false),
            "same-prefix sibling" to (File(base, "credential-encrypted-shadow/metadata") to false),
            "outside app root" to (File(base, "outside/metadata") to false),
            "device-protected child" to (File(deviceProtectedRoot, "metadata") to false),
        )

        candidates.forEach { (label, candidate) ->
            assertEquals(
                candidate.second,
                isCredentialEncryptedNoBackupDirectory(candidate.first, credentialRoot, deviceProtectedRoot),
                label,
            )
        }

        assertFalse(
            isCredentialEncryptedNoBackupDirectory(
                File(base, "credential-encrypted/../device-protected/metadata"),
                credentialRoot,
                deviceProtectedRoot,
            ),
            "candidate canonicalization must not hide a device-protected route",
        )
        assertTrue(
            isCredentialEncryptedNoBackupDirectory(
                File(credentialRoot, "metadata"),
                File(base, "credential-encrypted/nested/.."),
                deviceProtectedRoot,
            ),
            "app root canonicalization must preserve a credential-encrypted route",
        )
        assertFalse(
            isCredentialEncryptedNoBackupDirectory(
                File(deviceProtectedRoot, "metadata"),
                deviceProtectedRoot,
                File(base, "device-protected/nested/.."),
            ),
            "device-protected root canonicalization must reject its route",
        )
    }

    @Test
    fun `blank named roots are rejected before canonicalization even when legacy boundaries accept them`() {
        val workingDirectory = File("").canonicalFile
        val sibling = File(workingDirectory.parentFile, "${workingDirectory.name}-credential")
        val roots = linkedMapOf(
            "candidate" to Triple(File(""), workingDirectory.parentFile, File(workingDirectory, "dp")),
            "app root" to Triple(File(workingDirectory, "metadata"), File(""), File(workingDirectory, "dp")),
            "device-protected root" to Triple(File(sibling, "metadata"), sibling, File("")),
        )
        roots.forEach { (label, inputs) ->
            val (candidate, app, deviceProtected) = inputs
            val candidatePath = candidate.canonicalFile.toPath()
            val appPath = app.canonicalFile.toPath()
            val deviceProtectedPath = deviceProtected.canonicalFile.toPath()
            assertTrue(candidatePath != appPath && candidatePath.startsWith(appPath), "$label legacy containment")
            assertFalse(candidatePath.startsWith(deviceProtectedPath), "$label legacy DP exclusion")
        }
        assertEquals(
            roots.mapValues { false },
            roots.mapValues { (_, inputs) ->
                isCredentialEncryptedNoBackupDirectory(inputs.first, inputs.second, inputs.third)
            },
            "every blank input must independently invalidate an otherwise accepted boundary",
        )
        roots.forEach { (label, inputs) ->
            val thrown = assertFailsWith<IllegalStateException>(label) {
                AppStoragePolicy(FakeStorageEnvironment(
                    noBackup = inputs.first, appDataDir = inputs.second, deviceProtectedDataDir = inputs.third,
                ))
            }
            assertEquals("credential-encrypted storage unavailable", thrown.message, label)
            assertEquals(null, thrown.cause, label)
            val beforeCanonical = listOf(inputs.first, inputs.second, inputs.third).map { file ->
                if (file.path.isEmpty()) object : File(" ") {
                    override fun getCanonicalFile(): File = throw AssertionError("$label canonicalized blank path")
                } else file
            }
            assertFalse(isCredentialEncryptedNoBackupDirectory(
                beforeCanonical[0], beforeCanonical[1], beforeCanonical[2],
            ), "$label whitespace before canonicalization")
        }
    }

    @Test
    fun `false device-protected marker with a device-protected actual root is rejected without exposing its path`() {
        val base = createTempDirectory("storage-policy").toFile()
        val sensitiveDeviceProtectedRoot = File(base, "42 Example St/Jane Tenant/secret")
        val environment = FakeStorageEnvironment(
            noBackup = File(sensitiveDeviceProtectedRoot, "metadata"),
            appDataDir = sensitiveDeviceProtectedRoot,
            deviceProtectedDataDir = sensitiveDeviceProtectedRoot,
        )

        val thrown = assertFailsWith<IllegalStateException> { AppStoragePolicy(environment) }

        assertNoSensitiveText(thrown, thrown.message ?: "", thrown.cause?.toString() ?: "")
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
    fun `fatal canonical errors propagate unchanged for candidate app and device-protected roots`() {
        val base = createTempDirectory("storage-policy").toFile()
        val candidate = File(base, "candidate")
        val deviceProtected = File(base.parentFile, "device-protected")
        val candidateFatal = OutOfMemoryError("candidate")
        val appRootFatal = ThreadDeath()
        val deviceProtectedFatal = OutOfMemoryError("device-protected")
        val failingCandidate = object : File(candidate.path) {
            override fun getCanonicalFile(): File = throw candidateFatal
        }
        val failingAppRoot = object : File(base.path) {
            override fun getCanonicalFile(): File = throw appRootFatal
        }
        val failingDeviceProtectedRoot = object : File(deviceProtected.path) {
            override fun getCanonicalFile(): File = throw deviceProtectedFatal
        }

        assertSame(
            candidateFatal,
            assertFailsWith<OutOfMemoryError> {
                isCredentialEncryptedNoBackupDirectory(failingCandidate, base, deviceProtected)
            },
        )
        assertSame(
            appRootFatal,
            assertFailsWith<ThreadDeath> {
                isCredentialEncryptedNoBackupDirectory(candidate, failingAppRoot, deviceProtected)
            },
        )
        assertSame(
            deviceProtectedFatal,
            assertFailsWith<OutOfMemoryError> {
                isCredentialEncryptedNoBackupDirectory(candidate, base, failingDeviceProtectedRoot)
            },
        )
    }

    @Test
    fun `ordinary canonical lookup errors close false`() {
        val base = createTempDirectory("storage-policy").toFile()
        val candidate = File(base, "candidate")
        val deviceProtected = File(base.parentFile, "device-protected")
        val ioFailure = object : File(candidate.path) {
            override fun getCanonicalFile(): File = throw IOException("42 Example St Jane Tenant Authorization Bearer secret")
        }
        val securityFailure = object : File(deviceProtected.path) {
            override fun getCanonicalFile(): File = throw SecurityException("42 Example St Jane Tenant Authorization Bearer secret")
        }

        assertFalse(isCredentialEncryptedNoBackupDirectory(ioFailure, base, deviceProtected))
        assertFalse(isCredentialEncryptedNoBackupDirectory(candidate, base, securityFailure))

        val thrown = assertFailsWith<IllegalStateException> {
            AppStoragePolicy(
                FakeStorageEnvironment(
                    noBackup = ioFailure,
                    appDataDir = base,
                    deviceProtectedDataDir = deviceProtected,
                ),
            )
        }

        assertEquals("credential-encrypted storage unavailable", thrown.message)
        assertEquals(null, thrown.cause)
        assertNoSensitiveText(thrown, thrown.message ?: "")
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
    fun `media state probe failure closes unavailable without preserving its path-bearing exception`() {
        val root = createTempDirectory("storage-policy").toFile()
        val sensitiveExternal = File(root, "Android/data/nz.myinspection.app/files/42 Example St/Jane Tenant/secret")
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
 * R4 blank-path repair receipt: 37/37 mutations compiled (exit 0) and failed the named
 * java.lang.AssertionError (test exit 1); production and test snapshot bytes restored after each.
 * Production SHA-256: 751D453980527A55075EF3592950401C0CE98087EEFDC92AAF24BAE2496C0691
 * Test snapshot SHA-256: E33FECC6030C485125DF8F26C72C5AF65BCD02C5E9D2FFD080EDCCCC2CA376D1
 * This receipt is the only change from the tested snapshot. Before R4, full DoD passed:
 * 200 app tests (14 policy, 7 SafeLog), zero failures/errors/skips, and assembleDebug.
 * Evidence: _local/storage-policy/blank-path-repair/{mutation-plan.json,mutations/,final-mutation-audit.json}.
 * Audit independently reconstructed all mutant bytes and checked named XML failures and both pins.
 * M18 XML specifically proves the non-null absent path reported unwritable case in T9.
 * Android mapping, actual directory probes, and device execution belong to T1-APP-STORAGE-ANDROID.
 * T1 = each protected category uses its own credential encrypted no-backup subdirectory
 * T4 = device protected environment converts to credential encrypted before a protected route is exposed
 * T5 = environment that remains device protected is rejected without exposing its path
 * T6 = credential conversion probe and no-backup lookup failures are rejected without preserving sensitive exceptions
 * T7 = credential no-backup candidate must be a normalized child of a non-device-protected app root
 * T8 = false device-protected marker with a device-protected actual root is rejected without exposing its path
 * T9 = media returns unavailable for missing unmounted and read-only app-specific external volumes
 * T10 = media reports insufficient space below request and permits equal or greater usable bytes without a shared fallback
 * T11 = media state probe failure closes unavailable without preserving its path-bearing exception
 * T12 = fatal no-backup lookup error propagates with its identity
 * T13 = fatal media probe error propagates with its identity
 * T14 = fatal canonical errors propagate unchanged for candidate app and device-protected roots
 * T15 = ordinary canonical lookup errors close false
 * M01-database, M02-settings, M03-receipts, M04-secret_envelope, M05-restore_journal, M06-staging_metadata -> T1
 * M07-platform-mounted through M11-directory-writable transfer with the Android adapter.
 * M12-ce-conversion, M14-ce-root -> T4; M13-dp-rejection -> T5
 * M15-ce-cause -> T6; M16-missing-directory, M17-mount-guard, M18-writable-guard -> T9
 * M19-equality, M20-low-space, M21-above-request -> T10; M22-probe-state, M23-probe-escape -> T11
 * M24-ce-root-text, M26-location-text -> T1; M25-external-root-text, M27-available-text -> T10
 * M28-candidate-canonical, M29-app-canonical, M30-dp-canonical -> T7
 * M31-strict-child, M32-app-containment, M33-dp-exclusion -> T7; M34-root-validation-call -> T8
 * M35-media-fatal -> T13; M36-root-fatal -> T12
 * M37-canonical-fatal -> T14; M38-canonical-failopen -> T15
 * T16 = blank named roots are rejected before canonicalization even when legacy boundaries accept them
 * M39-media-blank -> T9; M40-candidate-blank, M41-app-blank, M42-dp-blank -> T16
 * Each root mutant admitted only its own empty-root vector; blank-vector-audit.json checks the exact XML maps.
 * Regression RED had two named AssertionErrors: all three blank roots accepted, and blank media Available.
 */

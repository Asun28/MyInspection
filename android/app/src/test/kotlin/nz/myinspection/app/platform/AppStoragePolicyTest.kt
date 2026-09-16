package nz.myinspection.app.platform

import android.os.Environment
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppStoragePolicyTest {
    @Test
    fun `only Android mounted state maps to writable media state`() {
        assertEquals(ExternalMediaVolumeState.MOUNTED, ExternalMediaVolumeState.fromPlatformState(Environment.MEDIA_MOUNTED))
        assertEquals(ExternalMediaVolumeState.MOUNTED_READ_ONLY, ExternalMediaVolumeState.fromPlatformState(Environment.MEDIA_MOUNTED_READ_ONLY))
        assertEquals(ExternalMediaVolumeState.UNMOUNTED, ExternalMediaVolumeState.fromPlatformState(Environment.MEDIA_UNMOUNTED))
    }

    @Test
    fun `adapter accepts writable directories but not writable plain files`() {
        val directory = createTempDirectory("storage-policy").toFile()
        val plainFile = File.createTempFile("storage-policy", ".tmp", directory)
        val readOnlyDirectory = object : File(directory.path) {
            override fun canWrite(): Boolean = false
        }

        assertTrue(isWritableDirectory(directory))
        assertFalse(isWritableDirectory(plainFile))
        assertFalse(isWritableDirectory(readOnlyDirectory))
    }

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
    fun `media returns unavailable for missing unmounted and read-only app-specific external volumes`() {
        val root = createTempDirectory("storage-policy").toFile()
        val sensitiveExternal = File(root, "Android/data/nz.myinspection.app/files/42 Example St/Jane Tenant/secret")
        val unavailableEnvironments = listOf(
            "missing directory" to FakeStorageEnvironment(noBackup = root, externalMedia = null),
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
 * R4: 34 single-site mutations compiled (exit 0) and failed named java.lang.AssertionError
 * in fresh TestNG XML (test exit 1); no survivor/invalid mutant. Original bytes SHA-restored each run.
 * Commands: :app:compileDebugUnitTestKotlin; :app:testDebugUnitTest --tests nz.myinspection.app.platform.AppStoragePolicyTest.
 * T1 = each protected category uses its own credential encrypted no-backup subdirectory
 * T2 = only Android mounted state maps to writable media state
 * T3 = adapter accepts writable directories but not writable plain files
 * T4 = device protected environment converts to credential encrypted before a protected route is exposed
 * T5 = environment that remains device protected is rejected without exposing its path
 * T6 = credential conversion probe and no-backup lookup failures are rejected without preserving sensitive exceptions
 * T7 = credential no-backup candidate must be a normalized child of a non-device-protected app root
 * T8 = false device-protected marker with a device-protected actual root is rejected without exposing its path
 * T9 = media returns unavailable for missing unmounted and read-only app-specific external volumes
 * T10 = media reports insufficient space below request and permits equal or greater usable bytes without a shared fallback
 * T11 = media state probe failure closes unavailable without preserving its path-bearing exception
 * M01-database, M02-settings, M03-receipts, M04-secret_envelope, M05-restore_journal, M06-staging_metadata -> T1
 * M07-platform-mounted, M08-platform-readonly, M09-platform-unmounted -> T2
 * M10-directory-type, M11-directory-writable -> T3; M12-ce-conversion, M14-ce-root -> T4; M13-dp-rejection -> T5
 * M15-ce-cause -> T6; M16-missing-directory, M17-mount-guard, M18-writable-guard -> T9
 * M19-equality, M20-low-space, M21-above-request -> T10; M22-probe-state, M23-probe-escape -> T11
 * M24-ce-root-text, M26-location-text -> T1; M25-external-root-text, M27-available-text -> T10
 * M28-candidate-canonical, M29-app-canonical, M30-dp-canonical -> T7
 * M31-strict-child, M32-app-containment, M33-dp-exclusion -> T7; M34-root-validation-call -> T8
 * Production SHA256 E269BE8E4DC91F70446A9C46DAC0AACAF731C70B960DE219A9BFD04B411BBAA3
 * Test snapshot before this receipt SHA256 5CD0AE41EE62EC36F30CB7A554BD46A2E718CEDAA2E99556786AA5875CBF9E20
 * Actual adapter: API35 emulator + API33 phone accept six CE routes and DP-to-package conversion.
 * A ContextWrapper overriding only the marker to false over genuine DP roots is safely rejected on both.
 * This is a synthetic-marker probe; ordinary APK defaultToDeviceProtectedStorage=true was not adopted.
 * That system-app-only manifest configuration was not runtime-reproduced; no root/system-app changes were made.
 */

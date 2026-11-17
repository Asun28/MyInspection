package nz.myinspection.app.platform

import java.io.File

/** Pure environment seam used to discover app-private storage roots and volume state. */
interface AppStorageEnvironment {
    val isDeviceProtectedStorage: Boolean
    val appDataDir: File
    val deviceProtectedDataDir: File
    val noBackupFilesDir: File
    val appSpecificExternalMediaDir: File?

    fun credentialEncryptedContext(): AppStorageEnvironment

    fun appSpecificExternalMediaState(directory: File): ExternalMediaVolumeState

    fun isAppSpecificExternalMediaWritable(directory: File): Boolean

    fun usableBytes(directory: File): Long
}

/** Closed external-media state supplied by the storage environment. */
enum class ExternalMediaVolumeState {
    MOUNTED,
    MOUNTED_READ_ONLY,
    UNMOUNTED,
}

enum class SecureStorageNamespace(val subdirectory: String) {
    DATABASE("database"),
    SETTINGS("settings"),
    RECEIPTS("receipts"),
    SECRET_ENVELOPE("secret-envelope"),
    RESTORE_JOURNAL("restore-journal"),
    STAGING_METADATA("staging"),
}

sealed interface StorageRoot {
    val directory: File

    class CredentialEncryptedNoBackup internal constructor(
        override val directory: File,
    ) : StorageRoot {
        override fun toString(): String = "CredentialEncryptedNoBackup"
    }

    class AppSpecificExternalMedia internal constructor(
        override val directory: File,
    ) : StorageRoot {
        override fun toString(): String = "AppSpecificExternalMedia"
    }
}

class StorageLocation internal constructor(
    val root: StorageRoot.CredentialEncryptedNoBackup,
    val directory: File,
) {
    override fun toString(): String = "StorageLocation.CredentialEncryptedNoBackup"
}

sealed interface MediaStorageLocation {
    class Available internal constructor(
        val root: StorageRoot.AppSpecificExternalMedia,
    ) : MediaStorageLocation {
        override fun toString(): String = "MediaStorageLocation.Available"
    }

    data object Unavailable : MediaStorageLocation

    data object InsufficientSpace : MediaStorageLocation
}

/**
 * Policy that routes protected data and consumes app-specific external media facts from its environment.
 * Protected routes sit under the no-backup root that [StoragePathBoundary] resolved and saved at construction; each
 * [location] call has the boundary check its category directory and returns the directory it checked. The check
 * holds when it is made: it grants no authority over later filesystem I/O.
 */
class AppStoragePolicy(private val environment: AppStorageEnvironment) {
    private val protectedBoundary = credentialEncryptedNoBackupBoundary(environment)
    private val protectedRoot = StorageRoot.CredentialEncryptedNoBackup(protectedBoundary.directory)

    fun location(namespace: SecureStorageNamespace): StorageLocation {
        val directory = protectedBoundary.resolveChild(File(protectedRoot.directory, namespace.subdirectory))
            ?: throw IllegalStateException(CREDENTIAL_STORAGE_UNAVAILABLE)
        return StorageLocation(protectedRoot, directory)
    }

    fun mediaLocation(requestedBytes: Long): MediaStorageLocation {
        return try {
            val directory = environment.appSpecificExternalMediaDir ?: return MediaStorageLocation.Unavailable
            if (directory.path.isBlank()) return MediaStorageLocation.Unavailable
            if (
                environment.appSpecificExternalMediaState(directory) != ExternalMediaVolumeState.MOUNTED ||
                !environment.isAppSpecificExternalMediaWritable(directory)
            ) {
                return MediaStorageLocation.Unavailable
            }
            if (environment.usableBytes(directory) < requestedBytes) {
                MediaStorageLocation.InsufficientSpace
            } else {
                MediaStorageLocation.Available(StorageRoot.AppSpecificExternalMedia(directory))
            }
        } catch (_: Exception) {
            MediaStorageLocation.Unavailable
        }
    }

    private fun credentialEncryptedNoBackupBoundary(environment: AppStorageEnvironment): StoragePathBoundary {
        val boundary = try {
            val credentialEnvironment =
                if (environment.isDeviceProtectedStorage) environment.credentialEncryptedContext() else environment
            check(!credentialEnvironment.isDeviceProtectedStorage)
            StoragePathBoundary.create(
                credentialEnvironment.noBackupFilesDir,
                credentialEnvironment.appDataDir,
                credentialEnvironment.deviceProtectedDataDir,
            )
        } catch (_: Exception) {
            null
        }
        return boundary ?: throw IllegalStateException(CREDENTIAL_STORAGE_UNAVAILABLE)
    }
}

private const val CREDENTIAL_STORAGE_UNAVAILABLE = "credential-encrypted storage unavailable"

package nz.myinspection.app.platform

import java.io.File

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

enum class ExternalMediaVolumeState { MOUNTED, MOUNTED_READ_ONLY, UNMOUNTED }

enum class SecureStorageNamespace(internal val subdirectory: String) {
    DATABASE("database"),
    SETTINGS("settings"),
    RECEIPTS("receipts"),
    SECRET_ENVELOPE("secret-envelope"),
    RESTORE_JOURNAL("restore-journal"),
    STAGING_METADATA("staging"),
}

sealed class StorageRoot(val directory: File) {
    class CredentialEncryptedNoBackup internal constructor(directory: File) : StorageRoot(directory) {
        override fun toString(): String = "CredentialEncryptedNoBackup"
    }

    class AppSpecificExternalMedia internal constructor(directory: File) : StorageRoot(directory) {
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
    data object Unavailable : MediaStorageLocation
    data object InsufficientSpace : MediaStorageLocation

    class Available internal constructor(val root: StorageRoot.AppSpecificExternalMedia) : MediaStorageLocation {
        override fun toString(): String = "MediaStorageLocation.Available"
    }
}

class AppStoragePolicy(private val environment: AppStorageEnvironment) {
    private val boundary = credentialBoundary()
    private val protectedRoot = StorageRoot.CredentialEncryptedNoBackup(boundary.directory)

    fun location(namespace: SecureStorageNamespace): StorageLocation {
        val directory = boundary.resolveChild(File(boundary.directory, namespace.subdirectory))
            ?: throw IllegalStateException(CREDENTIAL_STORAGE_UNAVAILABLE)
        return StorageLocation(protectedRoot, directory)
    }

    fun mediaLocation(requestedBytes: Long): MediaStorageLocation {
        return try {
            val directory = environment.appSpecificExternalMediaDir ?: return MediaStorageLocation.Unavailable
            if (directory.path.isBlank()) return MediaStorageLocation.Unavailable
            if (environment.appSpecificExternalMediaState(directory) != ExternalMediaVolumeState.MOUNTED ||
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

    private fun credentialBoundary(): StoragePathBoundary {
        return try {
            val credentialEnvironment =
                if (environment.isDeviceProtectedStorage) environment.credentialEncryptedContext() else environment
            check(!credentialEnvironment.isDeviceProtectedStorage)
            checkNotNull(StoragePathBoundary.create(
                candidate = credentialEnvironment.noBackupFilesDir,
                appDataDir = credentialEnvironment.appDataDir,
                deviceProtectedDataDir = credentialEnvironment.deviceProtectedDataDir,
            ))
        } catch (_: Exception) {
            throw IllegalStateException(CREDENTIAL_STORAGE_UNAVAILABLE)
        }
    }
}

private const val CREDENTIAL_STORAGE_UNAVAILABLE = "credential-encrypted storage unavailable"

package nz.myinspection.app.platform

import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Environment
import java.io.File

/** The only Android-facing seam used to discover app-private storage roots and volume state. */
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

/** Closed state translated from Android's external-storage state strings. */
enum class ExternalMediaVolumeState {
    MOUNTED,
    MOUNTED_READ_ONLY,
    UNMOUNTED,
    ;

    companion object {
        fun fromPlatformState(state: String): ExternalMediaVolumeState = when (state) {
            Environment.MEDIA_MOUNTED -> MOUNTED
            Environment.MEDIA_MOUNTED_READ_ONLY -> MOUNTED_READ_ONLY
            else -> UNMOUNTED
        }
    }
}

/** Android 35 public-API adapter; it never chooses a shared-media location. */
class AndroidAppStorageEnvironment private constructor(
    private val context: Context,
) : AppStorageEnvironment {
    override val isDeviceProtectedStorage: Boolean
        get() = context.isDeviceProtectedStorage

    override val appDataDir: File
        get() = context.dataDir

    override val deviceProtectedDataDir: File
        get() = context.createDeviceProtectedStorageContext().dataDir

    override val noBackupFilesDir: File
        get() = context.noBackupFilesDir

    override val appSpecificExternalMediaDir: File?
        get() = context.getExternalFilesDir(null)

    override fun appSpecificExternalMediaState(directory: File): ExternalMediaVolumeState =
        ExternalMediaVolumeState.fromPlatformState(Environment.getExternalStorageState(directory))

    override fun isAppSpecificExternalMediaWritable(directory: File): Boolean = isWritableDirectory(directory)

    override fun credentialEncryptedContext(): AppStorageEnvironment = try {
        AndroidAppStorageEnvironment(context.createPackageContext(context.packageName, 0))
    } catch (_: NameNotFoundException) {
        throw IllegalStateException(CREDENTIAL_STORAGE_UNAVAILABLE)
    }

    override fun usableBytes(directory: File): Long = directory.usableSpace

    companion object {
        fun from(context: Context): AndroidAppStorageEnvironment = AndroidAppStorageEnvironment(context)
    }
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

/** Routes protected application data to credential-encrypted no-backup storage and media to app-specific external storage. */
class AppStoragePolicy(private val environment: AppStorageEnvironment) {
    private val protectedRoot = credentialEncryptedNoBackupRoot(environment)

    fun location(namespace: SecureStorageNamespace): StorageLocation =
        StorageLocation(protectedRoot, File(protectedRoot.directory, namespace.subdirectory))

    fun mediaLocation(requestedBytes: Long): MediaStorageLocation {
        return try {
            val directory = environment.appSpecificExternalMediaDir ?: return MediaStorageLocation.Unavailable
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

    private fun credentialEncryptedNoBackupRoot(environment: AppStorageEnvironment): StorageRoot.CredentialEncryptedNoBackup {
        val directory = try {
            val credentialEnvironment =
                if (environment.isDeviceProtectedStorage) environment.credentialEncryptedContext() else environment
            check(!credentialEnvironment.isDeviceProtectedStorage)
            val candidate = credentialEnvironment.noBackupFilesDir
            check(
                isCredentialEncryptedNoBackupDirectory(
                    candidate,
                    credentialEnvironment.appDataDir,
                    credentialEnvironment.deviceProtectedDataDir,
                ),
            )
            candidate
        } catch (_: Exception) {
            throw IllegalStateException(CREDENTIAL_STORAGE_UNAVAILABLE)
        }
        return StorageRoot.CredentialEncryptedNoBackup(directory)
    }
}

internal fun isWritableDirectory(directory: File): Boolean = directory.isDirectory && directory.canWrite()

internal fun isCredentialEncryptedNoBackupDirectory(
    candidate: File,
    appDataDir: File,
    deviceProtectedDataDir: File,
): Boolean = try {
    val candidatePath = candidate.canonicalFile.toPath()
    val appDataPath = appDataDir.canonicalFile.toPath()
    val deviceProtectedPath = deviceProtectedDataDir.canonicalFile.toPath()
    candidatePath != appDataPath && candidatePath.startsWith(appDataPath) && !candidatePath.startsWith(deviceProtectedPath)
} catch (_: Exception) {
    false
}

private const val CREDENTIAL_STORAGE_UNAVAILABLE = "credential-encrypted storage unavailable"

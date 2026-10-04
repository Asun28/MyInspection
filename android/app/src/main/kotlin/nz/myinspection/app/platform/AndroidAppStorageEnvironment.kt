package nz.myinspection.app.platform

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Android implementation of [AppStorageEnvironment]. The marker and root members read the wrapped context's getters
 * (the device-protected root through its device-protected context), conversion creates a package context, and the
 * state, writability and space members use the directory they are given, not the context. Choosing the protected
 * root, converting a device-protected context first and refusing one that stays device protected are
 * [AppStoragePolicy]'s job. AppStorageProbeActivity (debug) checks this class on devices.
 */
class AndroidAppStorageEnvironment(private val context: Context) : AppStorageEnvironment {
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

    /**
     * A package context created without storage flags uses the app's default storage, which is credential encrypted
     * because this app does not request device-protected default storage; the policy still checks the returned marker.
     * Ordinary failures become one fixed message without a cause; an Error propagates.
     */
    override fun credentialEncryptedContext(): AppStorageEnvironment = try {
        AndroidAppStorageEnvironment(context.createPackageContext(context.packageName, 0))
    } catch (_: Exception) {
        throw IllegalStateException(CREDENTIAL_CONTEXT_UNAVAILABLE)
    }

    override fun appSpecificExternalMediaState(directory: File): ExternalMediaVolumeState =
        externalMediaVolumeState(Environment.getExternalStorageState(directory))

    override fun isAppSpecificExternalMediaWritable(directory: File): Boolean = isWritableDirectory(directory)

    override fun usableBytes(directory: File): Long = directory.usableSpace
}

/** Only the mounted raw state maps to MOUNTED; read-only keeps its own state and every other raw state is UNMOUNTED. */
internal fun externalMediaVolumeState(rawState: String): ExternalMediaVolumeState = when (rawState) {
    Environment.MEDIA_MOUNTED -> ExternalMediaVolumeState.MOUNTED
    Environment.MEDIA_MOUNTED_READ_ONLY -> ExternalMediaVolumeState.MOUNTED_READ_ONLY
    else -> ExternalMediaVolumeState.UNMOUNTED
}

internal fun isWritableDirectory(directory: File): Boolean = directory.isDirectory && directory.canWrite()

private const val CREDENTIAL_CONTEXT_UNAVAILABLE = "credential-encrypted context unavailable"

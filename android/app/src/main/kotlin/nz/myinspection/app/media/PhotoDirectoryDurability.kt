package nz.myinspection.app.media

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.IOException

/** Android directory-fsync adapter shared by ingest and orphan recovery. */
internal object PhotoDirectoryDurability {
    fun sync(directory: File) {
        val descriptor = try {
            Os.open(directory.path, OsConstants.O_RDONLY, 0)
        } catch (failure: ErrnoException) {
            throw IOException("could not open directory for fsync: ${directory.path}", failure)
        }
        var primary: Throwable? = null
        try {
            Os.fsync(descriptor)
        } catch (failure: Throwable) {
            primary = mapFailure(directory, failure)
            throw primary
        } finally {
            try {
                Os.close(descriptor)
            } catch (closeFailure: Throwable) {
                val mappedCloseFailure = mapFailure(directory, closeFailure)
                val activeFailure = primary
                if (activeFailure == null) throw mappedCloseFailure
                activeFailure.addSuppressed(mappedCloseFailure)
            }
        }
    }

    private fun mapFailure(directory: File, failure: Throwable): Throwable =
        if (failure is ErrnoException) {
            IOException("directory fsync failed: ${directory.path}", failure)
        } else {
            failure
        }
}

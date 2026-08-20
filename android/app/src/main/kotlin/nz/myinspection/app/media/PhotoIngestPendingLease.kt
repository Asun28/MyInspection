package nz.myinspection.app.media

import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.File
import nz.myinspection.core.media.PendingPhotoLease
import nz.myinspection.core.media.PendingPhotoLeaseDisposition
import nz.myinspection.core.media.PublicationLease

/** App adapter from the ingest outcome domain to the durable sidecar lease outcome domain. */
internal class PhotoIngestPendingLease private constructor(
    private val photoId: String,
    private val lease: PendingPhotoLease,
) : PublicationLease<PhotoIngestOutcome> {
    private var disposition = PendingPhotoLeaseDisposition.RETAIN

    override fun finish(result: PhotoIngestOutcome) {
        disposition = when (result) {
            is PhotoIngestOutcome.Recorded -> PendingPhotoLeaseDisposition.RECORDED
            is PhotoIngestOutcome.RejectedByGuard -> if (!result.orphanedFileRemains) {
                PendingPhotoLeaseDisposition.REJECTED_WITHOUT_ORPHAN
            } else {
                PendingPhotoLeaseDisposition.RETAIN
            }
            is PhotoIngestOutcome.RejectedTooLarge,
            is PhotoIngestOutcome.RejectedUndecodable,
            -> PendingPhotoLeaseDisposition.RETAIN
        }
    }

    override fun close() {
        if (!lease.closeAfter(disposition)) {
            Log.w(TAG, "op=deletePendingMarker photoId=$photoId path=${lease.marker.path} result=failed")
        }
    }

    override fun onCompletedCleanupFailure(failure: Throwable) {
        Log.w(TAG, "op=releasePendingLease photoId=$photoId path=${lease.marker.path} result=failed", failure)
    }

    companion object {
        private const val TAG = "PhotoIngestPendingLease"

        fun acquire(target: File, photoId: String): PhotoIngestPendingLease =
            PhotoIngestPendingLease(photoId, PendingPhotoLease.acquire(target, ::syncParentDirectory))

        private fun syncParentDirectory(parent: File) {
            val descriptor = Os.open(parent.path, OsConstants.O_RDONLY, 0)
            var primary: Throwable? = null
            try {
                Os.fsync(descriptor)
            } catch (failure: Throwable) {
                primary = failure
                throw failure
            } finally {
                try {
                    Os.close(descriptor)
                } catch (closeFailure: Throwable) {
                    val activeFailure = primary
                    if (activeFailure == null) throw closeFailure
                    activeFailure.addSuppressed(closeFailure)
                }
            }
        }
    }
}

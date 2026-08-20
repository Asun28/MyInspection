package nz.myinspection.app.media

import android.util.Log
import java.io.File
import nz.myinspection.core.media.PendingPhotoLease
import nz.myinspection.core.media.PendingPhotoLeaseDisposition
import nz.myinspection.core.media.PublicationLease

/** App adapter from the ingest outcome domain to the durable sidecar lease outcome domain. */
internal class PhotoIngestPendingLease private constructor(
    private val photoId: String,
    private val targetParent: File,
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
        if (!lease.closeAfterAssetDeletion(disposition) { PhotoDirectoryDurability.sync(targetParent) }) {
            Log.w(TAG, "op=deletePendingMarker photoId=$photoId path=${lease.marker.path} result=failed")
        }
    }

    override fun onCompletedCleanupFailure(failure: Throwable) {
        Log.w(TAG, "op=releasePendingLease photoId=$photoId path=${lease.marker.path} result=failed", failure)
    }

    companion object {
        private const val TAG = "PhotoIngestPendingLease"

        fun acquire(target: File, photoId: String, mediaRoot: File): PhotoIngestPendingLease =
            PhotoIngestPendingLease(
                photoId,
                checkNotNull(target.parentFile),
                PendingPhotoLease.acquire(
                    target,
                    checkNotNull(mediaRoot.parentFile),
                    PhotoDirectoryDurability::sync,
                ),
            )
    }
}

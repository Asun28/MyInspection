package nz.myinspection.app.media

import java.io.File
import nz.myinspection.app.platform.SafeLog
import nz.myinspection.app.platform.SafeLogEvent
import nz.myinspection.app.platform.SafeLogOpaqueId
import nz.myinspection.app.platform.SafeLogOperation
import nz.myinspection.app.platform.SafeLogReason
import nz.myinspection.core.media.PendingPhotoLease
import nz.myinspection.core.media.PendingPhotoLeaseDisposition
import nz.myinspection.core.media.PublicationLease

/** App adapter from the ingest outcome domain to the durable sidecar lease outcome domain. */
internal class PhotoIngestPendingLease(
    private val photoId: String,
    private val closeAction: (PendingPhotoLeaseDisposition) -> Boolean,
    private val log: SafeLog,
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
        if (!closeAction(disposition)) {
            log.record(
                SafeLogEvent(
                    SafeLogOperation.PENDING_MARKER_DELETE,
                    SafeLogReason.DELETE_FAILED,
                    SafeLogOpaqueId.parse(photoId),
                ),
            )
        }
    }

    override fun onCompletedCleanupFailure(failure: Throwable) {
        log.record(
            SafeLogEvent(
                SafeLogOperation.PENDING_LEASE_RELEASE,
                SafeLogReason.CLEANUP_FAILED,
                SafeLogOpaqueId.parse(photoId),
            ),
        )
    }

    companion object {
        fun acquire(target: File, photoId: String, mediaRoot: File): PhotoIngestPendingLease {
            val targetParent = checkNotNull(target.parentFile)
            val lease = PendingPhotoLease.acquire(
                target,
                checkNotNull(mediaRoot.parentFile),
                PhotoDirectoryDurability::sync,
            )
            return PhotoIngestPendingLease(
                photoId,
                { disposition -> lease.closeAfterAssetDeletion(disposition) { PhotoDirectoryDurability.sync(targetParent) } },
                SafeLog.android(),
            )
        }
    }
}

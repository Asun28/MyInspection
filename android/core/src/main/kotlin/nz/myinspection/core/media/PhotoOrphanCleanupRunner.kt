package nz.myinspection.core.media

import java.io.File
import java.io.IOException
import nz.myinspection.core.db.MyInspectionDatabase

/** The worker result domain: malformed persisted data fails closed, transient filesystem contention retries. */
enum class PhotoOrphanCleanupDecision {
    SUCCESS,
    RETRY,
    FAILURE,
}

enum class PhotoOrphanCleanupBucket(val logValue: String) {
    PENDING("pending"),
    SOFT_DELETE("soft_delete"),
}

enum class PhotoOrphanCleanupIssueResult(val logValue: String) {
    REJECTED("rejected"),
    FAILED("failed"),
}

/** One path-level item the Android worker must make observable without losing its cleanup bucket or cause. */
data class PhotoOrphanCleanupIssue(
    val result: PhotoOrphanCleanupIssueResult,
    val bucket: PhotoOrphanCleanupBucket,
    val path: String,
    val cause: Throwable?,
)

/** Complete evidence from one pass; neither cleanup source is collapsed into the WorkManager decision. */
data class PhotoOrphanCleanupReport private constructor(
    val pending: CleanupResult,
    val softDelete: CleanupResult,
    val decision: PhotoOrphanCleanupDecision,
) {
    fun issues(): List<PhotoOrphanCleanupIssue> = buildList {
        addIssues(PhotoOrphanCleanupBucket.PENDING, pending)
        addIssues(PhotoOrphanCleanupBucket.SOFT_DELETE, softDelete)
    }

    private fun MutableList<PhotoOrphanCleanupIssue>.addIssues(
        bucket: PhotoOrphanCleanupBucket,
        result: CleanupResult,
    ) {
        result.rejected.forEach { path ->
            add(PhotoOrphanCleanupIssue(PhotoOrphanCleanupIssueResult.REJECTED, bucket, path, cause = null))
        }
        result.failed.forEach { failure ->
            add(PhotoOrphanCleanupIssue(PhotoOrphanCleanupIssueResult.FAILED, bucket, failure.relPath, failure.cause))
        }
    }

    companion object {
        internal fun from(pending: CleanupResult, softDelete: CleanupResult): PhotoOrphanCleanupReport {
            val decision = when {
                pending.rejected.isNotEmpty() || softDelete.rejected.isNotEmpty() ->
                    PhotoOrphanCleanupDecision.FAILURE
                (pending.failed + softDelete.failed).any { !isRetryableFailure(it.cause) } ->
                    PhotoOrphanCleanupDecision.FAILURE
                pending.failed.isNotEmpty() || softDelete.failed.isNotEmpty() ->
                    PhotoOrphanCleanupDecision.RETRY
                else -> PhotoOrphanCleanupDecision.SUCCESS
            }
            return PhotoOrphanCleanupReport(pending, softDelete, decision)
        }

        private fun isRetryableFailure(failure: Throwable?): Boolean = when (failure) {
            null -> true
            is IOException, is SecurityException -> failure.suppressed.all(::isRetryableFailure)
            else -> false
        }
    }
}

/**
 * One injected-database cleanup pass: recover sidecars first, then execute the existing soft-delete cleaner.
 *
 * The Android worker owns driver construction and closure. This core use case owns every DB observation and remains
 * executable with the JVM SQLite driver.
 */
class PhotoOrphanCleanupRunner(
    private val database: MyInspectionDatabase,
    private val mediaRoot: File,
    private val deleter: OrphanFileDeleter,
    private val syncAssetParentDirectory: (File) -> Unit,
) {
    fun run(): PhotoOrphanCleanupReport {
        val pending = PendingPhotoAssetCleanup(
            mediaRoot = mediaRoot,
            findPhoto = { photoId ->
                database.photoQueries.selectById(photoId).executeAsOneOrNull()?.let { photo ->
                    PendingPhotoReference(relPath = photo.rel_path, active = photo.deleted_at == null)
                }
            },
            deleter = deleter,
            syncAssetParentDirectory = syncAssetParentDirectory,
        ).run()
        val softDelete = OrphanedAssetCleanup(database, deleter).run()
        return PhotoOrphanCleanupReport.from(pending, softDelete)
    }
}

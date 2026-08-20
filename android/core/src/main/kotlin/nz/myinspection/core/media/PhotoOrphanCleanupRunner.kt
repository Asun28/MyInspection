package nz.myinspection.core.media

import java.io.File
import nz.myinspection.core.db.MyInspectionDatabase

/** The worker result domain: malformed persisted data fails closed, transient filesystem contention retries. */
enum class PhotoOrphanCleanupDecision {
    SUCCESS,
    RETRY,
    FAILURE,
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
) {
    fun run(): PhotoOrphanCleanupDecision {
        val pending = PendingPhotoAssetCleanup(
            mediaRoot = mediaRoot,
            findPhoto = { photoId ->
                database.photoQueries.selectById(photoId).executeAsOneOrNull()?.let { photo ->
                    PendingPhotoReference(relPath = photo.rel_path, active = photo.deleted_at == null)
                }
            },
            deleter = deleter,
        ).run()
        val orphaned = OrphanedAssetCleanup(database, deleter).run()
        return when {
            pending.rejected.isNotEmpty() || orphaned.rejected.isNotEmpty() -> PhotoOrphanCleanupDecision.FAILURE
            pending.failed.isNotEmpty() || orphaned.failed.isNotEmpty() -> PhotoOrphanCleanupDecision.RETRY
            else -> PhotoOrphanCleanupDecision.SUCCESS
        }
    }
}

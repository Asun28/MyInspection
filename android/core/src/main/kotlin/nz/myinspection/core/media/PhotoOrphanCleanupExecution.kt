package nz.myinspection.core.media

/** The worker adapter can log [failure], while [decision] remains the only WorkManager-visible outcome. */
data class PhotoOrphanCleanupExecutionResult(
    val decision: PhotoOrphanCleanupDecision,
    val failure: Throwable?,
)

/** Portable boundary for Android's SQLiteException subclass mapping. */
enum class PhotoOrphanSqliteFailureKind {
    DATABASE_LOCKED,
    TABLE_LOCKED,
    DISK_IO,
    CANT_OPEN,
    OTHER,
}

fun isRetryablePhotoOrphanSqliteFailure(kind: PhotoOrphanSqliteFailureKind): Boolean = when (kind) {
    PhotoOrphanSqliteFailureKind.DATABASE_LOCKED,
    PhotoOrphanSqliteFailureKind.TABLE_LOCKED,
    PhotoOrphanSqliteFailureKind.DISK_IO,
    PhotoOrphanSqliteFailureKind.CANT_OPEN,
    -> true
    PhotoOrphanSqliteFailureKind.OTHER -> false
}

/**
 * Resource lifecycle for the Android worker, kept generic so JVM tests execute the exact primary/suppression rules.
 *
 * The app supplies an AndroidSqliteDriver; this core helper deliberately knows only AutoCloseable and the cleanup
 * decision domain. Expected close failures convert an otherwise-successful run to retry; unknown close failures fail
 * closed, while an active primary always retains ownership of suppressed cleanup evidence.
 */
object PhotoOrphanCleanupExecution {
    fun <Resource : AutoCloseable> run(
        open: () -> Resource,
        cleanup: (Resource) -> PhotoOrphanCleanupDecision,
        retryable: (Throwable) -> Boolean,
    ): PhotoOrphanCleanupExecutionResult {
        var resource: Resource? = null
        var primary: Throwable? = null
        var decision = PhotoOrphanCleanupDecision.FAILURE
        try {
            resource = open()
            decision = cleanup(resource)
        } catch (failure: Throwable) {
            primary = failure
            decision = if (retryable(failure)) {
                PhotoOrphanCleanupDecision.RETRY
            } else {
                PhotoOrphanCleanupDecision.FAILURE
            }
        } finally {
            try {
                resource?.close()
            } catch (closeFailure: Throwable) {
                val failure = primary
                if (failure != null) {
                    failure.addSuppressed(closeFailure)
                    if (!retryable(closeFailure)) {
                        decision = PhotoOrphanCleanupDecision.FAILURE
                    }
                } else {
                    primary = closeFailure
                    if (!retryable(closeFailure)) {
                        decision = PhotoOrphanCleanupDecision.FAILURE
                    } else if (decision == PhotoOrphanCleanupDecision.SUCCESS) {
                        decision = PhotoOrphanCleanupDecision.RETRY
                    }
                }
            }
        }
        return PhotoOrphanCleanupExecutionResult(decision, primary)
    }
}

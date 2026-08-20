package nz.myinspection.core.media

/** The worker adapter can log [failure], while [decision] remains the only WorkManager-visible outcome. */
data class PhotoOrphanCleanupExecutionResult(
    val decision: PhotoOrphanCleanupDecision,
    val failure: Throwable?,
)

/**
 * Resource lifecycle for the Android worker, kept generic so JVM tests execute the exact primary/suppression rules.
 *
 * The app supplies an AndroidSqliteDriver; this core helper deliberately knows only AutoCloseable and the cleanup
 * decision domain. A close failure converts only an otherwise-successful run to retry and never replaces a primary.
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
                } else {
                    primary = closeFailure
                    if (decision == PhotoOrphanCleanupDecision.SUCCESS) {
                        decision = PhotoOrphanCleanupDecision.RETRY
                    }
                }
            }
        }
        return PhotoOrphanCleanupExecutionResult(decision, primary)
    }
}

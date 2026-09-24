package nz.myinspection.app.media

import android.content.Context
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteTableLockedException
import androidx.work.Worker
import androidx.work.WorkerParameters
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import java.io.IOException
import nz.myinspection.app.platform.SafeLog
import nz.myinspection.app.platform.SafeLogEvent
import nz.myinspection.app.platform.SafeLogOpaqueId
import nz.myinspection.app.platform.SafeLogOperation
import nz.myinspection.app.platform.SafeLogReason
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.media.PhotoOrphanCleanupDecision
import nz.myinspection.core.media.PhotoOrphanCleanupExecution
import nz.myinspection.core.media.PhotoOrphanCleanupIssue
import nz.myinspection.core.media.PhotoOrphanCleanupReport
import nz.myinspection.core.media.PhotoOrphanCleanupRunner
import nz.myinspection.core.media.PhotoOrphanSqliteFailureKind
import nz.myinspection.core.media.isRetryablePhotoOrphanSqliteFailure

/** Android adapter: constructs the private DB runtime and delegates decision/lifecycle semantics to :core. */
class PhotoOrphanCleanupWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : Worker(appContext, parameters) {
    override fun doWork(): Result {
        var cleanupReport: PhotoOrphanCleanupReport? = null
        val execution = PhotoOrphanCleanupExecution.run(
            open = {
                val storage = PhotoRuntimeStorage.from(applicationContext)
                CleanupResources(
                    storage = storage,
                    driver = AndroidSqliteDriver(MyInspectionDatabase.Schema, applicationContext, storage.databaseName),
                )
            },
            cleanup = { resources ->
                val database = MyInspectionDatabase(resources.driver)
                val deleter = PhotoAssetCleanupExecutor(resources.storage.mediaRoot)
                PhotoOrphanCleanupRunner(
                    database,
                    resources.storage.mediaRoot,
                    deleter,
                    syncAssetParentDirectory = PhotoDirectoryDurability::sync,
                ).run().also { report ->
                    cleanupReport = report
                }.decision
            },
            retryable = ::isRetryableEnvironmentFailure,
        )
        reportFailures(cleanupReport?.issues().orEmpty(), execution, id.toString(), runAttemptCount, SafeLog.android())
        return when (execution.decision) {
            PhotoOrphanCleanupDecision.SUCCESS -> Result.success()
            PhotoOrphanCleanupDecision.RETRY -> Result.retry()
            PhotoOrphanCleanupDecision.FAILURE -> Result.failure()
        }
    }

    private fun isRetryableEnvironmentFailure(failure: Throwable): Boolean {
        val retryable = when (failure) {
            is IOException, is SecurityException -> true
            is SQLiteException -> isRetryablePhotoOrphanSqliteFailure(classifySqliteFailure(failure))
            else -> false
        }
        return retryable && failure.suppressed.all(::isRetryableEnvironmentFailure)
    }

    private fun classifySqliteFailure(failure: SQLiteException): PhotoOrphanSqliteFailureKind = when (failure) {
        is SQLiteDatabaseLockedException -> PhotoOrphanSqliteFailureKind.DATABASE_LOCKED
        is SQLiteTableLockedException -> PhotoOrphanSqliteFailureKind.TABLE_LOCKED
        is SQLiteDiskIOException -> PhotoOrphanSqliteFailureKind.DISK_IO
        is SQLiteCantOpenDatabaseException -> PhotoOrphanSqliteFailureKind.CANT_OPEN
        else -> PhotoOrphanSqliteFailureKind.OTHER
    }

    private data class CleanupResources(
        val storage: PhotoRuntimeStorage,
        val driver: AndroidSqliteDriver,
    ) : AutoCloseable {
        override fun close() {
            driver.close()
        }
    }

    companion object {
        internal fun reportFailures(
            issues: List<PhotoOrphanCleanupIssue>,
            execution: nz.myinspection.core.media.PhotoOrphanCleanupExecutionResult,
            workId: String,
            runAttemptCount: Int,
            log: SafeLog,
        ) {
            val opaqueId = SafeLogOpaqueId.parse(workId)
            issues.forEach { issue ->
                log.record(
                    SafeLogEvent(
                        SafeLogOperation.ORPHAN_CLEANUP,
                        issue.reason(),
                        opaqueId,
                        count = runAttemptCount,
                    ),
                )
            }
            if (execution.failure != null) {
                log.record(
                    SafeLogEvent(SafeLogOperation.ORPHAN_CLEANUP, SafeLogReason.EXECUTION_FAILED, opaqueId, runAttemptCount),
                )
            }
        }

        private fun PhotoOrphanCleanupIssue.reason(): SafeLogReason = when (bucket) {
            nz.myinspection.core.media.PhotoOrphanCleanupBucket.PENDING -> when (result) {
                nz.myinspection.core.media.PhotoOrphanCleanupIssueResult.REJECTED -> SafeLogReason.PENDING_REJECTED
                nz.myinspection.core.media.PhotoOrphanCleanupIssueResult.FAILED -> SafeLogReason.PENDING_FAILED
            }
            nz.myinspection.core.media.PhotoOrphanCleanupBucket.SOFT_DELETE -> when (result) {
                nz.myinspection.core.media.PhotoOrphanCleanupIssueResult.REJECTED -> SafeLogReason.SOFT_DELETE_REJECTED
                nz.myinspection.core.media.PhotoOrphanCleanupIssueResult.FAILED -> SafeLogReason.SOFT_DELETE_FAILED
            }
        }
    }
}

package nz.myinspection.app.media

import android.content.Context
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteTableLockedException
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import java.io.IOException
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
        cleanupReport?.issues()?.forEach { issue -> logIssue(issue) }
        execution.failure?.let { failure ->
            Log.e(
                TAG,
                "op=photoOrphanCleanup workId=$id runAttemptCount=$runAttemptCount " +
                    "result=execution_failure bucket=execution path=none cause=${failure.javaClass.name}",
                failure,
            )
        }
        return when (execution.decision) {
            PhotoOrphanCleanupDecision.SUCCESS -> Result.success()
            PhotoOrphanCleanupDecision.RETRY -> Result.retry()
            PhotoOrphanCleanupDecision.FAILURE -> Result.failure()
        }
    }

    private fun logIssue(issue: PhotoOrphanCleanupIssue) {
        val causeName = issue.cause?.javaClass?.name ?: "none"
        val message = "op=photoOrphanCleanup workId=$id runAttemptCount=$runAttemptCount " +
            "result=${issue.result.logValue} bucket=${issue.bucket.logValue} path=${issue.path} cause=$causeName"
        val cause = issue.cause
        if (cause == null) Log.e(TAG, message) else Log.e(TAG, message, cause)
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

    private companion object {
        const val TAG = "PhotoOrphanCleanup"
    }
}

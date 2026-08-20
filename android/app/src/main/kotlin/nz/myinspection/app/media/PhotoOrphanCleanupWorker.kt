package nz.myinspection.app.media

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import java.io.IOException
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.media.PhotoOrphanCleanupDecision
import nz.myinspection.core.media.PhotoOrphanCleanupExecution
import nz.myinspection.core.media.PhotoOrphanCleanupRunner

/** Android adapter: constructs the private DB runtime and delegates decision/lifecycle semantics to :core. */
class PhotoOrphanCleanupWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : Worker(appContext, parameters) {
    override fun doWork(): Result {
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
                PhotoOrphanCleanupRunner(database, resources.storage.mediaRoot, deleter).run()
            },
            retryable = ::isRetryableEnvironmentFailure,
        )
        execution.failure?.let { failure ->
            Log.e(TAG, "op=photoOrphanCleanup result=${decisionName(execution.decision)}", failure)
        }
        return when (execution.decision) {
            PhotoOrphanCleanupDecision.SUCCESS -> Result.success()
            PhotoOrphanCleanupDecision.RETRY -> Result.retry()
            PhotoOrphanCleanupDecision.FAILURE -> Result.failure()
        }
    }

    private fun isRetryableEnvironmentFailure(failure: Throwable): Boolean =
        failure is IOException || failure is SecurityException || failure is SQLiteException

    private fun decisionName(decision: PhotoOrphanCleanupDecision): String = when (decision) {
        PhotoOrphanCleanupDecision.SUCCESS -> "success"
        PhotoOrphanCleanupDecision.RETRY -> "retry"
        PhotoOrphanCleanupDecision.FAILURE -> "failure"
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

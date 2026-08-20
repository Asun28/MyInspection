package nz.myinspection.app.media

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Installs one conservative periodic recovery job; KEEP makes repeated Activity launches idempotent. */
object PhotoOrphanCleanupScheduler {
    private const val UNIQUE_WORK_NAME = "photo-orphan-cleanup"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<PhotoOrphanCleanupWorker>(24, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

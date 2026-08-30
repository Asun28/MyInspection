package nz.myinspection.app.feature.schedule

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

internal object ReminderWorkData {
    const val PROPERTY_ID = "property_id"
    const val INSPECTION_TYPE = "inspection_type"
    const val DUE_AT_EPOCH_MILLIS = "due_at_epoch_millis"
    const val OCCURRENCE_ID = "occurrence_id"
}

object ScheduleReminderScheduler {
    @Synchronized
    fun schedule(context: Context, spec: ReminderWorkSpec): Boolean {
        val store = SharedPreferencesReminderOccurrenceStore(context.applicationContext)
        return ReminderRegistrationCoordinator(store).register(spec.occurrenceId) { complete ->
            val input = Data.Builder()
                .putString(ReminderWorkData.PROPERTY_ID, spec.route.propertyId)
                .putString(ReminderWorkData.INSPECTION_TYPE, spec.route.inspectionType.name)
                .putLong(ReminderWorkData.DUE_AT_EPOCH_MILLIS, spec.dueAt.toEpochMilli())
                .putString(ReminderWorkData.OCCURRENCE_ID, spec.occurrenceId)
                .build()
            val request = OneTimeWorkRequestBuilder<ScheduleReminderWorker>()
                .setInitialDelay(spec.initialDelayMillis, TimeUnit.MILLISECONDS)
                .setInputData(input)
                .build()
            val operation = WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(spec.uniqueWorkName, ExistingWorkPolicy.KEEP, request)
            operation.result.addListener(
                { complete(runCatching { operation.result.get() }.isSuccess) },
                DIRECT_EXECUTOR,
            )
        }
    }

    private val DIRECT_EXECUTOR = Executor(Runnable::run)
}

internal class SharedPreferencesReminderOccurrenceStore(context: Context) : ReminderOccurrenceStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(occurrenceId: String): ReminderReceiptState? = preferences.getString(occurrenceId, null)
        ?.let { runCatching { ReminderReceiptState.valueOf(it) }.getOrNull() }

    override fun write(occurrenceId: String, state: ReminderReceiptState): Boolean =
        preferences.edit().putString(occurrenceId, state.name).commit()

    override fun remove(occurrenceId: String) {
        preferences.edit().remove(occurrenceId).commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "schedule-reminder-occurrences"
    }
}

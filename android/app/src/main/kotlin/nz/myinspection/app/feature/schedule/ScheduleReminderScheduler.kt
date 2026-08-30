package nz.myinspection.app.feature.schedule

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

internal object ReminderWorkData {
    const val PROPERTY_ID = "property_id"
    const val INSPECTION_TYPE = "inspection_type"
    const val DUE_AT_EPOCH_MILLIS = "due_at_epoch_millis"
}

object ScheduleReminderScheduler {
    @Synchronized
    fun schedule(context: Context, spec: ReminderWorkSpec): Boolean {
        val store = SharedPreferencesReminderOccurrenceStore(context.applicationContext)
        val gate = ReminderRegistrationGate(store)
        if (!gate.claim(spec.occurrenceId)) return false
        val input = Data.Builder()
            .putString(ReminderWorkData.PROPERTY_ID, spec.route.propertyId)
            .putString(ReminderWorkData.INSPECTION_TYPE, spec.route.inspectionType.name)
            .putLong(ReminderWorkData.DUE_AT_EPOCH_MILLIS, spec.dueAt.toEpochMilli())
            .build()
        val request = OneTimeWorkRequestBuilder<ScheduleReminderWorker>()
            .setInitialDelay(spec.initialDelayMillis, TimeUnit.MILLISECONDS)
            .setInputData(input)
            .build()
        return try {
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(spec.uniqueWorkName, ExistingWorkPolicy.KEEP, request)
            true
        } catch (error: RuntimeException) {
            gate.rollback(spec.occurrenceId)
            throw error
        }
    }
}

private class SharedPreferencesReminderOccurrenceStore(context: Context) : ReminderOccurrenceStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun claim(occurrenceId: String): Boolean {
        if (preferences.contains(occurrenceId)) return false
        return preferences.edit().putBoolean(occurrenceId, true).commit()
    }

    override fun remove(occurrenceId: String) {
        preferences.edit().remove(occurrenceId).commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "schedule-reminder-occurrences"
    }
}

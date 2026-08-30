package nz.myinspection.app.feature.schedule
import android.content.Context
import android.util.Log
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
        return schedule(spec, store, AndroidReminderLogger) { work, complete ->
            val input = Data.Builder()
                .putString(ReminderWorkData.PROPERTY_ID, work.route.propertyId)
                .putString(ReminderWorkData.INSPECTION_TYPE, work.route.inspectionType.name)
                .putLong(ReminderWorkData.DUE_AT_EPOCH_MILLIS, work.dueAt.toEpochMilli())
                .putString(ReminderWorkData.OCCURRENCE_ID, work.occurrenceId)
                .build()
            val request = OneTimeWorkRequestBuilder<ScheduleReminderWorker>()
                .setInitialDelay(work.initialDelayMillis, TimeUnit.MILLISECONDS)
                .setInputData(input)
                .build()
            val operation = WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(work.uniqueName, work.existingWorkPolicy, request)
            operation.result.addListener(
                { complete(runCatching { operation.result.get() }.isSuccess) },
                DIRECT_EXECUTOR,
            )
        }
    }
    internal fun schedule(
        spec: ReminderWorkSpec,
        store: ReminderOccurrenceStore,
        logger: ReminderEventLogger,
        enqueue: (ReminderEnqueueSpec, (Boolean) -> Unit) -> Unit,
    ): Boolean {
        val work = ReminderEnqueueSpec(spec.uniqueWorkName, spec.initialDelayMillis, spec.route, spec.dueAt, spec.occurrenceId, ExistingWorkPolicy.KEEP)
        val coordinator = ReminderRegistrationCoordinator(store) {
            logger.log(it, spec.occurrenceId, spec.route.inspectionType, true, ReminderLogError.RECEIPT_WRITE_FAILED)
        }
        return coordinator.register(spec.occurrenceId) { complete ->
            try {
                enqueue(work) { succeeded ->
                    if (!succeeded) logger.log(ReminderLogStage.ENQUEUE, spec.occurrenceId, spec.route.inspectionType, true, ReminderLogError.ENQUEUE_FAILED)
                    complete(succeeded)
                }
            } catch (error: RuntimeException) {
                logger.log(ReminderLogStage.ENQUEUE, spec.occurrenceId, spec.route.inspectionType, true, ReminderLogError.ENQUEUE_EXCEPTION)
                throw error
            }
        }
    }
    private val DIRECT_EXECUTOR = Executor(Runnable::run)
}
internal fun interface ReminderEventLogger {
    fun log(stage: ReminderLogStage, occurrenceId: String?, type: nz.myinspection.core.schedule.InspectionScheduleType?, retryable: Boolean, errorCode: ReminderLogError)
}
internal fun reminderLogMessage(stage: ReminderLogStage, occurrenceId: String?, type: nz.myinspection.core.schedule.InspectionScheduleType?, retryable: Boolean, errorCode: ReminderLogError): String =
    "{\"event\":\"schedule-reminder\",\"stage\":\"${stage.name.lowercase().replace('_', '-')}\",\"occurrence\":\"${occurrenceId?.takeIf { it.matches(Regex("[0-9a-f]{64}")) } ?: "missing"}\",\"type\":\"${type?.name ?: "unknown"}\",\"retryable\":$retryable,\"error_code\":\"${errorCode.name.lowercase().replace('_', '-')}\"}"
internal object AndroidReminderLogger : ReminderEventLogger {
    override fun log(stage: ReminderLogStage, occurrenceId: String?, type: nz.myinspection.core.schedule.InspectionScheduleType?, retryable: Boolean, errorCode: ReminderLogError) {
        Log.w("ScheduleReminder", reminderLogMessage(stage, occurrenceId, type, retryable, errorCode))
    }
}
internal class SharedPreferencesReminderOccurrenceStore(context: Context) : ReminderOccurrenceStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    override fun read(occurrenceId: String): ReminderReceiptState? = preferences.getString(occurrenceId, null)
        ?.let { runCatching { ReminderReceiptState.valueOf(it) }.getOrNull() }
    override fun compareAndSet(occurrenceId: String, expected: Set<ReminderReceiptState?>, state: ReminderReceiptState?): Boolean = synchronized(LOCK) {
        if (read(occurrenceId) !in expected) false
        else if (state == null) preferences.edit().remove(occurrenceId).commit()
        else preferences.edit().putString(occurrenceId, state.name).commit()
    }
    private companion object {
        const val PREFERENCES_NAME = "schedule-reminder-occurrences"
        val LOCK = Any()
    }
}

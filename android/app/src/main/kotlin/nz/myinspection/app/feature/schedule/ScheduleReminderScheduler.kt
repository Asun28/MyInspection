package nz.myinspection.app.feature.schedule
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
internal object WorkKeys {
    const val PROPERTY_ID = "property_id"
    const val INSPECTION_TYPE = "inspection_type"
    const val DUE_AT_EPOCH_MILLIS = "due_at_epoch_millis"
    const val OCCURRENCE_ID = "occurrence_id"
}
object ReminderScheduler {
    @Synchronized
    fun schedule(context: Context, spec: ReminderSpec, onResult: (Boolean) -> Unit = {}): Boolean {
        val store = SharedPreferencesReceiptStore(context.applicationContext)
        val report: (Boolean) -> Unit = { result -> ContextCompat.getMainExecutor(context).execute { onResult(result) } }
        return schedule(spec, store, AndroidReminderLogger, report) { work, complete ->
            enqueueWorkManagerReminder(work) { name, policy, request ->
                val operation = WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(name, policy, request)
                operation.result.addListener({ complete(runCatching { operation.result.get() }.isSuccess) }, ContextCompat.getMainExecutor(context))
            }
        }
    }
    internal fun schedule(
        spec: ReminderSpec,
        store: ReceiptStore,
        logger: EventLogger,
        onResult: (Boolean) -> Unit = {},
        enqueue: (EnqueueSpec, (Boolean) -> Unit) -> Unit,
    ): Boolean {
        val work = EnqueueSpec(spec.uniqueWorkName, spec.initialDelayMillis, spec.route, spec.dueAt, spec.occurrenceId, ExistingWorkPolicy.KEEP)
        val coordinator = RegistrationCoordinator(store) {
            logger.log(it, spec.occurrenceId, spec.route.inspectionType, true, LogError.RECEIPT_WRITE_FAILED)
        }
        return coordinator.register(spec.occurrenceId, onResult) { complete ->
            try {
                enqueue(work) { succeeded ->
                    if (!succeeded) logger.log(LogStage.ENQUEUE, spec.occurrenceId, spec.route.inspectionType, true, LogError.ENQUEUE_FAILED)
                    complete(succeeded)
                }
            } catch (error: RuntimeException) {
                logger.log(LogStage.ENQUEUE, spec.occurrenceId, spec.route.inspectionType, true, LogError.ENQUEUE_EXCEPTION)
                complete(false)
            }
        }
    }
}
internal fun enqueueWorkManagerReminder(work: EnqueueSpec, submit: (String, ExistingWorkPolicy, androidx.work.OneTimeWorkRequest) -> Unit) {
    val input = Data.Builder().putString(WorkKeys.PROPERTY_ID, work.route.propertyId).putString(WorkKeys.INSPECTION_TYPE, work.route.inspectionType.name).putLong(WorkKeys.DUE_AT_EPOCH_MILLIS, work.dueAt.toEpochMilli()).putString(WorkKeys.OCCURRENCE_ID, work.occurrenceId).build()
    submit(work.uniqueName, work.existingWorkPolicy, OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(work.initialDelayMillis, TimeUnit.MILLISECONDS).setInputData(input).build())
}
internal fun interface EventLogger {
    fun log(stage: LogStage, occurrenceId: String?, type: nz.myinspection.core.schedule.InspectionScheduleType?, retryable: Boolean, errorCode: LogError)
}
internal fun reminderLogMessage(stage: LogStage, occurrenceId: String?, type: nz.myinspection.core.schedule.InspectionScheduleType?, retryable: Boolean, errorCode: LogError): String =
    "{\"event\":\"schedule-reminder\",\"stage\":\"${stage.name.lowercase().replace('_', '-')}\",\"occurrence\":\"${occurrenceId?.takeIf { it.matches(Regex("[0-9a-f]{64}")) } ?: "missing"}\",\"type\":\"${type?.name ?: "unknown"}\",\"retryable\":$retryable,\"error_code\":\"${errorCode.name.lowercase().replace('_', '-')}\"}"
internal object AndroidReminderLogger : EventLogger {
    override fun log(stage: LogStage, occurrenceId: String?, type: nz.myinspection.core.schedule.InspectionScheduleType?, retryable: Boolean, errorCode: LogError) {
        Log.w("ScheduleReminder", reminderLogMessage(stage, occurrenceId, type, retryable, errorCode))
    }
}
internal class SharedPreferencesReceiptStore(context: Context) : ReceiptStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    override fun read(occurrenceId: String): Receipt? = preferences.getString(occurrenceId, null)
        ?.let { runCatching { Receipt.valueOf(it) }.getOrNull() }
    override fun compareAndSet(occurrenceId: String, expected: Set<Receipt?>, state: Receipt?): Boolean = synchronized(LOCK) {
        if (read(occurrenceId) !in expected) false
        else if (state == null) preferences.edit().remove(occurrenceId).commit()
        else preferences.edit().putString(occurrenceId, state.name).commit()
    }
    private companion object {
        const val PREFERENCES_NAME = "schedule-reminder-occurrences"
        val LOCK = Any()
    }
}

package nz.myinspection.app.feature.schedule
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import nz.myinspection.core.schedule.InspectionScheduleType
private typealias SRS = ReceiptState
private typealias FC = FailureCauseCode; private typealias SR = RegistrationResult; private class CallbackFailure(val failure: Throwable) : RuntimeException(failure)
internal object WorkKeys {
    const val PROPERTY_ID = "property_id"
    const val INSPECTION_TYPE = "inspection_type"
    const val DUE_AT_INSTANT = "due_at_instant"
    const val OCCURRENCE_ID = "occurrence_id"
}
data class EnqueueSpec(val uniqueName: String, val route: ScheduleRoute, val dueAt: Instant,
    val occurrenceId: String, val existingWorkPolicy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
    companion object { fun from(spec: ReminderSpec) = EnqueueSpec(spec.uniqueWorkName, spec.route, spec.dueAt, spec.occurrenceId) }
}
sealed interface EnqueueResult {
    data object Accepted : EnqueueResult
    data class Rejected(val error: Throwable?) : EnqueueResult
}
enum class FailureKind { TRANSIENT, PERMANENT }
enum class FailureCauseCode {
    SECURITY, INVALID_ARGUMENT, ILLEGAL_STATE, IO, CANCELLED, INTERRUPTED,
    UNKNOWN_RUNTIME, UNKNOWN, INVALID_INPUT, PERMISSION_DENIED, STORAGE_CORRUPT, STORAGE_WRITE, STORAGE_MISSING,
    DELIVERY_UNCERTAIN, RETRYABLE_STATE,
}
data class FailureDisposition(val kind: FailureKind, val causeCode: FC)
fun classifyReminderFailure(error: Throwable): FailureDisposition {
    if (error is ExecutionException && error.cause != null) {
        return classifyReminderFailure(requireNotNull(error.cause))
    }
    return when (error) {
        is SecurityException -> FailureDisposition(FailureKind.PERMANENT, FC.SECURITY)
        is CancellationException -> FailureDisposition(FailureKind.TRANSIENT, FC.CANCELLED)
        is IllegalArgumentException -> permanent(FC.INVALID_ARGUMENT)
        is IllegalStateException -> permanent(FC.ILLEGAL_STATE)
        is IOException -> transient(FC.IO)
        is InterruptedException -> transient(FC.INTERRUPTED)
        is RuntimeException -> permanent(FC.UNKNOWN_RUNTIME)
        else -> permanent(FC.UNKNOWN)
    }
}
private fun transient(cause: FC) = FailureDisposition(FailureKind.TRANSIENT, cause)
private fun permanent(cause: FC) = FailureDisposition(FailureKind.PERMANENT, cause)
enum class LogStage { ENQUEUE, INPUT, PERMISSION, RECEIPT_ENQUEUED, RECEIPT_DELIVERED, NOTIFY }
enum class LogError {
    ENQUEUE_FAILED, INVALID_INPUT, PERMISSION_DENIED,
    RECEIPT_CORRUPT, RECEIPT_MISSING, RECEIPT_WRITE_FAILED, NOTIFY_FAILED, DELIVERY_UNCERTAIN, RETRYABLE_RECEIPT,
}
data class LogRecord(
    val stage: LogStage, val occurrenceId: String?, val type: InspectionScheduleType?,
    val retryable: Boolean, val errorCode: LogError, val causeCode: FC,
)
internal fun interface EventLogger { fun log(record: LogRecord) }
internal fun reminderLogMessage(record: LogRecord): String {
    val occurrence = record.occurrenceId
        ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
        ?: "missing"
    return "{" +
        "\"event\":\"schedule-reminder\"," +
        "\"stage\":\"${record.stage.wireValue()}\"," +
        "\"occurrence\":\"$occurrence\"," +
        "\"type\":\"${record.type?.name ?: "unknown"}\"," +
        "\"retryable\":${record.retryable}," +
        "\"error_code\":\"${record.errorCode.wireValue()}\"," +
        "\"cause_code\":\"${record.causeCode.wireValue()}\"}"
}
private fun Enum<*>.wireValue() = name.lowercase().replace('_', '-')
internal object AndroidReminderLogger : EventLogger {
    override fun log(record: LogRecord) = Log.w("ScheduleReminder", reminderLogMessage(record)).let { Unit }
}
object ReminderScheduler {
    @Synchronized
    fun schedule(
        context: Context,
        spec: ReminderSpec,
        onResult: (SR) -> Unit = {},
    ): Boolean {
        val appContext = context.applicationContext
        val executor = ContextCompat.getMainExecutor(context)
        return schedule(spec, SharedPreferencesReceiptStore(appContext), AndroidReminderLogger, { result ->
            executor.execute { onResult(result) }
        }) { work, complete ->
            enqueueWorkManagerReminder(work) { name, policy, request ->
                val operation = WorkManager.getInstance(appContext).enqueueUniqueWork(name, policy, request)
                operation.result.addListener({
                    val result = try {
                        operation.result.get()
                        EnqueueResult.Accepted
                    } catch (error: InterruptedException) {
                        Thread.currentThread().interrupt()
                        EnqueueResult.Rejected(error)
                    } catch (error: Exception) {
                        EnqueueResult.Rejected(error)
                    }
                    complete(result)
                }, executor)
            }
        }
    }
    internal fun schedule(
        spec: ReminderSpec, store: ReceiptStore, logger: EventLogger,
        onResult: (SR) -> Unit = {},
        enqueue: (EnqueueSpec, (EnqueueResult) -> Unit) -> Unit,
    ): Boolean {
        if (runCatching { WorkSpecFactory().create(spec.route, spec.dueAt) }.getOrNull() != spec) {
            logger.log(LogRecord(LogStage.INPUT, null, spec.route.inspectionType, false, LogError.INVALID_INPUT, FC.INVALID_INPUT))
            onResult(SR.PERMANENT_FAILURE); return false
        }
        fun receiptFailure(stage: LogStage, error: LogError, cause: FC) {
            logger.log(LogRecord(stage, spec.occurrenceId, spec.route.inspectionType, false, error, cause))
        }
        val coordinator = RegistrationCoordinator(store) { state -> when (state) {
            SRS.CORRUPT -> receiptFailure(LogStage.RECEIPT_ENQUEUED, LogError.RECEIPT_CORRUPT, FC.STORAGE_CORRUPT)
            SRS.INDETERMINATE -> receiptFailure(LogStage.RECEIPT_ENQUEUED, LogError.RECEIPT_WRITE_FAILED, FC.STORAGE_WRITE)
            SRS.DELIVERY_UNCERTAIN -> receiptFailure(LogStage.NOTIFY, LogError.DELIVERY_UNCERTAIN, FC.DELIVERY_UNCERTAIN)
            else -> Unit
        } }
        return coordinator.register(spec.occurrenceId, onResult) { complete ->
            try {
                enqueue(EnqueueSpec.from(spec)) { result ->
                    try { if (result == EnqueueResult.Accepted) complete(SR.SUCCESS)
                        else complete(logEnqueueFailure(spec, (result as EnqueueResult.Rejected).error, logger).registrationResult())
                    } catch (error: Throwable) { throw CallbackFailure(error) }
                }
            } catch (error: CallbackFailure) { throw error.failure
            } catch (error: Exception) { complete(logEnqueueFailure(spec, error, logger).registrationResult()) }
        }
    }
}
private fun logEnqueueFailure(
    spec: ReminderSpec,
    error: Throwable?,
    logger: EventLogger,
): FailureDisposition {
    val disposition = error?.let(::classifyReminderFailure) ?: permanent(FC.UNKNOWN)
    logger.log(LogRecord(LogStage.ENQUEUE, spec.occurrenceId, spec.route.inspectionType,
        disposition.kind == FailureKind.TRANSIENT, LogError.ENQUEUE_FAILED, disposition.causeCode))
    return disposition
}
private fun FailureDisposition.registrationResult() =
    if (kind == FailureKind.TRANSIENT) SR.RETRYABLE_FAILURE
    else SR.PERMANENT_FAILURE
internal fun enqueueWorkManagerReminder(
    work: EnqueueSpec,
    clock: Clock = Clock.systemUTC(),
    submit: (String, ExistingWorkPolicy, OneTimeWorkRequest) -> Unit,
) {
    val input = Data.Builder()
        .putString(WorkKeys.PROPERTY_ID, work.route.propertyId)
        .putString(WorkKeys.INSPECTION_TYPE, work.route.inspectionType.name)
        .putString(WorkKeys.DUE_AT_INSTANT, work.dueAt.toString())
        .putString(WorkKeys.OCCURRENCE_ID, work.occurrenceId)
        .build()
    val request = OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(reminderDelayMillis(clock.instant(), work.dueAt), TimeUnit.MILLISECONDS).setInputData(input).build()
    submit(work.uniqueName, work.existingWorkPolicy, request)
}
internal fun reminderDelayMillis(now: Instant, dueAt: Instant): Long { val delay = Duration.between(now, dueAt)
    return if (delay.isNegative || delay.isZero) 0L else Math.addExact(delay.toMillis(), if (delay.nano % 1_000_000 == 0) 0L else 1L) }
internal fun decodeReceipt(raw: String?): SRS = when (raw) {
    null -> SRS.MISSING
    SRS.MISSING.name -> SRS.CORRUPT
    else -> runCatching { SRS.valueOf(raw) }.getOrDefault(SRS.CORRUPT)
}
private val STORE_LOCK = Any()
private val TAINTED = mutableSetOf<String>()
internal fun receiptStore(readRaw: (String) -> String?, writeRaw: (String, String?) -> Boolean) = object : ReceiptStore {
    override fun read(id: String) = synchronized(STORE_LOCK) {
        if (id in TAINTED) SRS.INDETERMINATE
        else try { decodeReceipt(readRaw(id)) } catch (_: RuntimeException) { SRS.CORRUPT }
    }
    override fun compareAndSet(id: String, expected: Set<SRS>, state: SRS) = synchronized(STORE_LOCK) {
        require(state != SRS.CORRUPT)
        if (id in TAINTED) return@synchronized WriteResult.Failed
        try {
            val current = read(id)
            if (current !in expected) WriteResult.Mismatch(current)
            else if (writeRaw(id, state.takeUnless { it == SRS.MISSING }?.name)) WriteResult.Applied
            else taint(id)
        } catch (_: RuntimeException) { taint(id) }
    }
}
private fun taint(id: String) = WriteResult.Failed.also { TAINTED += id }
private const val STORE_NAME = "schedule-reminder-occurrences"
private fun preferenceStore(context: Context) = context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE).let { preferences ->
    receiptStore({ preferences.getString(it, null) }) { id, value ->
        (if (value == null) preferences.edit().remove(id) else preferences.edit().putString(id, value)).commit()
    }
}
internal class SharedPreferencesReceiptStore(context: Context) : ReceiptStore by preferenceStore(context)

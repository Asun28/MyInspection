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

internal object WorkKeys {
    const val PROPERTY_ID = "property_id"
    const val INSPECTION_TYPE = "inspection_type"
    const val DUE_AT_INSTANT = "due_at_instant"
    const val OCCURRENCE_ID = "occurrence_id"
}
data class EnqueueSpec(
    val uniqueName: String, val route: ScheduleRoute, val dueAt: Instant,
    val occurrenceId: String,
    val existingWorkPolicy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
) {
    companion object {
        fun from(spec: ReminderSpec) = EnqueueSpec(spec.uniqueWorkName, spec.route, spec.dueAt, spec.occurrenceId)
    }
}
sealed interface EnqueueResult {
    data object Accepted : EnqueueResult
    data class Rejected(val error: Throwable?) : EnqueueResult
}
enum class FailureKind { TRANSIENT, PERMANENT }
enum class FailureCauseCode {
    SECURITY, INVALID_ARGUMENT, ILLEGAL_STATE, IO, CANCELLED, INTERRUPTED,
    UNKNOWN_RUNTIME, UNKNOWN, INVALID_INPUT, PERMISSION_DENIED, STORAGE_CORRUPT, STORAGE_WRITE,
}
data class FailureDisposition(val kind: FailureKind, val causeCode: FailureCauseCode)
fun classifyReminderFailure(error: Throwable): FailureDisposition {
    if (error is ExecutionException && error.cause != null) {
        return classifyReminderFailure(requireNotNull(error.cause))
    }
    return when (error) {
        is SecurityException -> FailureDisposition(FailureKind.PERMANENT, FailureCauseCode.SECURITY)
        is CancellationException -> FailureDisposition(FailureKind.TRANSIENT, FailureCauseCode.CANCELLED)
        is IllegalArgumentException -> permanent(FailureCauseCode.INVALID_ARGUMENT)
        is IllegalStateException -> permanent(FailureCauseCode.ILLEGAL_STATE)
        is IOException -> transient(FailureCauseCode.IO)
        is InterruptedException -> transient(FailureCauseCode.INTERRUPTED)
        is RuntimeException -> transient(FailureCauseCode.UNKNOWN_RUNTIME)
        else -> permanent(FailureCauseCode.UNKNOWN)
    }
}
private fun transient(cause: FailureCauseCode) = FailureDisposition(FailureKind.TRANSIENT, cause)
private fun permanent(cause: FailureCauseCode) = FailureDisposition(FailureKind.PERMANENT, cause)
enum class LogStage { ENQUEUE, INPUT, PERMISSION, RECEIPT_ENQUEUED, RECEIPT_DELIVERED, NOTIFY }
enum class LogError {
    ENQUEUE_FAILED, INVALID_INPUT, PERMISSION_DENIED,
    RECEIPT_CORRUPT, RECEIPT_WRITE_FAILED, NOTIFY_FAILED,
}
data class LogRecord(
    val stage: LogStage, val occurrenceId: String?, val type: InspectionScheduleType?,
    val retryable: Boolean, val errorCode: LogError, val causeCode: FailureCauseCode,
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
    override fun log(record: LogRecord) {
        Log.w("ScheduleReminder", reminderLogMessage(record))
    }
}
object ReminderScheduler {
    @Synchronized
    fun schedule(
        context: Context,
        spec: ReminderSpec,
        onResult: (RegistrationResult) -> Unit = {},
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
        onResult: (RegistrationResult) -> Unit = {},
        enqueue: (EnqueueSpec, (EnqueueResult) -> Unit) -> Unit,
    ): Boolean {
        fun receiptFailure(stage: LogStage, error: LogError, cause: FailureCauseCode) {
            logger.log(LogRecord(stage, spec.occurrenceId, spec.route.inspectionType, false, error, cause))
        }
        val coordinator = RegistrationCoordinator(
            store,
            onCorruptReceipt = {
                receiptFailure(LogStage.RECEIPT_ENQUEUED, LogError.RECEIPT_CORRUPT, FailureCauseCode.STORAGE_CORRUPT)
            },
            onWriteFailure = {
                receiptFailure(LogStage.RECEIPT_ENQUEUED, LogError.RECEIPT_WRITE_FAILED, FailureCauseCode.STORAGE_WRITE)
            },
        )
        return coordinator.register(spec.occurrenceId, onResult) { complete ->
            try {
                enqueue(EnqueueSpec.from(spec)) { result ->
                    if (result == EnqueueResult.Accepted) {
                        complete(RegistrationResult.SUCCESS)
                    } else {
                        val disposition = logEnqueueFailure(
                            spec,
                            (result as EnqueueResult.Rejected).error,
                            logger,
                        )
                        complete(disposition.registrationResult())
                    }
                }
            } catch (error: Exception) {
                complete(logEnqueueFailure(spec, error, logger).registrationResult())
            }
        }
    }
}
private fun logEnqueueFailure(
    spec: ReminderSpec,
    error: Throwable?,
    logger: EventLogger,
): FailureDisposition {
    val disposition = error?.let(::classifyReminderFailure) ?: transient(FailureCauseCode.UNKNOWN)
    logger.log(
        LogRecord(
            LogStage.ENQUEUE, spec.occurrenceId, spec.route.inspectionType,
            disposition.kind == FailureKind.TRANSIENT, LogError.ENQUEUE_FAILED, disposition.causeCode,
        ),
    )
    return disposition
}
private fun FailureDisposition.registrationResult() =
    if (kind == FailureKind.TRANSIENT) RegistrationResult.RETRYABLE_FAILURE
    else RegistrationResult.PERMANENT_FAILURE
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
    val request = OneTimeWorkRequestBuilder<ReminderWorker>()
        .setInitialDelay(
            maxOf(
                0L,
                Duration.between(clock.instant(), work.dueAt).toMillis(),
            ),
            TimeUnit.MILLISECONDS,
        )
        .setInputData(input)
        .build()
    submit(work.uniqueName, work.existingWorkPolicy, request)
}
internal fun decodeReceipt(raw: String?): ReceiptState = when (raw) {
    null -> ReceiptState.MISSING
    ReceiptState.ENQUEUED.name -> ReceiptState.ENQUEUED
    ReceiptState.DELIVERED.name -> ReceiptState.DELIVERED
    ReceiptState.RETRYABLE.name -> ReceiptState.RETRYABLE
    ReceiptState.TERMINAL.name -> ReceiptState.TERMINAL
    else -> ReceiptState.CORRUPT
}
internal fun readReceipt(readRaw: () -> String?): ReceiptState = try { decodeReceipt(readRaw()) }
catch (_: RuntimeException) { ReceiptState.CORRUPT }
internal class SharedPreferencesReceiptStore(context: Context) : ReceiptStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    override fun read(occurrenceId: String) = readReceipt { preferences.getString(occurrenceId, null) }
    override fun compareAndSet(
        occurrenceId: String, expected: Set<ReceiptState>, state: ReceiptState,
    ): Boolean = synchronized(LOCK) {
        require(state != ReceiptState.CORRUPT) { "CORRUPT is a read-only receipt state" }
        if (read(occurrenceId) !in expected) false
        else if (state == ReceiptState.MISSING) preferences.edit().remove(occurrenceId).commit()
        else preferences.edit().putString(occurrenceId, state.name).commit()
    }
    private companion object {
        const val PREFERENCES_NAME = "schedule-reminder-occurrences"
        val LOCK = Any()
    }
}

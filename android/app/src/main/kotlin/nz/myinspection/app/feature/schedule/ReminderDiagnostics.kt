package nz.myinspection.app.feature.schedule

import android.util.Log
import java.io.IOException
import nz.myinspection.core.schedule.InspectionScheduleType

enum class LogStage(val wireValue: String) {
    INPUT("input"),
    RECEIPT("receipt"),
    PERMISSION("permission"),
    PREPARATION("preparation"),
    NOTIFY("notify"),
}

enum class LogError(val wireValue: String) {
    INVALID_INPUT("invalid-input"),
    RECEIPT_INVALID("receipt-invalid"),
    RECEIPT_WRITE_FAILED("receipt-write-failed"),
    PERMISSION_DENIED("permission-denied"),
    PREPARATION_FAILED("preparation-failed"),
    NOTIFY_FAILED("notify-failed"),
}

enum class FailureCauseCode(val wireValue: String) {
    INVALID_INPUT("invalid-input"),
    SECURITY("security"),
    ILLEGAL_STATE("illegal-state"),
    IO("io"),
    UNKNOWN("unknown"),
}

enum class FailureKind {
    TRANSIENT,
    PERMANENT,
}

data class FailureDisposition(
    val kind: FailureKind,
    val causeCode: FailureCauseCode,
)

data class LogRecord(
    val stage: LogStage,
    val occurrenceId: String?,
    val type: InspectionScheduleType?,
    val generationNumber: Long?,
    val workRequestId: String?,
    val retryable: Boolean,
    val errorCode: LogError,
    val causeCode: FailureCauseCode,
)

fun classifyReminderFailure(error: Throwable): FailureDisposition = when (error) {
    is SecurityException -> FailureDisposition(FailureKind.PERMANENT, FailureCauseCode.SECURITY)
    is IOException -> FailureDisposition(FailureKind.TRANSIENT, FailureCauseCode.IO)
    is IllegalStateException -> FailureDisposition(FailureKind.PERMANENT, FailureCauseCode.ILLEGAL_STATE)
    else -> FailureDisposition(FailureKind.PERMANENT, FailureCauseCode.UNKNOWN)
}

fun reminderLogMessage(record: LogRecord): String {
    val occurrenceId = record.occurrenceId?.takeIf { it.matches(OCCURRENCE_ID_PATTERN) }
    val generationNumber = record.generationNumber?.takeIf { it >= 0 }
    val workRequestId = correlatedWorkRequestId(occurrenceId, generationNumber, record.workRequestId)
    return buildString {
        append("{\"event\":\"schedule-reminder\"")
        append(",\"stage\":\"")
        append(record.stage.wireValue)
        append("\",\"occurrence_id\":\"")
        append(occurrenceId ?: "missing")
        append("\",\"type\":")
        append(record.type?.name?.let { "\"$it\"" } ?: "null")
        append(",\"generation_number\":")
        append(generationNumber ?: "null")
        append(",\"work_request_id\":")
        append(workRequestId?.let { "\"$it\"" } ?: "null")
        append(",\"retryable\":")
        append(record.retryable)
        append(",\"error_code\":\"")
        append(record.errorCode.wireValue)
        append("\",\"cause_code\":\"")
        append(record.causeCode.wireValue)
        append("\"}")
    }
}

interface ReminderDiagnosticPort {
    fun record(record: LogRecord)
}

internal object NoOpReminderDiagnosticPort : ReminderDiagnosticPort {
    override fun record(record: LogRecord) = Unit
}

internal object AndroidReminderDiagnosticPort : ReminderDiagnosticPort {
    override fun record(record: LogRecord) {
        Log.w("ReminderDelivery", reminderLogMessage(record))
    }
}

/**
 * Returns the work request id only when [claimed] is exactly the one derived from [occurrenceId]
 * and [generationNumber].
 *
 * A4 asks for a validated occurrence/generation/work correlation, not three independently
 * plausible values. Checking the fields separately would let a valid but unrelated UUID be
 * published as though it belonged to this occurrence, and would keep a work id alive next to a
 * generation that could not be validated, so both of those become null here.
 *
 * The derived spelling is emitted rather than the caller's, so one work request always correlates
 * under a single canonical `work_request_id` whatever case it arrived in.
 */
private fun correlatedWorkRequestId(
    occurrenceId: String?,
    generationNumber: Long?,
    claimed: String?,
): String? {
    if (occurrenceId == null || generationNumber == null || claimed == null) return null
    val derived = reminderGenerationId(occurrenceId, generationNumber).toString()
    return derived.takeIf { it == claimed.lowercase() }
}

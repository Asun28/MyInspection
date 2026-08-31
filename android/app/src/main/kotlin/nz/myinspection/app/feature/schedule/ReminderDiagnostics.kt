package nz.myinspection.app.feature.schedule

import android.util.Log
import java.io.IOException
import java.util.UUID
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
    val occurrenceId = record.occurrenceId
        ?.takeIf { it.matches(OCCURRENCE_ID_PATTERN) }
        ?: "missing"
    val generationNumber = record.generationNumber?.takeIf { it >= 0 }
    val workRequestId = record.workRequestId?.let(::canonicalUuidOrNull)
    return buildString {
        append("{\"event\":\"schedule-reminder\"")
        append(",\"stage\":\"")
        append(record.stage.wireValue)
        append("\",\"occurrence_id\":\"")
        append(occurrenceId)
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
 * Returns the canonical lowercase spelling, or null when [value] is not exactly one UUID.
 *
 * The canonical form is returned rather than the caller's spelling so the same work request always
 * correlates under one `work_request_id`. Loose forms such as `1-1-1-1-1` parse but are not
 * canonical, so they are dropped instead of being silently widened.
 */
private fun canonicalUuidOrNull(value: String): String? = runCatching {
    UUID.fromString(value).toString().takeIf { it == value.lowercase() }
}.getOrNull()

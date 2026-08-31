package nz.myinspection.app.feature.schedule

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import nz.myinspection.core.schedule.InspectionScheduleType

class ReminderDiagnosticsTest {
    private val occurrenceId = "c118fefec6ee20d89eafa5533048237237d39116af40aa85123fb1f70c404108"
    private val workRequestId = "40fe7461-9be1-3ce7-8bdf-28b48b76359e"

    @Test
    fun `diagnostic JSON pins correlated fields and wire order`() {
        val record = LogRecord(
            stage = LogStage.NOTIFY,
            occurrenceId = occurrenceId,
            type = InspectionScheduleType.ROUTINE,
            generationNumber = 0L,
            workRequestId = workRequestId,
            retryable = false,
            errorCode = LogError.NOTIFY_FAILED,
            causeCode = FailureCauseCode.SECURITY,
        )

        assertEquals(
            "{" +
                "\"event\":\"schedule-reminder\"," +
                "\"stage\":\"notify\"," +
                "\"occurrence_id\":\"$occurrenceId\"," +
                "\"type\":\"ROUTINE\"," +
                "\"generation_number\":0," +
                "\"work_request_id\":\"$workRequestId\"," +
                "\"retryable\":false," +
                "\"error_code\":\"notify-failed\"," +
                "\"cause_code\":\"security\"}",
            reminderLogMessage(record),
        )
    }

    @Test
    fun `invalid correlation values become safe sentinels without raw leaks`() {
        val record = LogRecord(
            stage = LogStage.INPUT,
            occurrenceId = "property-a\nsecret",
            type = InspectionScheduleType.ROUTINE,
            generationNumber = null,
            workRequestId = "property-a secret",
            retryable = false,
            errorCode = LogError.INVALID_INPUT,
            causeCode = FailureCauseCode.INVALID_INPUT,
        )

        val json = reminderLogMessage(record)
        assertEquals(
            "{" +
                "\"event\":\"schedule-reminder\"," +
                "\"stage\":\"input\"," +
                "\"occurrence_id\":\"missing\"," +
                "\"type\":\"ROUTINE\"," +
                "\"generation_number\":null," +
                "\"work_request_id\":null," +
                "\"retryable\":false," +
                "\"error_code\":\"invalid-input\"," +
                "\"cause_code\":\"invalid-input\"}",
            json,
        )
        assertFalse(json.contains("property-a"))
        assertFalse(json.contains("secret"))
    }

    @Test
    fun `failure classifier emits stable cause without exception message`() {
        val disposition = classifyReminderFailure(IllegalStateException("property-a secret"))

        assertEquals(
            FailureDisposition(FailureKind.PERMANENT, FailureCauseCode.ILLEGAL_STATE),
            disposition,
        )
        val record = LogRecord(
            stage = LogStage.NOTIFY,
            occurrenceId = occurrenceId,
            type = InspectionScheduleType.ROUTINE,
            generationNumber = 0L,
            workRequestId = workRequestId,
            retryable = false,
            errorCode = LogError.NOTIFY_FAILED,
            causeCode = disposition.causeCode,
        )

        val json = reminderLogMessage(record)
        assertTrue(json.endsWith("\"cause_code\":\"illegal-state\"}"))
        assertFalse(json.contains("property-a"))
        assertFalse(json.contains("secret"))
    }

    @Test
    fun `work request id is emitted in canonical lowercase regardless of input case`() {
        val record = baseRecord(workRequestId = workRequestId.uppercase())

        val json = reminderLogMessage(record)

        assertTrue(json.contains("\"work_request_id\":\"$workRequestId\""))
        assertFalse(json.contains(workRequestId.uppercase()))
    }

    @Test
    fun `non canonical work request spellings are dropped rather than echoed`() {
        val rejected = listOf("1-1-1-1-1", "not-a-uuid", "", "$workRequestId ")

        rejected.forEach { candidate ->
            val json = reminderLogMessage(baseRecord(workRequestId = candidate))
            assertTrue(json.contains("\"work_request_id\":null"), "expected null for '$candidate'")
        }
    }

    @Test
    fun `negative generation numbers are dropped rather than serialized`() {
        val json = reminderLogMessage(baseRecord(generationNumber = -1L))

        assertTrue(json.contains("\"generation_number\":null"))
        assertFalse(json.contains("-1"))
    }

    @Test
    fun `every stage error and cause code reaches the wire under its declared value`() {
        LogStage.entries.forEach { stage ->
            assertTrue(reminderLogMessage(baseRecord(stage = stage))
                .contains("\"stage\":\"${stage.wireValue}\""))
        }
        LogError.entries.forEach { error ->
            assertTrue(reminderLogMessage(baseRecord(errorCode = error))
                .contains("\"error_code\":\"${error.wireValue}\""))
        }
        FailureCauseCode.entries.forEach { cause ->
            assertTrue(reminderLogMessage(baseRecord(causeCode = cause))
                .contains("\"cause_code\":\"${cause.wireValue}\""))
        }
    }

    @Test
    fun `failure classifier maps each known throwable to a stable retryability`() {
        assertEquals(
            FailureDisposition(FailureKind.PERMANENT, FailureCauseCode.SECURITY),
            classifyReminderFailure(SecurityException("property-a")),
        )
        assertEquals(
            FailureDisposition(FailureKind.TRANSIENT, FailureCauseCode.IO),
            classifyReminderFailure(IOException("property-a")),
        )
        assertEquals(
            FailureDisposition(FailureKind.PERMANENT, FailureCauseCode.UNKNOWN),
            classifyReminderFailure(RuntimeException("property-a")),
        )
    }

    private fun baseRecord(
        stage: LogStage = LogStage.NOTIFY,
        generationNumber: Long? = 0L,
        workRequestId: String? = this.workRequestId,
        errorCode: LogError = LogError.NOTIFY_FAILED,
        causeCode: FailureCauseCode = FailureCauseCode.SECURITY,
    ): LogRecord = LogRecord(
        stage = stage,
        occurrenceId = occurrenceId,
        type = InspectionScheduleType.ROUTINE,
        generationNumber = generationNumber,
        workRequestId = workRequestId,
        retryable = false,
        errorCode = errorCode,
        causeCode = causeCode,
    )

    @Test
    fun `no op diagnostic port stays silent for every stage`() {
        LogStage.entries.forEach { stage ->
            NoOpReminderDiagnosticPort.record(baseRecord(stage = stage))
        }
    }
}

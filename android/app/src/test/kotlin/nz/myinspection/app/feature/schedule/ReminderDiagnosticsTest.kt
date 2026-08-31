package nz.myinspection.app.feature.schedule

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
}

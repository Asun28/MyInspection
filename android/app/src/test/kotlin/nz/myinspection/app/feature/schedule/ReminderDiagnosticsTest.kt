package nz.myinspection.app.feature.schedule

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun `stage error and cause wire values match the frozen vocabulary exactly`() {
        assertEquals(
            mapOf(
                LogStage.INPUT to "input",
                LogStage.RECEIPT to "receipt",
                LogStage.PERMISSION to "permission",
                LogStage.PREPARATION to "preparation",
                LogStage.NOTIFY to "notify",
            ),
            LogStage.entries.associateWith { it.wireValue },
        )
        assertEquals(
            mapOf(
                LogError.INVALID_INPUT to "invalid-input",
                LogError.RECEIPT_INVALID to "receipt-invalid",
                LogError.RECEIPT_WRITE_FAILED to "receipt-write-failed",
                LogError.PERMISSION_DENIED to "permission-denied",
                LogError.PREPARATION_FAILED to "preparation-failed",
                LogError.NOTIFY_FAILED to "notify-failed",
            ),
            LogError.entries.associateWith { it.wireValue },
        )
        assertEquals(
            mapOf(
                FailureCauseCode.INVALID_INPUT to "invalid-input",
                FailureCauseCode.SECURITY to "security",
                FailureCauseCode.ILLEGAL_STATE to "illegal-state",
                FailureCauseCode.IO to "io",
                FailureCauseCode.UNKNOWN to "unknown",
            ),
            FailureCauseCode.entries.associateWith { it.wireValue },
        )
    }

    @Test
    fun `declared vocabulary values reach the wire`() {
        assertTrue(reminderLogMessage(baseRecord(stage = LogStage.PREPARATION))
            .contains("\"stage\":\"preparation\""))
        assertTrue(reminderLogMessage(baseRecord(errorCode = LogError.RECEIPT_WRITE_FAILED))
            .contains("\"error_code\":\"receipt-write-failed\""))
        assertTrue(reminderLogMessage(baseRecord(causeCode = FailureCauseCode.IO))
            .contains("\"cause_code\":\"io\""))
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
        occurrenceId: String? = this.occurrenceId,
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
    fun `no op diagnostic port is total and raises nothing for any stage`() {
        LogStage.entries.forEach { stage ->
            val thrown = runCatching {
                NoOpReminderDiagnosticPort.record(baseRecord(stage = stage))
            }.exceptionOrNull()

            assertNull(thrown, "no-op port raised for stage $stage")
        }
    }

    @Test
    fun `a valid but unrelated work request id is refused rather than published`() {
        val unrelated = "590ca815-2783-322a-acde-39ab31dafd39"

        val json = reminderLogMessage(baseRecord(generationNumber = 0L, workRequestId = unrelated))

        assertTrue(json.contains("\"work_request_id\":null"))
        assertFalse(json.contains(unrelated))
    }

    @Test
    fun `the work request id is published only beside the generation that derives it`() {
        assertTrue(
            reminderLogMessage(
                baseRecord(generationNumber = 1L, workRequestId = workRequestId),
            ).contains("\"work_request_id\":null"),
        )
        assertTrue(
            reminderLogMessage(
                baseRecord(
                    generationNumber = 1L,
                    workRequestId = "590ca815-2783-322a-acde-39ab31dafd39",
                ),
            ).contains("\"work_request_id\":\"590ca815-2783-322a-acde-39ab31dafd39\""),
        )
    }

    @Test
    fun `an uncorrelatable generation drops the work request id with it`() {
        listOf(null, -1L).forEach { generation ->
            val json = reminderLogMessage(baseRecord(generationNumber = generation))

            assertTrue(json.contains("\"generation_number\":null"), "generation $generation")
            assertTrue(json.contains("\"work_request_id\":null"), "generation $generation")
        }
    }

    @Test
    fun `a corrupt occurrence drops the work request id with it`() {
        val json = reminderLogMessage(baseRecord(occurrenceId = "property-a secret"))

        assertTrue(json.contains("\"occurrence_id\":\"missing\""))
        assertTrue(json.contains("\"work_request_id\":null"))
        assertFalse(json.contains("property-a"))
    }
}

/*
 * Semantic mutation receipts for ReminderDiagnostics.kt (card A5).
 *
 * Each row was applied alone to the final snapshot, the two focused test classes were run
 * expecting a non-zero exit, and the file was restored and re-hashed. Every mutation was
 * killed and the file returned to the identical digest, so no row below is a survivor.
 *
 * SHA-256 before all mutations: e6d1bbeca35700b09f8ddee04961d7ef1c6c60e0bea8fb61695624f0183f0297
 * SHA-256 after all mutations:  e6d1bbeca35700b09f8ddee04961d7ef1c6c60e0bea8fb61695624f0183f0297
 *
 * M11 [A4] RED exit 1
 *   selector: val generationNumber = record.generationNumber?.takeIf { it >= 0 }
 *   effect: negative generation numbers serialized into the diagnostic wire format
 * M12 [A4] RED exit 1
 *   selector: return derived.takeIf { it == claimed.lowercase() }
 *   effect: correlation dropped: a valid but unrelated UUID is published as this occurrence's work id
 * M13 [A4] RED exit 1
 *   selector: append(occurrenceId ?: "missing")
 *   effect: corrupt occurrence emitted as an empty string instead of the missing sentinel
 * M14 [A4] RED exit 1
 *   selector: NOTIFY("notify"),
 *   effect: a stage wire value renamed, silently breaking every downstream log consumer
 * M15 [A4] RED exit 1
 *   selector: override fun record(record: LogRecord) = Unit
 *   effect: the silent diagnostic port throws, breaking any caller that defaults to it
 */

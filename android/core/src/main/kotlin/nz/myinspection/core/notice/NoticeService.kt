package nz.myinspection.core.notice

import nz.myinspection.core.compliance.ComplianceEngine
import nz.myinspection.core.compliance.ComplianceReasonKey
import nz.myinspection.core.compliance.ExistingScheduledEntry
import nz.myinspection.core.compliance.ScheduleRequest
import nz.myinspection.core.compliance.ScheduleValidation
import nz.myinspection.core.db.ClockMs
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.Notice as NoticeRow
import nz.myinspection.core.db.SystemClockMs
import nz.myinspection.core.db.Uuid7Generator
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.Locale

data class NoticeComposeRequest(
    val inspectionId: String,
    val recipientName: String,
    val senderName: String,
)

enum class NoticeDeliveryMethod {
    SMS,
    EMAIL,
    LETTER,
}

data class NoticeRecord(
    val id: String,
    val inspectionId: String,
    val fullText: String,
    val generatedAt: Instant,
    val scheduledAt: Instant,
    val deliveryMethod: NoticeDeliveryMethod?,
    val deliveredAt: Instant?,
    val leadHours: Long,
    val validationSnapshot: String,
)

data class NoticeCopy(
    val fullText: String,
    /** Safe for an ambient/lock-screen confirmation; the actual clipboard still receives [fullText]. */
    val lockScreenText: String = "Inspection notice ready to copy",
    /** Android clipboard adapters use this to suppress sensitive preview content. */
    val isSensitive: Boolean = true,
)

sealed interface NoticeGeneration {
    data class Created(val notice: NoticeRecord) : NoticeGeneration

    data class Blocked(val reasonKeys: List<ComplianceReasonKey>) : NoticeGeneration
}

sealed interface NoticeDeliveryResult {
    data class Recorded(
        val notice: NoticeRecord,
        val isCompliant: Boolean,
        val reasonKeys: List<ComplianceReasonKey>,
    ) : NoticeDeliveryResult

    data class AlreadyRecorded(val notice: NoticeRecord) : NoticeDeliveryResult

    data class Rejected(val reason: NoticeDeliveryTimeReason) : NoticeDeliveryResult
}

enum class NoticeDeliveryTimeReason {
    BEFORE_GENERATION,
    FUTURE,
}

/**
 * Generates and records inspection notices. The app only copies the returned text; it has no sending operation.
 */
class NoticeService(
    private val database: MyInspectionDatabase,
    private val compliance: ComplianceEngine,
    private val uuid: Uuid7Generator = Uuid7Generator(),
    private val clock: ClockMs = SystemClockMs,
    private val zone: ZoneId = ZoneId.of("Pacific/Auckland"),
) {
    fun generate(request: NoticeComposeRequest): NoticeGeneration = database.transactionWithResult {
        require(request.recipientName.isNotBlank()) { "recipientName must not be blank" }
        require(request.senderName.isNotBlank()) { "senderName must not be blank" }

        val context = loadContext(request.inspectionId, activeOnly = true)
        val generatedAtMs = clock.nowMs()
        val validation = compliance.validateSchedule(
            context.scheduleRequest(Instant.ofEpochMilli(generatedAtMs), context.scheduledAt),
        )
        val reasonKeys = validation.reasonKeys()
        if (validation is ScheduleValidation.Blocked) {
            return@transactionWithResult NoticeGeneration.Blocked(reasonKeys)
        }

        val fullText = renderNotice(
            recipientName = request.recipientName,
            senderName = request.senderName,
            address = context.address,
            inspectionType = context.inspectionType,
            scheduledAt = context.scheduledAt,
        )
        val id = uuid.next()
        database.noticeQueries.insert(
            id = id,
            inspection_id = request.inspectionId,
            full_text = fullText,
            generated_at = generatedAtMs,
            scheduled_at = context.scheduledAt.toEpochMilli(),
            sent_via = null,
            sent_at = null,
            lead_hours = Duration.between(Instant.ofEpochMilli(generatedAtMs), context.scheduledAt).toHours(),
            validation_snapshot = validation.snapshot(),
            updated_at = generatedAtMs,
        )
        NoticeGeneration.Created(database.noticeQueries.selectById(id).executeAsOne().toRecord())
    }

    fun copy(noticeId: String): NoticeCopy? = database.noticeQueries.selectById(noticeId).executeAsOneOrNull()
        ?.takeIf { it.deleted_at == null }
        ?.let { NoticeCopy(it.full_text) }

    fun recordDelivery(
        noticeId: String,
        method: NoticeDeliveryMethod,
        deliveredAt: Instant,
    ): NoticeDeliveryResult = database.transactionWithResult {
        val existing = checkNotNull(database.noticeQueries.selectById(noticeId).executeAsOneOrNull()) {
            "no such notice: $noticeId"
        }
        check(existing.deleted_at == null) { "notice is deleted: $noticeId" }
        val recordedAt = Instant.ofEpochMilli(clock.nowMs())
        val generatedAt = Instant.ofEpochMilli(existing.generated_at)
        if (deliveredAt < generatedAt) {
            return@transactionWithResult NoticeDeliveryResult.Rejected(NoticeDeliveryTimeReason.BEFORE_GENERATION)
        }
        if (deliveredAt > recordedAt) {
            return@transactionWithResult NoticeDeliveryResult.Rejected(NoticeDeliveryTimeReason.FUTURE)
        }

        val scheduledAt = Instant.ofEpochMilli(existing.scheduled_at)
        val context = loadContext(existing.inspection_id, activeOnly = false, scheduledAt = scheduledAt)
        val validation = compliance.validateSchedule(context.scheduleRequest(deliveredAt, scheduledAt))
        val reasonKeys = validation.reasonKeys()
        val updated = database.noticeQueries.recordDelivery(
            sent_via = method.name,
            sent_at = deliveredAt.toEpochMilli(),
            lead_hours = Duration.between(deliveredAt, scheduledAt).toHours(),
            validation_snapshot = validation.snapshot(),
            updated_at = recordedAt.toEpochMilli(),
            id = noticeId,
        ).value
        if (updated != 1L) {
            return@transactionWithResult NoticeDeliveryResult.AlreadyRecorded(
                database.noticeQueries.selectById(noticeId).executeAsOne().toRecord(),
            )
        }
        NoticeDeliveryResult.Recorded(
            notice = database.noticeQueries.selectById(noticeId).executeAsOne().toRecord(),
            isCompliant = validation is ScheduleValidation.Pass,
            reasonKeys = reasonKeys,
        )
    }

    private fun loadContext(
        inspectionId: String,
        activeOnly: Boolean,
        scheduledAt: Instant? = null,
    ): NoticeContext {
        val inspection = if (activeOnly) {
            database.inspectionQueries.selectActive().executeAsList().singleOrNull { it.id == inspectionId }
        } else {
            database.inspectionQueries.selectById(inspectionId).executeAsOneOrNull()
        }
        checkNotNull(inspection) { "no such active inspection: $inspectionId" }
        val property = if (activeOnly) {
            database.propertyQueries.selectActiveById(inspection.property_id).executeAsOneOrNull()
        } else {
            database.propertyQueries.selectAnyById(inspection.property_id).executeAsOneOrNull()
        }
        checkNotNull(property) { "no such property: ${inspection.property_id}" }

        val history = database.inspectionQueries.selectActive().executeAsList().map {
            ExistingScheduledEntry(
                entryId = it.id,
                propertyId = it.property_id,
                entryPurpose = ENTRY_PURPOSE,
                inspectionType = it.type,
                scheduledAt = Instant.ofEpochMilli(it.scheduled_at),
            )
        }
        return NoticeContext(
            inspectionId = inspection.id,
            propertyId = property.id,
            address = property.address,
            inspectionType = inspection.type,
            isBoardingHouse = property.is_boarding_house == 1L,
            scheduledAt = scheduledAt ?: Instant.ofEpochMilli(inspection.scheduled_at),
            history = history,
        )
    }

    private fun renderNotice(
        recipientName: String,
        senderName: String,
        address: String,
        inspectionType: String,
        scheduledAt: Instant,
    ): String {
        val scheduled = DATE_TIME.format(scheduledAt.atZone(zone))
        val type = inspectionType.lowercase().replaceFirstChar { it.titlecase(Locale.ENGLISH) }
        return """
            Kia ora $recipientName,

            This notice is intended to meet the minimum 48-hour notice period for an inspection under the Residential Tenancies Act 1986.
            Property: $address
            Inspection: $type
            Scheduled: $scheduled
            MyInspection prepared this notice but did not send it.

            Regards,
            $senderName

            您好，$recipientName：

            此通知用于满足《1986 年住宅租赁法》规定的巡检至少提前 48 小时通知期。
            物业：$address
            巡检类型：$type
            预定时间：$scheduled
            MyInspection 仅生成此通知，并未发送。

            $senderName
        """.trimIndent()
    }

    private data class NoticeContext(
        val inspectionId: String,
        val propertyId: String,
        val address: String,
        val inspectionType: String,
        val isBoardingHouse: Boolean,
        val scheduledAt: Instant,
        val history: List<ExistingScheduledEntry>,
    ) {
        fun scheduleRequest(noticeGivenAt: Instant, scheduledAt: Instant) = ScheduleRequest(
            propertyId = propertyId,
            entryPurpose = ENTRY_PURPOSE,
            inspectionType = inspectionType,
            isBoardingHouse = isBoardingHouse,
            scheduledAt = scheduledAt,
            noticeGivenAt = noticeGivenAt,
            tenantConsented = false,
            existingEntries = history,
            currentEntryId = inspectionId,
        )
    }

    private companion object {
        const val ENTRY_PURPOSE = "inspection"
        val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm z", Locale.ENGLISH)
    }
}

private fun ScheduleValidation.reasonKeys(): List<ComplianceReasonKey> = Collections.unmodifiableList(
    when (this) {
        ScheduleValidation.Pass -> emptyList()
        is ScheduleValidation.Blocked -> reasons.map { it.key }
    },
)

private fun ScheduleValidation.snapshot(): String = when (this) {
    ScheduleValidation.Pass -> "{\"status\":\"PASS\",\"reasons\":[]}"
    is ScheduleValidation.Blocked -> reasons.joinToString(
        prefix = "{\"status\":\"BLOCKED\",\"reasons\":[\"",
        postfix = "\"]}",
        separator = "\",\"",
    ) { it.key.name }
}

private fun NoticeRow.toRecord() = NoticeRecord(
    id = id,
    inspectionId = inspection_id,
    fullText = full_text,
    generatedAt = Instant.ofEpochMilli(generated_at),
    scheduledAt = Instant.ofEpochMilli(scheduled_at),
    deliveryMethod = sent_via?.let(NoticeDeliveryMethod::valueOf),
    deliveredAt = sent_at?.let(Instant::ofEpochMilli),
    leadHours = lead_hours,
    validationSnapshot = validation_snapshot,
)

package nz.myinspection.core.notice

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import nz.myinspection.core.compliance.ComplianceConfigLoader
import nz.myinspection.core.compliance.ComplianceEngine
import nz.myinspection.core.compliance.ComplianceReasonKey
import nz.myinspection.core.compliance.ComplianceTestFixtures
import nz.myinspection.core.db.ClockMs
import nz.myinspection.core.db.DbTestFixtures
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.Uuid7Generator
import nz.myinspection.core.db.Uuid7RandomSource
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoticeServiceTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: MyInspectionDatabase
    private lateinit var uuid: Uuid7Generator
    private lateinit var inspectionId: String
    private var clockMs = 0L

    private val zone = ZoneId.of("Pacific/Auckland")
    private val scheduledAt = ZonedDateTime.of(2026, 8, 21, 10, 0, 0, 0, zone).toInstant()

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MyInspectionDatabase.Schema.create(driver)
        db = MyInspectionDatabase(driver)
        clockMs = scheduledAt.minus(Duration.ofHours(72)).toEpochMilli()
        uuid = Uuid7Generator(ClockMs { clockMs }, Uuid7RandomSource { 0L })

        val propertyId = DbTestFixtures.insertProperty(db, uuid, clockMs)
        val templateId = DbTestFixtures.insertTemplateVersion(db, uuid, now = clockMs)
        inspectionId = uuid.next()
        db.inspectionQueries.insert(
            id = inspectionId,
            type = "ROUTINE",
            property_id = propertyId,
            tenancy_id = null,
            template_version_id = templateId,
            scheduled_at = scheduledAt.toEpochMilli(),
            previous_inspection_id = null,
            baseline_inspection_id = null,
            status = "DRAFT",
            finalized_at = null,
            data_hash = null,
            created_at = clockMs,
            updated_at = clockMs,
        )
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `generation blocked by compliance writes no notice`() {
        // Break caught: persisting before the compliance verdict, or ignoring NOTICE_TOO_SHORT.
        clockMs = scheduledAt.minus(Duration.ofHours(40)).toEpochMilli()

        val result = service().generate(request())

        val blocked = assertIs<NoticeGeneration.Blocked>(result)
        assertEquals(listOf(ComplianceReasonKey.NOTICE_TOO_SHORT), blocked.reasonKeys)
        assertTrue(db.noticeQueries.selectByInspection(inspectionId).executeAsList().isEmpty())
    }

    @Test
    fun `blocked reasons expose bilingual corrective copy`() {
        val text = noticeReasonText(ComplianceReasonKey.NOTICE_TOO_SHORT)

        assertEquals(
            "The required notice period has not been met. Choose a later inspection time.",
            text.english,
        )
        assertEquals("尚未满足规定的通知期限。请选择更晚的巡检时间。", text.chinese)
        assertTrue(ComplianceReasonKey.entries.all { reason ->
            noticeReasonText(reason).let { it.english.isNotBlank() && it.chinese.isNotBlank() }
        })
    }

    @Test
    fun `passing generation persists the rendered bilingual audit snapshot`() {
        // Break caught: storing a template reference/recomputed value instead of the rendered historical snapshot.
        val created = createNotice()
        val row = db.noticeQueries.selectById(created.notice.id).executeAsOne()

        assertEquals(inspectionId, row.inspection_id)
        assertEquals(clockMs, row.generated_at)
        assertEquals(scheduledAt.toEpochMilli(), row.scheduled_at)
        assertEquals(72L, row.lead_hours)
        assertEquals("{\"status\":\"PASS\",\"reasons\":[]}", row.validation_snapshot)
        assertNull(row.sent_via)
        assertNull(row.sent_at)
        assertEquals(row.full_text, created.notice.fullText)
        assertTrue(row.full_text.contains("Property: 12 Test St"))
        assertTrue(row.full_text.contains("Scheduled: 2026-08-21 10:00 NZST"))
        assertTrue(row.full_text.contains("minimum 48-hour notice period"))
        assertTrue(row.full_text.contains("物业：12 Test St"))
        assertTrue(row.full_text.contains("至少提前 48 小时"))
        assertTrue(row.full_text.endsWith("Landlord A"))
    }

    @Test
    fun `copy returns the full text but never records delivery or leaks the address in lock screen copy`() {
        // Break caught: treating clipboard success as sent, or exposing the address in an ambient summary.
        val created = createNotice()

        val copy = service().copy(created.notice.id)
        val row = db.noticeQueries.selectById(created.notice.id).executeAsOne()

        assertEquals(created.notice.fullText, copy?.fullText)
        assertEquals("Inspection notice ready to copy", copy?.lockScreenText)
        assertFalse(copy!!.lockScreenText.contains("12 Test St"))
        assertNull(row.sent_via)
        assertNull(row.sent_at)
        assertEquals("{\"status\":\"PASS\",\"reasons\":[]}", row.validation_snapshot)
    }

    @Test
    fun `late delivery is recorded honestly as blocked and the first record is locked`() {
        // Break caught: silently preserving the generation-time pass or allowing a later overwrite.
        val created = createNotice()
        val sentAt = scheduledAt.minus(Duration.ofHours(40))
        clockMs = sentAt.plusSeconds(10).toEpochMilli()

        val first = assertIs<NoticeDeliveryResult.Recorded>(
            service().recordDelivery(created.notice.id, NoticeDeliveryMethod.EMAIL, sentAt),
        )
        assertEquals(listOf(ComplianceReasonKey.NOTICE_TOO_SHORT), first.reasonKeys)
        assertFalse(first.isCompliant)

        val firstRow = db.noticeQueries.selectById(created.notice.id).executeAsOne()
        assertEquals("EMAIL", firstRow.sent_via)
        assertEquals(sentAt.toEpochMilli(), firstRow.sent_at)
        assertEquals(40L, firstRow.lead_hours)
        assertEquals(
            "{\"status\":\"BLOCKED\",\"reasons\":[\"NOTICE_TOO_SHORT\"]}",
            firstRow.validation_snapshot,
        )

        val second = service().recordDelivery(
            created.notice.id,
            NoticeDeliveryMethod.SMS,
            scheduledAt.minus(Duration.ofHours(60)),
        )
        assertIs<NoticeDeliveryResult.AlreadyRecorded>(second)
        assertEquals(firstRow, db.noticeQueries.selectById(created.notice.id).executeAsOne())
    }

    @Test
    fun `delivery at exactly 48 hours remains compliant and records the selected method`() {
        // Break caught: changing the inclusive 48-hour boundary to an exclusive comparison in this workflow.
        val created = createNotice()
        val sentAt = scheduledAt.minus(Duration.ofHours(48))
        clockMs = sentAt.plusSeconds(10).toEpochMilli()

        val result = assertIs<NoticeDeliveryResult.Recorded>(
            service().recordDelivery(created.notice.id, NoticeDeliveryMethod.LETTER, sentAt),
        )
        val row = db.noticeQueries.selectById(created.notice.id).executeAsOne()

        assertTrue(result.isCompliant)
        assertEquals(emptyList(), result.reasonKeys)
        assertEquals("LETTER", row.sent_via)
        assertEquals(48L, row.lead_hours)
        assertEquals("{\"status\":\"PASS\",\"reasons\":[]}", row.validation_snapshot)
    }

    @Test
    fun `delivery outside generation and current time is rejected without writing or locking`() {
        // Break caught: accepting a forged pre-generation backdate or a future delivery timestamp.
        val created = createNotice()
        clockMs = scheduledAt.minus(Duration.ofHours(40)).toEpochMilli()

        val beforeGeneration = assertIs<NoticeDeliveryResult.Rejected>(
            service().recordDelivery(
                created.notice.id,
                NoticeDeliveryMethod.EMAIL,
                created.notice.generatedAt.minusSeconds(1),
            ),
        )
        assertEquals(NoticeDeliveryTimeReason.BEFORE_GENERATION, beforeGeneration.reason)
        assertNull(db.noticeQueries.selectById(created.notice.id).executeAsOne().sent_at)

        val future = assertIs<NoticeDeliveryResult.Rejected>(
            service().recordDelivery(
                created.notice.id,
                NoticeDeliveryMethod.EMAIL,
                Instant.ofEpochMilli(clockMs).plusSeconds(1),
            ),
        )
        assertEquals(NoticeDeliveryTimeReason.FUTURE, future.reason)
        assertNull(db.noticeQueries.selectById(created.notice.id).executeAsOne().sent_at)

        assertIs<NoticeDeliveryResult.Recorded>(
            service().recordDelivery(created.notice.id, NoticeDeliveryMethod.EMAIL, Instant.ofEpochMilli(clockMs)),
        )
    }

    private fun service(): NoticeService = NoticeService(
        database = db,
        compliance = ComplianceEngine(
            ComplianceConfigLoader.load(ComplianceTestFixtures.configJson().encodeToByteArray()).config,
        ),
        uuid = uuid,
        clock = ClockMs { clockMs },
    )

    private fun request() = NoticeComposeRequest(
        inspectionId = inspectionId,
        recipientName = "Tenant T",
        senderName = "Landlord A",
    )

    private fun createNotice(): NoticeGeneration.Created =
        assertIs<NoticeGeneration.Created>(service().generate(request()))
}

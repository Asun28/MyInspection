package nz.myinspection.core.compliance

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ComplianceEngineTest {
    private val zone = ZoneId.of("Pacific/Auckland")

    @Test
    fun `notice and ordinary-rental visit boundaries are inclusive and each outside edge blocks`() {
        val engine = engine()
        val atOpening = atNz("2026-08-10T08:00")
        val atClosing = atNz("2026-08-10T19:00")

        assertIs<ScheduleValidation.Pass>(
            engine.validateSchedule(request(atOpening, atOpening.minus(Duration.ofHours(48)))),
        )
        assertIs<ScheduleValidation.Pass>(
            engine.validateSchedule(request(atClosing, atClosing.minus(Duration.ofDays(14)))),
        )
        assertBlocked(
            engine.validateSchedule(request(atOpening, atOpening.minus(Duration.ofHours(48)).plusMillis(1))),
            ComplianceReasonKey.NOTICE_TOO_SHORT,
        )
        assertBlocked(
            engine.validateSchedule(request(atOpening, atOpening.minus(Duration.ofDays(14)).minusMillis(1))),
            ComplianceReasonKey.NOTICE_TOO_EARLY,
        )
        assertBlocked(
            engine.validateSchedule(request(atNz("2026-08-10T07:59:59"), atNz("2026-08-08T07:59:59"))),
            ComplianceReasonKey.OUTSIDE_VISIT_WINDOW,
        )
        assertBlocked(
            engine.validateSchedule(request(atNz("2026-08-10T19:00:00.001"), atNz("2026-08-08T19:00:00.001"))),
            ComplianceReasonKey.OUTSIDE_VISIT_WINDOW,
        )
    }

    @Test
    fun `boarding-house closing time is 18h while the signed product notice floor remains 48h`() {
        val engine = engine()
        val closing = atNz("2026-08-10T18:00")

        assertIs<ScheduleValidation.Pass>(
            engine.validateSchedule(request(closing, closing.minus(Duration.ofHours(48)), boardingHouse = true)),
        )
        assertBlocked(
            engine.validateSchedule(
                request(atNz("2026-08-10T18:00:00.001"), atNz("2026-08-08T18:00:00.001"), boardingHouse = true),
            ),
            ComplianceReasonKey.OUTSIDE_VISIT_WINDOW,
        )
        assertBlocked(
            engine.validateSchedule(request(closing, closing.minus(Duration.ofHours(24)), boardingHouse = true)),
            ComplianceReasonKey.NOTICE_TOO_SHORT,
        )
    }

    @Test
    fun `same-property routine inside four civil weeks blocks even with consent, while exact boundary passes`() {
        val engine = engine()
        val previous = ExistingScheduledEntry(
            propertyId = "property-a",
            entryPurpose = "inspection",
            inspectionType = "ROUTINE",
            scheduledAt = atNz("2026-08-01T10:00"),
        )

        assertBlocked(
            engine.validateSchedule(
                request(
                    scheduledAt = atNz("2026-08-15T10:00"),
                    noticeGivenAt = atNz("2026-08-13T10:00"),
                    existingEntries = listOf(previous),
                    tenantConsented = true,
                ),
            ),
            ComplianceReasonKey.FREQUENCY_LIMIT,
        )
        assertIs<ScheduleValidation.Pass>(
            engine.validateSchedule(
                request(
                    scheduledAt = atNz("2026-08-29T10:00"),
                    noticeGivenAt = atNz("2026-08-27T10:00"),
                    existingEntries = listOf(previous),
                ),
            ),
        )
    }

    @Test
    fun `frequency considers only same-property non-exempt inspection entries`() {
        val scheduled = atNz("2026-08-15T10:00")
        val notice = scheduled.minus(Duration.ofHours(48))
        val near = atNz("2026-08-01T10:00")
        val irrelevant = listOf(
            ExistingScheduledEntry("property-b", "inspection", "ROUTINE", near),
            ExistingScheduledEntry("property-a", "inspection", "INGOING", near),
            ExistingScheduledEntry("property-a", "inspection", "EXIT", near),
            ExistingScheduledEntry("property-a", "inspection", "ANNUAL", near),
        )

        assertIs<ScheduleValidation.Pass>(
            engine().validateSchedule(request(scheduled, notice, existingEntries = irrelevant)),
        )
        listOf("INGOING", "EXIT", "ANNUAL").forEach { type ->
            assertIs<ScheduleValidation.Pass>(
                engine().validateSchedule(request(scheduled, notice, inspectionType = type, existingEntries = listOf(
                    ExistingScheduledEntry("property-a", "inspection", "ROUTINE", near),
                ))),
            )
        }
    }

    @Test
    fun `notice uses elapsed time across both NZ DST transitions while frequency uses civil days`() {
        val engine = engine()
        val springSchedule = atNz("2026-09-28T08:00")
        val springWallClock48h = atNz("2026-09-26T08:00")
        assertEquals(Duration.ofHours(47), Duration.between(springWallClock48h, springSchedule))
        assertBlocked(
            engine.validateSchedule(request(springSchedule, springWallClock48h)),
            ComplianceReasonKey.NOTICE_TOO_SHORT,
        )
        assertIs<ScheduleValidation.Pass>(
            engine.validateSchedule(request(springSchedule, springSchedule.minus(Duration.ofHours(48)))),
        )

        val autumnSchedule = atNz("2026-04-06T08:00")
        val autumnWallClock48h = atNz("2026-04-04T08:00")
        assertEquals(Duration.ofHours(49), Duration.between(autumnWallClock48h, autumnSchedule))
        assertIs<ScheduleValidation.Pass>(
            engine.validateSchedule(request(autumnSchedule, autumnWallClock48h)),
        )

        val previous = ExistingScheduledEntry(
            "property-a", "inspection", "ROUTINE", atNz("2026-09-01T10:00"),
        )
        assertEquals(
            Duration.ofHours(671),
            Duration.between(previous.scheduledAt, atNz("2026-09-29T10:00")),
        )
        assertIs<ScheduleValidation.Pass>(
            engine.validateSchedule(
                request(
                    atNz("2026-09-29T10:00"),
                    atNz("2026-09-27T10:00"),
                    existingEntries = listOf(previous),
                ),
            ),
        )
    }

    @Test
    fun `entry-purpose rules are data driven and unknown purposes fail closed`() {
        val config = ComplianceConfigLoader.load(configJson(alternatePurpose = true).encodeToByteArray()).config
        val engine = ComplianceEngine(config)
        val scheduled = atNz("2026-08-10T10:00")

        assertIs<ScheduleValidation.Pass>(
            engine.validateSchedule(
                request(scheduled, scheduled.minus(Duration.ofHours(72)), entryPurpose = "fixture-purpose"),
            ),
        )
        assertBlocked(
            engine.validateSchedule(
                request(scheduled, scheduled.minus(Duration.ofHours(48)), entryPurpose = "not-configured"),
            ),
            ComplianceReasonKey.UNKNOWN_ENTRY_PURPOSE,
        )
        assertBlocked(
            engine.validateSchedule(
                request(scheduled, scheduled.minus(Duration.ofHours(48)), inspectionType = "ROUTIEN"),
            ),
            ComplianceReasonKey.UNKNOWN_INSPECTION_TYPE,
        )
    }

    @Test
    fun `blank property and malformed same-purpose history fail closed with stable reasons`() {
        val scheduled = atNz("2026-08-15T10:00")
        val notice = scheduled.minus(Duration.ofHours(48))

        assertBlocked(
            engine().validateSchedule(request(scheduled, notice, propertyId = "")),
            ComplianceReasonKey.INVALID_PROPERTY_ID,
        )
        assertBlocked(
            engine().validateSchedule(
                request(
                    scheduled,
                    notice,
                    existingEntries = listOf(
                        ExistingScheduledEntry("property-a", "inspection", "ROUTIEN", atNz("2026-08-01T10:00")),
                    ),
                ),
            ),
            ComplianceReasonKey.INVALID_HISTORY_ENTRY,
        )
    }

    @Test
    fun `history belonging to another entry purpose cannot trigger the inspection frequency limit`() {
        val scheduled = atNz("2026-08-15T10:00")

        assertIs<ScheduleValidation.Pass>(
            engine().validateSchedule(
                request(
                    scheduledAt = scheduled,
                    noticeGivenAt = scheduled.minus(Duration.ofHours(48)),
                    existingEntries = listOf(
                        ExistingScheduledEntry(
                            "property-a",
                            "fixture-purpose",
                            "ROUTINE",
                            atNz("2026-08-01T10:00"),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `repository rule file loads and drives the signed inspection behavior`() {
        val bytes = Files.readAllBytes(findRepositoryFile("configs/compliance/nz-rules-v1.json"))
        val loaded = ComplianceConfigLoader.load(bytes)
        val engine = ComplianceEngine(loaded.config)
        val scheduled = atNz("2026-08-15T10:00")

        assertEquals(ComplianceConfigSource.BUILT_IN, loaded.source)
        assertIs<ScheduleValidation.Pass>(
            engine.validateSchedule(request(scheduled, scheduled.minus(Duration.ofHours(48)))),
        )
        assertBlocked(
            engine.validateSchedule(request(scheduled, scheduled.minus(Duration.ofHours(47)))),
            ComplianceReasonKey.NOTICE_TOO_SHORT,
        )
    }

    private fun engine(): ComplianceEngine =
        ComplianceEngine(ComplianceConfigLoader.load(configJson().encodeToByteArray()).config)

    private fun request(
        scheduledAt: Instant,
        noticeGivenAt: Instant,
        propertyId: String = "property-a",
        entryPurpose: String = "inspection",
        inspectionType: String = "ROUTINE",
        boardingHouse: Boolean = false,
        tenantConsented: Boolean = false,
        existingEntries: List<ExistingScheduledEntry> = emptyList(),
    ) = ScheduleRequest(
        propertyId = propertyId,
        entryPurpose = entryPurpose,
        inspectionType = inspectionType,
        isBoardingHouse = boardingHouse,
        scheduledAt = scheduledAt,
        noticeGivenAt = noticeGivenAt,
        tenantConsented = tenantConsented,
        existingEntries = existingEntries,
    )

    private fun assertBlocked(result: ScheduleValidation, vararg keys: ComplianceReasonKey) {
        val blocked = assertIs<ScheduleValidation.Blocked>(result)
        assertEquals(keys.toList(), blocked.reasons.map { it.key })
    }

    private fun atNz(local: String): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()

    private fun configJson(alternatePurpose: Boolean = false): String {
        val alternatePurposeRule = if (alternatePurpose) {
            """
            ,
            "fixture-purpose": {
              "noticeMinHours": 72,
              "noticeMaxDays": 14,
              "visitWindow": {"start": "08:00", "end": "19:00", "boardingHouseEnd": "18:00"},
              "frequencyLimit": {"days": 1, "exemptTypes": ["ROUTINE", "INGOING", "EXIT", "ANNUAL"]}
            }
            """.trimIndent()
        } else {
            ""
        }
        return """
            {
              "schemaVersion": 1,
              "effectiveDate": "2025-12-01",
              "sourceRefs": ["https://www.legislation.govt.nz/act/public/1986/120/en/latest/sections/DLM95504/"],
              "timezone": "Pacific/Auckland",
              "rules": {
                "inspection": {
                  "noticeMinHours": 48,
                  "noticeMaxDays": 14,
                  "visitWindow": {"start": "08:00", "end": "19:00", "boardingHouseEnd": "18:00"},
                  "frequencyLimit": {"days": 28, "exemptTypes": ["INGOING", "EXIT", "ANNUAL"]}
                }
                $alternatePurposeRule
              }
            }
        """.trimIndent()
    }

    private fun findRepositoryFile(relative: String): Path {
        var cursor: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        while (cursor != null) {
            val candidate = cursor.resolve(relative)
            if (Files.isRegularFile(candidate)) return candidate
            cursor = cursor.parent
        }
        error("repository file not found: $relative")
    }
}

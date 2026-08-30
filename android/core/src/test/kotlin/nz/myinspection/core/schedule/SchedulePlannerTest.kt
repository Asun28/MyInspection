package nz.myinspection.core.schedule

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SchedulePlannerTest {
    private val zone = ZoneId.of("Pacific/Auckland")
    private val planner = SchedulePlanner(zone)

    @Test
    fun `routine cadence adds thirteen local weeks across spring DST`() {
        val finalized = atNz("2026-08-01T10:30")

        val advice = assertIs<ScheduleAdvice.Due>(
            planner.nextDue(
                propertyId = "property-a",
                inspectionType = InspectionScheduleType.ROUTINE,
                history = listOf(finalized("property-a", InspectionScheduleType.ROUTINE, finalized)),
            ),
        )

        assertEquals(atNz("2026-10-31T10:30"), advice.dueAt)
        assertEquals(finalized, advice.previousFinalizedAt)
        assertEquals(Duration.ofHours(2_183), Duration.between(finalized, advice.dueAt))
    }

    @Test
    fun `annual cadence adds twelve local calendar months at month end`() {
        val leapDay = atNz("2024-02-29T09:15")

        val advice = assertIs<ScheduleAdvice.Due>(
            planner.nextDue(
                propertyId = "property-a",
                inspectionType = InspectionScheduleType.ANNUAL,
                history = listOf(finalized("property-a", InspectionScheduleType.ANNUAL, leapDay)),
            ),
        )

        assertEquals(atNz("2025-02-28T09:15"), advice.dueAt)
    }

    @Test
    fun `ingoing and exit never recur even when finalized history exists`() {
        InspectionScheduleType.entries
            .filter { it == InspectionScheduleType.INGOING || it == InspectionScheduleType.EXIT }
            .forEach { type ->
                assertIs<ScheduleAdvice.NoRecurrence>(
                    planner.nextDue(
                        propertyId = "property-a",
                        inspectionType = type,
                        history = listOf(finalized("property-a", type, atNz("2026-08-01T10:00"))),
                    ),
                    "Expected $type to remain non-recurring",
                )
            }
    }

    @Test
    fun `latest finalized row is selected only within the requested property and type`() {
        val latestMatching = atNz("2026-06-15T11:00")
        val advice = assertIs<ScheduleAdvice.Due>(
            planner.nextDue(
                propertyId = "property-a",
                inspectionType = InspectionScheduleType.ROUTINE,
                history = listOf(
                    finalized("property-a", InspectionScheduleType.ROUTINE, atNz("2026-05-01T10:00")),
                    finalized("property-a", InspectionScheduleType.ROUTINE, latestMatching),
                    finalized("property-a", InspectionScheduleType.ANNUAL, atNz("2026-07-01T10:00")),
                    finalized("property-b", InspectionScheduleType.ROUTINE, atNz("2026-08-01T10:00")),
                ),
            ),
        )

        assertEquals(latestMatching, advice.previousFinalizedAt)
        assertEquals(atNz("2026-09-14T11:00"), advice.dueAt)
    }

    @Test
    fun `missing same-type history requests a first inspection`() {
        assertIs<ScheduleAdvice.FirstInspection>(
            planner.nextDue(
                propertyId = "property-a",
                inspectionType = InspectionScheduleType.ROUTINE,
                history = listOf(
                    finalized("property-a", InspectionScheduleType.ANNUAL, atNz("2026-08-01T10:00")),
                    finalized("property-b", InspectionScheduleType.ROUTINE, atNz("2026-08-01T10:00")),
                ),
            ),
        )
    }

    private fun finalized(
        propertyId: String,
        type: InspectionScheduleType,
        at: Instant,
    ) = FinalizedInspection(propertyId, type, at)

    private fun atNz(local: String): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()
}

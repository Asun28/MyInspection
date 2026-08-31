package nz.myinspection.app.feature.schedule

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import nz.myinspection.core.schedule.InspectionScheduleType

class ReminderContractsTest {
    private val route = ScheduleRoute("property-a", InspectionScheduleType.ROUTINE)
    private val dueAt = Instant.parse("2026-08-03T00:00:00.000000001Z")
    private val spec = WorkSpecFactory().create(route, dueAt)

    @Test
    fun `occurrence identity pins exact seconds and nanos golden vector`() {
        assertEquals(
            "c118fefec6ee20d89eafa5533048237237d39116af40aa85123fb1f70c404108",
            spec.occurrenceId,
        )
        assertEquals(spec.occurrenceId, reminderOccurrenceId(route, dueAt))
        assertEquals("schedule-reminder:${spec.occurrenceId}", spec.uniqueWorkName)
        assertEquals(route, spec.route)
        assertEquals(dueAt, spec.dueAt)
        assertEquals(64, spec.occurrenceId.length)
        assertTrue(spec.occurrenceId.matches(Regex("[0-9a-f]{64}")))
        assertTrue(route.propertyId !in spec.uniqueWorkName)
        assertFailsWith<IllegalArgumentException> {
            WorkSpecFactory().create(route.copy(propertyId = ""), dueAt)
        }
    }

    @Test
    fun `occurrence identity changes for every canonical input component`() {
        val variants = listOf(
            reminderOccurrenceId(route.copy(propertyId = "property-b"), dueAt),
            reminderOccurrenceId(route.copy(inspectionType = InspectionScheduleType.ANNUAL), dueAt),
            reminderOccurrenceId(route, dueAt.plusNanos(1)),
        )

        variants.forEach { variant -> assertNotEquals(spec.occurrenceId, variant) }
        assertEquals(variants.size, variants.toSet().size)
    }

    @Test
    fun `generation identifiers pin immutable work request vectors`() {
        assertEquals(
            "40fe7461-9be1-3ce7-8bdf-28b48b76359e",
            reminderGenerationId(spec.occurrenceId, 0).toString(),
        )
        assertEquals(
            "590ca815-2783-322a-acde-39ab31dafd39",
            reminderGenerationId(spec.occurrenceId, 1).toString(),
        )
        assertNotEquals(
            reminderGenerationId(spec.occurrenceId, 0),
            reminderGenerationId(spec.occurrenceId, 1),
        )
        assertFailsWith<IllegalArgumentException> {
            reminderGenerationId(spec.occurrenceId, -1)
        }
    }

    @Test
    fun `route intent carries collision safe private route identity`() {
        val intent = reminderRouteIntentSpec(route, dueAt)
        val expectedIdentity = NotificationIdentity(intent.notificationTag, intent.notificationId)

        assertEquals("myinspection://schedule/reminder/${spec.occurrenceId}", intent.data)
        assertEquals(spec.occurrenceId, intent.notificationTag)
        assertEquals(0, intent.notificationId)
        assertEquals(spec.occurrenceId.take(8).toLong(16).toInt(), intent.requestCode)
        assertEquals(route.propertyId, intent.propertyId)
        assertEquals(route.inspectionType.name, intent.inspectionType)
        assertTrue(intent.isExplicit)
        assertTrue(intent.isImmutable)
        assertEquals(expectedIdentity, reminderNotificationIdentity(intent))

        val other = reminderRouteIntentSpec(
            route.copy(inspectionType = InspectionScheduleType.ANNUAL),
            dueAt,
        )
        assertNotEquals(intent.data, other.data)
        assertNotEquals(intent.notificationTag, other.notificationTag)
    }

    @Test
    fun `notification copy has exact bilingual text for all schedule types`() {
        val expected = mapOf(
            InspectionScheduleType.ROUTINE to NotificationCopy(
                title = "Inspection reminder / 巡检提醒",
                body = "Routine inspection / 定期巡检 is due. Open MyInspection to review the property. / " +
                    "已到建议日期，请打开 MyInspection 查看物业。",
            ),
            InspectionScheduleType.ANNUAL to NotificationCopy(
                title = "Inspection reminder / 巡检提醒",
                body = "Annual home check / 年度住宅检查 is due. Open MyInspection to review the property. / " +
                    "已到建议日期，请打开 MyInspection 查看物业。",
            ),
            InspectionScheduleType.INGOING to NotificationCopy(
                title = "Inspection reminder / 巡检提醒",
                body = "Ingoing inspection / 入住巡检 is due. Open MyInspection to review the property. / " +
                    "已到建议日期，请打开 MyInspection 查看物业。",
            ),
            InspectionScheduleType.EXIT to NotificationCopy(
                title = "Inspection reminder / 巡检提醒",
                body = "Exit inspection / 退租巡检 is due. Open MyInspection to review the property. / " +
                    "已到建议日期，请打开 MyInspection 查看物业。",
            ),
        )

        assertEquals(InspectionScheduleType.values().toSet(), expected.keys)
        expected.forEach { (type, copy) ->
            assertEquals(copy, scheduleNotificationCopy(type))
            assertTrue("property-a" !in "${copy.title} ${copy.body}")
        }
    }

    @Test
    fun `delivery plan preserves immutable alert once intent and permission gate`() {
        val notify = assertIs<DeliveryPlan.Notify>(
            reminderDeliveryPlan(
                sdkInt = 32,
                permissionGranted = false,
                route = route,
                dueAt = dueAt,
            ),
        )

        assertEquals(true, notify.onlyAlertOnce)
        assertEquals(reminderRouteIntentSpec(route, dueAt), notify.intent)
        assertIs<DeliveryPlan.Retry>(
            reminderDeliveryPlan(
                sdkInt = 33,
                permissionGranted = false,
                route = route,
                dueAt = dueAt,
            ),
        )
    }
}

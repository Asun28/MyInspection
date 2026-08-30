package nz.myinspection.app.feature.schedule

import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import nz.myinspection.core.schedule.InspectionScheduleType
import nz.myinspection.core.schedule.ScheduleAdvice

/**
 * R4 mutation receipt (2026-08-31): each restored single-statement mutation exited nonzero.
 *
 * - Remove due-at or downgrade route identity to hashCode -> occurrence/collision assertions fail.
 * - Bypass durable claim/rollback, Settings resume, or synchronous permission receipt -> app contract fails.
 * - Nest the reminder action, relaunch a denied dialog, corrupt route extras, RETRY->DELIVER, or KEEP->REPLACE -> fails.
 * - Core receipt: ROUTINE 13->12 weeks failed two cadence assertions; ANNUAL 12->11 months failed month-end.
 */
class ScheduleFeatureTest {
    private val now = Instant.parse("2026-08-01T00:00:00Z")
    private val workSpecs = ReminderWorkSpecFactory(Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `work spec is stable unique and carries exact route with an offline initial delay`() {
        val route = ScheduleRoutePayload("property-a", InspectionScheduleType.ROUTINE)
        val dueAt = now.plus(Duration.ofDays(2))

        val first = workSpecs.create(route, dueAt)
        val repeated = workSpecs.create(route, dueAt)

        assertEquals(first, repeated)
        assertEquals(Duration.ofDays(2).toMillis(), first.initialDelayMillis)
        assertEquals(route, first.route)
        assertTrue(first.uniqueWorkName.startsWith("schedule-reminder:"))
        assertFalse(first.uniqueWorkName.contains(route.propertyId), "Unique work names must not expose property ids")
        assertNotEquals(first.uniqueWorkName, workSpecs.create(route, dueAt.plusSeconds(1)).uniqueWorkName)
        assertNotEquals(first.uniqueWorkName, workSpecs.create(route.copy(propertyId = "property-b"), dueAt).uniqueWorkName)
        assertEquals(0L, workSpecs.create(route, now.minusSeconds(1)).initialDelayMillis)
    }

    @Test
    fun `occurrence receipt remains idempotent after completion and can roll back a failed enqueue`() {
        val store = FakeOccurrenceStore()
        val occurrenceId = workSpecs.create(
            ScheduleRoutePayload("property-a", InspectionScheduleType.ROUTINE),
            now.plusSeconds(60),
        ).occurrenceId

        assertTrue(ReminderRegistrationGate(store).claim(occurrenceId))
        assertFalse(ReminderRegistrationGate(store).claim(occurrenceId), "A recreated gate must retain the receipt")
        ReminderRegistrationGate(store).rollback(occurrenceId)
        assertTrue(ReminderRegistrationGate(store).claim(occurrenceId), "A failed enqueue must remain retryable")
    }

    @Test
    fun `route intent identity is deterministic collision safe and privacy preserving`() {
        val dueAt = now.plusSeconds(60)
        val first = reminderRouteIntentSpec(ScheduleRoutePayload("Aa", InspectionScheduleType.ROUTINE), dueAt)
        val repeated = reminderRouteIntentSpec(ScheduleRoutePayload("Aa", InspectionScheduleType.ROUTINE), dueAt)
        val hashCollision = reminderRouteIntentSpec(ScheduleRoutePayload("BB", InspectionScheduleType.ROUTINE), dueAt)

        assertEquals(first, repeated)
        assertNotEquals(first.data, hashCollision.data)
        assertFalse(first.data.contains("Aa"))
        assertEquals("Aa", first.propertyId)
        assertEquals(InspectionScheduleType.ROUTINE.name, first.inspectionType)
    }

    @Test
    fun `permission policy asks only on api 33 and keeps denied recovery explicit`() {
        assertIs<ReminderPermissionAction.Schedule>(ReminderPermissionPolicy.next(32, NotificationPermissionState.UNKNOWN, true))
        assertIs<ReminderPermissionAction.Schedule>(ReminderPermissionPolicy.next(35, NotificationPermissionState.GRANTED))
        assertIs<ReminderPermissionAction.RequestPermission>(ReminderPermissionPolicy.next(33, NotificationPermissionState.UNKNOWN, false))
        assertIs<ReminderPermissionAction.ShowRationale>(ReminderPermissionPolicy.next(33, NotificationPermissionState.UNKNOWN, true))
        val denied = assertIs<ReminderPermissionAction.ExplainDenied>(
            ReminderPermissionPolicy.next(33, NotificationPermissionState.DENIED),
        )
        assertContains(denied.english, "Settings")
        assertContains(denied.chinese, "设置")
        assertEquals(ReminderDeliveryAction.RETRY, reminderDeliveryAction(33, permissionGranted = false))
        assertEquals(ReminderDeliveryAction.DELIVER, reminderDeliveryAction(32, permissionGranted = false))
    }

    @Test
    fun `local notification copy is bilingual and does not expose property identity`() {
        val copy = scheduleNotificationCopy(InspectionScheduleType.ROUTINE)
        val allText = "${copy.title}\n${copy.body}"

        assertContains(copy.title, "Inspection reminder")
        assertContains(copy.title, "巡检提醒")
        assertContains(copy.body, "Routine")
        assertContains(copy.body, "定期巡检")
        assertFalse(allText.contains("property-a"))
        assertFalse(allText.contains("address", ignoreCase = true))
    }

    @Test
    fun `presentation maps due first and non-recurring badges and filters by state or type`() {
        val items = listOf(
            item("due", InspectionScheduleType.ROUTINE, ScheduleAdvice.Due(now.minusSeconds(1), now.minusSeconds(2))),
            item("next", InspectionScheduleType.ANNUAL, ScheduleAdvice.Due(now.plusSeconds(60), now.minusSeconds(2))),
            item("first", InspectionScheduleType.ROUTINE, ScheduleAdvice.FirstInspection),
            item("ingoing", InspectionScheduleType.INGOING, ScheduleAdvice.NoRecurrence),
            item("one-off", InspectionScheduleType.EXIT, ScheduleAdvice.NoRecurrence),
        )

        val all = scheduleRows(items, now, ScheduleFilter.ALL)
        assertEquals(listOf(ScheduleBadge.DUE, ScheduleBadge.UPCOMING, ScheduleBadge.FIRST, ScheduleBadge.ONE_OFF, ScheduleBadge.ONE_OFF), all.map { it.badge })
        assertEquals(ScheduleRoutePayload("due", InspectionScheduleType.ROUTINE), all.first().route)
        assertEquals(listOf("due"), scheduleRows(items, now, ScheduleFilter.DUE).map { it.route.propertyId })
        assertEquals(listOf("next"), scheduleRows(items, now, ScheduleFilter.ANNUAL).map { it.route.propertyId })
        assertEquals(listOf("due", "first"), scheduleRows(items, now, ScheduleFilter.ROUTINE).map { it.route.propertyId })
        assertEquals(listOf("ingoing"), scheduleRows(items, now, ScheduleFilter.INGOING).map { it.route.propertyId })
        assertEquals(listOf("one-off"), scheduleRows(items, now, ScheduleFilter.EXIT).map { it.route.propertyId })
        assertTrue(scheduleRows(emptyList(), now, ScheduleFilter.ALL).isEmpty())
        assertEquals(ScheduleBadge.EMPTY, scheduleEmptyState(ScheduleFilter.ANNUAL).badge)
        assertContains(scheduleEmptyState(ScheduleFilter.ANNUAL).message, "Annual")
    }

    @Test
    fun `manifest scheduler and worker retain the production notification contract`() {
        val appRoot = locateAppRoot()
        val manifest = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(appRoot.resolve("src/main/AndroidManifest.xml").toFile())
        val permissions = manifest.getElementsByTagName("uses-permission")
        val names = (0 until permissions.length).map {
            permissions.item(it).attributes.getNamedItemNS(ANDROID_NS, "name").nodeValue
        }
        assertEquals(listOf("android.permission.POST_NOTIFICATIONS"), names)

        val feature = appRoot.resolve("src/main/kotlin/nz/myinspection/app/feature/schedule")
        val scheduler = Files.readAllBytes(feature.resolve("ScheduleReminderScheduler.kt")).decodeToString()
        val worker = Files.readAllBytes(feature.resolve("ScheduleReminderWorker.kt")).decodeToString()
        val screen = Files.readAllBytes(feature.resolve("ScheduleScreen.kt")).decodeToString()
        assertContains(scheduler, "enqueueUniqueWork")
        assertContains(scheduler, "ExistingWorkPolicy.KEEP")
        assertContains(scheduler, "ReminderRegistrationGate")
        assertContains(scheduler, "gate.rollback(spec.occurrenceId)")
        assertContains(scheduler, "setInitialDelay")
        assertContains(worker, "POST_NOTIFICATIONS")
        assertContains(worker, "NotificationManager")
        assertContains(worker, "ScheduleRoutePayload")
        assertContains(worker, "PendingIntent.FLAG_IMMUTABLE")
        assertContains(worker, "intent.data")
        assertContains(worker, "putExtra(EXTRA_PROPERTY_ID, intentSpec.propertyId)")
        assertContains(worker, "putExtra(EXTRA_INSPECTION_TYPE, intentSpec.inspectionType)")
        assertContains(worker, "Result.retry()")
        assertContains(screen, "ACTION_APPLICATION_DETAILS_SETTINGS")
        assertContains(screen, "is ReminderPermissionAction.ExplainDenied -> pendingRow = row")
        assertContains(screen, ".commit()")
        assertEquals(2, Regex("pendingRow\\?\\.schedule\\(context\\)").findAll(screen).count())
        assertFalse(screen.substringAfter("ListItem(").substringBefore("\n        )").contains("onReminder"))
    }

    private fun item(
        id: String,
        type: InspectionScheduleType,
        advice: ScheduleAdvice,
    ) = SchedulePropertyItem(id, "Property $id", type, advice)

    private class FakeOccurrenceStore : ReminderOccurrenceStore {
        private val receipts = mutableSetOf<String>()
        override fun claim(occurrenceId: String): Boolean = receipts.add(occurrenceId)
        override fun remove(occurrenceId: String) { receipts.remove(occurrenceId) }
    }

    private fun locateAppRoot(): Path = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        .first { Files.isRegularFile(it.resolve("src/main/AndroidManifest.xml")) }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}

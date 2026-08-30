package nz.myinspection.app.feature.schedule

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
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
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import nz.myinspection.core.schedule.InspectionScheduleType
import nz.myinspection.core.schedule.ScheduleAdvice

/** R4 receipt: restored mutations to cadence, occurrence identity, pending recovery/rollback,
 * saveable permission state, API-33 delivery, and KEEP idempotency each failed the focused suite. */
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
    fun `persistent coordinator recovers pending enqueue and reconciles async or sync failure`() {
        val store = FakeOccurrenceStore()
        val occurrenceId = workSpecs.create(ScheduleRoutePayload("property-a", InspectionScheduleType.ROUTINE), now).occurrenceId
        var firstCompletion: ((Boolean) -> Unit)? = null
        assertTrue(ReminderRegistrationCoordinator(store).register(occurrenceId) { firstCompletion = it })
        assertEquals(ReminderReceiptState.PENDING, store.read(occurrenceId))

        var recoveryCompletion: ((Boolean) -> Unit)? = null
        assertTrue(ReminderRegistrationCoordinator(store).register(occurrenceId) { recoveryCompletion = it })
        recoveryCompletion!!(true)
        firstCompletion!!(false)
        assertEquals(ReminderReceiptState.CONFIRMED, store.read(occurrenceId))
        assertFalse(ReminderRegistrationCoordinator(store).register(occurrenceId) { error("must not enqueue") })

        val failed = "$occurrenceId-failed"
        var failedCompletion: ((Boolean) -> Unit)? = null
        assertTrue(ReminderRegistrationCoordinator(store).register(failed) { failedCompletion = it })
        failedCompletion!!(false)
        assertNull(store.read(failed))
        assertFailsWith<IllegalStateException> {
            ReminderRegistrationCoordinator(store).register(failed) { throw IllegalStateException("enqueue") }
        }
        assertNull(store.read(failed))
    }

    @Test
    fun `pending reminder survives serialization and restores the exact work spec`() {
        val pending = PendingReminder(ScheduleRoutePayload("property-a", InspectionScheduleType.ANNUAL), now.plusSeconds(60))
        val bytes = ByteArrayOutputStream().also { output -> ObjectOutputStream(output).use { it.writeObject(pending) } }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as PendingReminder }
        assertEquals(pending, restored)
        assertNull(restored.resumeAfterGrant(NotificationPermissionState.DENIED, Clock.fixed(now, ZoneOffset.UTC)))
        assertEquals(workSpecs.create(pending.route, pending.dueAt), restored.resumeAfterGrant(NotificationPermissionState.GRANTED, Clock.fixed(now, ZoneOffset.UTC)))
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
    }

    @Test
    fun `local notification copy is bilingual and does not expose property identity`() {
        val route = ScheduleRoutePayload("property-a", InspectionScheduleType.ROUTINE)
        assertIs<ReminderDeliveryPlan.Retry>(reminderDeliveryPlan(33, false, route, now))
        val delivery = assertIs<ReminderDeliveryPlan.Notify>(reminderDeliveryPlan(32, false, route, now))
        val copy = delivery.copy
        val allText = "${copy.title}\n${copy.body}"

        assertContains(copy.title, "Inspection reminder")
        assertContains(copy.title, "巡检提醒")
        assertContains(copy.body, "Routine")
        assertContains(copy.body, "定期巡检")
        assertFalse(allText.contains("property-a"))
        assertFalse(allText.contains("address", ignoreCase = true))
        assertEquals(reminderRouteIntentSpec(route, now), delivery.intent)
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
    fun `manifest declares only the user initiated notification permission`() {
        val appRoot = locateAppRoot()
        val manifest = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(appRoot.resolve("src/main/AndroidManifest.xml").toFile())
        val permissions = manifest.getElementsByTagName("uses-permission")
        val names = (0 until permissions.length).map {
            permissions.item(it).attributes.getNamedItemNS(ANDROID_NS, "name").nodeValue
        }
        assertEquals(listOf("android.permission.POST_NOTIFICATIONS"), names)
    }

    private fun item(
        id: String,
        type: InspectionScheduleType,
        advice: ScheduleAdvice,
    ) = SchedulePropertyItem(id, "Property $id", type, advice)

    private class FakeOccurrenceStore : ReminderOccurrenceStore {
        private val receipts = mutableMapOf<String, ReminderReceiptState>()
        override fun read(occurrenceId: String): ReminderReceiptState? = receipts[occurrenceId]
        override fun write(occurrenceId: String, state: ReminderReceiptState): Boolean = true.also { receipts[occurrenceId] = state }
        override fun remove(occurrenceId: String) { receipts.remove(occurrenceId) }
    }

    private fun locateAppRoot(): Path = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        .first { Files.isRegularFile(it.resolve("src/main/AndroidManifest.xml")) }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}

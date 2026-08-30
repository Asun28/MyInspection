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
    fun `scheduler waits for durable enqueue before writing its receipt`() {
        val store = FakeOccurrenceStore()
        val logger = FakeLogger()
        val spec = workSpecs.create(ScheduleRoutePayload("property-a", InspectionScheduleType.ROUTINE), now.plusSeconds(60))
        var firstCompletion: ((Boolean) -> Unit)? = null
        var captured: ReminderEnqueueSpec? = null
        assertTrue(ScheduleReminderScheduler.schedule(spec, store, logger) { work, complete -> captured = work; firstCompletion = complete })
        assertEquals(ReminderEnqueueSpec(spec.uniqueWorkName, spec.initialDelayMillis, spec.route, spec.dueAt, spec.occurrenceId, androidx.work.ExistingWorkPolicy.KEEP), captured)
        assertNull(store.read(spec.occurrenceId))
        var recoveryCompletion: ((Boolean) -> Unit)? = null
        assertTrue(ScheduleReminderScheduler.schedule(spec, store, logger) { _, complete -> recoveryCompletion = complete })
        recoveryCompletion!!(true)
        firstCompletion!!(false)
        assertEquals(ReceiptState.ENQUEUED, store.read(spec.occurrenceId))
        assertFalse(ScheduleReminderScheduler.schedule(spec, store, logger) { _, _ -> error("must not enqueue") })
        val failed = workSpecs.create(spec.route, spec.dueAt.plusSeconds(1))
        var failedCompletion: ((Boolean) -> Unit)? = null
        assertTrue(ScheduleReminderScheduler.schedule(failed, store, logger) { _, complete -> failedCompletion = complete })
        failedCompletion!!(false)
        assertNull(store.read(failed.occurrenceId))
        assertFailsWith<IllegalStateException> {
            ScheduleReminderScheduler.schedule(failed, store, logger) { _, _ -> throw IllegalStateException("enqueue") }
        }
        assertNull(store.read(failed.occurrenceId))
        val raced = workSpecs.create(spec.route, spec.dueAt.plusSeconds(4)); var racedCompletion: ((Boolean) -> Unit)? = null; assertTrue(ScheduleReminderScheduler.schedule(raced, store, logger) { _, complete -> racedCompletion = complete }); store.compareAndSet(raced.occurrenceId, setOf(null), ReceiptState.DELIVERED); racedCompletion!!(true); assertEquals(ReceiptState.DELIVERED, store.read(raced.occurrenceId))
        store.reject = ReceiptState.ENQUEUED; val persistence = workSpecs.create(spec.route, spec.dueAt.plusSeconds(2)); var persistenceCompletion: ((Boolean) -> Unit)? = null
        assertTrue(ScheduleReminderScheduler.schedule(persistence, store, logger) { _, complete -> persistenceCompletion = complete }); persistenceCompletion!!(true); assertNull(store.read(persistence.occurrenceId)); assertContains(logger.stages, LogStage.RECEIPT_ENQUEUED)
        store.reject = null; var malformedCompletion: ((Boolean) -> Unit)? = null; assertTrue(ScheduleReminderScheduler.schedule(spec.copy(occurrenceId = "property-a\"\n"), store, logger) { _, complete -> malformedCompletion = complete }); malformedCompletion!!(false); assertFalse(logger.events.any { it.contains("property-a") })
        assertEquals(4, logger.stages.count { it == LogStage.ENQUEUE })
    }
    @Test
    fun `pending reminder survives serialization and restores the exact work spec`() {
        val row = SchedulePropertyRow("Property", ScheduleRoutePayload("property-a", InspectionScheduleType.ANNUAL), ScheduleBadge.UPCOMING, "Next", now.plusSeconds(60))
        val transition = scheduleRouteContentTransition(row, 33, NotificationPermissionState.UNKNOWN, false)
        assertIs<ReminderPermissionAction.RequestPermission>(transition.action)
        val pending = transition.pending
        val bytes = ByteArrayOutputStream().also { output -> ObjectOutputStream(output).use { it.writeObject(pending) } }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as PendingReminder }
        assertEquals(pending, restored)
        assertNull(restored.resumeAfterGrant(NotificationPermissionState.DENIED, Clock.fixed(now, ZoneOffset.UTC)))
        assertEquals(workSpecs.create(pending.route, pending.dueAt), restored.resumeAfterGrant(NotificationPermissionState.GRANTED, Clock.fixed(now, ZoneOffset.UTC)))
        assertIs<ReminderPermissionAction.ExplainDenied>(scheduleRouteContentTransition(row, 33, NotificationPermissionState.DENIED, false).action)
    }
    @Test
    fun `worker retries permission and post failures then marks only delivered notification`() {
        val spec = workSpecs.create(ScheduleRoutePayload("property-a", InspectionScheduleType.ROUTINE), now)
        val input = ReminderWorkerInput(spec.route.propertyId, spec.route.inspectionType.name, spec.dueAt.toEpochMilli(), spec.occurrenceId)
        val store = FakeOccurrenceStore().apply { compareAndSet(spec.occurrenceId, setOf(null), ReceiptState.ENQUEUED) }
        val logger = FakeLogger()
        val interleaved = FakeOccurrenceStore(); assertEquals(WorkerOutcome.SUCCESS, ScheduleReminderWorker.execute(input, 32, true, interleaved, logger) { interleaved.compareAndSet(spec.occurrenceId, setOf(null), ReceiptState.ENQUEUED) }); assertEquals(ReceiptState.DELIVERED, interleaved.read(spec.occurrenceId))
        assertEquals(WorkerOutcome.RETRY, ScheduleReminderWorker.execute(input, 33, false, store, logger) { error("must not notify") })
        assertEquals(ReceiptState.ENQUEUED, store.read(spec.occurrenceId))
        assertEquals(WorkerOutcome.RETRY, ScheduleReminderWorker.execute(input, 33, true, store, logger) { throw IllegalStateException("notify") })
        assertEquals(ReceiptState.ENQUEUED, store.read(spec.occurrenceId))
        var posted: ReminderDeliveryPlan.Notify? = null
        store.reject = ReceiptState.DELIVERED; assertEquals(WorkerOutcome.RETRY, ScheduleReminderWorker.execute(input, 33, true, store, logger) {}); assertEquals(ReceiptState.ENQUEUED, store.read(spec.occurrenceId)); store.reject = null
        assertEquals(WorkerOutcome.SUCCESS, ScheduleReminderWorker.execute(input, 33, true, store, logger) { posted = it })
        assertEquals(ReceiptState.DELIVERED, store.read(spec.occurrenceId))
        assertEquals(reminderRouteIntentSpec(spec.route, spec.dueAt), posted?.intent)
        var redeliveryPosts = 0; assertEquals(WorkerOutcome.SUCCESS, ScheduleReminderWorker.execute(input, 33, true, store, logger) { redeliveryPosts++ }); assertEquals(0, redeliveryPosts)
        assertEquals(WorkerOutcome.FAILURE, ScheduleReminderWorker.execute(input.copy(type = "BAD", occurrenceId = "property-a"), 33, true, store, logger) {})
        assertEquals(listOf(LogStage.PERMISSION, LogStage.NOTIFY, LogStage.RECEIPT_DELIVERED, LogStage.INPUT), logger.stages); assertFalse(logger.events.any { it.contains("property-a") })
        assertEquals("{\"event\":\"schedule-reminder\",\"stage\":\"notify\",\"occurrence\":\"${spec.occurrenceId}\",\"type\":\"ROUTINE\",\"retryable\":true,\"error_code\":\"notify-exception\"}", reminderLogMessage(LogStage.NOTIFY, spec.occurrenceId, InspectionScheduleType.ROUTINE, true, LogError.NOTIFY_EXCEPTION))
    }
    @Test
    fun `route intent identity is deterministic collision safe and privacy preserving`() {
        val dueAt = now.plusSeconds(60)
        val first = reminderRouteIntentSpec(ScheduleRoutePayload("Aa", InspectionScheduleType.ROUTINE), dueAt)
        val repeated = reminderRouteIntentSpec(ScheduleRoutePayload("Aa", InspectionScheduleType.ROUTINE), dueAt)
        val hashCollision = reminderRouteIntentSpec(ScheduleRoutePayload("BB", InspectionScheduleType.ROUTINE), dueAt)
        assertEquals(first, repeated)
        assertNotEquals(first.data, hashCollision.data)
        assertEquals(ReminderNotificationIdentity(first.data.substringAfterLast('/'), 0), reminderNotificationIdentity(first)); assertNotEquals(first.notificationTag, hashCollision.notificationTag)
        var postedIdentity: ReminderNotificationIdentity? = null; postReminderNotification(reminderNotificationIdentity(first), Unit) { tag, id, _ -> postedIdentity = ReminderNotificationIdentity(tag, id) }; assertEquals(reminderNotificationIdentity(first), postedIdentity)
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
        listOf("2026-09-26T14:30:00Z" to "2026-09-27", "2026-12-31T11:30:00Z" to "2027-01-01").forEach { (due, localDate) ->
            assertEquals("Next reminder: $localDate", scheduleRows(listOf(item("local", InspectionScheduleType.ANNUAL, ScheduleAdvice.Due(Instant.parse(due), now))), now, ScheduleFilter.ALL).single().nextFact)
        }
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
    private class FakeOccurrenceStore(var reject: ReceiptState? = null) : ReminderOccurrenceStore {
        private val receipts = mutableMapOf<String, ReceiptState>()
        override fun read(occurrenceId: String): ReceiptState? = receipts[occurrenceId]
        @Synchronized override fun compareAndSet(occurrenceId: String, expected: Set<ReceiptState?>, state: ReceiptState?): Boolean = if (read(occurrenceId) !in expected || state != null && state == reject) false else true.also { if (state == null) receipts.remove(occurrenceId) else receipts[occurrenceId] = state }
    }
    private class FakeLogger : ReminderEventLogger {
        val events = mutableListOf<String>(); val stages = mutableListOf<LogStage>()
        override fun log(stage: LogStage, occurrenceId: String?, type: InspectionScheduleType?, retryable: Boolean, errorCode: LogError) { stages += stage; events += reminderLogMessage(stage, occurrenceId, type, retryable, errorCode) }
    }
    private fun locateAppRoot(): Path = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        .first { Files.isRegularFile(it.resolve("src/main/AndroidManifest.xml")) }
    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}

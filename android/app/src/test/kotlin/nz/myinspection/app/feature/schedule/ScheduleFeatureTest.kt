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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
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
/** R4: cadence, identity, recovery, permission, API-33, and KEEP mutations fail this suite. */
class ScheduleFeatureTest {
    private val now = Instant.parse("2026-08-01T00:00:00Z")
    private val workSpecs = WorkSpecFactory(Clock.fixed(now, ZoneOffset.UTC))
    @Test
    fun `work is stable unique routed and delayed`() {
        val route = ScheduleRoutePayload("property-a", InspectionScheduleType.ROUTINE)
        val dueAt = now.plus(Duration.ofDays(2))
        val first = workSpecs.create(route, dueAt)
        val repeated = workSpecs.create(route, dueAt)
        assertEquals(first, repeated)
        assertEquals(Duration.ofDays(2).toMillis(), first.initialDelayMillis)
        assertEquals(route, first.route)
        assertTrue(first.uniqueWorkName.startsWith("schedule-reminder:"))
        assertFalse(first.uniqueWorkName.contains(route.propertyId))
        assertNotEquals(first.uniqueWorkName, workSpecs.create(route, dueAt.plusSeconds(1)).uniqueWorkName)
        assertNotEquals(first.uniqueWorkName, workSpecs.create(route.copy(propertyId = "property-b"), dueAt).uniqueWorkName)
        assertEquals(0L, workSpecs.create(route, now.minusSeconds(1)).initialDelayMillis)
    }
    @Test
    fun `scheduler receipts only durable enqueue`() {
        val store = FakeOccurrenceStore()
        val logger = FakeLogger()
        val spec = workSpecs.create(ScheduleRoutePayload("property-a", InspectionScheduleType.ROUTINE), now.plusSeconds(60))
        var firstDone: ((Boolean) -> Unit)? = null
        var captured: EnqueueSpec? = null
        assertTrue(ScheduleReminderScheduler.schedule(spec, store, logger) { work, done -> captured = work; firstDone = done })
        assertEquals(EnqueueSpec(spec.uniqueWorkName, spec.initialDelayMillis, spec.route, spec.dueAt, spec.occurrenceId, ExistingWorkPolicy.KEEP), captured)
        val queue = Files.createTempDirectory("schedule-restart").resolve("work")
        enqueueWorkManagerReminder(captured!!, persistentQueue(queue)); enqueueWorkManagerReminder(captured!!, persistentQueue(queue))
        assertEquals(listOf(spec.uniqueWorkName, "KEEP", ScheduleReminderWorker::class.java.name, spec.initialDelayMillis.toString(), spec.route.propertyId, spec.route.inspectionType.name, spec.dueAt.toEpochMilli().toString(), spec.occurrenceId), Files.readAllLines(queue))
        Files.delete(queue); Files.delete(queue.parent)
        assertNull(store.read(spec.occurrenceId))
        var retryDone: ((Boolean) -> Unit)? = null
        assertTrue(ScheduleReminderScheduler.schedule(spec, store, logger) { _, done -> retryDone = done })
        retryDone!!(true)
        firstDone!!(false)
        assertEquals(ReceiptState.ENQUEUED, store.read(spec.occurrenceId))
        assertFalse(ScheduleReminderScheduler.schedule(spec, store, logger) { _, _ -> error("must not enqueue") })
        val failed = workSpecs.create(spec.route, spec.dueAt.plusSeconds(1))
        var failDone: ((Boolean) -> Unit)? = null
        assertTrue(ScheduleReminderScheduler.schedule(failed, store, logger) { _, done -> failDone = done })
        failDone!!(false)
        assertNull(store.read(failed.occurrenceId))
        assertFailsWith<IllegalStateException> {
            ScheduleReminderScheduler.schedule(failed, store, logger) { _, _ -> throw IllegalStateException("enqueue") }
        }
        assertNull(store.read(failed.occurrenceId))
        val raced = workSpecs.create(spec.route, spec.dueAt.plusSeconds(4)); var raceDone: ((Boolean) -> Unit)? = null; assertTrue(ScheduleReminderScheduler.schedule(raced, store, logger) { _, done -> raceDone = done }); store.compareAndSet(raced.occurrenceId, setOf(null), ReceiptState.DELIVERED); raceDone!!(true); assertEquals(ReceiptState.DELIVERED, store.read(raced.occurrenceId))
        store.reject = ReceiptState.ENQUEUED; val persistence = workSpecs.create(spec.route, spec.dueAt.plusSeconds(2)); var persistDone: ((Boolean) -> Unit)? = null
        assertTrue(ScheduleReminderScheduler.schedule(persistence, store, logger) { _, done -> persistDone = done }); persistDone!!(true); assertNull(store.read(persistence.occurrenceId)); assertContains(logger.stages, LogStage.RECEIPT_ENQUEUED)
        store.reject = null; var badDone: ((Boolean) -> Unit)? = null; assertTrue(ScheduleReminderScheduler.schedule(spec.copy(occurrenceId = "property-a\"\n"), store, logger) { _, done -> badDone = done }); badDone!!(false); assertFalse(logger.events.any { it.contains("property-a") })
        assertEquals(4, logger.stages.count { it == LogStage.ENQUEUE })
    }
    @Test
    fun `pending reminder restores exact work`() {
        val row = SchedulePropertyRow("Property", ScheduleRoutePayload("property-a", InspectionScheduleType.ANNUAL), ScheduleBadge.UPCOMING, "Next", now.plusSeconds(60))
        val transition = scheduleRouteContentTransition(row, 33, PermissionState.UNKNOWN, false)
        assertIs<PermissionAction.RequestPermission>(transition.action)
        val pending = transition.pending
        val bytes = ByteArrayOutputStream().also { output -> ObjectOutputStream(output).use { it.writeObject(pending) } }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as PendingReminder }
        assertEquals(pending, restored)
        assertNull(restored.resumeAfterGrant(PermissionState.DENIED, Clock.fixed(now, ZoneOffset.UTC)))
        assertEquals(workSpecs.create(pending.route, pending.dueAt), restored.resumeAfterGrant(PermissionState.GRANTED, Clock.fixed(now, ZoneOffset.UTC)))
        assertIs<PermissionAction.ExplainDenied>(scheduleRouteContentTransition(row, 33, PermissionState.DENIED, false).action)
    }
    @Test
    fun `worker retries failures and marks delivery`() {
        val spec = workSpecs.create(ScheduleRoutePayload("property-a", InspectionScheduleType.ROUTINE), now)
        val input = ReminderWorkerInput(spec.route.propertyId, spec.route.inspectionType.name, spec.dueAt.toEpochMilli(), spec.occurrenceId)
        val store = FakeOccurrenceStore().apply { compareAndSet(spec.occurrenceId, setOf(null), ReceiptState.ENQUEUED) }
        val logger = FakeLogger()
        val interleaved = FakeOccurrenceStore(); assertEquals(WorkerOutcome.SUCCESS, ScheduleReminderWorker.execute(input, 32, true, interleaved, logger) { interleaved.compareAndSet(spec.occurrenceId, setOf(null), ReceiptState.ENQUEUED) }); assertEquals(ReceiptState.DELIVERED, interleaved.read(spec.occurrenceId))
        assertEquals(WorkerOutcome.RETRY, ScheduleReminderWorker.execute(input, 33, false, store, logger) { error("must not notify") })
        assertEquals(ReceiptState.ENQUEUED, store.read(spec.occurrenceId))
        assertEquals(WorkerOutcome.RETRY, ScheduleReminderWorker.execute(input, 33, true, store, logger) { throw IllegalStateException("notify") })
        assertEquals(ReceiptState.ENQUEUED, store.read(spec.occurrenceId))
        var posted: DeliveryPlan.Notify? = null
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
    fun `route identity is stable private and collision safe`() {
        val dueAt = now.plusSeconds(60)
        val first = reminderRouteIntentSpec(ScheduleRoutePayload("Aa", InspectionScheduleType.ROUTINE), dueAt)
        val repeated = reminderRouteIntentSpec(ScheduleRoutePayload("Aa", InspectionScheduleType.ROUTINE), dueAt)
        val hashCollision = reminderRouteIntentSpec(ScheduleRoutePayload("BB", InspectionScheduleType.ROUTINE), dueAt)
        assertEquals(first, repeated)
        assertNotEquals(first.data, hashCollision.data)
        assertEquals(NotificationIdentity(first.data.substringAfterLast('/'), 0), reminderNotificationIdentity(first)); assertNotEquals(first.notificationTag, hashCollision.notificationTag)
        var postedIdentity: NotificationIdentity? = null; postReminderNotification(reminderNotificationIdentity(first), Unit) { tag, id, _ -> postedIdentity = NotificationIdentity(tag, id) }; assertEquals(reminderNotificationIdentity(first), postedIdentity)
        assertFalse(first.data.contains("Aa"))
        assertEquals("Aa", first.propertyId)
        assertEquals(InspectionScheduleType.ROUTINE.name, first.inspectionType)
    }
    @Test
    fun `permission is user driven with denied recovery`() {
        assertIs<PermissionAction.Schedule>(PermissionPolicy.next(32, PermissionState.UNKNOWN, true))
        assertIs<PermissionAction.Schedule>(PermissionPolicy.next(35, PermissionState.GRANTED))
        assertIs<PermissionAction.RequestPermission>(PermissionPolicy.next(33, PermissionState.UNKNOWN, false))
        assertIs<PermissionAction.ShowRationale>(PermissionPolicy.next(33, PermissionState.UNKNOWN, true))
        val denied = assertIs<PermissionAction.ExplainDenied>(
            PermissionPolicy.next(33, PermissionState.DENIED),
        )
        assertContains(denied.english, "Settings")
        assertContains(denied.chinese, "设置")
    }
    @Test
    fun `notification is bilingual and private`() {
        val route = ScheduleRoutePayload("property-a", InspectionScheduleType.ROUTINE)
        assertIs<DeliveryPlan.Retry>(reminderDeliveryPlan(33, false, route, now))
        val delivery = assertIs<DeliveryPlan.Notify>(reminderDeliveryPlan(32, false, route, now))
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
    fun `rows map badges filters and local dates`() {
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
    fun `manifest declares notification permission only`() {
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
    private fun persistentQueue(path: Path): (String, ExistingWorkPolicy, OneTimeWorkRequest) -> Unit = { name, policy, request -> if (policy != ExistingWorkPolicy.KEEP || Files.notExists(path)) { val work = request.workSpec; val input = work.input; Files.write(path, listOf(name, policy.name, work.workerClassName, work.initialDelay.toString(), input.getString(ReminderWorkData.PROPERTY_ID), input.getString(ReminderWorkData.INSPECTION_TYPE), input.getLong(ReminderWorkData.DUE_AT_EPOCH_MILLIS, -1).toString(), input.getString(ReminderWorkData.OCCURRENCE_ID))) } }
    private companion object { const val ANDROID_NS = "http://schemas.android.com/apk/res/android" }
}

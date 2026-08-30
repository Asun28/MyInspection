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
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        val route = ScheduleRoute("property-a", InspectionScheduleType.ROUTINE)
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
        val spec = workSpecs.create(ScheduleRoute("property-a", InspectionScheduleType.ROUTINE), now.plusSeconds(60))
        var firstDone: ((Boolean) -> Unit)? = null
        var captured: EnqueueSpec? = null
        var result: Boolean? = null
        assertTrue(ReminderScheduler.schedule(spec, store, logger) { work, done -> captured = work; firstDone = done })
        assertEquals(EnqueueSpec(spec.uniqueWorkName, spec.initialDelayMillis, spec.route, spec.dueAt, spec.occurrenceId, ExistingWorkPolicy.KEEP), captured)
        enqueueWorkManagerReminder(captured!!) { name, policy, request ->
            val work = request.workSpec; val input = work.input
            assertEquals(listOf(spec.uniqueWorkName, "KEEP", ReminderWorker::class.java.name, spec.initialDelayMillis.toString(), spec.route.propertyId, spec.route.inspectionType.name, spec.dueAt.toEpochMilli().toString(), spec.occurrenceId), listOf(name, policy.name, work.workerClassName, work.initialDelay.toString(), input.getString(WorkKeys.PROPERTY_ID), input.getString(WorkKeys.INSPECTION_TYPE), input.getLong(WorkKeys.DUE_AT_EPOCH_MILLIS, -1).toString(), input.getString(WorkKeys.OCCURRENCE_ID)))
        }
        assertNull(store.read(spec.occurrenceId))
        var retryDone: ((Boolean) -> Unit)? = null
        assertTrue(ReminderScheduler.schedule(spec, store, logger) { _, done -> retryDone = done })
        retryDone!!(true)
        firstDone!!(false)
        assertEquals(Receipt.ENQUEUED, store.read(spec.occurrenceId))
        assertFalse(ReminderScheduler.schedule(spec, store, logger, { result = it }) { _, _ -> error("must not enqueue") }); assertEquals(true, result)
        val failed = workSpecs.create(spec.route, spec.dueAt.plusSeconds(1))
        result = null
        var failDone: ((Boolean) -> Unit)? = null
        assertTrue(ReminderScheduler.schedule(failed, store, logger, { result = it }) { _, done -> failDone = done })
        failDone!!(false)
        assertEquals(false, result)
        assertNull(store.read(failed.occurrenceId))
        result = null
        assertTrue(ReminderScheduler.schedule(failed, store, logger, { result = it }) { _, _ -> throw IllegalStateException("enqueue") })
        assertEquals(false, result)
        assertNull(store.read(failed.occurrenceId))
        val raced = workSpecs.create(spec.route, spec.dueAt.plusSeconds(4)); var raceDone: ((Boolean) -> Unit)? = null; assertTrue(ReminderScheduler.schedule(raced, store, logger) { _, done -> raceDone = done }); store.compareAndSet(raced.occurrenceId, setOf(null), Receipt.DELIVERED); raceDone!!(true); assertEquals(Receipt.DELIVERED, store.read(raced.occurrenceId))
        store.reject = Receipt.ENQUEUED; result = null; val persistence = workSpecs.create(spec.route, spec.dueAt.plusSeconds(2)); var persistDone: ((Boolean) -> Unit)? = null
        assertTrue(ReminderScheduler.schedule(persistence, store, logger, { result = it }) { _, done -> persistDone = done }); persistDone!!(true); assertEquals(false, result); assertNull(store.read(persistence.occurrenceId)); assertContains(logger.stages, LogStage.RECEIPT_ENQUEUED)
        store.reject = null; var badDone: ((Boolean) -> Unit)? = null; assertTrue(ReminderScheduler.schedule(spec.copy(occurrenceId = "property-a\"\n"), store, logger) { _, done -> badDone = done }); badDone!!(false); assertFalse(logger.events.any { it.contains("property-a") })
        assertEquals(4, logger.stages.count { it == LogStage.ENQUEUE })
    }
    @Test
    fun `pending reminder restores exact work`() {
        val row = ScheduleRow("Property", ScheduleRoute("property-a", InspectionScheduleType.ANNUAL), ScheduleBadge.UPCOMING, "Next", now.plusSeconds(60))
        val transition = scheduleRouteContentTransition(row, 33, PermissionState.UNKNOWN, false)
        assertIs<PermissionAction.RequestPermission>(transition.action)
        val pending = transition.pending
        val bytes = ByteArrayOutputStream().also { output -> ObjectOutputStream(output).use { it.writeObject(pending) } }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as PendingWork }
        assertEquals(pending, restored)
        assertEquals(restored, restored.afterSchedule(false)); assertNull(restored.afterSchedule(true))
        assertNull((null as PendingWork?).resumeAfterGrant(PermissionState.GRANTED))
        assertNull(restored.resumeAfterGrant(PermissionState.DENIED, Clock.fixed(now, ZoneOffset.UTC)))
        assertEquals(workSpecs.create(pending.route, pending.dueAt), restored.resumeAfterGrant(PermissionState.GRANTED, Clock.fixed(now, ZoneOffset.UTC)))
        assertIs<PermissionAction.ExplainDenied>(scheduleRouteContentTransition(row, 33, PermissionState.DENIED, false).action)
    }
    @Test
    fun `worker retries failures and marks delivery`() {
        val spec = workSpecs.create(ScheduleRoute("property-a", InspectionScheduleType.ROUTINE), now)
        val input = WorkerInput(spec.route.propertyId, spec.route.inspectionType.name, spec.dueAt.toEpochMilli(), spec.occurrenceId)
        val store = FakeOccurrenceStore().apply { compareAndSet(spec.occurrenceId, setOf(null), Receipt.ENQUEUED) }
        val logger = FakeLogger()
        val interleaved = FakeOccurrenceStore(); assertEquals(WorkerOutcome.SUCCESS, ReminderWorker.execute(input, 32, true, interleaved, logger) { interleaved.compareAndSet(spec.occurrenceId, setOf(null), Receipt.ENQUEUED) }); assertEquals(Receipt.DELIVERED, interleaved.read(spec.occurrenceId))
        assertEquals(WorkerOutcome.RETRY, ReminderWorker.execute(input, 33, false, store, logger) { error("must not notify") })
        assertEquals(Receipt.ENQUEUED, store.read(spec.occurrenceId))
        assertEquals(WorkerOutcome.RETRY, ReminderWorker.execute(input, 33, true, store, logger) { throw IllegalStateException("notify") })
        assertEquals(Receipt.ENQUEUED, store.read(spec.occurrenceId))
        var posted: DeliveryPlan.Notify? = null
        store.reject = Receipt.DELIVERED; assertEquals(WorkerOutcome.RETRY, ReminderWorker.execute(input, 33, true, store, logger) {}); assertEquals(Receipt.ENQUEUED, store.read(spec.occurrenceId)); store.reject = null
        assertEquals(WorkerOutcome.SUCCESS, ReminderWorker.execute(input, 33, true, store, logger) { posted = it })
        assertEquals(Receipt.DELIVERED, store.read(spec.occurrenceId))
        assertEquals(reminderRouteIntentSpec(spec.route, spec.dueAt), posted?.intent)
        var redeliveryPosts = 0; assertEquals(WorkerOutcome.SUCCESS, ReminderWorker.execute(input, 33, true, store, logger) { redeliveryPosts++ }); assertEquals(0, redeliveryPosts)
        assertEquals(WorkerOutcome.FAILURE, ReminderWorker.execute(input.copy(type = "BAD", occurrenceId = "property-a"), 33, true, store, logger) {})
        assertEquals(listOf(LogStage.PERMISSION, LogStage.NOTIFY, LogStage.RECEIPT_DELIVERED, LogStage.INPUT), logger.stages); assertFalse(logger.events.any { it.contains("property-a") })
        assertEquals("{\"event\":\"schedule-reminder\",\"stage\":\"notify\",\"occurrence\":\"${spec.occurrenceId}\",\"type\":\"ROUTINE\",\"retryable\":true,\"error_code\":\"notify-exception\"}", reminderLogMessage(LogStage.NOTIFY, spec.occurrenceId, InspectionScheduleType.ROUTINE, true, LogError.NOTIFY_EXCEPTION))
    }
    @Test
    fun `route identity is stable private and collision safe`() {
        val dueAt = now.plusSeconds(60)
        val first = reminderRouteIntentSpec(ScheduleRoute("Aa", InspectionScheduleType.ROUTINE), dueAt)
        val repeated = reminderRouteIntentSpec(ScheduleRoute("Aa", InspectionScheduleType.ROUTINE), dueAt)
        val hashCollision = reminderRouteIntentSpec(ScheduleRoute("BB", InspectionScheduleType.ROUTINE), dueAt)
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
        val labels = mapOf(InspectionScheduleType.ROUTINE to ("Routine" to "定期巡检"), InspectionScheduleType.ANNUAL to ("Annual" to "年度住宅检查"), InspectionScheduleType.INGOING to ("Ingoing" to "入住巡检"), InspectionScheduleType.EXIT to ("Exit" to "退租巡检"))
        labels.forEach { (type, labels) ->
            val route = ScheduleRoute("property-a", type); assertIs<DeliveryPlan.Retry>(reminderDeliveryPlan(33, false, route, now)); val delivery = assertIs<DeliveryPlan.Notify>(reminderDeliveryPlan(32, false, route, now)); val text = "${delivery.copy.title}\n${delivery.copy.body}"
            assertContains(text, "Inspection reminder"); assertContains(text, "巡检提醒"); assertContains(text, labels.first); assertContains(text, labels.second); assertFalse(text.contains("property-a")); assertFalse(text.contains("address", true)); assertEquals(reminderRouteIntentSpec(route, now), delivery.intent)
        }
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
        assertEquals(ScheduleRoute("due", InspectionScheduleType.ROUTINE), all.first().route)
        var opened: ScheduleRoute? = null; all.first().open { opened = it }; assertEquals(all.first().route, opened)
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
    ) = ScheduleItem(id, "Property $id", type, advice)
    private class FakeOccurrenceStore(var reject: Receipt? = null) : ReceiptStore {
        private val receipts = mutableMapOf<String, Receipt>()
        override fun read(occurrenceId: String): Receipt? = receipts[occurrenceId]
        @Synchronized override fun compareAndSet(occurrenceId: String, expected: Set<Receipt?>, state: Receipt?): Boolean = if (read(occurrenceId) !in expected || state != null && state == reject) false else true.also { if (state == null) receipts.remove(occurrenceId) else receipts[occurrenceId] = state }
    }
    private class FakeLogger : EventLogger {
        val events = mutableListOf<String>(); val stages = mutableListOf<LogStage>()
        override fun log(stage: LogStage, occurrenceId: String?, type: InspectionScheduleType?, retryable: Boolean, errorCode: LogError) { stages += stage; events += reminderLogMessage(stage, occurrenceId, type, retryable, errorCode) }
    }
    private fun locateAppRoot(): Path = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        .first { Files.isRegularFile(it.resolve("src/main/AndroidManifest.xml")) }
    private companion object { const val ANDROID_NS = "http://schemas.android.com/apk/res/android" }
}

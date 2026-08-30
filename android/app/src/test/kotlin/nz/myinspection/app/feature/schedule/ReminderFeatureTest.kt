package nz.myinspection.app.feature.schedule
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import nz.myinspection.core.schedule.InspectionScheduleType

class ReminderFeatureTest {
    private val now = Instant.parse("2026-08-01T00:00:00Z")
    private val factory = WorkSpecFactory()
    @Test
    fun `production request is unique kept routed and delayed`() {
        val route = ScheduleRoute("property-a", InspectionScheduleType.ROUTINE)
        val dueAt = now.plus(Duration.ofDays(2)).plusNanos(1)
        val spec = factory.create(route, dueAt)
        assertTrue(runCatching { factory.create(route.copy(propertyId = ""), dueAt) }.isFailure)
        assertEquals(spec, factory.create(route, dueAt))
        assertEquals("schedule-reminder:${spec.occurrenceId}", spec.uniqueWorkName)
        assertFalse(spec.uniqueWorkName.contains(route.propertyId))
        assertNotEquals(spec.uniqueWorkName, factory.create(route, dueAt.plusNanos(1)).uniqueWorkName)
        assertNotEquals(spec.uniqueWorkName, factory.create(route.copy(propertyId = "b"), dueAt).uniqueWorkName)
        val annual = factory.create(route.copy(inspectionType = InspectionScheduleType.ANNUAL), dueAt)
        assertNotEquals(spec.uniqueWorkName, annual.uniqueWorkName)
        val enqueueClock = Clock.fixed(now.plus(Duration.ofDays(1)), ZoneOffset.UTC)
        val submissions = mutableListOf<OneTimeWorkRequest>()
        enqueueWorkManagerReminder(EnqueueSpec.from(spec), enqueueClock) { name, policy, request ->
            submissions += request
            val work = request.workSpec
            assertEquals(spec.uniqueWorkName, name)
            assertEquals(ExistingWorkPolicy.KEEP, policy)
            assertEquals(ReminderWorker::class.java.name, work.workerClassName)
            assertEquals(Duration.ofDays(1).toMillis(), work.initialDelay)
            assertEquals(route.propertyId, work.input.getString(WorkKeys.PROPERTY_ID))
            assertEquals(route.inspectionType.name, work.input.getString(WorkKeys.INSPECTION_TYPE))
            assertEquals(dueAt.toString(), work.input.getString(WorkKeys.DUE_AT_INSTANT))
            assertEquals(spec.occurrenceId, work.input.getString(WorkKeys.OCCURRENCE_ID))
        }
        assertEquals(1, submissions.size)
        val alreadyDue = EnqueueSpec.from(factory.create(route, now.minusMillis(1)))
        enqueueWorkManagerReminder(alreadyDue, enqueueClock) { _, _, request ->
            submissions += request
            assertEquals(0L, request.workSpec.initialDelay)
        }
        assertEquals(2, submissions.size)
    }
    @Test
    fun `registration receipts are durable idempotent and race safe`() {
        val spec = spec(InspectionScheduleType.ROUTINE, 60)
        val store = FakeReceiptStore()
        val logger = FakeLogger()
        var completion: ((EnqueueResult) -> Unit)? = null
        var result: RegistrationResult? = null
        assertTrue(schedule(spec, store, logger, { result = it }) { completion = it })
        assertEquals(ReceiptState.MISSING, store.read(spec.occurrenceId))
        completion!!(EnqueueResult.Accepted)
        assertEquals(RegistrationResult.SUCCESS, result)
        assertEquals(ReceiptState.ENQUEUED, store.read(spec.occurrenceId))
        var reconciliations = 0
        assertTrue(schedule(spec, store, logger) { done -> done(EnqueueResult.Accepted).also { reconciliations++ } })
        assertEquals(1, reconciliations)
        val rejected = spec(InspectionScheduleType.ROUTINE, 61)
        schedule(rejected, store, logger, { result = it }) {
            it(EnqueueResult.Rejected(IOException("private path")))
        }
        assertEquals(ReceiptState.MISSING, store.read(rejected.occurrenceId))
        assertEquals(FailureCauseCode.IO, logger.records.last().causeCode)
        assertEquals(RegistrationResult.RETRYABLE_FAILURE, result)
        assertTrue(logger.records.last().retryable)
        assertFalse(logger.messages.last().contains("private path"))
        val nullError = spec(InspectionScheduleType.ROUTINE, 62)
        schedule(nullError, store, logger, { result = it }) { it(EnqueueResult.Rejected(null)) }
        assertEquals(RegistrationResult.RETRYABLE_FAILURE, result)
        assertEquals(
            "{\"event\":\"schedule-reminder\",\"stage\":\"enqueue\",\"occurrence\":\"${nullError.occurrenceId}\"," +
                "\"type\":\"ROUTINE\",\"retryable\":true,\"error_code\":\"enqueue-failed\",\"cause_code\":\"unknown\"}",
            logger.messages.last(),
        )
        val raced = spec(InspectionScheduleType.ROUTINE, 64)
        schedule(raced, store, logger, { result = it }) { completion = it }
        store.set(raced.occurrenceId, ReceiptState.DELIVERED)
        completion!!(EnqueueResult.Accepted)
        assertEquals(RegistrationResult.SUCCESS, result)
        assertEquals(ReceiptState.DELIVERED, store.read(raced.occurrenceId))
        val thrown = spec(InspectionScheduleType.ROUTINE, 65)
        ReminderScheduler.schedule(thrown, store, logger, { result = it }) { _, _ ->
            throw SecurityException("private detail")
        }
        assertEquals(FailureCauseCode.SECURITY, logger.records.last().causeCode)
        assertFalse(logger.records.last().retryable)
        assertEquals(RegistrationResult.PERMANENT_FAILURE, result)
        assertEquals(ReceiptState.TERMINAL, store.read(thrown.occurrenceId))
        var permanentRetries = 0
        assertFalse(ReminderScheduler.schedule(thrown, store, logger, { result = it }) { _, _ -> permanentRetries++ })
        assertEquals(RegistrationResult.PERMANENT_FAILURE, result)
        assertEquals(0, permanentRetries)
    }
    @Test
    fun `terminal worker before enqueue acceptance remains retryable`() {
        val spec = spec(InspectionScheduleType.ROUTINE)
        val store = FakeReceiptStore()
        val logger = FakeLogger()
        var completion: ((EnqueueResult) -> Unit)? = null
        var result: RegistrationResult? = null
        schedule(spec, store, logger, { result = it }) { completion = it }
        assertEquals(WorkerOutcome.FAILURE, execute(spec, store, logger, attempt = 2, granted = false))
        assertEquals(ReceiptState.RETRYABLE, store.read(spec.occurrenceId))
        val storageLogs = logger.records.count { it.causeCode == FailureCauseCode.STORAGE_WRITE }
        completion!!(EnqueueResult.Accepted)
        assertEquals(RegistrationResult.RETRYABLE_FAILURE, result)
        assertEquals(ReceiptState.RETRYABLE, store.read(spec.occurrenceId))
        assertEquals(storageLogs, logger.records.count { it.causeCode == FailureCauseCode.STORAGE_WRITE })
        var replacementCompletion: ((EnqueueResult) -> Unit)? = null
        schedule(spec, store, logger, { result = it }) { replacementCompletion = it }
        assertEquals(ReceiptState.MISSING, store.read(spec.occurrenceId))
        assertEquals(WorkerOutcome.FAILURE, execute(spec, store, logger, attempt = 2, granted = false))
        replacementCompletion!!(EnqueueResult.Accepted)
        assertEquals(RegistrationResult.RETRYABLE_FAILURE, result)
        assertEquals(ReceiptState.RETRYABLE, store.read(spec.occurrenceId))
    }
    @Test
    fun `corrupt and failed persistence fail closed with cause codes`() {
        val rawStates = listOf(null, "ENQUEUED", "DELIVERED", "RETRYABLE", "TERMINAL", "bad")
        val states = listOf(ReceiptState.MISSING, ReceiptState.ENQUEUED, ReceiptState.DELIVERED,
            ReceiptState.RETRYABLE, ReceiptState.TERMINAL, ReceiptState.CORRUPT)
        assertEquals(states, rawStates.map(::decodeReceipt))
        assertEquals(ReceiptState.CORRUPT, readReceipt { throw ClassCastException("wrong type") })
        val spec = spec(InspectionScheduleType.ANNUAL, 1)
        val logger = FakeLogger()
        val corrupt = FakeReceiptStore().apply { corrupt += spec.occurrenceId }
        assertFalse(ReminderScheduler.schedule(spec, corrupt, logger) { _, _ -> error("no enqueue") })
        assertEquals(FailureCauseCode.STORAGE_CORRUPT, logger.records.last().causeCode)
        val rejected = FakeReceiptStore().apply { rejectedTarget = ReceiptState.ENQUEUED }
        schedule(spec, rejected, logger) { it(EnqueueResult.Accepted) }
        assertEquals(FailureCauseCode.STORAGE_WRITE, logger.records.last().causeCode)
        val throwingRead = FakeReceiptStore().apply { throwOnRead = true }
        assertFalse(ReminderScheduler.schedule(spec, throwingRead, logger) { _, _ -> })
        assertEquals(FailureCauseCode.STORAGE_CORRUPT, logger.records.last().causeCode)
        val throwingWrite = FakeReceiptStore().apply { throwOnWrite = true }
        schedule(spec, throwingWrite, logger) { it(EnqueueResult.Accepted) }
        assertEquals(FailureCauseCode.STORAGE_WRITE, logger.records.last().causeCode)
        val resetFailure = FakeReceiptStore().apply { set(spec.occurrenceId, ReceiptState.RETRYABLE) }
            .apply { rejectedTarget = ReceiptState.MISSING }
        assertFalse(ReminderScheduler.schedule(spec, resetFailure, logger) { _, _ -> })
        assertEquals(FailureCauseCode.STORAGE_WRITE, logger.records.last().causeCode)
    }
    @Test
    fun `worker retries transient failures and releases terminal receipts`() {
        val spec = spec(InspectionScheduleType.ROUTINE)
        val logger = FakeLogger()
        listOf(0, 1).forEach { attempt ->
            val store = enqueuedStore(spec)
            val outcome = execute(spec, store, logger, attempt) { throw IOException("offline") }
            assertEquals(WorkerOutcome.RETRY, outcome)
            assertEquals(ReceiptState.ENQUEUED, store.read(spec.occurrenceId))
            assertTrue(logger.records.last().retryable)
        }
        val exhausted = enqueuedStore(spec)
        assertEquals(WorkerOutcome.FAILURE, execute(spec, exhausted, logger, attempt = 2) { throw IOException("offline") })
        assertEquals(ReceiptState.RETRYABLE, exhausted.read(spec.occurrenceId))
        assertFalse(logger.records.last { it.stage == LogStage.NOTIFY }.retryable)
        val permanent = enqueuedStore(spec)
        assertEquals(WorkerOutcome.FAILURE, execute(spec, permanent, logger) { throw SecurityException("revoked") })
        assertEquals(ReceiptState.TERMINAL, permanent.read(spec.occurrenceId))
        assertEquals(FailureCauseCode.SECURITY, logger.records.last { it.stage == LogStage.NOTIFY }.causeCode)
        var permanentPosts = 0
        assertEquals(WorkerOutcome.FAILURE, execute(spec, permanent, logger) { permanentPosts++ })
        assertEquals(0, permanentPosts)
        var registration: RegistrationResult? = null
        assertFalse(ReminderScheduler.schedule(spec, permanent, logger, { registration = it }) { _, _ -> permanentPosts++ })
        assertEquals(RegistrationResult.PERMANENT_FAILURE, registration)
        assertEquals(0, permanentPosts)
        val concurrentTerminal = enqueuedStore(spec)
        val storageLogs = logger.records.count { it.causeCode == FailureCauseCode.STORAGE_WRITE }
        assertEquals(WorkerOutcome.FAILURE, execute(spec, concurrentTerminal, logger, attempt = 2) {
            throw IOException().also { concurrentTerminal.set(spec.occurrenceId, ReceiptState.TERMINAL) }
        })
        assertEquals(storageLogs, logger.records.count { it.causeCode == FailureCauseCode.STORAGE_WRITE })
        val denied = enqueuedStore(spec)
        assertEquals(WorkerOutcome.RETRY, execute(spec, denied, logger, granted = false))
        assertEquals(WorkerOutcome.FAILURE, execute(spec, denied, logger, attempt = 2, granted = false))
        assertEquals(ReceiptState.RETRYABLE, denied.read(spec.occurrenceId))
    }
    @Test
    fun `delivery is idempotent and receipt failures are explicit`() {
        val spec = spec(InspectionScheduleType.EXIT)
        val logger = FakeLogger()
        val store = enqueuedStore(spec)
        var posted: DeliveryPlan.Notify? = null
        assertEquals(WorkerOutcome.SUCCESS, execute(spec, store, logger) { posted = it })
        assertEquals(ReceiptState.DELIVERED, store.read(spec.occurrenceId))
        assertEquals(reminderRouteIntentSpec(spec.route, spec.dueAt), posted?.intent)
        var repeats = 0
        assertEquals(WorkerOutcome.SUCCESS, execute(spec, store, logger) { repeats++ })
        assertEquals(0, repeats)
        val missing = FakeReceiptStore()
        assertEquals(WorkerOutcome.SUCCESS, execute(spec, missing, logger))
        assertEquals(ReceiptState.DELIVERED, missing.read(spec.occurrenceId))
        val concurrent = FakeReceiptStore()
        assertEquals(WorkerOutcome.SUCCESS, execute(spec, concurrent, logger) {
            concurrent.set(spec.occurrenceId, ReceiptState.DELIVERED)
        })
        val rejected = enqueuedStore(spec).apply { rejectedTarget = ReceiptState.DELIVERED }
        assertEquals(WorkerOutcome.RETRY, execute(spec, rejected, logger))
        assertEquals(FailureCauseCode.STORAGE_WRITE, logger.records.last().causeCode)
        assertEquals(WorkerOutcome.FAILURE, execute(spec, rejected, logger, attempt = 2))
        assertEquals(ReceiptState.RETRYABLE, rejected.read(spec.occurrenceId))
        val throwing = enqueuedStore(spec).apply { throwOnWrite = true }
        var alerts = 0
        assertEquals(WorkerOutcome.RETRY, execute(spec, throwing, logger) { alerts += if (it.onlyAlertOnce) 1 else 100 })
        assertEquals(WorkerOutcome.FAILURE, execute(spec, throwing, logger, attempt = 2) { alerts += if (it.onlyAlertOnce) 1 else 100 })
        assertEquals(2, alerts)
        assertEquals(FailureCauseCode.STORAGE_WRITE, logger.records.last().causeCode)
        assertEquals(ReceiptState.ENQUEUED, throwing.read(spec.occurrenceId))
        throwing.throwOnWrite = false
        var recovery: RegistrationResult? = null
        assertTrue(schedule(spec, throwing, logger, { recovery = it }) { it(EnqueueResult.Accepted) })
        assertEquals(RegistrationResult.SUCCESS, recovery)
        val cleanupFailure = enqueuedStore(spec).apply { rejectedTarget = ReceiptState.TERMINAL }
        assertEquals(WorkerOutcome.FAILURE, execute(spec, cleanupFailure, logger) { throw SecurityException("permanent") })
        assertEquals(ReceiptState.ENQUEUED, cleanupFailure.read(spec.occurrenceId))
        cleanupFailure.rejectedTarget = null
        assertTrue(schedule(spec, cleanupFailure, logger, { recovery = it }) { it(EnqueueResult.Accepted) })
        assertEquals(RegistrationResult.SUCCESS, recovery)
        val cleanupCorrupt = enqueuedStore(spec).apply { rejectedTarget = ReceiptState.TERMINAL }
            .apply { corruptOnRejectedTarget = true }
        assertEquals(WorkerOutcome.FAILURE, execute(spec, cleanupCorrupt, logger) { throw SecurityException() })
        assertEquals(FailureCauseCode.STORAGE_CORRUPT, logger.records.last().causeCode)
    }
    @Test
    fun `invalid and corrupt worker inputs never post or leak identifiers`() {
        val spec = spec(InspectionScheduleType.INGOING)
        val logger = FakeLogger()
        var posts = 0
        val corrupt = FakeReceiptStore().apply { this.corrupt += spec.occurrenceId }
        assertEquals(WorkerOutcome.FAILURE, execute(spec, corrupt, logger) { posts++ })
        assertEquals(FailureCauseCode.STORAGE_CORRUPT, logger.records.last().causeCode)
        val input = WorkerInput.from(spec)
        val blankId = reminderOccurrenceId(ScheduleRoute("", spec.route.inspectionType), spec.dueAt)
        listOf(
            input.copy(propertyId = null), input.copy(propertyId = "", occurrenceId = blankId), input.copy(type = null),
            input.copy(type = "BAD"), input.copy(dueAt = null), input.copy(occurrenceId = null),
            input.copy(occurrenceId = "0".repeat(64)), input.copy(occurrenceId = "property-a\nprivate"),
        ).forEach { malformed ->
            assertEquals(WorkerOutcome.FAILURE, ReminderWorker.execute(malformed, 32, true, 0, FakeReceiptStore(), logger) { posts++ })
        }
        assertEquals(0, posts)
        assertContains(logger.messages.last(), "\"occurrence\":\"missing\"")
        assertFalse(logger.messages.last().contains("property-a"))
    }
    @Test
    fun `failure classes and all notification types keep safe stable identity`() {
        val cases = listOf(
            SecurityException() to (FailureKind.PERMANENT to FailureCauseCode.SECURITY),
            IllegalArgumentException() to (FailureKind.PERMANENT to FailureCauseCode.INVALID_ARGUMENT),
            IllegalStateException() to (FailureKind.PERMANENT to FailureCauseCode.ILLEGAL_STATE),
            IOException() to (FailureKind.TRANSIENT to FailureCauseCode.IO),
            CancellationException() to (FailureKind.TRANSIENT to FailureCauseCode.CANCELLED),
            InterruptedException() to (FailureKind.TRANSIENT to FailureCauseCode.INTERRUPTED),
            RuntimeException() to (FailureKind.TRANSIENT to FailureCauseCode.UNKNOWN_RUNTIME),
            ExecutionException(IOException()) to (FailureKind.TRANSIENT to FailureCauseCode.IO),
            Exception() to (FailureKind.PERMANENT to FailureCauseCode.UNKNOWN),
        )
        cases.forEach { (error, expected) ->
            val actual = classifyReminderFailure(error)
            assertEquals(expected, actual.kind to actual.causeCode)
        }
        val labels = mapOf(
            InspectionScheduleType.ROUTINE to ("Routine" to "定期巡检"),
            InspectionScheduleType.ANNUAL to ("Annual" to "年度住宅检查"),
            InspectionScheduleType.INGOING to ("Ingoing" to "入住巡检"),
            InspectionScheduleType.EXIT to ("Exit" to "退租巡检"),
        )
        labels.forEach { (type, label) ->
            val route = ScheduleRoute("private-property", type)
            assertIs<DeliveryPlan.Retry>(reminderDeliveryPlan(33, false, route, now))
            val delivery = assertIs<DeliveryPlan.Notify>(reminderDeliveryPlan(32, false, route, now))
            val text = "${delivery.copy.title}\n${delivery.copy.body}"
            assertContains(text, label.first)
            assertContains(text, label.second)
            assertFalse(text.contains(route.propertyId))
            assertEquals(reminderRouteIntentSpec(route, now), delivery.intent)
        }
        val first = reminderRouteIntentSpec(ScheduleRoute("Aa", InspectionScheduleType.ROUTINE), now)
        val collision = reminderRouteIntentSpec(ScheduleRoute("BB", InspectionScheduleType.ROUTINE), now)
        assertNotEquals(first.data, collision.data)
        assertEquals(reminderOccurrenceId(ScheduleRoute("Aa", InspectionScheduleType.ROUTINE), now), first.notificationTag)
        assertEquals("myinspection://schedule/reminder/${first.notificationTag}", first.data)
        assertEquals(64, first.notificationTag.length)
        assertEquals(first.data.substringAfterLast('/'), first.notificationTag)
        assertEquals("Aa", first.propertyId)
        assertEquals("ROUTINE", first.inspectionType)
        assertEquals(NotificationIdentity(first.notificationTag, 0), reminderNotificationIdentity(first))
        assertFalse(first.data.contains("Aa"))
        var postedIdentity: NotificationIdentity? = null
        postReminderNotification(reminderNotificationIdentity(first), Unit) { tag, id, _ ->
            postedIdentity = NotificationIdentity(tag, id)
        }
        assertEquals(reminderNotificationIdentity(first), postedIdentity)
    }
    @Test
    fun `manifest declares only notification permission`() {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val manifest = factory.newDocumentBuilder().parse(locateAppRoot().resolve("src/main/AndroidManifest.xml").toFile())
        val permissions = manifest.getElementsByTagName("uses-permission")
        val names = (0 until permissions.length).map { index -> permissions.item(index).attributes.getNamedItemNS(ANDROID_NS, "name").nodeValue }
        assertEquals(listOf("android.permission.POST_NOTIFICATIONS"), names)
    }
    private fun spec(type: InspectionScheduleType, seconds: Long = 0) = factory.create(
        ScheduleRoute("property-a", type), now.plusSeconds(seconds).plusNanos(123),
    )
    private fun schedule(
        spec: ReminderSpec, store: ReceiptStore, logger: EventLogger,
        onResult: (RegistrationResult) -> Unit = {}, capture: ((EnqueueResult) -> Unit) -> Unit,
    ) = ReminderScheduler.schedule(spec, store, logger, onResult) { _, done -> capture(done) }
    private fun execute(
        spec: ReminderSpec, store: ReceiptStore, logger: EventLogger,
        attempt: Int = 0, granted: Boolean = true,
        notify: (DeliveryPlan.Notify) -> Unit = {},
    ) = ReminderWorker.execute(WorkerInput.from(spec), 33, granted, attempt, store, logger, notify)
    private fun enqueuedStore(spec: ReminderSpec) = FakeReceiptStore().apply { set(spec.occurrenceId, ReceiptState.ENQUEUED) }
    private class FakeReceiptStore : ReceiptStore {
        private val states = mutableMapOf<String, ReceiptState>()
        val corrupt = mutableSetOf<String>()
        var rejectedTarget: ReceiptState? = null
        var corruptOnRejectedTarget = false
        var throwOnRead = false
        var throwOnWrite = false
        override fun read(occurrenceId: String): ReceiptState = when {
            throwOnRead -> throw IllegalStateException("read failed")
            occurrenceId in corrupt -> ReceiptState.CORRUPT
            else -> states[occurrenceId] ?: ReceiptState.MISSING
        }
        override fun compareAndSet(
            occurrenceId: String, expected: Set<ReceiptState>, state: ReceiptState,
        ): Boolean {
            if (throwOnWrite) throw IllegalStateException("write failed")
            if (read(occurrenceId) !in expected) return false
            if (state == rejectedTarget) {
                if (corruptOnRejectedTarget) corrupt += occurrenceId
                return false
            }
            set(occurrenceId, state)
            return true
        }
        fun set(occurrenceId: String, state: ReceiptState) {
            if (state == ReceiptState.MISSING) states.remove(occurrenceId)
            else states[occurrenceId] = state
        }
    }
    private class FakeLogger : EventLogger {
        val records = mutableListOf<LogRecord>()
        val messages = mutableListOf<String>()
        override fun log(record: LogRecord) {
            records += record
            messages += reminderLogMessage(record)
        }
    }
    private fun locateAppRoot(): Path = generateSequence(
        Path.of(System.getProperty("user.dir")).toAbsolutePath(),
    ) { it.parent }.first { Files.isRegularFile(it.resolve("src/main/AndroidManifest.xml")) }
    private companion object { const val ANDROID_NS = "http://schemas.android.com/apk/res/android" }
}

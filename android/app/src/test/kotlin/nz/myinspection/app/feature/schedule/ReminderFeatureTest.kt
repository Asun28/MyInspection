package nz.myinspection.app.feature.schedule
import androidx.work.ExistingWorkPolicy
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

private typealias State = ReceiptState
private typealias Cause = FailureCauseCode
private typealias Outcome = WorkerOutcome
private typealias Type = InspectionScheduleType
private typealias Reg = RegistrationResult
class ReminderFeatureTest {
    private val now = Instant.parse("2026-08-01T00:00:00Z")
    private val factory = WorkSpecFactory()
    @Test
    fun `request is exact`() {
        val route = ScheduleRoute("property-a", Type.ROUTINE)
        val dueAt = now.plus(Duration.ofDays(2)).plusNanos(1)
        val spec = factory.create(route, dueAt)
        assertTrue(runCatching { factory.create(route.copy(propertyId = ""), dueAt) }.isFailure)
        assertEquals("schedule-reminder:${spec.occurrenceId}", spec.uniqueWorkName)
        assertFalse(spec.uniqueWorkName.contains(route.propertyId))
        listOf(factory.create(route, dueAt.plusNanos(1)), factory.create(route.copy(propertyId = "b"), dueAt),
            factory.create(route.copy(inspectionType = Type.ANNUAL), dueAt)).forEach { assertNotEquals(spec.uniqueWorkName, it.uniqueWorkName) }
        val enqueueClock = Clock.fixed(now.plus(Duration.ofDays(1)), ZoneOffset.UTC)
        var submissions = 0
        enqueueWorkManagerReminder(EnqueueSpec.from(spec), enqueueClock) { name, policy, request ->
            submissions++
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
        assertEquals(1, submissions)
    }
    @Test
    fun `registration is race safe`() {
        val spec = spec(Type.ROUTINE, 60)
        val store = Store()
        val logger = Logs()
        var completion: ((EnqueueResult) -> Unit)? = null
        var result: Reg? = null
        assertTrue(schedule(spec, store, logger, { result = it }) { completion = it })
        completion!!(EnqueueResult.Accepted)
        assertEquals(Reg.SUCCESS, result)
        assertEquals(State.ENQUEUED, store.read(spec.occurrenceId))
        var mismatches = 0; val mismatch = Store().apply { mismatchTarget = State.ENQUEUED }
        assertTrue(schedule(spec(Type.ROUTINE, 62), mismatch, logger) { mismatches++; it(EnqueueResult.Accepted) }); assertEquals(1, mismatches)
        val rejected = spec(Type.ROUTINE, 61)
        schedule(rejected, store, logger, { result = it }) {
            it(EnqueueResult.Rejected(IOException("private path")))
        }
        assertEquals(State.RETRYABLE, store.read(rejected.occurrenceId))
        assertEquals(Cause.IO, logger.records.last().causeCode)
        assertEquals(Reg.RETRYABLE_FAILURE, result)
        assertTrue(logger.records.last().retryable)
        assertFalse(logger.messages.last().contains("private path"))
        assertContains(logger.messages.last(), "\"event\":\"schedule-reminder\"")
        val raced = spec(Type.ROUTINE, 64)
        schedule(raced, store, logger, { result = it }) { completion = it }
        store.set(raced.occurrenceId, State.DELIVERY_UNCERTAIN)
        completion!!(EnqueueResult.Accepted)
        assertEquals(Reg.PERMANENT_FAILURE, result)
        assertLog(logger, LogStage.NOTIFY, LogError.DELIVERY_UNCERTAIN, Cause.DELIVERY_UNCERTAIN)
        val tainted = spec(Type.ROUTINE, 67); schedule(tainted, store, logger, { result = it }) { completion = it }
        store.set(tainted.occurrenceId, State.INDETERMINATE); requireNotNull(completion)(EnqueueResult.Accepted)
        assertEquals(Reg.PERMANENT_FAILURE, result); assertLog(logger, LogStage.RECEIPT_ENQUEUED, LogError.RECEIPT_WRITE_FAILED, Cause.STORAGE_WRITE)
        val conflicted = spec(Type.ROUTINE, 63); var rejectedDone: ((EnqueueResult) -> Unit)? = null
        schedule(conflicted, store, logger) { rejectedDone = it }; store.set(conflicted.occurrenceId, State.DELIVERY_UNCERTAIN)
        rejectedDone!!(EnqueueResult.Rejected(IOException())); assertLog(logger, LogStage.NOTIFY, LogError.DELIVERY_UNCERTAIN, Cause.DELIVERY_UNCERTAIN)
        logger.records.clear(); assertFalse(schedule(conflicted, store, logger) { error("") }); assertLog(logger, LogStage.NOTIFY, LogError.DELIVERY_UNCERTAIN, Cause.DELIVERY_UNCERTAIN)
        val unknown = spec(Type.ROUTINE, 65)
        schedule(unknown, store, logger, { result = it }) { it(EnqueueResult.Rejected(null)) }
        assertEquals(Cause.UNKNOWN, logger.records.last().causeCode)
        assertFalse(logger.records.last().retryable)
        assertEquals(Reg.PERMANENT_FAILURE, result)
        assertEquals(State.TERMINAL, store.read(unknown.occurrenceId))
        assertFalse(schedule(unknown, store, logger) { error("") })
        val thrown = spec(Type.ROUTINE, 66); assertTrue(ReminderScheduler.schedule(thrown, store, logger) { _, _ -> throw SecurityException() })
        assertEquals(State.TERMINAL, store.read(thrown.occurrenceId))
    }
    @Test
    fun `permission retry wins late rejection`() {
        val spec = spec(Type.ROUTINE)
        val store = Store()
        val logger = Logs()
        var completion: ((EnqueueResult) -> Unit)? = null
        var result: Reg? = null
        schedule(spec, store, logger, { result = it }) { completion = it }
        assertEquals(Outcome.RETRY, execute(spec, store, logger, granted = false))
        assertEquals(State.PERMISSION_RETRY, store.read(spec.occurrenceId))
        val storageLogs = logger.records.count { it.causeCode == Cause.STORAGE_WRITE }
        completion!!(EnqueueResult.Rejected(SecurityException()))
        assertEquals(Reg.SUCCESS, result)
        assertEquals(State.PERMISSION_RETRY, store.read(spec.occurrenceId))
        assertEquals(storageLogs, logger.records.count { it.causeCode == Cause.STORAGE_WRITE })
        assertFalse(schedule(spec, store, logger) { error("") })
        assertEquals(Outcome.SUCCESS, execute(spec, store, logger, granted = true))
        assertEquals(State.DELIVERED, store.read(spec.occurrenceId))
    }
    @Test
    fun `persistence failures fail closed`() {
        val raw = mutableMapOf<String, String>(); var commit = true
        fun store() = receiptStore({ raw[it] }) { id, value ->
            if (value == null) raw.remove(id) else raw[id] = value; commit
        }
        val persisted = store(); raw["bad"] = "bad"
        assertEquals(State.CORRUPT, persisted.read("bad"))
        assertEquals(WriteResult.Applied, persisted.compareAndSet("ok", setOf(State.MISSING), State.ENQUEUED))
        assertEquals(State.ENQUEUED, store().read("ok"))
        assertEquals(WriteResult.Mismatch(State.ENQUEUED), persisted.compareAndSet("ok", setOf(State.MISSING), State.DELIVERED))
        commit = false
        assertEquals(WriteResult.Failed, persisted.compareAndSet("ok", setOf(State.ENQUEUED), State.DELIVERED))
        assertEquals(State.INDETERMINATE, store().read("ok"))
        val throwing = receiptStore({ null }) { _, _ -> throw IllegalStateException() }
        assertEquals(WriteResult.Failed, throwing.compareAndSet("throw", setOf(State.MISSING), State.ENQUEUED))
        assertEquals(State.INDETERMINATE, store().read("throw"))
        val spec = spec(Type.ANNUAL, 1)
        val logger = Logs()
        val corrupt = Store().apply { corrupt += spec.occurrenceId }
        assertFalse(ReminderScheduler.schedule(spec, corrupt, logger) { _, _ -> error("") })
        assertEquals(Cause.STORAGE_CORRUPT, logger.records.last().causeCode)
        val rejected = Store().apply { failTarget = State.ENQUEUED }
        assertFalse(schedule(spec, rejected, logger) { error("") })
        assertEquals(Cause.STORAGE_WRITE, logger.records.last().causeCode)
        val shared = spec(Type.ANNUAL, 70)
        val sharedStore = Store()
        var owner: ((EnqueueResult) -> Unit)? = null
        var ownerResult: Reg? = null
        var followerResult: Reg? = null
        assertTrue(schedule(shared, sharedStore, logger, { ownerResult = it }) { owner = it })
        assertFalse(schedule(shared, sharedStore, logger, { followerResult = it }) { error("") })
        assertEquals(null, followerResult)
        owner!!(EnqueueResult.Rejected(SecurityException()))
        assertEquals(Reg.PERMANENT_FAILURE, ownerResult)
        assertEquals(ownerResult, followerResult)
        assertEquals(State.TERMINAL, sharedStore.read(shared.occurrenceId))
        val doubleFailureSpec = spec(Type.ANNUAL, 71)
        val doubleFailure = Store()
        var completion: ((EnqueueResult) -> Unit)? = null
        var quarantined: Reg? = null
        schedule(doubleFailureSpec, doubleFailure, logger, { quarantined = it }) { completion = it }
        doubleFailure.failTarget = State.RETRYABLE
        completion!!(EnqueueResult.Rejected(IOException()))
        assertEquals(Reg.PERMANENT_FAILURE, quarantined)
        assertFalse(schedule(doubleFailureSpec, doubleFailure, logger) { error("") })
        val retryRace = spec(Type.ANNUAL, 72)
        val retryStore = Store().apply { set(retryRace.occurrenceId, State.RETRYABLE) }
        var retryResult: Reg? = null
        val flight = requireNotNull(RegistrationFlights.join(retryRace.occurrenceId) { retryResult = it }.first)
        assertTrue(RegistrationFlights.begin(retryRace.occurrenceId, flight))
        assertFalse(RegistrationFlights.begin(retryRace.occurrenceId, flight))
        assertFalse(schedule(retryRace, retryStore, logger, { retryResult = it }) { error("") })
        assertEquals(State.RETRYABLE, retryStore.read(retryRace.occurrenceId))
        RegistrationFlights.finish(retryRace.occurrenceId, flight, false).forEach { it(Reg.RETRYABLE_FAILURE) }
        assertEquals(Reg.RETRYABLE_FAILURE, retryResult)
    }
    @Test
    fun `notify failures are safe`() {
        val spec = spec(Type.ROUTINE)
        val logger = Logs()
        val transient = enqueuedStore(spec)
        var transientPosts = 0
        assertEquals(Outcome.FAILURE, execute(spec, transient, logger) {
            transientPosts++
            throw IOException()
        })
        assertEquals(State.DELIVERY_UNCERTAIN, transient.read(spec.occurrenceId))
        assertEquals(Outcome.FAILURE, execute(spec, transient, logger) { transientPosts++ })
        assertEquals(1, transientPosts)
        assertEquals(Cause.DELIVERY_UNCERTAIN, logger.records.last().causeCode)
        val prePost = enqueuedStore(spec)
        assertEquals(Outcome.RETRY, execute(spec, prePost, logger) {
            throw PrePostNotificationException(IOException())
        })
        assertEquals(State.ENQUEUED, prePost.read(spec.occurrenceId))
        assertEquals(Outcome.FAILURE, execute(spec, failedStore(spec, State.ENQUEUED), logger) {
            throw PrePostNotificationException(IOException())
        })
        val permanent = enqueuedStore(spec)
        assertEquals(Outcome.RETRY, execute(spec, permanent, logger) { throw SecurityException() })
        assertEquals(State.PERMISSION_RETRY, permanent.read(spec.occurrenceId))
        assertEquals(Cause.PERMISSION_DENIED, logger.records.last().causeCode)
        var permanentPosts = 0
        assertEquals(Outcome.SUCCESS, execute(spec, permanent, logger) { permanentPosts++ })
        assertEquals(1, permanentPosts)
        val security = enqueuedStore(spec)
        assertEquals(Outcome.FAILURE, execute(spec, security, logger) { throw PrePostNotificationException(SecurityException()) })
        assertEquals(State.TERMINAL, security.read(spec.occurrenceId))
        assertEquals(Cause.SECURITY, logger.records.last().causeCode)
        val exhausted = enqueuedStore(spec)
        assertEquals(Outcome.FAILURE, execute(spec, exhausted, logger, attempt = 2) { throw SecurityException() })
        assertEquals(State.RETRYABLE, exhausted.read(spec.occurrenceId))
        val legacy = enqueuedStore(spec)
        assertEquals(Outcome.FAILURE, ReminderWorker.execute(WorkerInput.from(spec), 32, true, 0, legacy, logger) { throw SecurityException() })
        assertEquals(State.TERMINAL, legacy.read(spec.occurrenceId)); assertEquals(Cause.SECURITY, logger.records.last().causeCode)
        val denied = enqueuedStore(spec)
        assertEquals(Outcome.RETRY, execute(spec, denied, logger, granted = false))
        assertEquals(Outcome.FAILURE,
            execute(spec, failedStore(spec, State.PERMISSION_RETRY), logger, granted = false))
        assertEquals(Outcome.SUCCESS, execute(spec, denied, logger, granted = true))
        assertEquals(State.DELIVERED, denied.read(spec.occurrenceId))
        val deniedExhausted = enqueuedStore(spec)
        assertEquals(Outcome.FAILURE, execute(spec, deniedExhausted, logger, attempt = 2, granted = false))
        assertEquals(State.RETRYABLE, deniedExhausted.read(spec.occurrenceId))
    }
    @Test
    fun `delivery is idempotent`() {
        val spec = spec(Type.EXIT)
        val logger = Logs()
        val store = enqueuedStore(spec)
        var posted: DeliveryPlan.Notify? = null
        assertEquals(Outcome.SUCCESS, execute(spec, store, logger) { posted = it })
        assertEquals(State.DELIVERED, store.read(spec.occurrenceId))
        assertEquals(reminderRouteIntentSpec(spec.route, spec.dueAt), posted?.intent)
        var repeats = 0
        assertEquals(Outcome.SUCCESS, execute(spec, store, logger) { repeats++ })
        assertEquals(0, repeats)
        var stalePosts = 0
        assertEquals(Outcome.FAILURE, execute(spec, Store(), logger) { stalePosts++ })
        assertEquals(Cause.STORAGE_MISSING, logger.records.last().causeCode)
        val retryable = Store().apply { set(spec.occurrenceId, State.RETRYABLE) }
        assertEquals(Outcome.FAILURE, execute(spec, retryable, logger) { stalePosts++ })
        assertEquals(0, stalePosts)
        assertEquals(Cause.RETRYABLE_STATE, logger.records.last().causeCode)
        val rejected = failedStore(spec, State.DELIVERED)
        var posts = 0
        assertEquals(Outcome.FAILURE, execute(spec, rejected, logger) { posts++ })
        assertEquals(1, posts)
        assertEquals(State.DELIVERED, rejected.read(spec.occurrenceId)); assertEquals(State.INDETERMINATE, rejected.read(spec.occurrenceId))
        assertEquals(Cause.STORAGE_WRITE, logger.records.last().causeCode)
        assertEquals(Outcome.FAILURE, execute(spec, rejected, logger) { posts++ })
        assertEquals(1, posts)
        var recovery: Reg? = null
        assertFalse(schedule(spec, rejected, logger, { recovery = it }) { error("") })
        assertEquals(Reg.PERMANENT_FAILURE, recovery)
        val throwing = failedStore(spec, State.DELIVERY_UNCERTAIN)
        var alerts = 0
        assertEquals(Outcome.FAILURE, execute(spec, throwing, logger) { alerts++ })
        assertEquals(0, alerts)
    }
    @Test
    fun `invalid inputs do not post`() {
        val spec = spec(Type.INGOING)
        val logger = Logs()
        var posts = 0
        val corrupt = Store().apply { this.corrupt += spec.occurrenceId }
        assertEquals(Outcome.FAILURE, execute(spec, corrupt, logger) { posts++ })
        assertEquals(Cause.STORAGE_CORRUPT, logger.records.last().causeCode)
        val input = WorkerInput.from(spec)
        val forgedStore = Store()
        var forgedResult: Reg? = null
        listOf(spec.copy(occurrenceId = "0".repeat(64)), spec.copy(uniqueWorkName = this.spec(Type.INGOING, 1).uniqueWorkName)).forEach {
            assertFalse(schedule(it, forgedStore, logger, { forgedResult = it }) { posts++ }); assertEquals(State.MISSING, forgedStore.read(it.occurrenceId))
        }
        assertEquals(Reg.PERMANENT_FAILURE, forgedResult)
        assertEquals(Cause.INVALID_INPUT, logger.records.last().causeCode)
        val blankId = reminderOccurrenceId(ScheduleRoute("", spec.route.inspectionType), spec.dueAt)
        listOf(
            input.copy(propertyId = null), input.copy(propertyId = "", occurrenceId = blankId), input.copy(type = null),
            input.copy(type = "BAD"), input.copy(dueAt = null), input.copy(occurrenceId = null),
            input.copy(occurrenceId = "0".repeat(64)), input.copy(occurrenceId = "property-a\nprivate"),
        ).forEach { malformed ->
            assertEquals(Outcome.FAILURE, ReminderWorker.execute(malformed, 32, true, 0, Store(), logger) { posts++ })
        }
        assertEquals(0, posts)
        assertContains(logger.messages.last(), "\"occurrence\":\"missing\"")
        assertFalse(logger.messages.last().contains("property-a"))
    }
    @Test
    fun `failure and identity rules`() {
        val cases = listOf(
            SecurityException() to (FailureKind.PERMANENT to Cause.SECURITY),
            IllegalArgumentException() to (FailureKind.PERMANENT to Cause.INVALID_ARGUMENT),
            IllegalStateException() to (FailureKind.PERMANENT to Cause.ILLEGAL_STATE),
            IOException() to (FailureKind.TRANSIENT to Cause.IO),
            CancellationException() to (FailureKind.TRANSIENT to Cause.CANCELLED),
            InterruptedException() to (FailureKind.TRANSIENT to Cause.INTERRUPTED),
            ExecutionException(IOException()) to (FailureKind.TRANSIENT to Cause.IO),
            Exception() to (FailureKind.PERMANENT to Cause.UNKNOWN),
        )
        cases.forEach { (error, expected) ->
            val actual = classifyReminderFailure(error)
            assertEquals(expected, actual.kind to actual.causeCode)
        }
        listOf(RuntimeException(), NullPointerException(), UnsupportedOperationException()).forEach {
            assertEquals(FailureKind.PERMANENT, classifyReminderFailure(it).kind)
        }
        val labels = mapOf(
            Type.ROUTINE to ("Routine" to "定期巡检"),
            Type.ANNUAL to ("Annual" to "年度住宅检查"),
            Type.INGOING to ("Ingoing" to "入住巡检"),
            Type.EXIT to ("Exit" to "退租巡检"),
        )
        labels.forEach { (type, label) ->
            val route = ScheduleRoute("private-property", type)
            assertIs<DeliveryPlan.Retry>(reminderDeliveryPlan(33, false, route, now))
            val delivery = assertIs<DeliveryPlan.Notify>(reminderDeliveryPlan(32, false, route, now))
            assertContains("${delivery.copy.title}\n${delivery.copy.body}", label.first)
            assertContains(delivery.copy.body, label.second)
            assertFalse(delivery.copy.body.contains(route.propertyId))
            assertTrue(delivery.onlyAlertOnce)
            assertEquals(reminderRouteIntentSpec(route, now), delivery.intent)
        }
        val first = reminderRouteIntentSpec(ScheduleRoute("Aa", Type.ROUTINE), now)
        val collision = reminderRouteIntentSpec(ScheduleRoute("BB", Type.ROUTINE), now)
        assertNotEquals(first.data, collision.data)
        assertEquals(reminderOccurrenceId(ScheduleRoute("Aa", Type.ROUTINE), now), first.notificationTag)
        assertEquals(NotificationIdentity(first.notificationTag, 0), reminderNotificationIdentity(first))
        var postedIdentity: NotificationIdentity? = null
        postReminderNotification(reminderNotificationIdentity(first), Unit) { tag, id, _ -> postedIdentity = NotificationIdentity(tag, id) }
        assertEquals(reminderNotificationIdentity(first), postedIdentity)
    }
    @Test
    fun `manifest permission is exact`() {
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
        ScheduleRoute("property-a", type), now.plusSeconds(seconds).plusNanos(123))
    private fun schedule(
        spec: ReminderSpec, store: ReceiptStore, logger: EventLogger,
        onResult: (Reg) -> Unit = {}, capture: ((EnqueueResult) -> Unit) -> Unit,
    ) = ReminderScheduler.schedule(spec, store, logger, onResult) { _, done -> capture(done) }
    private fun execute(
        spec: ReminderSpec, store: ReceiptStore, logger: EventLogger,
        attempt: Int = 0, granted: Boolean = true,
        notify: (DeliveryPlan.Notify) -> Unit = {},
    ) = ReminderWorker.execute(WorkerInput.from(spec), 33, granted, attempt, store, logger, notify)
    private fun enqueuedStore(spec: ReminderSpec) = Store().apply { set(spec.occurrenceId, State.ENQUEUED) }
    private fun failedStore(spec: ReminderSpec, target: State) = enqueuedStore(spec).apply { failTarget = target }
    private fun assertLog(logger: Logs, stage: LogStage, error: LogError, cause: Cause) = assertEquals(Triple(stage, error, cause), logger.records.last().let { Triple(it.stage, it.errorCode, it.causeCode) })
    private class Store : ReceiptStore {
        private val states = mutableMapOf<String, ReceiptState>()
        private val failed = mutableSetOf<String>()
        private val visible = mutableSetOf<String>()
        val corrupt = mutableSetOf<String>()
        var failTarget: State? = null
        var mismatchTarget: State? = null
        override fun read(id: String): State = when {
            id in visible -> states.getValue(id).also { visible -= id }
            id in failed -> State.INDETERMINATE
            id in corrupt -> State.CORRUPT
            else -> states[id] ?: State.MISSING
        }
        override fun compareAndSet(id: String, expected: Set<State>, state: State): WriteResult {
            if (read(id) !in expected) return WriteResult.Mismatch(read(id))
            if (state == mismatchTarget) { set(id, state); return WriteResult.Mismatch(state) }
            if (state == failTarget) {
                set(id, state)
                failed += id; visible += id
                return WriteResult.Failed
            }
            set(id, state)
            return WriteResult.Applied
        }
        fun set(occurrenceId: String, state: State) {
            if (state == State.MISSING) states.remove(occurrenceId)
            else states[occurrenceId] = state
        }
    }
    private class Logs : EventLogger {
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

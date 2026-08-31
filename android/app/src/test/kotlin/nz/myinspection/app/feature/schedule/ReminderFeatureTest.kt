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
import kotlin.test.assertContains as has
import kotlin.test.assertEquals as eq
import kotlin.test.assertFalse as no
import kotlin.test.assertIs as isA
import kotlin.test.assertNotEquals as ne
import kotlin.test.assertTrue as yes
import nz.myinspection.core.schedule.InspectionScheduleType
private typealias State = ReceiptState
private typealias Cause = FailureCauseCode
private typealias Outcome = WorkerOutcome
private typealias Type = InspectionScheduleType
private typealias Reg = RegistrationResult
private val ReminderSpec.id get() = occurrenceId
class ReminderFeatureTest {
    private val now = Instant.parse("2026-08-01T00:00:00Z")
    private val factory = WorkSpecFactory()
    @Test
    fun `request is exact`() {
        val route = ScheduleRoute("property-a", Type.ROUTINE)
        val dueAt = now.plus(Duration.ofDays(2)).plusNanos(1)
        val spec = factory.create(route, dueAt)
        yes(runCatching { factory.create(route.copy(propertyId = ""), dueAt) }.isFailure)
        eq("schedule-reminder:${spec.id}", spec.uniqueWorkName)
        no(spec.uniqueWorkName.contains(route.propertyId))
        listOf(factory.create(route, dueAt.plusNanos(1)), factory.create(route.copy(propertyId = "b"), dueAt),
            factory.create(route.copy(inspectionType = Type.ANNUAL), dueAt)).forEach { ne(spec.uniqueWorkName, it.uniqueWorkName) }
        val enqueueClock = Clock.fixed(now.plus(Duration.ofDays(1)), ZoneOffset.UTC)
        var calls = 0
        enqueueWorkManagerReminder(EnqueueSpec.from(spec), enqueueClock) { name, policy, request ->
            calls++
            val work = request.workSpec
            eq(spec.uniqueWorkName, name)
            eq(ExistingWorkPolicy.KEEP, policy)
            eq(ReminderWorker::class.java.name, work.workerClassName)
            eq(Duration.ofDays(1).toMillis() + 1, work.initialDelay)
            eq(route.propertyId, work.input.getString(WorkKeys.PROPERTY_ID))
            eq(route.inspectionType.name, work.input.getString(WorkKeys.INSPECTION_TYPE))
            eq(dueAt.toString(), work.input.getString(WorkKeys.DUE_AT_INSTANT))
            eq(spec.id, work.input.getString(WorkKeys.OCCURRENCE_ID))
        }
        eq(1, calls)
        eq(listOf(0L, 1L, 1L), listOf(now.minusNanos(1), now.plusNanos(1), now.plusMillis(1)).map { reminderDelayMillis(now, it) })
    }
    @Test
    fun `registration is race safe`() {
        val spec = spec(Type.ROUTINE, 60)
        val store = Store()
        val logger = Logs()
        var done: ((EnqueueResult) -> Unit)? = null
        var result: Reg? = null
        yes(schedule(spec, store, logger, { result = it }) { done = it })
        done!!(EnqueueResult.Accepted)
        eq(Reg.SUCCESS, result)
        eq(State.ENQUEUED, store.read(spec.id))
        var mismatches = 0; val mismatch = Store().apply { mismatchTarget = State.ENQUEUED }
        yes(schedule(spec(Type.ROUTINE, 62), mismatch, logger) { mismatches++; it(EnqueueResult.Accepted) }); eq(1, mismatches)
        val rejected = spec(Type.ROUTINE, 61)
        schedule(rejected, store, logger, { result = it }) {
            it(EnqueueResult.Rejected(IOException("private path")))
        }
        eq(State.RETRYABLE, store.read(rejected.id))
        eq(Cause.IO, logger.cause)
        eq(Reg.RETRYABLE_FAILURE, result)
        yes(logger.records.last().retryable)
        no(logger.messages.last().contains("private path"))
        has(logger.messages.last(), "\"event\":\"schedule-reminder\"")
        val raced = spec(Type.ROUTINE, 64)
        schedule(raced, store, logger, { result = it }) { done = it }
        store.set(raced.id, State.DELIVERY_UNCERTAIN)
        done!!(EnqueueResult.Accepted)
        eq(Reg.PERMANENT_FAILURE, result)
        assertLog(logger, LogStage.NOTIFY, LogError.DELIVERY_UNCERTAIN, Cause.DELIVERY_UNCERTAIN)
        val tainted = spec(Type.ROUTINE, 67); schedule(tainted, store, logger, { result = it }) { done = it }
        store.set(tainted.id, State.INDETERMINATE); requireNotNull(done)(EnqueueResult.Accepted)
        eq(Reg.PERMANENT_FAILURE, result); assertLog(logger, LogStage.RECEIPT_ENQUEUED, LogError.RECEIPT_WRITE_FAILED, Cause.STORAGE_WRITE)
        val conflicted = spec(Type.ROUTINE, 63); var rejectedDone: ((EnqueueResult) -> Unit)? = null
        schedule(conflicted, store, logger) { rejectedDone = it }; store.set(conflicted.id, State.DELIVERY_UNCERTAIN)
        rejectedDone!!(EnqueueResult.Rejected(IOException())); assertLog(logger, LogStage.NOTIFY, LogError.DELIVERY_UNCERTAIN, Cause.DELIVERY_UNCERTAIN)
        logger.records.clear(); no(schedule(conflicted, store, logger) { error("") }); assertLog(logger, LogStage.NOTIFY, LogError.DELIVERY_UNCERTAIN, Cause.DELIVERY_UNCERTAIN)
        val unknown = spec(Type.ROUTINE, 65)
        schedule(unknown, store, logger, { result = it }) { it(EnqueueResult.Rejected(null)) }
        eq(Cause.UNKNOWN, logger.cause)
        no(logger.records.last().retryable)
        eq(Reg.PERMANENT_FAILURE, result)
        eq(State.TERMINAL, store.read(unknown.id))
        no(schedule(unknown, store, logger) { error("") })
        val joined = spec(Type.ROUTINE, 68); val coordinator = RegistrationCoordinator(store) {}; var joinedDone: ((Reg) -> Unit)? = null; val seen = mutableListOf<Reg>(); val failure = IllegalStateException("same")
        coordinator.register(joined.id, { throw failure }) { joinedDone = it }; coordinator.register(joined.id, { throw failure }) { error("duplicate") }; coordinator.register(joined.id, { seen += it }) { error("duplicate") }
        eq(failure, runCatching { joinedDone!!(Reg.SUCCESS) }.exceptionOrNull()); eq(listOf(Reg.SUCCESS), seen)
        val callback = spec(Type.ROUTINE, 69); val logCount = logger.records.size
        yes(runCatching { ReminderScheduler.schedule(callback, Store(), logger, { error("callback") }) { _, done -> done(EnqueueResult.Accepted) } }.exceptionOrNull() is IllegalStateException); eq(logCount, logger.records.size)
        val thrown = spec(Type.ROUTINE, 66); yes(ReminderScheduler.schedule(thrown, store, logger) { _, _ -> throw SecurityException() })
        eq(State.TERMINAL, store.read(thrown.id))
    }
    @Test
    fun `permission retry wins late rejection`() {
        val spec = spec(Type.ROUTINE)
        val store = Store()
        val logger = Logs()
        var done: ((EnqueueResult) -> Unit)? = null
        var result: Reg? = null
        schedule(spec, store, logger, { result = it }) { done = it }
        eq(Outcome.RETRY, execute(spec, store, logger, granted = false))
        eq(State.PERMISSION_RETRY, store.read(spec.id))
        val storageLogs = logger.records.count { it.causeCode == Cause.STORAGE_WRITE }
        done!!(EnqueueResult.Rejected(SecurityException()))
        eq(Reg.SUCCESS, result)
        eq(State.PERMISSION_RETRY, store.read(spec.id))
        eq(storageLogs, logger.records.count { it.causeCode == Cause.STORAGE_WRITE })
        no(schedule(spec, store, logger) { error("") })
        eq(Outcome.SUCCESS, execute(spec, store, logger, granted = true))
        eq(State.DELIVERED, store.read(spec.id))
    }
    @Test
    fun `persistence failures fail closed`() {
        val raw = mutableMapOf<String, String>(); var commit = true
        fun store() = receiptStore({ raw[it] }) { id, value ->
            if (value == null) raw.remove(id) else raw[id] = value; commit
        }
        val persisted = store(); raw["bad"] = "bad"
        eq(State.CORRUPT, persisted.read("bad"))
        eq(WriteResult.Applied, persisted.compareAndSet("ok", setOf(State.MISSING), State.ENQUEUED))
        eq(State.ENQUEUED, store().read("ok"))
        eq(WriteResult.Mismatch(State.ENQUEUED), persisted.compareAndSet("ok", setOf(State.MISSING), State.DELIVERED))
        commit = false
        eq(WriteResult.Failed, persisted.compareAndSet("ok", setOf(State.ENQUEUED), State.DELIVERED))
        eq(State.INDETERMINATE, store().read("ok"))
        val throwing = receiptStore({ null }) { _, _ -> throw IllegalStateException() }
        eq(WriteResult.Failed, throwing.compareAndSet("throw", setOf(State.MISSING), State.ENQUEUED))
        eq(State.INDETERMINATE, store().read("throw"))
        val spec = spec(Type.ANNUAL, 1)
        val logger = Logs()
        val corrupt = Store().apply { corrupt += spec.id }
        no(ReminderScheduler.schedule(spec, corrupt, logger) { _, _ -> error("") })
        eq(Cause.STORAGE_CORRUPT, logger.cause)
        val rejected = Store().apply { failTarget = State.ENQUEUED }
        no(schedule(spec, rejected, logger) { error("") })
        eq(Cause.STORAGE_WRITE, logger.cause)
        val shared = spec(Type.ANNUAL, 70)
        val sharedStore = Store()
        var owner: ((EnqueueResult) -> Unit)? = null
        var ownerResult: Reg? = null
        var followerResult: Reg? = null
        yes(schedule(shared, sharedStore, logger, { ownerResult = it }) { owner = it })
        no(schedule(shared, sharedStore, logger, { followerResult = it }) { error("") })
        eq(null, followerResult)
        owner!!(EnqueueResult.Rejected(SecurityException()))
        eq(Reg.PERMANENT_FAILURE, ownerResult)
        eq(ownerResult, followerResult)
        eq(State.TERMINAL, sharedStore.read(shared.id))
        val doubleFailureSpec = spec(Type.ANNUAL, 71)
        val doubleFailure = Store()
        var done: ((EnqueueResult) -> Unit)? = null
        var quarantined: Reg? = null
        schedule(doubleFailureSpec, doubleFailure, logger, { quarantined = it }) { done = it }
        doubleFailure.failTarget = State.RETRYABLE
        done!!(EnqueueResult.Rejected(IOException()))
        eq(Reg.PERMANENT_FAILURE, quarantined)
        no(schedule(doubleFailureSpec, doubleFailure, logger) { error("") })
        val retryRace = spec(Type.ANNUAL, 72)
        val retryStore = Store().apply { set(retryRace.id, State.RETRYABLE) }
        var retryResult: Reg? = null
        val flight = requireNotNull(RegistrationFlights.join(retryRace.id) { retryResult = it }.first)
        yes(RegistrationFlights.begin(retryRace.id, flight))
        no(RegistrationFlights.begin(retryRace.id, flight))
        no(schedule(retryRace, retryStore, logger, { retryResult = it }) { error("") })
        eq(State.RETRYABLE, retryStore.read(retryRace.id))
        RegistrationFlights.finish(retryRace.id, flight, false).forEach { it(Reg.RETRYABLE_FAILURE) }
        eq(Reg.RETRYABLE_FAILURE, retryResult)
    }
    @Test
    fun `notify failures are safe`() {
        val spec = spec(Type.ROUTINE)
        val logger = Logs()
        val transient = queued(spec)
        var transientPosts = 0
        eq(Outcome.FAILURE, execute(spec, transient, logger) {
            transientPosts++
            throw IOException()
        })
        eq(State.DELIVERY_UNCERTAIN, transient.read(spec.id))
        eq(Outcome.FAILURE, execute(spec, transient, logger) { transientPosts++ })
        eq(1, transientPosts)
        eq(Cause.DELIVERY_UNCERTAIN, logger.cause)
        val prePost = queued(spec)
        eq(Outcome.RETRY, execute(spec, prePost, logger) {
            throw PrePostNotificationException(IOException())
        })
        eq(State.DELIVERY_RETRY, prePost.read(spec.id))
        eq(Outcome.FAILURE, execute(spec, failed(spec, State.DELIVERY_RETRY), logger) {
            throw PrePostNotificationException(IOException())
        })
        val permanent = queued(spec)
        eq(Outcome.RETRY, execute(spec, permanent, logger) { throw SecurityException() })
        eq(State.PERMISSION_RETRY, permanent.read(spec.id))
        eq(Cause.PERMISSION_DENIED, logger.cause)
        var permanentPosts = 0
        eq(Outcome.SUCCESS, execute(spec, permanent, logger) { permanentPosts++ })
        eq(1, permanentPosts)
        val security = queued(spec)
        eq(Outcome.FAILURE, execute(spec, security, logger) { throw PrePostNotificationException(SecurityException()) })
        eq(State.TERMINAL, security.read(spec.id))
        eq(Cause.SECURITY, logger.cause)
        val exhausted = queued(spec)
        eq(Outcome.RETRY, execute(spec, exhausted, logger, attempt = 2) { throw SecurityException() })
        eq(State.PERMISSION_RETRY, exhausted.read(spec.id))
        var result: Reg? = null; var calls = 0
        no(schedule(spec, exhausted, logger, { result = it }) { calls++ })
        eq(Reg.SUCCESS, result); eq(0, calls)
        eq(Outcome.FAILURE, execute(spec, exhausted, logger, attempt = 3) { throw SecurityException() })
        eq(State.TERMINAL, exhausted.read(spec.id))
        no(schedule(spec, exhausted, logger, { result = it }) { calls++ })
        eq(Reg.PERMANENT_FAILURE, result); eq(0, calls)
        val handoff = queued(spec)
        eq(Outcome.RETRY, execute(spec, handoff, logger, attempt = 2) { throw PrePostNotificationException(IOException()) })
        eq(State.DELIVERY_RETRY, handoff.read(spec.id))
        no(schedule(spec, handoff, logger, { result = it }) { calls++ })
        eq(Reg.SUCCESS, result); eq(0, calls)
        eq(Outcome.FAILURE, execute(spec, handoff, logger, attempt = 3) { throw PrePostNotificationException(IOException()) })
        eq(State.TERMINAL, handoff.read(spec.id))
        val cross = queued(spec, State.DELIVERY_RETRY); eq(Outcome.FAILURE, execute(spec, cross, logger, attempt = 3, granted = false)); eq(State.TERMINAL, cross.read(spec.id))
        val legacy = queued(spec)
        eq(Outcome.FAILURE, ReminderWorker.execute(WorkerInput.from(spec), 32, true, 0, legacy, logger) { throw SecurityException() })
        eq(State.TERMINAL, legacy.read(spec.id)); eq(Cause.SECURITY, logger.cause)
        val denied = queued(spec)
        eq(Outcome.RETRY, execute(spec, denied, logger, granted = false))
        eq(Outcome.FAILURE,
            execute(spec, failed(spec, State.PERMISSION_RETRY), logger, granted = false))
        eq(Outcome.SUCCESS, execute(spec, denied, logger, granted = true))
        eq(State.DELIVERED, denied.read(spec.id))
    }
    @Test
    fun `delivery is idempotent`() {
        val spec = spec(Type.EXIT)
        val logger = Logs()
        val store = queued(spec)
        var posted: DeliveryPlan.Notify? = null
        eq(Outcome.SUCCESS, execute(spec, store, logger) { posted = it })
        eq(State.DELIVERED, store.read(spec.id))
        eq(reminderRouteIntentSpec(spec.route, spec.dueAt), posted?.intent)
        var repeats = 0
        eq(Outcome.SUCCESS, execute(spec, store, logger) { repeats++ })
        eq(0, repeats)
        var stalePosts = 0
        eq(Outcome.FAILURE, execute(spec, Store(), logger) { stalePosts++ })
        eq(Cause.STORAGE_MISSING, logger.cause)
        val retryable = Store().apply { set(spec.id, State.RETRYABLE) }
        eq(Outcome.FAILURE, execute(spec, retryable, logger) { stalePosts++ })
        eq(0, stalePosts)
        eq(Cause.RETRYABLE_STATE, logger.cause)
        val rejected = failed(spec, State.DELIVERED)
        var posts = 0
        eq(Outcome.FAILURE, execute(spec, rejected, logger) { posts++ })
        eq(1, posts)
        eq(State.DELIVERED, rejected.read(spec.id)); eq(State.INDETERMINATE, rejected.read(spec.id))
        eq(Cause.STORAGE_WRITE, logger.cause)
        eq(Outcome.FAILURE, execute(spec, rejected, logger) { posts++ })
        eq(1, posts)
        var recovery: Reg? = null
        no(schedule(spec, rejected, logger, { recovery = it }) { error("") })
        eq(Reg.PERMANENT_FAILURE, recovery)
        val throwing = failed(spec, State.DELIVERY_UNCERTAIN)
        var alerts = 0
        eq(Outcome.FAILURE, execute(spec, throwing, logger) { alerts++ })
        eq(0, alerts)
    }
    @Test
    fun `invalid inputs do not post`() {
        val spec = spec(Type.INGOING)
        val logger = Logs()
        var posts = 0
        val corrupt = Store().apply { this.corrupt += spec.id }
        eq(Outcome.FAILURE, execute(spec, corrupt, logger) { posts++ })
        eq(Cause.STORAGE_CORRUPT, logger.cause)
        val input = WorkerInput.from(spec)
        val forgedStore = Store()
        var forgedResult: Reg? = null
        listOf(spec.copy(occurrenceId = "0".repeat(64)), spec.copy(uniqueWorkName = this.spec(Type.INGOING, 1).uniqueWorkName)).forEach {
            no(schedule(it, forgedStore, logger, { forgedResult = it }) { posts++ }); eq(State.MISSING, forgedStore.read(it.id))
        }
        eq(Reg.PERMANENT_FAILURE, forgedResult)
        eq(Cause.INVALID_INPUT, logger.cause)
        val blankId = reminderOccurrenceId(ScheduleRoute("", spec.route.inspectionType), spec.dueAt)
        listOf(
            input.copy(propertyId = null), input.copy(propertyId = "", occurrenceId = blankId), input.copy(type = null),
            input.copy(type = "BAD"), input.copy(dueAt = null), input.copy(occurrenceId = null),
            input.copy(occurrenceId = "0".repeat(64)), input.copy(occurrenceId = "property-a\nprivate"),
        ).forEach { malformed ->
            eq(Outcome.FAILURE, ReminderWorker.execute(malformed, 32, true, 0, Store(), logger) { posts++ })
        }
        eq(0, posts)
        has(logger.messages.last(), "\"occurrence\":\"missing\"")
        no(logger.messages.last().contains("property-a"))
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
            eq(expected, actual.kind to actual.causeCode)
        }
        listOf(RuntimeException(), NullPointerException(), UnsupportedOperationException()).forEach {
            eq(FailureKind.PERMANENT, classifyReminderFailure(it).kind)
        }
        val labels = mapOf(
            Type.ROUTINE to ("Routine" to "定期巡检"),
            Type.ANNUAL to ("Annual" to "年度住宅检查"),
            Type.INGOING to ("Ingoing" to "入住巡检"),
            Type.EXIT to ("Exit" to "退租巡检"),
        )
        labels.forEach { (type, label) ->
            val route = ScheduleRoute("private-property", type)
            isA<DeliveryPlan.Retry>(reminderDeliveryPlan(33, false, route, now))
            val delivery = isA<DeliveryPlan.Notify>(reminderDeliveryPlan(32, false, route, now))
            has("${delivery.copy.title}\n${delivery.copy.body}", label.first)
            has(delivery.copy.body, label.second)
            no(delivery.copy.body.contains(route.propertyId))
            yes(delivery.onlyAlertOnce)
            eq(reminderRouteIntentSpec(route, now), delivery.intent)
        }
        val first = reminderRouteIntentSpec(ScheduleRoute("Aa", Type.ROUTINE), now)
        val collision = reminderRouteIntentSpec(ScheduleRoute("BB", Type.ROUTINE), now)
        ne(first.data, collision.data)
        eq(reminderOccurrenceId(ScheduleRoute("Aa", Type.ROUTINE), now), first.notificationTag)
        eq(NotificationIdentity(first.notificationTag, 0), reminderNotificationIdentity(first))
        var postedIdentity: NotificationIdentity? = null
        postReminderNotification(reminderNotificationIdentity(first), Unit) { tag, id, _ -> postedIdentity = NotificationIdentity(tag, id) }
        eq(reminderNotificationIdentity(first), postedIdentity)
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
        eq(listOf("android.permission.POST_NOTIFICATIONS"), names)
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
    private fun queued(spec: ReminderSpec, state: State = State.ENQUEUED) = Store().apply { set(spec.id, state) }
    private fun failed(spec: ReminderSpec, target: State) = queued(spec).apply { failTarget = target }
    private val Logs.cause get() = records.last().causeCode
    private fun assertLog(logger: Logs, stage: LogStage, error: LogError, cause: Cause) = eq(Triple(stage, error, cause), logger.records.last().let { Triple(it.stage, it.errorCode, it.causeCode) })
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

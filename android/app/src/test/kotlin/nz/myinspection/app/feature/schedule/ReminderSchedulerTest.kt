package nz.myinspection.app.feature.schedule

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import nz.myinspection.app.feature.schedule.ReminderPhase.ADMISSION_PENDING
import nz.myinspection.app.feature.schedule.ReminderPhase.DELIVERED
import nz.myinspection.app.feature.schedule.ReminderPhase.DELIVERY_UNCERTAIN
import nz.myinspection.app.feature.schedule.ReminderPhase.ENQUEUED
import nz.myinspection.app.feature.schedule.ReminderPhase.PERMISSION_BLOCKED
import nz.myinspection.app.feature.schedule.ReminderPhase.QUARANTINED
import nz.myinspection.app.feature.schedule.ReminderPhase.RETRYABLE
import nz.myinspection.app.feature.schedule.ReminderPhase.TERMINAL
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.CALLBACK_CONFIRMED_ADMISSION
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.ENQUEUE_CALLBACK_ERROR
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.ENQUEUE_CALLBACK_NULL
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.ENQUEUE_CALLBACK_THROWABLE
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.ENQUEUE_CALLBACK_TIMEOUT
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.ENQUEUE_SUBMIT_FATAL
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.ENQUEUE_SUBMIT_TRANSIENT
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.GENERATION_SUPERSEDED
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.INVALID_ROUTE
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.OCCURRENCE_CLOSED
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.PERMISSION_NOT_GRANTED
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.RECEIPT_QUARANTINED
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.RECEIPT_WRITE_UNCERTAIN
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.RETAINED_WORK_BLOCKED
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.RETAINED_WORK_CANCELLED
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.RETAINED_WORK_DUPLICATE
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.RETAINED_WORK_ENQUEUED
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.RETAINED_WORK_FAILED
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.RETAINED_WORK_ID_MISMATCH
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.RETAINED_WORK_QUERY_FAILED
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.RETAINED_WORK_SUCCEEDED_WITHOUT_RECEIPT
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.WORKER_CONFIRMED_ADMISSION
import nz.myinspection.app.feature.schedule.ReminderRegistrationOutcome.ADMITTED
import nz.myinspection.app.feature.schedule.ReminderRegistrationOutcome.PERMANENT_FAILURE
import nz.myinspection.app.feature.schedule.ReminderRegistrationOutcome.RETRYABLE_FAILURE
import nz.myinspection.app.feature.schedule.ReminderRegistrationOutcome.SKIPPED
import nz.myinspection.core.schedule.InspectionScheduleType.ROUTINE

/**
 * Black box acceptance tests for the scheduler, driven through the same ports the production
 * factory injects, over the real receipt store on an in memory preference file. The occurrence
 * digest and both generation work ids are the golden vectors frozen by the contracts card, so a
 * drifting identity is caught rather than mirrored, and a test that needs the matching worker to
 * have run runs the production runner over the same store. The fixture nests because the merged
 * worker tests own these names at package level.
 */
class ReminderSchedulerTest {
    @Test
    fun `a fresh occurrence reserves generation zero and enqueues the request the worker accepts`() {
        val fixture = Fixture()

        val cause = fixture.register()

        assertEquals(CALLBACK_CONFIRMED_ADMISSION, cause)
        assertEquals(ENQUEUED, fixture.phase())
        val submission = fixture.enqueue.submissions.single()
        assertEquals(UNIQUE_WORK_NAME, submission.name)
        assertEquals(ExistingWorkPolicy.KEEP, submission.policy)
        assertEquals(listOf(UNIQUE_WORK_NAME), fixture.query.names)
        assertEquals(WORK_ID_0, submission.request.id)
        assertEquals(60_000L, submission.request.workSpec.initialDelay)
        assertEquals(ReminderWorker::class.java.name, submission.request.workSpec.workerClassName)
        // The request is right only if the merged Worker accepts it, so the runner reads it back.
        assertEquals(ReminderRunOutcome.SUCCESS, fixture.runWorker(submission.request))
        assertEquals(DELIVERED, fixture.phase())
        assertEquals(
            ReminderRegistrationRecord(OCCURRENCE_ID, ROUTINE, 0L, CALLBACK_CONFIRMED_ADMISSION),
            fixture.diagnostics.records.single(),
        )
    }

    @Test
    fun `the initial delay rounds up below a millisecond and clamps instead of overflowing`() {
        val cases = listOf(
            Triple("one nanosecond after the due instant", DUE_AT.plusNanos(1), 0L),
            Triple("exactly the due instant", DUE_AT, 0L),
            Triple("one nanosecond before", DUE_AT.minusNanos(1), 1L),
            Triple("just under one millisecond before", DUE_AT.minusNanos(999_999), 1L),
            Triple("exactly one millisecond before", DUE_AT.minusNanos(1_000_000), 1L),
            Triple("one nanosecond over a millisecond before", DUE_AT.minusNanos(1_000_001), 2L),
            Triple("further back than a delay can express", Instant.MIN, MAX_INITIAL_DELAY_MILLIS),
            Triple("past the cap but not past the arithmetic", BEYOND_CAP, MAX_INITIAL_DELAY_MILLIS),
        )

        cases.forEach { (label, now, expected) ->
            val fixture = Fixture(now = now)

            assertEquals(CALLBACK_CONFIRMED_ADMISSION, fixture.register(), label)
            assertEquals(expected, fixture.enqueue.submissions.single().request.workSpec.initialDelay, label)
        }
    }

    @Test
    fun `a reservation that cannot be committed never reaches WorkManager`() {
        val fixture = Fixture()
        fixture.preferences.commitsBeforeFailure = 0

        val cause = fixture.register()

        assertEquals(RECEIPT_WRITE_UNCERTAIN, cause)
        assertEquals(emptyList(), fixture.query.names)
        assertEquals(emptyList(), fixture.enqueue.submissions)
    }

    @Test
    fun `evidence the store refuses to read fails closed without touching WorkManager`() {
        val quarantined = Fixture(ENQUEUED)
        quarantined.preferences.tamper(STORE_KEY, "reminder-receipts/v2")
        val uncertain = Fixture(ENQUEUED)
        uncertain.preferences.commitsBeforeFailure = uncertain.preferences.commits
        uncertain.store.compareAndSet(OCCURRENCE_ID, 0L, WORK_ID_0, ENQUEUED, RETRYABLE, null)

        listOf(quarantined to RECEIPT_QUARANTINED, uncertain to RECEIPT_WRITE_UNCERTAIN).forEach { (fix, expected) ->
            val label = expected.name
            assertEquals(expected, fix.register(), label)
            assertEquals(emptyList(), fix.query.names, label)
            assertEquals(emptyList(), fix.enqueue.submissions, label)
        }
    }

    @Test
    fun `an occurrence that already left the schedulable phases is skipped`() {
        listOf(DELIVERY_UNCERTAIN, DELIVERED, TERMINAL, QUARANTINED).forEach { phase ->
            val fixture = Fixture(phase, retained = listOf(RetainedWork(WORK_ID_0, WorkInfo.State.SUCCEEDED)))

            val cause = fixture.register()

            val label = phase.name
            assertEquals(OCCURRENCE_CLOSED, cause, label)
            assertEquals(SKIPPED, cause.outcome, label)
            assertEquals(phase, fixture.phase(), label)
            assertEquals(emptyList(), fixture.query.names, label)
            assertEquals(emptyList(), fixture.enqueue.submissions, label)
        }
    }

    @Test
    fun `every retained state of the current work request id reconciles without a second enqueue`() {
        val cases = listOf(
            Triple(WorkInfo.State.ENQUEUED, RETAINED_WORK_ENQUEUED, ENQUEUED),
            Triple(WorkInfo.State.RUNNING, RETAINED_WORK_ENQUEUED, ENQUEUED),
            Triple(WorkInfo.State.BLOCKED, RETAINED_WORK_BLOCKED, QUARANTINED),
            Triple(WorkInfo.State.SUCCEEDED, RETAINED_WORK_SUCCEEDED_WITHOUT_RECEIPT, QUARANTINED),
            Triple(WorkInfo.State.FAILED, RETAINED_WORK_FAILED, TERMINAL),
            Triple(WorkInfo.State.CANCELLED, RETAINED_WORK_CANCELLED, TERMINAL),
        )

        cases.forEach { (state, cause, phase) ->
            val fixture = Fixture(ADMISSION_PENDING, retained = listOf(RetainedWork(WORK_ID_0, state)))

            val label = state.name
            assertEquals(cause, fixture.register(), label)
            assertEquals(phase, fixture.phase(), label)
            assertEquals(emptyList(), fixture.enqueue.submissions, label)
        }
    }

    @Test
    fun `foreign active or duplicated retained work quarantines the registration without enqueueing`() {
        listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED).forEach { state ->
            val fixture = Fixture(ADMISSION_PENDING, retained = listOf(RetainedWork(FOREIGN_WORK_ID, state)))

            val label = state.name
            val cause = fixture.register()

            assertEquals(RETAINED_WORK_ID_MISMATCH, cause, label)
            assertEquals(QUARANTINED, fixture.phase(), label)
            assertEquals(emptyList(), fixture.enqueue.submissions, label)
        }

        val duplicated = Fixture(
            ADMISSION_PENDING,
            retained = listOf(
                RetainedWork(WORK_ID_0, WorkInfo.State.ENQUEUED),
                RetainedWork(WORK_ID_0, WorkInfo.State.RUNNING),
            ),
        )

        assertEquals(RETAINED_WORK_DUPLICATE, duplicated.register())
        assertEquals(QUARANTINED, duplicated.phase())
        assertEquals(emptyList(), duplicated.enqueue.submissions)

        // Work of another generation that the platform already finished is history, not a conflict.
        listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED).forEach { state ->
            val fixture = Fixture(ADMISSION_PENDING, retained = listOf(RetainedWork(FOREIGN_WORK_ID, state)))

            val label = state.name
            assertEquals(CALLBACK_CONFIRMED_ADMISSION, fixture.register(), label)
            assertEquals(1, fixture.enqueue.submissions.size, label)
        }
    }

    @Test
    fun `a query that fails is retryable and enqueues nothing`() {
        val fixture = Fixture(ADMISSION_PENDING, queryFailure = IOException("work database locked"))

        val cause = fixture.register()

        assertEquals(RETAINED_WORK_QUERY_FAILED, cause)
        assertEquals(RETRYABLE_FAILURE, cause.outcome)
        assertEquals(ADMISSION_PENDING, fixture.phase())
        assertEquals(emptyList(), fixture.enqueue.submissions)
    }

    @Test
    fun `each enqueue outcome settles under its own cause`() {
        val cases = listOf(
            Triple(ReminderEnqueueSignal.Confirmed, CALLBACK_CONFIRMED_ADMISSION, ENQUEUED),
            Triple(ReminderEnqueueSignal.Absent, ENQUEUE_CALLBACK_NULL, RETRYABLE),
            Triple(ReminderEnqueueSignal.Reported(IOException("refused")), ENQUEUE_CALLBACK_ERROR, RETRYABLE),
            Triple(ReminderEnqueueSignal.Raised(IllegalStateException("gone")), ENQUEUE_CALLBACK_THROWABLE, RETRYABLE),
            Triple(ReminderEnqueueSignal.TimedOut, ENQUEUE_CALLBACK_TIMEOUT, ADMISSION_PENDING),
        )

        cases.forEach { (signal, cause, phase) ->
            val fixture = Fixture(ADMISSION_PENDING, signal = signal)

            val label = cause.name
            assertEquals(cause, fixture.register(), label)
            assertEquals(phase, fixture.phase(), label)
            assertEquals(1, fixture.enqueue.submissions.size, label)
        }
    }

    @Test
    fun `a fatal submission terminates while a transient one leaves the reservation pending`() {
        val fatal = Fixture(ADMISSION_PENDING, submitFailure = SecurityException("no scheduler access"))
        val transient = Fixture(ADMISSION_PENDING, submitFailure = IOException("work database locked"))

        assertEquals(ENQUEUE_SUBMIT_FATAL, fatal.register())
        assertEquals(TERMINAL, fatal.phase())

        assertEquals(ENQUEUE_SUBMIT_TRANSIENT, transient.register())
        assertEquals(ADMISSION_PENDING, transient.phase())
    }

    @Test
    fun `a worker that confirms admission first is reported as admitted, never as a failure`() {
        // One confirmation and one failure, which are the two ways this run would otherwise have
        // written the receipt itself.
        val signals = listOf(ReminderEnqueueSignal.Confirmed, ReminderEnqueueSignal.Absent)

        signals.forEach { signal ->
            val fixture = Fixture(ADMISSION_PENDING, signal = signal)
            // The platform started the matching worker before it answered the submission, so the
            // production runner leaves ADMISSION_PENDING while this call is still in flight.
            fixture.enqueue.beforeSignal = { request -> fixture.runWorker(request) }

            val label = signal.toString()
            val cause = fixture.register()

            assertEquals(WORKER_CONFIRMED_ADMISSION, cause, label)
            assertEquals(DELIVERED, fixture.phase(), label)
            assertEquals(1, fixture.enqueue.submissions.size, label)
        }
    }

    @Test
    fun `a registration overtaken by a newer generation reports the supersession`() {
        val fixture = Fixture(ADMISSION_PENDING)
        // Another registration recovered this occurrence while the submission was in flight, so
        // the receipt this call holds is a generation behind by the time it writes.
        fixture.enqueue.beforeSignal = { fixture.supersedeGeneration() }

        val cause = fixture.register()

        assertEquals(GENERATION_SUPERSEDED, cause)
        assertEquals(SKIPPED, cause.outcome)
        assertEquals(ADMISSION_PENDING, fixture.phase())
        assertEquals(1L, fixture.generation())
    }

    @Test
    fun `permission recovery refuses without a fresh grant and derives the next generation once granted`() {
        val denied = Fixture(PERMISSION_BLOCKED, granted = false)

        val refused = denied.register()

        assertEquals(PERMISSION_NOT_GRANTED, refused)
        assertEquals(ReminderRegistrationOutcome.PERMISSION_BLOCKED, refused.outcome)
        assertEquals(PERMISSION_BLOCKED, denied.phase())
        assertEquals(0L, denied.generation())
        assertEquals(emptyList(), denied.query.names)
        assertEquals(emptyList(), denied.enqueue.submissions)

        val granted = Fixture(PERMISSION_BLOCKED)

        assertEquals(CALLBACK_CONFIRMED_ADMISSION, granted.register())
        assertEquals(1L, granted.generation())
        assertEquals(ENQUEUED, granted.phase())
        val request = granted.enqueue.submissions.single().request
        assertEquals(WORK_ID_1, request.id)
        assertEquals(ReminderRunOutcome.SUCCESS, granted.runWorker(request))

        val legacy = Fixture(PERMISSION_BLOCKED, granted = false, sdkInt = 32)

        assertEquals(CALLBACK_CONFIRMED_ADMISSION, legacy.register())
        assertEquals(1L, legacy.generation())
    }

    @Test
    fun `a route without a property is refused before any store or platform call`() {
        val fixture = Fixture()

        val cause = fixture.register(PendingReminder(ScheduleRoute("   ", ROUTINE), DUE_AT))

        assertEquals(INVALID_ROUTE, cause)
        assertEquals(PERMANENT_FAILURE, cause.outcome)
        assertEquals(0, fixture.preferences.reads)
        assertEquals(emptyList(), fixture.query.names)
        assertEquals(emptyList(), fixture.enqueue.submissions)
        // An unresolvable occurrence publishes no half of an identity that correlates with nothing.
        assertEquals(
            ReminderRegistrationRecord(null, ROUTINE, null, INVALID_ROUTE),
            fixture.diagnostics.records.single(),
        )
    }

    /** One occurrence, its store and the ports, wired as the production factory wires them. */
    private class Fixture(
        phase: ReminderPhase? = null,
        granted: Boolean = true,
        signal: ReminderEnqueueSignal = ReminderEnqueueSignal.Confirmed,
        submitFailure: Throwable? = null,
        retained: List<RetainedWork> = emptyList(),
        queryFailure: Throwable? = null,
        private val now: Instant = DUE_AT.minusSeconds(60),
        private val sdkInt: Int = 35,
    ) {
        val preferences = FakePreferences()
        val store = storeAt(phase, preferences)
        val enqueue = RecordingEnqueue(signal, submitFailure)
        val query = RecordingQuery(retained, queryFailure)
        val diagnostics = RecordingDiagnostics()
        private val permission = FixedPermission(granted)

        fun register(reminder: PendingReminder = PendingReminder(ROUTE, DUE_AT)): ReminderRegistrationCause =
            ReminderScheduler(
                store, enqueue, query, permission, diagnostics, Clock.fixed(now, ZoneOffset.UTC), sdkInt,
            ).register(reminder)

        /** Runs the merged production runner over this store, reading only what the request carries. */
        fun runWorker(request: OneTimeWorkRequest): ReminderRunOutcome {
            val input = request.workSpec.input
            return ReminderDeliveryRunner(
                store, permission, Preparation(), Notifier(), NoOpReminderDiagnosticPort,
            ).run(
                ReminderWorkInput(
                    occurrenceId = input.getString(ReminderWorkKeys.OCCURRENCE_ID),
                    propertyId = input.getString(ReminderWorkKeys.PROPERTY_ID),
                    inspectionType = input.getString(ReminderWorkKeys.INSPECTION_TYPE),
                    dueAt = input.getString(ReminderWorkKeys.DUE_AT),
                    generationNumber = input.getString(ReminderWorkKeys.GENERATION_NUMBER),
                    workRequestId = request.id,
                ),
                sdkInt = sdkInt,
                runAttemptCount = 0,
            )
        }

        /** Drives this occurrence to the next generation through the store's own public API. */
        fun supersedeGeneration() {
            store.compareAndSet(OCCURRENCE_ID, 0L, WORK_ID_0, ADMISSION_PENDING, ENQUEUED, null)
            store.compareAndSet(OCCURRENCE_ID, 0L, WORK_ID_0, ENQUEUED, PERMISSION_BLOCKED, null)
            store.recoverPermissionBlocked(receipt(PERMISSION_BLOCKED))
        }

        fun phase(): ReminderPhase? = present()?.phase

        fun generation(): Long? = present()?.generationNumber

        private fun present(): ReminderReceipt? =
            (store.lookup(OCCURRENCE_ID) as? ReminderReceiptLookup.Present)?.receipt
    }

    private class FakePreferences : ReminderPreferencePort {
        private val entries = mutableMapOf<String, String>()
        override val backingStore: Any = Any()
        var commitsBeforeFailure = Int.MAX_VALUE
        var commits = 0
            private set
        var reads = 0
            private set

        override fun readAll(): Map<String, String> = synchronized(entries) {
            reads++
            entries.toMap()
        }

        override fun commit(writes: Map<String, String>): Boolean {
            synchronized(entries) {
                commits++
                if (commits > commitsBeforeFailure) {
                    return false
                }
                entries.putAll(writes)
            }
            return true
        }

        /** Overwrites one stored entry, which is how corruption reaches an otherwise live store. */
        fun tamper(key: String, value: String) = synchronized(entries) { entries[key] = value }
    }

    private class FixedPermission(private val granted: Boolean) : ReminderPermissionPort {
        override fun isPostNotificationsGranted(): Boolean = granted
    }

    private class Submission(val name: String, val policy: ExistingWorkPolicy, val request: OneTimeWorkRequest)

    private class RecordingEnqueue(private val signal: ReminderEnqueueSignal, private val failure: Throwable?) :
        ReminderEnqueuePort {
        val submissions = mutableListOf<Submission>()

        /** What the platform does between accepting the submission and answering it. */
        var beforeSignal: (OneTimeWorkRequest) -> Unit = {}

        override fun enqueueUnique(
            name: String,
            policy: ExistingWorkPolicy,
            request: OneTimeWorkRequest,
        ): ReminderEnqueueSignal {
            failure?.let { throw it }
            submissions += Submission(name, policy, request)
            beforeSignal(request)
            return signal
        }
    }

    private class RecordingQuery(private val retained: List<RetainedWork>, private val failure: Throwable?) :
        ReminderWorkQueryPort {
        val names = mutableListOf<String>()

        override fun retainedWork(uniqueWorkName: String): List<RetainedWork> {
            names += uniqueWorkName
            failure?.let { throw it }
            return retained
        }
    }

    private class RecordingDiagnostics : ReminderSchedulerDiagnosticPort {
        val records = mutableListOf<ReminderRegistrationRecord>()

        override fun record(record: ReminderRegistrationRecord) { records += record }
    }

    private class Preparation : ReminderPreparationPort<String> {
        override fun prepare(plan: DeliveryPlan.Notify): String = "prepared-notification"
    }

    private class Notifier : ReminderNotifierPort<String> {
        override fun post(identity: NotificationIdentity, prepared: String) = Unit
    }

    private companion object {
        const val PROPERTY = "property-a"
        const val STORE_KEY = "store"
        const val DUE_AT_TEXT = "2026-08-03T00:00:00.000000001Z"
        const val OCCURRENCE_ID = "c118fefec6ee20d89eafa5533048237237d39116af40aa85123fb1f70c404108"
        const val UNIQUE_WORK_NAME = "schedule-reminder:$OCCURRENCE_ID"
        val WORK_ID_0: UUID = UUID.fromString("40fe7461-9be1-3ce7-8bdf-28b48b76359e")
        val WORK_ID_1: UUID = UUID.fromString("590ca815-2783-322a-acde-39ab31dafd39")
        val FOREIGN_WORK_ID: UUID = UUID.fromString("00000000-0000-3000-8000-0000000000ff")
        val DUE_AT: Instant = Instant.parse(DUE_AT_TEXT)
        val BEYOND_CAP: Instant = DUE_AT.minusSeconds(5_000_000_000_000_000)
        val ROUTE = ScheduleRoute(PROPERTY, ROUTINE)

        /** The transitions each phase is reached by, written out here rather than derived. */
        val PATH_TO: Map<ReminderPhase, List<ReminderPhase>> = mapOf(
            ADMISSION_PENDING to emptyList(),
            ENQUEUED to listOf(ENQUEUED),
            RETRYABLE to listOf(RETRYABLE),
            TERMINAL to listOf(TERMINAL),
            QUARANTINED to listOf(QUARANTINED),
            DELIVERY_UNCERTAIN to listOf(ENQUEUED, DELIVERY_UNCERTAIN),
            PERMISSION_BLOCKED to listOf(ENQUEUED, PERMISSION_BLOCKED),
            DELIVERED to listOf(ENQUEUED, DELIVERY_UNCERTAIN, DELIVERED),
        )

        fun receipt(phase: ReminderPhase): ReminderReceipt = ReminderReceipt(
            occurrenceId = OCCURRENCE_ID,
            generationNumber = 0,
            workRequestId = WORK_ID_0,
            spec = WorkSpecFactory().create(ROUTE, DUE_AT),
            phase = phase,
            causeCode = causeFor(phase),
        )

        fun causeFor(phase: ReminderPhase): ReminderCause? =
            ReminderCause.PERMANENT_DELIVERY_FAILURE.takeIf { phase == TERMINAL }

        /** Walks a freshly admitted occurrence to [phase] through the store's public API. */
        fun storeAt(phase: ReminderPhase?, preferences: FakePreferences): ReminderReceiptStore {
            val store = ReminderReceiptStore(preferences)
            if (phase == null) {
                return store
            }
            assertEquals(ReminderReceiptAdmissionResult.Admitted, store.admit(receipt(ADMISSION_PENDING)))
            var current = ADMISSION_PENDING
            PATH_TO.getValue(phase).forEach { next ->
                store.compareAndSet(OCCURRENCE_ID, 0L, WORK_ID_0, current, next, causeFor(next))
                current = next
            }
            return store
        }
    }
}

/* R4 semantic mutation receipts. Each row was applied alone to ReminderScheduler.kt at SHA-256
 * 8af09018e1f4b7f0fece530417212f58912c1c4d0f5b7a7cf8896ad32a961689, run through this card's test
 * task, then reverted and re-hashed to that same value. A kill is exit 1 with the named test among
 * the failing cases, and every run reported 14 executed cases, which rules out a compile break.
 * The bounded re-read exhaustion is the survivor this card pre-declared.
 * A1 M01 never round a sub millisecond delay up           delay table: one nanosecond before
 * A1 M02 drop the clamp on an unrepresentable delay       delay table: past the cap
 * A1 M03 write the property id under the occurrence key   fresh occurrence: the runner refuses it
 * A2 M04 read quarantined evidence as a fresh occurrence  refused evidence: reserved and enqueued
 * A2 M05 submit without a durable reservation             lost reservation: submitted anyway
 * A3 M06 treat finished foreign work as a live conflict   foreign work: no enqueue at all
 * A3 M07 read retained BLOCKED work as this admission     retained states: admitted instead
 * A4 M08 write the receipt before the operation answers   enqueue outcomes: left RETRYABLE
 * A4 M09 close on a transient submission failure          submission: terminal, not still pending
 * A4 M10 downgrade an admission the worker proved         worker first: reported as a failure
 * A4 M11 write into the generation that superseded this   supersession: overwrote generation one
 * A5 M12 recover without reading the grant again          permission: recovered while denied
 */

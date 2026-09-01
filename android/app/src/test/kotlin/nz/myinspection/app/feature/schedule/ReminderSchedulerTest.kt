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
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.ADMISSION_ALREADY_RECORDED
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
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.RECEIPT_CONTENDED
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
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.RECEIPT_REJECTED
import nz.myinspection.app.feature.schedule.ReminderRegistrationOutcome.ADMITTED
import nz.myinspection.app.feature.schedule.ReminderRegistrationOutcome.PERMANENT_FAILURE
import nz.myinspection.app.feature.schedule.ReminderRegistrationOutcome.RETRYABLE_FAILURE
import nz.myinspection.app.feature.schedule.ReminderRegistrationOutcome.SKIPPED
import nz.myinspection.core.schedule.InspectionScheduleType.ROUTINE

/**
 * Black box acceptance tests for the scheduler, driven through the same ports the production
 * factory injects, over the real receipt store on an in memory preference file. The occurrence
 * digest and the generation work id are golden vectors frozen by the contracts card, and a worker
 * that has to have run is the production runner over this same store. The fixture nests because the
 * merged worker tests own these names at package level.
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
        assertEquals(ReminderRunOutcome.SUCCESS, fixture.runWorker(submission.request))
        assertEquals(DELIVERED, fixture.phase())
        assertEquals(
            ReminderRegistrationRecord(
                ReminderRegistrationIdentity(OCCURRENCE_ID, 0L), ROUTINE, CALLBACK_CONFIRMED_ADMISSION,
            ),
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
            Triple("exactly Long.MAX milliseconds", DUE_AT.minusMillis(Long.MAX_VALUE), MAX_INITIAL_DELAY_MILLIS),
            Triple("Long.MAX milliseconds plus a rounding nanosecond", LONG_MAX_PLUS_NANO, MAX_INITIAL_DELAY_MILLIS),
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
        listOf(DELIVERY_UNCERTAIN, DELIVERED, TERMINAL, QUARANTINED, PERMISSION_BLOCKED).forEach { phase ->
            val fixture = Fixture(phase, retained = listOf(RetainedWork(WORK_ID_0, WorkInfo.State.SUCCEEDED)))

            val cause = fixture.register()

            val label = phase.name
            assertEquals(OCCURRENCE_CLOSED, cause, label)
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
            assertEquals(cause, fixture.diagnostics.records.single().cause, label)
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

        val mixed = Fixture(
            ADMISSION_PENDING,
            retained = listOf(
                RetainedWork(WORK_ID_0, WorkInfo.State.ENQUEUED),
                RetainedWork(FOREIGN_WORK_ID, WorkInfo.State.RUNNING),
            ),
        )

        assertEquals(RETAINED_WORK_ID_MISMATCH, mixed.register())
        assertEquals(emptyList(), mixed.enqueue.submissions)

        // Work of another generation that the platform already finished is history, not a conflict.
        listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED).forEach { state ->
            val fixture = Fixture(ADMISSION_PENDING, retained = listOf(RetainedWork(FOREIGN_WORK_ID, state)))

            val label = state.name
            assertEquals(CALLBACK_CONFIRMED_ADMISSION, fixture.register(), label)
            assertEquals(1, fixture.enqueue.submissions.size, label)
        }
    }

    @Test
    fun `a persisted active receipt resumes into a submission under its own generation`() {
        listOf(ENQUEUED, RETRYABLE).forEach { phase ->
            val fixture = Fixture(phase)

            val label = phase.name
            assertEquals(CALLBACK_CONFIRMED_ADMISSION, fixture.register(), label)
            assertEquals(WORK_ID_0, fixture.enqueue.submissions.single().request.id, label)
            assertEquals(ENQUEUED, fixture.phase(), label)
        }
    }

    @Test
    fun `a transition this registration cannot confirm durably is retryable`() {
        val fixture = Fixture(ADMISSION_PENDING, retained = listOf(RetainedWork(WORK_ID_0, WorkInfo.State.RUNNING)))
        fixture.preferences.commitsBeforeFailure = fixture.preferences.commits

        assertEquals(RECEIPT_WRITE_UNCERTAIN, fixture.register())
        assertEquals(emptyList(), fixture.enqueue.submissions)
    }

    @Test
    fun `a failing query is retryable, and an interrupted one also hands the interrupt back`() {
        val failed = Fixture(ADMISSION_PENDING, queryFailure = IOException("work database locked"))

        val cause = failed.register()

        assertEquals(RETAINED_WORK_QUERY_FAILED, cause)
        assertEquals(ADMISSION_PENDING, failed.phase())
        assertEquals(emptyList(), failed.enqueue.submissions)

        val interrupted = Fixture(ADMISSION_PENDING, queryFailure = InterruptedException("cancelled"))

        assertEquals(RETAINED_WORK_QUERY_FAILED, interrupted.register())
        // Reading the flag also clears it, so this assertion cannot leak into another test.
        assertEquals(true, Thread.interrupted())
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
            assertEquals(cause, fixture.diagnostics.records.single().cause, label)
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
    fun `a worker that ran first leaves an occurrence this registration reports as closed`() {
        // One confirmation and one failure: neither may be read as an admission this cannot prove.
        val signals = listOf(ReminderEnqueueSignal.Confirmed, ReminderEnqueueSignal.Absent)

        signals.forEach { signal ->
            val fixture = Fixture(ADMISSION_PENDING, signal = signal)
            // The platform started the matching worker before it answered the submission, so the
            // production runner leaves ADMISSION_PENDING while this call is still in flight.
            fixture.enqueue.beforeSignal = { request -> fixture.runWorker(request) }

            val label = signal.toString()
            val cause = fixture.register()

            assertEquals(OCCURRENCE_CLOSED, cause, label)
            assertEquals(SKIPPED, cause.outcome, label)
            assertEquals(DELIVERED, fixture.phase(), label)
            assertEquals(1, fixture.enqueue.submissions.size, label)
        }
    }

    @Test
    fun `a same generation change is recorded admission or contention, never proof this cannot give`() {
        val cases = listOf(ENQUEUED to ADMISSION_ALREADY_RECORDED, RETRYABLE to RECEIPT_CONTENDED)

        cases.forEach { (phase, cause) ->
            val fixture = Fixture(ADMISSION_PENDING)
            // Another registration moved the receipt while this submission was in flight.
            fixture.enqueue.beforeSignal = {
                fixture.store.compareAndSet(OCCURRENCE_ID, 0L, WORK_ID_0, ADMISSION_PENDING, phase, null)
            }

            val label = phase.name
            assertEquals(cause, fixture.register(), label)
            assertEquals(phase, fixture.phase(), label)
        }
    }

    @Test
    fun `evidence that disappears under a transition is refused rather than rewritten`() {
        val fixture = Fixture(ADMISSION_PENDING)
        fixture.enqueue.beforeSignal = { fixture.preferences.wipe() }

        val cause = fixture.register()

        assertEquals(RECEIPT_REJECTED, cause)
    }

    @Test
    fun `a registration overtaken by a newer generation reports the supersession`() {
        val fixture = Fixture(ADMISSION_PENDING)
        // Another registration recovered this occurrence while the submission was in flight, so
        // the receipt this call holds is a generation behind by the time it writes.
        fixture.enqueue.beforeSignal = { fixture.supersedeGeneration() }

        val cause = fixture.register()

        assertEquals(GENERATION_SUPERSEDED, cause)
        assertEquals(ADMISSION_PENDING, fixture.phase())
        assertEquals(1L, fixture.generation())
    }

    @Test
    fun `a route without a property is refused before any store or platform call`() {
        val fixture = Fixture()

        val cause = fixture.register(PendingReminder(ScheduleRoute("   ", ROUTINE), DUE_AT))

        assertEquals(INVALID_ROUTE, cause)
        assertEquals(PERMANENT_FAILURE, cause.outcome)
        assertEquals(0, fixture.preferences.reads)
        assertEquals(emptyList(), fixture.enqueue.submissions)
        // An unresolvable occurrence publishes no half of an identity that correlates with nothing.
        assertEquals(
            ReminderRegistrationRecord(null, ROUTINE, INVALID_ROUTE),
            fixture.diagnostics.records.single(),
        )
    }

    /** One occurrence, its store and the ports, wired as the production factory wires them. */
    private class Fixture(
        phase: ReminderPhase? = null,
        signal: ReminderEnqueueSignal = ReminderEnqueueSignal.Confirmed,
        submitFailure: Throwable? = null,
        retained: List<RetainedWork> = emptyList(),
        queryFailure: Throwable? = null,
        private val now: Instant = DUE_AT.minusSeconds(60),
    ) {
        val preferences = FakePreferences()
        val store = storeAt(phase, preferences)
        val enqueue = RecordingEnqueue(signal, submitFailure)
        val query = RecordingQuery(retained, queryFailure)
        val diagnostics = RecordingDiagnostics()
        private val permission = GrantedPermission()

        fun register(reminder: PendingReminder = PendingReminder(ROUTE, DUE_AT)): ReminderRegistrationCause =
            ReminderScheduler(store, enqueue, query, diagnostics, Clock.fixed(now, ZoneOffset.UTC))
                .register(reminder)

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
                sdkInt = 35,
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

        /** Drops every entry, which is how a cleared preference file reaches a live registration. */
        fun wipe() = synchronized(entries) { entries.clear() }
    }

    private class GrantedPermission : ReminderPermissionPort {
        override fun isPostNotificationsGranted(): Boolean = true
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
        val FOREIGN_WORK_ID: UUID = UUID.fromString("00000000-0000-3000-8000-0000000000ff")
        val DUE_AT: Instant = Instant.parse(DUE_AT_TEXT)
        val BEYOND_CAP: Instant = DUE_AT.minusSeconds(5_000_000_000_000_000)
        val LONG_MAX_PLUS_NANO: Instant = DUE_AT.minusMillis(Long.MAX_VALUE).minusNanos(1)
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
 * 264f6839152fa50efc63d63024187e6a034efc323b8bb31bed328af0ad442632, run through this card's test
 * task, then reverted and re-hashed. A kill is exit 1 with the named test among the failing cases,
 * and every run reported the same executed-case count, which rules out a compile break.
 * A1 M01 never round a sub millisecond delay up           delay table: one nanosecond before
 * A1 M02 drop the clamp on an unrepresentable delay       delay table: past the cap
 * A1 M03 write the property id under the occurrence key   fresh occurrence: the runner refuses it
 * A1 M20 let a rounding overflow fall through as zero     delay table: Long.MAX plus a nanosecond
 * A2 M04 read quarantined evidence as a fresh occurrence  refused evidence: reserved and enqueued
 * A2 M05 submit without a durable reservation             lost reservation: submitted anyway
 * A2 M06 read a persisted active receipt as settled       persisted receipt: skipped, not resumed
 * A2 M07 treat a refused transition as an applied one     wiped evidence: reported as admitted
 * A3 M08 treat finished foreign work as a live conflict   foreign work: no enqueue at all
 * A3 M09 look for foreign work only when this one is idle mixed retained ids: enqueued anyway
 * A3 M10 read retained BLOCKED work as this admission     retained states: admitted instead
 * A4 M11 write the receipt before the operation answers   enqueue outcomes: left RETRYABLE
 * A4 M12 close on a transient submission failure          submission: terminal, not still pending
 * A4 M13 read a closed occurrence as a confirmed admission worker ran first: claimed admission
 * A4 M14 read an unattributable change as an admission    contention: claimed admission
 * A4 M15 write into the generation that superseded this   supersession: overwrote generation one
 * A4 M16 report a lost CAS without re-reading it          supersession: reported contention
 * A4 M17 swallow the interrupt instead of handing it back interrupted query: flag never restored
 * A4 M19 read a same generation ENQUEUED as contention    same generation race: no admission
 * A5 M18 derive the id from an unreserved generation      fresh occurrence: the runner refuses it
 */

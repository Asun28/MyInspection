package nz.myinspection.app.feature.schedule

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicIntegerArray
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
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.ENQUEUE_CALLBACK_AFTER_WORKER_STARTED
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
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.RECEIPT_CONTENDED
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.RECEIPT_QUARANTINED
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.RECEIPT_REJECTED
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.RECEIPT_REREAD_EXHAUSTED
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
import nz.myinspection.app.feature.schedule.ReminderRegistrationNote.LATE_CALLBACK
import nz.myinspection.app.feature.schedule.ReminderRegistrationNote.WAITER_FAILED
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
 *
 * Registration is asynchronous, the cause arriving through a waiter. [Fixture.register] returns
 * that one cause, [Fixture.registerDeferred] the list it is appended to.
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
            ReminderRegistrationRecord(IDENTITY_0, ROUTINE, CALLBACK_CONFIRMED_ADMISSION),
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
        // PERMISSION_BLOCKED is deliberately absent: it is the one closed phase a fresh grant
        // reopens, which the recovery cases below own.
        listOf(DELIVERY_UNCERTAIN, DELIVERED, TERMINAL, QUARANTINED).forEach { phase ->
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
    fun `permission recovery reads the grant at that moment and re-registers under the next generation`() {
        val denied = Fixture(PERMISSION_BLOCKED)
        // Granted while this scheduler was built, withdrawn before the recovery reads it.
        denied.permission.granted = false
        val committed = denied.preferences.commits

        assertEquals(PERMISSION_NOT_GRANTED, denied.register())
        assertEquals(RETRYABLE_FAILURE, PERMISSION_NOT_GRANTED.outcome)
        assertEquals(1, denied.permission.checks)
        assertEquals(PERMISSION_BLOCKED, denied.phase())
        assertEquals(0L, denied.generation())
        assertEquals(committed, denied.preferences.commits)
        assertEquals(emptyList(), denied.query.names)
        assertEquals(emptyList(), denied.enqueue.submissions)
        assertEquals(
            "{\"event\":\"schedule-reminder\",\"stage\":\"permission\"," +
                "\"occurrence_id\":\"$OCCURRENCE_ID\",\"type\":\"ROUTINE\",\"generation_number\":0," +
                "\"work_request_id\":\"40fe7461-9be1-3ce7-8bdf-28b48b76359e\"," +
                "\"retained_work_request_id\":null,\"retryable\":true," +
                "\"cause_code\":\"permission-not-granted\",\"callback_cause_code\":null,\"note\":null}",
            denied.diagnostics.messages.single(),
        )

        val granted = Fixture(PERMISSION_BLOCKED)

        assertEquals(CALLBACK_CONFIRMED_ADMISSION, granted.register())
        assertEquals(1, granted.permission.checks)
        assertEquals(1L, granted.generation())
        assertEquals(ENQUEUED, granted.phase())
        assertEquals(listOf(UNIQUE_WORK_NAME), granted.query.names)
        assertEquals(WORK_ID_1, granted.enqueue.submissions.single().request.id)
        assertEquals(
            ReminderRegistrationRecord(identity(1L), ROUTINE, CALLBACK_CONFIRMED_ADMISSION),
            granted.diagnostics.records.single(),
        )
        // The platform accepts the recovered generation's request, and the runner it starts reads
        // that very generation back out of this same store.
        assertEquals(ReminderRunOutcome.SUCCESS, granted.runWorker(granted.enqueue.submissions.single().request))
        assertEquals(DELIVERED, granted.phase())
    }

    @Test
    fun `a recovery the store keeps moving under is bounded and reports its own cause`() {
        val fixture = Fixture(PERMISSION_BLOCKED)
        // Another registration recovers this occurrence and a delivery run that still finds no
        // grant blocks it again, every single time this one reads the grant, so no view it holds
        // is ever the view the store has when the recovery writes.
        fixture.permission.onCheck = { fixture.blockNextGeneration() }

        val cause = fixture.register()

        assertEquals(RECEIPT_REREAD_EXHAUSTED, cause)
        assertEquals(RETRYABLE_FAILURE, cause.outcome)
        assertEquals(RECOVERY_READS, fixture.permission.checks)
        assertEquals(RECOVERY_READS.toLong(), fixture.generation())
        assertEquals(PERMISSION_BLOCKED, fixture.phase())
        assertEquals(emptyList(), fixture.query.names)
        assertEquals(emptyList(), fixture.enqueue.submissions)
        assertEquals(
            ReminderRegistrationRecord(identity(RECOVERY_READS.toLong()), ROUTINE, RECEIPT_REREAD_EXHAUSTED),
            fixture.diagnostics.records.single(),
        )
    }

    @Test
    fun `a recovery that loses its write reports what the actor that beat it left behind`() {
        // Another registration recovered it first, so this one continues under their generation
        // rather than opening one of its own.
        val joined = Fixture(PERMISSION_BLOCKED)
        joined.permission.onCheck = { joined.recoverOnce() }

        assertEquals(CALLBACK_CONFIRMED_ADMISSION, joined.register())
        assertEquals(1, joined.permission.checks)
        assertEquals(1L, joined.generation())
        assertEquals(WORK_ID_1, joined.enqueue.submissions.single().request.id)

        // That other registration got all the way to delivered, so nothing is left to schedule.
        val delivered = Fixture(PERMISSION_BLOCKED)
        delivered.permission.onCheck = {
            delivered.recoverOnce()
            assertEquals(ReminderRunOutcome.SUCCESS, delivered.runWorker(1L))
        }

        assertEquals(OCCURRENCE_CLOSED, delivered.register())
        assertEquals(DELIVERED, delivered.phase())
        assertEquals(emptyList(), delivered.enqueue.submissions)

        // The recovery itself could not be committed, so nothing was recovered and nothing ran.
        val uncertain = Fixture(PERMISSION_BLOCKED)
        uncertain.preferences.commitsBeforeFailure = uncertain.preferences.commits

        assertEquals(RECEIPT_WRITE_UNCERTAIN, uncertain.register())
        assertEquals(emptyList(), uncertain.enqueue.submissions)

        // The evidence went away between the read that found it blocked and the recovery.
        val gone = Fixture(PERMISSION_BLOCKED)
        gone.permission.onCheck = { gone.preferences.wipe() }

        assertEquals(RECEIPT_REJECTED, gone.register())
        assertEquals(PERMANENT_FAILURE, RECEIPT_REJECTED.outcome)
        assertEquals(emptyList(), gone.enqueue.submissions)
    }

    @Test
    fun `registration diagnostics publish only what correlates with this occurrence`() {
        val admitted = Fixture()
        admitted.register()

        assertEquals(
            "{\"event\":\"schedule-reminder\",\"stage\":\"receipt\"," +
                "\"occurrence_id\":\"$OCCURRENCE_ID\",\"type\":\"ROUTINE\",\"generation_number\":0," +
                "\"work_request_id\":\"40fe7461-9be1-3ce7-8bdf-28b48b76359e\"," +
                "\"retained_work_request_id\":null,\"retryable\":false," +
                "\"cause_code\":\"callback-confirmed-admission\",\"callback_cause_code\":null,\"note\":null}",
            admitted.diagnostics.messages.single(),
        )

        val foreign = Fixture(
            ADMISSION_PENDING,
            retained = listOf(RetainedWork(FOREIGN_WORK_ID, WorkInfo.State.RUNNING)),
        )
        foreign.register()

        assertEquals(
            "{\"event\":\"schedule-reminder\",\"stage\":\"receipt\"," +
                "\"occurrence_id\":\"$OCCURRENCE_ID\",\"type\":\"ROUTINE\",\"generation_number\":0," +
                "\"work_request_id\":\"40fe7461-9be1-3ce7-8bdf-28b48b76359e\"," +
                "\"retained_work_request_id\":\"00000000-0000-3000-8000-0000000000ff\"," +
                "\"retryable\":false,\"cause_code\":\"retained-work-id-mismatch\"," +
                "\"callback_cause_code\":null,\"note\":null}",
            foreign.diagnostics.messages.single(),
        )

        val unresolvable = Fixture()
        unresolvable.register(PendingReminder(ScheduleRoute("   ", ROUTINE), DUE_AT))

        assertEquals(
            "{\"event\":\"schedule-reminder\",\"stage\":\"input\"," +
                "\"occurrence_id\":\"missing\",\"type\":\"ROUTINE\",\"generation_number\":null," +
                "\"work_request_id\":null,\"retained_work_request_id\":null,\"retryable\":false," +
                "\"cause_code\":\"invalid-route\",\"callback_cause_code\":null,\"note\":null}",
            unresolvable.diagnostics.messages.single(),
        )

        // The record is a public type, so this boundary is handed halves of identities and
        // collisions that correlate with nothing. Each is dropped rather than published.
        val uncorrelated = listOf(
            ReminderRegistrationRecord(ReminderRegistrationIdentity("not-a-digest", 0L), ROUTINE, RECEIPT_CONTENDED) to
                "\"occurrence_id\":\"missing\",\"type\":\"ROUTINE\",\"generation_number\":0," +
                    "\"work_request_id\":null,\"retained_work_request_id\":null",
            ReminderRegistrationRecord(ReminderRegistrationIdentity(OCCURRENCE_ID, -1L), ROUTINE, RECEIPT_CONTENDED) to
                "\"occurrence_id\":\"$OCCURRENCE_ID\",\"type\":\"ROUTINE\",\"generation_number\":null," +
                    "\"work_request_id\":null,\"retained_work_request_id\":null",
            ReminderRegistrationRecord(null, ROUTINE, RECEIPT_CONTENDED, retainedWorkRequestId = FOREIGN_WORK_ID) to
                "\"occurrence_id\":\"missing\",\"type\":\"ROUTINE\",\"generation_number\":null," +
                    "\"work_request_id\":null,\"retained_work_request_id\":null",
            // A collision that is this registration's own work under a second name, which would
            // otherwise be published as a conflict with a request that does not exist.
            ReminderRegistrationRecord(IDENTITY_0, ROUTINE, RECEIPT_CONTENDED, retainedWorkRequestId = WORK_ID_0) to
                "\"occurrence_id\":\"$OCCURRENCE_ID\",\"type\":\"ROUTINE\",\"generation_number\":0," +
                    "\"work_request_id\":\"40fe7461-9be1-3ce7-8bdf-28b48b76359e\"," +
                    "\"retained_work_request_id\":null",
        )

        uncorrelated.forEach { (record, correlated) ->
            assertEquals(
                "{\"event\":\"schedule-reminder\",\"stage\":\"receipt\"," + correlated +
                    ",\"retryable\":true,\"cause_code\":\"receipt-contended\"," +
                    "\"callback_cause_code\":null,\"note\":null}",
                reminderRegistrationMessage(record),
                record.identity.toString(),
            )
        }
    }

    @Test
    fun `every retained answer reconciles, and a race to enqueued never becomes a false admission`() {
        val cases = mapOf(
            "enqueued" to Retained(retained(WorkInfo.State.ENQUEUED), RETAINED_WORK_ENQUEUED, ENQUEUED),
            "running" to Retained(retained(WorkInfo.State.RUNNING), RETAINED_WORK_ENQUEUED, ENQUEUED),
            "blocked" to Retained(retained(WorkInfo.State.BLOCKED), RETAINED_WORK_BLOCKED, QUARANTINED),
            "succeeded" to Retained(
                retained(WorkInfo.State.SUCCEEDED), RETAINED_WORK_SUCCEEDED_WITHOUT_RECEIPT, QUARANTINED,
            ),
            "failed" to Retained(retained(WorkInfo.State.FAILED), RETAINED_WORK_FAILED, TERMINAL),
            "cancelled" to Retained(retained(WorkInfo.State.CANCELLED), RETAINED_WORK_CANCELLED, TERMINAL),
            "duplicated" to Retained(
                retained(WorkInfo.State.ENQUEUED) + retained(WorkInfo.State.RUNNING),
                RETAINED_WORK_DUPLICATE, QUARANTINED,
            ),
            "foreign active" to Retained(
                listOf(RetainedWork(FOREIGN_WORK_ID, WorkInfo.State.RUNNING)),
                RETAINED_WORK_ID_MISMATCH, QUARANTINED,
            ),
            "foreign beside this one" to Retained(
                retained(WorkInfo.State.ENQUEUED) + RetainedWork(FOREIGN_WORK_ID, WorkInfo.State.RUNNING),
                RETAINED_WORK_ID_MISMATCH, QUARANTINED,
            ),
        )

        cases.forEach { (label, case) ->
            val plain = Fixture(ADMISSION_PENDING, retained = case.work)

            assertEquals(case.settles, plain.register(), label)
            assertEquals(case.phase, plain.phase(), label)
            assertEquals(emptyList(), plain.enqueue.submissions, label)
            assertEquals(case.settles, plain.diagnostics.records.single().cause, label)

            val raced = Fixture(ADMISSION_PENDING, retained = case.work)
            // The receipt reaches ENQUEUED between this query and the transition it decided on.
            // Only a caller that was itself establishing admission may read that as one.
            raced.query.beforeAnswer = {
                raced.store.compareAndSet(OCCURRENCE_ID, 0L, WORK_ID_0, ADMISSION_PENDING, ENQUEUED, null)
            }
            val admissible = case.settles == RETAINED_WORK_ENQUEUED

            assertEquals(
                if (admissible) ADMISSION_ALREADY_RECORDED else RECEIPT_CONTENDED,
                raced.register(),
                label,
            )
        }
    }

    @Test
    fun `a lost compare and set keeps the admission its call site proved and reads only for closure`() {
        val confirmed = Fixture(ADMISSION_PENDING, retained = retained(WorkInfo.State.ENQUEUED))
        // The matching worker takes this generation out of ADMISSION_PENDING between the retained
        // work query and the transition that answer decided on, through the production runner over
        // this same store, and leaves it in RETRYABLE rather than in the phase confirm expects.
        confirmed.query.beforeAnswer = {
            confirmed.permission.granted = false
            assertEquals(ReminderRunOutcome.RETRY, confirmed.runWorker(0L))
        }

        assertEquals(ADMISSION_ALREADY_RECORDED, confirmed.register())
        assertEquals(ADMITTED, ADMISSION_ALREADY_RECORDED.outcome)
        assertEquals(RETRYABLE, confirmed.phase())

        // The same race at a call site whose retained work contradicts admission stays contention.
        val contended = Fixture(ADMISSION_PENDING, retained = retained(WorkInfo.State.BLOCKED))
        contended.query.beforeAnswer = {
            contended.permission.granted = false
            assertEquals(ReminderRunOutcome.RETRY, contended.runWorker(0L))
        }

        assertEquals(RECEIPT_CONTENDED, contended.register())
        assertEquals(RETRYABLE_FAILURE, RECEIPT_CONTENDED.outcome)

        // Closure still outranks the evidence: this worker delivered before confirm could write.
        val closed = Fixture(ADMISSION_PENDING, retained = retained(WorkInfo.State.RUNNING))
        closed.query.beforeAnswer = { assertEquals(ReminderRunOutcome.SUCCESS, closed.runWorker(0L)) }

        assertEquals(OCCURRENCE_CLOSED, closed.register())
        assertEquals(SKIPPED, OCCURRENCE_CLOSED.outcome)
        assertEquals(DELIVERED, closed.phase())
    }

    @Test
    fun `finished work of another generation does not stop this generation from enqueueing`() {
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
    fun `each enqueue callback outcome settles under its own cause`() {
        val cases = listOf(
            Triple(ReminderEnqueueSignal.Confirmed, CALLBACK_CONFIRMED_ADMISSION, ENQUEUED),
            Triple(ReminderEnqueueSignal.Absent, ENQUEUE_CALLBACK_NULL, RETRYABLE),
            Triple(ReminderEnqueueSignal.Reported(IOException("refused")), ENQUEUE_CALLBACK_ERROR, RETRYABLE),
            Triple(ReminderEnqueueSignal.Raised(IllegalStateException("gone")), ENQUEUE_CALLBACK_THROWABLE, RETRYABLE),
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
    fun `a fatal submission terminates, a transient one stays pending, and both clear the flight`() {
        val fatal = Fixture(ADMISSION_PENDING, submitFailure = SecurityException("no scheduler access"))
        val transient = Fixture(ADMISSION_PENDING, submitFailure = IOException("work database locked"))

        assertEquals(ENQUEUE_SUBMIT_FATAL, fatal.register())
        assertEquals(TERMINAL, fatal.phase())

        assertEquals(ENQUEUE_SUBMIT_TRANSIENT, transient.register())
        assertEquals(ADMISSION_PENDING, transient.phase())

        // Neither produced an operation, so both re-coordinate.
        transient.enqueue.failure = null

        assertEquals(CALLBACK_CONFIRMED_ADMISSION, transient.register())
        assertEquals(1, transient.enqueue.submissions.size)
        assertEquals(OCCURRENCE_CLOSED, fatal.register())
        assertEquals(emptyList(), fatal.enqueue.submissions)
    }

    @Test
    fun `a worker that proved admission is never downgraded by a later callback or the watchdog`() {
        val after = ENQUEUE_CALLBACK_AFTER_WORKER_STARTED
        val cases = listOf(
            WorkerFirst(ReminderEnqueueSignal.Confirmed, WORKER_CONFIRMED_ADMISSION, null),
            WorkerFirst(ReminderEnqueueSignal.Absent, after, ENQUEUE_CALLBACK_NULL),
            WorkerFirst(ReminderEnqueueSignal.Reported(IOException("no")), after, ENQUEUE_CALLBACK_ERROR),
            WorkerFirst(ReminderEnqueueSignal.Raised(IllegalStateException("x")), after, ENQUEUE_CALLBACK_THROWABLE),
            WorkerFirst(null, WORKER_CONFIRMED_ADMISSION, null),
        )

        cases.forEach { case ->
            val fixture = Fixture(ADMISSION_PENDING, signal = null)
            val settled = fixture.registerDeferred()
            // The platform started the matching worker before it answered the submission, so the
            // runner takes this occurrence out of ADMISSION_PENDING under this very generation.
            assertEquals(
                ReminderRunOutcome.SUCCESS,
                fixture.runWorker(fixture.enqueue.submissions.single().request),
                case.label,
            )

            if (case.signal == null) fixture.expireWatchdog() else fixture.enqueue.answer(case.signal)

            assertEquals(case.settles, settled.single(), case.label)
            assertEquals(ADMITTED, settled.single().outcome, case.label)
            assertEquals(DELIVERED, fixture.phase(), case.label)
            assertEquals(
                ReminderRegistrationRecord(IDENTITY_0, ROUTINE, case.settles, case.reported),
                fixture.diagnostics.records.single(),
                case.label,
            )
        }
    }

    @Test
    fun `a failure callback the watchdog beat to a worker proof is classed as the winner would be`() {
        val fixture = Fixture(signal = null)
        val settled = fixture.registerDeferred()
        assertEquals(ReminderRunOutcome.SUCCESS, fixture.runWorker(fixture.enqueue.submissions.single().request))
        fixture.expireWatchdog()
        val committed = fixture.preferences.commits

        fixture.enqueue.answer(ReminderEnqueueSignal.Reported(IOException("refused")))

        // One fact, one classification: which of the two got there first changed nothing here.
        assertEquals(WORKER_CONFIRMED_ADMISSION, settled.single())
        assertEquals(DELIVERED, fixture.phase())
        assertEquals(committed, fixture.preferences.commits)
        assertEquals(
            listOf(
                ReminderRegistrationRecord(IDENTITY_0, ROUTINE, WORKER_CONFIRMED_ADMISSION),
                ReminderRegistrationRecord(
                    IDENTITY_0, ROUTINE, ENQUEUE_CALLBACK_AFTER_WORKER_STARTED, ENQUEUE_CALLBACK_ERROR,
                    note = LATE_CALLBACK,
                ),
            ),
            fixture.diagnostics.records.toList(),
        )

        // A late callback that confirms is itself an admission, so it keeps its own class.
        val confirming = Fixture(signal = null)
        confirming.registerDeferred()
        val request = confirming.enqueue.submissions.single().request
        assertEquals(ReminderRunOutcome.SUCCESS, confirming.runWorker(request))
        confirming.expireWatchdog()

        confirming.enqueue.answer(ReminderEnqueueSignal.Confirmed)

        assertEquals(
            ReminderRegistrationRecord(IDENTITY_0, ROUTINE, CALLBACK_CONFIRMED_ADMISSION, note = LATE_CALLBACK),
            confirming.diagnostics.records.last(),
        )
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
        // Filed under the generation this flight submitted, naming the one that replaced it: the
        // other way round reads as though the newer generation were the one superseded.
        assertEquals(
            ReminderRegistrationRecord(IDENTITY_0, ROUTINE, GENERATION_SUPERSEDED, retainedWorkRequestId = WORK_ID_1),
            fixture.diagnostics.records.single(),
        )

        // The same reading reached without a callback to prove anything, from a confirm that lost.
        val overtaken = Fixture(ADMISSION_PENDING, retained = retained(WorkInfo.State.ENQUEUED))
        overtaken.query.beforeAnswer = { overtaken.supersedeGeneration() }

        assertEquals(GENERATION_SUPERSEDED, overtaken.register())
        assertEquals(
            ReminderRegistrationRecord(IDENTITY_0, ROUTINE, GENERATION_SUPERSEDED, retainedWorkRequestId = WORK_ID_1),
            overtaken.diagnostics.records.single(),
        )
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

    @Test
    fun `concurrent registrations coalesce and a cross thread callback settles each waiter once`() {
        val fixture = Fixture(signal = null)
        val start = CountDownLatch(1)
        val calls = AtomicIntegerArray(WAITERS)
        val settled = Collections.synchronizedList(mutableListOf<ReminderRegistrationCause>())
        val threads = (0 until WAITERS).map { index ->
            Thread {
                start.await()
                fixture.scheduler.register(PendingReminder(ROUTE, DUE_AT)) { cause ->
                    calls.incrementAndGet(index)
                    settled += cause
                }
            }
        }

        threads.forEach { it.start() }
        start.countDown()
        threads.forEach { it.join() }

        assertEquals(1, fixture.preferences.commits)
        assertEquals(1, fixture.query.names.size)
        assertEquals(1, fixture.enqueue.submissions.size)
        assertEquals(emptyList(), settled)

        val answering = Thread { fixture.enqueue.answer(ReminderEnqueueSignal.Confirmed) }
        answering.start()
        answering.join()

        assertEquals(List(WAITERS) { 1 }, (0 until WAITERS).map { calls.get(it) })
        assertEquals(setOf(CALLBACK_CONFIRMED_ADMISSION), settled.toSet())
        assertEquals(ENQUEUED, fixture.phase())
        assertEquals(
            ReminderRegistrationRecord(IDENTITY_0, ROUTINE, CALLBACK_CONFIRMED_ADMISSION),
            fixture.diagnostics.records.single(),
        )
    }

    @Test
    fun `a joining registration returns before the owner has submitted and settles with it`() {
        val fixture = Fixture(signal = null)
        val settled = Collections.synchronizedList(mutableListOf<ReminderRegistrationCause>())
        var submissionsWhenJoined = -1
        // Park the owner between query and submission: the only window that exposes the order.
        fixture.query.beforeAnswer = {
            val joiner = Thread {
                fixture.scheduler.register(PendingReminder(ROUTE, DUE_AT)) { cause -> settled += cause }
            }
            joiner.start()
            joiner.join()
            submissionsWhenJoined = fixture.enqueue.submissions.size
        }

        fixture.scheduler.register(PendingReminder(ROUTE, DUE_AT)) { cause -> settled += cause }

        assertEquals(0, submissionsWhenJoined)
        assertEquals(1, fixture.enqueue.submissions.size)

        fixture.enqueue.answer(ReminderEnqueueSignal.Confirmed)

        assertEquals(listOf(CALLBACK_CONFIRMED_ADMISSION, CALLBACK_CONFIRMED_ADMISSION), settled)
    }

    @Test
    fun `a waiter that throws starves neither the remaining waiters nor the diagnostic`() {
        val fixture = Fixture(signal = null)
        val calls = AtomicIntegerArray(3)
        val reminder = PendingReminder(ROUTE, DUE_AT)
        fixture.scheduler.register(reminder) { calls.incrementAndGet(0) }
        fixture.scheduler.register(reminder) { cause ->
            calls.incrementAndGet(1)
            // Not an Exception: narrowing the catch to Exception cannot stay green.
            throw AssertionError("misbehaving caller, got $cause")
        }
        fixture.scheduler.register(reminder) { calls.incrementAndGet(2) }

        fixture.enqueue.answer(ReminderEnqueueSignal.Confirmed)

        assertEquals(listOf(1, 1, 1), (0 until 3).map { calls.get(it) })
        // The caller's failure is reported rather than swallowed, and reported without a word of
        // it: the exact rendering below is the whole record, so the thrown text cannot be in it.
        assertEquals(
            listOf(
                ReminderRegistrationRecord(IDENTITY_0, ROUTINE, CALLBACK_CONFIRMED_ADMISSION),
                ReminderRegistrationRecord(
                    IDENTITY_0, ROUTINE, CALLBACK_CONFIRMED_ADMISSION, note = WAITER_FAILED,
                ),
            ),
            fixture.diagnostics.records.toList(),
        )
        assertEquals(
            "{\"event\":\"schedule-reminder\",\"stage\":\"receipt\"," +
                "\"occurrence_id\":\"$OCCURRENCE_ID\",\"type\":\"ROUTINE\",\"generation_number\":0," +
                "\"work_request_id\":\"40fe7461-9be1-3ce7-8bdf-28b48b76359e\"," +
                "\"retained_work_request_id\":null,\"retryable\":false," +
                "\"cause_code\":\"callback-confirmed-admission\",\"callback_cause_code\":null," +
                "\"note\":\"waiter-failed\"}",
            fixture.diagnostics.messages.last(),
        )
    }

    @Test
    fun `a watchdog that wakes early reschedules the remainder instead of settling`() {
        val fixture = Fixture(signal = null)

        val settled = fixture.registerDeferred()
        assertEquals(listOf(WATCHDOG_NANOS), fixture.watchdog.scheduled)

        fixture.watchdog.nanos = EARLY_NANOS
        fixture.watchdog.fire()

        assertEquals(listOf(WATCHDOG_NANOS, WATCHDOG_NANOS - EARLY_NANOS), fixture.watchdog.scheduled)
        assertEquals(emptyList(), settled)
        assertEquals(ADMISSION_PENDING, fixture.phase())
        assertEquals(1, fixture.preferences.commits)
        assertEquals(1, fixture.enqueue.submissions.size)
    }

    @Test
    fun `an expired watchdog settles every waiter as retryable, keeps the receipt and clears the flight`() {
        val fixture = Fixture(signal = null)
        val settled = fixture.registerDeferred()

        fixture.expireWatchdog()

        assertEquals(ENQUEUE_CALLBACK_TIMEOUT, settled.single())
        assertEquals(RETRYABLE_FAILURE, settled.single().outcome)
        assertEquals(ADMISSION_PENDING, fixture.phase())
        assertEquals(1, fixture.enqueue.submissions.size)
        assertEquals(1, fixture.preferences.commits)

        fixture.registerDeferred()

        assertEquals(2, fixture.enqueue.submissions.size)
    }

    @Test
    fun `an expired watchdog reports unreadable, uncertain and superseded receipts under their own cause`() {
        val quarantined = Fixture(signal = null)
        val quarantinedSettled = quarantined.registerDeferred()
        quarantined.preferences.tamper(STORE_KEY, "reminder-receipts/v2")
        quarantined.expireWatchdog()

        assertEquals(RECEIPT_QUARANTINED, quarantinedSettled.single())
        // Evidence that cannot be read still leaves the generation this flight submitted under
        // known, so the record names it rather than publishing no identity at all.
        assertEquals(
            ReminderRegistrationRecord(IDENTITY_0, ROUTINE, RECEIPT_QUARANTINED),
            quarantined.diagnostics.records.single(),
        )

        // Evidence this flight reserved cannot simply be absent either, so a store that lost it
        // fails closed the same way rather than reading as an operation still running.
        val absent = Fixture(signal = null)
        val absentSettled = absent.registerDeferred()
        absent.preferences.wipe()
        absent.expireWatchdog()

        assertEquals(RECEIPT_QUARANTINED, absentSettled.single())
        assertEquals(
            ReminderRegistrationRecord(IDENTITY_0, ROUTINE, RECEIPT_QUARANTINED),
            absent.diagnostics.records.single(),
        )

        val uncertain = Fixture(signal = null)
        val uncertainSettled = uncertain.registerDeferred()
        uncertain.preferences.commitsBeforeFailure = uncertain.preferences.commits
        uncertain.store.compareAndSet(OCCURRENCE_ID, 0L, WORK_ID_0, ADMISSION_PENDING, RETRYABLE, null)
        uncertain.expireWatchdog()

        assertEquals(RECEIPT_WRITE_UNCERTAIN, uncertainSettled.single())
        assertEquals(
            ReminderRegistrationRecord(IDENTITY_0, ROUTINE, RECEIPT_WRITE_UNCERTAIN),
            uncertain.diagnostics.records.single(),
        )

        val superseded = Fixture(signal = null)
        val supersededSettled = superseded.registerDeferred()
        superseded.supersedeGeneration()
        superseded.expireWatchdog()

        assertEquals(GENERATION_SUPERSEDED, supersededSettled.single())
        assertEquals(1L, superseded.generation())
        assertEquals(
            ReminderRegistrationRecord(IDENTITY_0, ROUTINE, GENERATION_SUPERSEDED, retainedWorkRequestId = WORK_ID_1),
            superseded.diagnostics.records.single(),
        )

        val resumed = Fixture(ENQUEUED, signal = null)
        val resumedSettled = resumed.registerDeferred()
        resumed.expireWatchdog()

        assertEquals(ENQUEUE_CALLBACK_TIMEOUT, resumedSettled.single())
        assertEquals(ENQUEUED, resumed.phase())
    }

    @Test
    fun `a callback that arrives after the flight settled changes no waiter and no receipt`() {
        val fixture = Fixture(signal = null)
        val settled = fixture.registerDeferred()
        fixture.expireWatchdog()

        fixture.enqueue.answer(ReminderEnqueueSignal.Reported(IOException("refused")))

        assertEquals(ENQUEUE_CALLBACK_TIMEOUT, settled.single())
        assertEquals(ADMISSION_PENDING, fixture.phase())
        assertEquals(1, fixture.preferences.commits)
        // The late arrival is still recorded, under the class it actually reported.
        assertEquals(ENQUEUE_CALLBACK_TIMEOUT, fixture.diagnostics.records.first().cause)
        // Nothing here proved an admission, so this late arrival keeps its own class.
        assertEquals(
            ReminderRegistrationRecord(IDENTITY_0, ROUTINE, ENQUEUE_CALLBACK_ERROR, note = LATE_CALLBACK),
            fixture.diagnostics.records.last(),
        )
    }

    @Test
    fun `a callback overtaken by a newer generation settles its own waiters and clears the flight`() {
        val fixture = Fixture(signal = null)
        val overtaken = fixture.registerDeferred()
        fixture.supersedeGeneration()
        val recovered = fixture.preferences.commits

        fixture.enqueue.answer(ReminderEnqueueSignal.Confirmed)

        assertEquals(GENERATION_SUPERSEDED, overtaken.single())
        assertEquals(1L, fixture.generation())
        assertEquals(ADMISSION_PENDING, fixture.phase())
        assertEquals(recovered, fixture.preferences.commits)

        val next = fixture.registerDeferred()

        assertEquals(2, fixture.enqueue.submissions.size)
        assertEquals(emptyList(), next)
    }

    /** One occurrence, its store and the ports, wired as the production factory wires them. */
    private class Fixture(
        phase: ReminderPhase? = null,
        signal: ReminderEnqueueSignal? = ReminderEnqueueSignal.Confirmed,
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
        val watchdog = ManualWatchdog()
        val permission = TogglePermission()
        val scheduler = ReminderScheduler(
            store, enqueue, query, permission, diagnostics, watchdog, Clock.fixed(now, ZoneOffset.UTC),
        )

        /** Registers and returns the single cause its waiter received, for paths that settle at once. */
        fun register(reminder: PendingReminder = PendingReminder(ROUTE, DUE_AT)): ReminderRegistrationCause =
            registerDeferred(reminder).single()

        /** Registers and hands back the list its waiter appends to, which stays empty until settled. */
        fun registerDeferred(
            reminder: PendingReminder = PendingReminder(ROUTE, DUE_AT),
        ): List<ReminderRegistrationCause> {
            val settled = Collections.synchronizedList(mutableListOf<ReminderRegistrationCause>())
            scheduler.register(reminder) { cause -> settled += cause }
            return settled
        }

        /** Moves the injected time source past the deadline and wakes the watchdog, as a timer would. */
        fun expireWatchdog() {
            watchdog.nanos += WATCHDOG_NANOS
            watchdog.fire()
        }

        /** Runs the merged production runner over this store, reading only what the request carries. */
        fun runWorker(request: OneTimeWorkRequest): ReminderRunOutcome {
            val input = request.workSpec.input
            return runWorker(
                ReminderWorkInput(
                    occurrenceId = input.getString(ReminderWorkKeys.OCCURRENCE_ID),
                    propertyId = input.getString(ReminderWorkKeys.PROPERTY_ID),
                    inspectionType = input.getString(ReminderWorkKeys.INSPECTION_TYPE),
                    dueAt = input.getString(ReminderWorkKeys.DUE_AT),
                    generationNumber = input.getString(ReminderWorkKeys.GENERATION_NUMBER),
                    workRequestId = request.id,
                ),
            )
        }

        /** The same runner from written out input, for a race with no submission to borrow one from. */
        fun runWorker(generation: Long): ReminderRunOutcome = runWorker(
            ReminderWorkInput(
                OCCURRENCE_ID, PROPERTY, "ROUTINE", DUE_AT_TEXT, generation.toString(), workId(generation),
            ),
        )

        private fun runWorker(input: ReminderWorkInput): ReminderRunOutcome = ReminderDeliveryRunner(
            store, permission, Preparation(), Notifier(), NoOpReminderDiagnosticPort,
        ).run(input, sdkInt = 35, runAttemptCount = 0)

        /** Drives this occurrence to the next generation through the store's own public API. */
        fun supersedeGeneration() {
            store.compareAndSet(OCCURRENCE_ID, 0L, WORK_ID_0, ADMISSION_PENDING, ENQUEUED, null)
            store.compareAndSet(OCCURRENCE_ID, 0L, WORK_ID_0, ENQUEUED, PERMISSION_BLOCKED, null)
            store.recoverPermissionBlocked(receipt(PERMISSION_BLOCKED))
        }

        /** Recovers this blocked occurrence, as another registration reaching it first would. */
        fun recoverOnce() {
            present()?.let { store.recoverPermissionBlocked(it) }
        }

        /**
         * Recovers this blocked occurrence and blocks it again, which is what another registration
         * plus a delivery run that still finds no grant leave behind, one generation further on.
         */
        fun blockNextGeneration() {
            val blocked = present() ?: return
            store.recoverPermissionBlocked(blocked)
            val next = blocked.generationNumber + 1
            store.compareAndSet(OCCURRENCE_ID, next, workId(next), ADMISSION_PENDING, ENQUEUED, null)
            store.compareAndSet(OCCURRENCE_ID, next, workId(next), ENQUEUED, PERMISSION_BLOCKED, null)
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

    /** The runtime grant, which the user may withdraw or give between any two reads of it. */
    private class TogglePermission : ReminderPermissionPort {
        var granted = true
        var checks = 0
            private set

        /** What another actor does while this grant is being read. */
        var onCheck: () -> Unit = {}
        private var acting = false

        override fun isPostNotificationsGranted(): Boolean {
            checks++
            // That other actor reads this same grant, and its reads are its own, not another
            // occasion for it to act.
            if (!acting) {
                acting = true
                try {
                    onCheck()
                } finally {
                    acting = false
                }
            }
            return granted
        }
    }

    private class Submission(val name: String, val policy: ExistingWorkPolicy, val request: OneTimeWorkRequest)

    /** Accepting and answering are two moments: a null signal leaves the operation in flight. */
    private class RecordingEnqueue(private val signal: ReminderEnqueueSignal?, var failure: Throwable?) :
        ReminderEnqueuePort {
        val submissions = Collections.synchronizedList(mutableListOf<Submission>())
        private val pending = Collections.synchronizedList(mutableListOf<(ReminderEnqueueSignal) -> Unit>())

        /** What the platform does between accepting the submission and answering it. */
        var beforeSignal: (OneTimeWorkRequest) -> Unit = {}

        override fun submitUnique(
            name: String,
            policy: ExistingWorkPolicy,
            request: OneTimeWorkRequest,
            onSettled: (ReminderEnqueueSignal) -> Unit,
        ) {
            failure?.let { throw it }
            submissions += Submission(name, policy, request)
            beforeSignal(request)
            if (signal == null) pending += onSettled else onSettled(signal)
        }

        /** Answers one operation the platform left in flight, the most recent one by default. */
        fun answer(signal: ReminderEnqueueSignal, submission: Int = pending.lastIndex) {
            pending.removeAt(submission)(signal)
        }
    }

    private class RecordingQuery(private val retained: List<RetainedWork>, private val failure: Throwable?) :
        ReminderWorkQueryPort {
        val names = Collections.synchronizedList(mutableListOf<String>())

        /** What another actor does between this query and the transition its answer decides on. */
        var beforeAnswer: () -> Unit = {}

        override fun retainedWork(uniqueWorkName: String): List<RetainedWork> {
            names += uniqueWorkName
            failure?.let { throw it }
            beforeAnswer()
            return retained
        }
    }

    /** The one injected time source: one reading both schedules a deadline and judges it. */
    private class ManualWatchdog : ReminderWatchdogPort {
        var nanos = 0L
        val scheduled = Collections.synchronizedList(mutableListOf<Long>())
        private var pending: (() -> Unit)? = null

        override fun nowNanos(): Long = nanos

        override fun schedule(delayNanos: Long, wake: () -> Unit) {
            scheduled += delayNanos
            pending = wake
        }

        /** Fires the pending wake up, as a timer would. */
        fun fire() {
            val wake = pending
            pending = null
            wake?.invoke()
        }
    }

    /** One retained-work answer: what it settles as, and the receipt phase it leaves behind. */
    private class Retained(
        val work: List<RetainedWork>,
        val settles: ReminderRegistrationCause,
        val phase: ReminderPhase,
    )

    /** One way a flight ends after its worker already proved the admission. */
    private class WorkerFirst(
        val signal: ReminderEnqueueSignal?,
        val settles: ReminderRegistrationCause,
        val reported: ReminderRegistrationCause?,
    ) {
        val label: String get() = signal?.toString() ?: "watchdog"
    }

    /** Keeps both what was recorded and what the production renderer makes of it, as the log does. */
    private class RecordingDiagnostics : ReminderSchedulerDiagnosticPort {
        val records = Collections.synchronizedList(mutableListOf<ReminderRegistrationRecord>())
        val messages = Collections.synchronizedList(mutableListOf<String>())

        override fun record(record: ReminderRegistrationRecord) {
            records += record
            messages += reminderRegistrationMessage(record)
        }
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
        const val WAITERS = 8

        /** Thirty seconds, as the contract states it rather than as the implementation spells it. */
        const val WATCHDOG_NANOS = 30_000_000_000L
        const val EARLY_NANOS = 11_000_000_000L

        /** How many times one registration may re-read a recovery it lost, as the contract states it. */
        const val RECOVERY_READS = 3

        /**
         * The work request id of each generation of this occurrence, computed outside this project
         * from `reminder-work/v1`, the occurrence digest and the generation, and written out so a
         * derivation that changed could never agree with itself here.
         */
        val WORK_IDS: List<UUID> = listOf(
            UUID.fromString("40fe7461-9be1-3ce7-8bdf-28b48b76359e"),
            UUID.fromString("590ca815-2783-322a-acde-39ab31dafd39"),
            UUID.fromString("cd2c750b-f369-3ef4-a19f-368ee3f2d4e6"),
            UUID.fromString("832a81a5-7326-3ffa-b8cc-1d097fb73d08"),
            // One past the bound, so a bound that grew fails the count rather than this lookup.
            UUID.fromString("e827a4d6-c8c3-3ffb-9064-2f0d7092554a"),
        )
        val WORK_ID_0: UUID = WORK_IDS[0]
        val WORK_ID_1: UUID = WORK_IDS[1]
        val FOREIGN_WORK_ID: UUID = UUID.fromString("00000000-0000-3000-8000-0000000000ff")
        val DUE_AT: Instant = Instant.parse(DUE_AT_TEXT)
        val BEYOND_CAP: Instant = DUE_AT.minusSeconds(5_000_000_000_000_000)
        val LONG_MAX_PLUS_NANO: Instant = DUE_AT.minusMillis(Long.MAX_VALUE).minusNanos(1)
        val ROUTE = ScheduleRoute(PROPERTY, ROUTINE)
        val IDENTITY_0 = ReminderRegistrationIdentity(OCCURRENCE_ID, 0L)

        fun workId(generation: Long): UUID = WORK_IDS[generation.toInt()]

        fun identity(generation: Long) = ReminderRegistrationIdentity(OCCURRENCE_ID, generation)

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

        fun retained(state: WorkInfo.State): List<RetainedWork> = listOf(RetainedWork(WORK_ID_0, state))

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


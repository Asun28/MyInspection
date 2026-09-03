package nz.myinspection.app.feature.schedule

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import nz.myinspection.app.feature.schedule.ReminderPhase.ADMISSION_PENDING
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
import nz.myinspection.app.feature.schedule.ReminderRegistrationOutcome.RETRYABLE_FAILURE
import nz.myinspection.core.schedule.InspectionScheduleType

/** What a registration achieved. Every cause below maps onto exactly one of these. */
enum class ReminderRegistrationOutcome {
    ADMITTED,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE,
    SKIPPED,
}

/**
 * Why a registration ended where it did. The outcome is carried by the cause rather than beside it,
 * so the two can never disagree. The receipt store keeps its own closed vocabulary and puts a cause
 * only on TERMINAL, so these richer reasons live in the result and its diagnostic, never in the
 * stored receipt.
 */
enum class ReminderRegistrationCause(val outcome: ReminderRegistrationOutcome) {
    CALLBACK_CONFIRMED_ADMISSION(ReminderRegistrationOutcome.ADMITTED),
    WORKER_CONFIRMED_ADMISSION(ReminderRegistrationOutcome.ADMITTED),
    ENQUEUE_CALLBACK_AFTER_WORKER_STARTED(ReminderRegistrationOutcome.ADMITTED),
    RETAINED_WORK_ENQUEUED(ReminderRegistrationOutcome.ADMITTED),
    ADMISSION_ALREADY_RECORDED(ReminderRegistrationOutcome.ADMITTED),
    ENQUEUE_CALLBACK_NULL(ReminderRegistrationOutcome.RETRYABLE_FAILURE),
    ENQUEUE_CALLBACK_ERROR(ReminderRegistrationOutcome.RETRYABLE_FAILURE),
    ENQUEUE_CALLBACK_THROWABLE(ReminderRegistrationOutcome.RETRYABLE_FAILURE),
    ENQUEUE_CALLBACK_TIMEOUT(ReminderRegistrationOutcome.RETRYABLE_FAILURE),
    ENQUEUE_SUBMIT_TRANSIENT(ReminderRegistrationOutcome.RETRYABLE_FAILURE),
    ENQUEUE_SUBMIT_FATAL(ReminderRegistrationOutcome.PERMANENT_FAILURE),
    RETAINED_WORK_QUERY_FAILED(ReminderRegistrationOutcome.RETRYABLE_FAILURE),
    RETAINED_WORK_BLOCKED(ReminderRegistrationOutcome.PERMANENT_FAILURE),
    RETAINED_WORK_SUCCEEDED_WITHOUT_RECEIPT(ReminderRegistrationOutcome.PERMANENT_FAILURE),
    RETAINED_WORK_FAILED(ReminderRegistrationOutcome.PERMANENT_FAILURE),
    RETAINED_WORK_CANCELLED(ReminderRegistrationOutcome.PERMANENT_FAILURE),
    RETAINED_WORK_ID_MISMATCH(ReminderRegistrationOutcome.PERMANENT_FAILURE),
    RETAINED_WORK_DUPLICATE(ReminderRegistrationOutcome.PERMANENT_FAILURE),
    RECEIPT_QUARANTINED(ReminderRegistrationOutcome.PERMANENT_FAILURE),
    RECEIPT_REJECTED(ReminderRegistrationOutcome.PERMANENT_FAILURE),
    RECEIPT_WRITE_UNCERTAIN(ReminderRegistrationOutcome.RETRYABLE_FAILURE),
    RECEIPT_CONTENDED(ReminderRegistrationOutcome.RETRYABLE_FAILURE),
    INVALID_ROUTE(ReminderRegistrationOutcome.PERMANENT_FAILURE),
    // The grant may arrive at any time, so a blocked occurrence waits for the next registration
    // rather than closing: nothing was written and nothing was scheduled.
    PERMISSION_NOT_GRANTED(ReminderRegistrationOutcome.RETRYABLE_FAILURE),
    RECEIPT_REREAD_EXHAUSTED(ReminderRegistrationOutcome.RETRYABLE_FAILURE),
    OCCURRENCE_CLOSED(ReminderRegistrationOutcome.SKIPPED),
    GENERATION_SUPERSEDED(ReminderRegistrationOutcome.SKIPPED),
}

/**
 * What a record reports beside the settlement it names. Both values mark a record that changed no
 * waiter and no receipt, and they are one field because a record can only be one of them: the
 * settlement itself carries no note, so a note is exactly what makes a record an aside.
 */
enum class ReminderRegistrationNote {
    LATE_CALLBACK,
    WAITER_FAILED,
}

/**
 * The occurrence and generation a registration settled under. They are one value because half an
 * identity correlates with nothing: the work request id is derived from both wherever it is
 * needed, so publishing either alone would publish a correlation that does not exist.
 */
data class ReminderRegistrationIdentity(val occurrenceId: String, val generationNumber: Long)

/**
 * One settled registration, as the log sees it. [identity] is absent whenever this registration
 * could not establish both halves. No property, date, path or exception text is ever carried.
 * [callbackCause] is the class the enqueue callback reported, kept beside [cause] when the two
 * differ, so a failure that arrived after the worker proved the admission stays legible without
 * downgrading it. [retainedWorkRequestId] is the work this settlement collided with rather than
 * this registration's own, which is what a late or cross generation reading is located by.
 * [causeClass] is the shared failure class of what was actually thrown, carried only where a real
 * Throwable reached this record, because the class of a thrown failure is a fact about that
 * failure while the cause above is a fact about the answer this registration reached. On a record
 * marked [ReminderRegistrationNote.WAITER_FAILED] the Throwable that reached it is the waiter's,
 * which is what that record is about, so it is that waiter's class this carries.
 */
data class ReminderRegistrationRecord(
    val identity: ReminderRegistrationIdentity?,
    val type: InspectionScheduleType,
    val cause: ReminderRegistrationCause,
    val callbackCause: ReminderRegistrationCause? = null,
    val retainedWorkRequestId: UUID? = null,
    val note: ReminderRegistrationNote? = null,
    val causeClass: FailureCauseCode? = null,
)

interface ReminderSchedulerDiagnosticPort {
    fun record(record: ReminderRegistrationRecord)
}

/**
 * What one submitted operation ended up reporting. [Absent], [Reported] and [Raised] are three
 * different facts and stay three: nothing was scheduled, the platform reported this failure, and
 * reading the outcome itself threw. A submission never answered at all is none of these, because
 * no answer arrived to classify, and it is the watchdog that ends such a flight.
 */
sealed interface ReminderEnqueueSignal {
    data object Confirmed : ReminderEnqueueSignal
    data object Absent : ReminderEnqueueSignal
    data class Reported(val error: Throwable) : ReminderEnqueueSignal
    data class Raised(val error: Throwable) : ReminderEnqueueSignal
}

/**
 * Submits unique work and answers that submission's own outcome through [onSettled], which may run
 * on any thread at any time after the submission was accepted, including before this call returns.
 * Throwing means the submission never produced an operation at all, so nothing is left to answer.
 */
interface ReminderEnqueuePort {
    fun submitUnique(
        name: String,
        policy: ExistingWorkPolicy,
        request: OneTimeWorkRequest,
        onSettled: (ReminderEnqueueSignal) -> Unit,
    )
}

/**
 * The one time source a flight's deadline is both set and judged by: scheduling from one clock and
 * deciding from another would let a wake up settle a deadline that has not passed.
 */
interface ReminderWatchdogPort {
    fun nowNanos(): Long

    fun schedule(delayNanos: Long, wake: () -> Unit)
}

/** One work request WorkManager still retains under a unique name. */
data class RetainedWork(val id: UUID, val state: WorkInfo.State)

interface ReminderWorkQueryPort {
    fun retainedWork(uniqueWorkName: String): List<RetainedWork>
}

/**
 * Registers one reminder occurrence with WorkManager.
 *
 * Registration reserves durable evidence, asks WorkManager what it still retains under this
 * occurrence's unique name, and submits only when that answer leaves no retained work of its own.
 * The query is what decides every submission, so a KEEP policy can never be read as admission of
 * work this generation did not put there. The call returns once that submission is accepted, and
 * the cause reaches the caller through its waiter.
 *
 * Concurrent registrations of one occurrence coalesce into a single flight, so one reservation,
 * one query and one submission serve all of them and every waiter is answered with the same cause.
 *
 * The scheduler shares one [ReminderReceiptStore] with the delivery runner: every cross actor
 * decision is an atomic result of admit, compare and set or permission recovery.
 */
class ReminderScheduler(
    private val store: ReminderReceiptStore,
    private val enqueue: ReminderEnqueuePort,
    private val query: ReminderWorkQueryPort,
    private val permissions: ReminderPermissionPort,
    private val diagnostics: ReminderSchedulerDiagnosticPort,
    private val watchdog: ReminderWatchdogPort,
    private val clock: Clock,
) {
    private val flights = mutableMapOf<String, Flight>()

    fun register(reminder: PendingReminder, waiter: (ReminderRegistrationCause) -> Unit) {
        val spec = try {
            reminder.toSpec()
        } catch (_: IllegalArgumentException) {
            null
        }
        if (spec == null) {
            // An unresolvable route has no occurrence to coalesce on, so it is answered here.
            val record = ReminderRegistrationRecord(null, reminder.route.inspectionType, INVALID_ROUTE)
            publish(record, listOf(waiter))
            return
        }
        val opened = synchronized(flights) {
            val joined = flights[spec.occurrenceId]
            if (joined != null) {
                joined.waiters += waiter
                null
            } else {
                Flight(spec, reminder.route.inspectionType).also { fresh ->
                    fresh.waiters += waiter
                    flights[spec.occurrenceId] = fresh
                }
            }
        }
        opened?.let { coordinate(it) }
    }

    private fun coordinate(flight: Flight) {
        val settled = when (val lookup = store.lookup(flight.spec.occurrenceId)) {
            ReminderReceiptLookup.Missing -> reserve(flight)
            is ReminderReceiptLookup.Quarantined -> Settlement(RECEIPT_QUARANTINED)
            // The stored receipt carries this same spec by construction: the occurrence digest
            // binds property, type and due instant, and the store refuses a receipt whose spec
            // does not match its own occurrence.
            is ReminderReceiptLookup.Present -> when {
                lookup.writeUncertain -> lookup.receipt.settle(RECEIPT_WRITE_UNCERTAIN)
                lookup.receipt.phase in ACTIVE_PHASES -> place(flight, lookup.receipt)
                lookup.receipt.phase == PERMISSION_BLOCKED -> recover(flight, lookup.receipt)
                else -> lookup.receipt.settle(OCCURRENCE_CLOSED)
            }
        }
        settled?.let { settle(flight) { it } }
    }

    /**
     * Reopens an occurrence a delivery run left blocked on the notification permission.
     *
     * The grant is read here, at the moment of recovery, because one read earlier says nothing
     * about now, and while it is missing this touches neither WorkManager nor the receipt. The
     * store derives the next generation and its work request id itself, so the re-registration
     * below runs under an identity nothing here could have drifted.
     *
     * Losing that write means the view this pass held was already stale, so the fresh one is tried
     * instead. Those passes are bounded: an occurrence another actor keeps blocking again would
     * otherwise be recovered from here forever, and an exhausted bound is its own answer rather
     * than the contention a single lost write reports.
     */
    private fun recover(flight: Flight, blocked: ReminderReceipt): Settlement? {
        var current = blocked
        repeat(MAX_RECOVERY_READS) {
            if (!permissions.isPostNotificationsGranted()) {
                return current.settle(PERMISSION_NOT_GRANTED)
            }
            val fresh = when (val result = store.recoverPermissionBlocked(current)) {
                is ReminderReceiptTransitionResult.Applied -> return place(flight, result.receipt)
                ReminderReceiptTransitionResult.WriteUncertain -> return current.settle(RECEIPT_WRITE_UNCERTAIN)
                // The store refuses a recovery of an occurrence it can no longer read as a receipt
                // at all, which a fresh read of that same occurrence would only confirm.
                ReminderReceiptTransitionResult.Rejected -> return current.settle(RECEIPT_REJECTED)
                is ReminderReceiptTransitionResult.Stale ->
                    (result.lookup as? ReminderReceiptLookup.Present)?.receipt
                        ?: return current.settle(RECEIPT_QUARANTINED)
            }
            // Someone else already recovered it, so this registration continues from their
            // generation rather than opening another one of its own.
            if (fresh.phase in ACTIVE_PHASES) {
                return place(flight, fresh)
            }
            if (fresh.phase != PERMISSION_BLOCKED) {
                return fresh.settle(OCCURRENCE_CLOSED)
            }
            current = fresh
        }
        return current.settle(RECEIPT_REREAD_EXHAUSTED)
    }

    /** Reserves generation zero. Only a durable reservation may be followed by a submission. */
    private fun reserve(flight: Flight): Settlement? {
        val reserved = ReminderReceipt(
            occurrenceId = flight.spec.occurrenceId,
            generationNumber = FIRST_GENERATION,
            workRequestId = reminderGenerationId(flight.spec.occurrenceId, FIRST_GENERATION),
            spec = flight.spec,
            phase = ADMISSION_PENDING,
            causeCode = null,
        )
        return when (store.admit(reserved)) {
            ReminderReceiptAdmissionResult.Admitted -> place(flight, reserved)
            ReminderReceiptAdmissionResult.WriteUncertain -> reserved.settle(RECEIPT_WRITE_UNCERTAIN)
            // Another registration reserved this occurrence between the lookup and the admission.
            // The evidence is sound, so this is contention rather than corruption.
            ReminderReceiptAdmissionResult.Rejected -> reserved.settle(RECEIPT_CONTENDED)
        }
    }

    /**
     * Reconciles retained work first: an absent current id is the only path to a submission. A
     * null return means this flight was handed to the platform and its callback owns the answer.
     */
    private fun place(flight: Flight, receipt: ReminderReceipt): Settlement? {
        val retained = try {
            query.retainedWork(receipt.spec.uniqueWorkName)
        } catch (failure: InterruptedException) {
            // The domain answer is still retryable, but the flag that tells this thread it was
            // interrupted belongs to the caller and is handed straight back.
            Thread.currentThread().interrupt()
            return receipt.failed(ReminderRegistrationCause.RETAINED_WORK_QUERY_FAILED, failure)
        } catch (failure: Throwable) {
            return receipt.failed(ReminderRegistrationCause.RETAINED_WORK_QUERY_FAILED, failure)
        }
        val current = retained.filter { it.id == receipt.workRequestId }
        val foreign = retained.filter { it.id != receipt.workRequestId && !it.state.isFinished }
        if (current.size > 1) {
            // Retained twice under this generation's own id, so there is no other work to name.
            return quarantine(receipt, ReminderRegistrationCause.RETAINED_WORK_DUPLICATE)
        }
        if (foreign.isNotEmpty()) {
            // Which foreign request the collision is with is only well defined when there is one.
            // Neither this port nor WorkManager promises an order over what it retains, so naming
            // one of several would name a different one from run to run over the same state.
            return quarantine(
                receipt, ReminderRegistrationCause.RETAINED_WORK_ID_MISMATCH, foreign.singleOrNull()?.id,
            )
        }
        val single = current.singleOrNull() ?: return submit(flight, receipt)
        return when (single.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING ->
                confirm(receipt, ReminderRegistrationCause.RETAINED_WORK_ENQUEUED)
            // This card's request has no prerequisite, so blocked work is evidence about work
            // nobody here asked for rather than a state to wait out.
            WorkInfo.State.BLOCKED -> quarantine(receipt, ReminderRegistrationCause.RETAINED_WORK_BLOCKED)
            WorkInfo.State.SUCCEEDED ->
                quarantine(receipt, ReminderRegistrationCause.RETAINED_WORK_SUCCEEDED_WITHOUT_RECEIPT)
            WorkInfo.State.FAILED -> terminate(receipt, ReminderRegistrationCause.RETAINED_WORK_FAILED)
            WorkInfo.State.CANCELLED -> terminate(receipt, ReminderRegistrationCause.RETAINED_WORK_CANCELLED)
        }
    }

    /**
     * Hands the request to the platform and arms this flight's single watchdog. The receipt reaches
     * the flight first, because the callback may answer on another thread before this returns.
     */
    private fun submit(flight: Flight, receipt: ReminderReceipt): Settlement? {
        flight.receipt = receipt
        val request = reminderWorkRequest(receipt.spec, receipt.generationNumber, clock.instant())
        try {
            enqueue.submitUnique(receipt.spec.uniqueWorkName, ExistingWorkPolicy.KEEP, request) { signal ->
                answer(flight, signal)
            }
        } catch (failure: Throwable) {
            return refuseSubmission(receipt, failure)
        }
        arm(flight)
        return null
    }

    /**
     * Only a permanent submission failure closes the occurrence: nothing was scheduled either way.
     * Both settle the flight, because a submission that produced no operation leaves nothing to
     * wait on and the next registration must re-coordinate rather than join it.
     */
    private fun refuseSubmission(receipt: ReminderReceipt, failure: Throwable): Settlement {
        val disposition = classifyReminderFailure(failure)
        return if (disposition.kind == FailureKind.PERMANENT) {
            terminate(receipt, ReminderRegistrationCause.ENQUEUE_SUBMIT_FATAL, disposition.causeCode)
        } else {
            receipt.failed(ReminderRegistrationCause.ENQUEUE_SUBMIT_TRANSIENT, failure)
        }
    }

    /** Arms the one watchdog a flight gets, unless its operation has already been answered. */
    private fun arm(flight: Flight) {
        synchronized(flights) {
            if (flights[flight.spec.occurrenceId] !== flight) {
                return
            }
            flight.deadlineNanos = watchdog.nowNanos() + WATCHDOG_NANOS
        }
        watchdog.schedule(WATCHDOG_NANOS) { wake(flight) }
    }

    /**
     * One watchdog wake up. The deadline is re-read from the same source that set it, so a wake up
     * that arrives early reschedules the remainder instead of settling. Nothing here submits.
     */
    private fun wake(flight: Flight) {
        val remaining = synchronized(flights) {
            if (flights[flight.spec.occurrenceId] !== flight) {
                return
            }
            flight.deadlineNanos - watchdog.nowNanos()
        }
        if (remaining > 0) {
            watchdog.schedule(remaining) { wake(flight) }
            return
        }
        settle(flight) { flight.receipt?.let { receipt -> expire(receipt) } }
    }

    /**
     * What an expired deadline settles as. A timeout is the answer only for this generation's own
     * receipt, still sitting exactly where this flight submitted it: an unreadable read, a write
     * this store never confirmed and a generation that moved on each say something a timeout would
     * erase, so each keeps its own cause. Nothing here writes: the operation may still be running.
     */
    private fun expire(receipt: ReminderReceipt): Settlement =
        when (val lookup = store.lookup(receipt.occurrenceId)) {
            // Evidence this flight reserved cannot simply be absent, so a missing or refused read
            // is corruption rather than a slow operation. A reading nothing can be made of still
            // leaves the generation this flight submitted under known, so it is filed under that.
            ReminderReceiptLookup.Missing -> receipt.settle(RECEIPT_QUARANTINED)
            is ReminderReceiptLookup.Quarantined -> receipt.settle(RECEIPT_QUARANTINED)
            is ReminderReceiptLookup.Present -> when {
                lookup.writeUncertain -> receipt.settle(RECEIPT_WRITE_UNCERTAIN)
                lookup.receipt.generationNumber != receipt.generationNumber ->
                    receipt.settle(GENERATION_SUPERSEDED, lookup.receipt.workRequestId)
                // Only the matching worker moves this generation while the flight is open, and the
                // platform runs it only once it has admitted the request.
                lookup.receipt.phase != receipt.phase ->
                    Settlement(WORKER_CONFIRMED_ADMISSION, receipt.generationNumber)
                else -> receipt.settle(ENQUEUE_CALLBACK_TIMEOUT)
            }
        }

    /**
     * One answered operation. A callback that no longer names the active flight changed nothing and
     * is recorded as the late arrival it is, under the class it reported: its flight was already
     * ended by the worker proof, the watchdog, or a newer generation.
     */
    private fun answer(flight: Flight, signal: ReminderEnqueueSignal) {
        val reported = signal.cause()
        val reportedClass = signal.reportedClass()
        val settled = settle(flight) {
            flight.receipt?.let { receipt ->
                val next = if (reported == CALLBACK_CONFIRMED_ADMISSION) ENQUEUED else RETRYABLE
                advance(receipt, next, null, reported, reported = reported, causeClass = reportedClass)
            }
        }
        if (settled == null) {
            diagnostics.record(flight.lateRecord(reported, reportedClass))
        }
    }

    /**
     * Ends [flight] under the flight lock, taking the waiters and clearing the flight before any of
     * them run. Null means this was no longer the active flight, so nothing was decided or written.
     */
    private fun settle(flight: Flight, decide: () -> Settlement?): Settlement? {
        val ended = synchronized(flights) {
            if (flights[flight.spec.occurrenceId] !== flight) {
                return null
            }
            val settled = decide() ?: return null
            flights.remove(flight.spec.occurrenceId)
            flight.settledCause = settled.cause
            settled to flight.waiters.toList()
        }
        publish(flight.record(ended.first), ended.second)
        return ended.first
    }

    /**
     * Reports one settlement to the log first and then to each waiter in isolation, so a waiter
     * that throws can starve neither the diagnostic nor the waiters after it. Not rethrowing is
     * the point: a waiter belongs to a caller, and one caller's failure says nothing about this
     * occurrence. It is still recorded, because a failure nobody can see is a bug nobody can find,
     * and recorded with the class of what was thrown but never a word of it: a class is a closed
     * vocabulary this record already publishes, while the text belongs to the caller that threw it.
     * That class replaces the settlement's own on this record alone, because this record exists on
     * account of the waiter, and the settlement was already published with its own class above.
     */
    private fun publish(
        record: ReminderRegistrationRecord,
        waiters: List<(ReminderRegistrationCause) -> Unit>,
    ) {
        diagnostics.record(record)
        waiters.forEach { waiter ->
            try {
                waiter(record.cause)
            } catch (failure: Throwable) {
                val thrown = classifyReminderFailure(failure).causeCode
                diagnostics.record(record.copy(note = WAITER_FAILED, causeClass = thrown))
            }
        }
    }

    private fun confirm(receipt: ReminderReceipt, applied: ReminderRegistrationCause): Settlement =
        advance(receipt, ENQUEUED, null, applied, admitted = true)

    private fun quarantine(
        receipt: ReminderReceipt,
        applied: ReminderRegistrationCause,
        retained: UUID? = null,
    ): Settlement = advance(receipt, QUARANTINED, null, applied, retained = retained)

    private fun terminate(
        receipt: ReminderReceipt,
        applied: ReminderRegistrationCause,
        causeClass: FailureCauseCode? = null,
    ): Settlement =
        advance(receipt, TERMINAL, ReminderCause.PERMANENT_DELIVERY_FAILURE, applied, causeClass = causeClass)

    /**
     * Applies one phase change, re-reading rather than overwriting when the store says the caller's
     * view is stale. A lost compare and set is reported rather than retried in a loop: the re-read
     * already names why it was lost, and the caller repeats a contended registration from the top.
     * [admitted] is true only where this call site was itself establishing admission, because a
     * caller acting on retained work that contradicts admission must not report one. [reported] is
     * set only by an enqueue callback, the one caller whose lost transition is proof. [retained]
     * names the work this decision collided with, and belongs to the applied answer alone: every
     * other answer here is about the receipt rather than about retained work. [causeClass] is the
     * class of the Throwable this caller reports, and travels only with an answer still reporting
     * it: the applied answer, or the admitted reading a lost callback still reports. An answer
     * about the receipt instead, such as a refused or an unconfirmed transition, drops it.
     */
    private fun advance(
        receipt: ReminderReceipt,
        next: ReminderPhase,
        cause: ReminderCause?,
        applied: ReminderRegistrationCause,
        admitted: Boolean = false,
        reported: ReminderRegistrationCause? = null,
        retained: UUID? = null,
        causeClass: FailureCauseCode? = null,
    ): Settlement {
        val result = store.compareAndSet(
            receipt.occurrenceId, receipt.generationNumber, receipt.workRequestId,
            receipt.phase, next, cause,
        )
        return when (result) {
            is ReminderReceiptTransitionResult.Applied -> receipt.settle(applied, retained, causeClass)
            // Rejected means the store forbids this transition, which this scheduler never asks
            // for, so it is a fail closed default rather than a reachable branch.
            ReminderReceiptTransitionResult.Rejected -> receipt.settle(RECEIPT_REJECTED)
            ReminderReceiptTransitionResult.WriteUncertain -> receipt.settle(RECEIPT_WRITE_UNCERTAIN)
            is ReminderReceiptTransitionResult.Stale -> reported
                ?.let { proved(result.lookup, receipt, it, causeClass) }
                ?: reread(result.lookup, receipt, admitted)
        }
    }

    /**
     * What a callback may conclude once its own transition was lost. While a flight is open the
     * only other writer of its generation is the matching worker, which the platform runs only
     * after admitting this request, so a loss under the same generation proves the admission. A
     * reported failure keeps its own class beside that proof rather than replacing it: failing to
     * read an operation says nothing about work already running.
     */
    private fun proved(
        lookup: ReminderReceiptLookup,
        current: ReminderReceipt,
        reported: ReminderRegistrationCause,
        causeClass: FailureCauseCode?,
    ): Settlement {
        // The store reports Stale only with the receipt it read under its own lock, and answers a
        // missing or unreadable occurrence with Rejected instead, so this is a fail closed default.
        val fresh = (lookup as? ReminderReceiptLookup.Present)?.receipt
            ?: return current.settle(RECEIPT_QUARANTINED)
        if (fresh.generationNumber != current.generationNumber) {
            // Filed under the generation this registration submitted, naming the one that replaced
            // it: the other way round reads as though the newer generation had been superseded.
            return current.settle(GENERATION_SUPERSEDED, fresh.workRequestId)
        }
        return if (reported == CALLBACK_CONFIRMED_ADMISSION) {
            fresh.settle(WORKER_CONFIRMED_ADMISSION)
        } else {
            Settlement(
                ENQUEUE_CALLBACK_AFTER_WORKER_STARTED, fresh.generationNumber, reported,
                causeClass = causeClass,
            )
        }
    }

    /**
     * Reports what replaced this registration's view of the receipt.
     *
     * A call site that was itself establishing admission held that evidence before this read: the
     * callback confirmed the operation, or retained work showed this generation ENQUEUED or
     * RUNNING. A phase that moved afterwards cannot unmake evidence gathered before it moved, so
     * the read decides only whether a newer generation or a closed occurrence overtook this
     * registration entirely. A caller whose retained work instead contradicted admission reads the
     * same movement as the contention it is.
     */
    private fun reread(
        lookup: ReminderReceiptLookup,
        current: ReminderReceipt,
        admitted: Boolean,
    ): Settlement {
        val fresh = (lookup as? ReminderReceiptLookup.Present)?.receipt
            ?: return current.settle(RECEIPT_QUARANTINED)
        return when {
            fresh.generationNumber != current.generationNumber ->
                current.settle(GENERATION_SUPERSEDED, fresh.workRequestId)
            fresh.phase !in ACTIVE_PHASES -> fresh.settle(OCCURRENCE_CLOSED)
            admitted -> fresh.settle(ADMISSION_ALREADY_RECORDED)
            else -> fresh.settle(RECEIPT_CONTENDED)
        }
    }
}

/**
 * One coordinated registration of one occurrence: its waiters, the receipt it submitted under and
 * the deadline its watchdog judges. The list is touched only under the scheduler's flight lock.
 */
private class Flight(val spec: ReminderSpec, val type: InspectionScheduleType) {
    val waiters = mutableListOf<(ReminderRegistrationCause) -> Unit>()

    @Volatile
    var receipt: ReminderReceipt? = null

    @Volatile
    var deadlineNanos: Long = 0L

    /** What ended this flight, which is what a callback arriving afterwards is read against. */
    @Volatile
    var settledCause: ReminderRegistrationCause? = null

    fun identity(): ReminderRegistrationIdentity? =
        receipt?.let { ReminderRegistrationIdentity(spec.occurrenceId, it.generationNumber) }

    fun record(settled: Settlement): ReminderRegistrationRecord = ReminderRegistrationRecord(
        settled.generationNumber?.let { ReminderRegistrationIdentity(spec.occurrenceId, it) },
        type,
        settled.cause,
        settled.reported,
        settled.retained,
        causeClass = settled.causeClass,
    )

    /**
     * How a callback that changed nothing is recorded. A flight already ended having proved this
     * generation was admitted classifies a failing callback exactly as the callback winning that
     * same race is classified, so which of the two arrived first cannot change what is reported.
     */
    fun lateRecord(
        reported: ReminderRegistrationCause,
        causeClass: FailureCauseCode?,
    ): ReminderRegistrationRecord {
        val proved = settledCause?.outcome == ADMITTED && reported != CALLBACK_CONFIRMED_ADMISSION
        return ReminderRegistrationRecord(
            identity(),
            type,
            if (proved) ENQUEUE_CALLBACK_AFTER_WORKER_STARTED else reported,
            reported.takeIf { proved },
            note = LATE_CALLBACK,
            causeClass = causeClass,
        )
    }
}

private data class Settlement(
    val cause: ReminderRegistrationCause,
    val generationNumber: Long? = null,
    val reported: ReminderRegistrationCause? = null,
    val retained: UUID? = null,
    val causeClass: FailureCauseCode? = null,
)

private fun ReminderReceipt.settle(
    cause: ReminderRegistrationCause,
    retained: UUID? = null,
    causeClass: FailureCauseCode? = null,
): Settlement = Settlement(cause, generationNumber, retained = retained, causeClass = causeClass)

/**
 * One settlement whose class is the class of what was thrown, rather than of the answer that
 * throw became: the two are different facts, and only the first says what actually went wrong.
 */
private fun ReminderReceipt.failed(
    cause: ReminderRegistrationCause,
    failure: Throwable,
): Settlement = settle(cause, causeClass = classifyReminderFailure(failure).causeCode)

/** The class an answered operation reported, which the callback carries wherever it lands. */
private fun ReminderEnqueueSignal.cause(): ReminderRegistrationCause = when (this) {
    ReminderEnqueueSignal.Confirmed -> CALLBACK_CONFIRMED_ADMISSION
    ReminderEnqueueSignal.Absent -> ENQUEUE_CALLBACK_NULL
    is ReminderEnqueueSignal.Reported -> ENQUEUE_CALLBACK_ERROR
    is ReminderEnqueueSignal.Raised -> ENQUEUE_CALLBACK_THROWABLE
}

/**
 * The shared failure class this callback carries wherever it lands: the class of what was thrown
 * where a real Throwable answered, and otherwise the class its own answer is of.
 *
 * It has to travel with the callback rather than be re-derived from the answer the record ends up
 * filed under, because a callback that arrives once the worker has proved the admission is filed
 * under that admission, and an admission is not a failure and names no class. Deriving it there
 * would make the class of one callback depend on which of the two got there first.
 */
private fun ReminderEnqueueSignal.reportedClass(): FailureCauseCode? = when (this) {
    is ReminderEnqueueSignal.Reported -> classifyReminderFailure(error).causeCode
    is ReminderEnqueueSignal.Raised -> classifyReminderFailure(error).causeCode
    // Nothing was thrown here, so the answer itself is what names a class, or names none.
    ReminderEnqueueSignal.Confirmed, ReminderEnqueueSignal.Absent -> cause().failureClass()
}

/**
 * Builds the request this generation runs under. The id is derived rather than allocated, because
 * the Worker validates the id the platform runs it under against that same derivation.
 */
internal fun reminderWorkRequest(
    spec: ReminderSpec,
    generationNumber: Long,
    now: Instant,
): OneTimeWorkRequest {
    val input = Data.Builder()
        .putString(ReminderWorkKeys.OCCURRENCE_ID, spec.occurrenceId)
        .putString(ReminderWorkKeys.PROPERTY_ID, spec.route.propertyId)
        .putString(ReminderWorkKeys.INSPECTION_TYPE, spec.route.inspectionType.name)
        .putString(ReminderWorkKeys.DUE_AT, spec.dueAt.toString())
        .putString(ReminderWorkKeys.GENERATION_NUMBER, generationNumber.toString())
        .build()
    return OneTimeWorkRequest.Builder(ReminderWorker::class.java)
        .setId(reminderGenerationId(spec.occurrenceId, generationNumber))
        .setInitialDelay(reminderDelayMillis(now, spec.dueAt), TimeUnit.MILLISECONDS)
        .setInputData(input)
        .build()
}

/**
 * The delay from [now] to [dueAt] in whole milliseconds, rounded up so a reminder is never handed
 * to the platform early, and clamped rather than allowed to overflow: WorkManager itself refuses a
 * delay that would overflow the arithmetic it schedules with, and the cap is far beyond any due
 * date an inspection could carry.
 */
internal fun reminderDelayMillis(now: Instant, dueAt: Instant): Long {
    val delay = Duration.between(now, dueAt)
    if (delay <= Duration.ZERO) {
        return 0L
    }
    val rounded = try {
        Math.addExact(delay.toMillis(), if (delay.nano % NANOS_PER_MILLI == 0) 0L else 1L)
    } catch (_: ArithmeticException) {
        MAX_INITIAL_DELAY_MILLIS
    }
    return minOf(rounded, MAX_INITIAL_DELAY_MILLIS)
}

internal const val MAX_INITIAL_DELAY_MILLIS: Long = Long.MAX_VALUE / 2
private const val NANOS_PER_MILLI = 1_000_000
private const val FIRST_GENERATION = 0L

/** How many views of a blocked occurrence one registration may lose before it stops trying. */
private const val MAX_RECOVERY_READS = 3
private const val OPERATION_TIMEOUT_SECONDS = 30L
private const val WATCHDOG_SECONDS = 30L
private val WATCHDOG_NANOS: Long = TimeUnit.SECONDS.toNanos(WATCHDOG_SECONDS)

/** The phases a registration can still act on. Everything else has settled this generation. */
private val ACTIVE_PHASES = setOf(ADMISSION_PENDING, ENQUEUED, RETRYABLE)

/**
 * Submits through WorkManager and answers when that operation's own result completes. The listener
 * runs on whichever thread completed the future, so the flight it answers linearises its decisions
 * through the receipt store rather than through this call.
 */
internal class WorkManagerReminderEnqueuePort(private val context: Context) : ReminderEnqueuePort {
    override fun submitUnique(
        name: String,
        policy: ExistingWorkPolicy,
        request: OneTimeWorkRequest,
        onSettled: (ReminderEnqueueSignal) -> Unit,
    ) {
        val result = WorkManager.getInstance(context).enqueueUniqueWork(name, policy, request).result
        result.addListener(
            {
                val signal = try {
                    result.get()?.let { ReminderEnqueueSignal.Confirmed } ?: ReminderEnqueueSignal.Absent
                } catch (failure: ExecutionException) {
                    ReminderEnqueueSignal.Reported(failure.cause ?: failure)
                } catch (failure: InterruptedException) {
                    Thread.currentThread().interrupt()
                    ReminderEnqueueSignal.Raised(failure)
                } catch (failure: Throwable) {
                    // A cancelled operation arrives this way rather than as a reported failure, and
                    // it is still an operation that existed, so it is not a refused submission.
                    ReminderEnqueueSignal.Raised(failure)
                }
                onSettled(signal)
            },
            Executor { runnable -> runnable.run() },
        )
    }
}

internal class WorkManagerReminderQueryPort(private val context: Context) : ReminderWorkQueryPort {
    override fun retainedWork(uniqueWorkName: String): List<RetainedWork> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(uniqueWorkName)
            .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .map { RetainedWork(it.id, it.state) }
}

/**
 * The platform timer every watchdog runs on. A wake up only re-reads a receipt, so one daemon
 * thread is enough and it never holds the process open by itself.
 */
internal object AndroidReminderWatchdogPort : ReminderWatchdogPort {
    private val timer: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "reminder-watchdog").apply { isDaemon = true }
        }

    override fun nowNanos(): Long = System.nanoTime()

    override fun schedule(delayNanos: Long, wake: () -> Unit) {
        timer.schedule(Runnable { wake() }, delayNanos, TimeUnit.NANOSECONDS)
    }
}

/**
 * Renders one registration for the log, in the field vocabulary the delivery side already uses.
 *
 * `error_code` and `cause_code` answer two different questions and stay two fields. The first is
 * this registration's own closed vocabulary answer, the second the failure class registration and
 * delivery share. A settlement that carried a real Throwable publishes that Throwable's class,
 * because what was thrown is a fact about the failure while the answer it became is a fact about
 * this registration, and an answer that failed nothing publishes no class at all.
 *
 * The identity is judged as one value. The occurrence has to be the irreversible digest shape and
 * the generation has to be a real one, and failing either publishes neither half nor any id
 * derived from them: half an identity correlates with nothing, so publishing it would assert a
 * correlation that does not exist. The work request id is likewise the one those two derive rather
 * than any spelling a caller might hold. Those two conditions are exactly the two that derivation
 * requires, so an identity this refuses to publish is also one it never asks to derive from.
 *
 * `retained_work_request_id` names the work this settlement collided with, so it is published only
 * beside a whole identity to correlate it against, and only when it is not this registration's own
 * id: naming that would assert a conflict with a request that does not exist.
 *
 * No property, date, path or exception text can appear here, because the record carries none.
 */
internal fun reminderRegistrationMessage(record: ReminderRegistrationRecord): String {
    val identity = record.identity?.takeIf {
        it.occurrenceId.matches(OCCURRENCE_ID_PATTERN) && it.generationNumber >= 0
    }
    val workRequestId = identity?.let { reminderGenerationId(it.occurrenceId, it.generationNumber) }
    val retained = record.retainedWorkRequestId?.takeIf { workRequestId != null && it != workRequestId }
    return buildString {
        append("{\"event\":\"schedule-reminder\",\"stage\":\"")
        append(record.cause.stage().wireValue)
        append("\",\"occurrence_id\":")
        append(identity?.occurrenceId.quoted())
        append(",\"type\":\"")
        append(record.type.name)
        append("\",\"generation_number\":")
        append(identity?.generationNumber ?: "null")
        append(",\"work_request_id\":")
        append(workRequestId?.toString().quoted())
        append(",\"retained_work_request_id\":")
        append(retained?.toString().quoted())
        append(",\"retryable\":")
        append(record.cause.outcome == RETRYABLE_FAILURE)
        append(",\"error_code\":\"")
        append(record.cause.wireValue())
        append("\",\"cause_code\":")
        append((record.causeClass ?: record.cause.failureClass())?.wireValue.quoted())
        append(",\"callback_cause_code\":")
        append(record.callbackCause?.wireValue().quoted())
        append(",\"note\":")
        append(record.note?.wireValue().quoted())
        append("}")
    }
}

/**
 * Where a registration reached its answer, in the delivery side's own closed set of stages. Only
 * the two answers decided somewhere other than the receipt name another stage.
 */
private fun ReminderRegistrationCause.stage(): LogStage = when (this) {
    INVALID_ROUTE -> LogStage.INPUT
    PERMISSION_NOT_GRANTED -> LogStage.PERMISSION
    else -> LogStage.RECEIPT
}

/**
 * The failure class an answer is of by itself, which is what `cause_code` falls back to when no
 * Throwable reached the settlement to classify. An answer that failed nothing has no class, which
 * is what keeps the field absent rather than a spelling of success. The answers a Throwable does
 * reach name unknown here, because those answers classify nothing on their own: in production each
 * of them travels with the class of what was thrown, and that class is preferred over this one.
 */
private fun ReminderRegistrationCause.failureClass(): FailureCauseCode? = when (this) {
    CALLBACK_CONFIRMED_ADMISSION, WORKER_CONFIRMED_ADMISSION, RETAINED_WORK_ENQUEUED,
    ENQUEUE_CALLBACK_AFTER_WORKER_STARTED, ADMISSION_ALREADY_RECORDED, OCCURRENCE_CLOSED,
    GENERATION_SUPERSEDED -> null
    INVALID_ROUTE -> FailureCauseCode.INVALID_INPUT
    PERMISSION_NOT_GRANTED -> FailureCauseCode.SECURITY
    RECEIPT_WRITE_UNCERTAIN -> FailureCauseCode.IO
    ENQUEUE_CALLBACK_NULL, ENQUEUE_CALLBACK_ERROR, ENQUEUE_CALLBACK_THROWABLE,
    ENQUEUE_CALLBACK_TIMEOUT, ENQUEUE_SUBMIT_TRANSIENT, ENQUEUE_SUBMIT_FATAL,
    RETAINED_WORK_QUERY_FAILED -> FailureCauseCode.UNKNOWN
    RETAINED_WORK_BLOCKED, RETAINED_WORK_SUCCEEDED_WITHOUT_RECEIPT, RETAINED_WORK_FAILED,
    RETAINED_WORK_CANCELLED, RETAINED_WORK_ID_MISMATCH, RETAINED_WORK_DUPLICATE,
    RECEIPT_QUARANTINED, RECEIPT_REJECTED, RECEIPT_CONTENDED,
    RECEIPT_REREAD_EXHAUSTED -> FailureCauseCode.ILLEGAL_STATE
}

/**
 * The logged spelling of one value of a closed vocabulary, derived rather than written out beside
 * it, so a value added later cannot reach the log unspelled. The spellings are still pinned, by a
 * test comparing every one of them against a written out table, so a rename cannot quietly change
 * what this emits either.
 */
private fun Enum<*>.wireValue(): String = name.lowercase().replace('_', '-')

private fun String?.quoted(): String = this?.let { "\"$it\"" } ?: "null"

/** Publishes a settled registration in the field vocabulary the delivery side already uses. */
internal object AndroidReminderSchedulerDiagnosticPort : ReminderSchedulerDiagnosticPort {
    override fun record(record: ReminderRegistrationRecord) {
        Log.w("ReminderScheduler", reminderRegistrationMessage(record))
    }
}

private object SchedulerHolder {
    @Volatile
    var instance: ReminderScheduler? = null
}

/**
 * The scheduler over the process wide receipt store and the real WorkManager. One instance serves
 * the process: coalescing is only real while every registration reaches the same flight table.
 */
internal fun reminderScheduler(context: Context): ReminderScheduler =
    SchedulerHolder.instance ?: synchronized(SchedulerHolder) {
        SchedulerHolder.instance ?: context.applicationContext.let { app ->
            ReminderScheduler(
                reminderReceiptStore(app),
                WorkManagerReminderEnqueuePort(app),
                WorkManagerReminderQueryPort(app),
                AndroidReminderPermissionPort(app),
                AndroidReminderSchedulerDiagnosticPort,
                AndroidReminderWatchdogPort,
                Clock.systemUTC(),
            )
        }.also { SchedulerHolder.instance = it }
    }

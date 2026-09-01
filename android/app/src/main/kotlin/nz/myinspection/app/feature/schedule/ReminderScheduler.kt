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
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import nz.myinspection.app.feature.schedule.ReminderPhase.ADMISSION_PENDING
import nz.myinspection.app.feature.schedule.ReminderPhase.ENQUEUED
import nz.myinspection.app.feature.schedule.ReminderPhase.QUARANTINED
import nz.myinspection.app.feature.schedule.ReminderPhase.RETRYABLE
import nz.myinspection.app.feature.schedule.ReminderPhase.TERMINAL
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.CALLBACK_CONFIRMED_ADMISSION
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.ENQUEUE_CALLBACK_ERROR
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.ENQUEUE_CALLBACK_NULL
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.ENQUEUE_CALLBACK_THROWABLE
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.ENQUEUE_CALLBACK_TIMEOUT
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.OCCURRENCE_CLOSED
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.RECEIPT_CONTENDED
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.RECEIPT_QUARANTINED
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.RECEIPT_REJECTED
import nz.myinspection.app.feature.schedule.ReminderRegistrationCause.RECEIPT_WRITE_UNCERTAIN
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
    RETAINED_WORK_ENQUEUED(ReminderRegistrationOutcome.ADMITTED),
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
    OCCURRENCE_CLOSED(ReminderRegistrationOutcome.SKIPPED),
    GENERATION_SUPERSEDED(ReminderRegistrationOutcome.SKIPPED),
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
 */
data class ReminderRegistrationRecord(
    val identity: ReminderRegistrationIdentity?,
    val type: InspectionScheduleType,
    val cause: ReminderRegistrationCause,
)

interface ReminderSchedulerDiagnosticPort {
    fun record(record: ReminderRegistrationRecord)
}

/**
 * What one submitted operation ended up reporting. [Absent], [Reported] and [Raised] are three
 * different facts and stay three: nothing was scheduled, the platform reported this failure, and
 * reading the outcome itself threw. [TimedOut] is none of those, because the operation may still
 * be in flight, so it is the one outcome that leaves the reservation untouched.
 */
sealed interface ReminderEnqueueSignal {
    data object Confirmed : ReminderEnqueueSignal
    data object Absent : ReminderEnqueueSignal
    data class Reported(val error: Throwable) : ReminderEnqueueSignal
    data class Raised(val error: Throwable) : ReminderEnqueueSignal
    data object TimedOut : ReminderEnqueueSignal
}

/**
 * Submits unique work and reports that submission's own outcome. Throwing means the submission
 * never produced an operation at all, which is the only failure this card treats as fatal.
 */
interface ReminderEnqueuePort {
    fun enqueueUnique(name: String, policy: ExistingWorkPolicy, request: OneTimeWorkRequest): ReminderEnqueueSignal
}

/** One work request WorkManager still retains under a unique name. */
data class RetainedWork(val id: UUID, val state: WorkInfo.State)

interface ReminderWorkQueryPort {
    fun retainedWork(uniqueWorkName: String): List<RetainedWork>
}

/**
 * Registers one reminder occurrence with WorkManager.
 *
 * Registration is a blocking background call: it reserves durable evidence, asks WorkManager what
 * it still retains under this occurrence's unique name, and submits only when that answer leaves
 * no retained work of its own. The query is what decides every submission, so a KEEP policy can
 * never be read as admission of work this generation did not put there.
 *
 * The scheduler shares one [ReminderReceiptStore] with the delivery runner and holds no lock of
 * its own: every cross actor decision is an atomic result of admit, compare and set or permission
 * recovery. A lost compare and set is therefore re-read rather than overwritten, and a receipt
 * that has left ADMISSION_PENDING under the same generation proves the matching worker was
 * admitted, which no later failure may downgrade.
 */
class ReminderScheduler(
    private val store: ReminderReceiptStore,
    private val enqueue: ReminderEnqueuePort,
    private val query: ReminderWorkQueryPort,
    private val diagnostics: ReminderSchedulerDiagnosticPort,
    private val clock: Clock,
) {
    fun register(reminder: PendingReminder): ReminderRegistrationCause {
        val spec = try {
            reminder.toSpec()
        } catch (_: IllegalArgumentException) {
            null
        }
        val settled = spec?.let { coordinate(it) } ?: Settlement(ReminderRegistrationCause.INVALID_ROUTE)
        val identity = spec?.let { known ->
            settled.generationNumber?.let { ReminderRegistrationIdentity(known.occurrenceId, it) }
        }
        diagnostics.record(ReminderRegistrationRecord(identity, reminder.route.inspectionType, settled.cause))
        return settled.cause
    }

    private fun coordinate(spec: ReminderSpec): Settlement =
        when (val lookup = store.lookup(spec.occurrenceId)) {
            ReminderReceiptLookup.Missing -> reserve(spec)
            is ReminderReceiptLookup.Quarantined -> Settlement(RECEIPT_QUARANTINED)
            // The stored receipt carries this same spec by construction: the occurrence digest
            // binds property, type and due instant, and the store refuses a receipt whose spec
            // does not match its own occurrence.
            is ReminderReceiptLookup.Present -> when {
                lookup.writeUncertain -> lookup.receipt.settle(RECEIPT_WRITE_UNCERTAIN)
                lookup.receipt.phase in ACTIVE_PHASES -> place(lookup.receipt)
                // A blocked occurrence needs a fresh grant and a new generation, which the flight
                // card owns along with the rest of the generation machinery.
                else -> lookup.receipt.settle(OCCURRENCE_CLOSED)
            }
        }

    /** Reserves generation zero. Only a durable reservation may be followed by a submission. */
    private fun reserve(spec: ReminderSpec): Settlement {
        val reserved = ReminderReceipt(
            occurrenceId = spec.occurrenceId,
            generationNumber = FIRST_GENERATION,
            workRequestId = reminderGenerationId(spec.occurrenceId, FIRST_GENERATION),
            spec = spec,
            phase = ADMISSION_PENDING,
            causeCode = null,
        )
        return when (store.admit(reserved)) {
            ReminderReceiptAdmissionResult.Admitted -> place(reserved)
            ReminderReceiptAdmissionResult.WriteUncertain -> reserved.settle(RECEIPT_WRITE_UNCERTAIN)
            // Another registration reserved this occurrence between the lookup and the admission.
            // The evidence is sound, so this is contention rather than corruption.
            ReminderReceiptAdmissionResult.Rejected -> reserved.settle(RECEIPT_CONTENDED)
        }
    }

    /** Reconciles retained work first: an absent current id is the only path to a submission. */
    private fun place(receipt: ReminderReceipt): Settlement {
        val retained = try {
            query.retainedWork(receipt.spec.uniqueWorkName)
        } catch (_: InterruptedException) {
            // The domain answer is still retryable, but the flag that tells this thread it was
            // interrupted belongs to the caller and is handed straight back.
            Thread.currentThread().interrupt()
            return receipt.settle(ReminderRegistrationCause.RETAINED_WORK_QUERY_FAILED)
        } catch (_: Throwable) {
            return receipt.settle(ReminderRegistrationCause.RETAINED_WORK_QUERY_FAILED)
        }
        val current = retained.filter { it.id == receipt.workRequestId }
        val foreign = retained.any { it.id != receipt.workRequestId && !it.state.isFinished }
        if (current.size > 1) {
            return quarantine(receipt, ReminderRegistrationCause.RETAINED_WORK_DUPLICATE)
        }
        if (foreign) {
            return quarantine(receipt, ReminderRegistrationCause.RETAINED_WORK_ID_MISMATCH)
        }
        val single = current.singleOrNull() ?: return submit(receipt)
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

    private fun submit(receipt: ReminderReceipt): Settlement {
        val request = reminderWorkRequest(receipt.spec, receipt.generationNumber, clock.instant())
        val signal = try {
            enqueue.enqueueUnique(receipt.spec.uniqueWorkName, ExistingWorkPolicy.KEEP, request)
        } catch (failure: Throwable) {
            return refuseSubmission(receipt, failure)
        }
        return when (signal) {
            ReminderEnqueueSignal.Confirmed -> confirm(receipt, CALLBACK_CONFIRMED_ADMISSION)
            ReminderEnqueueSignal.Absent -> holdForRetry(receipt, ENQUEUE_CALLBACK_NULL)
            is ReminderEnqueueSignal.Reported -> holdForRetry(receipt, ENQUEUE_CALLBACK_ERROR)
            is ReminderEnqueueSignal.Raised -> holdForRetry(receipt, ENQUEUE_CALLBACK_THROWABLE)
            // The operation may still be running, so the reservation stays exactly as it is and a
            // later registration reads the retained work rather than submitting a second time.
            ReminderEnqueueSignal.TimedOut -> receipt.settle(ENQUEUE_CALLBACK_TIMEOUT)
        }
    }

    /** Only a permanent submission failure closes the occurrence: nothing was scheduled either way. */
    private fun refuseSubmission(receipt: ReminderReceipt, failure: Throwable): Settlement =
        if (classifyReminderFailure(failure).kind == FailureKind.PERMANENT) {
            terminate(receipt, ReminderRegistrationCause.ENQUEUE_SUBMIT_FATAL)
        } else {
            receipt.settle(ReminderRegistrationCause.ENQUEUE_SUBMIT_TRANSIENT)
        }

    private fun confirm(receipt: ReminderReceipt, applied: ReminderRegistrationCause): Settlement =
        advance(receipt, ENQUEUED, null, applied)

    private fun holdForRetry(receipt: ReminderReceipt, applied: ReminderRegistrationCause): Settlement =
        advance(receipt, RETRYABLE, null, applied)

    private fun quarantine(receipt: ReminderReceipt, applied: ReminderRegistrationCause): Settlement =
        advance(receipt, QUARANTINED, null, applied)

    private fun terminate(receipt: ReminderReceipt, applied: ReminderRegistrationCause): Settlement =
        advance(receipt, TERMINAL, ReminderCause.PERMANENT_DELIVERY_FAILURE, applied)

    /**
     * Applies one phase change, re-reading rather than overwriting when the store says the caller's
     * view is stale. A lost compare and set is reported rather than retried in a loop: the re-read
     * already names why it was lost, and a registration that reports contention is one the caller
     * repeats from the top rather than one that spins here.
     */
    private fun advance(
        receipt: ReminderReceipt,
        next: ReminderPhase,
        cause: ReminderCause?,
        applied: ReminderRegistrationCause,
    ): Settlement {
        val result = store.compareAndSet(
            receipt.occurrenceId, receipt.generationNumber, receipt.workRequestId,
            receipt.phase, next, cause,
        )
        return when (result) {
            is ReminderReceiptTransitionResult.Applied -> receipt.settle(applied)
            // Rejected means the store forbids this transition, which this scheduler never asks
            // for, so it is a fail closed default rather than a reachable branch.
            ReminderReceiptTransitionResult.Rejected -> receipt.settle(RECEIPT_REJECTED)
            ReminderReceiptTransitionResult.WriteUncertain -> receipt.settle(RECEIPT_WRITE_UNCERTAIN)
            is ReminderReceiptTransitionResult.Stale -> reread(result.lookup, receipt)
        }
    }

    /**
     * Reports what replaced this registration's view of the receipt. Nothing here claims an
     * admission: within one generation both the matching worker and another registration can leave
     * ADMISSION_PENDING, and the phase alone cannot tell them apart, so a same generation change
     * this registration did not make is contention rather than proof of anything.
     */
    private fun reread(lookup: ReminderReceiptLookup, current: ReminderReceipt): Settlement {
        // The store reports Stale only with the receipt it read under its own lock, and answers a
        // missing or unreadable occurrence with Rejected instead, so this is a fail closed default.
        val fresh = (lookup as? ReminderReceiptLookup.Present)?.receipt
            ?: return Settlement(RECEIPT_QUARANTINED)
        return when {
            fresh.generationNumber != current.generationNumber ->
                fresh.settle(ReminderRegistrationCause.GENERATION_SUPERSEDED)
            fresh.phase !in ACTIVE_PHASES -> fresh.settle(OCCURRENCE_CLOSED)
            else -> fresh.settle(RECEIPT_CONTENDED)
        }
    }
}

private data class Settlement(
    val cause: ReminderRegistrationCause,
    val generationNumber: Long? = null,
)

private fun ReminderReceipt.settle(cause: ReminderRegistrationCause): Settlement =
    Settlement(cause, generationNumber)

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
private const val OPERATION_TIMEOUT_SECONDS = 30L

/** The phases a registration can still act on. Everything else has settled this generation. */
private val ACTIVE_PHASES = setOf(ADMISSION_PENDING, ENQUEUED, RETRYABLE)

/** Submits through WorkManager and reads that operation's own result under a bounded wait. */
internal class WorkManagerReminderEnqueuePort(private val context: Context) : ReminderEnqueuePort {
    override fun enqueueUnique(
        name: String,
        policy: ExistingWorkPolicy,
        request: OneTimeWorkRequest,
    ): ReminderEnqueueSignal {
        val result = WorkManager.getInstance(context).enqueueUniqueWork(name, policy, request).result
        return try {
            result.get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                ?.let { ReminderEnqueueSignal.Confirmed }
                ?: ReminderEnqueueSignal.Absent
        } catch (_: TimeoutException) {
            ReminderEnqueueSignal.TimedOut
        } catch (failure: ExecutionException) {
            ReminderEnqueueSignal.Reported(failure.cause ?: failure)
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            ReminderEnqueueSignal.Raised(failure)
        } catch (failure: Throwable) {
            // A cancelled operation arrives this way rather than as a reported failure, and it is
            // still an operation that existed, so it must not be read as a refused submission.
            ReminderEnqueueSignal.Raised(failure)
        }
    }
}

internal class WorkManagerReminderQueryPort(private val context: Context) : ReminderWorkQueryPort {
    override fun retainedWork(uniqueWorkName: String): List<RetainedWork> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(uniqueWorkName)
            .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .map { RetainedWork(it.id, it.state) }
}

internal object AndroidReminderSchedulerDiagnosticPort : ReminderSchedulerDiagnosticPort {
    override fun record(record: ReminderRegistrationRecord) {
        Log.w("ReminderScheduler", record.toString())
    }
}

/** The scheduler over the process wide receipt store and the real WorkManager. */
internal fun reminderScheduler(context: Context): ReminderScheduler {
    val app = context.applicationContext
    return ReminderScheduler(
        reminderReceiptStore(app), WorkManagerReminderEnqueuePort(app), WorkManagerReminderQueryPort(app),
        AndroidReminderSchedulerDiagnosticPort, Clock.systemUTC(),
    )
}

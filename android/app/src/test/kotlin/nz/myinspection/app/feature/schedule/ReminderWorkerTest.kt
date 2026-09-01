package nz.myinspection.app.feature.schedule

import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import nz.myinspection.app.feature.schedule.ReminderPhase.ADMISSION_PENDING
import nz.myinspection.app.feature.schedule.ReminderPhase.DELIVERED
import nz.myinspection.app.feature.schedule.ReminderPhase.DELIVERY_UNCERTAIN
import nz.myinspection.app.feature.schedule.ReminderPhase.ENQUEUED
import nz.myinspection.app.feature.schedule.ReminderPhase.PERMISSION_BLOCKED
import nz.myinspection.app.feature.schedule.ReminderPhase.QUARANTINED
import nz.myinspection.app.feature.schedule.ReminderPhase.RETRYABLE
import nz.myinspection.app.feature.schedule.ReminderPhase.TERMINAL
import nz.myinspection.core.schedule.InspectionScheduleType

/**
 * Black box acceptance tests for the delivery runner, driven through the same ports the production
 * Worker injects, over the real receipt store on an in memory preference file. Every expectation is
 * a literal written here, and the occurrence digest and both generation work ids are the golden
 * vectors frozen by the contracts card, so an identity that drifts is caught rather than mirrored.
 */
class ReminderWorkerTest {
    @Test
    fun `a pending occurrence is admitted, delivered once and settled as delivered`() {
        val fixture = Fixture(ADMISSION_PENDING)

        val outcome = fixture.run()

        assertEquals(ReminderRunOutcome.SUCCESS, outcome)
        assertEquals(DELIVERED, fixture.phase())
        assertEquals(listOf(NotificationIdentity(OCCURRENCE_ID, 0) to PREPARED), fixture.notifier.posts)
        assertEquals(emptyList(), fixture.diagnostics.records)
        // Four commits are the admission plus three transitions. The store reaches
        // DELIVERY_UNCERTAIN only from ENQUEUED or RETRYABLE and DELIVERED only from
        // DELIVERY_UNCERTAIN, so a delivered receipt after exactly four commits must have gone
        // through the admission confirming write. A run that skipped it commits three.
        assertEquals(4, fixture.preferences.commits)
    }

    @Test
    fun `the prepared plan carries this occurrence's private immutable explicit alert once route`() {
        val fixture = Fixture(ENQUEUED)

        fixture.run()

        val plan = fixture.preparation.plans.single()
        assertEquals(true, plan.onlyAlertOnce)
        assertEquals(NotificationVisibility.PRIVATE, plan.visibility)
        assertEquals("myinspection://schedule/reminder/$OCCURRENCE_ID", plan.intent.data)
        assertEquals(OCCURRENCE_ID, plan.intent.notificationTag)
        assertEquals(PROPERTY, plan.intent.propertyId)
        assertEquals("ROUTINE", plan.intent.inspectionType)
        assertEquals(true, plan.intent.isExplicit)
        assertEquals(true, plan.intent.isImmutable)
    }

    @Test
    fun `a retryable receipt is a legal start for delivery`() {
        val fixture = Fixture(RETRYABLE)

        val outcome = fixture.run(attempt = 1)

        assertEquals(ReminderRunOutcome.SUCCESS, outcome)
        assertEquals(DELIVERED, fixture.phase())
        assertEquals(1, fixture.notifier.posts.size)
    }

    @Test
    fun `malformed work input is refused before the store is read`() {
        val cases = mapOf(
            "occurrence id missing" to input(occurrenceId = null),
            "occurrence id of another occurrence" to input(occurrenceId = OCCURRENCE_ID_B),
            "occurrence id that is not a digest" to input(occurrenceId = "not-an-id"),
            "property id missing" to input(propertyId = null),
            "property id blank" to input(propertyId = "   "),
            "property id of another property" to input(propertyId = "property-b"),
            "inspection type missing" to input(inspectionType = null),
            "inspection type outside the vocabulary" to input(inspectionType = "WEEKLY"),
            "inspection type of another type" to input(inspectionType = "ANNUAL"),
            "due instant missing" to input(dueAt = null),
            "due instant that does not parse" to input(dueAt = "2026-08-03"),
            "due instant of another moment" to input(dueAt = "2026-08-04T00:00:00.000000001Z"),
            "generation missing" to input(generationNumber = null),
            "generation that is not a number" to input(generationNumber = "zero"),
            "generation below zero" to input(generationNumber = "-1"),
            "work request id of another generation" to input(workRequestId = WORK_ID_1),
        )

        cases.forEach { (label, malformed) ->
            val fixture = Fixture(ENQUEUED)
            fixture.preferences.forget()

            val outcome = fixture.run(malformed)

            assertEquals(ReminderRunOutcome.FAILURE, outcome, label)
            assertEquals(0, fixture.preferences.reads, label)
            assertEquals(0, fixture.preferences.commits, label)
            assertEquals(emptyList(), fixture.preparation.plans, label)
            assertEquals(emptyList(), fixture.notifier.posts, label)
            assertEquals(
                LogRecord(
                    LogStage.INPUT, malformed.occurrenceId, null, null,
                    malformed.workRequestId.toString(), false,
                    LogError.INVALID_INPUT, FailureCauseCode.INVALID_INPUT,
                ),
                fixture.diagnostics.records.single(),
                label,
            )
        }
    }

    @Test
    fun `evidence that does not correspond to this run is refused as invalid`() {
        val absent = Fixture(null)
        val foreign = Fixture(ENQUEUED)
        foreign.preferences.tamper(STORE_KEY, "reminder-receipts/v2")
        val superseded = Fixture(PERMISSION_BLOCKED)
        assertIs<ReminderReceiptTransitionResult.Applied>(
            superseded.store.recoverPermissionBlocked(receipt(PERMISSION_BLOCKED)),
        )
        val cases = mapOf(
            "no receipt at all" to absent,
            "sentinel of a foreign store version" to foreign,
            "receipt of a later generation" to superseded,
        )

        cases.forEach { (label, fixture) ->
            val outcome = fixture.run()

            assertEquals(ReminderRunOutcome.FAILURE, outcome, label)
            assertEquals(emptyList(), fixture.notifier.posts, label)
            assertEquals(
                record(LogStage.RECEIPT, LogError.RECEIPT_INVALID, FailureCauseCode.ILLEGAL_STATE, false),
                fixture.diagnostics.records.single(),
                label,
            )
        }
    }

    @Test
    fun `a receipt that is already closed or uncertain stops without posting or reporting`() {
        listOf(DELIVERY_UNCERTAIN, DELIVERED, PERMISSION_BLOCKED, TERMINAL, QUARANTINED).forEach { phase ->
            val fixture = Fixture(phase)
            val committed = fixture.preferences.commits

            val outcome = fixture.run()

            val label = phase.name
            assertEquals(ReminderRunOutcome.FAILURE, outcome, label)
            assertEquals(phase, fixture.phase(), label)
            assertEquals(committed, fixture.preferences.commits, label)
            assertEquals(emptyList(), fixture.preparation.plans, label)
            assertEquals(emptyList(), fixture.notifier.posts, label)
            // Every LogError names a failure, and an occurrence another run already settled is not
            // one, so these stops are deliberately silent rather than reported as errors.
            assertEquals(emptyList(), fixture.diagnostics.records, label)
        }
    }

    @Test
    fun `concurrent runners over one occurrence post exactly once`() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            repeat(RACE_ROUNDS) { round ->
                val fixture = Fixture(ADMISSION_PENDING)
                val barrier = CyclicBarrier(2)
                val race = List(2) {
                    Callable {
                        barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        fixture.run()
                    }
                }

                val outcomes = pool.invokeAll(race).map { it.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }

                val label = "round $round"
                assertEquals(1, fixture.notifier.posts.size, label)
                assertEquals(1, outcomes.count { it == ReminderRunOutcome.SUCCESS }, label)
                assertEquals(1, outcomes.count { it == ReminderRunOutcome.FAILURE }, label)
                assertEquals(DELIVERED, fixture.phase(), label)
            }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `a denied notification permission retries attempts zero and one then blocks`() {
        val cases = mapOf(
            0 to (ReminderRunOutcome.RETRY to RETRYABLE),
            1 to (ReminderRunOutcome.RETRY to RETRYABLE),
            2 to (ReminderRunOutcome.FAILURE to PERMISSION_BLOCKED),
            9 to (ReminderRunOutcome.FAILURE to PERMISSION_BLOCKED),
        )

        cases.forEach { (attempt, expected) ->
            val fixture = Fixture(ENQUEUED, granted = false)

            val outcome = fixture.run(sdkInt = 33, attempt = attempt)

            val label = "attempt $attempt"
            assertEquals(expected.first, outcome, label)
            assertEquals(expected.second, fixture.phase(), label)
            assertEquals(emptyList(), fixture.preparation.plans, label)
            assertEquals(emptyList(), fixture.notifier.posts, label)
            assertEquals(
                record(
                    LogStage.PERMISSION, LogError.PERMISSION_DENIED, FailureCauseCode.SECURITY,
                    expected.first == ReminderRunOutcome.RETRY,
                ),
                fixture.diagnostics.records.single(),
                label,
            )
        }
    }

    @Test
    fun `a denied permission below api thirty three still delivers`() {
        val fixture = Fixture(ENQUEUED, granted = false)

        val outcome = fixture.run(sdkInt = 32, attempt = 2)

        assertEquals(ReminderRunOutcome.SUCCESS, outcome)
        assertEquals(DELIVERED, fixture.phase())
        assertEquals(1, fixture.notifier.posts.size)
    }

    @Test
    fun `a transient preparation failure retries attempts zero and one then terminates`() {
        val cases = mapOf(
            0 to (ReminderRunOutcome.RETRY to RETRYABLE),
            1 to (ReminderRunOutcome.RETRY to RETRYABLE),
            2 to (ReminderRunOutcome.FAILURE to TERMINAL),
        )

        cases.forEach { (attempt, expected) ->
            val fixture = Fixture(ENQUEUED, preparationFailure = IOException("channel unavailable"))

            val outcome = fixture.run(attempt = attempt)

            val label = "attempt $attempt"
            assertEquals(expected.first, outcome, label)
            assertEquals(expected.second, fixture.phase(), label)
            assertEquals(emptyList(), fixture.notifier.posts, label)
            assertEquals(
                record(
                    LogStage.PREPARATION, LogError.PREPARATION_FAILED, FailureCauseCode.IO,
                    expected.first == ReminderRunOutcome.RETRY,
                ),
                fixture.diagnostics.records.single(),
                label,
            )
        }
    }

    @Test
    fun `a permanent preparation failure terminates on the first attempt`() {
        val cases = mapOf(
            SecurityException("revoked") to FailureCauseCode.SECURITY,
            IllegalStateException("no channel") to FailureCauseCode.ILLEGAL_STATE,
            IllegalArgumentException("mutable route") to FailureCauseCode.UNKNOWN,
        )

        cases.forEach { (failure, cause) ->
            val fixture = Fixture(ENQUEUED, preparationFailure = failure)

            val outcome = fixture.run()

            val label = failure.javaClass.simpleName
            assertEquals(ReminderRunOutcome.FAILURE, outcome, label)
            assertEquals(TERMINAL, fixture.phase(), label)
            assertEquals(emptyList(), fixture.notifier.posts, label)
            assertEquals(
                record(LogStage.PREPARATION, LogError.PREPARATION_FAILED, cause, false),
                fixture.diagnostics.records.single(),
                label,
            )
        }
    }

    @Test
    fun `a failed post leaves the delivery uncertain and is never reposted`() {
        val fixture = Fixture(ENQUEUED, notifyFailure = IOException("notification service down"))

        val outcome = fixture.run()

        // The failure classifies as transient, yet a post that may already have reached the user is
        // never retried, so this run closes rather than asking for another attempt.
        assertEquals(ReminderRunOutcome.FAILURE, outcome)
        assertEquals(DELIVERY_UNCERTAIN, fixture.phase())
        assertEquals(1, fixture.notifier.posts.size)
        assertEquals(
            record(LogStage.NOTIFY, LogError.NOTIFY_FAILED, FailureCauseCode.IO, false),
            fixture.diagnostics.records.single(),
        )

        assertEquals(ReminderRunOutcome.FAILURE, fixture.run(attempt = 1))
        assertEquals(1, fixture.notifier.posts.size)
    }

    @Test
    fun `an unconfirmed uncertain write never posts`() {
        val fixture = Fixture(ENQUEUED)
        fixture.preferences.commitsBeforeFailure = fixture.preferences.commits

        val outcome = fixture.run()

        assertEquals(ReminderRunOutcome.FAILURE, outcome)
        assertEquals(emptyList(), fixture.notifier.posts)
        assertEquals(
            record(LogStage.RECEIPT, LogError.RECEIPT_WRITE_FAILED, FailureCauseCode.IO, false),
            fixture.diagnostics.records.single(),
        )
    }

    @Test
    fun `an unconfirmed final write reports the uncertainty and never reposts`() {
        val fixture = Fixture(ENQUEUED)
        fixture.preferences.commitsBeforeFailure = fixture.preferences.commits + 1

        val outcome = fixture.run()

        assertEquals(ReminderRunOutcome.FAILURE, outcome)
        assertEquals(1, fixture.notifier.posts.size)
        assertEquals(
            record(LogStage.RECEIPT, LogError.RECEIPT_WRITE_FAILED, FailureCauseCode.IO, false),
            fixture.diagnostics.records.single(),
        )

        assertEquals(ReminderRunOutcome.FAILURE, fixture.run(attempt = 1))
        assertEquals(1, fixture.notifier.posts.size)
        // Two records, not one: the second run must report the uncertainty it inherited rather
        // than stop silently, which a reader of only the last record could not tell apart.
        assertEquals(2, fixture.diagnostics.records.size)
        assertEquals(
            record(LogStage.RECEIPT, LogError.RECEIPT_WRITE_FAILED, FailureCauseCode.IO, false),
            fixture.diagnostics.records.last(),
        )
    }

    @Test
    fun `the generation recovered after a block delivers only under its own work request`() {
        val fixture = Fixture(PERMISSION_BLOCKED)
        assertIs<ReminderReceiptTransitionResult.Applied>(
            fixture.store.recoverPermissionBlocked(receipt(PERMISSION_BLOCKED)),
        )

        val stale = fixture.run()
        val recovered = fixture.run(input(generationNumber = "1", workRequestId = WORK_ID_1))

        assertEquals(ReminderRunOutcome.FAILURE, stale)
        assertEquals(ReminderRunOutcome.SUCCESS, recovered)
        assertEquals(DELIVERED, fixture.phase())
        assertEquals(1, fixture.notifier.posts.size)
    }
}

private const val PROPERTY = "property-a"
private const val PREPARED = "prepared-notification"
private const val STORE_KEY = "store"
private const val RACE_ROUNDS = 40
private const val TIMEOUT_SECONDS = 30L
private const val DUE_AT_TEXT = "2026-08-03T00:00:00.000000001Z"
private const val OCCURRENCE_ID =
    "c118fefec6ee20d89eafa5533048237237d39116af40aa85123fb1f70c404108"
private const val OCCURRENCE_ID_B =
    "9d5b6f1b1acb3d9da1f0c7da08d20f4563b3fddde91214182ab76626f03fc13d"
private val WORK_ID_0: UUID = UUID.fromString("40fe7461-9be1-3ce7-8bdf-28b48b76359e")
private val WORK_ID_1: UUID = UUID.fromString("590ca815-2783-322a-acde-39ab31dafd39")
private val DUE_AT: Instant = Instant.parse(DUE_AT_TEXT)

/** The transitions each phase is reached by, written out here rather than derived. */
private val PATH_TO: Map<ReminderPhase, List<ReminderPhase>> = mapOf(
    ADMISSION_PENDING to emptyList(),
    ENQUEUED to listOf(ENQUEUED),
    RETRYABLE to listOf(RETRYABLE),
    TERMINAL to listOf(TERMINAL),
    QUARANTINED to listOf(QUARANTINED),
    DELIVERY_UNCERTAIN to listOf(ENQUEUED, DELIVERY_UNCERTAIN),
    PERMISSION_BLOCKED to listOf(ENQUEUED, PERMISSION_BLOCKED),
    DELIVERED to listOf(ENQUEUED, DELIVERY_UNCERTAIN, DELIVERED),
)

/**
 * One occurrence, its store and the four ports, wired exactly as the Worker wires them. A null
 * [phase] leaves the preference file blank, which is the only legitimate empty store.
 */
private class Fixture(
    phase: ReminderPhase?,
    granted: Boolean = true,
    preparationFailure: Throwable? = null,
    notifyFailure: Throwable? = null,
) {
    val preferences = FakePreferences()
    val store = storeAt(phase, preferences)
    val preparation = RecordingPreparation(preparationFailure)
    val notifier = RecordingNotifier(notifyFailure)
    val diagnostics = RecordingDiagnostics()
    private val permission = FixedPermission(granted)

    fun run(
        input: ReminderWorkInput = input(),
        sdkInt: Int = 35,
        attempt: Int = 0,
    ): ReminderRunOutcome =
        ReminderDeliveryRunner(store, permission, preparation, notifier, diagnostics)
            .run(input, sdkInt, attempt)

    fun phase(): ReminderPhase? =
        (store.lookup(OCCURRENCE_ID) as? ReminderReceiptLookup.Present)?.receipt?.phase
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

    /** Drops the access counters so a test measures only what the run under test did. */
    fun forget() = synchronized(entries) {
        commits = 0
        reads = 0
    }
}

private class FixedPermission(private val granted: Boolean) : ReminderPermissionPort {
    override fun isPostNotificationsGranted(): Boolean = granted
}

private class RecordingPreparation(private val failure: Throwable?) :
    ReminderPreparationPort<String> {
    val plans = mutableListOf<DeliveryPlan.Notify>()

    override fun prepare(plan: DeliveryPlan.Notify): String {
        synchronized(plans) { plans += plan }
        failure?.let { throw it }
        return PREPARED
    }
}

private class RecordingNotifier(private val failure: Throwable?) : ReminderNotifierPort<String> {
    val posts = mutableListOf<Pair<NotificationIdentity, String>>()

    override fun post(identity: NotificationIdentity, prepared: String) {
        synchronized(posts) { posts += identity to prepared }
        failure?.let { throw it }
    }
}

private class RecordingDiagnostics : ReminderDiagnosticPort {
    val records = mutableListOf<LogRecord>()

    override fun record(record: LogRecord) {
        synchronized(records) { records += record }
    }
}

private fun input(
    occurrenceId: String? = OCCURRENCE_ID,
    propertyId: String? = PROPERTY,
    inspectionType: String? = "ROUTINE",
    dueAt: String? = DUE_AT_TEXT,
    generationNumber: String? = "0",
    workRequestId: UUID = WORK_ID_0,
): ReminderWorkInput =
    ReminderWorkInput(occurrenceId, propertyId, inspectionType, dueAt, generationNumber, workRequestId)

private fun receipt(phase: ReminderPhase, generationNumber: Long = 0): ReminderReceipt = ReminderReceipt(
    occurrenceId = OCCURRENCE_ID,
    generationNumber = generationNumber,
    workRequestId = reminderGenerationId(OCCURRENCE_ID, generationNumber),
    spec = WorkSpecFactory().create(ScheduleRoute(PROPERTY, InspectionScheduleType.ROUTINE), DUE_AT),
    phase = phase,
    causeCode = causeFor(phase),
)

private fun causeFor(phase: ReminderPhase): ReminderCause? =
    ReminderCause.PERMANENT_DELIVERY_FAILURE.takeIf { phase == TERMINAL }

/** Walks a freshly admitted occurrence to [phase] through the store's own public API. */
private fun storeAt(phase: ReminderPhase?, preferences: FakePreferences): ReminderReceiptStore {
    val store = ReminderReceiptStore(preferences)
    if (phase == null) {
        return store
    }
    assertEquals(ReminderReceiptAdmissionResult.Admitted, store.admit(receipt(ADMISSION_PENDING)))
    var current = ADMISSION_PENDING
    PATH_TO.getValue(phase).forEach { next ->
        assertIs<ReminderReceiptTransitionResult.Applied>(
            store.compareAndSet(OCCURRENCE_ID, 0L, WORK_ID_0, current, next, causeFor(next)),
        )
        current = next
    }
    return store
}

/** The generation zero record every failure of this occurrence must correlate under. */
private fun record(
    stage: LogStage,
    error: LogError,
    cause: FailureCauseCode,
    retryable: Boolean,
): LogRecord = LogRecord(
    stage, OCCURRENCE_ID, InspectionScheduleType.ROUTINE, 0L, WORK_ID_0.toString(),
    retryable, error, cause,
)

/*
 * R4 semantic mutation receipts. Each row was applied alone to ReminderWorker.kt at SHA-256
 * 3f2be2b3ea684e1cabc9b597f3a7a88bfe46be65af82432e5a4d7277894e8ef7, run through
 * `:app:testDebugUnitTest --tests nz.myinspection.app.feature.schedule.ReminderWorkerTest`, then
 * reverted and re-hashed to that same value. A kill required exit 1 with the expected test among
 * the named failing testcases, and every run reported 15 executed cases, which is what rules out
 * counting a compile break as a kill.
 *
 * A1 M01 drop `occurrenceId == spec.occurrenceId`        exit 1  input: "occurrence id missing"
 * A1 M02 force the work id comparison to true            exit 1  input: "work request id of another generation"
 * A1 M03 drop `takeIf { it >= 0 }` on the generation     exit 1  input: "generation below zero" throws
 * A2 M04 skip the admission confirming write             exit 1  pending occurrence: FAILURE, not SUCCESS
 * A2 M05 let a closed phase start a delivery             exit 1  DELIVERY_UNCERTAIN: a plan was prepared
 * A2 M06 let a lost uncertain claim post anyway          exit 1  concurrent runners: 2 posts in round 0
 * A2 M07 return RETRY after a failed post                exit 1  failed post: RETRY, not FAILURE
 * A2 M08 drop the write uncertainty branch of the lookup exit 1  final write: 1 record, not 2
 * A3 M09 retry attempt zero only                         exit 1  permission attempt 1: FAILURE, not RETRY
 * A3 M10 close exhausted permission as TERMINAL          exit 1  permission attempt 2: not PERMISSION_BLOCKED
 * A3 M11 treat every preparation failure as transient    exit 1  permanent preparation: RETRY, not FAILURE
 * A3 M12 write TERMINAL without its cause                exit 1  exhausted preparation: phase stayed ENQUEUED
 * A4 M13 report a write failure as RECEIPT_INVALID       exit 1  unconfirmed uncertain write: wrong error code
 * A4 M14 pin the diagnostic retryable flag to false      exit 1  permission attempt 0: wrong record
 * A4 M15 drop the occurrence id from refused input       exit 1  malformed input: wrong record
 * A4 M16 report every post failure as UNKNOWN            exit 1  failed post: wrong cause code
 *
 * Two declared survivors, predicted before the batch rather than found by it: both comparisons in
 * `corresponds` below the generation check are entailed by the store's own receipt invariant, so
 * no input can make either false. They are kept for the reason given there, and recorded here.
 *
 * A1 S01 drop `receipt.workRequestId == valid.workRequestId`   exit 0  SURVIVED as declared
 * A1 S02 force `receipt.spec == valid.spec` to true            exit 0  SURVIVED as declared
 */

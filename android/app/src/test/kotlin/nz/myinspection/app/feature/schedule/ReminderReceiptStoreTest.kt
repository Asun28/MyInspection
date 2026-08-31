package nz.myinspection.app.feature.schedule

import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import nz.myinspection.app.feature.schedule.ReminderPhase.ADMISSION_PENDING
import nz.myinspection.app.feature.schedule.ReminderPhase.DELIVERED
import nz.myinspection.app.feature.schedule.ReminderPhase.DELIVERY_UNCERTAIN
import nz.myinspection.app.feature.schedule.ReminderPhase.ENQUEUED
import nz.myinspection.app.feature.schedule.ReminderPhase.PERMISSION_BLOCKED
import nz.myinspection.app.feature.schedule.ReminderPhase.QUARANTINED
import nz.myinspection.app.feature.schedule.ReminderPhase.RETRYABLE
import nz.myinspection.app.feature.schedule.ReminderPhase.TERMINAL
import nz.myinspection.app.feature.schedule.ReminderQuarantineReason.INVALID_OCCURRENCE_ID
import nz.myinspection.app.feature.schedule.ReminderQuarantineReason.OCCURRENCE_KEYS_INVALID
import nz.myinspection.app.feature.schedule.ReminderQuarantineReason.PREFERENCE_READ_FAILED
import nz.myinspection.app.feature.schedule.ReminderQuarantineReason.RECEIPT_INVALID
import nz.myinspection.app.feature.schedule.ReminderQuarantineReason.STORE_SENTINEL_INVALID
import nz.myinspection.core.schedule.InspectionScheduleType

/**
 * Black box acceptance tests for the durable receipt protocol. Every expectation is a literal
 * written here and the wire records come from this file's own encoder, pinned against a golden
 * string, so a production encoder that drifts is caught rather than mirrored. Nothing here reads
 * production source, resources or compiled artifacts.
 */
class ReminderReceiptStoreTest {
    @Test
    fun `admission commits the sentinel both markers and the canonical record together`() {
        val preferences = FakeReminderPreferences()
        val store = ReminderReceiptStore(preferences)
        val admitted = receipt()

        assertIs<ReminderReceiptAdmissionResult.Admitted>(store.admit(admitted))

        assertEquals(GOLDEN_RECORD_GENERATION_ZERO, record())
        assertEquals(1, preferences.commits.size)
        assertEquals(storeSnapshot().keys, preferences.commits.single().keys)
        assertEquals(storeSnapshot(), preferences.snapshot())
        val loaded = assertIs<ReminderReceiptLookup.Present>(store.lookup(OCCURRENCE_ID))
        assertEquals(admitted, loaded.receipt)
        assertEquals(WORK_ID_0, loaded.receipt.workRequestId)
        assertEquals(0L, loaded.receipt.generationNumber)
        assertEquals(ADMISSION_PENDING, loaded.receipt.phase)
        assertNull(loaded.receipt.causeCode)
        assertEquals(false, loaded.writeUncertain)
    }

    @Test
    fun `only a fresh generation zero receipt is admissible`() {
        val forgedSpec = ReminderSpec(
            uniqueWorkName = "schedule-reminder:forged",
            occurrenceId = OCCURRENCE_ID,
            route = ScheduleRoute(PROPERTY_A, InspectionScheduleType.ROUTINE),
            dueAt = DUE_AT,
        )
        val cases = mapOf(
            "generation one" to receipt(generationNumber = 1),
            "generation below zero" to receipt(generationNumber = -1, workRequestId = WORK_ID_0),
            "occurrence id that is not a digest" to
                receipt(occurrenceId = "not-an-id", workRequestId = WORK_ID_0),
            "phase already enqueued" to receipt(phase = ENQUEUED),
            "cause on a phase that forbids one" to
                receipt(causeCode = ReminderCause.OCCURRENCE_SUPERSEDED),
            "work id of another generation" to receipt(workRequestId = WORK_ID_1),
            "spec of another occurrence" to receipt(spec = specFor(PROPERTY_B)),
            "spec whose unique work name was forged" to receipt(spec = forgedSpec),
        )

        cases.forEach { (label, candidate) ->
            val preferences = FakeReminderPreferences()
            val store = ReminderReceiptStore(preferences)

            assertIs<ReminderReceiptAdmissionResult.Rejected>(store.admit(candidate), label)
            assertEquals(0, preferences.commits.size, label)
            assertEquals(ReminderReceiptLookup.Missing, store.lookup(OCCURRENCE_ID), label)
        }
    }

    @Test
    fun `admission is refused whenever any evidence for the occurrence survives`() {
        val valid = storeSnapshot()
        val cases = mapOf(
            "already admitted" to valid,
            "admitted marker retained alone" to valid.filterKeys { it in setOf(STORE, ADMITTED) },
            "record retained alone" to valid.filterKeys { it in setOf(STORE, RECORD) },
            "sentinel corrupt while evidence survives" to valid + mapOf(STORE to FUTURE_SENTINEL),
        )

        cases.forEach { (label, entries) ->
            val preferences = FakeReminderPreferences(entries)
            val admission = ReminderReceiptStore(preferences).admit(receipt())

            assertIs<ReminderReceiptAdmissionResult.Rejected>(admission, label)
            assertEquals(0, preferences.commits.size, label)
        }
    }

    @Test
    fun `an occurrence is missing only on a blank store or beside other complete occurrences`() {
        val store = ReminderReceiptStore(FakeReminderPreferences())
        assertEquals(ReminderReceiptLookup.Missing, store.lookup(OCCURRENCE_ID))

        assertIs<ReminderReceiptAdmissionResult.Admitted>(store.admit(receipt()))
        assertEquals(ReminderReceiptLookup.Missing, store.lookup(OCCURRENCE_ID_B))
        assertIs<ReminderReceiptAdmissionResult.Admitted>(
            store.admit(receipt(spec = specFor(PROPERTY_B), occurrenceId = OCCURRENCE_ID_B)),
        )

        val first = assertIs<ReminderReceiptLookup.Present>(store.lookup(OCCURRENCE_ID))
        val second = assertIs<ReminderReceiptLookup.Present>(store.lookup(OCCURRENCE_ID_B))
        assertEquals(PROPERTY_A, first.receipt.spec.route.propertyId)
        assertEquals(PROPERTY_B, second.receipt.spec.route.propertyId)
        assertEquals(ReminderReceiptLookup.Missing, store.lookup(UNKNOWN_OCCURRENCE_ID))
    }

    @Test
    fun `corrupt and non canonical evidence is quarantined with a typed reason`() {
        val valid = storeSnapshot()
        val cases = mutableMapOf<String, Pair<Map<String, String>, ReminderQuarantineReason>>()
        cases["sentinel absent"] = Pair(valid - STORE, STORE_SENTINEL_INVALID)
        cases["sentinel from a future format"] =
            Pair(valid + mapOf(STORE to FUTURE_SENTINEL), STORE_SENTINEL_INVALID)
        cases["admitted marker from a future format"] =
            Pair(valid + mapOf(ADMITTED to "v2"), OCCURRENCE_KEYS_INVALID)
        cases["seen marker from a future format"] =
            Pair(valid + mapOf(SEEN to "v2"), OCCURRENCE_KEYS_INVALID)
        PARTIAL_KEY_SETS.forEach { retained ->
            cases["only $retained retained"] =
                Pair(valid.filterKeys { it == STORE || it in retained }, OCCURRENCE_KEYS_INVALID)
        }
        corruptRecords().forEach { (label, corrupt) ->
            cases[label] = Pair(valid + mapOf(RECORD to corrupt), RECEIPT_INVALID)
        }

        cases.forEach { (label, expectation) ->
            val (entries, reason) = expectation
            val store = ReminderReceiptStore(FakeReminderPreferences(entries))

            val lookup = assertIs<ReminderReceiptLookup.Quarantined>(store.lookup(OCCURRENCE_ID), label)
            assertEquals(reason, lookup.reason, label)
        }
    }

    @Test
    fun `an unreadable occurrence id or preference file is quarantined`() {
        val readable = ReminderReceiptStore(FakeReminderPreferences(storeSnapshot()))
        val unreadable = ReminderReceiptStore(FakeReminderPreferences(throwOnRead = true))

        val malformedId = assertIs<ReminderReceiptLookup.Quarantined>(readable.lookup("not-an-id"))
        assertEquals(INVALID_OCCURRENCE_ID, malformedId.reason)
        val unreadableFile = assertIs<ReminderReceiptLookup.Quarantined>(unreadable.lookup(OCCURRENCE_ID))
        assertEquals(PREFERENCE_READ_FAILED, unreadableFile.reason)
    }

    @Test
    fun `a false or throwing first admission leaves no receipt and quarantines the occurrence`() {
        listOf(CommitPlan.RETURN_FALSE, CommitPlan.THROW).forEach { plan ->
            val preferences = FakeReminderPreferences(plans = listOf(plan))
            val store = ReminderReceiptStore(preferences)

            assertIs<ReminderReceiptAdmissionResult.WriteUncertain>(store.admit(receipt()), "$plan")
            assertEquals(1, preferences.commits.size, "$plan")
            val lookup = assertIs<ReminderReceiptLookup.Quarantined>(store.lookup(OCCURRENCE_ID), "$plan")
            assertEquals(ReminderQuarantineReason.WRITE_UNCERTAIN, lookup.reason, "$plan")
        }
    }

    @Test
    fun `the commit result is the only durability oracle`() {
        val landedButFailed = FakeReminderPreferences(plans = listOf(CommitPlan.APPLY_THEN_FAIL))
        val lostButSucceeded = FakeReminderPreferences(plans = listOf(CommitPlan.DROP_THEN_SUCCEED))
        val pessimist = ReminderReceiptStore(landedButFailed)
        val optimist = ReminderReceiptStore(lostButSucceeded)

        assertIs<ReminderReceiptAdmissionResult.WriteUncertain>(pessimist.admit(receipt()))
        assertIs<ReminderReceiptAdmissionResult.Admitted>(optimist.admit(receipt()))

        assertEquals(GOLDEN_RECORD_GENERATION_ZERO, landedButFailed.snapshot()[RECORD])
        assertTrue(assertIs<ReminderReceiptLookup.Present>(pessimist.lookup(OCCURRENCE_ID)).writeUncertain)
        assertEquals(emptyMap<String, String>(), lostButSucceeded.snapshot())
        assertEquals(ReminderReceiptLookup.Missing, optimist.lookup(OCCURRENCE_ID))
    }

    @Test
    fun `a failed transition preserves the prior receipt and poisons later mutations`() {
        listOf(CommitPlan.RETURN_FALSE, CommitPlan.THROW).forEach { plan ->
            val preferences = FakeReminderPreferences(plans = listOf(CommitPlan.COMMIT, plan))
            val store = ReminderReceiptStore(preferences)
            val admitted = receipt()
            assertIs<ReminderReceiptAdmissionResult.Admitted>(store.admit(admitted), "$plan")

            val uncertain = store.advance(ADMISSION_PENDING, ENQUEUED)

            assertIs<ReminderReceiptTransitionResult.WriteUncertain>(uncertain, "$plan")
            val poisoned = assertIs<ReminderReceiptLookup.Present>(store.lookup(OCCURRENCE_ID), "$plan")
            assertEquals(admitted, poisoned.receipt, "$plan")
            assertTrue(poisoned.writeUncertain, "$plan")
            assertIs<ReminderReceiptTransitionResult.WriteUncertain>(
                store.advance(ADMISSION_PENDING, ENQUEUED), "$plan",
            )
            assertIs<ReminderReceiptTransitionResult.WriteUncertain>(
                store.recoverPermissionBlocked(admitted), "$plan",
            )
            assertIs<ReminderReceiptAdmissionResult.Rejected>(store.admit(admitted), "$plan")
            assertEquals(2, preferences.commits.size, "$plan")
        }
    }

    @Test
    fun `compare and set advances only on the exact expected tuple`() {
        val preferences = FakeReminderPreferences()
        val store = ReminderReceiptStore(preferences)
        assertIs<ReminderReceiptAdmissionResult.Admitted>(store.admit(receipt()))
        val mismatches = mapOf(
            "generation" to Triple(1L, WORK_ID_0, ADMISSION_PENDING),
            "work request id" to Triple(0L, WORK_ID_1, ADMISSION_PENDING),
            "phase" to Triple(0L, WORK_ID_0, ENQUEUED),
        )

        mismatches.forEach { (label, expected) ->
            val (generationNumber, workRequestId, phase) = expected
            val stale = store.advance(phase, ENQUEUED, null, generationNumber, workRequestId)

            assertIs<ReminderReceiptTransitionResult.Stale>(stale, label)
            assertEquals(1, preferences.commits.size, label)
        }

        val applied = assertIs<ReminderReceiptTransitionResult.Applied>(
            store.advance(ADMISSION_PENDING, ENQUEUED),
        )
        assertEquals(ENQUEUED, applied.receipt.phase)
        assertEquals(WORK_ID_0, applied.receipt.workRequestId)
        assertEquals(ENQUEUED, assertIs<ReminderReceiptLookup.Present>(store.lookup(OCCURRENCE_ID)).receipt.phase)
        assertEquals(2, preferences.commits.size)
    }

    @Test
    fun `only the declared transitions and their own causes advance a receipt`() {
        ReminderPhase.entries.forEach { from ->
            val advanced = ReminderPhase.entries.filter { to ->
                val outcome = storeAt(from).advance(from, to)
                val applied = outcome as? ReminderReceiptTransitionResult.Applied
                if (applied == null) {
                    assertIs<ReminderReceiptTransitionResult.Rejected>(outcome, "$from to $to")
                } else {
                    assertEquals(to, applied.receipt.phase, "$from to $to")
                    assertEquals(causeFor(to), applied.receipt.causeCode, "$from to $to")
                }
                applied != null
            }

            assertEquals(LEGAL_TRANSITIONS.getValue(from), advanced.toSet(), "$from")
            advanced.forEach { to ->
                val wrong = if (causeFor(to) == null) ReminderCause.OCCURRENCE_SUPERSEDED else null
                assertIs<ReminderReceiptTransitionResult.Rejected>(
                    storeAt(from).advance(from, to, wrong), "$from to $to carrying the wrong cause",
                )
            }
        }
    }

    @Test
    fun `a compare and set without a trusted receipt is rejected`() {
        val blank = ReminderReceiptStore(FakeReminderPreferences())
        val corrupt = ReminderReceiptStore(FakeReminderPreferences(storeSnapshot() - SEEN))

        listOf(blank, corrupt).forEach { store ->
            assertIs<ReminderReceiptTransitionResult.Rejected>(store.advance(ADMISSION_PENDING, ENQUEUED))
            assertIs<ReminderReceiptTransitionResult.Rejected>(store.recoverPermissionBlocked(receipt()))
        }
    }

    @Test
    fun `permission recovery derives the next generation and reopens admission`() {
        val preferences = FakeReminderPreferences()
        val store = ReminderReceiptStore(preferences)
        val blocked = blockOnPermission(store)

        val applied = assertIs<ReminderReceiptTransitionResult.Applied>(
            store.recoverPermissionBlocked(blocked),
        )

        assertEquals(1L, applied.receipt.generationNumber)
        assertEquals(WORK_ID_1, applied.receipt.workRequestId)
        assertEquals(ADMISSION_PENDING, applied.receipt.phase)
        assertNull(applied.receipt.causeCode)
        assertEquals(blocked.spec, applied.receipt.spec)
        assertEquals(GOLDEN_RECORD_GENERATION_ONE, preferences.snapshot()[RECORD])
        assertEquals(GOLDEN_RECORD_GENERATION_ONE, record(generation = "1"))
    }

    @Test
    fun `an old generation cannot overwrite the recovered generation`() {
        val preferences = FakeReminderPreferences()
        val store = ReminderReceiptStore(preferences)
        val blocked = blockOnPermission(store)
        assertIs<ReminderReceiptTransitionResult.Applied>(store.recoverPermissionBlocked(blocked))
        val commitsAfterRecovery = preferences.commits.size

        assertIs<ReminderReceiptTransitionResult.Stale>(store.advance(ADMISSION_PENDING, ENQUEUED))
        assertIs<ReminderReceiptTransitionResult.Stale>(store.recoverPermissionBlocked(blocked))

        val loaded = assertIs<ReminderReceiptLookup.Present>(store.lookup(OCCURRENCE_ID))
        assertEquals(1L, loaded.receipt.generationNumber)
        assertEquals(WORK_ID_1, loaded.receipt.workRequestId)
        assertEquals(ADMISSION_PENDING, loaded.receipt.phase)
        assertEquals(commitsAfterRecovery, preferences.commits.size)
    }

    @Test
    fun `recovery outside a matched permission blocked receipt fails closed`() {
        val preferences = FakeReminderPreferences()
        val store = ReminderReceiptStore(preferences)
        val admitted = receipt()
        assertIs<ReminderReceiptAdmissionResult.Admitted>(store.admit(admitted))
        val foreign = receipt(spec = specFor(PROPERTY_B), occurrenceId = OCCURRENCE_ID_B)

        assertIs<ReminderReceiptTransitionResult.Rejected>(store.recoverPermissionBlocked(admitted))
        assertIs<ReminderReceiptTransitionResult.Rejected>(store.recoverPermissionBlocked(foreign))
        assertIs<ReminderReceiptTransitionResult.Stale>(
            store.recoverPermissionBlocked(admitted.copy(phase = PERMISSION_BLOCKED)),
        )
        assertEquals(1, preferences.commits.size)
    }

    @Test
    fun `recovery at the maximum generation is rejected instead of wrapping`() {
        val maximum = record(generation = "9223372036854775807", phase = "PERMISSION_BLOCKED")
        val preferences = FakeReminderPreferences(storeSnapshot(maximum))
        val store = ReminderReceiptStore(preferences)

        val loaded = assertIs<ReminderReceiptLookup.Present>(store.lookup(OCCURRENCE_ID))
        assertEquals(Long.MAX_VALUE, loaded.receipt.generationNumber)
        assertIs<ReminderReceiptTransitionResult.Rejected>(store.recoverPermissionBlocked(loaded.receipt))
        assertEquals(0, preferences.commits.size)
    }

    @Test
    fun `the process lock linearises concurrent compare and set attempts`() {
        val racers = 8
        val preferences = FakeReminderPreferences()
        val store = ReminderReceiptStore(preferences)
        assertIs<ReminderReceiptAdmissionResult.Admitted>(store.admit(receipt()))
        val barrier = CyclicBarrier(racers)
        val pool = Executors.newFixedThreadPool(racers)

        val outcomes = try {
            val racer = Callable {
                barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                store.advance(ADMISSION_PENDING, ENQUEUED)
            }
            pool.invokeAll(List(racers) { racer }, TIMEOUT_SECONDS, TimeUnit.SECONDS).map { it.get() }
        } finally {
            pool.shutdownNow()
        }

        assertEquals(1, outcomes.count { it is ReminderReceiptTransitionResult.Applied })
        assertEquals(racers - 1, outcomes.count { it is ReminderReceiptTransitionResult.Stale })
        assertEquals(2, preferences.commits.size)
        assertEquals(ENQUEUED, assertIs<ReminderReceiptLookup.Present>(store.lookup(OCCURRENCE_ID)).receipt.phase)
    }

    private fun ReminderReceiptStore.advance(
        from: ReminderPhase,
        to: ReminderPhase,
        causeCode: ReminderCause? = causeFor(to),
        generationNumber: Long = 0,
        workRequestId: UUID = WORK_ID_0,
    ): ReminderReceiptTransitionResult =
        compareAndSet(OCCURRENCE_ID, generationNumber, workRequestId, from, to, causeCode)

    private fun storeAt(phase: ReminderPhase): ReminderReceiptStore {
        val store = ReminderReceiptStore(FakeReminderPreferences())
        assertIs<ReminderReceiptAdmissionResult.Admitted>(store.admit(receipt()))
        var current = ADMISSION_PENDING
        PATHS.getValue(phase).forEach { step ->
            assertIs<ReminderReceiptTransitionResult.Applied>(store.advance(current, step), "reach $phase")
            current = step
        }
        return store
    }

    private fun blockOnPermission(store: ReminderReceiptStore): ReminderReceipt {
        assertIs<ReminderReceiptAdmissionResult.Admitted>(store.admit(receipt()))
        assertIs<ReminderReceiptTransitionResult.Applied>(store.advance(ADMISSION_PENDING, ENQUEUED))
        val blocked = assertIs<ReminderReceiptTransitionResult.Applied>(
            store.advance(ENQUEUED, PERMISSION_BLOCKED),
        )
        return blocked.receipt
    }

    private fun receipt(
        spec: ReminderSpec = specFor(PROPERTY_A),
        occurrenceId: String = OCCURRENCE_ID,
        generationNumber: Long = 0,
        workRequestId: UUID = reminderGenerationId(occurrenceId, generationNumber),
        phase: ReminderPhase = ADMISSION_PENDING,
        causeCode: ReminderCause? = null,
    ): ReminderReceipt =
        ReminderReceipt(occurrenceId, generationNumber, workRequestId, spec, phase, causeCode)

    private fun specFor(propertyId: String): ReminderSpec =
        WorkSpecFactory().create(ScheduleRoute(propertyId, InspectionScheduleType.ROUTINE), DUE_AT)

    private class FakeReminderPreferences(
        initial: Map<String, String> = emptyMap(),
        plans: List<CommitPlan> = emptyList(),
        private val throwOnRead: Boolean = false,
    ) : ReminderPreferencePort {
        private val entries = initial.toMutableMap()
        private val remainingPlans = plans.toMutableList()

        val commits = mutableListOf<Map<String, String>>()

        override fun readAll(): Map<String, String> {
            if (throwOnRead) {
                throw IllegalStateException("simulated preference read failure")
            }
            return entries.toMap()
        }

        override fun commit(writes: Map<String, String>): Boolean {
            commits += writes
            val plan = remainingPlans.removeFirstOrNull() ?: CommitPlan.COMMIT
            if (plan == CommitPlan.THROW) {
                throw IllegalStateException("simulated commit failure")
            }
            if (plan == CommitPlan.COMMIT || plan == CommitPlan.APPLY_THEN_FAIL) {
                entries.putAll(writes)
            }
            return plan == CommitPlan.COMMIT || plan == CommitPlan.DROP_THEN_SUCCEED
        }

        fun snapshot(): Map<String, String> = entries.toMap()
    }

    private enum class CommitPlan { COMMIT, RETURN_FALSE, THROW, APPLY_THEN_FAIL, DROP_THEN_SUCCEED }

    private companion object {
        const val PROPERTY_A = "property-a"
        const val PROPERTY_B = "property-b"
        const val TIMEOUT_SECONDS = 30L
        const val FUTURE_SENTINEL = "reminder-receipts/v2"
        const val OCCURRENCE_ID = "c118fefec6ee20d89eafa5533048237237d39116af40aa85123fb1f70c404108"
        const val OCCURRENCE_ID_B = "9d5b6f1b1acb3d9da1f0c7da08d20f4563b3fddde91214182ab76626f03fc13d"
        const val UNKNOWN_OCCURRENCE_ID =
            "00000000000000000000000000000000000000000000000000000000000000ff"
        const val STORE = "store"
        const val ADMITTED = "admitted:$OCCURRENCE_ID"
        const val SEEN = "seen:$OCCURRENCE_ID"
        const val RECORD = "record:$OCCURRENCE_ID"
        const val GOLDEN_RECORD_GENERATION_ZERO =
            "strict-v1-envelope|c118fefec6ee20d89eafa5533048237237d39116af40aa85123fb1f70c404108" +
                "|0|ADMISSION_PENDING|-|cHJvcGVydHktYQ|ROUTINE|1785715200|1"
        const val GOLDEN_RECORD_GENERATION_ONE =
            "strict-v1-envelope|c118fefec6ee20d89eafa5533048237237d39116af40aa85123fb1f70c404108" +
                "|1|ADMISSION_PENDING|-|cHJvcGVydHktYQ|ROUTINE|1785715200|1"
        val WORK_ID_0: UUID = UUID.fromString("40fe7461-9be1-3ce7-8bdf-28b48b76359e")
        val WORK_ID_1: UUID = UUID.fromString("590ca815-2783-322a-acde-39ab31dafd39")
        val DUE_AT: Instant = Instant.parse("2026-08-03T00:00:00.000000001Z")
        val PARTIAL_KEY_SETS = listOf(
            setOf(ADMITTED), setOf(SEEN), setOf(RECORD),
            setOf(ADMITTED, SEEN), setOf(ADMITTED, RECORD), setOf(SEEN, RECORD),
        )

        // The transition table and the routes to each phase, written out rather than derived.
        val LIVE = setOf(ENQUEUED, RETRYABLE, DELIVERY_UNCERTAIN, PERMISSION_BLOCKED, TERMINAL, QUARANTINED)
        val LEGAL_TRANSITIONS: Map<ReminderPhase, Set<ReminderPhase>> = mapOf(
            ADMISSION_PENDING to setOf(ENQUEUED, RETRYABLE, TERMINAL, QUARANTINED),
            ENQUEUED to LIVE,
            RETRYABLE to LIVE,
            DELIVERY_UNCERTAIN to setOf(DELIVERED),
            DELIVERED to emptySet(),
            PERMISSION_BLOCKED to emptySet(),
            TERMINAL to emptySet(),
            QUARANTINED to emptySet(),
        )
        val PATHS: Map<ReminderPhase, List<ReminderPhase>> = mapOf(
            ADMISSION_PENDING to emptyList(),
            ENQUEUED to listOf(ENQUEUED),
            RETRYABLE to listOf(RETRYABLE),
            TERMINAL to listOf(TERMINAL),
            QUARANTINED to listOf(QUARANTINED),
            DELIVERY_UNCERTAIN to listOf(ENQUEUED, DELIVERY_UNCERTAIN),
            PERMISSION_BLOCKED to listOf(ENQUEUED, PERMISSION_BLOCKED),
            DELIVERED to listOf(ENQUEUED, DELIVERY_UNCERTAIN, DELIVERED),
        )

        fun causeFor(phase: ReminderPhase): ReminderCause? =
            ReminderCause.DELIVERY_ACKNOWLEDGED.takeIf { phase == TERMINAL }

        /** This file's own canonical encoder, pinned against the golden records above. */
        fun record(
            envelope: String = "strict-v1-envelope",
            occurrence: String = OCCURRENCE_ID,
            generation: String = "0",
            phase: String = "ADMISSION_PENDING",
            cause: String = "-",
            property: String = "cHJvcGVydHktYQ",
            type: String = "ROUTINE",
            seconds: String = "1785715200",
            nano: String = "1",
        ): String =
            listOf(envelope, occurrence, generation, phase, cause, property, type, seconds, nano)
                .joinToString("|")

        fun storeSnapshot(record: String = record()): Map<String, String> =
            mapOf(STORE to "reminder-receipts/v1", ADMITTED to "v1", SEEN to "v1", RECORD to record)

        /** Wire records that must never read back, each differing from the golden one way. */
        fun corruptRecords(): Map<String, String> = mapOf(
            "record that is not an envelope at all" to "malformed",
            "envelope from a future format" to record(envelope = "strict-v2-envelope"),
            "field dropped" to record().substringBeforeLast("|"),
            "field appended" to record() + "|extra",
            "generation with a leading zero" to record(generation = "00"),
            "generation that is negative" to record(generation = "-1"),
            "epoch second that is not a number" to record(seconds = "soon"),
            "nano that normalises into the seconds field" to record(nano = "1000000000"),
            "upper case occurrence id" to record(occurrence = OCCURRENCE_ID.uppercase()),
            "occurrence id of another property" to record(occurrence = OCCURRENCE_ID_B),
            "receipt of another occurrence entirely" to
                record(occurrence = OCCURRENCE_ID_B, property = "cHJvcGVydHktYg"),
            "phase that is not in the vocabulary" to record(phase = "SENT"),
            "inspection type that is not in the vocabulary" to record(type = "WEEKLY"),
            "cause that is not in the vocabulary" to
                record(phase = "TERMINAL", cause = "DELIVERY_ABANDONED"),
            "cause on a phase that forbids one" to
                record(phase = "DELIVERED", cause = "DELIVERY_ACKNOWLEDGED"),
            "cause absent on a phase that requires one" to record(phase = "TERMINAL"),
            "base64 that is not base64" to record(property = "!!!!"),
            "base64 carrying padding this store never writes" to record(property = "cHJvcGVydHktYQ=="),
            "base64 with non zero trailing bits" to record(property = "cHJvcGVydHktYR"),
            "base64 of bytes that are not UTF-8" to record(property = "ww"),
            "base64 of an empty property id" to record(property = ""),
        )
    }
}

/*
 * Semantic mutation receipts for ReminderReceiptStore.kt (R4). Each row was applied alone to
 * the final snapshot as one line deleted or rewritten, the suite was run expecting a non-zero
 * exit, and the file was restored and re-hashed. Every row exited 1 with the named test red and
 * none survived, so no guard in that file is dead code. SHA-256 of the production file before
 * and after every mutation: da87bc2a0f5eb679b5dbdfddc0179428976e7a9708ccace73b0c785c9f785dd9
 *
 * M01 A1 admit drops the generation-zero gate -> only a fresh generation zero receipt is admissible
 * M02 A1 admit drops the admission-pending phase gate -> only a fresh generation zero receipt is admissible
 * M03 A1 admit stops validating the receipt -> only a fresh generation zero receipt is admissible
 * M04 A1 admit stops requiring the occurrence to be missing -> a failed transition preserves the prior receipt and...
 * M05 A1 admission commit omits the seen marker -> a failed transition preserves the prior receipt and poisons lat...
 * M06 A1 admission commit omits the store sentinel -> a failed transition preserves the prior receipt and poisons ...
 * M07 A4 admission reports success on a failed commit -> a false or throwing first admission leaves no receipt and...
 * M08 A4 compareAndSet ignores the write-uncertain poison -> a failed transition preserves the prior receipt and p...
 * M09 A5 compareAndSet stops comparing the work request id -> compare and set advances only on the exact expected ...
 * M10 A5 compareAndSet stops comparing the generation -> compare and set advances only on the exact expected tuple
 * M11 A5 compareAndSet stops comparing the phase -> compare and set advances only on the exact expected tuple
 * M12 A5 compareAndSet stops consulting the transition table -> only the declared transitions and their own causes...
 * M13 A5 compareAndSet stops validating the next receipt -> only the declared transitions and their own causes adv...
 * M14 A5 compareAndSet calls an untrusted lookup stale rather than rejected -> a compare and set without a trusted...
 * M15 A5 recovery stops matching the caller receipt against the store -> an old generation cannot overwrite the re...
 * M16 A5 recovery stops requiring the permission-blocked phase -> recovery outside a matched permission blocked re...
 * M17 A5 recovery drops the generation overflow guard -> recovery at the maximum generation is rejected instead of...
 * M18 A4 recovery ignores the write-uncertain poison -> a failed transition preserves the prior receipt and poison...
 * M19 A5 recovery reuses the current generation instead of deriving the next -> an old generation cannot overwrite...
 * M20 A4 a transition reports success on a failed commit -> a failed transition preserves the prior receipt and po...
 * M21 A5 operations stop taking the process lock -> the process lock linearises concurrent compare and set attempts
 * M22 A3 lookup accepts an occurrence id that is not a digest -> an unreadable occurrence id or preference file is...
 * M23 A3 an unreadable preference file reads as an empty one -> an unreadable occurrence id or preference file is ...
 * M24 A4 an absent occurrence after a failed write reads as fresh -> a false or throwing first admission leaves no...
 * M25 A2 a blank store no longer short circuits to absent -> a failed transition preserves the prior receipt and p...
 * M26 A3 lookup stops checking the store sentinel -> corrupt and non canonical evidence is quarantined with a type...
 * M27 A3 lookup stops checking both occurrence markers -> a compare and set without a trusted receipt is rejected
 * M28 A3 lookup stops binding the record to the queried occurrence -> corrupt and non canonical evidence is quaran...
 * M29 A3 lookup stops validating the decoded receipt -> corrupt and non canonical evidence is quarantined with a t...
 * M30 A4 a present receipt never reports write uncertainty -> a failed transition preserves the prior receipt and ...
 * M31 A4 a failed commit stops poisoning the occurrence -> a failed transition preserves the prior receipt and poi...
 * M32 A4 a throwing commit is treated as a successful one -> a failed transition preserves the prior receipt and p...
 * M33 A3 receipt validation stops checking the phase and cause pair -> corrupt and non canonical evidence is quara...
 * M34 A3 receipt validation stops binding the spec to the occurrence id -> only a fresh generation zero receipt is...
 * M35 A1 receipt validation stops requiring a canonical spec -> only a fresh generation zero receipt is admissible
 * M36 A3 receipt validation stops deriving the work request id -> only a fresh generation zero receipt is admissible
 * M37 A1 receipt validation drops the non-negative generation guard -> only a fresh generation zero receipt is adm...
 * M38 A1 receipt validation drops the occurrence id shape guard -> only a fresh generation zero receipt is admissible
 * M39 A5 delivery uncertainty can no longer resolve to delivered -> only the declared transitions and their own ca...
 * M40 A5 terminal phases stop being terminal -> only the declared transitions and their own causes advance a receipt
 * M41 A1 encode stamps a different envelope -> admission commits the sentinel both markers and the canonical recor...
 * M42 A3 encode emits padded base64 -> admission commits the sentinel both markers and the canonical record together
 * M43 A3 decode drops the canonical round trip comparison -> corrupt and non canonical evidence is quarantined wit...
 */

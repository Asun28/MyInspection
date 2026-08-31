package nz.myinspection.app.feature.schedule

import android.content.Context
import android.content.SharedPreferences
import java.time.Instant
import java.util.Base64
import java.util.IdentityHashMap
import java.util.UUID
import nz.myinspection.core.schedule.InspectionScheduleType

/**
 * Lifecycle position of one occurrence. [QUARANTINED] is a phase a receipt is deliberately parked
 * in, unlike [ReminderReceiptLookup.Quarantined], which reports evidence the store refused to trust.
 */
enum class ReminderPhase {
    ADMISSION_PENDING,
    ENQUEUED,
    RETRYABLE,
    DELIVERY_UNCERTAIN,
    DELIVERED,
    PERMISSION_BLOCKED,
    TERMINAL,
    QUARANTINED,
}

/**
 * Reasons a receipt reached a phase whose name does not say why. Only [ReminderPhase.TERMINAL] is
 * reached for more than one reason, so only it carries a cause, and the vocabulary is closed so a
 * record naming anything else is refused rather than kept as opaque text.
 */
enum class ReminderCause {
    DELIVERY_ACKNOWLEDGED,
    PERMANENT_DELIVERY_FAILURE,
    OCCURRENCE_SUPERSEDED,
}

/** Why the store refused to read stored evidence as a receipt. */
enum class ReminderQuarantineReason {
    INVALID_OCCURRENCE_ID,
    PREFERENCE_READ_FAILED,
    WRITE_UNCERTAIN,
    STORE_SENTINEL_INVALID,
    OCCURRENCE_KEYS_INVALID,
    RECEIPT_INVALID,
}

data class ReminderReceipt(
    val occurrenceId: String,
    val generationNumber: Long,
    val workRequestId: UUID,
    val spec: ReminderSpec,
    val phase: ReminderPhase,
    val causeCode: ReminderCause?,
)

sealed interface ReminderReceiptLookup {
    data object Missing : ReminderReceiptLookup

    data class Present(
        val receipt: ReminderReceipt,
        val writeUncertain: Boolean = false,
    ) : ReminderReceiptLookup

    data class Quarantined(val reason: ReminderQuarantineReason) : ReminderReceiptLookup
}

sealed interface ReminderReceiptAdmissionResult {
    data object Admitted : ReminderReceiptAdmissionResult
    data object Rejected : ReminderReceiptAdmissionResult
    data object WriteUncertain : ReminderReceiptAdmissionResult
}

sealed interface ReminderReceiptTransitionResult {
    data class Applied(val receipt: ReminderReceipt) : ReminderReceiptTransitionResult
    data class Stale(val lookup: ReminderReceiptLookup) : ReminderReceiptTransitionResult
    data object Rejected : ReminderReceiptTransitionResult
    data object WriteUncertain : ReminderReceiptTransitionResult
}

/**
 * The only durability primitive the store may use. [commit] reports whether the whole write group
 * reached durable storage, and is the sole durability oracle: the store never reads back what it
 * just wrote, because that read can be served from an in memory cache which outlives an unflushed
 * file and would turn a lost write into a confident success.
 */
interface ReminderPreferencePort {
    /** The backing file. Two ports over one file must return the same instance, compared by identity. */
    val backingStore: Any

    fun readAll(): Map<String, String>

    fun commit(writes: Map<String, String>): Boolean
}

/**
 * Durable, application private evidence about reminder occurrences. Three keys carry one
 * occurrence: an immutable admitted marker, a seen marker and a record envelope, all written by the
 * single commit that also plants the store sentinel. Any surviving subset is corruption rather than
 * a fresh occurrence, which stops a half wiped store from silently re-admitting an occurrence that
 * already fired. Deletion of every key stays deliberately indistinguishable from fresh app data.
 *
 * The write uncertainty guard is keyed by the backing file rather than by this object, so every
 * store over one file shares one poison set however many instances exist.
 *
 * Failures are reported as typed results rather than logged here. The caller that owns
 * [ReminderDiagnosticPort] holds the stage, generation and work id this file never sees, so logging
 * from both places would emit one failure twice with less context on this side.
 */
class ReminderReceiptStore(
    private val preferences: ReminderPreferencePort,
) {
    fun lookup(occurrenceId: String): ReminderReceiptLookup = withProcessLock {
        lookupLocked(occurrenceId)
    }

    /** Admits a fresh occurrence. Only generation zero is ever a first admission. */
    fun admit(receipt: ReminderReceipt): ReminderReceiptAdmissionResult = withProcessLock {
        val admissible = isValidReceipt(receipt) &&
            receipt.generationNumber == 0L &&
            receipt.phase == ReminderPhase.ADMISSION_PENDING
        if (!admissible || lookupLocked(receipt.occurrenceId) != ReminderReceiptLookup.Missing) {
            return@withProcessLock ReminderReceiptAdmissionResult.Rejected
        }
        val writes = mapOf(
            STORE_KEY to STORE_VALUE,
            admittedKey(receipt.occurrenceId) to MARKER_VALUE,
            seenKey(receipt.occurrenceId) to MARKER_VALUE,
            recordKey(receipt.occurrenceId) to encodeReceipt(receipt),
        )
        if (commitLocked(receipt.occurrenceId, null, writes)) {
            ReminderReceiptAdmissionResult.Admitted
        } else {
            ReminderReceiptAdmissionResult.WriteUncertain
        }
    }

    /**
     * Advances a receipt only while the caller's whole expected tuple still holds. A tuple that no
     * longer matches is Stale, because the store moved on and a fresh read would show why. A
     * transition the state machine forbids is Rejected, because re-reading cannot make it legal.
     */
    fun compareAndSet(
        occurrenceId: String,
        expectedGenerationNumber: Long,
        expectedWorkRequestId: UUID,
        expectedPhase: ReminderPhase,
        nextPhase: ReminderPhase,
        causeCode: ReminderCause?,
    ): ReminderReceiptTransitionResult = withProcessLock {
        val lookup = lookupLocked(occurrenceId)
        val present = lookup as? ReminderReceiptLookup.Present
            ?: return@withProcessLock ReminderReceiptTransitionResult.Rejected
        if (present.writeUncertain) {
            return@withProcessLock ReminderReceiptTransitionResult.WriteUncertain
        }
        val current = present.receipt
        val expected = current.generationNumber == expectedGenerationNumber &&
            current.workRequestId == expectedWorkRequestId &&
            current.phase == expectedPhase
        if (!expected) {
            return@withProcessLock ReminderReceiptTransitionResult.Stale(lookup)
        }
        val next = current.copy(phase = nextPhase, causeCode = causeCode)
        if (!isAllowedTransition(current.phase, nextPhase) || !isValidReceipt(next)) {
            return@withProcessLock ReminderReceiptTransitionResult.Rejected
        }
        commitTransitionLocked(current, next)
    }

    /**
     * The sole generation increment. It takes the caller's view of the current
     * [ReminderPhase.PERMISSION_BLOCKED] receipt, derives generation n plus one itself and writes
     * [ReminderPhase.ADMISSION_PENDING]. Deriving rather than accepting the next receipt makes
     * identity drift unrepresentable: once the caller's view matches what is stored, no supplied
     * occurrence id, work request id or spec is left that could disagree with it.
     */
    fun recoverPermissionBlocked(current: ReminderReceipt): ReminderReceiptTransitionResult =
        withProcessLock {
            val lookup = lookupLocked(current.occurrenceId)
            val present = lookup as? ReminderReceiptLookup.Present
                ?: return@withProcessLock ReminderReceiptTransitionResult.Rejected
            if (present.writeUncertain) {
                return@withProcessLock ReminderReceiptTransitionResult.WriteUncertain
            }
            if (present.receipt != current) {
                return@withProcessLock ReminderReceiptTransitionResult.Stale(lookup)
            }
            if (current.phase != ReminderPhase.PERMISSION_BLOCKED ||
                current.generationNumber == Long.MAX_VALUE
            ) {
                return@withProcessLock ReminderReceiptTransitionResult.Rejected
            }
            val nextGeneration = current.generationNumber + 1
            commitTransitionLocked(
                current,
                current.copy(
                    generationNumber = nextGeneration,
                    workRequestId = reminderGenerationId(current.occurrenceId, nextGeneration),
                    phase = ReminderPhase.ADMISSION_PENDING,
                    causeCode = null,
                ),
            )
        }

    private fun commitTransitionLocked(
        prior: ReminderReceipt,
        next: ReminderReceipt,
    ): ReminderReceiptTransitionResult {
        val write = mapOf(recordKey(next.occurrenceId) to encodeReceipt(next))
        return if (commitLocked(next.occurrenceId, prior, write)) {
            ReminderReceiptTransitionResult.Applied(next)
        } else {
            ReminderReceiptTransitionResult.WriteUncertain
        }
    }

    private fun <T> withProcessLock(block: () -> T): T = synchronized(PROCESS_LOCK) {
        block()
    }

    private fun lookupLocked(occurrenceId: String): ReminderReceiptLookup {
        if (!occurrenceId.matches(OCCURRENCE_ID_PATTERN)) {
            return ReminderReceiptLookup.Quarantined(ReminderQuarantineReason.INVALID_OCCURRENCE_ID)
        }
        val poison = poisonLocked()
        if (poison.containsKey(occurrenceId)) {
            // The file may already hold the bytes of the write that was never confirmed, so it is
            // not consulted at all. Only the receipt that was durable before that write is exposed.
            return poison[occurrenceId]?.let { ReminderReceiptLookup.Present(it, writeUncertain = true) }
                ?: ReminderReceiptLookup.Quarantined(ReminderQuarantineReason.WRITE_UNCERTAIN)
        }
        val entries = try {
            preferences.readAll()
        } catch (_: Exception) {
            return ReminderReceiptLookup.Quarantined(ReminderQuarantineReason.PREFERENCE_READ_FAILED)
        }
        if (entries.isEmpty()) {
            return ReminderReceiptLookup.Missing
        }
        if (entries[STORE_KEY] != STORE_VALUE) {
            return ReminderReceiptLookup.Quarantined(ReminderQuarantineReason.STORE_SENTINEL_INVALID)
        }
        val admitted = entries[admittedKey(occurrenceId)]
        val seen = entries[seenKey(occurrenceId)]
        val encoded = entries[recordKey(occurrenceId)]
        if (admitted == null && seen == null && encoded == null) {
            return ReminderReceiptLookup.Missing
        }
        if (admitted != MARKER_VALUE || seen != MARKER_VALUE || encoded == null) {
            return ReminderReceiptLookup.Quarantined(ReminderQuarantineReason.OCCURRENCE_KEYS_INVALID)
        }
        val receipt = decodeReceipt(encoded)
            ?.takeIf { it.occurrenceId == occurrenceId && isValidReceipt(it) }
            ?: return ReminderReceiptLookup.Quarantined(ReminderQuarantineReason.RECEIPT_INVALID)
        return ReminderReceiptLookup.Present(receipt)
    }

    private fun commitLocked(
        occurrenceId: String,
        prior: ReminderReceipt?,
        writes: Map<String, String>,
    ): Boolean {
        val committed = try {
            preferences.commit(writes)
        } catch (_: Exception) {
            false
        }
        if (!committed) {
            poisonLocked()[occurrenceId] = prior
        }
        return committed
    }

    private fun poisonLocked(): MutableMap<String, ReminderReceipt?> =
        POISON.getOrPut(preferences.backingStore) { mutableMapOf() }

    companion object {
        const val PREFERENCES_NAME = "reminder-receipts-v1"
        private const val STORE_KEY = "store"
        private const val STORE_VALUE = "reminder-receipts/v1"
        private const val MARKER_VALUE = "v1"
        private const val ENVELOPE = "strict-v1-envelope"
        private const val NO_CAUSE = "-"
        private val PROCESS_LOCK = Any()

        // Keyed by identity, not equality, so a port that implements equals cannot borrow another
        // file's poison. One entry per backing file, which in the app is one for the process life.
        private val POISON = IdentityHashMap<Any, MutableMap<String, ReminderReceipt?>>()

        private fun admittedKey(occurrenceId: String): String = "admitted:$occurrenceId"

        private fun seenKey(occurrenceId: String): String = "seen:$occurrenceId"

        private fun recordKey(occurrenceId: String): String = "record:$occurrenceId"

        private fun isValidReceipt(receipt: ReminderReceipt): Boolean {
            // A cause is carried exactly where the phase name does not say why, and TERMINAL is
            // the only such phase, so the type alone decides which causes exist.
            val pairing = (receipt.causeCode != null) == (receipt.phase == ReminderPhase.TERMINAL)
            val derivedWorkId = receipt.occurrenceId.matches(OCCURRENCE_ID_PATTERN) &&
                receipt.generationNumber >= 0 &&
                receipt.workRequestId == reminderGenerationId(receipt.occurrenceId, receipt.generationNumber)
            return derivedWorkId &&
                receipt.spec.occurrenceId == receipt.occurrenceId &&
                receipt.spec == WorkSpecFactory().create(receipt.spec.route, receipt.spec.dueAt) &&
                pairing
        }

        private fun isAllowedTransition(current: ReminderPhase, next: ReminderPhase): Boolean = when (current) {
            ReminderPhase.ADMISSION_PENDING -> next in setOf(
                ReminderPhase.ENQUEUED, ReminderPhase.RETRYABLE,
                ReminderPhase.TERMINAL, ReminderPhase.QUARANTINED,
            )

            ReminderPhase.ENQUEUED, ReminderPhase.RETRYABLE -> next in setOf(
                ReminderPhase.ENQUEUED, ReminderPhase.RETRYABLE, ReminderPhase.DELIVERY_UNCERTAIN,
                ReminderPhase.PERMISSION_BLOCKED, ReminderPhase.TERMINAL, ReminderPhase.QUARANTINED,
            )

            ReminderPhase.DELIVERY_UNCERTAIN -> next == ReminderPhase.DELIVERED
            ReminderPhase.DELIVERED, ReminderPhase.PERMISSION_BLOCKED,
            ReminderPhase.TERMINAL, ReminderPhase.QUARANTINED,
            -> false
        }

        /**
         * Writes the one canonical spelling of a receipt. Derived fields are omitted rather than
         * stored beside their inputs: the unique work name, the work request id and the rest of the
         * spec fall out of property, type, due instant and generation, so a stored copy would only
         * be a second authority for a value the reader recomputes anyway.
         */
        private fun encodeReceipt(receipt: ReminderReceipt): String = listOf(
            ENVELOPE,
            receipt.occurrenceId,
            receipt.generationNumber.toString(),
            receipt.phase.name,
            receipt.causeCode?.name ?: NO_CAUSE,
            encodeText(receipt.spec.route.propertyId),
            receipt.spec.route.inspectionType.name,
            receipt.spec.dueAt.epochSecond.toString(),
            receipt.spec.dueAt.nano.toString(),
        ).joinToString("|")

        /**
         * Reads a record back, or returns null when the bytes are anything but the canonical
         * spelling this store would itself have written. The closing round trip comparison is
         * deliberately the single canonicity authority: it refuses padded and trailing bit variant
         * base64, an upper case enum spelling, a leading zero or signed integer, a nano field that
         * normalises into the seconds field, malformed UTF-8 (which decodes to the replacement
         * character and so can never re-encode to the stored bytes), a foreign envelope version
         * and an extra field. A separate version or arity check would be a second authority no
         * test could make fail, and too few fields never reach the comparison because indexing
         * past the end throws inside the same catch.
         */
        private fun decodeReceipt(encoded: String): ReminderReceipt? = try {
            val fields = encoded.split('|')
            val occurrenceId = fields[1]
            val generationNumber = fields[2].toLong()
            val phase = ReminderPhase.valueOf(fields[3])
            val causeCode = fields[4].takeUnless { it == NO_CAUSE }?.let(ReminderCause::valueOf)
            val route = ScheduleRoute(decodeText(fields[5]), InspectionScheduleType.valueOf(fields[6]))
            val dueAt = Instant.ofEpochSecond(fields[7].toLong(), fields[8].toLong())
            val receipt = ReminderReceipt(
                occurrenceId = occurrenceId,
                generationNumber = generationNumber,
                workRequestId = reminderGenerationId(occurrenceId, generationNumber),
                spec = WorkSpecFactory().create(route, dueAt),
                phase = phase,
                causeCode = causeCode,
            )
            require(encodeReceipt(receipt) == encoded)
            receipt
        } catch (_: Exception) {
            null
        }

        private fun encodeText(value: String): String = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.encodeToByteArray())

        private fun decodeText(value: String): String = Base64.getUrlDecoder()
            .decode(value)
            .decodeToString()
    }
}

internal class SharedPreferencesReminderPreferencePort(
    private val sharedPreferences: SharedPreferences,
) : ReminderPreferencePort {
    override val backingStore: Any
        get() = sharedPreferences

    override fun readAll(): Map<String, String> = sharedPreferences.all.mapValues { (_, value) ->
        value as? String ?: error("reminder preference value is not a string")
    }

    override fun commit(writes: Map<String, String>): Boolean {
        val editor = sharedPreferences.edit()
        writes.forEach(editor::putString)
        return editor.commit()
    }
}

/**
 * The process wide store over the application private preference file. The default [Context]
 * resolves to credential encrypted storage, so the file stays unreadable until first unlock after a
 * reboot. Device protected storage is deliberately not used: reminder evidence names a property a
 * tenant lives in and need not be legible before the user has unlocked the device.
 */
internal fun reminderReceiptStore(context: Context): ReminderReceiptStore = ReminderReceiptStore(
    SharedPreferencesReminderPreferencePort(
        context.applicationContext.getSharedPreferences(
            ReminderReceiptStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ),
    ),
)

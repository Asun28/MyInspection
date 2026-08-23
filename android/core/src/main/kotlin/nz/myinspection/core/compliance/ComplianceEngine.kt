package nz.myinspection.core.compliance

import java.time.Duration
import java.time.Instant
import java.util.Collections

/**
 * One already-scheduled visit, as the caller knows it.
 *
 * [entryId] exists so rescheduling can be expressed at all. Without an identity the row being moved is
 * indistinguishable from a competing row, so passing an unfiltered history made an inspection collide with
 * itself and report [ComplianceReasonKey.FREQUENCY_LIMIT] for a date it already legitimately occupied.
 * Callers pass the whole history and name the row under edit; they do not pre-filter. [entryId] must therefore
 * be a real identity: blank or repeated ids are refused rather than matched, because one name that fits several
 * rows excludes all of them.
 */
data class ExistingScheduledEntry(
    val entryId: String,
    val propertyId: String,
    val entryPurpose: String,
    val inspectionType: String,
    val scheduledAt: Instant,
)

data class ScheduleRequest(
    val propertyId: String,
    val entryPurpose: String,
    val inspectionType: String,
    val isBoardingHouse: Boolean,
    val scheduledAt: Instant,
    val noticeGivenAt: Instant,
    /** Audit fact only. Consent must never relax an inspection rule. */
    val tenantConsented: Boolean,
    val existingEntries: List<ExistingScheduledEntry>,
    /**
     * When this request reschedules an existing visit, the [ExistingScheduledEntry.entryId] of that visit.
     * It is excluded from the frequency comparison: a row must never block its own move. Null for new visits;
     * when non-null it must name exactly one row of [ScheduleRequest.existingEntries].
     */
    val currentEntryId: String? = null,
)

enum class ComplianceReasonKey {
    UNKNOWN_ENTRY_PURPOSE,
    UNKNOWN_INSPECTION_TYPE,
    INVALID_PROPERTY_ID,
    INVALID_HISTORY_ENTRY,
    NOTICE_TOO_SHORT,
    NOTICE_TOO_EARLY,
    OUTSIDE_VISIT_WINDOW,
    FREQUENCY_LIMIT,
}

data class ComplianceReason(val key: ComplianceReasonKey)

sealed interface ScheduleValidation {
    data object Pass : ScheduleValidation

    data class Blocked(val reasons: List<ComplianceReason>) : ScheduleValidation
}

/**
 * Pure schedule gate. All numeric thresholds and the civil timezone come from [ComplianceConfig].
 *
 * Notice uses elapsed time between instants. Visit hours and frequency days use the configured civil timezone: a
 * four-week boundary remains the same local time 28 days later even when NZ enters or leaves daylight saving.
 */
class ComplianceEngine(private val config: ComplianceConfig) {
    fun validateSchedule(request: ScheduleRequest): ScheduleValidation {
        val rule = config.rules[request.entryPurpose]
            ?: return blocked(ComplianceReasonKey.UNKNOWN_ENTRY_PURPOSE)

        val reasons = mutableListOf<ComplianceReason>()
        if (request.propertyId.isBlank()) reasons += ComplianceReason(ComplianceReasonKey.INVALID_PROPERTY_ID)
        if (request.inspectionType !in SUPPORTED_INSPECTION_TYPES) {
            reasons += ComplianceReason(ComplianceReasonKey.UNKNOWN_INSPECTION_TYPE)
        }

        val notice = Duration.between(request.noticeGivenAt, request.scheduledAt)
        if (notice < Duration.ofHours(rule.noticeMinHours.toLong())) {
            reasons += ComplianceReason(ComplianceReasonKey.NOTICE_TOO_SHORT)
        } else if (notice > Duration.ofDays(rule.noticeMaxDays.toLong())) {
            reasons += ComplianceReason(ComplianceReasonKey.NOTICE_TOO_EARLY)
        }

        val localTime = request.scheduledAt.atZone(config.timezone).toLocalTime()
        val closingTime = if (request.isBoardingHouse) {
            rule.visitWindow.boardingHouseEnd
        } else {
            rule.visitWindow.end
        }
        if (localTime.isBefore(rule.visitWindow.start) || localTime.isAfter(closingTime)) {
            reasons += ComplianceReason(ComplianceReasonKey.OUTSIDE_VISIT_WINDOW)
        }

        // History has to be trustworthy as identity and as data before it can excuse or trigger the cap.
        // entryId is free text the caller supplies: a blank id (an unpersisted row) or a repeated one makes a
        // single currentEntryId exclude several competing rows at once, and an id naming no row is not a
        // reschedule at all. A purpose the config does not know has no rule to be judged under, so leaving it
        // in the comparison would drop a genuine competitor silently while an unknown type below fails closed.
        val entryIds = request.existingEntries.map { it.entryId }
        val historyUsable = entryIds.none { it.isBlank() } &&
            entryIds.distinct().size == entryIds.size &&
            (request.currentEntryId == null || entryIds.count { it == request.currentEntryId } == 1) &&
            request.existingEntries.all { it.entryPurpose in config.rules }
        if (!historyUsable) reasons += ComplianceReason(ComplianceReasonKey.INVALID_HISTORY_ENTRY)

        if (historyUsable &&
            request.inspectionType in SUPPORTED_INSPECTION_TYPES &&
            request.inspectionType !in rule.frequencyLimit.exemptTypes
        ) {
            var frequencyBlocked = false
            request.existingEntries.forEach { existing ->
                // The row being rescheduled is not competition for itself.
                if (request.currentEntryId != null && existing.entryId == request.currentEntryId) return@forEach
                if (existing.propertyId == request.propertyId && existing.entryPurpose == request.entryPurpose) {
                    if (existing.inspectionType !in SUPPORTED_INSPECTION_TYPES) {
                        reasons += ComplianceReason(ComplianceReasonKey.INVALID_HISTORY_ENTRY)
                    } else if (existing.inspectionType !in rule.frequencyLimit.exemptTypes &&
                        areTooClose(existing.scheduledAt, request.scheduledAt, rule.frequencyLimit.days)
                    ) {
                        frequencyBlocked = true
                    }
                }
            }
            if (frequencyBlocked) reasons += ComplianceReason(ComplianceReasonKey.FREQUENCY_LIMIT)
        }

        // request.tenantConsented is intentionally not a branch: consent cannot override this configured gate.
        // The verdict lists the grounds for refusal, not one entry per offending row: INVALID_HISTORY_ENTRY is
        // raised per bad row above, and the UI and notice layers render this list verbatim.
        return if (reasons.isEmpty()) {
            ScheduleValidation.Pass
        } else {
            ScheduleValidation.Blocked(Collections.unmodifiableList(reasons.distinct()))
        }
    }

    private fun areTooClose(first: Instant, second: Instant, minimumDays: Int): Boolean {
        val (earlier, later) = if (first <= second) first to second else second to first
        val earliestAllowed = earlier.atZone(config.timezone).plusDays(minimumDays.toLong()).toInstant()
        return later.isBefore(earliestAllowed)
    }

    private fun blocked(key: ComplianceReasonKey): ScheduleValidation.Blocked =
        ScheduleValidation.Blocked(Collections.singletonList(ComplianceReason(key)))
}

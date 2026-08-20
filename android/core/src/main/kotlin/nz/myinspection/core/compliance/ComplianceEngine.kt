package nz.myinspection.core.compliance

import java.time.Duration
import java.time.Instant
import java.util.Collections

data class ExistingScheduledEntry(
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

        if (request.inspectionType in SUPPORTED_INSPECTION_TYPES &&
            request.inspectionType !in rule.frequencyLimit.exemptTypes
        ) {
            var frequencyBlocked = false
            request.existingEntries.forEach { existing ->
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
        return if (reasons.isEmpty()) {
            ScheduleValidation.Pass
        } else {
            ScheduleValidation.Blocked(Collections.unmodifiableList(reasons))
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

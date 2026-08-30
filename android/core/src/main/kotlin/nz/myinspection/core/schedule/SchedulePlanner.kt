package nz.myinspection.core.schedule

import java.time.Instant
import java.time.ZoneId

enum class InspectionScheduleType {
    ROUTINE,
    ANNUAL,
    INGOING,
    EXIT,
}

data class FinalizedInspection(
    val propertyId: String,
    val inspectionType: InspectionScheduleType,
    val finalizedAt: Instant,
)

sealed interface ScheduleAdvice {
    data class Due(
        val dueAt: Instant,
        val previousFinalizedAt: Instant,
    ) : ScheduleAdvice

    data object FirstInspection : ScheduleAdvice

    data object NoRecurrence : ScheduleAdvice
}

/**
 * Produces advisory reminder dates only. Creating or rescheduling an inspection remains subject to
 * ComplianceEngine with the real proposed visit and notice timestamps.
 */
class SchedulePlanner(
    private val zone: ZoneId = ZoneId.of("Pacific/Auckland"),
) {
    fun nextDue(
        propertyId: String,
        inspectionType: InspectionScheduleType,
        history: List<FinalizedInspection>,
    ): ScheduleAdvice {
        require(propertyId.isNotBlank()) { "propertyId must not be blank" }

        if (inspectionType == InspectionScheduleType.INGOING || inspectionType == InspectionScheduleType.EXIT) {
            return ScheduleAdvice.NoRecurrence
        }

        val previous = history
            .asSequence()
            .filter { it.propertyId == propertyId && it.inspectionType == inspectionType }
            .maxByOrNull(FinalizedInspection::finalizedAt)
            ?: return ScheduleAdvice.FirstInspection

        val localPrevious = previous.finalizedAt.atZone(zone)
        val localDue = when (inspectionType) {
            InspectionScheduleType.ROUTINE -> localPrevious.plusWeeks(13)
            InspectionScheduleType.ANNUAL -> localPrevious.plusMonths(12)
            InspectionScheduleType.INGOING,
            InspectionScheduleType.EXIT,
            -> error("Non-recurring inspection types return before cadence calculation")
        }
        return ScheduleAdvice.Due(
            dueAt = localDue.toInstant(),
            previousFinalizedAt = previous.finalizedAt,
        )
    }
}

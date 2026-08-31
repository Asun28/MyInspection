package nz.myinspection.app.feature.schedule

import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import nz.myinspection.core.schedule.InspectionScheduleType

data class ScheduleRoute(
    val propertyId: String,
    val inspectionType: InspectionScheduleType,
)

data class PendingReminder(
    val route: ScheduleRoute,
    val dueAt: Instant,
) {
    fun toSpec(): ReminderSpec = WorkSpecFactory().create(route, dueAt)
}

data class ReminderSpec(
    val uniqueWorkName: String,
    val occurrenceId: String,
    val route: ScheduleRoute,
    val dueAt: Instant,
)

class WorkSpecFactory {
    fun create(route: ScheduleRoute, dueAt: Instant): ReminderSpec {
        require(route.propertyId.isNotBlank()) { "propertyId must not be blank" }
        val occurrenceId = reminderOccurrenceId(route, dueAt)
        return ReminderSpec(
            uniqueWorkName = "schedule-reminder:$occurrenceId",
            occurrenceId = occurrenceId,
            route = route,
            dueAt = dueAt,
        )
    }
}

fun reminderOccurrenceId(route: ScheduleRoute, dueAt: Instant): String {
    require(route.propertyId.isNotBlank()) { "propertyId must not be blank" }
    val canonical = buildString {
        append(route.propertyId)
        append('\u0000')
        append(route.inspectionType.name)
        append('\u0000')
        append(dueAt.epochSecond)
        append('\u0000')
        append(dueAt.nano)
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(canonical.encodeToByteArray())
        .toLowerHex()
}

fun reminderGenerationId(occurrenceId: String, generationNumber: Long): UUID {
    require(occurrenceId.matches(OCCURRENCE_ID_PATTERN)) { "invalid occurrenceId" }
    require(generationNumber >= 0) { "generationNumber must be non-negative" }
    val canonical = "reminder-work/v1\n$occurrenceId\n$generationNumber"
    return UUID.nameUUIDFromBytes(canonical.encodeToByteArray())
}

data class RouteIntentSpec(
    val data: String,
    val notificationTag: String,
    val notificationId: Int,
    val requestCode: Int,
    val propertyId: String,
    val inspectionType: String,
    val isExplicit: Boolean,
    val isImmutable: Boolean,
)

data class NotificationIdentity(
    val tag: String,
    val id: Int,
)

data class NotificationCopy(
    val title: String,
    val body: String,
)

/**
 * Lock-screen visibility the delivery adapter must apply.
 *
 * A reminder names a property the tenant lives in, so its body never belongs on a locked screen.
 * Both values exist because a descriptor that cannot express the wrong answer cannot constrain
 * anything: with only PRIVATE declared, asserting PRIVATE would hold no matter what the adapter
 * did. PUBLIC is the value this contract exists to rule out, not an option offered to callers.
 */
enum class NotificationVisibility {
    PRIVATE,
    PUBLIC,
}

sealed interface DeliveryPlan {
    data object Retry : DeliveryPlan

    data class Notify(
        val copy: NotificationCopy,
        val intent: RouteIntentSpec,
        val onlyAlertOnce: Boolean = true,
        val visibility: NotificationVisibility = NotificationVisibility.PRIVATE,
    ) : DeliveryPlan
}

fun reminderRouteIntentSpec(route: ScheduleRoute, dueAt: Instant): RouteIntentSpec {
    val occurrenceId = reminderOccurrenceId(route, dueAt)
    return RouteIntentSpec(
        data = "myinspection://schedule/reminder/$occurrenceId",
        notificationTag = occurrenceId,
        notificationId = 0,
        requestCode = reminderRequestCode(occurrenceId),
        propertyId = route.propertyId,
        inspectionType = route.inspectionType.name,
        isExplicit = true,
        isImmutable = true,
    )
}

/**
 * Projects an occurrence id onto the 32 bits Android allows for a PendingIntent request code.
 *
 * The projection is lossy, so two occurrences sharing a 32-bit prefix collide here. That is safe
 * only because PendingIntent equality also compares the intent data, which carries the whole
 * occurrence id. Identity must therefore never be read from this value alone.
 */
internal fun reminderRequestCode(occurrenceId: String): Int {
    require(occurrenceId.matches(OCCURRENCE_ID_PATTERN)) { "invalid occurrenceId" }
    return occurrenceId.take(8).toLong(16).toInt()
}

fun reminderNotificationIdentity(intent: RouteIntentSpec): NotificationIdentity = NotificationIdentity(
    tag = intent.notificationTag,
    id = intent.notificationId,
)

fun scheduleNotificationCopy(type: InspectionScheduleType): NotificationCopy {
    val label = when (type) {
        InspectionScheduleType.ROUTINE -> "Routine inspection / 定期巡检"
        InspectionScheduleType.ANNUAL -> "Annual home check / 年度住宅检查"
        InspectionScheduleType.INGOING -> "Ingoing inspection / 入住巡检"
        InspectionScheduleType.EXIT -> "Exit inspection / 退租巡检"
    }
    return NotificationCopy(
        title = "Inspection reminder / 巡检提醒",
        body = "$label is due. Open MyInspection to review the property. / " +
            "已到建议日期，请打开 MyInspection 查看物业。",
    )
}

fun reminderDeliveryPlan(
    sdkInt: Int,
    permissionGranted: Boolean,
    route: ScheduleRoute,
    dueAt: Instant,
): DeliveryPlan = if (sdkInt >= 33 && !permissionGranted) {
    DeliveryPlan.Retry
} else {
    DeliveryPlan.Notify(
        copy = scheduleNotificationCopy(route.inspectionType),
        intent = reminderRouteIntentSpec(route, dueAt),
    )
}

internal object ReminderWorkKeys {
    const val OCCURRENCE_ID = "reminder_occurrence_id"
    const val PROPERTY_ID = "reminder_property_id"
    const val INSPECTION_TYPE = "reminder_inspection_type"
    const val DUE_AT = "reminder_due_at"
    const val GENERATION_NUMBER = "reminder_generation_number"
}

internal val OCCURRENCE_ID_PATTERN = Regex("[0-9a-f]{64}")

private fun ByteArray.toLowerHex(): String = joinToString(separator = "") { byte ->
    "%02x".format(byte.toInt() and 0xff)
}

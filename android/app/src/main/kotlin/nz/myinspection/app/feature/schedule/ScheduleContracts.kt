package nz.myinspection.app.feature.schedule

import java.io.Serializable
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import nz.myinspection.core.schedule.InspectionScheduleType
import nz.myinspection.core.schedule.ScheduleAdvice

data class ScheduleRoutePayload(val propertyId: String, val inspectionType: InspectionScheduleType) : Serializable

data class ReminderWorkSpec(val uniqueWorkName: String, val occurrenceId: String, val initialDelayMillis: Long, val route: ScheduleRoutePayload, val dueAt: Instant)

data class PendingReminder(val route: ScheduleRoutePayload, val dueAt: Instant) : Serializable {
    fun workSpec(clock: Clock = Clock.systemUTC()): ReminderWorkSpec = ReminderWorkSpecFactory(clock).create(route, dueAt)
}

fun PendingReminder?.resumeAfterGrant(state: NotificationPermissionState, clock: Clock = Clock.systemUTC()): ReminderWorkSpec? =
    if (state == NotificationPermissionState.GRANTED) this?.workSpec(clock) else null

class ReminderWorkSpecFactory(
    private val clock: Clock = Clock.systemUTC(),
) {
    fun create(route: ScheduleRoutePayload, dueAt: Instant): ReminderWorkSpec {
        require(route.propertyId.isNotBlank()) { "propertyId must not be blank" }
        val occurrenceId = reminderOccurrenceId(route, dueAt)
        return ReminderWorkSpec(
            uniqueWorkName = "schedule-reminder:$occurrenceId",
            occurrenceId = occurrenceId,
            initialDelayMillis = maxOf(0L, Duration.between(clock.instant(), dueAt).toMillis()),
            route = route,
            dueAt = dueAt,
        )
    }
}

fun reminderOccurrenceId(route: ScheduleRoutePayload, dueAt: Instant): String {
    val occurrence = "${route.propertyId}\u0000${route.inspectionType.name}\u0000${dueAt.toEpochMilli()}"
    return MessageDigest.getInstance("SHA-256").digest(occurrence.encodeToByteArray()).toHex()
}

enum class ReminderReceiptState { PENDING, CONFIRMED }

interface ReminderOccurrenceStore {
    fun read(occurrenceId: String): ReminderReceiptState?
    fun write(occurrenceId: String, state: ReminderReceiptState): Boolean
    fun remove(occurrenceId: String)
}

class ReminderRegistrationCoordinator(private val store: ReminderOccurrenceStore) {
    fun register(occurrenceId: String, enqueue: ((Boolean) -> Unit) -> Unit): Boolean {
        when (store.read(occurrenceId)) {
            ReminderReceiptState.CONFIRMED -> return false
            ReminderReceiptState.PENDING -> Unit
            null -> if (!store.write(occurrenceId, ReminderReceiptState.PENDING)) return false
        }
        return try {
            enqueue { succeeded -> complete(occurrenceId, succeeded) }
            true
        } catch (error: RuntimeException) {
            complete(occurrenceId, false)
            throw error
        }
    }

    private fun complete(occurrenceId: String, succeeded: Boolean) {
        if (succeeded) store.write(occurrenceId, ReminderReceiptState.CONFIRMED)
        else if (store.read(occurrenceId) == ReminderReceiptState.PENDING) store.remove(occurrenceId)
    }
}

data class ReminderRouteIntentSpec(val data: String, val requestCode: Int, val propertyId: String, val inspectionType: String)

fun reminderRouteIntentSpec(route: ScheduleRoutePayload, dueAt: Instant): ReminderRouteIntentSpec {
    val occurrenceId = reminderOccurrenceId(route, dueAt)
    return ReminderRouteIntentSpec(
        data = "myinspection://schedule/reminder/$occurrenceId",
        requestCode = occurrenceId.take(8).toLong(16).toInt(),
        propertyId = route.propertyId,
        inspectionType = route.inspectionType.name,
    )
}

enum class NotificationPermissionState { UNKNOWN, GRANTED, DENIED }

sealed interface ReminderPermissionAction {
    data object Schedule : ReminderPermissionAction

    data object RequestPermission : ReminderPermissionAction

    data class ShowRationale(val english: String, val chinese: String) : ReminderPermissionAction

    data class ExplainDenied(
        val english: String,
        val chinese: String,
    ) : ReminderPermissionAction
}

object ReminderPermissionPolicy {
    fun next(
        sdkInt: Int,
        state: NotificationPermissionState,
        rationaleRequired: Boolean = false,
    ): ReminderPermissionAction = when {
        sdkInt < 33 || state == NotificationPermissionState.GRANTED -> ReminderPermissionAction.Schedule
        state == NotificationPermissionState.UNKNOWN && rationaleRequired -> ReminderPermissionAction.ShowRationale(
            english = "Allow notifications to receive this local inspection reminder.",
            chinese = "允许通知以接收此本地巡检提醒。",
        )
        state == NotificationPermissionState.UNKNOWN -> ReminderPermissionAction.RequestPermission
        else -> ReminderPermissionAction.ExplainDenied(
            english = "Notifications are off. Allow them in Settings, then retry this reminder.",
            chinese = "通知已关闭。请在设置中允许通知，然后重试此提醒。",
        )
    }
}

data class ScheduleNotificationCopy(val title: String, val body: String)

fun scheduleNotificationCopy(type: InspectionScheduleType): ScheduleNotificationCopy {
    val label = when (type) {
        InspectionScheduleType.ROUTINE -> "Routine inspection / 定期巡检"
        InspectionScheduleType.ANNUAL -> "Annual home check / 年度住宅检查"
        InspectionScheduleType.INGOING -> "Ingoing inspection / 入住巡检"
        InspectionScheduleType.EXIT -> "Exit inspection / 退租巡检"
    }
    return ScheduleNotificationCopy(
        title = "Inspection reminder / 巡检提醒",
        body = "$label is due. Open MyInspection to review the property. / 已到建议日期，请打开 MyInspection 查看物业。",
    )
}

sealed interface ReminderDeliveryPlan {
    data object Retry : ReminderDeliveryPlan
    data class Notify(val copy: ScheduleNotificationCopy, val intent: ReminderRouteIntentSpec) : ReminderDeliveryPlan
}

fun reminderDeliveryPlan(sdkInt: Int, permissionGranted: Boolean, route: ScheduleRoutePayload, dueAt: Instant): ReminderDeliveryPlan =
    if (sdkInt >= 33 && !permissionGranted) ReminderDeliveryPlan.Retry
    else ReminderDeliveryPlan.Notify(scheduleNotificationCopy(route.inspectionType), reminderRouteIntentSpec(route, dueAt))

enum class ScheduleBadge { DUE, UPCOMING, FIRST, ONE_OFF, EMPTY }

enum class ScheduleFilter { ALL, DUE, ROUTINE, ANNUAL, INGOING, EXIT }

data class ScheduleEmptyState(val badge: ScheduleBadge, val message: String)

fun scheduleEmptyState(filter: ScheduleFilter): ScheduleEmptyState = ScheduleEmptyState(
    badge = ScheduleBadge.EMPTY,
    message = when (filter) {
        ScheduleFilter.ALL -> "No properties are available for scheduling."
        ScheduleFilter.DUE -> "No properties are due."
        else -> "No ${filter.label()} inspections match this filter."
    },
)

data class SchedulePropertyItem(val propertyId: String, val displayName: String, val inspectionType: InspectionScheduleType, val advice: ScheduleAdvice)

data class SchedulePropertyRow(val displayName: String, val route: ScheduleRoutePayload, val badge: ScheduleBadge, val nextFact: String, val dueAt: Instant?)

fun scheduleRows(
    items: List<SchedulePropertyItem>,
    now: Instant,
    filter: ScheduleFilter,
): List<SchedulePropertyRow> = items.map { item ->
    val route = ScheduleRoutePayload(item.propertyId, item.inspectionType)
    when (val advice = item.advice) {
        is ScheduleAdvice.Due -> {
            val isDue = !advice.dueAt.isAfter(now)
            SchedulePropertyRow(
                displayName = item.displayName,
                route = route,
                badge = if (isDue) ScheduleBadge.DUE else ScheduleBadge.UPCOMING,
                nextFact = if (isDue) "Reminder due" else "Next reminder: ${advice.dueAt}",
                dueAt = advice.dueAt,
            )
        }
        ScheduleAdvice.FirstInspection -> SchedulePropertyRow(
            displayName = item.displayName,
            route = route,
            badge = ScheduleBadge.FIRST,
            nextFact = "Plan the first ${item.inspectionType.name.lowercase()} inspection",
            dueAt = null,
        )
        ScheduleAdvice.NoRecurrence -> SchedulePropertyRow(
            displayName = item.displayName,
            route = route,
            badge = ScheduleBadge.ONE_OFF,
            nextFact = "One-off inspection",
            dueAt = null,
        )
    }
}.filter { row ->
    when (filter) {
        ScheduleFilter.ALL -> true
        ScheduleFilter.DUE -> row.badge == ScheduleBadge.DUE
        ScheduleFilter.ROUTINE -> row.route.inspectionType == InspectionScheduleType.ROUTINE
        ScheduleFilter.ANNUAL -> row.route.inspectionType == InspectionScheduleType.ANNUAL
        ScheduleFilter.INGOING -> row.route.inspectionType == InspectionScheduleType.INGOING
        ScheduleFilter.EXIT -> row.route.inspectionType == InspectionScheduleType.EXIT
    }
}

fun ScheduleFilter.label(): String = name.lowercase().replaceFirstChar(Char::titlecase)

fun ScheduleBadge.label(): String = when (this) {
    ScheduleBadge.DUE -> "Due"
    ScheduleBadge.UPCOMING -> "Upcoming"
    ScheduleBadge.FIRST -> "First inspection"
    ScheduleBadge.ONE_OFF -> "One-off"
    ScheduleBadge.EMPTY -> "No results"
}

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

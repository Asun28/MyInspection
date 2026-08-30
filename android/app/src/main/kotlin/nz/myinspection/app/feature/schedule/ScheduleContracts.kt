package nz.myinspection.app.feature.schedule
import java.io.Serializable
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import nz.myinspection.core.schedule.InspectionScheduleType
import nz.myinspection.core.schedule.ScheduleAdvice
data class ScheduleRoutePayload(val propertyId: String, val inspectionType: InspectionScheduleType) : Serializable
data class ReminderWorkSpec(val uniqueWorkName: String, val occurrenceId: String, val initialDelayMillis: Long, val route: ScheduleRoutePayload, val dueAt: Instant)
data class PendingReminder(val route: ScheduleRoutePayload, val dueAt: Instant) : Serializable {
    fun workSpec(clock: Clock = Clock.systemUTC()): ReminderWorkSpec = ReminderWorkSpecFactory(clock).create(route, dueAt)
}
fun PendingReminder?.resumeAfterGrant(state: NotificationPermissionState, clock: Clock = Clock.systemUTC()): ReminderWorkSpec? =
    if (state == NotificationPermissionState.GRANTED) this?.workSpec(clock) else null
class ReminderWorkSpecFactory(private val clock: Clock = Clock.systemUTC()) {
    fun create(route: ScheduleRoutePayload, dueAt: Instant): ReminderWorkSpec {
        require(route.propertyId.isNotBlank()) { "propertyId must not be blank" }
        val occurrenceId = reminderOccurrenceId(route, dueAt)
        return ReminderWorkSpec("schedule-reminder:$occurrenceId", occurrenceId, maxOf(0L, Duration.between(clock.instant(), dueAt).toMillis()), route, dueAt)
    }
}
fun reminderOccurrenceId(route: ScheduleRoutePayload, dueAt: Instant): String {
    val occurrence = "${route.propertyId}\u0000${route.inspectionType.name}\u0000${dueAt.toEpochMilli()}"
    return MessageDigest.getInstance("SHA-256").digest(occurrence.encodeToByteArray()).toHex()
}
enum class ReceiptState { ENQUEUED, DELIVERED }
enum class LogStage { ENQUEUE, INPUT, PERMISSION, RECEIPT_ENQUEUED, RECEIPT_DELIVERED, NOTIFY }
enum class LogError { ENQUEUE_FAILED, ENQUEUE_EXCEPTION, INVALID_INPUT, PERMISSION_DENIED, RECEIPT_WRITE_FAILED, NOTIFY_EXCEPTION }
interface ReminderOccurrenceStore {
    fun read(occurrenceId: String): ReceiptState?
    fun compareAndSet(occurrenceId: String, expected: Set<ReceiptState?>, state: ReceiptState?): Boolean
}
class ReminderRegistrationCoordinator(
    private val store: ReminderOccurrenceStore,
    private val onPersistenceFailure: (LogStage) -> Unit = {},
) {
    fun register(occurrenceId: String, enqueue: ((Boolean) -> Unit) -> Unit): Boolean {
        if (store.read(occurrenceId) in setOf(ReceiptState.ENQUEUED, ReceiptState.DELIVERED)) return false
        return try {
            enqueue { succeeded -> complete(occurrenceId, succeeded) }
            true
        } catch (error: RuntimeException) {
            throw error
        }
    }
    private fun complete(occurrenceId: String, succeeded: Boolean) {
        if (succeeded && !store.compareAndSet(occurrenceId, setOf(null), ReceiptState.ENQUEUED) && store.read(occurrenceId) !in listOf(ReceiptState.ENQUEUED, ReceiptState.DELIVERED)) onPersistenceFailure(LogStage.RECEIPT_ENQUEUED)
    }
}
data class ReminderEnqueueSpec(val uniqueName: String, val initialDelayMillis: Long, val route: ScheduleRoutePayload, val dueAt: Instant, val occurrenceId: String, val existingWorkPolicy: androidx.work.ExistingWorkPolicy)
data class ReminderRouteIntentSpec(val data: String, val notificationTag: String, val notificationId: Int, val requestCode: Int, val propertyId: String, val inspectionType: String)
data class ReminderNotificationIdentity(val tag: String, val id: Int)
fun reminderNotificationIdentity(intent: ReminderRouteIntentSpec): ReminderNotificationIdentity = ReminderNotificationIdentity(intent.notificationTag, intent.notificationId)
fun reminderRouteIntentSpec(route: ScheduleRoutePayload, dueAt: Instant): ReminderRouteIntentSpec {
    val occurrenceId = reminderOccurrenceId(route, dueAt)
    return ReminderRouteIntentSpec(
        data = "myinspection://schedule/reminder/$occurrenceId",
        notificationTag = occurrenceId,
        notificationId = 0,
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
    data class ExplainDenied(val english: String, val chinese: String) : ReminderPermissionAction
}
object ReminderPermissionPolicy {
    fun next(sdkInt: Int, state: NotificationPermissionState, rationaleRequired: Boolean = false): ReminderPermissionAction = when {
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
data class ScheduleRouteContentTransition(val pending: PendingReminder, val action: ReminderPermissionAction)
fun scheduleRouteContentTransition(row: SchedulePropertyRow, sdkInt: Int, state: NotificationPermissionState, rationaleRequired: Boolean): ScheduleRouteContentTransition =
    ScheduleRouteContentTransition(PendingReminder(row.route, requireNotNull(row.dueAt)), ReminderPermissionPolicy.next(sdkInt, state, rationaleRequired))
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
    zone: ZoneId = ZoneId.of("Pacific/Auckland"),
): List<SchedulePropertyRow> = items.map { item ->
    val route = ScheduleRoutePayload(item.propertyId, item.inspectionType)
    when (val advice = item.advice) {
        is ScheduleAdvice.Due -> {
            val isDue = !advice.dueAt.isAfter(now)
            SchedulePropertyRow(
                displayName = item.displayName,
                route = route,
                badge = if (isDue) ScheduleBadge.DUE else ScheduleBadge.UPCOMING,
                nextFact = if (isDue) "Reminder due" else "Next reminder: ${advice.dueAt.atZone(zone).toLocalDate()}",
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

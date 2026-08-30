package nz.myinspection.app.feature.schedule
import java.io.Serializable
import java.security.MessageDigest
import java.time.Instant
import nz.myinspection.core.schedule.InspectionScheduleType

data class ScheduleRoute(val propertyId: String, val inspectionType: InspectionScheduleType) : Serializable
data class PendingReminder(val route: ScheduleRoute, val dueAt: Instant) : Serializable {
    fun toSpec(): ReminderSpec = WorkSpecFactory().create(route, dueAt)
}
data class ReminderSpec(
    val uniqueWorkName: String, val occurrenceId: String, val route: ScheduleRoute, val dueAt: Instant,
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
    val occurrence = "${route.propertyId}\u0000${route.inspectionType.name}\u0000${dueAt.epochSecond}\u0000${dueAt.nano}"
    return MessageDigest.getInstance("SHA-256")
        .digest(occurrence.encodeToByteArray())
        .toHex()
}
enum class ReceiptState { MISSING, ENQUEUED, DELIVERED, RETRYABLE, TERMINAL, CORRUPT }
enum class RegistrationResult { SUCCESS, RETRYABLE_FAILURE, PERMANENT_FAILURE }
interface ReceiptStore {
    fun read(occurrenceId: String): ReceiptState
    fun compareAndSet(
        occurrenceId: String, expected: Set<ReceiptState>, state: ReceiptState,
    ): Boolean
}
internal fun ReceiptStore.readSafely(occurrenceId: String): ReceiptState = try {
    read(occurrenceId)
} catch (_: RuntimeException) {
    ReceiptState.CORRUPT
}
internal fun ReceiptStore.compareAndSetSafely(
    occurrenceId: String, expected: Set<ReceiptState>, state: ReceiptState,
): Boolean = try {
    compareAndSet(occurrenceId, expected, state)
} catch (_: RuntimeException) {
    false
}
internal class RegistrationCoordinator(
    private val store: ReceiptStore, private val onCorruptReceipt: () -> Unit,
    private val onWriteFailure: () -> Unit,
) {
    fun register(
        occurrenceId: String, onResult: (RegistrationResult) -> Unit,
        enqueue: ((RegistrationResult) -> Unit) -> Unit,
    ): Boolean {
        var initialState = store.readSafely(occurrenceId)
        if (initialState == ReceiptState.RETRYABLE) {
            if (!store.compareAndSetSafely(
                    occurrenceId,
                    setOf(ReceiptState.RETRYABLE),
                    ReceiptState.MISSING,
                )
            ) {
                val current = store.readSafely(occurrenceId)
                if (current == ReceiptState.CORRUPT) onCorruptReceipt()
                if (current == ReceiptState.RETRYABLE) onWriteFailure()
                onResult(
                    when {
                        current in setOf(ReceiptState.ENQUEUED, ReceiptState.DELIVERED) -> RegistrationResult.SUCCESS
                        current in setOf(ReceiptState.TERMINAL, ReceiptState.CORRUPT) -> RegistrationResult.PERMANENT_FAILURE
                        else -> RegistrationResult.RETRYABLE_FAILURE
                    },
                )
                return false
            }
            initialState = ReceiptState.MISSING
        }
        if (initialState in setOf(ReceiptState.ENQUEUED, ReceiptState.DELIVERED)) {
            onResult(RegistrationResult.SUCCESS)
            return false
        }
        if (initialState in setOf(ReceiptState.TERMINAL, ReceiptState.CORRUPT)) {
            if (initialState == ReceiptState.CORRUPT) onCorruptReceipt()
            onResult(RegistrationResult.PERMANENT_FAILURE)
            return false
        }
        enqueue { result ->
            if (result == RegistrationResult.RETRYABLE_FAILURE) {
                onResult(result)
                return@enqueue
            }
            val target = if (result == RegistrationResult.SUCCESS) {
                ReceiptState.ENQUEUED
            } else {
                ReceiptState.TERMINAL
            }
            store.compareAndSetSafely(occurrenceId, setOf(initialState), target)
            val current = store.readSafely(occurrenceId)
            if (current == ReceiptState.CORRUPT) onCorruptReceipt()
            if (current == ReceiptState.MISSING) onWriteFailure()
            onResult(
                when {
                    current in setOf(ReceiptState.ENQUEUED, ReceiptState.DELIVERED) -> RegistrationResult.SUCCESS
                    current in setOf(ReceiptState.TERMINAL, ReceiptState.CORRUPT) -> RegistrationResult.PERMANENT_FAILURE
                    else -> RegistrationResult.RETRYABLE_FAILURE
                },
            )
        }
        return true
    }
}
data class RouteIntentSpec(
    val data: String, val notificationTag: String, val notificationId: Int,
    val requestCode: Int, val propertyId: String, val inspectionType: String,
)
data class NotificationIdentity(val tag: String, val id: Int)
fun reminderRouteIntentSpec(route: ScheduleRoute, dueAt: Instant): RouteIntentSpec {
    val occurrenceId = reminderOccurrenceId(route, dueAt)
    return RouteIntentSpec(
        data = "myinspection://schedule/reminder/$occurrenceId",
        notificationTag = occurrenceId,
        notificationId = 0,
        requestCode = occurrenceId.take(8).toLong(16).toInt(),
        propertyId = route.propertyId,
        inspectionType = route.inspectionType.name,
    )
}
fun reminderNotificationIdentity(intent: RouteIntentSpec) = NotificationIdentity(intent.notificationTag, intent.notificationId)
data class NotificationCopy(val title: String, val body: String)
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

sealed interface DeliveryPlan {
    data object Retry : DeliveryPlan
    data class Notify(val copy: NotificationCopy, val intent: RouteIntentSpec) : DeliveryPlan
}
fun reminderDeliveryPlan(
    sdkInt: Int,
    permissionGranted: Boolean,
    route: ScheduleRoute,
    dueAt: Instant,
): DeliveryPlan = if (sdkInt >= 33 && !permissionGranted) {
    DeliveryPlan.Retry
} else {
    DeliveryPlan.Notify(scheduleNotificationCopy(route.inspectionType), reminderRouteIntentSpec(route, dueAt))
}
private fun ByteArray.toHex(): String = joinToString("") { byte ->
    "%02x".format(byte.toInt() and 0xff)
}

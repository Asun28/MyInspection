package nz.myinspection.app.feature.schedule
import java.io.Serializable
import java.security.MessageDigest
import java.time.Instant
import nz.myinspection.core.schedule.InspectionScheduleType

data class ScheduleRoute(val propertyId: String, val inspectionType: InspectionScheduleType) : Serializable
data class PendingReminder(val route: ScheduleRoute, val dueAt: Instant) : Serializable { fun toSpec() = WorkSpecFactory().create(route, dueAt) }
data class ReminderSpec(val uniqueWorkName: String, val occurrenceId: String, val route: ScheduleRoute, val dueAt: Instant)
class WorkSpecFactory {
    fun create(route: ScheduleRoute, dueAt: Instant): ReminderSpec {
        require(route.propertyId.isNotBlank()) { "propertyId must not be blank" }
        val occurrenceId = reminderOccurrenceId(route, dueAt)
        return ReminderSpec("schedule-reminder:$occurrenceId", occurrenceId, route, dueAt)
    }
}
fun reminderOccurrenceId(route: ScheduleRoute, dueAt: Instant): String {
    val occurrence = "${route.propertyId}\u0000${route.inspectionType.name}\u0000${dueAt.epochSecond}\u0000${dueAt.nano}"
    return MessageDigest.getInstance("SHA-256").digest(occurrence.encodeToByteArray()).toHex()
}
enum class ReceiptState { MISSING, ENQUEUED, PERMISSION_RETRY, INDETERMINATE, DELIVERED, RETRYABLE, TERMINAL, CORRUPT }
enum class RegistrationResult { SUCCESS, RETRYABLE_FAILURE, PERMANENT_FAILURE }
interface ReceiptStore {
    fun read(occurrenceId: String): ReceiptState
    fun compareAndSet(occurrenceId: String, expected: Set<ReceiptState>, state: ReceiptState): Boolean
}
internal fun ReceiptStore.readSafely(occurrenceId: String) = try { read(occurrenceId) } catch (_: RuntimeException) { ReceiptState.CORRUPT }
internal fun ReceiptStore.compareAndSetSafely(
    occurrenceId: String, expected: Set<ReceiptState>, state: ReceiptState,
): Boolean = try { compareAndSet(occurrenceId, expected, state) } catch (_: RuntimeException) { false }
internal object RegistrationFlights {
    class Flight(val waiters: MutableList<(RegistrationResult) -> Unit>, var settling: Boolean = false)
    private val active = mutableMapOf<String, Flight>()
    private val quarantined = mutableSetOf<String>()
    @Synchronized fun join(id: String, waiter: (RegistrationResult) -> Unit): Pair<Flight?, Boolean> {
        if (id in quarantined) return null to true
        active[id]?.let { it.waiters += waiter; return null to false }
        return Flight(mutableListOf(waiter)).also { active[id] = it } to false
    }
    @Synchronized fun begin(id: String, flight: Flight): Boolean {
        if (active[id] !== flight || flight.settling) return false
        return true.also { flight.settling = it }
    }
    @Synchronized fun finish(id: String, flight: Flight, quarantine: Boolean) =
        if (active[id] !== flight) emptyList() else flight.waiters.toList().also {
            active.remove(id); if (quarantine) quarantined += id
        }
}
internal class RegistrationCoordinator(
    private val store: ReceiptStore, private val onCorruptReceipt: () -> Unit,
    private val onWriteFailure: () -> Unit,
) {
    fun register(
        occurrenceId: String, onResult: (RegistrationResult) -> Unit,
        enqueue: ((RegistrationResult) -> Unit) -> Unit,
    ): Boolean {
        val initial = store.readSafely(occurrenceId)
        if (initial in setOf(ReceiptState.PERMISSION_RETRY, ReceiptState.DELIVERED)) return finish(initial, onResult)
        if (initial in setOf(ReceiptState.INDETERMINATE, ReceiptState.TERMINAL, ReceiptState.CORRUPT)) {
            if (initial == ReceiptState.CORRUPT) onCorruptReceipt() else if (initial == ReceiptState.INDETERMINATE) onWriteFailure()
            return finish(initial, onResult)
        }
        val (flight, quarantined) = RegistrationFlights.join(occurrenceId, onResult)
        if (quarantined) return finish(ReceiptState.INDETERMINATE, onResult)
        if (flight == null) return false
        val current = store.readSafely(occurrenceId)
        if (current !in setOf(ReceiptState.MISSING, ReceiptState.ENQUEUED, ReceiptState.RETRYABLE)) {
            return finishFlight(occurrenceId, flight, current)
        }
        if (current != ReceiptState.ENQUEUED) {
            val reserved = store.compareAndSetSafely(
                occurrenceId, setOf(current), ReceiptState.ENQUEUED,
            )
            if (!reserved) {
                val observed = store.readSafely(occurrenceId)
                if (observed != ReceiptState.ENQUEUED) {
                    if (observed in setOf(ReceiptState.MISSING, ReceiptState.RETRYABLE)) onWriteFailure()
                    return finishFlight(occurrenceId, flight, observed)
                }
            }
        }
        try {
            enqueue { result -> complete(occurrenceId, flight, result) }
        } catch (error: Exception) {
            RegistrationFlights.finish(occurrenceId, flight, true)
                .forEach { it(RegistrationResult.PERMANENT_FAILURE) }
            throw error
        }
        return true
    }
    private fun complete(
        occurrenceId: String, flight: RegistrationFlights.Flight, result: RegistrationResult,
    ) {
        if (!RegistrationFlights.begin(occurrenceId, flight)) return
        var settled = false
        try {
            var resolved = result
            var quarantine = false
            if (result == RegistrationResult.SUCCESS) {
                resolved = resultFor(store.readSafely(occurrenceId))
            } else {
                val target = if (result == RegistrationResult.RETRYABLE_FAILURE) ReceiptState.RETRYABLE else ReceiptState.TERMINAL
                if (!store.compareAndSetSafely(occurrenceId, setOf(ReceiptState.ENQUEUED), target)) {
                    var current = store.readSafely(occurrenceId)
                    if (current == ReceiptState.ENQUEUED) {
                        onWriteFailure()
                        store.compareAndSetSafely(
                            occurrenceId, setOf(ReceiptState.ENQUEUED), ReceiptState.INDETERMINATE,
                        )
                        current = store.readSafely(occurrenceId)
                        quarantine = current == ReceiptState.ENQUEUED
                    }
                    resolved = resultFor(current)
                }
            }
            if (resolved == RegistrationResult.PERMANENT_FAILURE && store.readSafely(occurrenceId) == ReceiptState.CORRUPT) onCorruptReceipt()
            val waiters = RegistrationFlights.finish(occurrenceId, flight, quarantine)
            settled = true
            waiters.forEach { it(if (quarantine) RegistrationResult.PERMANENT_FAILURE else resolved) }
        } finally {
            if (!settled) RegistrationFlights.finish(occurrenceId, flight, true)
                .forEach { it(RegistrationResult.PERMANENT_FAILURE) }
        }
    }
    private fun finish(state: ReceiptState, onResult: (RegistrationResult) -> Unit): Boolean {
        onResult(resultFor(state))
        return false
    }
    private fun finishFlight(occurrenceId: String, flight: RegistrationFlights.Flight, state: ReceiptState): Boolean {
        if (state == ReceiptState.CORRUPT) onCorruptReceipt()
        if (state == ReceiptState.INDETERMINATE) onWriteFailure()
        RegistrationFlights.finish(occurrenceId, flight, false).forEach { it(resultFor(state)) }
        return false
    }
    private fun resultFor(state: ReceiptState) = when (state) {
            ReceiptState.ENQUEUED, ReceiptState.PERMISSION_RETRY, ReceiptState.DELIVERED -> RegistrationResult.SUCCESS
            ReceiptState.MISSING, ReceiptState.RETRYABLE -> RegistrationResult.RETRYABLE_FAILURE
            ReceiptState.INDETERMINATE, ReceiptState.TERMINAL, ReceiptState.CORRUPT -> RegistrationResult.PERMANENT_FAILURE
    }
}
data class RouteIntentSpec(val data: String, val notificationTag: String, val notificationId: Int,
    val requestCode: Int, val propertyId: String, val inspectionType: String)
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
    return NotificationCopy("Inspection reminder / 巡检提醒",
        "$label is due. Open MyInspection to review the property. / 已到建议日期，请打开 MyInspection 查看物业。")
}
sealed interface DeliveryPlan {
    data object Retry : DeliveryPlan
    data class Notify(val copy: NotificationCopy, val intent: RouteIntentSpec, val onlyAlertOnce: Boolean = true) : DeliveryPlan
}
fun reminderDeliveryPlan(sdkInt: Int, permissionGranted: Boolean, route: ScheduleRoute, dueAt: Instant): DeliveryPlan =
    if (sdkInt >= 33 && !permissionGranted) DeliveryPlan.Retry
    else DeliveryPlan.Notify(scheduleNotificationCopy(route.inspectionType), reminderRouteIntentSpec(route, dueAt))
private fun ByteArray.toHex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }

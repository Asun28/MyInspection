package nz.myinspection.app.feature.schedule
import java.io.Serializable
import java.security.MessageDigest
import java.time.Instant
import nz.myinspection.core.schedule.InspectionScheduleType
private typealias RS = ReceiptState
private typealias RR = RegistrationResult
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
enum class ReceiptState { MISSING, ENQUEUED, PERMISSION_RETRY, DELIVERY_RETRY, DELIVERY_UNCERTAIN, INDETERMINATE, DELIVERED, RETRYABLE, TERMINAL, CORRUPT }
enum class RegistrationResult { SUCCESS, RETRYABLE_FAILURE, PERMANENT_FAILURE }
sealed interface WriteResult { data object Applied : WriteResult; data class Mismatch(val state: RS) : WriteResult; data object Failed : WriteResult }
interface ReceiptStore { fun read(id: String): RS
    fun compareAndSet(id: String, expected: Set<RS>, state: RS): WriteResult }
internal fun ReceiptStore.readSafely(occurrenceId: String) = try { read(occurrenceId) } catch (_: RuntimeException) { RS.CORRUPT }
internal fun ReceiptStore.compareAndSetSafely(occurrenceId: String, expected: Set<RS>, state: RS) =
    try { compareAndSet(occurrenceId, expected, state) } catch (_: RuntimeException) { WriteResult.Failed }
internal object RegistrationFlights {
    class Flight(val waiters: MutableList<(RR) -> Unit>, var settling: Boolean = false)
    private val active = mutableMapOf<String, Flight>()
    private val quarantined = mutableSetOf<String>()
    @Synchronized fun join(id: String, waiter: (RR) -> Unit): Pair<Flight?, Boolean> {
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
internal class RegistrationCoordinator(private val store: ReceiptStore, private val onState: (RS) -> Unit) {
    fun register(
        occurrenceId: String, onResult: (RR) -> Unit,
        enqueue: ((RR) -> Unit) -> Unit,
    ): Boolean {
        val initial = store.readSafely(occurrenceId)
        if (initial in setOf(RS.PERMISSION_RETRY, RS.DELIVERY_RETRY, RS.DELIVERED)) return finish(initial, onResult)
        if (initial in setOf(RS.DELIVERY_UNCERTAIN, RS.INDETERMINATE, RS.TERMINAL, RS.CORRUPT))
            return finish(initial, onResult)
        val (flight, quarantined) = RegistrationFlights.join(occurrenceId, onResult)
        if (quarantined) return finish(RS.INDETERMINATE, onResult)
        if (flight == null) return false
        val current = store.readSafely(occurrenceId)
        if (current !in setOf(RS.MISSING, RS.ENQUEUED, RS.RETRYABLE)) {
            return finishFlight(occurrenceId, flight, current)
        }
        if (current != RS.ENQUEUED) {
            when (val write = store.compareAndSetSafely(occurrenceId, setOf(current), RS.ENQUEUED)) {
                WriteResult.Applied -> Unit
                WriteResult.Failed -> return finishFlight(occurrenceId, flight, RS.INDETERMINATE, true)
                is WriteResult.Mismatch -> if (write.state != RS.ENQUEUED)
                    return finishFlight(occurrenceId, flight, write.state)
            }
        }
        try {
            enqueue { result -> complete(occurrenceId, flight, result) }
        } catch (error: Exception) {
            RegistrationFlights.finish(occurrenceId, flight, true)
                .forEach { it(RR.PERMANENT_FAILURE) }
            throw error
        }
        return true
    }
    private fun complete(
        occurrenceId: String, flight: RegistrationFlights.Flight, result: RR,
    ) {
        if (!RegistrationFlights.begin(occurrenceId, flight)) return
        var settled = false
        try {
            var resolved = result
            var quarantine = false
            if (result == RR.SUCCESS) {
                val state = store.readSafely(occurrenceId); onState(state); resolved = resultFor(state)
            } else {
                val target = if (result == RR.RETRYABLE_FAILURE) RS.RETRYABLE else RS.TERMINAL
                when (val write = store.compareAndSetSafely(occurrenceId, setOf(RS.ENQUEUED), target)) {
                    WriteResult.Applied -> Unit
                    is WriteResult.Mismatch -> { onState(write.state); resolved = resultFor(write.state) }
                    WriteResult.Failed -> {
                        onState(RS.INDETERMINATE); quarantine = true; resolved = RR.PERMANENT_FAILURE
                    }
                }
            }
            val waiters = RegistrationFlights.finish(occurrenceId, flight, quarantine)
            settled = true
            waiters.forEach { it(if (quarantine) RR.PERMANENT_FAILURE else resolved) }
        } finally {
            if (!settled) RegistrationFlights.finish(occurrenceId, flight, true)
                .forEach { it(RR.PERMANENT_FAILURE) }
        }
    }
    private fun finish(state: RS, onResult: (RR) -> Unit): Boolean {
        onState(state)
        onResult(resultFor(state))
        return false
    }
    private fun finishFlight(occurrenceId: String, flight: RegistrationFlights.Flight, state: RS, quarantine: Boolean = false): Boolean {
        onState(state)
        RegistrationFlights.finish(occurrenceId, flight, quarantine).forEach { it(resultFor(state)) }
        return false
    }
    private fun resultFor(state: RS) = when (state) {
            RS.ENQUEUED, RS.PERMISSION_RETRY, RS.DELIVERY_RETRY, RS.DELIVERED -> RR.SUCCESS
            RS.MISSING, RS.RETRYABLE -> RR.RETRYABLE_FAILURE
            RS.DELIVERY_UNCERTAIN, RS.INDETERMINATE, RS.TERMINAL, RS.CORRUPT -> RR.PERMANENT_FAILURE
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

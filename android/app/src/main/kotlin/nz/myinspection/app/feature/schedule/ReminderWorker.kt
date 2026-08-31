package nz.myinspection.app.feature.schedule
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.Instant
import nz.myinspection.app.MainActivity
private typealias WType = nz.myinspection.core.schedule.InspectionScheduleType
private typealias WRS = ReceiptState
private typealias WFC = FailureCauseCode; private typealias WO = WorkerOutcome
internal data class WorkerInput(
    val propertyId: String?, val type: String?,
    val dueAt: Instant?, val occurrenceId: String?,
) {
    companion object { fun from(spec: ReminderSpec) = WorkerInput(
        spec.route.propertyId, spec.route.inspectionType.name, spec.dueAt, spec.occurrenceId) }
}
internal enum class WorkerOutcome { SUCCESS, RETRY, FAILURE }
internal class PrePostNotificationException(cause: Exception) : Exception(cause)
internal fun <T> postReminderNotification(identity: NotificationIdentity, notification: T,
    post: (String, Int, T) -> Unit) = post(identity.tag, identity.id, notification)
class ReminderWorker(appContext: Context, parameters: WorkerParameters) : Worker(appContext, parameters) {
    override fun doWork(): Result {
        val permissionGranted = Build.VERSION.SDK_INT < 33 ||
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val outcome = execute(
            WorkerInput(
                inputData.getString(WorkKeys.PROPERTY_ID),
                inputData.getString(WorkKeys.INSPECTION_TYPE),
                inputData.getString(WorkKeys.DUE_AT_INSTANT)
                    ?.let { runCatching { Instant.parse(it) }.getOrNull() },
                inputData.getString(WorkKeys.OCCURRENCE_ID),
            ),
            Build.VERSION.SDK_INT,
            permissionGranted,
            runAttemptCount,
            SharedPreferencesReceiptStore(applicationContext),
            AndroidReminderLogger,
            ::postNotification,
        )
        return when (outcome) {
            WO.SUCCESS -> Result.success()
            WO.RETRY -> Result.retry()
            WO.FAILURE -> Result.failure()
        }
    }
    private fun postNotification(delivery: DeliveryPlan.Notify) {
        val prepared = try {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Inspection reminders / 巡检提醒", NotificationManager.IMPORTANCE_DEFAULT),
            )
            manager to Notification.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(delivery.copy.title).setContentText(delivery.copy.body)
                .setStyle(Notification.BigTextStyle().bigText(delivery.copy.body))
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setAutoCancel(true).setOnlyAlertOnce(delivery.onlyAlertOnce)
                .setContentIntent(routePendingIntent(delivery.intent)).build()
        } catch (error: Exception) {
            throw PrePostNotificationException(error)
        }
        postReminderNotification(reminderNotificationIdentity(delivery.intent), prepared.second, prepared.first::notify)
    }
    private fun routePendingIntent(spec: RouteIntentSpec): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            action = ACTION_OPEN_SCHEDULE
            data = Uri.parse(spec.data)
            putExtra(EXTRA_PROPERTY_ID, spec.propertyId)
            putExtra(EXTRA_INSPECTION_TYPE, spec.inspectionType)
        }
        return PendingIntent.getActivity(
            applicationContext,
            spec.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
    companion object {
        const val ACTION_OPEN_SCHEDULE = "nz.myinspection.app.action.OPEN_SCHEDULE"
        const val EXTRA_PROPERTY_ID = "nz.myinspection.app.extra.PROPERTY_ID"
        const val EXTRA_INSPECTION_TYPE = "nz.myinspection.app.extra.INSPECTION_TYPE"
        private const val CHANNEL_ID = "inspection-reminders"
        private const val MAX_RETRIES = 3
        internal fun execute(
            input: WorkerInput, sdkInt: Int, permissionGranted: Boolean,
            runAttemptCount: Int, store: ReceiptStore, logger: EventLogger,
            notify: (DeliveryPlan.Notify) -> Unit,
        ): WO {
            val valid = validate(input) ?: return invalidInput(input, logger)
            val (route, dueAt, occurrenceId) = valid
            val receipt = store.readSafely(occurrenceId)
            if (receipt !in setOf(WRS.ENQUEUED, WRS.PERMISSION_RETRY, WRS.DELIVERY_RETRY))
                return stoppedBy(receipt, occurrenceId, route.inspectionType, logger)
            val delivery = reminderDeliveryPlan(sdkInt, permissionGranted, route, dueAt)
            if (delivery is DeliveryPlan.Retry) {
                return permissionFailure(occurrenceId, route.inspectionType, runAttemptCount, store, logger)
            }
            when (val write = store.compareAndSetSafely(
                occurrenceId, setOf(WRS.ENQUEUED, WRS.PERMISSION_RETRY, WRS.DELIVERY_RETRY), WRS.DELIVERY_UNCERTAIN,
            )) {
                WriteResult.Applied -> Unit
                WriteResult.Failed -> return receiptWriteFailure(occurrenceId, route.inspectionType, logger)
                is WriteResult.Mismatch -> return stoppedBy(write.state, occurrenceId, route.inspectionType, logger)
            }
            try {
                notify(delivery as DeliveryPlan.Notify)
            } catch (error: Exception) {
                return notifyFailure(occurrenceId, route.inspectionType, sdkInt, error, runAttemptCount, store, logger)
            }
            return transitionOutcome(
                occurrenceId, route.inspectionType, WRS.DELIVERED, WO.SUCCESS,
                store.compareAndSetSafely(
                occurrenceId, setOf(WRS.DELIVERY_UNCERTAIN), WRS.DELIVERED,
                ), logger,
            )
        }
        private fun validate(input: WorkerInput): ValidatedInput? {
            val propertyId = input.propertyId?.takeIf(String::isNotBlank) ?: return null
            val type = input.type
                ?.let { runCatching { WType.valueOf(it) }.getOrNull() }
                ?: return null
            val dueAt = input.dueAt ?: return null
            val occurrenceId = input.occurrenceId ?: return null
            val route = ScheduleRoute(propertyId, type)
            return if (occurrenceId == reminderOccurrenceId(route, dueAt)) {
                ValidatedInput(route, dueAt, occurrenceId)
            } else null
        }
        private fun invalidInput(input: WorkerInput, logger: EventLogger): WO {
            logger.record(LogStage.INPUT, input.occurrenceId, null, false,
                LogError.INVALID_INPUT, WFC.INVALID_INPUT)
            return WO.FAILURE
        }
        private fun permissionFailure(
            occurrenceId: String, type: WType, attempt: Int,
            store: ReceiptStore, logger: EventLogger,
            expected: Set<WRS> = setOf(WRS.ENQUEUED, WRS.PERMISSION_RETRY, WRS.DELIVERY_RETRY),
        ): WO {
            val retryable = attempt < MAX_RETRIES
            logger.record(LogStage.PERMISSION, occurrenceId, type, retryable,
                LogError.PERMISSION_DENIED, WFC.PERMISSION_DENIED)
            val target = if (retryable) WRS.PERMISSION_RETRY else WRS.TERMINAL
            return transitionOutcome(
                occurrenceId, type, target, if (retryable) WO.RETRY else WO.FAILURE,
                store.compareAndSetSafely(occurrenceId, expected, target), logger,
            )
        }
        private fun notifyFailure(
            occurrenceId: String, type: WType, sdkInt: Int, error: Exception, attempt: Int,
            store: ReceiptStore, logger: EventLogger,
        ): WO {
            if (sdkInt >= 33 && error is SecurityException) return permissionFailure(occurrenceId, type, attempt, store, logger,
                setOf(WRS.DELIVERY_UNCERTAIN))
            val beforePost = error as? PrePostNotificationException
            val disposition = classifyReminderFailure(beforePost?.cause ?: error)
            val retryable = beforePost != null && disposition.kind == FailureKind.TRANSIENT && attempt < MAX_RETRIES
            logger.record(LogStage.NOTIFY, occurrenceId, type, retryable, LogError.NOTIFY_FAILED, disposition.causeCode)
            if (beforePost != null && disposition.kind == FailureKind.TRANSIENT) {
                val target = if (retryable) WRS.DELIVERY_RETRY else WRS.TERMINAL
                return transitionOutcome(occurrenceId, type, target,
                    if (retryable) WO.RETRY else WO.FAILURE,
                    store.compareAndSetSafely(occurrenceId, setOf(WRS.DELIVERY_UNCERTAIN), target), logger)
            }
            if (disposition.kind == FailureKind.TRANSIENT) return WO.FAILURE
            return transitionOutcome(
                occurrenceId, type, WRS.TERMINAL, WO.FAILURE,
                store.compareAndSetSafely(
                    occurrenceId, setOf(WRS.DELIVERY_UNCERTAIN), WRS.TERMINAL,
                ), logger,
            )
        }
        private fun transitionOutcome(occurrenceId: String, type: WType, target: WRS,
            targetOutcome: WO, write: WriteResult, logger: EventLogger): WO = when (write) {
            WriteResult.Applied -> targetOutcome
            WriteResult.Failed -> receiptWriteFailure(occurrenceId, type, logger)
            is WriteResult.Mismatch -> if (write.state == target) targetOutcome
                else stoppedBy(write.state, occurrenceId, type, logger)
        }
        private fun stoppedBy(state: WRS, id: String, type: WType, logger: EventLogger) = when (state) {
            WRS.DELIVERED -> WO.SUCCESS
            WRS.TERMINAL -> WO.FAILURE
            WRS.CORRUPT -> receiptFailure(id, type, logger, LogStage.RECEIPT_DELIVERED, LogError.RECEIPT_CORRUPT, WFC.STORAGE_CORRUPT)
            WRS.DELIVERY_UNCERTAIN -> receiptFailure(id, type, logger, LogStage.NOTIFY, LogError.DELIVERY_UNCERTAIN, WFC.DELIVERY_UNCERTAIN)
            WRS.RETRYABLE -> receiptFailure(id, type, logger, LogStage.RECEIPT_ENQUEUED, LogError.RETRYABLE_RECEIPT, WFC.RETRYABLE_STATE)
            WRS.MISSING -> receiptFailure(id, type, logger, LogStage.RECEIPT_ENQUEUED, LogError.RECEIPT_MISSING, WFC.STORAGE_MISSING)
            else -> receiptWriteFailure(id, type, logger)
        }
        private fun receiptWriteFailure(id: String, type: WType, logger: EventLogger) =
            receiptFailure(id, type, logger, LogStage.RECEIPT_DELIVERED, LogError.RECEIPT_WRITE_FAILED, WFC.STORAGE_WRITE)
        private fun receiptFailure(id: String, type: WType, logger: EventLogger,
            stage: LogStage, error: LogError, cause: WFC): WO {
            logger.record(stage, id, type, false, error, cause)
            return WO.FAILURE
        }
    }
}
private data class ValidatedInput(val route: ScheduleRoute, val dueAt: Instant, val occurrenceId: String)
private fun EventLogger.record(
    stage: LogStage, occurrenceId: String?, type: WType?,
    retryable: Boolean, error: LogError, cause: WFC,
) = log(LogRecord(stage, occurrenceId, type, retryable, error, cause))

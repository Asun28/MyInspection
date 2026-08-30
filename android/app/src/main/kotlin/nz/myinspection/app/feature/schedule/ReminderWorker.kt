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
import nz.myinspection.core.schedule.InspectionScheduleType

internal data class WorkerInput(
    val propertyId: String?, val type: String?,
    val dueAt: Instant?, val occurrenceId: String?,
) {
    companion object {
        fun from(spec: ReminderSpec) = WorkerInput(
            spec.route.propertyId,
            spec.route.inspectionType.name,
            spec.dueAt,
            spec.occurrenceId,
        )
    }
}
internal enum class WorkerOutcome { SUCCESS, RETRY, FAILURE }
internal fun <T> postReminderNotification(
    identity: NotificationIdentity,
    notification: T,
    post: (String, Int, T) -> Unit,
) = post(identity.tag, identity.id, notification)
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
            WorkerOutcome.SUCCESS -> Result.success()
            WorkerOutcome.RETRY -> Result.retry()
            WorkerOutcome.FAILURE -> Result.failure()
        }
    }
    private fun postNotification(delivery: DeliveryPlan.Notify) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Inspection reminders / 巡检提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(delivery.copy.title)
            .setContentText(delivery.copy.body)
            .setStyle(Notification.BigTextStyle().bigText(delivery.copy.body))
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true).setOnlyAlertOnce(delivery.onlyAlertOnce)
            .setContentIntent(routePendingIntent(delivery.intent))
            .build()
        postReminderNotification(reminderNotificationIdentity(delivery.intent), notification, manager::notify)
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
        private const val MAX_ATTEMPTS = 3
        internal fun execute(
            input: WorkerInput, sdkInt: Int, permissionGranted: Boolean,
            runAttemptCount: Int, store: ReceiptStore, logger: EventLogger,
            notify: (DeliveryPlan.Notify) -> Unit,
        ): WorkerOutcome {
            val valid = validate(input) ?: return invalidInput(input, logger)
            val (route, dueAt, occurrenceId) = valid
            when (store.readSafely(occurrenceId)) {
                ReceiptState.DELIVERED -> return WorkerOutcome.SUCCESS
                ReceiptState.TERMINAL -> return WorkerOutcome.FAILURE
                ReceiptState.CORRUPT -> return corruptReceipt(occurrenceId, route.inspectionType, logger)
                ReceiptState.MISSING, ReceiptState.ENQUEUED, ReceiptState.RETRYABLE -> Unit
            }
            val delivery = reminderDeliveryPlan(sdkInt, permissionGranted, route, dueAt)
            if (delivery is DeliveryPlan.Retry) {
                return failureOutcome(
                    LogStage.PERMISSION,
                    occurrenceId,
                    route.inspectionType,
                    transientPermission(),
                    LogError.PERMISSION_DENIED,
                    runAttemptCount,
                    store,
                    logger,
                )
            }
            try {
                notify(delivery as DeliveryPlan.Notify)
            } catch (error: Exception) {
                return failureOutcome(
                    LogStage.NOTIFY,
                    occurrenceId,
                    route.inspectionType,
                    classifyReminderFailure(error),
                    LogError.NOTIFY_FAILED,
                    runAttemptCount,
                    store,
                    logger,
                )
            }
            val durable = store.compareAndSetSafely(
                occurrenceId,
                setOf(ReceiptState.MISSING, ReceiptState.ENQUEUED, ReceiptState.RETRYABLE),
                ReceiptState.DELIVERED,
            ) || store.readSafely(occurrenceId) == ReceiptState.DELIVERED
            if (durable) return WorkerOutcome.SUCCESS
            if (store.readSafely(occurrenceId) == ReceiptState.CORRUPT) {
                return corruptReceipt(occurrenceId, route.inspectionType, logger)
            }
            return failureOutcome(
                LogStage.RECEIPT_DELIVERED,
                occurrenceId,
                route.inspectionType,
                FailureDisposition(FailureKind.TRANSIENT, FailureCauseCode.STORAGE_WRITE),
                LogError.RECEIPT_WRITE_FAILED,
                runAttemptCount,
                store,
                logger,
            )
        }
        private fun validate(input: WorkerInput): ValidatedInput? {
            val propertyId = input.propertyId?.takeIf(String::isNotBlank) ?: return null
            val type = input.type
                ?.let { runCatching { InspectionScheduleType.valueOf(it) }.getOrNull() }
                ?: return null
            val dueAt = input.dueAt ?: return null
            val occurrenceId = input.occurrenceId ?: return null
            val route = ScheduleRoute(propertyId, type)
            return if (occurrenceId == reminderOccurrenceId(route, dueAt)) {
                ValidatedInput(route, dueAt, occurrenceId)
            } else null
        }
        private fun invalidInput(input: WorkerInput, logger: EventLogger): WorkerOutcome {
            logger.record(
                LogStage.INPUT,
                input.occurrenceId,
                null,
                false,
                LogError.INVALID_INPUT,
                FailureCauseCode.INVALID_INPUT,
            )
            return WorkerOutcome.FAILURE
        }
        private fun corruptReceipt(
            occurrenceId: String,
            type: InspectionScheduleType,
            logger: EventLogger,
        ): WorkerOutcome {
            logger.record(
                LogStage.RECEIPT_DELIVERED,
                occurrenceId,
                type,
                false,
                LogError.RECEIPT_CORRUPT,
                FailureCauseCode.STORAGE_CORRUPT,
            )
            return WorkerOutcome.FAILURE
        }
        private fun failureOutcome(
            stage: LogStage, occurrenceId: String, type: InspectionScheduleType,
            disposition: FailureDisposition, errorCode: LogError, attempt: Int,
            store: ReceiptStore, logger: EventLogger,
        ): WorkerOutcome {
            val retryable = disposition.kind == FailureKind.TRANSIENT && attempt + 1 < MAX_ATTEMPTS
            logger.record(stage, occurrenceId, type, retryable, errorCode, disposition.causeCode)
            if (retryable) return WorkerOutcome.RETRY
            val target = if (disposition.kind == FailureKind.PERMANENT) ReceiptState.TERMINAL
            else ReceiptState.RETRYABLE
            val current = store.readSafely(occurrenceId)
            if (current != target) {
                store.compareAndSetSafely(
                    occurrenceId,
                    setOf(ReceiptState.MISSING, ReceiptState.ENQUEUED, ReceiptState.RETRYABLE),
                    target,
                )
            }
            val finalState = store.readSafely(occurrenceId)
            if (finalState == ReceiptState.CORRUPT) {
                return corruptReceipt(occurrenceId, type, logger)
            }
            if (finalState != target && finalState !in setOf(ReceiptState.DELIVERED, ReceiptState.TERMINAL)) {
                logger.record(LogStage.RECEIPT_ENQUEUED, occurrenceId, type, false,
                    LogError.RECEIPT_WRITE_FAILED, FailureCauseCode.STORAGE_WRITE)
            }
            return WorkerOutcome.FAILURE
        }
        private fun transientPermission() =
            FailureDisposition(FailureKind.TRANSIENT, FailureCauseCode.PERMISSION_DENIED)
    }
}
private data class ValidatedInput(val route: ScheduleRoute, val dueAt: Instant, val occurrenceId: String)
private fun EventLogger.record(
    stage: LogStage, occurrenceId: String?, type: InspectionScheduleType?,
    retryable: Boolean, error: LogError, cause: FailureCauseCode,
) = log(LogRecord(stage, occurrenceId, type, retryable, error, cause))

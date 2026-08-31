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
            WorkerOutcome.SUCCESS -> Result.success()
            WorkerOutcome.RETRY -> Result.retry()
            WorkerOutcome.FAILURE -> Result.failure()
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
        private const val MAX_ATTEMPTS = 3
        internal fun execute(
            input: WorkerInput, sdkInt: Int, permissionGranted: Boolean,
            runAttemptCount: Int, store: ReceiptStore, logger: EventLogger,
            notify: (DeliveryPlan.Notify) -> Unit,
        ): WorkerOutcome {
            val valid = validate(input) ?: return invalidInput(input, logger)
            val (route, dueAt, occurrenceId) = valid
            when (val receipt = store.readSafely(occurrenceId)) {
                ReceiptState.DELIVERED -> return WorkerOutcome.SUCCESS
                ReceiptState.TERMINAL -> return WorkerOutcome.FAILURE
                ReceiptState.CORRUPT -> return corruptReceipt(occurrenceId, route.inspectionType, logger)
                ReceiptState.INDETERMINATE -> return receiptWriteFailure(occurrenceId, route.inspectionType, logger)
                ReceiptState.MISSING, ReceiptState.RETRYABLE -> return missingReceipt(occurrenceId, route.inspectionType, logger)
                ReceiptState.ENQUEUED, ReceiptState.PERMISSION_RETRY -> Unit
            }
            val delivery = reminderDeliveryPlan(sdkInt, permissionGranted, route, dueAt)
            if (delivery is DeliveryPlan.Retry) {
                return permissionFailure(occurrenceId, route.inspectionType, runAttemptCount, store, logger)
            }
            when (val write = store.compareAndSetSafely(
                occurrenceId, setOf(ReceiptState.ENQUEUED, ReceiptState.PERMISSION_RETRY), ReceiptState.INDETERMINATE,
            )) {
                WriteResult.Applied -> Unit
                WriteResult.Failed -> return receiptWriteFailure(occurrenceId, route.inspectionType, logger)
                is WriteResult.Mismatch -> return when (write.state) {
                    ReceiptState.DELIVERED -> WorkerOutcome.SUCCESS
                    ReceiptState.CORRUPT -> corruptReceipt(occurrenceId, route.inspectionType, logger)
                    else -> receiptWriteFailure(occurrenceId, route.inspectionType, logger)
                }
            }
            try {
                notify(delivery as DeliveryPlan.Notify)
            } catch (error: Exception) {
                return notifyFailure(occurrenceId, route.inspectionType, error, runAttemptCount, store, logger)
            }
            return transitionOutcome(
                occurrenceId, route.inspectionType, ReceiptState.DELIVERED, WorkerOutcome.SUCCESS,
                store.compareAndSetSafely(
                occurrenceId, setOf(ReceiptState.INDETERMINATE), ReceiptState.DELIVERED,
                ), logger,
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
            logger.record(LogStage.INPUT, input.occurrenceId, null, false,
                LogError.INVALID_INPUT, FailureCauseCode.INVALID_INPUT)
            return WorkerOutcome.FAILURE
        }
        private fun corruptReceipt(
            occurrenceId: String,
            type: InspectionScheduleType,
            logger: EventLogger,
        ): WorkerOutcome {
            logger.record(LogStage.RECEIPT_DELIVERED, occurrenceId, type, false,
                LogError.RECEIPT_CORRUPT, FailureCauseCode.STORAGE_CORRUPT)
            return WorkerOutcome.FAILURE
        }
        private fun missingReceipt(
            occurrenceId: String, type: InspectionScheduleType, logger: EventLogger,
        ): WorkerOutcome {
            logger.record(LogStage.RECEIPT_ENQUEUED, occurrenceId, type, false,
                LogError.RECEIPT_MISSING, FailureCauseCode.STORAGE_MISSING)
            return WorkerOutcome.FAILURE
        }
        private fun permissionFailure(
            occurrenceId: String, type: InspectionScheduleType, attempt: Int,
            store: ReceiptStore, logger: EventLogger,
        ): WorkerOutcome {
            val retryable = attempt + 1 < MAX_ATTEMPTS
            logger.record(LogStage.PERMISSION, occurrenceId, type, retryable,
                LogError.PERMISSION_DENIED, FailureCauseCode.PERMISSION_DENIED)
            val target = if (retryable) ReceiptState.PERMISSION_RETRY else ReceiptState.RETRYABLE
            return transitionOutcome(
                occurrenceId, type, target, if (retryable) WorkerOutcome.RETRY else WorkerOutcome.FAILURE,
                store.compareAndSetSafely(
                    occurrenceId, setOf(ReceiptState.ENQUEUED, ReceiptState.PERMISSION_RETRY), target,
                ), logger,
            )
        }
        private fun notifyFailure(
            occurrenceId: String, type: InspectionScheduleType, error: Exception, attempt: Int,
            store: ReceiptStore, logger: EventLogger,
        ): WorkerOutcome {
            val beforePost = error as? PrePostNotificationException
            val disposition = classifyReminderFailure(beforePost?.cause ?: error)
            val retryable = beforePost != null && disposition.kind == FailureKind.TRANSIENT && attempt + 1 < MAX_ATTEMPTS
            logger.record(LogStage.NOTIFY, occurrenceId, type, retryable, LogError.NOTIFY_FAILED, disposition.causeCode)
            if (beforePost != null && disposition.kind == FailureKind.TRANSIENT) {
                val target = if (retryable) ReceiptState.ENQUEUED else ReceiptState.RETRYABLE
                return transitionOutcome(occurrenceId, type, target,
                    if (retryable) WorkerOutcome.RETRY else WorkerOutcome.FAILURE,
                    store.compareAndSetSafely(occurrenceId, setOf(ReceiptState.INDETERMINATE), target), logger)
            }
            if (disposition.kind == FailureKind.TRANSIENT) return WorkerOutcome.FAILURE
            return transitionOutcome(
                occurrenceId, type, ReceiptState.TERMINAL, WorkerOutcome.FAILURE,
                store.compareAndSetSafely(
                    occurrenceId, setOf(ReceiptState.INDETERMINATE), ReceiptState.TERMINAL,
                ), logger,
            )
        }
        private fun transitionOutcome(occurrenceId: String, type: InspectionScheduleType, target: ReceiptState,
            targetOutcome: WorkerOutcome, write: WriteResult, logger: EventLogger): WorkerOutcome = when (write) {
            WriteResult.Applied -> targetOutcome
            WriteResult.Failed -> receiptWriteFailure(occurrenceId, type, logger)
            is WriteResult.Mismatch -> when (val observed = write.state) {
                target -> targetOutcome
                ReceiptState.DELIVERED -> WorkerOutcome.SUCCESS
                ReceiptState.TERMINAL, ReceiptState.RETRYABLE -> WorkerOutcome.FAILURE
                ReceiptState.CORRUPT -> corruptReceipt(occurrenceId, type, logger)
                else -> receiptWriteFailure(occurrenceId, type, logger)
            }
        }
        private fun receiptWriteFailure(
            occurrenceId: String, type: InspectionScheduleType, logger: EventLogger,
        ): WorkerOutcome {
            logger.record(LogStage.RECEIPT_DELIVERED, occurrenceId, type, false,
                LogError.RECEIPT_WRITE_FAILED, FailureCauseCode.STORAGE_WRITE)
            return WorkerOutcome.FAILURE
        }
    }
}
private data class ValidatedInput(val route: ScheduleRoute, val dueAt: Instant, val occurrenceId: String)
private fun EventLogger.record(
    stage: LogStage, occurrenceId: String?, type: InspectionScheduleType?,
    retryable: Boolean, error: LogError, cause: FailureCauseCode,
) = log(LogRecord(stage, occurrenceId, type, retryable, error, cause))

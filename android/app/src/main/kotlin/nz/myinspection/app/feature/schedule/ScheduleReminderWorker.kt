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
internal data class ReminderWorkerInput(val propertyId: String?, val type: String?, val dueAtMillis: Long?, val occurrenceId: String?)
internal enum class WorkerOutcome { SUCCESS, RETRY, FAILURE }
internal fun <T> postReminderNotification(identity: ReminderNotificationIdentity, notification: T, post: (String, Int, T) -> Unit) = post(identity.tag, identity.id, notification)
class ScheduleReminderWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : Worker(appContext, parameters) {
    override fun doWork(): Result {
        val permissionGranted = Build.VERSION.SDK_INT < 33 ||
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val outcome = execute(
            ReminderWorkerInput(
                inputData.getString(ReminderWorkData.PROPERTY_ID),
                inputData.getString(ReminderWorkData.INSPECTION_TYPE),
                inputData.getLong(ReminderWorkData.DUE_AT_EPOCH_MILLIS, Long.MIN_VALUE).takeUnless { it == Long.MIN_VALUE },
                inputData.getString(ReminderWorkData.OCCURRENCE_ID),
            ),
            Build.VERSION.SDK_INT,
            permissionGranted,
            SharedPreferencesReminderOccurrenceStore(applicationContext),
            AndroidReminderLogger,
            ::postNotification,
        )
        return when (outcome) {
            WorkerOutcome.SUCCESS -> Result.success()
            WorkerOutcome.RETRY -> Result.retry()
            WorkerOutcome.FAILURE -> Result.failure()
        }
    }
    private fun postNotification(delivery: ReminderDeliveryPlan.Notify) {
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
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
            .setAutoCancel(true)
            .setContentIntent(routePendingIntent(delivery.intent))
            .build()
        postReminderNotification(reminderNotificationIdentity(delivery.intent), notification, notificationManager::notify)
    }
    private fun routePendingIntent(intentSpec: ReminderRouteIntentSpec): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            action = ACTION_OPEN_SCHEDULE
            putExtra(EXTRA_PROPERTY_ID, intentSpec.propertyId)
            putExtra(EXTRA_INSPECTION_TYPE, intentSpec.inspectionType)
        }
        intent.data = Uri.parse(intentSpec.data)
        return PendingIntent.getActivity(
            applicationContext,
            intentSpec.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
    companion object {
        const val ACTION_OPEN_SCHEDULE = "nz.myinspection.app.action.OPEN_SCHEDULE"
        const val EXTRA_PROPERTY_ID = "nz.myinspection.app.extra.PROPERTY_ID"
        const val EXTRA_INSPECTION_TYPE = "nz.myinspection.app.extra.INSPECTION_TYPE"
        private const val CHANNEL_ID = "inspection-reminders"
        internal fun execute(
            input: ReminderWorkerInput,
            sdkInt: Int,
            permissionGranted: Boolean,
            store: ReminderOccurrenceStore,
            logger: ReminderEventLogger,
            notify: (ReminderDeliveryPlan.Notify) -> Unit,
        ): WorkerOutcome {
            fun invalid(): WorkerOutcome {
                logger.log(LogStage.INPUT, input.occurrenceId, null, false, LogError.INVALID_INPUT)
                return WorkerOutcome.FAILURE
            }
            val propertyId = input.propertyId?.takeIf(String::isNotBlank) ?: return invalid()
            val type = input.type?.let { runCatching { InspectionScheduleType.valueOf(it) }.getOrNull() } ?: return invalid()
            val dueAt = input.dueAtMillis?.let(Instant::ofEpochMilli) ?: return invalid()
            val occurrenceId = input.occurrenceId ?: return invalid()
            val route = ScheduleRoutePayload(propertyId, type)
            if (occurrenceId != reminderOccurrenceId(route, dueAt)) return invalid()
            if (store.read(occurrenceId) == ReceiptState.DELIVERED) return WorkerOutcome.SUCCESS
            val delivery = reminderDeliveryPlan(sdkInt, permissionGranted, route, dueAt)
            if (delivery is ReminderDeliveryPlan.Retry) {
                logger.log(LogStage.PERMISSION, occurrenceId, type, true, LogError.PERMISSION_DENIED)
                return WorkerOutcome.RETRY
            }
            delivery as ReminderDeliveryPlan.Notify
            return try {
                notify(delivery)
                if (store.compareAndSet(occurrenceId, setOf(ReceiptState.ENQUEUED, null), ReceiptState.DELIVERED) || store.read(occurrenceId) == ReceiptState.DELIVERED) WorkerOutcome.SUCCESS
                else WorkerOutcome.RETRY.also { logger.log(LogStage.RECEIPT_DELIVERED, occurrenceId, type, true, LogError.RECEIPT_WRITE_FAILED) }
            } catch (error: RuntimeException) {
                logger.log(LogStage.NOTIFY, occurrenceId, type, true, LogError.NOTIFY_EXCEPTION)
                WorkerOutcome.RETRY
            }
        }
    }
}

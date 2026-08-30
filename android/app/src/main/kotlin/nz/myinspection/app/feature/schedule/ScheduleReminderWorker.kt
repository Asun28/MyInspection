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

class ScheduleReminderWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : Worker(appContext, parameters) {
    override fun doWork(): Result {
        val propertyId = inputData.getString(ReminderWorkData.PROPERTY_ID) ?: return Result.failure()
        val type = inputData.getString(ReminderWorkData.INSPECTION_TYPE)
            ?.let { runCatching { InspectionScheduleType.valueOf(it) }.getOrNull() }
            ?: return Result.failure()
        val dueAtMillis = inputData.getLong(ReminderWorkData.DUE_AT_EPOCH_MILLIS, Long.MIN_VALUE)
        if (dueAtMillis == Long.MIN_VALUE) return Result.failure()
        val route = ScheduleRoutePayload(propertyId, type)

        val permissionGranted = Build.VERSION.SDK_INT < 33 ||
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (reminderDeliveryAction(Build.VERSION.SDK_INT, permissionGranted) == ReminderDeliveryAction.RETRY) {
            return Result.retry()
        }

        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Inspection reminders / 巡检提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val copy = scheduleNotificationCopy(type)
        val notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(copy.title)
            .setContentText(copy.body)
            .setStyle(Notification.BigTextStyle().bigText(copy.body))
            .setAutoCancel(true)
            .setContentIntent(routePendingIntent(route, Instant.ofEpochMilli(dueAtMillis)))
            .build()
        notificationManager.notify(reminderRouteIntentSpec(route, Instant.ofEpochMilli(dueAtMillis)).requestCode, notification)
        return Result.success()
    }

    private fun routePendingIntent(route: ScheduleRoutePayload, dueAt: Instant): PendingIntent {
        val intentSpec = reminderRouteIntentSpec(route, dueAt)
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
    }
}

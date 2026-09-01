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
import java.util.UUID
import nz.myinspection.app.MainActivity
import nz.myinspection.core.schedule.InspectionScheduleType

/** Answers whether this app may post a notification right now. */
interface ReminderPermissionPort {
    fun isPostNotificationsGranted(): Boolean
}

/**
 * Builds the platform notification. Split from [ReminderNotifierPort] so that "the failure landed
 * before the post" is decided by which port threw, not by a wrapper a caller could forget to apply.
 */
interface ReminderPreparationPort<out T : Any> {
    fun prepare(plan: DeliveryPlan.Notify): T
}

/** Hands a prepared notification to the platform. Anything thrown here may already have shown. */
interface ReminderNotifierPort<in T : Any> {
    fun post(identity: NotificationIdentity, prepared: T)
}

/** Work input exactly as the platform hands it over, before any of it is trusted. */
data class ReminderWorkInput(
    val occurrenceId: String?,
    val propertyId: String?,
    val inspectionType: String?,
    val dueAt: String?,
    val generationNumber: String?,
    val workRequestId: UUID,
)

enum class ReminderRunOutcome { SUCCESS, RETRY, FAILURE }

private data class ValidInput(
    val occurrenceId: String,
    val generationNumber: Long,
    val workRequestId: UUID,
    val spec: ReminderSpec,
)

/**
 * One reminder delivery, from untrusted work input to a durable outcome.
 *
 * Two writes bracket the notifier. The first moves the occurrence to
 * [ReminderPhase.DELIVERY_UNCERTAIN], and only the caller whose own compare and set applied it may
 * post: finding that another caller already left it uncertain is a stop, never permission to post
 * again. The second records the delivery. Everything that can fail while the user is still
 * un-notified therefore runs before the first write, which is why a post failure can only leave
 * the delivery uncertain.
 */
class ReminderDeliveryRunner<T : Any>(
    private val store: ReminderReceiptStore,
    private val permissions: ReminderPermissionPort,
    private val preparation: ReminderPreparationPort<T>,
    private val notifier: ReminderNotifierPort<T>,
    private val diagnostics: ReminderDiagnosticPort,
) {
    fun run(input: ReminderWorkInput, sdkInt: Int, runAttemptCount: Int): ReminderRunOutcome {
        val valid = validate(input) ?: return refuseInput(input)
        val held = admittedPhase(valid) ?: return ReminderRunOutcome.FAILURE
        val plan = reminderDeliveryPlan(
            sdkInt = sdkInt,
            permissionGranted = permissions.isPostNotificationsGranted(),
            route = valid.spec.route,
            dueAt = valid.spec.dueAt,
        )
        if (plan !is DeliveryPlan.Notify) {
            return close(
                valid, held, runAttemptCount, LogStage.PERMISSION, LogError.PERMISSION_DENIED,
                FailureDisposition(FailureKind.TRANSIENT, FailureCauseCode.SECURITY),
                ReminderPhase.PERMISSION_BLOCKED,
            )
        }
        val prepared = try {
            preparation.prepare(plan)
        } catch (failure: Throwable) {
            return close(
                valid, held, runAttemptCount, LogStage.PREPARATION, LogError.PREPARATION_FAILED,
                classifyReminderFailure(failure), ReminderPhase.TERMINAL,
            )
        }
        return deliver(valid, held, plan, prepared)
    }

    /**
     * The phase this run holds the occurrence in, or null when it may not deliver. A pending
     * receipt is confirmed here, and that compare and set is what makes admission unique: a second
     * worker either loses it or arrives to find a phase it must not restart from.
     */
    private fun admittedPhase(valid: ValidInput): ReminderPhase? {
        val lookup = store.lookup(valid.occurrenceId)
        val present = lookup as? ReminderReceiptLookup.Present
        val uncertain = present?.writeUncertain == true ||
            lookup == ReminderReceiptLookup.Quarantined(ReminderQuarantineReason.WRITE_UNCERTAIN)
        if (uncertain) {
            noteWriteUncertain(valid)
            return null
        }
        // Generation is the whole correspondence check. The store only returns a receipt whose own
        // occurrence matches, and its receipt invariant already derives the work id from that
        // occurrence and generation and keeps the spec canonical for it, while validate derived
        // this run's work id and spec the same way. Equal generations therefore already mean equal
        // work ids and equal specs, so re-comparing them here would be a second authority that no
        // input could ever make disagree.
        if (present == null || present.receipt.generationNumber != valid.generationNumber) {
            noteInvalidReceipt(valid)
            return null
        }
        return when (present.receipt.phase) {
            ReminderPhase.ADMISSION_PENDING -> confirmAdmission(valid)
            ReminderPhase.ENQUEUED, ReminderPhase.RETRYABLE -> present.receipt.phase
            // Already settled, or left uncertain by a run that did post. Every LogError names a
            // failure and this is not one, so the stop is silent rather than reported.
            else -> null
        }
    }

    private fun confirmAdmission(valid: ValidInput): ReminderPhase? = when (
        transition(valid, ReminderPhase.ADMISSION_PENDING, ReminderPhase.ENQUEUED, null)
    ) {
        is ReminderReceiptTransitionResult.Applied -> ReminderPhase.ENQUEUED
        ReminderReceiptTransitionResult.WriteUncertain -> {
            noteWriteUncertain(valid)
            null
        }
        else -> null
    }

    private fun deliver(
        valid: ValidInput,
        held: ReminderPhase,
        plan: DeliveryPlan.Notify,
        prepared: T,
    ): ReminderRunOutcome {
        when (transition(valid, held, ReminderPhase.DELIVERY_UNCERTAIN, null)) {
            is ReminderReceiptTransitionResult.Applied -> Unit
            ReminderReceiptTransitionResult.WriteUncertain -> return noteWriteUncertain(valid)
            else -> return ReminderRunOutcome.FAILURE
        }
        try {
            notifier.post(reminderNotificationIdentity(plan.intent), prepared)
        } catch (failure: Throwable) {
            // The notification may already be on screen, so no automatic path may post again. The
            // occurrence stays uncertain, which the store only lets a delivery acknowledgement
            // leave, and this run closes rather than asking for another attempt.
            note(
                valid, LogStage.NOTIFY, LogError.NOTIFY_FAILED,
                classifyReminderFailure(failure).causeCode, false,
            )
            return ReminderRunOutcome.FAILURE
        }
        return when (
            transition(valid, ReminderPhase.DELIVERY_UNCERTAIN, ReminderPhase.DELIVERED, null)
        ) {
            is ReminderReceiptTransitionResult.Applied -> ReminderRunOutcome.SUCCESS
            ReminderReceiptTransitionResult.WriteUncertain -> noteWriteUncertain(valid)
            else -> noteInvalidReceipt(valid)
        }
    }

    /**
     * Records a failure reached before the notifier ran. A transient one is retried at attempts
     * zero and one and closed at the next, an exhausted or permanent one closes immediately.
     */
    private fun close(
        valid: ValidInput,
        held: ReminderPhase,
        runAttemptCount: Int,
        stage: LogStage,
        error: LogError,
        disposition: FailureDisposition,
        exhausted: ReminderPhase,
    ): ReminderRunOutcome {
        val retry = disposition.kind == FailureKind.TRANSIENT &&
            runAttemptCount <= LAST_RETRYABLE_ATTEMPT
        note(valid, stage, error, disposition.causeCode, retry)
        val next = if (retry) ReminderPhase.RETRYABLE else exhausted
        val closingCause = ReminderCause.PERMANENT_DELIVERY_FAILURE
            .takeIf { next == ReminderPhase.TERMINAL }
        return when (transition(valid, held, next, closingCause)) {
            is ReminderReceiptTransitionResult.Applied ->
                if (retry) ReminderRunOutcome.RETRY else ReminderRunOutcome.FAILURE
            // Asking for a retry this run could not record durably would leave the attempt count
            // as the only survivor of the decision, so an unrecorded outcome is always a failure.
            ReminderReceiptTransitionResult.WriteUncertain -> noteWriteUncertain(valid)
            else -> ReminderRunOutcome.FAILURE
        }
    }

    private fun transition(
        valid: ValidInput,
        from: ReminderPhase,
        to: ReminderPhase,
        cause: ReminderCause?,
    ): ReminderReceiptTransitionResult = store.compareAndSet(
        valid.occurrenceId, valid.generationNumber, valid.workRequestId, from, to, cause,
    )

    private fun noteWriteUncertain(valid: ValidInput): ReminderRunOutcome {
        note(valid, LogStage.RECEIPT, LogError.RECEIPT_WRITE_FAILED, FailureCauseCode.IO, false)
        return ReminderRunOutcome.FAILURE
    }

    private fun noteInvalidReceipt(valid: ValidInput): ReminderRunOutcome {
        note(valid, LogStage.RECEIPT, LogError.RECEIPT_INVALID, FailureCauseCode.ILLEGAL_STATE, false)
        return ReminderRunOutcome.FAILURE
    }

    private fun note(
        valid: ValidInput,
        stage: LogStage,
        error: LogError,
        cause: FailureCauseCode,
        retryable: Boolean,
    ) = diagnostics.record(
        LogRecord(
            stage, valid.occurrenceId, valid.spec.route.inspectionType, valid.generationNumber,
            valid.workRequestId.toString(), retryable, error, cause,
        ),
    )

    /**
     * Reports input this run could not read. The two ids carried are unverified, and the
     * diagnostics contract drops either one it cannot correlate rather than publishing it.
     */
    private fun refuseInput(input: ReminderWorkInput): ReminderRunOutcome {
        diagnostics.record(
            LogRecord(
                LogStage.INPUT, input.occurrenceId, null, null, input.workRequestId.toString(),
                false, LogError.INVALID_INPUT, FailureCauseCode.INVALID_INPUT,
            ),
        )
        return ReminderRunOutcome.FAILURE
    }

    private companion object {
        const val LAST_RETRYABLE_ATTEMPT = 1
    }
}

/**
 * Reads the work input, or null when any of it fails to describe this run. The occurrence digest
 * re-derived from property, type and due instant binds those three, and the request id the
 * platform is actually running under binds the generation. Nothing is read from the store until
 * both hold, so a request naming another occurrence or generation never reaches a receipt.
 */
private fun validate(input: ReminderWorkInput): ValidInput? {
    val propertyId = input.propertyId?.takeIf(String::isNotBlank) ?: return null
    val type = input.inspectionType
        ?.let { name -> runCatching { InspectionScheduleType.valueOf(name) }.getOrNull() }
        ?: return null
    val dueAt = input.dueAt?.let { text -> runCatching { Instant.parse(text) }.getOrNull() }
        ?: return null
    val generationNumber = input.generationNumber?.toLongOrNull()?.takeIf { it >= 0 } ?: return null
    val spec = WorkSpecFactory().create(ScheduleRoute(propertyId, type), dueAt)
    val correlated = input.occurrenceId == spec.occurrenceId &&
        input.workRequestId == reminderGenerationId(spec.occurrenceId, generationNumber)
    return if (correlated) {
        ValidInput(spec.occurrenceId, generationNumber, input.workRequestId, spec)
    } else {
        null
    }
}

/**
 * Android adapter. It reads the work input, resolves the runtime ports and translates the runner's
 * outcome, and holds no delivery decision of its own.
 */
class ReminderWorker(appContext: Context, parameters: WorkerParameters) :
    Worker(appContext, parameters) {
    override fun doWork(): Result {
        val outcome = ReminderDeliveryRunner(
            store = reminderReceiptStore(applicationContext),
            permissions = AndroidReminderPermissionPort(applicationContext),
            preparation = AndroidReminderPreparationPort(applicationContext),
            notifier = AndroidReminderNotifierPort(applicationContext),
            diagnostics = AndroidReminderDiagnosticPort,
        ).run(
            input = ReminderWorkInput(
                occurrenceId = inputData.getString(ReminderWorkKeys.OCCURRENCE_ID),
                propertyId = inputData.getString(ReminderWorkKeys.PROPERTY_ID),
                inspectionType = inputData.getString(ReminderWorkKeys.INSPECTION_TYPE),
                dueAt = inputData.getString(ReminderWorkKeys.DUE_AT),
                generationNumber = inputData.getString(ReminderWorkKeys.GENERATION_NUMBER),
                workRequestId = id,
            ),
            sdkInt = Build.VERSION.SDK_INT,
            runAttemptCount = runAttemptCount,
        )
        return when (outcome) {
            ReminderRunOutcome.SUCCESS -> Result.success()
            ReminderRunOutcome.RETRY -> Result.retry()
            ReminderRunOutcome.FAILURE -> Result.failure()
        }
    }

    companion object {
        const val ACTION_OPEN_SCHEDULE = "nz.myinspection.app.action.OPEN_SCHEDULE"
        const val EXTRA_PROPERTY_ID = "nz.myinspection.app.extra.PROPERTY_ID"
        const val EXTRA_INSPECTION_TYPE = "nz.myinspection.app.extra.INSPECTION_TYPE"
        internal const val CHANNEL_ID = "inspection-reminders"
        internal const val CHANNEL_NAME = "Inspection reminders"
    }
}

internal class AndroidReminderPermissionPort(private val context: Context) : ReminderPermissionPort {
    override fun isPostNotificationsGranted(): Boolean = context.checkSelfPermission(
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}

internal class AndroidReminderPreparationPort(private val context: Context) :
    ReminderPreparationPort<Notification> {
    override fun prepare(plan: DeliveryPlan.Notify): Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                ReminderWorker.CHANNEL_ID,
                ReminderWorker.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        return Notification.Builder(context, ReminderWorker.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(plan.copy.title)
            .setContentText(plan.copy.body)
            .setStyle(Notification.BigTextStyle().bigText(plan.copy.body))
            .setVisibility(lockScreenVisibility(plan.visibility))
            .setOnlyAlertOnce(plan.onlyAlertOnce)
            .setAutoCancel(true)
            .setContentIntent(routePendingIntent(context, plan.intent))
            .build()
    }
}

internal class AndroidReminderNotifierPort(private val context: Context) :
    ReminderNotifierPort<Notification> {
    override fun post(identity: NotificationIdentity, prepared: Notification) {
        context.getSystemService(NotificationManager::class.java)
            .notify(identity.tag, identity.id, prepared)
    }
}

private fun lockScreenVisibility(visibility: NotificationVisibility): Int = when (visibility) {
    NotificationVisibility.PRIVATE -> Notification.VISIBILITY_PRIVATE
    NotificationVisibility.PUBLIC -> Notification.VISIBILITY_PUBLIC
}

/**
 * Builds the tap target. A mutable or implicit PendingIntent handed to the notification shade is
 * the classic hijack, so a contract that ever stopped asking for both must fail here loudly.
 */
private fun routePendingIntent(context: Context, spec: RouteIntentSpec): PendingIntent {
    require(spec.isExplicit && spec.isImmutable) { "route intent must stay explicit and immutable" }
    val intent = Intent(context, MainActivity::class.java).apply {
        action = ReminderWorker.ACTION_OPEN_SCHEDULE
        data = Uri.parse(spec.data)
        putExtra(ReminderWorker.EXTRA_PROPERTY_ID, spec.propertyId)
        putExtra(ReminderWorker.EXTRA_INSPECTION_TYPE, spec.inspectionType)
    }
    return PendingIntent.getActivity(
        context,
        spec.requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

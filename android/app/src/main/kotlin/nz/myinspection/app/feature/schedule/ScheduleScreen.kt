package nz.myinspection.app.feature.schedule
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import nz.myinspection.app.ui.theme.FieldLedgerStatusColor
import nz.myinspection.app.ui.theme.fieldLedgerDarkStatusColors
import nz.myinspection.app.ui.theme.fieldLedgerLightStatusColors
import nz.myinspection.app.ui.theme.fieldLedgerShapes
@Composable
fun ScheduleRouteContent(
    items: List<ScheduleItem>,
    now: Instant,
    filter: ScheduleFilter,
    onFilterChange: (ScheduleFilter) -> Unit,
    onOpenInspection: (ScheduleRoute) -> Unit,
) {
    val context = LocalContext.current
    var permissionState by remember {
        mutableStateOf(
            if (
                Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                PermissionState.GRANTED
            } else if (context.notificationPermissionWasRequested()) {
                PermissionState.DENIED
            } else {
                PermissionState.UNKNOWN
            },
        )
    }
    var pendingReminder by rememberSaveable { mutableStateOf<PendingReminder?>(null) }
    var showRationale by rememberSaveable { mutableStateOf(false) }
    var reminderFailed by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionState = if (granted) PermissionState.GRANTED else PermissionState.DENIED
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val granted = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        permissionState = if (granted) PermissionState.GRANTED else PermissionState.DENIED
    }
    val requestPermission = {
        if (context.markNotificationPermissionRequested()) {
            showRationale = false
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissionState = PermissionState.DENIED
        }
    }
    val rows = scheduleRows(items, now, filter)
    val schedulePending: (PendingReminder) -> Unit = { pending ->
        pendingReminder = pending; reminderFailed = false
        ReminderScheduler.schedule(context, pending.workSpec()) { success -> if (pendingReminder == pending) { pendingReminder = pending.afterSchedule(success); reminderFailed = !success } }
    }
    LaunchedEffect(permissionState) {
        if (permissionState == PermissionState.GRANTED && !reminderFailed) pendingReminder?.let(schedulePending)
    }
    ScheduleScreen(
        rows = rows,
        filter = filter,
        permissionState = permissionState,
        showRationale = showRationale,
        reminderFailed = reminderFailed,
        onFilterChange = onFilterChange,
        onOpenInspection = onOpenInspection,
        onContinuePermission = requestPermission,
        onOpenSettings = {
            settingsLauncher.launch(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                },
            )
        },
        onRetryReminder = { pendingReminder?.let(schedulePending) },
        onReminderAction = { row ->
            val rationaleRequired = context.findActivity()
                ?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
            val transition = scheduleRouteContentTransition(row, Build.VERSION.SDK_INT, permissionState, rationaleRequired)
            when (transition.action) {
                PermissionAction.Schedule -> schedulePending(transition.pending)
                PermissionAction.RequestPermission -> {
                    pendingReminder = transition.pending
                    requestPermission()
                }
                is PermissionAction.ShowRationale -> {
                    pendingReminder = transition.pending
                    showRationale = true
                }
                is PermissionAction.ExplainDenied -> pendingReminder = transition.pending
            }
        },
    )
}
@Composable
fun ScheduleScreen(
    rows: List<ScheduleRow>,
    filter: ScheduleFilter,
    permissionState: PermissionState,
    showRationale: Boolean,
    reminderFailed: Boolean,
    onFilterChange: (ScheduleFilter) -> Unit,
    onOpenInspection: (ScheduleRoute) -> Unit,
    onContinuePermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetryReminder: () -> Unit,
    onReminderAction: (ScheduleRow) -> Unit,
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Inspection schedule", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Reminder dates are planning guidance. Compliance is checked when you create or reschedule an inspection.",
            style = MaterialTheme.typography.bodyMedium,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ScheduleFilter.entries.size) { index ->
                val option = ScheduleFilter.entries[index]
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilterChange(option) },
                    label = { Text(option.label()) },
                )
            }
        }
        if (filter != ScheduleFilter.ALL) {
            TextButton(onClick = { onFilterChange(ScheduleFilter.ALL) }) { Text("Clear filters") }
        }
        if (showRationale) {
            Surface(
                shape = fieldLedgerShapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Allow notifications for this local reminder. / 允许通知以接收此本地提醒。")
                    Button(onClick = onContinuePermission) { Text("Continue / 继续") }
                }
            }
        }
        if (permissionState == PermissionState.DENIED) {
            val denied = PermissionPolicy.next(33, permissionState) as PermissionAction.ExplainDenied
            Surface(
                shape = fieldLedgerShapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${denied.english}\n${denied.chinese}\nSchedule guidance remains available here without notifications.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Button(onClick = onOpenSettings) { Text("Open settings / 打开设置") }
                }
            }
        }
        if (reminderFailed) Button(onClick = onRetryReminder) { Text("Retry reminder / 重试提醒") }
        if (rows.isEmpty()) {
            val empty = scheduleEmptyState(filter)
            Surface(
                shape = fieldLedgerShapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScheduleStateBadge(empty.badge)
                    Text(empty.message)
                }
            }
        }
        rows.forEach { row ->
            ScheduleRow(
                row = row,
                onOpen = { onOpenInspection(row.route) },
                onReminder = { onReminderAction(row) },
            )
        }
    }
}
@Composable
private fun ScheduleRow(
    row: ScheduleRow,
    onOpen: () -> Unit,
    onReminder: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ListItem(
            headlineContent = { Text(row.displayName, style = MaterialTheme.typography.titleMedium) },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ScheduleStateBadge(row.badge)
                    Text(row.nextFact)
                }
            },
            trailingContent = { Text("Open inspection") },
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        )
        if (row.dueAt != null) {
            Button(onClick = onReminder, modifier = Modifier.fillMaxWidth()) { Text("Set local reminder") }
        }
    }
}
@Composable
private fun ScheduleStateBadge(badge: ScheduleBadge) {
    val roles = if (isSystemInDarkTheme()) fieldLedgerDarkStatusColors else fieldLedgerLightStatusColors
    val color: FieldLedgerStatusColor = when (badge) {
        ScheduleBadge.DUE -> roles.critical
        ScheduleBadge.UPCOMING -> roles.ok
        ScheduleBadge.FIRST -> roles.attention
        ScheduleBadge.ONE_OFF -> roles.notApplicable
        ScheduleBadge.EMPTY -> roles.notApplicable
    }
    Surface(shape = fieldLedgerShapes.small, color = color.container) {
        Text(
            badge.label(),
            color = color.content,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
private fun Context.notificationPermissionWasRequested(): Boolean =
    getSharedPreferences(PERMISSION_PREFERENCES, Context.MODE_PRIVATE).getBoolean(PERMISSION_REQUESTED, false)
private fun Context.markNotificationPermissionRequested(): Boolean =
    getSharedPreferences(PERMISSION_PREFERENCES, Context.MODE_PRIVATE).edit()
        .putBoolean(PERMISSION_REQUESTED, true)
        .commit()
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
private const val PERMISSION_PREFERENCES = "schedule-notification-permission"
private const val PERMISSION_REQUESTED = "requested"

package nz.myinspection.app.feature.notice

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.PersistableBundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import nz.myinspection.core.compliance.ComplianceReasonKey
import nz.myinspection.core.notice.NoticeCopy
import nz.myinspection.core.notice.NoticeDeliveryMethod
import nz.myinspection.core.notice.NoticeStatusText
import nz.myinspection.core.notice.noticeReasonText
import nz.myinspection.core.notice.recordedNoticeStatus

enum class NoticeUiStatus {
    DRAFT,
    VALID,
    BLOCKED,
    COPIED,
    RECORDED,
}

data class NoticeListItemUi(
    val id: String,
    val noticeDate: String,
    val inspectionDate: String,
    val status: NoticeUiStatus,
    val deliverySummary: String? = null,
    val validationPassed: Boolean? = null,
)

data class NoticeComposeUi(
    val status: NoticeUiStatus = NoticeUiStatus.DRAFT,
    val recipientName: String = "",
    val senderName: String = "",
    val fullText: String = "",
    val reasonKeys: List<ComplianceReasonKey> = emptyList(),
    val validationPassed: Boolean? = null,
    val shareBoundaryAcknowledged: Boolean = false,
    val deliveryMethod: NoticeDeliveryMethod? = null,
    val deliveryTime: String = "",
    val deliveryTimeError: String? = null,
    val isRecordingDelivery: Boolean = false,
)

data class NoticeComposeActions(
    val onRecipientNameChange: (String) -> Unit,
    val onSenderNameChange: (String) -> Unit,
    val onGenerate: () -> Unit,
    val onAcknowledgeShareBoundary: () -> Unit,
    val onCopy: () -> NoticeCopy?,
    val onCopied: () -> Unit,
    val onDeliveryMethodChange: (NoticeDeliveryMethod) -> Unit,
    val onDeliveryTimeChange: (String) -> Unit,
    val onRecordDelivery: (NoticeDeliveryMethod, String) -> Unit,
    val onCorrectSchedule: () -> Unit,
)

/** Stateless route content; navigation and database lifetime stay with the owning app shell. */
@Composable
fun NoticeCenterScreen(
    notices: List<NoticeListItemUi>,
    onNewNotice: () -> Unit,
    onOpenNotice: (String) -> Unit,
    restoreNewNoticeFocus: Boolean = false,
) {
    val newNoticeFocus = remember { FocusRequester() }
    LaunchedEffect(restoreNewNoticeFocus) {
        if (restoreNewNoticeFocus) newNoticeFocus.requestFocus()
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Inspection notices", style = MaterialTheme.typography.titleLarge)
        Text(
            "MyInspection prepares notices for you to copy. It never sends a text, email or letter.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = onNewNotice,
            modifier = Modifier.focusRequester(newNoticeFocus),
        ) {
            Text("New notice")
        }
        if (notices.isEmpty()) {
            Text("No notices recorded.")
        }
        notices.forEach { notice ->
            NoticeDeliveryRow(notice = notice, onOpen = { onOpenNotice(notice.id) })
        }
    }
}

@Composable
fun NoticeComposeScreen(
    state: NoticeComposeUi,
    actions: NoticeComposeActions,
) {
    val context = LocalContext.current
    val clipboard = remember(context) {
        requireNotNull(context.getSystemService(ClipboardManager::class.java))
    }
    val recipientFocus = remember { FocusRequester() }
    val statusFocus = remember { FocusRequester() }
    val correctionFocus = remember { FocusRequester() }
    val boundaryFocus = remember { FocusRequester() }
    val copyFocus = remember { FocusRequester() }
    val methodFocus = remember { FocusRequester() }

    LaunchedEffect(state.status, state.shareBoundaryAcknowledged) {
        when (state.status) {
            NoticeUiStatus.DRAFT -> recipientFocus.requestFocus()
            NoticeUiStatus.BLOCKED -> correctionFocus.requestFocus()
            NoticeUiStatus.VALID -> {
                if (state.shareBoundaryAcknowledged) copyFocus.requestFocus() else boundaryFocus.requestFocus()
            }
            NoticeUiStatus.COPIED -> methodFocus.requestFocus()
            NoticeUiStatus.RECORDED -> statusFocus.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Prepare inspection notice", style = MaterialTheme.typography.titleLarge)
        NoticeStatusBanner(state.status, state.validationPassed, statusFocus)

        if (state.status == NoticeUiStatus.DRAFT) {
            OutlinedTextField(
                value = state.recipientName,
                onValueChange = actions.onRecipientNameChange,
                label = { Text("Recipient name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(recipientFocus),
            )
            OutlinedTextField(
                value = state.senderName,
                onValueChange = actions.onSenderNameChange,
                label = { Text("Sender name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = actions.onGenerate,
                enabled = state.recipientName.isNotBlank() && state.senderName.isNotBlank(),
            ) {
                Text("Check and generate")
            }
        }

        ComplianceResult(
            passed = state.validationPassed,
            reasonKeys = state.reasonKeys,
            showCorrection = state.status == NoticeUiStatus.BLOCKED || state.validationPassed == false,
            correctionFocus = correctionFocus,
            onCorrectSchedule = actions.onCorrectSchedule,
        )

        if (
            state.fullText.isNotBlank() &&
            state.status in setOf(NoticeUiStatus.VALID, NoticeUiStatus.COPIED, NoticeUiStatus.RECORDED)
        ) {
            Text("Notice preview", style = MaterialTheme.typography.titleMedium)
            Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 1.dp) {
                Text(state.fullText, modifier = Modifier.padding(12.dp))
            }
            Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 2.dp) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (state.shareBoundaryAcknowledged) {
                            "✓ Copy boundary acknowledged"
                        } else {
                            "↗ Copy boundary"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "A copy will leave MyInspection and may be stored by another app. " +
                            "Copying does not send this notice.",
                    )
                    if (!state.shareBoundaryAcknowledged) {
                        TextButton(
                            onClick = actions.onAcknowledgeShareBoundary,
                            modifier = Modifier.focusRequester(boundaryFocus),
                        ) {
                            Text("I understand")
                        }
                    }
                }
            }
            Button(
                onClick = {
                    actions.onCopy()?.let { copy ->
                        clipboard.copyNotice(copy)
                        actions.onCopied()
                    }
                },
                enabled = state.shareBoundaryAcknowledged,
                modifier = Modifier.focusRequester(copyFocus),
            ) {
                Text("Copy notice")
            }

            if (state.status == NoticeUiStatus.COPIED) {
                Text("Copied. MyInspection has not sent the notice.")
                DeliveryForm(state = state, actions = actions, methodFocus = methodFocus)
            }
        }
    }
}

@Composable
private fun NoticeDeliveryRow(notice: NoticeListItemUi, onOpen: () -> Unit) {
    val presentation = statusPresentation(notice.status, notice.validationPassed)
    ListItem(
        headlineContent = {
            Text(
                presentation.label,
                color = if (presentation.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Notice prepared: ${notice.noticeDate}")
                Text("Inspection: ${notice.inspectionDate}")
                notice.deliverySummary?.let { Text("Delivery: $it") }
                notice.validationPassed?.let { passed ->
                    Text(if (passed) "Compliance: Pass" else "Compliance: Fail")
                }
            }
        },
        trailingContent = { TextButton(onClick = onOpen) { Text("Open notice") } },
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (presentation.isError) Modifier.semantics { error(presentation.label) } else Modifier,
            ),
    )
}

@Composable
private fun NoticeStatusBanner(
    status: NoticeUiStatus,
    validationPassed: Boolean?,
    focusRequester: FocusRequester,
) {
    val presentation = statusPresentation(status, validationPassed)
    Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 2.dp) {
        Text(
            presentation.label,
            modifier = Modifier
                .padding(12.dp)
                .focusRequester(focusRequester)
                .focusable()
                .then(
                    if (presentation.isError) Modifier.semantics { error(presentation.label) } else Modifier,
                ),
            style = MaterialTheme.typography.titleMedium,
            color = if (presentation.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ComplianceResult(
    passed: Boolean?,
    reasonKeys: List<ComplianceReasonKey>,
    showCorrection: Boolean,
    correctionFocus: FocusRequester,
    onCorrectSchedule: () -> Unit,
) {
    if (passed == null && reasonKeys.isEmpty()) return
    val failed = passed != true
    val resultLabel = if (failed) "Compliance check: Fail" else "Compliance check: Pass"
    Text(
        resultLabel,
        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = if (failed) Modifier.semantics { error(resultLabel) } else Modifier,
    )
    reasonKeys.forEach { key ->
        val copy = noticeReasonText(key)
        ListItem(
            headlineContent = { Text(copy.english) },
            supportingContent = { Text(copy.chinese) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (showCorrection) {
        Text("The record remains unchanged. Review the inspection time before relying on this notice.")
        Button(
            onClick = onCorrectSchedule,
            modifier = Modifier.focusRequester(correctionFocus),
        ) {
            Text("Review inspection time")
        }
    }
}

@Composable
private fun DeliveryForm(
    state: NoticeComposeUi,
    actions: NoticeComposeActions,
    methodFocus: FocusRequester,
) {
    Text("Record delivery", style = MaterialTheme.typography.titleMedium)
    Text("Do this only after you have sent the notice yourself.")
    NoticeDeliveryMethod.entries.forEachIndexed { index, method ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (index == 0) Modifier.focusRequester(methodFocus) else Modifier)
                .clickable(enabled = !state.isRecordingDelivery) { actions.onDeliveryMethodChange(method) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = state.deliveryMethod == method,
                onClick = null,
            )
            Text(method.label())
        }
    }
    OutlinedTextField(
        value = state.deliveryTime,
        onValueChange = actions.onDeliveryTimeChange,
        label = { Text("Delivery time") },
        supportingText = { Text(state.deliveryTimeError ?: "Use local date and time") },
        isError = state.deliveryTimeError != null,
        enabled = !state.isRecordingDelivery,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { actions.onRecordDelivery(checkNotNull(state.deliveryMethod), state.deliveryTime) },
        enabled = !state.isRecordingDelivery &&
            state.deliveryMethod != null &&
            state.deliveryTime.isNotBlank() &&
            state.deliveryTimeError == null,
    ) {
        Text(if (state.isRecordingDelivery) "Recording…" else "Record delivery")
    }
}

private fun NoticeDeliveryMethod.label(): String = when (this) {
    NoticeDeliveryMethod.SMS -> "Text message (SMS)"
    NoticeDeliveryMethod.EMAIL -> "Email"
    NoticeDeliveryMethod.LETTER -> "Letter"
}

private fun statusPresentation(status: NoticeUiStatus, validationPassed: Boolean?): NoticeStatusText = when (status) {
    NoticeUiStatus.DRAFT -> NoticeStatusText("○ Draft", isError = false)
    NoticeUiStatus.VALID -> NoticeStatusText("✓ Valid", isError = false)
    NoticeUiStatus.BLOCKED -> NoticeStatusText("⚠ Blocked", isError = true)
    NoticeUiStatus.COPIED -> NoticeStatusText("✓ Copied — not sent", isError = false)
    NoticeUiStatus.RECORDED -> recordedNoticeStatus(validationPassed == true)
}

private fun ClipboardManager.copyNotice(copy: NoticeCopy) {
    val clip = ClipData.newPlainText(copy.lockScreenText, copy.fullText)
    clip.description.extras = PersistableBundle().apply {
        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, copy.isSensitive)
    }
    setPrimaryClip(clip)
}

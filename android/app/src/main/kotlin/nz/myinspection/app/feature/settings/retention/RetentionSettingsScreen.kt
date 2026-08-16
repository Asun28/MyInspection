package nz.myinspection.app.feature.settings.retention

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nz.myinspection.core.retention.CONTACT_RETENTION_MONTHS
import nz.myinspection.core.retention.ContactRetentionState
import nz.myinspection.core.retention.TenancyRetentionStatus

/**
 * 设置页保留区块：展示各活跃 tenancy 的联系方式保留状态 + 到期后一键清理入口。
 *
 * 纯展示 + 本地确认态；真实数据（[nz.myinspection.core.retention.ContactRetentionService]）与导航
 * 入口由调用方注入/挂接——那部分依赖 Activity/DB 生命周期，留给把设置页整体接进 app 的后续任务卡。
 */
@Composable
fun RetentionSettingsScreen(
    statuses: List<TenancyRetentionStatus>,
    onPurge: (tenancyId: String) -> Unit,
) {
    var pendingPurge by remember { mutableStateOf<TenancyRetentionStatus?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Tenant contact retention", style = MaterialTheme.typography.titleLarge)
        Text(
            // 第一个数字来自 CONTACT_RETENTION_MONTHS（本 app 的清理策略，可能改）；第二个「12-month
            // minimum」是 RTA s123A 的法定证据保留下限——两个数字今天恰好相同，但含义不同、不应共用
            // 同一处字面量（改本 app 策略不该悄悄改写这句法律事实陈述，反之亦然）。
            "Contact details can be cleared $CONTACT_RETENTION_MONTHS months after a tenancy ends. " +
                "Inspection records, photos and reports are kept indefinitely, independently of this — " +
                "longer than the Residential Tenancies Act's 12-month minimum, since they may still be " +
                "needed as deposit-dispute evidence.",
            style = MaterialTheme.typography.bodyMedium,
        )

        if (statuses.isEmpty()) {
            Text("No tenancies on record.", style = MaterialTheme.typography.bodyMedium)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(statuses, key = { it.tenancyId }) { status ->
                RetentionRow(status = status, onRequestPurge = { pendingPurge = status })
            }
        }
    }

    pendingPurge?.let { status ->
        PurgeConfirmDialog(
            status = status,
            onConfirm = {
                onPurge(status.tenancyId)
                pendingPurge = null
            },
            onDismiss = { pendingPurge = null },
        )
    }
}

@Composable
private fun RetentionRow(status: TenancyRetentionStatus, onRequestPurge: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(status.tenantName ?: "Tenancy ${status.tenancyId.take(8)}", style = MaterialTheme.typography.titleMedium)
            Text(statusLabel(status.state), style = MaterialTheme.typography.bodyMedium)
            if (status.isPurgeable) {
                Button(onClick = onRequestPurge) { Text("Clear contact info") }
            }
        }
    }
}

private fun statusLabel(state: ContactRetentionState): String = when (state) {
    ContactRetentionState.ACTIVE_TENANCY -> "Tenancy ongoing — retention not started"
    ContactRetentionState.AWAITING_EXPIRY -> "Within the $CONTACT_RETENTION_MONTHS-month contact retention window"
    ContactRetentionState.PURGEABLE -> "Retention window elapsed — eligible to clear"
    ContactRetentionState.PURGED -> "Contact info cleared"
}

/**
 * type-to-confirm：必须原样敲出确认词才点亮「Clear」——清理在 core 层是不可逆的（`tenant_name`/
 * `contact` 被 UPDATE 成 NULL，没有撤销路径），所以这是唯一的确认手段，没有「再想想」式的二次弹窗。
 *
 * 确认词优先取 `tenant_name`，但它是可空列（`tenancy.tenant_name`，见 Tenancy.sq）——若为空，
 * 落回 tenancyId 全量文本。不这样做的话，一个联系方式本就缺失租客姓名的 tenancy 会永远等到一个
 * 空字符串当确认词，按钮永远点不亮，这条 tenancy 的联系方式就再也清不掉了。
 */
@Composable
private fun PurgeConfirmDialog(
    status: TenancyRetentionStatus,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    val expected = status.tenantName?.takeIf { it.isNotBlank() } ?: status.tenancyId

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear contact info?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This permanently clears the tenant name and contact details for this tenancy. " +
                        "Inspection records, photos and reports are not affected.",
                )
                Text("Type \"$expected\" to confirm.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = typed, onValueChange = { typed = it }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = typed == expected) {
                Text("Clear contact info")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

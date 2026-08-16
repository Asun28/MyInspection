package nz.myinspection.app.skeleton

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime

/**
 * T1-SKELETON-E2E · 一条能点完的路：建巡检 → 加一项 → 拍一张 → 导出 PDF。**用完即弃**。
 *
 * 刻意不做（见卡片 non_goals）：无房间导航、无模板、无短语库/听写、无历史对比、无草稿保存、
 * 无进程死亡恢复、无 UI 打磨。真实采集界面是 T2-CAPTURE-UI 的产出。
 *
 * 相机走 [ActivityResultContracts.TakePicturePreview]（返回缩略图 Bitmap）：不需要 CAMERA 权限、
 * 不需要 FileProvider。全分辨率照片管线是 T2-PHOTO-PIPELINE 的活。
 * 导出走 SAF [ActivityResultContracts.CreateDocument]：用户自己选落点，同 ADR-0002，
 * 也免掉 res/xml/file_paths.xml（那在 T2-CAPTURE-UI 的 allow_paths 里）。
 */
@Composable
fun SkeletonScreen() {
    val context = LocalContext.current

    var address by remember { mutableStateOf("") }
    var startedAt by remember { mutableStateOf<LocalDateTime?>(null) }
    var itemText by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf<SkeletonCondition?>(null) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }

    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) {
        if (it != null) photo = it
    }

    // mimeType 决定系统保存面板的默认后缀；返回的 Uri 由 SAF 授权，直接 openOutputStream 写入。
    val exportPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val inspection = SkeletonInspection(
            address = address.ifBlank { "(no address)" },
            startedAt = startedAt ?: LocalDateTime.now(),
            item = condition?.let { SkeletonItem(itemText.ifBlank { "(no description)" }, it, photo) },
        )
        // 失败要看得见：骨架卡没有日志层，Toast 就是它的错误通道。
        val message = try {
            context.contentResolver.openOutputStream(uri)?.use { SkeletonPdf.write(inspection, it) }
                ?: error("openOutputStream returned null for $uri")
            "PDF exported"
        } catch (e: Exception) {
            "Export failed: ${e.message}"
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Walking skeleton", style = MaterialTheme.typography.titleLarge)

        // 1) 建巡检
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Address") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { startedAt = LocalDateTime.now() }) {
            Text(if (startedAt == null) "Start inspection" else "Started ${startedAt}")
        }

        if (startedAt != null) {
            // 2) 加一个检查项 + 状态
            OutlinedTextField(
                value = itemText,
                onValueChange = { itemText = it },
                label = { Text("Item") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonCondition.entries.forEach { candidate ->
                    if (candidate == condition) {
                        Button(onClick = { condition = candidate }) { Text(candidate.label) }
                    } else {
                        OutlinedButton(onClick = { condition = candidate }) { Text(candidate.label) }
                    }
                }
            }

            // 3) 拍一张
            OutlinedButton(onClick = { takePhoto.launch(null) }) {
                Text(if (photo == null) "Take photo" else "Retake photo")
            }
            photo?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                )
            }

            // 4) 导出
            Button(
                onClick = { exportPdf.launch("inspection-skeleton.pdf") },
                enabled = condition != null,
            ) {
                Text("Export PDF")
            }
        }
    }
}

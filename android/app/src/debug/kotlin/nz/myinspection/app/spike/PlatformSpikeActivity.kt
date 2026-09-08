package nz.myinspection.app.spike

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.concurrent.Executors

/** Disposable platform experiments. No inspection records or production navigation. */
class PlatformSpikeActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var result by mutableStateOf("尚未运行；模拟器结果不能替代实体手机验收。")
    private var busy by mutableStateOf(false)
    private val treePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { response ->
        val uri = response.data?.data
        if (response.resultCode == Activity.RESULT_OK && uri != null) {
            val flags = response.data!!.flags
            runProbe { SafProbe(applicationContext).writeAndVerify(uri, flags) }
        } else result = "目录选择已取消；原有验证记录保留。"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var camera by remember { mutableStateOf(false) }
            BackHandler(camera) { camera = false }
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("平台探针", style = MaterialTheme.typography.headlineSmall)
                        Text("${Build.MODEL} · Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
                        if (camera) {
                            Button(onClick = { camera = false }) { Text("返回探针菜单") }
                            CameraGhostProbe { result = it }
                        } else {
                            Button(onClick = { camera = true }, enabled = !busy) { Text("相机叠图") }
                            Button(onClick = {
                                treePicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION))
                            }, enabled = !busy) { Text("选择测试目录并写入") }
                            Button(onClick = { runProbe { SafProbe(applicationContext).verifyAfterRestart() } },
                                enabled = !busy) { Text("重启后只读验证") }
                            Button(onClick = { runProbe { runPdfStress(applicationContext) } }, enabled = !busy) {
                                Text("运行 80 图 PDF 压力测试")
                            }
                        }
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (!camera) Text(result)
                        Text("仅向新建的测试文件写入。相机与 PDF 存 app 专属 spike 目录。")
                    }
                }
            }
        }
    }

    private fun runProbe(block: () -> String) {
        if (busy) return
        busy = true
        result = "测试进行中…"
        worker.execute {
            val message = try { block() } catch (e: Exception) { "测试失败：${e.javaClass.simpleName}；未形成成功结论。" }
            runOnUiThread { if (!isDestroyed) { result = message; busy = false } }
        }
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }
}

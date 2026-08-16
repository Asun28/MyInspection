package nz.myinspection.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import nz.myinspection.app.skeleton.SkeletonScreen

/**
 * T0-TOOLCHAIN 骨架卡：空屏 Compose Activity，证明 :app 能空编译 + 装机包出。
 *
 * T1-SKELETON-E2E 在其上挂了一条**一次性**走通路径（[SkeletonScreen]）：建巡检 → 加一项 → 拍一张 →
 * 导出 PDF，只为在真实 UI 之前先看见端到端结果。真实 UI（模板走查/拍照/短语库等）由后续任务卡填充
 * （见 CLAUDE.md 非目标）；T2-CAPTURE-UI 落地时把 skeleton 包整个删掉，本文件恢复空屏。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InspectionAppRoot()
        }
    }
}

@Composable
private fun InspectionAppRoot() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            SkeletonScreen()
        }
    }
}

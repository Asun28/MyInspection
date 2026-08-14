package nz.myinspection.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * T0-TOOLCHAIN 骨架卡：空屏 Compose Activity，证明 :app 能空编译 + 装机包出。
 * 真实 UI（模板走查/拍照/短语库等）由后续任务卡填充（见 CLAUDE.md 非目标）。
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
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}

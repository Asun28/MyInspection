package nz.myinspection.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 冒烟测试：证明 Kotlin/JVM 工具链 + kotlin.test 跑得起来（T0-TOOLCHAIN DoD 的一部分）。
 */
class CoreModuleTest {
    @Test
    fun `core module name is stable`() {
        assertEquals("core", CoreModule.NAME)
    }
}

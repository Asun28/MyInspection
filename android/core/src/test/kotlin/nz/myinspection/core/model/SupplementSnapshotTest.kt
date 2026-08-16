package nz.myinspection.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SupplementSnapshotTest {
    @Test
    fun `both fields participate in equality`() {
        val base = SupplementSnapshot(createdAt = 1_700_000_000_000L, text = "landlord to fix gate latch")
        assertEquals(base, base.copy())
        assertNotEquals(base, base.copy(createdAt = base.createdAt + 1))
        assertNotEquals(base, base.copy(text = "different text"))
    }

    /**
     * 同 [InspectionSnapshotTest] 的形状断言：相等性证明不了「没有多余字段」。这里额外要守的是**排除项**——
     * 链哈希刻意不含 `id`（随机 UUID，与内容无关）、不含 `inspectionId`（链本就属于某次巡检，冗余）、
     * 不含 `prevHash`（那是链的输入参数，由 `supplementChainHash(prev, s)` 单独传入，不是这一条自身的内容）。
     * 任何一个悄悄混进来，链哈希就变了，而既有相等性用例不会响。
     */
    @Test
    fun `SupplementSnapshot carries exactly createdAt and text`() {
        // 比较排序后的集合：JVM 规范不保证 declaredFields 的顺序，断言顺序会造出假确定性（同
        // InspectionSnapshotTest.assertExactShape 的说明）。要守的是「哪些字段在」。
        assertEquals(
            listOf("createdAt:long", "text:String"),
            SupplementSnapshot::class.java.declaredFields.map { "${it.name}:${it.type.simpleName}" }.sorted(),
            "链哈希域只含内容本身；id / inspectionId / prevHash 一旦混入，chain_hash 会静默改变",
        )
    }
}

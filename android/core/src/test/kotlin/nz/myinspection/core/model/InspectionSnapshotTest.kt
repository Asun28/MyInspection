package nz.myinspection.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * [InspectionSnapshot] 及其嵌套快照类型只是不可变数据形状，没有行为——真正要守的契约是「每个字段都
 * 真的参与相等性」：T1-CANON-HASH 靠这份快照的完整字段集合算哈希，若哪个字段被漏进主构造函数（比如
 * 误写成类体内的可变属性），`.copy(该字段 = 新值)` 根本编不过，或编过了却不影响 `equals`——两种情况
 * 这份测试都会抓到。逐字段用 `copy` 造一个只差一个字段的变体，断言它不等于原值。
 */
class InspectionSnapshotTest {

    private fun sampleSnapshot() = InspectionSnapshot(
        id = "insp-1",
        type = "EXIT",
        tenancyId = "tenancy-1",
        scheduledAt = 1_700_000_000_000L,
        finalizedAt = 1_700_000_100_000L,
        previousInspectionId = "insp-0",
        baselineInspectionId = "insp-baseline",
        property = PropertySnapshot(id = "prop-1", address = "12 Test St", kind = "RENTAL", isBoardingHouse = false),
        tenancy = TenancySnapshot(id = "tenancy-1", startMs = 1_699_000_000_000L, endMs = 1_700_000_000_000L),
        template = TemplateSnapshot(id = "tv-1", type = "EXIT", version = 1, contentHash = "hash-abc"),
        items = listOf(
            InspectionItemSnapshot(stableId = "wall.paint", status = "POOR", note = "scuffed", wearOrDamage = "DAMAGE"),
        ),
        photos = listOf(
            PhotoSnapshot(contentHash = "photohash-1", source = "CAMERA", exifTimeMs = 1_700_000_050_000L, isRoomLevel = false),
        ),
        audios = listOf(AudioSnapshot(contentHash = "audiohash-1")),
    )

    @Test
    fun `structurally identical snapshots are equal`() {
        assertEquals(sampleSnapshot(), sampleSnapshot())
    }

    @Test
    fun `every top-level field participates in equality`() {
        val base = sampleSnapshot()
        assertNotEquals(base, base.copy(id = "insp-2"))
        assertNotEquals(base, base.copy(type = "ROUTINE"))
        assertNotEquals(base, base.copy(tenancyId = null))
        assertNotEquals(base, base.copy(scheduledAt = base.scheduledAt + 1))
        assertNotEquals(base, base.copy(finalizedAt = null))
        assertNotEquals(base, base.copy(previousInspectionId = null))
        assertNotEquals(base, base.copy(baselineInspectionId = null))
        assertNotEquals(base, base.copy(property = base.property.copy(address = "different")))
        assertNotEquals(base, base.copy(tenancy = null))
        assertNotEquals(base, base.copy(template = base.template.copy(version = 2)))
        assertNotEquals(base, base.copy(items = emptyList()))
        assertNotEquals(base, base.copy(photos = emptyList()))
        assertNotEquals(base, base.copy(audios = emptyList()))
    }

    @Test
    fun `tenancy contact fields have no home in TenancySnapshot`() {
        // 哈希域明文排除租客联系方式（保留期清理不得破坏哈希可复验性）——这里没有反射黑魔法能测
        // "某字段不存在"，但至少证明 TenancySnapshot 只用 id/start/end 三个字段就能完整构造。
        val tenancy = TenancySnapshot(id = "t-1", startMs = 0L, endMs = null)
        assertEquals("t-1", tenancy.id)
    }

    @Test
    fun `every property field participates in equality`() {
        val base = PropertySnapshot(id = "p-1", address = "1 Road", kind = "RENTAL", isBoardingHouse = false)
        assertNotEquals(base, base.copy(id = "p-2"))
        assertNotEquals(base, base.copy(address = "2 Road"))
        assertNotEquals(base, base.copy(kind = "OWNER_OCCUPIED"))
        assertNotEquals(base, base.copy(isBoardingHouse = true))
    }

    @Test
    fun `every photo field participates in equality`() {
        val base = PhotoSnapshot(contentHash = "h1", source = "CAMERA", exifTimeMs = 1L, isRoomLevel = false)
        assertNotEquals(base, base.copy(contentHash = "h2"))
        assertNotEquals(base, base.copy(source = "IMPORTED"))
        assertNotEquals(base, base.copy(exifTimeMs = null))
        assertNotEquals(base, base.copy(isRoomLevel = true))
    }
}

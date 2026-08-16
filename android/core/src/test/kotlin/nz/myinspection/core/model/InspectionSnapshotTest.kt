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

    /**
     * 相等性测试**证明不了形状**：给任何一个快照类型加一个带默认值的字段，上面每一条 `copy` 断言照样全绿，
     * 而哈希域已经悄悄变了。租客联系方式那条尤其要害——原先只断言「三参数能构造成功」，可**多出一个带默认值
     * 的第四参数时三参数构造同样成功**，它证明不了缺席。
     *
     * 故按字段逐一钉死形状。`declaredFields` 对 data class 返回主构造函数字段，**按声明顺序**。
     * **本断言不覆盖引用类型的可空性**（`String?` 与 `String` 在 Java 侧同为 `String`）；基本类型可空则装箱
     * （`Long` vs `long`、`Boolean` vs `boolean`），那一类反而被覆盖到了。这个边界是实测结论，不是遗漏。
     */
    private fun assertExactShape(type: Class<*>, expected: List<String>) {
        val actual = type.declaredFields.map { "${it.name}:${it.type.simpleName}" }
        assertEquals(
            expected, actual,
            "${type.simpleName} 的字段集合就是 T1-CANON-HASH 的哈希域形状：多一个、少一个、改名或改类型都会静默" +
                "改变哈希结果。若这是有意的形状变更，同步改这里与 T1-CANON-HASH 的黄金向量。",
        )
    }

    @Test
    fun `every snapshot type has exactly the declared hash-domain shape`() {
        assertExactShape(
            InspectionSnapshot::class.java,
            listOf(
                "id:String", "type:String", "tenancyId:String", "scheduledAt:long", "finalizedAt:Long",
                "previousInspectionId:String", "baselineInspectionId:String",
                "property:PropertySnapshot", "tenancy:TenancySnapshot", "template:TemplateSnapshot",
                "items:List", "photos:List", "audios:List",
            ),
        )
        assertExactShape(
            PropertySnapshot::class.java,
            listOf("id:String", "address:String", "kind:String", "isBoardingHouse:boolean"),
        )
        // 租客联系方式（tenant_name / contact）必须不在这里：它们若进哈希域，保留期清理一执行就会让历史
        // 报告的哈希再也复验不出来，而这份清理是 Privacy Act 2020 下的硬要求。
        assertExactShape(
            TenancySnapshot::class.java,
            listOf("id:String", "startMs:long", "endMs:Long"),
        )
        assertExactShape(
            TemplateSnapshot::class.java,
            listOf("id:String", "type:String", "version:long", "contentHash:String"),
        )
        assertExactShape(
            InspectionItemSnapshot::class.java,
            listOf("stableId:String", "status:String", "note:String", "wearOrDamage:String"),
        )
        assertExactShape(
            PhotoSnapshot::class.java,
            listOf("contentHash:String", "source:String", "exifTimeMs:Long", "isRoomLevel:boolean"),
        )
        assertExactShape(AudioSnapshot::class.java, listOf("contentHash:String"))
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
    fun `every tenancy field participates in equality`() {
        val base = TenancySnapshot(id = "t-1", startMs = 100L, endMs = 200L)
        assertNotEquals(base, base.copy(id = "t-2"))
        assertNotEquals(base, base.copy(startMs = 101L))
        assertNotEquals(base, base.copy(endMs = null))
    }

    @Test
    fun `every template field participates in equality`() {
        val base = TemplateSnapshot(id = "tv-1", type = "EXIT", version = 1, contentHash = "hash-a")
        assertNotEquals(base, base.copy(id = "tv-2"))
        assertNotEquals(base, base.copy(type = "ROUTINE"))
        assertNotEquals(base, base.copy(version = 2))
        assertNotEquals(base, base.copy(contentHash = "hash-b"))
    }

    @Test
    fun `every inspection item field participates in equality`() {
        val base = InspectionItemSnapshot(stableId = "wall.paint", status = "POOR", note = "scuffed", wearOrDamage = "DAMAGE")
        assertNotEquals(base, base.copy(stableId = "ceiling.paint"))
        assertNotEquals(base, base.copy(status = "GOOD"))
        assertNotEquals(base, base.copy(note = null))
        assertNotEquals(base, base.copy(wearOrDamage = null))
    }

    @Test
    fun `every photo field participates in equality`() {
        val base = PhotoSnapshot(contentHash = "h1", source = "CAMERA", exifTimeMs = 1L, isRoomLevel = false)
        assertNotEquals(base, base.copy(contentHash = "h2"))
        assertNotEquals(base, base.copy(source = "IMPORTED"))
        assertNotEquals(base, base.copy(exifTimeMs = null))
        assertNotEquals(base, base.copy(isRoomLevel = true))
    }

    @Test
    fun `the audio field participates in equality`() {
        val base = AudioSnapshot(contentHash = "h1")
        assertNotEquals(base, base.copy(contentHash = "h2"))
    }
}

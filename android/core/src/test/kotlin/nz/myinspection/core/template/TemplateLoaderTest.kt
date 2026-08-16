package nz.myinspection.core.template

import nz.myinspection.core.template.TemplateTestFixtures.ANNUAL_STATUSES
import nz.myinspection.core.template.TemplateTestFixtures.GOLDEN_JSON
import nz.myinspection.core.template.TemplateTestFixtures.GOLDEN_SHA256
import nz.myinspection.core.template.TemplateTestFixtures.RENTAL_STATUSES
import nz.myinspection.core.template.TemplateTestFixtures.item
import nz.myinspection.core.template.TemplateTestFixtures.routineTemplate
import nz.myinspection.core.template.TemplateTestFixtures.template
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * 加载 + 校验 + content_hash 的契约测试。
 *
 * 校验错误一律做**整串等值**断言（不是 `contains`）：错误文案本身就是内容卡 DoD 的判据面，
 * "点名了哪一条、缺什么"是被测契约的一部分；只断言"包含 stableId"的话，把域名写错、把
 * 类型名写错、把两条错误合并成一条，测试都照样绿（L165）。
 */
class TemplateLoaderTest {

    private fun load(json: String): LoadedTemplate = TemplateLoader.load(json.byteInputStream())

    private fun errorsOf(json: String): List<String> =
        assertFailsWith<TemplateValidationException> { load(json) }.errors

    @Test
    fun `load maps every template and item field`() {
        val loaded = load(routineTemplate())

        assertEquals("ROUTINE", loaded.template.type)
        assertEquals(1, loaded.template.version)
        assertEquals(listOf("KIT-BENCH-01", "KIT-ROOM-01", "BED-WALL-01"), loaded.template.items.map { it.stableId })

        val first = loaded.template.items.first()
        assertEquals("INTERIOR", first.area)
        assertEquals("KITCHEN", first.room)
        assertEquals("Bench tops and splashback", first.textEn)
        assertEquals("厨房台面与挡水板", first.textZh)
        assertEquals(listOf("GOOD", "FAIR", "POOR", "NOT_APPLICABLE"), first.allowedStatuses)
        assertEquals("ADVERSE_ONLY", first.photoRule)
        assertEquals("BEDROOM", loaded.template.items[2].room)
        assertNull(loaded.template.items[2].photoRule, "photoRule 可空：null = 该项无强制拍照要求")
    }

    @Test
    fun `content hash is the SHA-256 of the file bytes`() {
        assertEquals(GOLDEN_SHA256, load(GOLDEN_JSON).contentHash)
    }

    @Test
    fun `content hash follows the bytes, not the parsed template`() {
        val reformatted = GOLDEN_JSON.replace("\"items\":", "\"items\": ")

        assertEquals(load(GOLDEN_JSON).template, load(reformatted).template, "多一个空格不该改变解析结果")
        assertNotEquals(
            GOLDEN_SHA256,
            load(reformatted).contentHash,
            "content_hash 哈的是文件字节：同版本号下换了内容（哪怕只是重排版）必须能被看见",
        )
    }

    @Test
    fun `duplicate stable ids are rejected and the error names the duplicated id`() {
        // 第二条只改了措辞——正是"改措辞不改 id"被误用成"复制一条改文案"的形态。
        val json = template(items = listOf(item(), item(textEn = "Bench tops", textZh = "台面")))

        assertEquals(listOf("KIT-BENCH-01: duplicate stableId"), errorsOf(json))
    }

    @Test
    fun `a status outside the type's domain is rejected and the error names the item and the value`() {
        val json = template(items = listOf(item(allowedStatuses = """["GOOD","NO_ISSUE"]""")))

        assertEquals(listOf("KIT-BENCH-01: status NO_ISSUE is not allowed for template type ROUTINE"), errorsOf(json))
    }

    @Test
    fun `an item with no allowed status is rejected regardless of the template type`() {
        // 空集不是"随便填"而是"填不了"：采集时这一项没有任何合法评级可选，整条项目形同哑弹。
        assertEquals(
            listOf("KIT-BENCH-01: allowedStatuses is empty"),
            errorsOf(template(items = listOf(item(allowedStatuses = "[]")))),
        )

        // 类型拼错时只有"这个评级在不在域内"判不了；空集与类型无关，不该被类型这条错误顺带吞掉。
        assertEquals(
            listOf("template: unknown type ROUTIN", "KIT-BENCH-01: allowedStatuses is empty"),
            errorsOf(template(type = "ROUTIN", items = listOf(item(allowedStatuses = "[]")))),
        )
    }

    @Test
    fun `the allowed status domain follows the template type`() {
        // ANNUAL 认五态、不认出租四态；NOT_APPLICABLE 是两域的交集，故不在错误里出现。
        val annual = load(template(type = "ANNUAL", items = listOf(item(allowedStatuses = ANNUAL_STATUSES))))
        assertEquals(
            listOf("NO_ISSUE", "MONITOR", "MAINTENANCE_ITEM", "SIGNIFICANT_DEFECT", "NOT_APPLICABLE"),
            annual.template.items.single().allowedStatuses,
        )

        assertEquals(
            listOf(
                "KIT-BENCH-01: status GOOD is not allowed for template type ANNUAL",
                "KIT-BENCH-01: status FAIR is not allowed for template type ANNUAL",
                "KIT-BENCH-01: status POOR is not allowed for template type ANNUAL",
            ),
            errorsOf(template(type = "ANNUAL", items = listOf(item(allowedStatuses = RENTAL_STATUSES)))),
        )
    }

    @Test
    fun `a blank required field is rejected and the error names the item and the field`() {
        assertEquals(
            listOf("KIT-BENCH-01: textEn is blank"),
            errorsOf(template(items = listOf(item(textEn = "")))),
        )

        // area / room 也是 check_item_def 的 NOT NULL 列：空值会让报告里出现无名分区、
        // 建巡检时实例化不出房间，且数据库层拦不住（NOT NULL 拦得住 null，拦不住空串）。
        assertEquals(
            listOf("KIT-BENCH-01: area is blank"),
            errorsOf(template(items = listOf(item(area = "")))),
        )
        assertEquals(
            listOf("KIT-BENCH-01: room is blank"),
            errorsOf(template(items = listOf(item(room = "")))),
        )

        // 整个键漏抄（内容作者最常见的形态）：解码补空串，校验才点得到是哪一条。
        val missingTextZh =
            """{"stableId":"KIT-BENCH-01","area":"INTERIOR","room":"KITCHEN","textEn":"Bench","allowedStatuses":$RENTAL_STATUSES,"photoRule":null}"""
        assertEquals(
            listOf("KIT-BENCH-01: textZh is blank"),
            errorsOf(template(items = listOf(missingTextZh))),
        )
    }

    @Test
    fun `a blank stable id is reported by position, and that item's other defects still surface`() {
        // 同一条上再叠一个缺陷（textEn 也空）：点不了名就按位置标注，其余检查照跑——
        // 「一次报全」不能因为某条缺了身份就打折，否则作者修完这条才看见下一条。
        assertEquals(
            listOf("item[1]: stableId is blank", "item[1]: textEn is blank"),
            errorsOf(template(items = listOf(item(), item(stableId = " ", textEn = "")))),
        )
    }

    @Test
    fun `an unknown photo rule is rejected and the error names the item`() {
        assertEquals(
            listOf("KIT-BENCH-01: unknown photoRule PANORAMA"),
            errorsOf(template(items = listOf(item(photoRule = "\"PANORAMA\"")))),
        )
    }

    @Test
    fun `template-level defects are rejected`() {
        // 拼错的类型不能被当成第五类模板静默收下（同 TemplateVersion.sq 的 CHECK 之理）。
        // 类型越界时评级域无从判定，故**只**报类型这一条，不再对每条项目喷一串 status 噪音。
        assertEquals(listOf("template: unknown type ROUTIN"), errorsOf(template(type = "ROUTIN")))

        assertEquals(listOf("template: items is empty"), errorsOf(template(items = emptyList())))

        assertEquals(listOf("template: version must be >= 1"), errorsOf(template(version = 0)))
    }

    @Test
    fun `every defect is reported in one pass, in template order`() {
        val json = template(
            items = listOf(
                item(textZh = ""),
                item(stableId = "BED-WALL-01", allowedStatuses = """["MONITOR"]"""),
                item(stableId = "BED-WALL-01", photoRule = "\"PANORAMA\""),
            ),
        )

        assertEquals(
            listOf(
                "KIT-BENCH-01: textZh is blank",
                "BED-WALL-01: status MONITOR is not allowed for template type ROUTINE",
                "BED-WALL-01: duplicate stableId",
                "BED-WALL-01: unknown photoRule PANORAMA",
            ),
            errorsOf(json),
        )
    }
}

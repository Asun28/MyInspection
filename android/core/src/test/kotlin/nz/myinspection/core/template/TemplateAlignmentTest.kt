package nz.myinspection.core.template

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 版本升级对齐规则：历史只靠 stable_id 对齐，不靠名字（CLAUDE.md 关键不变量 / 需求 §4）。
 */
class TemplateAlignmentTest {

    /** `stableId to textEn` 的极简模板；对齐只看 stable_id，其余字段填成合法值即可。 */
    private fun template(type: String, version: Int, vararg items: Pair<String, String>) = Template(
        type = type,
        version = version,
        items = items.map { (stableId, textEn) ->
            TemplateItem(
                stableId = stableId,
                area = "INTERIOR",
                room = "KITCHEN",
                textEn = textEn,
                textZh = textEn,
                allowedStatuses = listOf("GOOD", "NOT_APPLICABLE"),
            )
        },
    )

    @Test
    fun `an item whose wording changed keeps its stable id and carries over`() {
        val v1 = template("ROUTINE", 1, "KIT-BENCH-01" to "Bench tops")
        val v2 = template("ROUTINE", 2, "KIT-BENCH-01" to "Bench tops and splashback")

        assertEquals(
            TemplateAlignment(carriedOver = setOf("KIT-BENCH-01"), added = emptySet(), removed = emptySet()),
            alignHistory(v1, v2),
        )
    }

    @Test
    fun `a new item id is added and a dropped one is removed`() {
        val v1 = template("ROUTINE", 1, "KIT-BENCH-01" to "Bench", "KIT-SINK-01" to "Sink")
        val v2 = template("ROUTINE", 2, "KIT-BENCH-01" to "Bench", "KIT-OVEN-01" to "Oven")

        assertEquals(
            TemplateAlignment(
                carriedOver = setOf("KIT-BENCH-01"),
                added = setOf("KIT-OVEN-01"),
                removed = setOf("KIT-SINK-01"),
            ),
            alignHistory(v1, v2),
        )
    }

    @Test
    fun `aligning across template types is rejected`() {
        // stable_id 只在同一类模板内有意义；跨类型对齐会返回"旧版全移除 + 新版全新增"这种
        // 看着成立、实则荒谬的结果，历史对比会据此把所有项都判成新增。
        val routine = template("ROUTINE", 1, "KIT-BENCH-01" to "Bench")
        val annual = template("ANNUAL", 1, "KIT-BENCH-01" to "Bench")

        assertFailsWith<IllegalArgumentException> { alignHistory(routine, annual) }
    }
}

package nz.myinspection.core.phrase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * `data/templates/phrases-v1.json` 内容完备性测试（T2-PHRASELIB DoD）。
 *
 * 语法/域校验（未知字段、分类域、评级域、必填非空、重复 en/shortcut）由 [PhraseLoader.validate]
 * 逐条覆盖，经 [loadPhrases] 转成可读 `fail`——本类只加内容卡自己的契约：种子条数、分类覆盖、
 * 查询接口在真实数据上的可用性、以及几条**逐字**内容锚点（卡片自身举的例子、shortcut 先例）。
 * 逐字断言不用子串判定：内容作者随手改一个词会被立刻发现，`contains` 挡不住"关键词还在、
 * 意思已经变了"的改动（同 `RoutineContentTest` 的理由，L165）。
 *
 * 文件由 `android/core/build.gradle.kts` 的 test resources srcDir 注册（`data/templates/`，
 * T2-ROUTINE-CONTENT 落地），走 classpath 读取，不随 Gradle 工作目录漂移。
 *
 * **独立第二模型复核记录**（卡片强制项；记录随 diff 走，因 R3 评审者只读 diff、不读 PR 描述——L227）：
 * 复核模型 DeepSeek V4 Pro（deepseek-rescue，替代默认 Luna Max，未接入本 harness 时按卡片工具无关条款
 * 允许的席位替代），2026-08-16，逐条复读全部 66 条短语（en/zh 对应关系、客观中性表述、分类归属、
 * appliesToStatuses 取值合理性、是否近似复制第三方/官方文本、跨条目重复）。发现 1 处问题并已修正：
 * `Item not present at this property` 原译文以"该物业不适用此项"重新表述成检查状态，偏离英文的物品
 * 视角，改译"本物业没有该物品"。其余 65 条无阻断问题。
 */
class PhraseLibraryContentTest {

    private fun loadPhrases(): LoadedPhraseLibrary {
        val stream = javaClass.getResourceAsStream("/phrases-v1.json")
            ?: fail("data/templates/phrases-v1.json not found on test classpath (test resources srcDir wiring broken?)")
        return try {
            PhraseLoader.load(stream)
        } catch (e: PhraseValidationException) {
            fail("phrases-v1.json failed engine validation:\n${e.errors.joinToString("\n")}")
        }
    }

    @Test
    fun `phrases-v1 json passes full engine validation`() {
        // loadPhrases() 本身即断言：validate() 非空清单会在这里 fail，带着全部错误文案
        // （含重复 en/shortcut、分类域、双语非空——engine 已判定，本类不另开重复用例）。
        loadPhrases()
    }

    @Test
    fun `library identity is pinned to version 1`() {
        assertEquals(1, loadPhrases().library.version)
    }

    @Test
    fun `seed count is at least 60`() {
        val count = loadPhrases().library.phrases.size
        assertTrue(count >= 60, "phrase count is $count, expected >= 60")
    }

    @Test
    fun `every category from the card's context package is present`() {
        val expectedCategories = setOf("condition-general", "wear", "damage", "cleaning", "action-needed", "hhc")
        val actualCategories = loadPhrases().library.phrases.map { it.category }.toSet()
        assertEquals(expectedCategories, actualCategories, "category set drifted from the card's fixed six-category domain")

        for (category in expectedCategories) {
            val count = loadPhrases().library.phrases.count { it.category == category }
            assertTrue(count > 0, "category $category has no seed phrases")
        }
    }

    @Test
    fun `the FWT shortcut expands to the card's own worked example, verbatim`() {
        // 卡片上下文包原文举的例子：shortcut "FWT" -> "Fair wear and tear / 正常损耗"。
        val byShortcut = loadPhrases().library.phrases.filter { it.shortcut != null }.associateBy { it.shortcut }
        val fwt = byShortcut["FWT"] ?: fail("no seed phrase carries the FWT shortcut")
        assertEquals("Fair wear and tear", fwt.en)
        assertEquals("正常损耗", fwt.zh)
        assertEquals("wear", fwt.category)
    }

    @Test
    fun `the requirements doc's worked wear example is present, verbatim`() {
        // 需求文档 §7 与本卡上下文包原文引用的例子："Minor wear consistent with age of property /
        // 轻微磨损，与房龄相符"——种子内容里若漏收或被改写，这条断言会直接失败。
        val phrases = loadPhrases().library.phrases
        val match = phrases.firstOrNull { it.en == "Minor wear consistent with age of property" }
            ?: fail("the requirements doc's worked example phrase is missing")
        assertEquals("轻微磨损，与房龄相符", match.zh)
    }

    @Test
    fun `hhc phrases do not reproduce the official checklist's rating labels`() {
        // MB_TEN8271 官方 checklist 的评级标签是二值 "Room to improve" / "You're on track"（受保护的
        // 官方表达）。本卡的 hhc 短语是独立撰写的客观状况描述，不得抄这两个标签——即便是内容后续
        // 被顺手"简化"成官方措辞，这条断言也会当场拦住。
        val hhcTexts = loadPhrases().library.phrases.filter { it.category == "hhc" }
            .flatMap { listOf(it.en, it.zh) }
        for (text in hhcTexts) {
            assertFalse(text.contains("Room to improve"), "hhc phrase reproduces the official checklist label verbatim: $text")
            assertFalse(text.contains("You're on track"), "hhc phrase reproduces the official checklist label verbatim: $text")
        }
    }

    @Test
    fun `phrasesFor and suggestFor work against the real seed content`() {
        val loaded = loadPhrases()

        val wearPhrases = loaded.phrasesFor("wear")
        assertTrue(wearPhrases.isNotEmpty(), "wear category should have seed phrases")
        assertTrue(wearPhrases.any { it.shortcut == "FWT" }, "FWT-shortcut phrase should surface via phrasesFor(\"wear\")")

        // "KIT-FRIDGE-01" is a real stableId from data/templates/routine-v1.json (T2-ROUTINE-CONTENT):
        // suggestFor doesn't look stableId up (see its KDoc — no item->category mapping exists in
        // this card's scope), but a content-completeness test should still call it with an id that
        // genuinely identifies a real check item, not an arbitrary placeholder string.
        val fairSuggestions = loaded.suggestFor("KIT-FRIDGE-01", "FAIR")
        assertTrue(fairSuggestions.isNotEmpty(), "FAIR should have at least one suggested phrase")
        assertTrue(
            fairSuggestions.all { it.appliesToStatuses == null || "FAIR" in it.appliesToStatuses!! },
            "every suggested phrase must be universal or explicitly applicable to FAIR",
        )
        assertFalse(
            fairSuggestions.any { it.appliesToStatuses != null && "FAIR" !in it.appliesToStatuses!! },
            "no phrase restricted to a different status should be suggested for FAIR",
        )
        // v1 只按 status 过滤，不按分类：cleaning 短语都是 appliesToStatuses=null（清洁与状况评级
        // 是两条独立轴，需求 synthesis #3），故理应与其它通用短语一起出现在 FAIR 结果里；
        // hhc 短语全部限定 GOOD（正面结论类文案），理应被 FAIR 查询排除——用真实数据钉住两头。
        assertTrue(fairSuggestions.any { it.category == "cleaning" }, "cleaning phrases are universal and must surface for FAIR too")
        assertTrue(fairSuggestions.none { it.category == "hhc" }, "GOOD-only hhc phrases must not surface for FAIR")
    }

    @Test
    fun `a phrase that presumes a positive verdict is not suggested for an adverse status`() {
        // "No issues noted" 的措辞本身就是一个 GOOD 结论；appliesToStatuses 若漏标（或被误删），
        // 它会跟着任何评级一起被推荐——包括 POOR，产生自相矛盾的建议。同理扫过全部种子内容
        // （condition-general 的 "Not accessible..." 与全部 8 条 hhc 正面表述），本用例只需钉住
        // 最容易被复发触碰的一条：suggestFor 的真实输出里，"No issues noted" 不得出现在 POOR 结果中。
        val poorSuggestions = loadPhrases().suggestFor("KIT-FRIDGE-01", "POOR")
        assertTrue(
            poorSuggestions.none { it.en == "No issues noted" },
            "a phrase declaring no issues must not surface as a suggestion for a POOR item",
        )
    }
}

package nz.myinspection.core.phrase

import nz.myinspection.core.phrase.PhraseTestFixtures.library
import nz.myinspection.core.phrase.PhraseTestFixtures.phrase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 查询接口契约测试（卡片产出：`phrasesFor(category)` + `suggestFor(stableId, status)`）。
 *
 * 与 [PhraseLoaderTest] 分开：那份测引擎（解析/校验），这份测查询语义——过滤条件、排序、
 * 参数校验。断言全部用整串 `en` 文案列表等值，不用 `contains`/size 判定：过滤逻辑漏判一条
 * 或多判一条，size 断言可能因为另一条同时漏判/多判而"凑巧对上"，逐条等值断言不会（同 L165）。
 */
class PhraseQueryTest {

    private fun load(json: String): LoadedPhraseLibrary = PhraseLoader.load(json.byteInputStream())

    @Test
    fun `phrasesFor returns only phrases in that category, sorted by sort`() {
        val loaded = load(
            library(
                phrases = listOf(
                    phrase(en = "wear-b", category = "wear", sort = 2, appliesToStatuses = null, shortcut = null),
                    phrase(en = "damage-a", category = "damage", sort = 0, appliesToStatuses = null, shortcut = null),
                    phrase(en = "wear-a", category = "wear", sort = 1, appliesToStatuses = null, shortcut = null),
                ),
            ),
        )

        assertEquals(listOf("wear-a", "wear-b"), loaded.phrasesFor("wear").map { it.en })
        assertEquals(listOf("damage-a"), loaded.phrasesFor("damage").map { it.en })
    }

    @Test
    fun `phrasesFor preserves array order among phrases with equal sort`() {
        // 三条同分类、同 sort=0：唯一区分它们相对顺序的就是原数组序（stable sort 保证）。
        val loaded = load(
            library(
                phrases = listOf(
                    phrase(en = "first", category = "cleaning", sort = 0, appliesToStatuses = null, shortcut = null),
                    phrase(en = "second", category = "cleaning", sort = 0, appliesToStatuses = null, shortcut = null),
                    phrase(en = "third", category = "cleaning", sort = 0, appliesToStatuses = null, shortcut = null),
                ),
            ),
        )

        assertEquals(listOf("first", "second", "third"), loaded.phrasesFor("cleaning").map { it.en })
    }

    @Test
    fun `phrasesFor rejects an unknown category instead of silently returning empty`() {
        val loaded = load(library())
        assertFailsWith<IllegalArgumentException> { loaded.phrasesFor("not-a-real-category") }
    }

    @Test
    fun `suggestFor filters by status across every category, universal phrases always included`() {
        val loaded = load(
            library(
                phrases = listOf(
                    phrase(en = "fair-only", category = "wear", sort = 0, appliesToStatuses = """["FAIR"]""", shortcut = null),
                    phrase(en = "poor-only", category = "damage", sort = 0, appliesToStatuses = """["POOR"]""", shortcut = null),
                    phrase(en = "universal", category = "condition-general", sort = 0, appliesToStatuses = null, shortcut = null),
                ),
            ),
        )

        // 排序按 (category, sort)：categories 按字典序为 condition-general < damage < wear,
        // 故两次查询里 "universal"（condition-general）都排在前面。
        assertEquals(listOf("universal", "fair-only"), loaded.suggestFor("ITEM-1", "FAIR").map { it.en })
        assertEquals(listOf("universal", "poor-only"), loaded.suggestFor("ITEM-1", "POOR").map { it.en })
    }

    @Test
    fun `suggestFor includes cleaning and hhc phrases like any other category (v1 filters by status only)`() {
        // v1 契约只有一个过滤维度——status；分类不参与过滤，故 cleaning/hhc 的短语与其它分类
        // 一视同仁：appliesToStatuses 为 null 即任意评级入选，非 null 就按值匹配。
        val loaded = load(
            library(
                phrases = listOf(
                    phrase(en = "condition-universal", category = "condition-general", sort = 0, appliesToStatuses = null, shortcut = null),
                    phrase(en = "cleaning-universal", category = "cleaning", sort = 0, appliesToStatuses = null, shortcut = null),
                    phrase(en = "hhc-good-only", category = "hhc", sort = 0, appliesToStatuses = """["GOOD"]""", shortcut = null),
                ),
            ),
        )

        // 字典序 cleaning < condition-general < hhc；GOOD 查询三条全中，FAIR 查询排除 hhc-good-only。
        assertEquals(
            listOf("cleaning-universal", "condition-universal", "hhc-good-only"),
            loaded.suggestFor("ITEM-1", "GOOD").map { it.en },
        )
        assertEquals(listOf("cleaning-universal", "condition-universal"), loaded.suggestFor("ITEM-1", "FAIR").map { it.en })
    }

    @Test
    fun `suggestFor orders results by category then sort`() {
        val loaded = load(
            library(
                phrases = listOf(
                    phrase(en = "wear-1", category = "wear", sort = 1, appliesToStatuses = null, shortcut = null),
                    phrase(en = "damage-1", category = "damage", sort = 5, appliesToStatuses = null, shortcut = null),
                    phrase(en = "wear-0", category = "wear", sort = 0, appliesToStatuses = null, shortcut = null),
                ),
            ),
        )

        assertEquals(
            listOf("damage-1", "wear-0", "wear-1"),
            loaded.suggestFor("ITEM-1", "FAIR").map { it.en },
        )
    }

    @Test
    fun `suggestFor rejects a blank stableId`() {
        val loaded = load(library())
        assertFailsWith<IllegalArgumentException> { loaded.suggestFor("  ", "FAIR") }
    }

    @Test
    fun `suggestFor rejects a blank status`() {
        val loaded = load(library())
        assertFailsWith<IllegalArgumentException> { loaded.suggestFor("ITEM-1", "") }
    }

    @Test
    fun `suggestFor rejects a status outside the recognized rating domain instead of silently returning universal phrases only`() {
        // 拼错的评级值（如 "GOOD " 多个空格，或 "EXCELLENT" 这类不存在的等级）不该静默通过并
        // 只返回通用短语——那样调用方会以为过滤生效了，实则每一条 appliesToStatuses 限定的短语
        // 都被误判为不匹配。
        val loaded = load(library())
        assertFailsWith<IllegalArgumentException> { loaded.suggestFor("ITEM-1", "EXCELLENT") }
    }
}

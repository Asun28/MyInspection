package nz.myinspection.core.phrase

/**
 * 本卡引擎测试的合成 fixture（真实内容归 `data/templates/phrases-v1.json`，由
 * [PhraseLibraryContentTest] 单独覆盖）。
 *
 * 刻意拼字符串而不是落文件：每个坏 fixture 只与好 fixture 差**一处**，断言面因此恰好等于被测的
 * 那条规则（L165），理由同 `TemplateTestFixtures`。[phrase] 收原始 JSON 片段（不是值），
 * 这样 `null`、非法类型这些坏形态才表达得出来。
 */
internal object PhraseTestFixtures {
    fun phrase(
        en: String = "Fair wear and tear",
        zh: String = "正常损耗",
        category: String = "wear",
        sort: Int = 0,
        appliesToStatuses: String? = """["FAIR"]""",
        shortcut: String? = "\"FWT\"",
    ): String {
        val fields = mutableListOf(
            "\"en\":\"$en\"",
            "\"zh\":\"$zh\"",
            "\"category\":\"$category\"",
            "\"sort\":$sort",
        )
        if (appliesToStatuses != null) fields += "\"appliesToStatuses\":$appliesToStatuses"
        if (shortcut != null) fields += "\"shortcut\":$shortcut"
        return "{${fields.joinToString(",")}}"
    }

    fun library(version: Int = 1, phrases: List<String> = listOf(phrase())): String =
        """{"version":$version,"phrases":[${phrases.joinToString(",")}]}"""

    /** 好 fixture：3 条、3 个分类、appliesToStatuses/shortcut 各种取值都出现一次。 */
    fun smallLibrary(): String = library(
        phrases = listOf(
            phrase(),
            phrase(
                en = "No issues noted",
                zh = "未见异常",
                category = "condition-general",
                sort = 1,
                appliesToStatuses = null,
                shortcut = null,
            ),
            phrase(
                en = "Stain observed on the surface",
                zh = "表面发现污渍",
                category = "damage",
                sort = 2,
                appliesToStatuses = """["POOR"]""",
                shortcut = null,
            ),
        ),
    )
}

package nz.myinspection.core.phrase

import kotlinx.serialization.Serializable
import nz.myinspection.core.template.TemplateDomains
import java.util.Collections

/**
 * 短语库 JSON 的数据类即 schema（卡片 T2-PHRASELIB）：`{ "version": 1, "phrases": [ { "en": …, … } ] }`。
 *
 * 短语库与检查项模板是两份独立演进的文件（卡片 forbid：短语不得进模板混编），故不复用 [Template]/[TemplateItem]
 * 的形状——短语没有 `stableId` 这类历史对齐身份（一条短语被撤下不影响任何历史巡检记录），标识只是
 * 内容本身（[Phrase.en] 是否重复）与可选的 [Phrase.shortcut]。
 */
@Serializable
data class PhraseLibrary(
    val version: Int,
    val phrases: List<Phrase>,
)

/**
 * 一条短语。字段全部给了空/null 默认值（同 [nz.myinspection.core.template.TemplateItem] 的理由）：
 * 内容作者漏抄一个键时，kotlinx 解码不该抛一个只报字段名、报不出是第几条的 `MissingFieldException`——
 * 由 [PhraseLoader.validate] 统一按位置点名。
 */
@Serializable
data class Phrase(
    val en: String = "",
    val zh: String = "",
    /** 六选一封闭域，见 [PhraseDomains.CATEGORIES]。 */
    val category: String = "",
    /** 同分类内的显示序（`phrase_entry.sort`，DB 层显式列，见该表 `ORDER BY sort ASC, id ASC` 注释）。 */
    val sort: Int = 0,
    /** 可空：该短语只在这些评级下推荐（如 wear 类只在 FAIR）；null = 不限评级，任何状态都可用。 */
    val appliesToStatuses: List<String>? = null,
    /** 可空：输入快捷键（如 "FWT" → 展开为整条短语），备注输入框命中即展开（UI 消费见 T2-CAPTURE-UI）。 */
    val shortcut: String? = null,
)

/**
 * 短语库的封闭域真相源。
 *
 * 六个分类是卡片上下文包定义的固定集合，不从别处派生。评级域**复用** [TemplateDomains]
 * 的出租/年检两个集合的并集——短语不像模板项那样绑定单一模板类型（同一条短语可能在 ROUTINE
 * 与 ANNUAL 里都用得上），故 [appliesToStatuses] 的合法值取两域之并，而不是另立一份重复的枚举
 * 清单（重复清单 = 两处各改一半的漂移源）。
 */
object PhraseDomains {
    val CATEGORIES: Set<String> = Collections.unmodifiableSet(
        linkedSetOf("condition-general", "wear", "damage", "cleaning", "action-needed", "hhc"),
    )

    val STATUSES: Set<String> = Collections.unmodifiableSet(
        LinkedHashSet(TemplateDomains.RENTAL_STATUSES + TemplateDomains.ANNUAL_STATUSES),
    )
}

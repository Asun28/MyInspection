package nz.myinspection.core.template

import kotlinx.serialization.Serializable

/**
 * 模板 JSON 的数据类即 schema（卡片 T1-TEMPLATE-ENGINE）。一类型一文件，形如：
 * `{ "type": "ROUTINE", "version": 1, "items": [ { "stableId": …, … } ] }`
 *
 * **字段类型刻意用 String 而不是 enum**：enum 会让 kotlinx 在解码期就对第一个越界值抛
 * `SerializationException`，错误信息只有字段名、点不到是哪一条模板项，而且**一次只报一个**。
 * 内容卡（T2-ROUTINE-CONTENT / T6-TEMPLATES-REST）有 80–120 条项目，其 DoD 直接拿本模块的
 * 校验器当闸门，作者需要「一次跑出全部问题、每条点名 stableId」——故封闭域一律在
 * [TemplateLoader.validate] 里判，见 [TemplateDomains]。
 *
 * [items] 的**数组顺序即模板序**：入库时投影成 `check_item_def.sort`（0 起），报告按它排版，
 * canonical 哈希的 items[] 也按它定序（见 InspectionItem.sq 的 selectByInspectionInTemplateOrder
 * 注释）。改顺序 = 改内容，须走新 version。
 */
@Serializable
data class Template(
    val type: String,
    val version: Int,
    val items: List<TemplateItem>,
)

/**
 * 一条检查项定义。除 [stableId] 外的必填字段都给了空默认值：内容作者最常见的坏形态是**整个键漏抄**，
 * 若声明成无默认值的必填字段，kotlinx 会在解码期抛 `MissingFieldException`，只报字段名、**报不出是哪一条**。
 * 给空默认值后解码照常通过，由 [TemplateLoader.validate] 统一报「KIT-BENCH-01: textZh is blank」。
 *
 * [stableId] 是唯一没有默认值的字段——它缺失时本就无从「点名条目」，让解码当场失败反而更清楚。
 */
@Serializable
data class TemplateItem(
    /** ★历史对齐唯一键：模板内唯一、跨版本恒定（改措辞不改 id，加项给新 id；需求 §4）。 */
    val stableId: String,
    /** 报告分区（如 INTERIOR / EXTERIOR / GROUNDS），落 `check_item_def.area`。 */
    val area: String = "",
    /** 模板层房间键（如 KITCHEN / BEDROOM），建巡检时实例化成 room_instance；落 `check_item_def.room`。 */
    val room: String = "",
    val textEn: String = "",
    val textZh: String = "",
    /** 该项允许的评级枚举集合，须落在本模板 type 的域内（[TemplateDomains.allowedStatusesFor]）。 */
    val allowedStatuses: List<String> = emptyList(),
    /** ROOM_PANORAMA / ADVERSE_ONLY / null（无强制拍照要求），域同 `check_item_def` 的 CHECK。 */
    val photoRule: String? = null,
)

/** 解析结果：模板文档 + 该文件**字节**的 SHA-256（入 `template_version.content_hash`）。 */
data class LoadedTemplate(
    val template: Template,
    val contentHash: String,
)

/**
 * 模板里各封闭域的真相源。这些集合与 SQLDelight schema 的 CHECK 约束**必须一致**——
 * `template_version.type`、`check_item_def.photo_rule` 各有 CHECK，写进去才不会被数据库拒；
 * 评级域 schema 刻意不约束（`inspection_item.status` 无 CHECK，合法值随模板类型而变），
 * 所以它只在这里成文，是 :core 层唯一的判据。
 */
object TemplateDomains {
    /** 出租三类模板的四档评级（需求 §4 / 卡片上下文包）。 */
    val RENTAL_STATUSES: Set<String> = setOf("GOOD", "FAIR", "POOR", "NOT_APPLICABLE")

    /** 年检模板的五态评级（TASK-BOARD「用户已定」第 4 条）。 */
    val ANNUAL_STATUSES: Set<String> = setOf("NO_ISSUE", "MONITOR", "MAINTENANCE_ITEM", "SIGNIFICANT_DEFECT", "NOT_APPLICABLE")

    /** 拍照规则封闭域，与 `check_item_def.photo_rule` 的 CHECK 同集（NULL 另表示"无要求"）。 */
    val PHOTO_RULES: Set<String> = setOf("ROOM_PANORAMA", "ADVERSE_ONLY")

    /**
     * 该模板类型允许的评级集合；类型本身越界时返回 null（判不了域，调用方须跳过评级检查）。
     * 这个 when 同时就是**模板类型的封闭域**——不另立一份类型清单，免得两处各改一半。
     */
    fun allowedStatusesFor(type: String): Set<String>? = when (type) {
        "ROUTINE", "INGOING", "EXIT" -> RENTAL_STATUSES
        "ANNUAL" -> ANNUAL_STATUSES
        else -> null
    }
}

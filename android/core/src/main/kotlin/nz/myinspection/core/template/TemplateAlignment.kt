package nz.myinspection.core.template

/**
 * 两个模板版本按 stable_id 的对齐结果（历史对比 T3-HISTORY-COMPARE 的基础设施）。
 *
 * 三个集合都保持**确定的迭代顺序**（[carriedOver]/[added] 按新版模板序，[removed] 按旧版模板序）：
 * 消费方按序渲染时不会因集合实现而漂移。
 */
data class TemplateAlignment(
    /** 两版都有的 stable_id——即"改了措辞但还是同一条"，历史值可直接沿用。 */
    val carriedOver: Set<String>,
    /** 只在新版出现的 stable_id：新增项，历史侧无对应值。 */
    val added: Set<String>,
    /** 只在旧版出现的 stable_id：已移除项，新报告不再有它，但历史数据仍须能解释。 */
    val removed: Set<String>,
)

/**
 * 按 stable_id 对齐两个模板版本。
 *
 * 卡片写作 `alignHistory(old: TemplateVersion, new: TemplateVersion)`；此处参数是**解析后的模板文档**
 * [Template]（`TemplateVersion` 这个名字属于 SQLDelight 由 `template_version` 表生成的行类型，
 * 两者别混）。
 *
 * 要求两版**同 type**：stable_id 只在同一类模板内有意义（ROUTINE 的 `KIT-BENCH-01` 与 ANNUAL 的
 * 同名项不是同一条），跨类型对齐会静默返回「旧版全移除 + 新版全新增」这种看似成立、实则荒谬的结果。
 */
fun alignHistory(old: Template, new: Template): TemplateAlignment {
    require(old.type == new.type) {
        "cannot align templates of different types: ${old.type} vs ${new.type}"
    }
    val oldIds = old.items.map { it.stableId }
    val newIds = new.items.map { it.stableId }
    val oldIdSet = oldIds.toSet()
    val newIdSet = newIds.toSet()
    // 先 filter 再 toSet：得到的是 LinkedHashSet，迭代序 = 各自的模板序（见类注释的确定性要求）。
    return TemplateAlignment(
        carriedOver = newIds.filter { it in oldIdSet }.toSet(),
        added = newIds.filterNot { it in oldIdSet }.toSet(),
        removed = oldIds.filterNot { it in newIdSet }.toSet(),
    )
}

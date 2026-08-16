package nz.myinspection.core.finalize

/**
 * finalize 只读强制在**用例层**的第二道闸（卡文：「只读强制已在 SQL 层预埋…本卡在用例层再挡一道」）。
 * SQL 层的谓词（各 `.sq` 文件的 `finalized_at IS NULL` 守卫）命中时只返回**受影响行数 0**——那是给
 * SQLDelight 生成 API 的通用约定，本身不是"错误"，调用方若忘了检查返回值，一次静默失败的写就会被
 * 当作成功处理。这个函数把"0 行"这一约定翻译成一次**显式抛出**的异常，让忘记检查返回值不再是一个
 * 选项——用例层调用方（:app、以及未来的 T2-CAPTURE-CORE）应当把每一次对原始条目的写路径都过一遍这层。
 *
 * 只在这一处失败即抛：真正合法的路径（DRAFT 巡检下的正常写）`affected` 恒为 1，这个函数对它们完全
 * 透明。
 */
fun requireOriginalEntryWritten(affected: Long, description: String) {
    if (affected != 1L) {
        throw FinalizedInspectionReadOnlyException(
            "$description affected $affected rows — the inspection is FINALIZED (or the target row no longer exists); " +
                "original entries are read-only after finalize (only Supplement is append-only).",
        )
    }
}

/** [requireOriginalEntryWritten] 命中时抛出的显式错误。 */
class FinalizedInspectionReadOnlyException(message: String) : IllegalStateException(message)

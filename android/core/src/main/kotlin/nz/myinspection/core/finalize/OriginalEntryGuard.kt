package nz.myinspection.core.finalize

/**
 * finalize 只读强制在用例层的第二道闸（卡文：「只读强制已在 SQL 层预埋…本卡在用例层再挡一道」）。
 * SQL 层谓词命中时只返回受影响行数 0——那是 SQLDelight 生成 API 的通用约定，不是"错误"，调用方若
 * 忘了检查返回值，一次静默失败的写就会被当作成功处理。这个函数把"非 1 行"翻译成一次显式抛出的异常。
 *
 * 不咬定具体原因：`affected == 0` 既可能因为父巡检已 FINALIZED（本包的谓词只挡这一类），也可能因为
 * `id` 指向的行根本不存在——两者从受影响行数本身分辨不出来，异常消息如实说"写被拒绝"而不是替调用方
 * 猜一个具体原因。
 */
fun requireOriginalEntryWritten(affected: Long, description: String) {
    if (affected != 1L) {
        throw OriginalEntryWriteRejectedException(
            "$description affected $affected rows (expected 1) — the write was rejected. " +
                "Original entries are read-only once the parent inspection is FINALIZED (only Supplement " +
                "is append-only); affected=0 may also mean the target row does not exist.",
        )
    }
}

/** [requireOriginalEntryWritten] 命中时抛出的显式错误。 */
class OriginalEntryWriteRejectedException(message: String) : IllegalStateException(message)

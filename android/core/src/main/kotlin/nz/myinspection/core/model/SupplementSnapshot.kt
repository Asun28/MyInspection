package nz.myinspection.core.model

/**
 * `T1-CANON-HASH` 的 `supplementChainHash(prev: String, s: SupplementSnapshot): String` 输入形状——
 * `chain_hash(n) = SHA-256(canonical(supplement_n) + prev_hash)`。
 *
 * 只带链条真正需要哈希进去的内容：`createdAt` + `text`。**不带** `id`（随机 UUID，与内容无关，
 * 不该参与哈希）、**不带** `inspectionId`（链已经是"某次巡检下"的链，上下文冗余）、**不带**
 * `prevHash`（那是链的输入参数、不是这一条自身的内容，函数签名里单独作为 `prev` 传入）。
 */
data class SupplementSnapshot(
    val createdAt: Long,
    val text: String,
)

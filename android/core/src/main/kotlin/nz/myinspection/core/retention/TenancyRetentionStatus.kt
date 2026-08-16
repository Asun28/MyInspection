package nz.myinspection.core.retention

import nz.myinspection.core.db.Tenancy

/** 设置页展示用的每-tenancy 保留状态；`purged_at` 存在与否优先于到期比较（一旦清理，状态恒为 PURGED）。 */
enum class ContactRetentionState {
    /** 租约尚未结束（`end_ms` 为 NULL）——保留期尚未开始计时。 */
    ACTIVE_TENANCY,

    /** 租约已结束，联系方式仍在配置的清理策略窗口内（见 [CONTACT_RETENTION_MONTHS]）。 */
    AWAITING_EXPIRY,

    /** 保留期已过、联系方式尚未清理——可执行一键清理。 */
    PURGEABLE,

    /** 联系方式已清理（`tenant_name`/`contact` 均为 NULL，`purged_at` 已记录）。 */
    PURGED,
}

/**
 * 单个 tenancy 的保留状态投影。`tenantName`/`contact` 只为设置页展示 + 清理前的 type-to-confirm
 * 校验携带——与 T1-CANON-HASH 的哈希域无关，那份排除只发生在 [nz.myinspection.core.model.TenancySnapshot]。
 */
data class TenancyRetentionStatus(
    val tenancyId: String,
    val propertyId: String,
    val tenancyEndMs: Long?,
    val contactExpiresAtMs: Long?,
    val purgedAtMs: Long?,
    val tenantName: String?,
    val contact: String?,
    val state: ContactRetentionState,
) {
    val isPurgeable: Boolean get() = state == ContactRetentionState.PURGEABLE
}

/** 单条 DB 行 → 状态投影的纯函数；`nowMs` 由调用方注入的时钟提供，不直接读系统时钟（可测性）。 */
fun statusOf(row: Tenancy, nowMs: Long): TenancyRetentionStatus {
    val endMs = row.end_ms
    val state = when {
        row.purged_at != null -> ContactRetentionState.PURGED
        endMs == null -> ContactRetentionState.ACTIVE_TENANCY
        nowMs >= contactExpiryMs(endMs) -> ContactRetentionState.PURGEABLE
        else -> ContactRetentionState.AWAITING_EXPIRY
    }
    return TenancyRetentionStatus(
        tenancyId = row.id,
        propertyId = row.property_id,
        tenancyEndMs = endMs,
        contactExpiresAtMs = endMs?.let { contactExpiryMs(it) },
        purgedAtMs = row.purged_at,
        tenantName = row.tenant_name,
        contact = row.contact,
        state = state,
    )
}

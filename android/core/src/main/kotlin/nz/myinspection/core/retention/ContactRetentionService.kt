package nz.myinspection.core.retention

import nz.myinspection.core.db.ClockMs
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.SystemClockMs
import java.util.Collections

/**
 * 联系方式保留期清理用例：读到期状态 + 执行一键清理。DB 侧机械行为（`purgeContactInfo` 本身的
 * NULL 守卫、行不删除）已由 Tenancy.sq 冻结查询与 T1-SCHEMA-CORE 的 DbInvariantsTest/
 * DbDownstreamQueriesTest 钉住，本类只负责"何时允许清理"的业务判断。
 */
class ContactRetentionService(
    private val db: MyInspectionDatabase,
    private val clock: ClockMs = SystemClockMs,
) {

    /**
     * 全部活跃 tenancy 的保留状态，供设置页展示。`selectActive` 是冻结查询、无 ORDER BY——按
     * tenancyId（UUIDv7，单调）排序钉定序，不把 SQLite 的物理返回序当成契约。
     */
    fun listStatuses(): List<TenancyRetentionStatus> {
        val now = clock.nowMs()
        val sorted = db.tenancyQueries.selectActive().executeAsList()
            .map { statusOf(it, now) }
            .sortedBy { it.tenancyId }
        // sortedBy 的返回值只挡结构性改动（add/remove/clear）——底层要么是定长的 Arrays.asList()
        // 视图，要么（单元素时）是 singletonList，两者都仍放行 .set() 原地换元素。不裹一层，调用方
        // 转回 MutableList 后 `.set(i, 别的 status)` 会静默成功（本仓已知的一类缺陷，同
        // TemplateStore.read() 对读回集合的处理）。
        return Collections.unmodifiableList(sorted)
    }

    /**
     * 清理一个 tenancy 的联系方式（`tenant_name`/`contact` -> NULL），标记 `purged_at`，绝不 DELETE
     * 行——巡检/照片/报告哈希链是法定证据，不受本操作影响（T1-CANON-HASH 哈希域本就不含联系方式）。
     *
     * 读（当前状态）与写（UPDATE）落在同一事务内：业务前提不满足时直接抛出对应的 [ContactPurgeRejected]
     * 子类，事务随异常自动回滚——这条路径尚未发生任何写入，故不需要 `rollback(value)`（那是"先写后判断"
     * 场景才要的收尾）。
     */
    fun purge(tenancyId: String): TenancyRetentionStatus = db.transactionWithResult {
        val now = clock.nowMs()
        // Privacy expiry applies to historical rows too; this is deliberately not an active-only read.
        val row = db.tenancyQueries.selectAnyById(tenancyId).executeAsOneOrNull()
            ?: throw ContactPurgeRejected.TenancyNotFound(tenancyId)
        val purgedAt = row.purged_at
        if (purgedAt != null) {
            throw ContactPurgeRejected.AlreadyPurged(tenancyId, purgedAt)
        }
        val endMs = row.end_ms ?: throw ContactPurgeRejected.TenancyNotEnded(tenancyId)
        val expiresAtMs = contactExpiryMs(endMs)
        if (now < expiresAtMs) {
            throw ContactPurgeRejected.RetentionPeriodNotElapsed(tenancyId, expiresAtMs, now)
        }

        // 前提刚在本事务内核验过（行存在、未清理过、已过期），单连接下这条 UPDATE 必然精确命中
        // 这一行——不加 affected==1 的事后检查：那样的检查在这条路径上永远不可能翻红，是一枚
        // 没有测试能让它触发的死代码（同 T1-TEMPLATE-ENGINE R3 抓到的那类缺陷）。多连接下的
        // enlistment 语义另有 TD10 追踪，不在本卡处理范围。
        db.tenancyQueries.purgeContactInfo(purged_at = now, updated_at = now, id = tenancyId)

        statusOf(db.tenancyQueries.selectAnyById(tenancyId).executeAsOne(), now)
    }
}

/**
 * 清理被拒绝的具体原因，供 UI 分流展示——都是业务规则判断，不是运行期偶发故障，重试不会自愈
 * （须等到期或本就是误触），故不归入 retryable 分类。
 */
sealed class ContactPurgeRejected(message: String) : IllegalStateException(message) {
    class TenancyNotFound(val tenancyId: String) :
        ContactPurgeRejected("no such tenancy: $tenancyId")

    class TenancyNotEnded(val tenancyId: String) :
        ContactPurgeRejected(
            "tenancy $tenancyId has not ended yet (end_ms is NULL) — contact retention only starts counting after tenancy end",
        )

    class RetentionPeriodNotElapsed(val tenancyId: String, val expiresAtMs: Long, val nowMs: Long) :
        ContactPurgeRejected("tenancy $tenancyId retention period has not elapsed: expires at $expiresAtMs, now is $nowMs")

    class AlreadyPurged(val tenancyId: String, val purgedAtMs: Long) :
        ContactPurgeRejected("tenancy $tenancyId contact info was already purged at $purgedAtMs")
}

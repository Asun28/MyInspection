package nz.myinspection.core.retention

import nz.myinspection.core.db.Tenancy
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [contactExpiryMs] 与 [statusOf] 是纯函数（不碰 DB），到期计算与状态派生的契约在这里钉死；
 * DB 往返/事务行为的测试在 [ContactRetentionServiceTest]，端到端哈希不变量在
 * [ContactRetentionCanonInvarianceTest]——三份各测一层，不重复。
 */
class ContactRetentionPolicyTest {

    private fun row(
        id: String = "t-1",
        propertyId: String = "p-1",
        endMs: Long? = null,
        purgedAt: Long? = null,
    ) = Tenancy(
        id = id, property_id = propertyId, start_ms = 0L, end_ms = endMs,
        tenant_name = "J Doe", contact = "j@example.com", baseline_inspection_id = null,
        purged_at = purgedAt, created_at = 0L, updated_at = 0L, deleted_at = null,
    )

    // 以下三条金值独立算自 .NET `TimeZoneInfo`（"New Zealand Standard Time"），与生产实现走的
    // java.time/tzdata 是两条独立路径的交叉复算（同 T1-CANON-HASH 黄金向量法），不是拿实现自证实现。

    @Test
    fun `expiry is exactly 12 calendar months later in NZ civil time, in a season with no DST either side`() {
        // 2023-06-15T00:00:00 NZST（冬季，UTC+12）+ 12 个月 = 2024-06-15T00:00:00 NZST（同季节、同偏移，
        // 隔离出「纯 12 个月」这一条契约，不与 DST 混在一起断言）。
        val end = 1_686_744_000_000L // 2023-06-15T00:00:00 NZST
        val expected = 1_718_366_400_000L // 2024-06-15T00:00:00 NZST
        assertEquals(expected, contactExpiryMs(end))
    }

    @Test
    fun `expiry clamps Feb 29 to Feb 28 across a real 12-month leap-year boundary`() {
        // 2024（闰年）2024-02-29T00:00:00 NZDT + 12 个月：2025 不是闰年，2 月只有 28 天，java.time 的
        // plusMonths 夹紧到月末而非溢出到 3 月 1 日——必须走真 12 个月跨界，1 个月的替代路径测不出
        // 「12 个月」这个契约本身是否也正确夹紧（闰年偏移量与 1 个月不同，二者不是同一条断言）。
        val feb29_2024 = 1_709_118_000_000L // 2024-02-29T00:00:00 NZDT
        val feb28_2025 = 1_740_654_000_000L // 2025-02-28T00:00:00 NZDT（夹紧，非溢出到 3 月 1 日）
        assertEquals(feb28_2025, contactExpiryMs(feb29_2024))
    }

    @Test
    fun `expiry crosses the NZDT to NZST daylight-saving transition at the correct civil instant`() {
        // 2020-04-04T12:00 NZDT（当年 DST 4-05 才结束，此刻仍 UTC+13）+ 12 个月，civil 上该落在
        // 2021-04-04T12:00——但 2021 年 DST 已于当天凌晨结束，12:00 那一刻已是 NZST（UTC+12）。
        // 用真实时区（Pacific/Auckland）算日历月会自动按目标日期重新解出正确偏移，保持本地墙钟时刻
        // 12:00 不变；若退化成 ZoneOffset.UTC（没有夏令时），机械保持 UTC 墙钟时刻不变，会偏出 1 小时。
        val start = 1_585_954_800_000L // 2020-04-04T12:00:00 NZDT (UTC+13)
        val expected = 1_617_494_400_000L // 2021-04-04T12:00:00 NZST (UTC+12)
        assertEquals(expected, contactExpiryMs(start))
    }

    @Test
    fun `an ongoing tenancy with no end date is ACTIVE_TENANCY regardless of clock`() {
        val status = statusOf(row(endMs = null), nowMs = Long.MAX_VALUE)
        assertEquals(ContactRetentionState.ACTIVE_TENANCY, status.state)
        assertEquals(null, status.contactExpiresAtMs)
        assertEquals(false, status.isPurgeable, "ACTIVE_TENANCY must never show the purge button")
    }

    @Test
    fun `a tenancy inside the contact retention window is AWAITING_EXPIRY`() {
        val end = 1_700_000_000_000L
        val expiresAt = contactExpiryMs(end)
        val status = statusOf(row(endMs = end), nowMs = expiresAt - 1L)
        assertEquals(ContactRetentionState.AWAITING_EXPIRY, status.state)
        assertEquals(expiresAt, status.contactExpiresAtMs)
        assertEquals(false, status.isPurgeable, "AWAITING_EXPIRY must never show the purge button")
    }

    @Test
    fun `a tenancy exactly at the expiry instant is PURGEABLE (boundary is inclusive)`() {
        val end = 1_700_000_000_000L
        val expiresAt = contactExpiryMs(end)
        val status = statusOf(row(endMs = end), nowMs = expiresAt)
        assertEquals(ContactRetentionState.PURGEABLE, status.state)
        assertEquals(true, status.isPurgeable, "PURGEABLE is the only state that shows the irreversible purge button")
    }

    @Test
    fun `a purged tenancy is PURGED even if it would otherwise still be inside the contact retention window`() {
        // purged_at 优先于到期判断：现实中不会发生（清理前必先过期），但状态派生不该依赖调用顺序假设。
        val end = 1_700_000_000_000L
        val status = statusOf(row(endMs = end, purgedAt = end + 1L), nowMs = end + 1L)
        assertEquals(ContactRetentionState.PURGED, status.state)
        assertEquals(false, status.isPurgeable, "PURGED must never show the purge button again")
    }

    @Test
    fun `statusOf carries the row's identity and contact fields through unchanged`() {
        val status = statusOf(row(id = "t-9", propertyId = "p-9"), nowMs = 0L)
        assertEquals("t-9", status.tenancyId)
        assertEquals("p-9", status.propertyId)
        assertEquals("J Doe", status.tenantName)
        assertEquals("j@example.com", status.contact)
    }
}

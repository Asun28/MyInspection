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

    @Test
    fun `expiry is exactly 12 calendar months after tenancy end, not a fixed day count`() {
        // 2023-01-15T00:00:00Z + 12 个月 = 2024-01-15T00:00:00Z（相差 365 天，含一个平年）。
        val end = 1_673_740_800_000L // 2023-01-15T00:00:00Z
        val expected = 1_705_276_800_000L // 2024-01-15T00:00:00Z
        assertEquals(expected, contactExpiryMs(end))
    }

    @Test
    fun `expiry clamps to the shorter month when the end date has no matching day 12 months later`() {
        // 2023-01-31 + 12 个月 = 2024-01-31（本例两侧月长相同，另用闰年 2 月钉夹紧语义）：
        // 2023-08-31 + 12 个月理论上落在 2024-08-31，月长相同不夹紧；改用 2024-01-31 + 1 个月
        // 验证 java.time 的夹紧行为（Feb 只有 29 天）。
        val jan31_2024 = 1_706_659_200_000L // 2024-01-31T00:00:00Z
        val feb29_2024 = 1_709_164_800_000L // 2024-02-29T00:00:00Z（夹紧到月末，而非溢出到 3 月 2 日）
        assertEquals(feb29_2024, contactExpiryMs(jan31_2024, months = 1L))
    }

    @Test
    fun `an ongoing tenancy with no end date is ACTIVE_TENANCY regardless of clock`() {
        val status = statusOf(row(endMs = null), nowMs = Long.MAX_VALUE)
        assertEquals(ContactRetentionState.ACTIVE_TENANCY, status.state)
        assertEquals(null, status.contactExpiresAtMs)
    }

    @Test
    fun `a tenancy inside the contact retention window is AWAITING_EXPIRY`() {
        val end = 1_700_000_000_000L
        val expiresAt = contactExpiryMs(end)
        val status = statusOf(row(endMs = end), nowMs = expiresAt - 1L)
        assertEquals(ContactRetentionState.AWAITING_EXPIRY, status.state)
        assertEquals(expiresAt, status.contactExpiresAtMs)
    }

    @Test
    fun `a tenancy exactly at the expiry instant is PURGEABLE (boundary is inclusive)`() {
        val end = 1_700_000_000_000L
        val expiresAt = contactExpiryMs(end)
        val status = statusOf(row(endMs = end), nowMs = expiresAt)
        assertEquals(ContactRetentionState.PURGEABLE, status.state)
    }

    @Test
    fun `a purged tenancy is PURGED even if it would otherwise still be inside the contact retention window`() {
        // purged_at 优先于到期判断：现实中不会发生（清理前必先过期），但状态派生不该依赖调用顺序假设。
        val end = 1_700_000_000_000L
        val status = statusOf(row(endMs = end, purgedAt = end + 1L), nowMs = end + 1L)
        assertEquals(ContactRetentionState.PURGED, status.state)
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

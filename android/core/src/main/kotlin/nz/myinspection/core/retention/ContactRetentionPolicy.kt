package nz.myinspection.core.retention

import java.time.Instant
import java.time.ZoneOffset

/**
 * 联系方式清理策略窗口：租约结束后 12 个月，用户已签认（`docs/TASK-BOARD.md`「用户已定」#3）。
 *
 * **这不是 RTA s123A 本身规定的数字**——s123A 管的是另一件事：巡检报告/照片证据（reg 40）须在
 * 租期中 + 结束后 12 个月内可出示（MBIE 索取 10 个工作日内交付），那是**证据保留**的法定下限，
 * 与联系方式该何时清理无关，法律并未规定联系方式的清理时限。本常量选 12 个月，是产品策略上不早于
 * 该证据保留窗口清空联系方式（Privacy Principle 9「不得超出必要期限保留」是驱动，但没有给出固定
 * 数字）——**是应用侧的清理策略值，被用户确认对齐了 s123A 的证据窗口，而非受 s123A 直接约束**。
 * 照片/报告/哈希链无限期保留，不受本常量约束（T1-CANON-HASH 哈希域本就不含联系方式，
 * 见 [nz.myinspection.core.model.TenancySnapshot]）。
 */
const val CONTACT_RETENTION_MONTHS: Long = 12L

/**
 * 联系方式到期时间点 = tenancy 结束时间 + [months] 个日历月，按 UTC 计算（入库时间戳一律 UTC epoch
 * 毫秒，展示层才转 Pacific/Auckland）。用日历月而非固定天数：月长不一致，12 个日历月与 360/365 固定
 * 天数在跨闰年/大小月时会差出几天——这是应用自己承诺的清理策略窗口，算错几天就是没兑现承诺。
 */
fun contactExpiryMs(tenancyEndMs: Long, months: Long = CONTACT_RETENTION_MONTHS): Long =
    Instant.ofEpochMilli(tenancyEndMs).atZone(ZoneOffset.UTC).plusMonths(months).toInstant().toEpochMilli()

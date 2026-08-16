package nz.myinspection.core.retention

import java.time.Instant
import java.time.ZoneOffset

/**
 * 联系方式保留期：法定下限 12 个月（RTA s123A 反向要求——巡检报告租期中 + 结束后 12 个月内须可
 * 出示，MBIE 索取 10 个工作日内交付）。用户已签认（`docs/TASK-BOARD.md` 「用户已定」#3）：租约结束后
 * 12 个月，联系方式（`tenant_name`/`contact`）一键清空（置 NULL，不删行）；照片/报告/哈希链无限期
 * 保留，不受本常量约束（T1-CANON-HASH 哈希域本就不含联系方式，见 [nz.myinspection.core.model.TenancySnapshot]）。
 */
const val CONTACT_RETENTION_MONTHS: Long = 12L

/**
 * 联系方式到期时间点 = tenancy 结束时间 + [months] 个日历月，按 UTC 计算（入库时间戳一律 UTC epoch
 * 毫秒，展示层才转 Pacific/Auckland）。用日历月而非固定天数：月长不一致，12 个日历月与 360/365 固定
 * 天数在跨闰年/大小月时会差出几天——保留期是法律义务，差几天可能让联系方式提前于法定下限被清。
 */
fun contactExpiryMs(tenancyEndMs: Long, months: Long = CONTACT_RETENTION_MONTHS): Long =
    Instant.ofEpochMilli(tenancyEndMs).atZone(ZoneOffset.UTC).plusMonths(months).toInstant().toEpochMilli()

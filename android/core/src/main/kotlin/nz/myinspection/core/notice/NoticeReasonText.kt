package nz.myinspection.core.notice

import nz.myinspection.core.compliance.ComplianceReasonKey

data class NoticeReasonText(
    val english: String,
    val chinese: String,
)

data class NoticeStatusText(
    val label: String,
    val isError: Boolean,
)

fun recordedNoticeStatus(isCompliant: Boolean): NoticeStatusText = if (isCompliant) {
    NoticeStatusText(label = "✓ Delivery recorded", isError = false)
} else {
    NoticeStatusText(label = "⚠ Delivery recorded — notice period failed", isError = true)
}

/** Fixed user-facing correction copy for the compliance engine's stable reason keys. */
fun noticeReasonText(key: ComplianceReasonKey): NoticeReasonText = when (key) {
    ComplianceReasonKey.UNKNOWN_ENTRY_PURPOSE -> NoticeReasonText(
        english = "This visit type cannot be checked. Choose an inspection entry.",
        chinese = "无法核验此到访类型。请选择巡检项目。",
    )
    ComplianceReasonKey.UNKNOWN_INSPECTION_TYPE -> NoticeReasonText(
        english = "This inspection type cannot be checked. Choose a supported inspection type.",
        chinese = "无法核验此巡检类型。请选择受支持的巡检类型。",
    )
    ComplianceReasonKey.INVALID_PROPERTY_ID -> NoticeReasonText(
        english = "The property cannot be checked. Choose the property again.",
        chinese = "无法核验该物业。请重新选择物业。",
    )
    ComplianceReasonKey.INVALID_HISTORY_ENTRY -> NoticeReasonText(
        english = "A previous inspection record cannot be checked. Review the schedule history.",
        chinese = "无法核验一条既往巡检记录。请检查巡检日程记录。",
    )
    ComplianceReasonKey.NOTICE_TOO_SHORT -> NoticeReasonText(
        english = "The required notice period has not been met. Choose a later inspection time.",
        chinese = "尚未满足规定的通知期限。请选择更晚的巡检时间。",
    )
    ComplianceReasonKey.NOTICE_TOO_EARLY -> NoticeReasonText(
        english = "The inspection is beyond the allowed notice window. Choose an earlier inspection date.",
        chinese = "巡检日期超出允许的通知期限。请选择更早的巡检日期。",
    )
    ComplianceReasonKey.OUTSIDE_VISIT_WINDOW -> NoticeReasonText(
        english = "The inspection is outside the permitted visiting hours. Choose another time.",
        chinese = "巡检时间不在允许的到访时段内。请选择其他时间。",
    )
    ComplianceReasonKey.FREQUENCY_LIMIT -> NoticeReasonText(
        english = "This inspection is too close to another recorded inspection. Choose a date outside the minimum interval.",
        chinese = "本次巡检与另一条已记录巡检相隔过近。请选择符合最短间隔要求的日期。",
    )
}

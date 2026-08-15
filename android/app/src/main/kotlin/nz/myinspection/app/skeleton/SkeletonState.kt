package nz.myinspection.app.skeleton

import android.graphics.Bitmap
import java.time.LocalDateTime

/**
 * T1-SKELETON-E2E · 一次性走通骨架的进程内状态。**用完即弃**。
 *
 * 刻意不做（见卡片 non_goals）：不落库、不用 UUIDv7、无 created_at/deleted_at、无软删除、无状态机。
 * 真实数据模型是 T1-SCHEMA-CORE 的产出，本文件不得被任何后续卡 import；真实实现落地后整包删除。
 */
enum class SkeletonCondition(val label: String) {
    GOOD("好"),
    FAIR("一般"),
    POOR("差"),
}

data class SkeletonItem(
    val description: String,
    val condition: SkeletonCondition,
    val photo: Bitmap?,
)

data class SkeletonInspection(
    val address: String,
    val startedAt: LocalDateTime,
    val item: SkeletonItem?,
)

package nz.myinspection.core.media

/**
 * 存储布局唯一派生点（关键不变量：全仓禁手拼路径）。app 私有存储根下
 * `photos/{propertyId}/{inspectionId}/{photoId}.jpg`；根路径由 :app 运行时注入，这里只产出相对路径。
 */
object MediaPaths {
    /** 派生形态与 [isPhotoRelPathShape] 的判定共用同一个真相源，两处不会各写一份而漂移。 */
    private val PHOTO_REL_PATH_PATTERN = Regex("^photos/([^/]+)/([^/]+)/([^/]+)\\.jpg$")

    fun photoRelPath(propertyId: String, inspectionId: String, photoId: String): String {
        requireSafeSegment("propertyId", propertyId)
        requireSafeSegment("inspectionId", inspectionId)
        requireSafeSegment("photoId", photoId)
        return "photos/$propertyId/$inspectionId/$photoId.jpg"
    }

    /**
     * 一个相对路径是否落在照片命名空间形状内。去重复用（[PhotoIngest]）与孤儿清理（[OrphanedAssetCleanup]）
     * 在物理触碰任何来自 DB 的 rel_path 之前都要先过这道闸：`photo.rel_path` 列没有约束保证它长这个样子
     * （schema 已冻结、不能补 CHECK），一行损坏/串表数据会经这两条链路波及命名空间之外的文件。
     * 与 `MediaFileStore` 的根包含性校验是两道独立闸——那道防"逃出根目录"，这道防"在根内但不该被碰"。
     */
    fun isPhotoRelPathShape(relPath: String): Boolean {
        val match = PHOTO_REL_PATH_PATTERN.matchEntire(relPath) ?: return false
        return match.groupValues.drop(1).all(::isSafeSegment)
    }

    /** 三个入参本应恒为 UUIDv7，天然安全；这层校验防的是损坏/异常输入把 `/`、`\`、`.`、`..` 带进路径。 */
    private fun requireSafeSegment(name: String, value: String) {
        require(isSafeSegment(value)) { "$name is not a safe path segment: $value" }
    }

    private fun isSafeSegment(value: String): Boolean =
        value.isNotBlank() && !value.contains('/') && !value.contains('\\') && value != "." && value != ".."
}

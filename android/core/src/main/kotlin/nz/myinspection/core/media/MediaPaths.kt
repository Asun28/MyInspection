package nz.myinspection.core.media

/**
 * 存储布局唯一派生点（关键不变量：全仓禁手拼路径）。app 私有外部存储根下
 * `photos/{propertyId}/{inspectionId}/{photoId}.jpg`；根路径由 :app 运行时注入，这里只产出相对路径。
 */
object MediaPaths {
    /** [photoRelPath] 的形状本身，钉死给 [isPhotoRelPathShape] 复用（唯一真相源，两处不能各写一份而漂移）。 */
    private val PHOTO_REL_PATH_PATTERN = Regex("^photos/([^/]+)/([^/]+)/([^/]+)\\.jpg$")

    fun photoRelPath(propertyId: String, inspectionId: String, photoId: String): String {
        requireSafeSegment("propertyId", propertyId)
        requireSafeSegment("inspectionId", inspectionId)
        requireSafeSegment("photoId", photoId)
        return "photos/$propertyId/$inspectionId/$photoId.jpg"
    }

    /**
     * 判定一个相对路径是否落在本卡定义的照片命名空间形状内（`photos/{propertyId}/{inspectionId}/{photoId}.jpg`，
     * 三段各自不含分隔符/穿越序列）。去重复用（[nz.myinspection.core.media.PhotoIngest]）与孤儿清理
     * （[OrphanedAssetCleanup]）在**物理触碰**一个来自 DB/查询的 rel_path 之前都要先过这道形状闸——
     * DB 的 `photo.rel_path` 列没有任何约束保证它长这个样子（schema 冻结、不能事后加 CHECK），一行数据
     * 损坏/串表（如误把 `audio/x.m4a` 当 photo 行、或 `.` 直指媒体根）经这两条链路波及命名空间之外的文件。
     * 与 [MediaFileStore][nz.myinspection.app.media.MediaFileStore] 的根包含性校验是**两道独立闸**——
     * 那道防的是"逃出根目录"，这道防的是"落在根目录内但不该被这两条链路碰"。
     */
    fun isPhotoRelPathShape(relPath: String): Boolean {
        val match = PHOTO_REL_PATH_PATTERN.matchEntire(relPath) ?: return false
        return match.groupValues.drop(1).all(::isSafeSegment)
    }

    /**
     * 三个入参本应恒为 UUIDv7（[nz.myinspection.core.db.Uuid7Generator]），天然不含分隔符——这层校验
     * 防的是数据损坏/异常输入把段落里的 `/`（改写目标目录）或 `..`（逃出 photos/ 根）带进最终路径。
     */
    private fun requireSafeSegment(name: String, value: String) {
        require(isSafeSegment(value)) { "$name is not a safe path segment: $value" }
    }

    private fun isSafeSegment(value: String): Boolean =
        value.isNotBlank() && !value.contains('/') && !value.contains('\\') && value != "." && value != ".."
}

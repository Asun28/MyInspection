package nz.myinspection.core.media

/**
 * 存储布局唯一派生点（关键不变量：全仓禁手拼路径）。app 私有外部存储根下
 * `photos/{propertyId}/{inspectionId}/{photoId}.jpg`；根路径由 :app 运行时注入，这里只产出相对路径。
 */
object MediaPaths {
    fun photoRelPath(propertyId: String, inspectionId: String, photoId: String): String {
        requireSafeSegment("propertyId", propertyId)
        requireSafeSegment("inspectionId", inspectionId)
        requireSafeSegment("photoId", photoId)
        return "photos/$propertyId/$inspectionId/$photoId.jpg"
    }

    /**
     * 三个入参本应恒为 UUIDv7（[nz.myinspection.core.db.Uuid7Generator]），天然不含分隔符——这层校验
     * 防的是数据损坏/异常输入把段落里的 `/`（改写目标目录）或 `..`（逃出 photos/ 根）带进最终路径。
     */
    private fun requireSafeSegment(name: String, value: String) {
        require(value.isNotBlank()) { "$name must not be blank" }
        require(!value.contains('/') && !value.contains('\\')) { "$name must not contain a path separator: $value" }
        require(value != "." && value != "..") { "$name must not be a path traversal segment: $value" }
    }
}

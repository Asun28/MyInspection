package nz.myinspection.core.media

/**
 * [ImportBounds.check] 的判定结果。两种拒绝都**不可重试**——同一份文件与同一个预算永远得到同一个结论
 * （CLAUDE.md「错误分 retryable/non-retryable」）。
 */
sealed interface ImportBoundsResult {
    data object Accepted : ImportBoundsResult

    /** 转正烘焙+缩放+编码所需的瞬时内存 [requiredBytes] 超出本次可用预算 [budgetBytes]。 */
    data class Rejected(val width: Int, val height: Int, val requiredBytes: Long, val budgetBytes: Long) : ImportBoundsResult

    /** 取不到正的图像边界（`BitmapFactory` 的 `inJustDecodeBounds` 对非图片/损坏文件给出 -1）。 */
    data class Undecodable(val width: Int, val height: Int) : ImportBoundsResult
}

/**
 * 解码前置校验：按**本次操作真实同时存活的 ARGB_8888 位图**判定「继续解码这份图会不会撑爆进程」，是进程
 * 存活底线，不是显示尺寸策略。源位图始终存在；非 identity EXIF 转正会分配同尺寸 baked 位图；仅当冻结档位
 * 真正缩小时，缩放会在 baked/source 尚存活时分配第三份位图。JPEG 直接写入有界文件流，故不计作整份内存。
 *
 * 预算由调用方按设备实际堆余量注入（:app 的 `PhotoMemoryBudget`），不写死像素上限。相机与导入必须传入
 * 同一操作已冻结的 [PhotoQualityProfile] 和将要执行的 EXIF orientation，避免检查一个较小峰值、实际分配更大
 * 峰值。超限的 UX 属 T2-CAPTURE-UI（已登记技术债）。
 */
object ImportBounds {
    /** `Bitmap.Config.ARGB_8888` 每像素字节数。 */
    const val BYTES_PER_PIXEL: Long = 4

    /**
     * 返回当前 EXIF+档位操作的峰值位图字节数；无效边界是调用错误，供外部输入使用时请先调用 [check]。
     * 计算超过 [Long] 可表示范围时饱和到 [Long.MAX_VALUE]。
     */
    fun requiredBytes(
        width: Int,
        height: Int,
        profile: PhotoQualityProfile,
        exifOrientation: Int,
    ): Long {
        require(width > 0 && height > 0) { "image bounds must be positive: ${width}x${height}" }
        return peakRequirement(width, height, profile, exifOrientation).bytes
    }

    fun check(
        width: Int,
        height: Int,
        budgetBytes: Long,
        profile: PhotoQualityProfile,
        exifOrientation: Int,
    ): ImportBoundsResult {
        if (width <= 0 || height <= 0) return ImportBoundsResult.Undecodable(width, height)
        val requirement = peakRequirement(width, height, profile, exifOrientation)
        return if (requirement.saturated || requirement.bytes > budgetBytes) {
            ImportBoundsResult.Rejected(width, height, requirement.bytes, budgetBytes)
        } else {
            ImportBoundsResult.Accepted
        }
    }

    private fun peakRequirement(
        width: Int,
        height: Int,
        profile: PhotoQualityProfile,
        exifOrientation: Int,
    ): ByteRequirement {
        val transform = ExifOrientation.transformFor(exifOrientation)
        val isQuarterTurn = transform.rotationDegrees == 90 || transform.rotationDegrees == 270
        val orientedWidth = if (isQuarterTurn) height else width
        val orientedHeight = if (isQuarterTurn) width else height
        val scaled = profile.scaledDimensions(orientedWidth, orientedHeight)

        var peak = bitmapBytes(width, height) // decoded/caller-owned source
        if (transform.rotationDegrees != 0 || transform.flipHorizontal) {
            peak = peak.plus(bitmapBytes(width, height)) // EXIF-baked destination
        }
        if (scaled.width != orientedWidth || scaled.height != orientedHeight) {
            peak = peak.plus(bitmapBytes(scaled.width, scaled.height)) // scale destination
        }
        return peak
    }

    private fun bitmapBytes(width: Int, height: Int): ByteRequirement {
        // Positive Int bounds multiply safely in Long; multiplying their pixel count by four may not.
        val pixels = width.toLong() * height.toLong()
        return if (pixels > Long.MAX_VALUE / BYTES_PER_PIXEL) {
            ByteRequirement(Long.MAX_VALUE, saturated = true)
        } else {
            ByteRequirement(pixels * BYTES_PER_PIXEL, saturated = false)
        }
    }

    private data class ByteRequirement(val bytes: Long, val saturated: Boolean) {
        fun plus(other: ByteRequirement): ByteRequirement {
            if (saturated || other.saturated || bytes > Long.MAX_VALUE - other.bytes) {
                return ByteRequirement(Long.MAX_VALUE, saturated = true)
            }
            return ByteRequirement(bytes + other.bytes, saturated = false)
        }
    }
}

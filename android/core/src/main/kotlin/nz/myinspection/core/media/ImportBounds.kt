package nz.myinspection.core.media

/**
 * [ImportBounds.check] 的判定结果。两种拒绝都**不可重试**——同一份文件与同一个预算永远得到同一个结论
 * （CLAUDE.md「错误分 retryable/non-retryable」）。
 */
sealed interface ImportBoundsResult {
    data object Accepted : ImportBoundsResult

    /** 转正烘焙+编码所需的瞬时内存 [requiredBytes] 超出本次可用预算 [budgetBytes]。 */
    data class Rejected(val width: Int, val height: Int, val requiredBytes: Long, val budgetBytes: Long) : ImportBoundsResult

    /** 取不到正的图像边界（`BitmapFactory` 的 `inJustDecodeBounds` 对非图片/损坏文件给出 -1）。 */
    data class Undecodable(val width: Int, val height: Int) : ImportBoundsResult
}

/**
 * 解码前置校验：按**字节预算**判定「继续解码这份图会不会撑爆进程」，是进程存活底线，不是显示尺寸策略。
 * 预算由调用方按设备实际堆余量注入（:app 的 `PhotoMemoryBudget`），不写死像素上限——固定阈值在小堆设备
 * 上仍会 OOM、在大堆设备上又白拒合法证据。相机与导入两条管线在编码那一刻都同时持有源位图与转正位图外加
 * 编码缓冲（见 [PEAK_BYTES_PER_PIXEL]），故共用同一套判定。超限的 UX 属 T2-CAPTURE-UI（已登记技术债）。
 */
object ImportBounds {
    /** `Bitmap.Config.ARGB_8888` 每像素字节数。 */
    const val BYTES_PER_PIXEL: Long = 4

    /** 编码那一刻同时存活的位图数：源位图 + 转正烘焙新分配的那份（源位图由其属主在编码之后才回收）。 */
    const val CONCURRENT_BITMAPS: Long = 2

    /** JPEG 编码缓冲的每像素预留：q92 照片输出经验上 ≤ 1 B/px，`ByteArrayOutputStream` 扩容瞬间新旧两份数组并存，取 2。 */
    const val ENCODER_BYTES_PER_PIXEL: Long = 2

    /** 单像素峰值 = 两份位图 + 编码缓冲。 */
    const val PEAK_BYTES_PER_PIXEL: Long = BYTES_PER_PIXEL * CONCURRENT_BITMAPS + ENCODER_BYTES_PER_PIXEL

    /** 该尺寸解码+烘焙+编码的峰值字节数；超出 `Long` 表示范围时饱和到 [Long.MAX_VALUE]（仍是"远超任何预算"）。 */
    fun requiredBytes(width: Int, height: Int): Long {
        val pixels = width.toLong() * height.toLong()
        return if (pixels > Long.MAX_VALUE / PEAK_BYTES_PER_PIXEL) Long.MAX_VALUE else pixels * PEAK_BYTES_PER_PIXEL
    }

    fun check(width: Int, height: Int, budgetBytes: Long): ImportBoundsResult {
        if (width <= 0 || height <= 0) return ImportBoundsResult.Undecodable(width, height)
        // 比较写成"像素数 vs 预算/单像素"而非"所需字节 vs 预算"：Int 上界的两条边相乘已达 4.6e18，
        // 再乘单像素字节会溢出 Long 变负数，一张荒谬大的图反而会被判为"装得下"。
        val pixels = width.toLong() * height.toLong()
        return if (pixels > budgetBytes / PEAK_BYTES_PER_PIXEL) {
            ImportBoundsResult.Rejected(width, height, requiredBytes(width, height), budgetBytes)
        } else {
            ImportBoundsResult.Accepted
        }
    }
}

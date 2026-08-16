package nz.myinspection.core.media

/**
 * 导入前置校验的判定结果：[Rejected] 携带实际尺寸与阈值，供 :app 侧构造一条具名的、**不可重试**的
 * 导入失败结果（同一份文件永远会再次被拒——不是环境性失败，重试没有意义，同 CLAUDE.md「错误分
 * retryable/non-retryable」）。
 */
sealed interface ImportBoundsResult {
    data object Accepted : ImportBoundsResult
    data class Rejected(val width: Int, val height: Int, val limitPixels: Long) : ImportBoundsResult
}

/**
 * 导入前置校验：宽×高像素总数超过 [MAX_IMPORT_PIXELS] 时拒绝，防止 :app 侧在真正分配位图内存前就已
 * 注定 OOM。**这是进程存活底线，不是显示/UX 尺寸策略**——「导入前提示用户确认/降采样后再导入」那类
 * 交互属 T2-CAPTURE-UI 的导入流程（见 tech-debt-tracker 登记），本函数只管「继续解码这份文件会不会
 * 大概率炸掉进程」这一件事，判定结果只有"接受"或"拒绝"两种，不做中间的自动降质处理（本卡上下文包已定
 * 「不做有损降采样」，见 [nz.myinspection.app.media.PhotoImportPipeline] KDoc）。
 *
 * **阈值取值依据**：转正烘焙路径瞬时最多同时持有两份 ARGB_8888 位图（[nz.myinspection.app.media.PhotoOrientationBaker]
 * 对 EXIF orientation 2–8 会额外新分配一份转正后的位图，与原图同时存活到编码完成），每像素 4 字节。
 * `MAX_IMPORT_PIXELS = 40_000_000`（40MP）时两份位图峰值 = 40,000,000 × 4 × 2 = 320,000,000 字节
 * （约 305 MiB）——仍处于常见 Android 单进程堆上限的危险区，但明显超出主流手机相机默认拍摄输出
 * （旗舰后置主摄默认模式多在 12–16MP，50MP+ 只在专门的高像素模式下出现且非默认），合法巡检证据照片
 * 不太可能触达此值；触达大概率是异常输入（超大扫描件/拼接图/损坏文件）。
 */
object ImportBounds {
    const val MAX_IMPORT_PIXELS: Long = 40_000_000L

    fun check(width: Int, height: Int): ImportBoundsResult {
        val pixels = width.toLong() * height.toLong()
        return if (pixels > MAX_IMPORT_PIXELS) {
            ImportBoundsResult.Rejected(width = width, height = height, limitPixels = MAX_IMPORT_PIXELS)
        } else {
            ImportBoundsResult.Accepted
        }
    }
}

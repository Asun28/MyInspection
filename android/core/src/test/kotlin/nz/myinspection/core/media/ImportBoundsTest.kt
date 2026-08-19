package nz.myinspection.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * 预算边界向量。**budget/required 全部写死为字面量、不由 [ImportBounds] 自己的常量算出**——否则把
 * `CONCURRENT_BITMAPS` 从 2 改成 1 这类变异会同时移动断言两侧，测试照样绿（L165）。
 * 4000 x 3000 = 12,000,000 px；峰值 8 B/px（两份 ARGB 位图）。JPEG 已流式落盘，故不再为整份
 * 编码结果预留线性内存 = 96,000,000 字节。
 */
class ImportBoundsTest {
    @Test
    fun `the peak-per-pixel model is two ARGB bitmaps after JPEG streams to disk`() {
        assertEquals(8L, ImportBounds.PEAK_BYTES_PER_PIXEL, "4 B/px x 2 concurrent bitmaps; the JPEG is not a whole in-memory array")
        assertEquals(96_000_000L, ImportBounds.requiredBytes(width = 4000, height = 3000))
    }

    @Test
    fun `a budget exactly equal to the requirement accepts`() {
        assertEquals(
            ImportBoundsResult.Accepted,
            ImportBounds.check(width = 4000, height = 3000, budgetBytes = 96_000_000L),
        )
    }

    @Test
    fun `one byte less than the requirement rejects`() {
        val rejected = assertIs<ImportBoundsResult.Rejected>(
            ImportBounds.check(width = 4000, height = 3000, budgetBytes = 95_999_999L),
        )
        assertEquals(4000, rejected.width)
        assertEquals(3000, rejected.height)
        assertEquals(96_000_000L, rejected.requiredBytes)
        assertEquals(95_999_999L, rejected.budgetBytes, "the budget in force must be reported, not a hard-coded limit")
    }

    @Test
    fun `one pixel more than the exactly-fitting budget rejects`() {
        assertIs<ImportBoundsResult.Rejected>(
            ImportBounds.check(width = 4001, height = 3000, budgetBytes = 96_000_000L),
        )
    }

    @Test
    fun `a 45MP import is rejected on a budget that comfortably fits a 12MP one`() {
        // 同一个预算下：12MP 过、45MP 不过——这正是固定像素阈值做不到的事（阈值要么在小堆设备上放行
        // 会 OOM 的图，要么在大堆设备上白拒合法证据）。
        val budget = 200_000_000L
        assertEquals(ImportBoundsResult.Accepted, ImportBounds.check(4000, 3000, budget))
        assertIs<ImportBoundsResult.Rejected>(ImportBounds.check(8192, 5464, budget))
    }

    @Test
    fun `a zero budget rejects even a one-pixel image`() {
        assertIs<ImportBoundsResult.Rejected>(ImportBounds.check(width = 1, height = 1, budgetBytes = 0L))
    }

    @Test
    fun `non-positive dimensions report Undecodable instead of being sized`() {
        // BitmapFactory 的 inJustDecodeBounds 对非图片/损坏文件给出 -1；0 同样不是可编码的证据。
        assertEquals(ImportBoundsResult.Undecodable(-1, -1), ImportBounds.check(-1, -1, budgetBytes = Long.MAX_VALUE))
        assertEquals(ImportBoundsResult.Undecodable(0, 4000), ImportBounds.check(0, 4000, budgetBytes = Long.MAX_VALUE))
        assertEquals(ImportBoundsResult.Undecodable(4000, 0), ImportBounds.check(4000, 0, budgetBytes = Long.MAX_VALUE))
    }

    @Test
    fun `an absurd dimension pair saturates instead of wrapping into an accept`() {
        // Int.MAX_VALUE^2 x 10 会溢出 Long；若比较写成"所需字节 vs 预算"，溢出后的负数会被判成"装得下"。
        assertEquals(Long.MAX_VALUE, ImportBounds.requiredBytes(Int.MAX_VALUE, Int.MAX_VALUE))
        assertIs<ImportBoundsResult.Rejected>(
            ImportBounds.check(Int.MAX_VALUE, Int.MAX_VALUE, budgetBytes = Long.MAX_VALUE),
        )
    }
}

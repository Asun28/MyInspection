package nz.myinspection.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * 峰值预算必须按操作真实存活的位图计算：解码源始终存在；EXIF 转正仅在需要时分配同尺寸目标；缩放仅在
 * profile 真正缩小时分配第三份目标。JPEG 是流式落盘，不属于整份内存预算。
 */
class ImportBoundsTest {
    @Test
    fun `identity orientation reserves the source and only the necessary scaled destination`() {
        // 4000 x 3000 source = 48,000,000 B; MEDIUM target 1920 x 1440 = 11,059,200 B.
        assertEquals(
            59_059_200L,
            ImportBounds.requiredBytes(
                width = 4000,
                height = 3000,
                profile = PhotoQualityProfile.MEDIUM,
                exifOrientation = 1,
            ),
        )
    }

    @Test
    fun `rotated medium image budgets the real three-bitmap allocation instant`() {
        // Source 48,000,000 B + 90-degree baked source 48,000,000 B + 1440 x 1920 scaled target 11,059,200 B.
        val required = 107_059_200L
        assertEquals(
            required,
            ImportBounds.requiredBytes(
                width = 4000,
                height = 3000,
                profile = PhotoQualityProfile.MEDIUM,
                exifOrientation = 6,
            ),
        )
        assertEquals(
            ImportBoundsResult.Accepted,
            ImportBounds.check(
                width = 4000,
                height = 3000,
                budgetBytes = required,
                profile = PhotoQualityProfile.MEDIUM,
                exifOrientation = 6,
            ),
        )
        val rejected = assertIs<ImportBoundsResult.Rejected>(
            ImportBounds.check(
                width = 4000,
                height = 3000,
                budgetBytes = required - 1,
                profile = PhotoQualityProfile.MEDIUM,
                exifOrientation = 6,
            ),
        )
        assertEquals(required, rejected.requiredBytes)
        assertEquals(required - 1, rejected.budgetBytes)
    }

    @Test
    fun `unscaled upright image does not reserve phantom intermediate bitmaps`() {
        assertEquals(
            48_000_000L,
            ImportBounds.requiredBytes(
                width = 4000,
                height = 3000,
                profile = PhotoQualityProfile.EXTRA_HIGH,
                exifOrientation = 1,
            ),
        )
    }

    @Test
    fun `mirror-only orientation reserves its baked bitmap even when no scale is needed`() {
        assertEquals(
            96_000_000L,
            ImportBounds.requiredBytes(
                width = 4000,
                height = 3000,
                profile = PhotoQualityProfile.EXTRA_HIGH,
                exifOrientation = 2,
            ),
        )
    }

    @Test
    fun `non-positive dimensions report Undecodable instead of being sized`() {
        assertEquals(
            ImportBoundsResult.Undecodable(-1, -1),
            ImportBounds.check(
                width = -1,
                height = -1,
                budgetBytes = Long.MAX_VALUE,
                profile = PhotoQualityProfile.MEDIUM,
                exifOrientation = 1,
            ),
        )
        assertEquals(
            ImportBoundsResult.Undecodable(0, 4000),
            ImportBounds.check(
                width = 0,
                height = 4000,
                budgetBytes = Long.MAX_VALUE,
                profile = PhotoQualityProfile.MEDIUM,
                exifOrientation = 1,
            ),
        )
    }

    @Test
    fun `an absurd dimension pair saturates and rejects instead of wrapping into an accept`() {
        assertEquals(
            Long.MAX_VALUE,
            ImportBounds.requiredBytes(
                width = Int.MAX_VALUE,
                height = Int.MAX_VALUE,
                profile = PhotoQualityProfile.LOW,
                exifOrientation = 6,
            ),
        )
        assertIs<ImportBoundsResult.Rejected>(
            ImportBounds.check(
                width = Int.MAX_VALUE,
                height = Int.MAX_VALUE,
                budgetBytes = Long.MAX_VALUE,
                profile = PhotoQualityProfile.LOW,
                exifOrientation = 6,
            ),
        )
    }
}

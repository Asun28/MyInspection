package nz.myinspection.app.media

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/** JPEG 重编码薄壳：转正烘焙后的产出统一按 quality 92 重编码（卡片已定），清掉源文件可能带的 orientation 标记。 */
object PhotoJpegEncoder {
    private const val QUALITY = 92

    /** @throws IllegalStateException 若 `Bitmap.compress` 返回 false——半份字节放行下去会让一条正常的 photo 行背后是一张打不开的图。 */
    fun encode(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().use { out ->
            val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            check(ok) { "Bitmap.compress reported failure — refusing to hash/persist a partial JPEG" }
            out.toByteArray()
        }
}

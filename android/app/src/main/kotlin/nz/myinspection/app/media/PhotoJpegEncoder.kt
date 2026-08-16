package nz.myinspection.app.media

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/** JPEG 重编码薄壳：转正烘焙后的产出统一按 quality 92 重编码（卡片已定），清掉源文件可能带的 orientation 标记。 */
object PhotoJpegEncoder {
    private const val QUALITY = 92

    fun encode(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            out.toByteArray()
        }
}

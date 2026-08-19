package nz.myinspection.app.media

import android.graphics.Bitmap
import java.io.OutputStream
import nz.myinspection.core.media.StreamEncoder

/** JPEG 重编码薄壳：转正烘焙后的产出统一按 quality 92 重编码（卡片已定），清掉源文件可能带的 orientation 标记。 */
object PhotoJpegEncoder : StreamEncoder<Bitmap> {
    private const val QUALITY = 92

    /** @throws IllegalStateException 若 `Bitmap.compress` 返回 false——半份字节绝不能进入暂存文件。 */
    override fun encodeInto(bitmap: Bitmap, output: OutputStream) {
        val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, output)
        check(ok) { "Bitmap.compress reported failure — refusing to hash/persist a partial JPEG" }
    }
}

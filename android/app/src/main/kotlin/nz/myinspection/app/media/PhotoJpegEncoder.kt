package nz.myinspection.app.media

import android.graphics.Bitmap
import java.io.OutputStream
import nz.myinspection.core.media.PhotoQualityProfile
import nz.myinspection.core.media.StreamEncoder

/** JPEG 重编码薄壳：按每次操作冻结的共享档位编码，清掉源文件可能带的 orientation 标记。 */
class PhotoJpegEncoder(
    private val qualityProfile: PhotoQualityProfile,
) : StreamEncoder<Bitmap> {

    /** @throws IllegalStateException 若 `Bitmap.compress` 返回 false——半份字节绝不能进入暂存文件。 */
    override fun encodeInto(bitmap: Bitmap, output: OutputStream) {
        val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, qualityProfile.jpegQuality, output)
        check(ok) { "Bitmap.compress reported failure — refusing to hash/persist a partial JPEG" }
    }
}

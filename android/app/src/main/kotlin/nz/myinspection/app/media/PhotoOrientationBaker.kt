package nz.myinspection.app.media

import android.graphics.Bitmap
import android.graphics.Matrix
import nz.myinspection.core.media.ExifOrientation

/**
 * 转正烘焙薄壳：把 :core [ExifOrientation] 算出的参数表接到 android.graphics.Matrix 上——
 * postScale 与 postRotate 均为「后乘」，调用顺序即施加顺序，与 OrientationTransform 的 KDoc 约定一致
 * （先 flipHorizontal 再 rotationDegrees）。`Bitmap.createBitmap(src,...,matrix,true)` 会按变换后的
 * 边界自动重新平移，无需本层再算平移量。
 */
object PhotoOrientationBaker {
    fun bake(source: Bitmap, exifOrientation: Int): Bitmap {
        val transform = ExifOrientation.transformFor(exifOrientation)
        if (transform.rotationDegrees == 0 && !transform.flipHorizontal) return source

        val matrix = Matrix()
        if (transform.flipHorizontal) matrix.postScale(-1f, 1f)
        matrix.postRotate(transform.rotationDegrees.toFloat())
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }
}

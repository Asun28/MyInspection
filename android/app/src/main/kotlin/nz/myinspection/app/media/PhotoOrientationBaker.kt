package nz.myinspection.app.media

import android.graphics.Bitmap
import android.graphics.Matrix
import nz.myinspection.core.media.ExifOrientation

/**
 * 转正烘焙薄壳：把 :core [ExifOrientation] 的参数表接到 `Matrix` 上。post* 是后乘，调用顺序即施加顺序
 * （先 flipHorizontal 再 rotationDegrees，与 `OrientationTransform` 的约定一致）。已正的图原样返回、
 * 不新分配——调用方据此判断该不该回收返回值。
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

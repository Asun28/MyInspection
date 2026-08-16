package nz.myinspection.core.media

/**
 * EXIF orientation → 转正参数表（"先 mirror horizontal 再顺时针 rotate" 分解，对齐 EXIF 官方 8 值语义，
 * 见 exiftool.org/TagNames/EXIF.html 的 Orientation 描述：1=Horizontal(normal) / 2=Mirror horizontal /
 * 3=Rotate 180 / 4=Mirror vertical / 5=Mirror horizontal and rotate 270 CW / 6=Rotate 90 CW /
 * 7=Mirror horizontal and rotate 90 CW / 8=Rotate 270 CW）。
 *
 * [flipHorizontal] 恒先施加，[rotationDegrees]（顺时针）随后施加——对应 android.graphics.Matrix 的
 * `postScale(-1f, 1f)` 接 `postRotate(deg)`（Matrix 的 post* 是后乘：先调的变换先作用在点上，与本类型的
 * 施加顺序一致）。4/5/7 三档用「mirror + rotate」复合表示纯 mirror-vertical / transpose / transverse
 * 语义——与 EXIF 官方各自的单一动作描述是同一个变换矩阵的两种等价写法（ExifOrientationTest 用矩阵乘法
 * 逐条验证）。
 */
data class OrientationTransform(val rotationDegrees: Int, val flipHorizontal: Boolean)

object ExifOrientation {
    private val TABLE = mapOf(
        1 to OrientationTransform(rotationDegrees = 0, flipHorizontal = false),
        2 to OrientationTransform(rotationDegrees = 0, flipHorizontal = true),
        3 to OrientationTransform(rotationDegrees = 180, flipHorizontal = false),
        4 to OrientationTransform(rotationDegrees = 180, flipHorizontal = true),
        5 to OrientationTransform(rotationDegrees = 270, flipHorizontal = true),
        6 to OrientationTransform(rotationDegrees = 90, flipHorizontal = false),
        7 to OrientationTransform(rotationDegrees = 90, flipHorizontal = true),
        8 to OrientationTransform(rotationDegrees = 270, flipHorizontal = false),
    )

    /** 未知/UNDEFINED（0，或任何超出 1..8 的值）视为已正——不猜测方向，原样使用。 */
    fun transformFor(exifOrientation: Int): OrientationTransform =
        TABLE[exifOrientation] ?: OrientationTransform(rotationDegrees = 0, flipHorizontal = false)
}

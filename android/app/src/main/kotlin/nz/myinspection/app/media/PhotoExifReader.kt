package nz.myinspection.app.media

import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * ExifInterface 读取薄壳（androidx.exifinterface 1.4.2，见 libs.versions.toml pin）。
 * :core 只认整型 orientation 与毫秒时间戳，本层负责把 EXIF 的字符串标签转成这两种形状。
 */
object PhotoExifReader {
    private val EXIF_DATETIME_PATTERN: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    /** 读 `TAG_ORIENTATION`；缺失/损坏一律回退 `ORIENTATION_NORMAL`（getAttributeInt 的 defaultValue 语义）。 */
    fun readOrientation(file: File): Int =
        ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

    /**
     * 读拍摄时间（需求 §5：与巡检时间分开存），毫秒 epoch。EXIF `DateTimeOriginal` 无时区信息，按设备本地
     * 时区解释（`TAG_OFFSET_TIME_ORIGINAL` 多数相机不写，v1 不依赖它）。标签缺失或格式不符一律返回 null——
     * 调用方据此判定「无拍摄时间可用」，不得当异常抛出中断导入。
     */
    fun readExifTimeMs(file: File): Long? {
        val raw = ExifInterface(file).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: return null
        return try {
            LocalDateTime.parse(raw, EXIF_DATETIME_PATTERN)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }
}

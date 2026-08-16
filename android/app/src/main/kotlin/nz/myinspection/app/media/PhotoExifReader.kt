package nz.myinspection.app.media

import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * ExifInterface 读取薄壳（androidx.exifinterface 1.4.2，见 libs.versions.toml pin；`TAG_OFFSET_TIME_ORIGINAL`
 * 已核实存在于该 pin 版本——`OffsetTimeOriginal`，见该版本 aar 内 `ExifInterface.class` 常量池）。
 * :core 只认整型 orientation 与毫秒时间戳，本层负责把 EXIF 的字符串标签转成这两种形状。
 */
object PhotoExifReader {
    private val EXIF_DATETIME_PATTERN: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    /** 读 `TAG_ORIENTATION`；缺失/损坏一律回退 `ORIENTATION_NORMAL`（getAttributeInt 的 defaultValue 语义）。 */
    fun readOrientation(file: File): Int =
        ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

    /**
     * 读拍摄时间（需求 §5：与巡检时间分开存），毫秒 epoch。EXIF `DateTimeOriginal` 本身无时区信息——
     * **确定性回退策略**：`TAG_OFFSET_TIME_ORIGINAL`（EXIF 2.31+，"+HH:MM"/"-HH:MM"）若存在且能解析，
     * 按它定的时区解释，与设备当前时区无关（同一张图任何设备读出同一个 epoch）；缺失或格式不符时才回退
     * 设备当前时区——这一支本就依赖运行环境、不承诺跨设备确定性，仅为「无 offset 时也总能给个时间戳」兜底。
     * `DateTimeOriginal` 标签缺失或格式不符一律返回 null——调用方据此判定「无拍摄时间可用」，不当异常抛出中断导入。
     */
    fun readExifTimeMs(file: File): Long? {
        val exif = ExifInterface(file)
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: return null
        val local = try {
            LocalDateTime.parse(raw, EXIF_DATETIME_PATTERN)
        } catch (e: DateTimeParseException) {
            return null
        }
        val offsetRaw = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
        val zone = offsetRaw?.let { runCatching { ZoneOffset.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
        return local.atZone(zone).toInstant().toEpochMilli()
    }
}

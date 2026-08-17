package nz.myinspection.app.media

import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import nz.myinspection.core.media.ExifSubSecond

/**
 * ExifInterface 读取薄壳（androidx.exifinterface 1.4.2，见 libs.versions.toml pin；`TAG_OFFSET_TIME_ORIGINAL`
 * 已核实存在于该 pin 版本——`OffsetTimeOriginal`，见该版本 aar 内 `ExifInterface.class` 常量池）。
 * :core 只认整型 orientation 与毫秒时间戳，本层负责把 EXIF 的字符串标签转成这两种形状。
 */
object PhotoExifReader {
    // uuuu（proleptic year）配 STRICT：默认 SMART 会把「2月30日」这类不存在的日历日静默挪成合法日期再
    // 放行，于是"格式不符返回 null"的承诺对坏值就成了假的。STRICT 让它抛 DateTimeParseException 走 null 分支。
    private val EXIF_DATETIME_PATTERN: DateTimeFormatter = DateTimeFormatter
        .ofPattern("uuuu:MM:dd HH:mm:ss")
        .withResolverStyle(ResolverStyle.STRICT)

    /** 读 `TAG_ORIENTATION`；标签缺失、元数据损坏、文件根本打不开，一律回退 `ORIENTATION_NORMAL`。 */
    fun readOrientation(file: File): Int =
        exifOrNull(file)?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ?: ExifInterface.ORIENTATION_NORMAL

    /**
     * `ExifInterface(File)` 对非图片/损坏文件会抛 [IOException]——那类输入的归宿是边界闸给出的具名拒绝
     * （`RejectedUndecodable`），不是从 EXIF 读取处炸出一个异常打断整条 ingest。只吞这一种：其余异常照抛。
     */
    private fun exifOrNull(file: File): ExifInterface? = try {
        ExifInterface(file)
    } catch (e: IOException) {
        null
    }

    /**
     * 读拍摄时间（需求 §5：与巡检时间分开存），毫秒 epoch。EXIF `DateTimeOriginal` 本身无时区信息，
     * `SubSecTimeOriginal`（若存在）补足到毫秒精度。
     *
     * **确定性约束（改动此函数前先读）**：`TAG_OFFSET_TIME_ORIGINAL`（EXIF 2.31+）存在且能解析时按它定的
     * 时区解释，同一张图在任何设备、任何时刻读出同一个 epoch。**只有** offset 缺失/格式不符才退回
     * `ZoneId.systemDefault()`——那一支本就依赖运行环境，是兜底而非主路径。`DateTimeOriginal` 缺失或格式
     * 不符一律返回 null，不抛异常中断导入。
     *
     * 未用 androidx.exifinterface 1.4.2 自带的 `getDateTimeOriginal()`：实测（反编译同版本 aar）它把
     * `DateTimeOriginal` 按 UTC 解析后再**累加** offset 而非用于修正，对 "+13:00" 这类正偏移会推离真实
     * UTC 瞬间。本函数自行用 `java.time` 实现「本地墙钟 + 时区 → UTC 瞬间」，语义可自证。
     */
    fun readExifTimeMs(file: File): Long? {
        val exif = exifOrNull(file) ?: return null
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: return null
        val local = try {
            LocalDateTime.parse(raw, EXIF_DATETIME_PATTERN)
        } catch (e: DateTimeParseException) {
            return null
        }
        val offsetRaw = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
        // 只捕获 ZoneOffset.of 文档化会抛的 DateTimeException；runCatching 兜一切会把本段自身的 bug
        // 也悄悄当成"offset 解析失败"。
        val zone = offsetRaw?.let {
            try {
                ZoneOffset.of(it)
            } catch (e: DateTimeException) {
                null
            }
        } ?: ZoneId.systemDefault()
        val baseMillis = local.atZone(zone).toInstant().toEpochMilli()
        return baseMillis + ExifSubSecond.parseMillis(exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL))
    }
}

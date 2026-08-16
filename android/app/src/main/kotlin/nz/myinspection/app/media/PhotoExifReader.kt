package nz.myinspection.app.media

import androidx.exifinterface.media.ExifInterface
import java.io.File
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
    // uuuu（proleptic year，非 yyyy 的 year-of-era）配 ResolverStyle.STRICT：默认 SMART 解析会把
    // 「2月30日」这类不存在的日历日静默挪成合法日期再放行——那样"格式不符返回 null"的承诺对这类坏值
    // 就是假的（会悄悄存一个被挪动过的错误时间戳，而不是如实报告"读不出"）。STRICT 让这类输入直接抛
    // DateTimeParseException，走下面既有的「解析失败 → null」分支。
    private val EXIF_DATETIME_PATTERN: DateTimeFormatter = DateTimeFormatter
        .ofPattern("uuuu:MM:dd HH:mm:ss")
        .withResolverStyle(ResolverStyle.STRICT)

    /** 读 `TAG_ORIENTATION`；缺失/损坏一律回退 `ORIENTATION_NORMAL`（getAttributeInt 的 defaultValue 语义）。 */
    fun readOrientation(file: File): Int =
        ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

    /**
     * 读拍摄时间（需求 §5：与巡检时间分开存），毫秒 epoch。EXIF `DateTimeOriginal` 本身无时区信息，
     * `SubSecTimeOriginal`（若存在）补足到毫秒精度。
     *
     * **确定性约束（久性声明，改动此函数前先读）**：`TAG_OFFSET_TIME_ORIGINAL`（EXIF 2.31+，
     * "+HH:MM"/"-HH:MM"）存在且能解析时，按它定的时区解释——同一张图，任何设备、任何时刻读出同一个
     * epoch，与运行设备当前时区无关。**只有** offset 标签缺失或格式不符时才退回
     * `ZoneId.systemDefault()`——这一支本就依赖运行环境，不承诺跨设备/跨时刻确定性，只是「没有 offset
     * 信息时也总能给出一个时间戳」的兜底，不是主路径。`DateTimeOriginal` 标签缺失或格式不符一律返回
     * null——调用方据此判定「无拍摄时间可用」，不当异常抛出中断导入。
     *
     * 未采用 androidx.exifinterface 1.4.2 自带的 `getDateTimeOriginal()`：实测反编译该方法（同版本 aar
     * 内 `ExifInterface.class`），其内部把 `DateTimeOriginal` 按固定 UTC 时区解析后，再把 offset 差值
     * **累加**上去而非用于修正——对 "+13:00" 这类正偏移会把结果推离真实 UTC 瞬间，语义与本函数要的
     * 「本地墙钟时间 + 时区 → 正确 UTC 瞬间」不同。故本函数自行用 `java.time`（`LocalDateTime` +
     * `ZoneOffset`/`ZoneId`）实现，语义可自证、不依赖对该内部实现细节的信任。
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
        // 只捕获 ZoneOffset.of 文档化会抛的 DateTimeException（offset 字符串格式不符）——不用
        // runCatching 兜一切：那样连"这段代码本身有 bug"的异常都会被悄悄当成"offset 解析失败"处理掉。
        val zone = offsetRaw?.let {
            try {
                ZoneOffset.of(it)
            } catch (e: DateTimeException) {
                null
            }
        } ?: ZoneId.systemDefault()
        val baseMillis = local.atZone(zone).toInstant().toEpochMilli()
        // 亚秒解析（"SubSecTime* 是数字串、不是定长毫秒 field"及溢出防护）是纯字符串→数字逻辑，判定
        // 都在 :core 的 ExifSubSecond 里做、并有边界向量测试钉住；这里只管把 EXIF 字符串标签递过去。
        return baseMillis + ExifSubSecond.parseMillis(exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL))
    }
}

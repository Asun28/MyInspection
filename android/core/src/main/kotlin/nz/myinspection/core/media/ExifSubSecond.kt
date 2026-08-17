package nz.myinspection.core.media

/**
 * EXIF `SubSecTime*` 标签解析：秒后小数部分的十进制数字串（不是定长毫秒 field）——`"5"`/`"500"` 同表
 * 0.5 秒（500ms），`"1234"` 表 0.1234 秒。**只取前三位数字**换算为 0–999ms（不足三位右补零）——这个截断
 * 同时让函数对任意长度输入结构性免于整型溢出，21 位全 9 也稳定得到 999ms。
 * 非数字/空/null 一律记 0（无可用亚秒精度，不影响秒级时间戳本身）。
 */
object ExifSubSecond {
    fun parseMillis(subSec: String?): Long {
        if (subSec.isNullOrEmpty() || !subSec.all(Char::isDigit)) return 0L
        return subSec.take(3).padEnd(3, '0').toLong()
    }
}

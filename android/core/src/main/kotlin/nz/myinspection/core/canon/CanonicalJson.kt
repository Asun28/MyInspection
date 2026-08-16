package nz.myinspection.core.canon

import java.text.Normalizer
import java.util.TreeMap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * ADR-0003 冻结版 canonical JSON 序列化器（RFC 8785 风格，T1-CANON-HASH）。
 * finalize 哈希（本卡）与备份 manifest（T5-BACKUP-FORMAT）共用这一个实现——合并即冻结。
 *
 * 规则（全部由 CanonicalJsonTest / GoldenVectorTest 的黄金向量钉死）：
 * - 对象键按 UTF-16 码位排序；键与字符串值先 NFC 归一（java.text.Normalizer）；
 *   NFC 归一后键冲突 = 输入非法，抛 [IllegalArgumentException]（静默去重会让两份不同输入算出同一哈希）。
 * - 数值只允许整数（时间一律 epoch 毫秒 Long），且必须是规范拼写——toString 往返相等，
 *   拒绝 "007" / "+5" / "-0" 这类输入而不是静默改写（改写=两份不同文本同哈希且无诊断）。
 * - 字符串必须是良构 Unicode：孤立代理项（lone surrogate）非法——UTF-8 编码会把它静默替换成 '?'，
 *   两个不同的 canonical 串会哈希成同一值，摧毁防篡改自证；RFC 8785 亦要求良构。
 * - 数组顺序 = 调用方给定顺序（items 按模板全序、photos/audios 按 UUID 序，由调用方排好，见
 *   [nz.myinspection.core.model.InspectionSnapshot] 顶部说明）。
 * - 无空白；字符串转义 = RFC 8785 最小集（引号、反斜杠与 U+0000-U+001F；短形式 b t n f r，
 *   其余控制符 u00xx 小写十六进制；非 ASCII 字符含代理对不转义、按 UTF-8 原样输出）。
 */
object CanonicalJson {

    fun serialize(element: JsonElement): String = buildString { write(element) }

    private fun StringBuilder.write(element: JsonElement) {
        when (element) {
            is JsonNull -> append("null")
            is JsonPrimitive -> writePrimitive(element)
            is JsonObject -> writeObject(element)
            is JsonArray -> writeArray(element)
        }
    }

    private fun StringBuilder.writePrimitive(primitive: JsonPrimitive) {
        if (primitive.isString) {
            writeString(primitive.content)
            return
        }
        when (val body = primitive.content) {
            "true", "false" -> append(body)
            else -> {
                val integer = body.toLongOrNull()
                    ?: throw IllegalArgumentException("canonical JSON 数值只允许整数（ADR-0003；时间一律 epoch 毫秒 Long）：$body")
                require(integer.toString() == body) {
                    "canonical JSON 整数必须是规范拼写（拒绝而非静默改写）：$body"
                }
                append(body)
            }
        }
    }

    private fun StringBuilder.writeObject(obj: JsonObject) {
        // TreeMap 的 String 自然序 = 逐 char 比较 = UTF-16 码位序，正是 ADR-0003/RFC 8785 要的键序。
        val sorted = TreeMap<String, JsonElement>()
        for ((key, value) in obj) {
            val normalized = Normalizer.normalize(key, Normalizer.Form.NFC)
            require(sorted.put(normalized, value) == null) {
                "NFC 归一后对象键冲突（静默去重会让两份不同输入算出同一哈希）：$normalized"
            }
        }
        append('{')
        var first = true
        for ((key, value) in sorted) {
            if (!first) append(',')
            first = false
            writeEscaped(key) // 键在上面已 NFC 归一，这里不做第二次
            append(':')
            write(value)
        }
        append('}')
    }

    private fun StringBuilder.writeArray(array: JsonArray) {
        append('[')
        for ((index, element) in array.withIndex()) {
            if (index > 0) append(',')
            write(element)
        }
        append(']')
    }

    private fun StringBuilder.writeString(raw: String) {
        writeEscaped(Normalizer.normalize(raw, Normalizer.Form.NFC))
    }

    private fun StringBuilder.writeEscaped(normalized: String) {
        append('"')
        var i = 0
        while (i < normalized.length) {
            val ch = normalized[i]
            when {
                ch == '"' -> append("\\\"")
                ch == '\\' -> append("\\\\")
                ch.code == 0x08 -> append("\\b")
                ch.code == 0x09 -> append("\\t")
                ch.code == 0x0A -> append("\\n")
                ch.code == 0x0C -> append("\\f")
                ch.code == 0x0D -> append("\\r")
                ch.code < 0x20 -> append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                ch.isHighSurrogate() -> {
                    require(i + 1 < normalized.length && normalized[i + 1].isLowSurrogate()) {
                        "canonical JSON 字符串必须是良构 Unicode：孤立高代理项会被 UTF-8 编码静默替换成 ?，两串同哈希"
                    }
                    append(ch)
                    append(normalized[i + 1])
                    i++
                }
                ch.isLowSurrogate() -> throw IllegalArgumentException(
                    "canonical JSON 字符串必须是良构 Unicode：孤立低代理项会被 UTF-8 编码静默替换成 ?，两串同哈希",
                )
                else -> append(ch)
            }
            i++
        }
        append('"')
    }
}

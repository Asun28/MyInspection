package nz.myinspection.core.canon

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 通用 canonicalizer 的行为契约（ADR-0003 冻结规则逐条钉死）。快照投影层的黄金向量在
 * [GoldenVectorTest]；这里只测 [CanonicalJson.serialize] 对任意 JsonElement 的确定性规则——
 * 「键乱序输入产同一哈希」只能在这一层测：快照数据类是定型输入，键序无从乱起。
 * 字符串里的组合字符（e-acute 组合/分解两种编码）与控制符一律以 \uXXXX 转义写死——隐形字符
 * 经不起编辑器/格式化工具的静默归一（L165：机检认 ASCII）；中文为 NFC 不变的 CJK，按仓库惯例保留原文。
 */
class CanonicalJsonTest {

    @Test
    fun `object keys sort by UTF-16 code units regardless of insertion order`() {
        val a = buildJsonObject { put("z", 3); put("a", 1); put("id", 2); put("items", 4) }
        val b = buildJsonObject { put("items", 4); put("id", 2); put("a", 1); put("z", 3) }
        val expected = "{\"a\":1,\"id\":2,\"items\":4,\"z\":3}"
        assertEquals(expected, CanonicalJson.serialize(a))
        assertEquals(expected, CanonicalJson.serialize(b))
    }

    @Test
    fun `string values NFC normalize so composed and decomposed forms canonicalize identically`() {
        val composed = buildJsonObject { put("note", "caf\u00e9") }
        val decomposed = buildJsonObject { put("note", "cafe\u0301") }
        val expected = "{\"note\":\"caf\u00e9\"}"
        assertEquals(expected, CanonicalJson.serialize(composed))
        assertEquals(expected, CanonicalJson.serialize(decomposed))
    }

    @Test
    fun `object keys NFC normalize and post-normalization collisions are rejected`() {
        val decomposedKey = buildJsonObject { put("cafe\u0301", 1) }
        assertEquals("{\"caf\u00e9\":1}", CanonicalJson.serialize(decomposedKey))
        // 静默去重会让两份不同输入产同一哈希——必须显式拒绝。
        val colliding = buildJsonObject { put("caf\u00e9", 1); put("cafe\u0301", 2) }
        assertFailsWith<IllegalArgumentException> { CanonicalJson.serialize(colliding) }
    }

    @Test
    fun `escaping uses the RFC 8785 minimal set`() {
        // 输入含 双引号 反斜杠 换行 制表符 U+0001；期望串与哈希由独立 Python 实现预先算出（黄金向量法）。
        val obj = buildJsonObject { put("k", "a\"b\\c\nd\te\u0001f") }
        val canon = CanonicalJson.serialize(obj)
        assertEquals("{\"k\":\"a\\\"b\\\\c\\nd\\te\\u0001f\"}", canon)
        assertEquals("4387f2bcd3c5937c099ab9642ddb3c28afd34c62fe9566bc79eae3d000bb880a", sha256Hex(canon))
        // 短形式 \b \f \r（U+0008 / U+000C / U+000D）。
        val shortForms = buildJsonObject { put("k2", "\u0008\u000c\r") }
        assertEquals("{\"k2\":\"\\b\\f\\r\"}", CanonicalJson.serialize(shortForms))
    }

    @Test
    fun `non-integer numbers are rejected`() {
        assertFailsWith<IllegalArgumentException> { CanonicalJson.serialize(buildJsonObject { put("x", 1.5) }) }
        // 1.0 数学上是整数，但含小数点即非法——时间一律 epoch 毫秒 Long，不给浮点留门。
        assertFailsWith<IllegalArgumentException> { CanonicalJson.serialize(buildJsonObject { put("x", 1.0) }) }
    }

    @Test
    fun `long extremes serialize as plain decimal`() {
        assertEquals("{\"x\":9223372036854775807}", CanonicalJson.serialize(buildJsonObject { put("x", Long.MAX_VALUE) }))
        assertEquals("{\"x\":-9223372036854775808}", CanonicalJson.serialize(buildJsonObject { put("x", Long.MIN_VALUE) }))
    }

    @Test
    fun `null boolean and array order serialize canonically without whitespace`() {
        val obj = buildJsonObject {
            put("n", JsonNull)
            put("t", true)
            put("f", false)
            put("arr", buildJsonArray { add(JsonPrimitive(3)); add(JsonPrimitive(1)); add(JsonPrimitive(2)) })
        }
        // 数组顺序 = 调用方给定顺序（[3,1,2] 不重排）；键照排；无任何空白。
        assertEquals("{\"arr\":[3,1,2],\"f\":false,\"n\":null,\"t\":true}", CanonicalJson.serialize(obj))
    }

    @Test
    fun `key order is UTF-16 code units not code points`() {
        // U+10000（代理对，首码元 D800）在 UTF-16 码元序下排在 U+E000（单码元）之前；码点序恰好相反。
        // 这一对键让「UTF-16 码元序」这个冻结比较器契约自校验——纯 ASCII 键区分不了两种序。
        val astralKey = StringBuilder().appendCodePoint(0x10000).toString()
        val bmpKey = 0xE000.toChar().toString()
        val obj = buildJsonObject { put(bmpKey, 2); put(astralKey, 1) }
        assertEquals("{\"" + astralKey + "\":1,\"" + bmpKey + "\":2}", CanonicalJson.serialize(obj))
    }

    @Test
    fun `astral pairs serialize raw and pin the golden hash`() {
        // U+1F600（合法代理对）不转义、按 UTF-8 原样输出；期望哈希由独立 Python 实现预先算出。
        // 全 ASCII 源码构造（appendCodePoint），防编码链坑。
        val emoji = StringBuilder().appendCodePoint(0x1F600).toString()
        val canon = CanonicalJson.serialize(buildJsonObject { put("e", emoji) })
        assertEquals("{\"e\":\"" + emoji + "\"}", canon)
        assertEquals("47a47202d021be06dcb0cf0f7f943a022eb9bb434ef039969ffaa344b9280dca", sha256Hex(canon))
    }

    @Test
    fun `lone surrogates are rejected as malformed unicode`() {
        // UTF-8 编码把孤立代理项静默替换成 ?——两个不同的 canonical 串会同哈希，摧毁防篡改自证，
        // 必须拒绝（RFC 8785 要求良构 Unicode）。JDK 17 实测：x+U+D800+y 与 x+U+DC00+y 编码后字节相同。
        val loneHigh = "x" + 0xD800.toChar() + "y"
        val loneLow = "x" + 0xDC00.toChar() + "y"
        val highAtEnd = "x" + 0xD83D.toChar()
        assertFailsWith<IllegalArgumentException> { CanonicalJson.serialize(buildJsonObject { put("k", loneHigh) }) }
        assertFailsWith<IllegalArgumentException> { CanonicalJson.serialize(buildJsonObject { put("k", loneLow) }) }
        assertFailsWith<IllegalArgumentException> { CanonicalJson.serialize(buildJsonObject { put("k", highAtEnd) }) }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `non-canonical integer spellings are rejected not rewritten`() {
        // 静默改写（007 -> 7）会让两份不同文本无诊断地哈希到同一值；与拒非整数/拒键冲突同一立场。
        for (body in listOf("007", "+5", "-0", "1e2")) {
            assertFailsWith<IllegalArgumentException>("body=" + body) {
                CanonicalJson.serialize(buildJsonObject { put("x", JsonUnquotedLiteral(body)) })
            }
        }
    }
}

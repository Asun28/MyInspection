package nz.myinspection.core.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RFC 9562 UUIDv7 契约测试（七项）：
 * 固定向量（全 128 位逐字节）/ version 位 / variant 位 / 唯一性 / 同毫秒非降序 / 时钟回拨冻结 / 计数器耗尽不环绕。
 *
 * L165：断言面必须恰好等于被测契约。固定向量与时钟回拨两项都靠注入 [ClockMs]（+ 固定向量额外注入
 * [Uuid7RandomSource]）拿到确定性输出，而不是 sleep 或只断言"两个 UUID 不同"——那种写法在生成器被换成
 * 随机 UUID 后照样通过，等于没测。固定向量的期望值在测试里独立重新按 RFC 9562 位布局算一遍（不调用
 * 生产代码的私有方法），这样才能真正抓到"实现算错了"，而不仅仅是"实现返回了某个值、我们照抄它当期望值"。
 */
class Uuid7GeneratorTest {

    /** 可控时钟：按构造顺序逐个吐出时间戳，耗尽后冻结在最后一个值上（供多次调用复用）。 */
    private class FakeClock(vararg timestamps: Long) : ClockMs {
        private val queue = ArrayDeque(timestamps.toList())
        override fun nowMs(): Long = if (queue.size > 1) queue.removeFirst() else queue.first()
    }

    /** 可控随机源：按构造顺序逐个吐出 64 位值；用尽即报错（防止某条用例悄悄少算/多算取数次数）。 */
    private class FakeRandomSource(vararg values: Long) : Uuid7RandomSource {
        private val queue = ArrayDeque(values.toList())
        override fun nextLong(): Long =
            queue.removeFirstOrNull() ?: error("FakeRandomSource 已耗尽——用例对取数次数的估计有误")
    }

    /**
     * 独立按 RFC 9562 位布局重算期望的 UUID 字符串（不调用 [Uuid7Generator] 的私有实现），
     * 供固定向量测试逐字节比对。
     */
    private fun expectedUuid(timestampMs: Long, counterSeed: Long, lowRandom32Seed: Long): String {
        val counter = counterSeed and 0x3FFFFFFFFFFL // 42 位
        val ts = timestampMs and 0xFFFFFFFFFFFFL // 48 位

        val bytes = ByteArray(16)
        for (i in 0..5) {
            bytes[i] = ((ts ushr (40 - i * 8)) and 0xFF).toByte()
        }
        val randA = (counter ushr 30) and 0xFFFL
        bytes[6] = (0x70 or ((randA ushr 8).toInt() and 0x0F)).toByte()
        bytes[7] = (randA and 0xFF).toByte()

        val counterLow30 = counter and 0x3FFFFFFFL
        val randBLow32 = lowRandom32Seed and 0xFFFFFFFFL
        val randB = (counterLow30 shl 32) or randBLow32

        bytes[8] = (0x80 or ((randB ushr 56).toInt() and 0x3F)).toByte()
        bytes[9] = ((randB ushr 48) and 0xFF).toByte()
        bytes[10] = ((randB ushr 40) and 0xFF).toByte()
        bytes[11] = ((randB ushr 32) and 0xFF).toByte()
        bytes[12] = ((randB ushr 24) and 0xFF).toByte()
        bytes[13] = ((randB ushr 16) and 0xFF).toByte()
        bytes[14] = ((randB ushr 8) and 0xFF).toByte()
        bytes[15] = (randB and 0xFF).toByte()

        val hex = buildString(32) { for (b in bytes) append("%02x".format(b)) }
        return buildString(36) {
            append(hex, 0, 8); append('-')
            append(hex, 8, 12); append('-')
            append(hex, 12, 16); append('-')
            append(hex, 16, 20); append('-')
            append(hex, 20, 32)
        }
    }

    @Test
    fun `fixed vector - full 128-bit output is byte-exact`() {
        val fixedMs = 1_734_000_000_000L
        val counterSeed = 0x0ABCDEF012345L // 高于 42 位的部分会被掩掉，故意留几个高位试探掩码是否真的生效
        val lowRandomSeed = 0x1122_3344_5566_7788L
        val generator = Uuid7Generator(
            clock = FakeClock(fixedMs),
            randomSource = FakeRandomSource(counterSeed, lowRandomSeed),
        )

        val actual = generator.next()
        val expected = expectedUuid(fixedMs, counterSeed, lowRandomSeed)

        assertEquals(expected, actual, "全 128 位输出必须与独立重算的期望值逐字节一致")
        assertEquals('7', actual[14], "version 半字节必须是 7")
        val variantNibble = actual[19].digitToInt(16)
        assertTrue(variantNibble in 8..11, "variant 半字节必须落在 0b10xx（8..B）")
    }

    @Test
    fun `version nibble is always 7`() {
        val generator = Uuid7Generator()
        repeat(200) {
            assertEquals('7', generator.next()[14])
        }
    }

    @Test
    fun `variant bits are always binary 10`() {
        val generator = Uuid7Generator()
        repeat(200) {
            val uuid = generator.next()
            val variantNibble = uuid[19].digitToInt(16)
            assertTrue(variantNibble in 8..11, "variant nibble 0x${variantNibble.toString(16)} not in [8,B] (0b10xx)")
        }
    }

    @Test
    fun `generated ids are unique under load`() {
        val generator = Uuid7Generator()
        val ids = (1..20_000).map { generator.next() }
        assertEquals(ids.size, ids.toSet().size, "duplicate UUIDv7 values generated")
    }

    @Test
    fun `same millisecond ids sort strictly increasing`() {
        val fixedMs = 1_734_000_000_000L
        val generator = Uuid7Generator(clock = FakeClock(fixedMs))
        val ids = (1..500).map { generator.next() }
        assertEquals(ids, ids.sorted(), "ids generated within the same frozen millisecond must be strictly increasing")
        assertEquals(ids.size, ids.toSet().size, "monotonic counter must not repeat within the same millisecond")
    }

    @Test
    fun `clock regression freezes at the last observed timestamp`() {
        val laterMs = 1_734_000_000_000L
        val earlierMs = laterMs - 60_000L // simulated backward clock jump, no sleeping / raciness
        val generator = Uuid7Generator(clock = FakeClock(laterMs, earlierMs))

        val first = generator.next() // observes laterMs
        val second = generator.next() // clock has "gone backward"; must freeze at laterMs

        assertEquals(first.substring(0, 13), second.substring(0, 13), "timestamp+version prefix must not regress")
        assertTrue(second > first, "ids must remain monotonic across a clock regression")
    }

    @Test
    fun `counter exhaustion within a frozen millisecond advances time instead of wrapping`() {
        // 计数器种子若恰好取到 COUNTER_MASK（42 位全 1），同毫秒内下一次递增会越界；这里显式把种子钉在
        // 上限，确定性地触发耗尽分支（不环绕，见 Uuid7Generator 文档）。
        val fixedMs = 1_734_000_000_000L
        val maxCounterSeed = 0x3FFFFFFFFFFL // 42 位全 1，即 COUNTER_MASK 本身
        val generator = Uuid7Generator(
            clock = FakeClock(fixedMs),
            randomSource = FakeRandomSource(
                maxCounterSeed, 0x1111_1111L, // 第一枚：换种子（=上限）+ 低 32 位
                0x2222_2222_2222_2222L, 0x3333_3333L, // 第二枚：耗尽后换的新种子 + 低 32 位
            ),
        )

        val first = generator.next()
        val second = generator.next()

        assertTrue(second > first, "counter overflow must still yield a strictly greater id, never wrap to a smaller one")
        // 耗尽分支把内部时间戳前推了 1ms；第二枚的时间戳前缀必须严格大于第一枚（而非相等，那是"未耗尽"的形态；
        // 也不能倒退）。
        val firstTsHex = first.substring(0, 8) + first.substring(9, 13)
        val secondTsHex = second.substring(0, 8) + second.substring(9, 13)
        assertTrue(secondTsHex > firstTsHex, "counter exhaustion must bump the internal timestamp forward by 1ms, not wrap")
    }
}

package nz.myinspection.core.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RFC 9562 UUIDv7 契约测试：固定向量（全 128 位）/ version 位 / variant 位 / 唯一性 /
 * 同毫秒非降序 / 时钟回拨冻结 / 计数器耗尽不环绕。
 *
 * 固定向量与时钟回拨两项靠注入 [ClockMs]（固定向量再注入 [Uuid7RandomSource]）拿到确定性输出，
 * 不用 sleep 或只断言"两个 UUID 不同"。固定向量的期望值是外部（Python，与本文件的实现语言、
 * 代码路径都无关）独立算出来再抄成字面量的，不是同一套公式在 Kotlin 里抄两遍——那样两处共享
 * 同一个位布局理解错误时会一起测过。
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

    @Test
    fun `fixed vector - full 128-bit output is byte-exact`() {
        // 时间戳 1734000000000ms + 计数器种子 0x0ABCDEF0123 + rand_b 低 32 位 0x11223344，
        // 按 RFC 9562 位布局用 Python 独立算出的期望值（脚本见本卡交付说明，不在本文件重复）。
        val fixedMs = 1_734_000_000_000L
        val counterSeed = 0x0ABCDEF0123L
        val lowRandomSeed = 0x11223344L
        val generator = Uuid7Generator(
            clock = FakeClock(fixedMs),
            randomSource = FakeRandomSource(counterSeed, lowRandomSeed),
        )

        val actual = generator.next()

        assertEquals("0193ba74-3c00-72af-8def-012311223344", actual, "全 128 位输出必须与外部独立算出的期望值逐字节一致")
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
        // 计数器种子钉在 COUNTER_MASK（42 位全 1），确定性触发耗尽分支（不环绕，见 Uuid7Generator 文档）。
        val fixedMs = 1_734_000_000_000L
        val maxCounterSeed = 0x3FFFFFFFFFFL
        val generator = Uuid7Generator(
            clock = FakeClock(fixedMs),
            randomSource = FakeRandomSource(
                maxCounterSeed, 0x1111_1111L,
                0x2222_2222_2222_2222L, 0x3333_3333L,
            ),
        )

        val first = generator.next()
        val second = generator.next()

        assertTrue(second > first, "counter overflow must still yield a strictly greater id, never wrap to a smaller one")
        val firstTsHex = first.substring(0, 8) + first.substring(9, 13)
        val secondTsHex = second.substring(0, 8) + second.substring(9, 13)
        assertTrue(secondTsHex > firstTsHex, "counter exhaustion must bump the internal timestamp forward by 1ms, not wrap")
    }
}

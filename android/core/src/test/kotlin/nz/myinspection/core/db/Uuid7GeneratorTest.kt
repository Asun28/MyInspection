package nz.myinspection.core.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RFC 9562 UUIDv7 六项契约测试（T1-SCHEMA-CORE dod_assert）：
 * 固定向量 / version 位 / variant 位 / 唯一性 / 同毫秒非降序 / 时钟回拨冻结。
 *
 * L165：断言面必须恰好等于被测契约。固定向量与时钟回拨两项都靠注入 [ClockMs] 拿到确定性的
 * 时间戳，而不是 sleep 或只断言"两个 UUID 不同"——那种写法在生成器被换成随机 UUID 后照样通过，
 * 等于没测。
 */
class Uuid7GeneratorTest {

    /** 可控时钟：按构造顺序逐个吐出时间戳，耗尽后冻结在最后一个值上（供多次调用复用）。 */
    private class FakeClock(vararg timestamps: Long) : ClockMs {
        private val queue = ArrayDeque(timestamps.toList())
        override fun nowMs(): Long = if (queue.size > 1) queue.removeFirst() else queue.first()
    }

    @Test
    fun `fixed vector - timestamp and version prefix are byte-exact`() {
        val fixedMs = 1_734_000_000_000L
        val generator = Uuid7Generator(clock = FakeClock(fixedMs))
        val uuid = generator.next()

        val expectedTsHex = buildString {
            for (i in 0..5) {
                val shift = 40 - i * 8
                append("%02x".format((fixedMs ushr shift) and 0xFF))
            }
        }
        val actualTsHex = uuid.substring(0, 8) + uuid.substring(9, 13)
        assertEquals(expectedTsHex, actualTsHex, "first 48 bits must be the exact big-endian ms timestamp")
        assertEquals('7', uuid[14], "version nibble (first char of 3rd group) must be 7")
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
}

package nz.myinspection.core.db

import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicReference

/**
 * 毫秒级时钟源，可注入（供测试钉死固定时刻 / 确定性模拟时钟回拨，见 Uuid7GeneratorTest）。
 * L165：断言面必须恰好等于被测契约——固定向量与时钟回拨两项测试都要靠注入时钟拿到确定性时间戳，
 * 不能靠 sleep 或只断言"生成的两个值不同"（那种写法在实现被换掉后照样通过，等于没测）。
 */
fun interface ClockMs {
    fun nowMs(): Long
}

/** 默认时钟：JVM 系统时钟。 */
object SystemClockMs : ClockMs {
    override fun nowMs(): Long = System.currentTimeMillis()
}

/**
 * RFC 9562 UUIDv7 自研生成器（ADR-0003，2-1 决；不用第三方库，见卡片"若评审判不可靠可换
 * `uuid-creator`(MIT) 的 `getTimeOrderedEpoch()`"备选）。
 *
 * 128 位布局（16 字节，big-endian）：
 *  - bytes[0..5]  （48 位）：unix_ts_ms
 *  - bytes[6]     高 4 位 = version 0111（7）；低 4 位 + bytes[7] = rand_a（12 位）
 *  - bytes[8]     高 2 位 = variant 10；其余位 + bytes[9..15] = rand_b（62 位）
 *
 * 单调性：同一观测毫秒内（含时钟回拨后冻结的那个毫秒），不重新随机化 rand_a/rand_b 的高位，而是
 * 把它们当作一个 42 位计数器递增（RFC 9562 "Method 1: Fixed-Length Dedicated Counter"）——只用 12 位
 * rand_a 做计数器在同毫秒内快速批量生成时会在数千次内环绕、产生非单调值（真实 bug，非假设），42 位
 * 计数器把环绕推到不可能在任何真实同毫秒批次触达的量级。计数器之外的低位仍每次取新鲜随机数，保持
 * 不可预测性。时钟回拨：若观测时间早于上次记录的时间戳，冻结在上次时间戳（不倒退）。
 */
class Uuid7Generator(
    private val clock: ClockMs = SystemClockMs,
    private val random: SecureRandom = SecureRandom(),
) {
    private data class State(val timestampMs: Long, val counter: Long)

    private val last = AtomicReference(State(timestampMs = -1L, counter = -1L))

    /** 生成一枚 canonical 小写 UUIDv7 字符串（`xxxxxxxx-xxxx-7xxx-yxxx-xxxxxxxxxxxx`）。 */
    fun next(): String {
        while (true) {
            val prev = last.get()
            val observedMs = clock.nowMs()
            // 时钟回拨=沿用上一时间戳（冻结语义，卡片已定）。
            val timestampMs = if (observedMs > prev.timestampMs) observedMs else prev.timestampMs
            val counter = if (timestampMs == prev.timestampMs) {
                (prev.counter + 1) and COUNTER_MASK
            } else {
                random.nextLong() and COUNTER_MASK
            }
            val candidate = State(timestampMs, counter)
            if (last.compareAndSet(prev, candidate)) {
                return format(timestampMs, counter)
            }
            // CAS 输给了并发的另一次调用，用刷新后的状态重试。
        }
    }

    private fun format(timestampMs: Long, counter: Long): String {
        val bytes = ByteArray(16)

        val ts = timestampMs and TIMESTAMP_MASK
        for (i in 0..5) {
            bytes[i] = ((ts ushr (40 - i * 8)) and 0xFF).toByte()
        }

        // 计数器高 12 位 = rand_a；version 0111 占 bytes[6] 高 4 位。
        val randA = (counter ushr COUNTER_LOW_BITS) and 0xFFFL
        bytes[6] = (0x70 or ((randA ushr 8).toInt() and 0x0F)).toByte()
        bytes[7] = (randA and 0xFF).toByte()

        // 计数器低 30 位构成 rand_b 的高 30 位；rand_b 剩余 32 位每次取新鲜随机数。
        val counterLow30 = counter and COUNTER_LOW_MASK
        val freshRandom32 = random.nextInt().toLong() and 0xFFFFFFFFL
        val randB = (counterLow30 shl 32) or freshRandom32 // 62 有效位

        bytes[8] = (0x80 or ((randB ushr 56).toInt() and 0x3F)).toByte() // variant 10 + 高 6 位
        bytes[9] = ((randB ushr 48) and 0xFF).toByte()
        bytes[10] = ((randB ushr 40) and 0xFF).toByte()
        bytes[11] = ((randB ushr 32) and 0xFF).toByte()
        bytes[12] = ((randB ushr 24) and 0xFF).toByte()
        bytes[13] = ((randB ushr 16) and 0xFF).toByte()
        bytes[14] = ((randB ushr 8) and 0xFF).toByte()
        bytes[15] = (randB and 0xFF).toByte()

        return toUuidString(bytes)
    }

    private fun toUuidString(bytes: ByteArray): String {
        val hex = buildString(32) { for (b in bytes) append("%02x".format(b)) }
        return buildString(36) {
            append(hex, 0, 8); append('-')
            append(hex, 8, 12); append('-')
            append(hex, 12, 16); append('-')
            append(hex, 16, 20); append('-')
            append(hex, 20, 32)
        }
    }

    private companion object {
        /** 48 位毫秒时间戳掩码。用变量掩码而非超出 Long 范围的字面量（见草稿的溢出坑，本卡明确修正）。 */
        const val TIMESTAMP_MASK = 0xFFFFFFFFFFFFL
        /** 42 位单调计数器掩码：12 位 rand_a + 30 位 rand_b 高段。 */
        const val COUNTER_MASK = 0x3FFFFFFFFFFL
        const val COUNTER_LOW_BITS = 30
        const val COUNTER_LOW_MASK = 0x3FFFFFFFL
    }
}

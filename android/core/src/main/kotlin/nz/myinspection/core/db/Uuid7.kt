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
 * 随机位源：每次 `next()` 调用，[Uuid7Generator] 从这里取 64 位随机值、按需只用低 N 位——
 * 换新计数器种子（42 位）取一次，rand_b 低 32 位再取一次。生产默认 [SecureUuid7Random]；
 * 测试注入固定序列，让"固定向量"测试能对完整 128 位输出逐字节断言，而不只测时间戳前缀
 * （R3 评审指出：只测前缀 = rand_a/rand_b 打包逻辑从未被验证过）。
 */
fun interface Uuid7RandomSource {
    fun nextLong(): Long
}

/** 生产默认随机源：JVM `SecureRandom`（卡片已定，ADR-0003）。 */
object SecureUuid7Random : Uuid7RandomSource {
    private val secureRandom = SecureRandom()
    override fun nextLong(): Long = secureRandom.nextLong()
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
 * 把它们当作一个 42 位计数器递增（RFC 9562 "Method 1: Fixed-Length Dedicated Counter"）。
 * **计数器耗尽处理**（R3 评审指出的真实 bug，已修正）：计数器种子是随机取的 42 位值，若恰好取到
 * 贴近上限的起点，同毫秒内递增几次就会越过 [COUNTER_MASK]——旧实现对此取模环绕，会把计数器绕回
 * 一个更小的值，产出的 UUID 反而变小，破坏单调性。改法：递增会越界时，不环绕，而是把内部记录的
 * 时间戳前推 1ms 并换一枚新种子——序列依旧严格递增；真实时钟追上后自然接续，不产生可观测的错误。
 * 计数器之外的低位每次调用都取新鲜随机数，保持不可预测性。时钟回拨：若观测时间早于上次记录的时间戳，
 * 冻结在上次时间戳（不倒退，卡片已定语义）。
 */
class Uuid7Generator(
    private val clock: ClockMs = SystemClockMs,
    private val randomSource: Uuid7RandomSource = SecureUuid7Random,
) {
    private data class State(val timestampMs: Long, val counter: Long)

    private val last = AtomicReference(State(timestampMs = -1L, counter = -1L))

    /** 生成一枚 canonical 小写 UUIDv7 字符串（`xxxxxxxx-xxxx-7xxx-yxxx-xxxxxxxxxxxx`）。 */
    fun next(): String {
        while (true) {
            val prev = last.get()
            val observedMs = clock.nowMs()
            // 时钟回拨=沿用上一时间戳（冻结语义，卡片已定）。
            var timestampMs = if (observedMs > prev.timestampMs) observedMs else prev.timestampMs
            val counter: Long
            if (timestampMs == prev.timestampMs) {
                val incremented = prev.counter + 1
                if (incremented > COUNTER_MASK) {
                    // 计数器耗尽：前推 1ms + 换新种子，绝不环绕（环绕=非单调，真实 bug 见上方文档）。
                    timestampMs += 1
                    counter = randomSource.nextLong() and COUNTER_MASK
                } else {
                    counter = incremented
                }
            } else {
                counter = randomSource.nextLong() and COUNTER_MASK
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
        val freshRandom32 = randomSource.nextLong() and 0xFFFFFFFFL
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

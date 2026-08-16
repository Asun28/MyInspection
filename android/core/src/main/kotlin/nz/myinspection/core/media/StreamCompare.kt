package nz.myinspection.core.media

import java.io.InputStream

/**
 * 逐块比对两条流的字节。「目标文件已存在」时要判断是不是同一次写入的幂等重试，只能把两份内容都读一遍——
 * 整份读进内存会让这条路径自己变成 OOM 源（证据照片可以很大），故按固定大小的块流式比对，峰值内存恒为
 * 两个 [CHUNK_BYTES] 缓冲。:core 只认流，打开文件是 :app 的事。
 */
object StreamCompare {
    const val CHUNK_BYTES: Int = 64 * 1024

    /** 两条流剩余内容完全相同（含都为空）时返回 true。不关闭任何一条流——谁打开谁负责关。 */
    fun contentEquals(a: InputStream, b: InputStream): Boolean {
        val left = ByteArray(CHUNK_BYTES)
        val right = ByteArray(CHUNK_BYTES)
        while (true) {
            val leftRead = fill(a, left)
            val rightRead = fill(b, right)
            if (leftRead != rightRead) return false
            if (leftRead == 0) return true
            for (i in 0 until leftRead) if (left[i] != right[i]) return false
        }
    }

    /**
     * 填满缓冲或读到流尾为止，返回实际读到的字节数。**必须循环**：`InputStream.read` 允许只交出一部分，
     * 把一次短读当成流尾会让两条内容相同的流被判为长度不同。
     * 不用 `InputStream.readNBytes`：Android API 33+ 才有，minSdk 26 上会运行时崩（L217）。
     */
    private fun fill(input: InputStream, buffer: ByteArray): Int {
        var filled = 0
        while (filled < buffer.size) {
            val read = input.read(buffer, filled, buffer.size - filled)
            if (read < 0) break
            filled += read
        }
        return filled
    }
}

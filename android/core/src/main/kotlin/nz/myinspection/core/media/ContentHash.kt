package nz.myinspection.core.media

import java.security.MessageDigest

/**
 * 内容哈希：字节 → SHA-256 十六进制小写串。相机路径喂**烘焙后**字节、导入路径喂**原始**字节（卡片上下文包）。
 *
 * 不用 `java.util.HexFormat`：Android API 34+ 才有，minSdk 26 上会在运行时崩（L217）。
 */
object ContentHash {
    fun sha256Hex(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    /** 摘要已由调用方流式算出时（`DigestInputStream` 边拷边摘要）复用同一套格式化，两条路径形态一致。 */
    fun hex(digestBytes: ByteArray): String = buildString(digestBytes.size * 2) {
        for (b in digestBytes) append("%02x".format(b))
    }
}

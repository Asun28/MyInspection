package nz.myinspection.core.media

import java.security.MessageDigest

/**
 * 内容哈希：纯函数，字节 → SHA-256 十六进制小写串。相机路径喂**烘焙后**字节，导入路径喂**原始**字节
 * （由调用方决定喂什么，本函数只管字节→哈希，见卡片上下文包）。
 *
 * 不用 `java.util.HexFormat`：Android API 34+ 才有，minSdk 26 上会在运行时崩（L217）。
 */
object ContentHash {
    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return buildString(digest.size * 2) {
            for (b in digest) append("%02x".format(b))
        }
    }
}

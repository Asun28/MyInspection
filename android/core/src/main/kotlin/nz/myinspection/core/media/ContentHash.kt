package nz.myinspection.core.media

import java.security.MessageDigest

/**
 * 内容哈希：纯函数，字节 → SHA-256 十六进制小写串。相机路径喂**烘焙后**字节，导入路径喂**原始**字节
 * （由调用方决定喂什么，本函数只管字节→哈希，见卡片上下文包）。
 *
 * 不用 `java.util.HexFormat`：Android API 34+ 才有，minSdk 26 上会在运行时崩（L217）。
 */
object ContentHash {
    fun sha256Hex(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    /**
     * 十六进制格式化复用点：调用方已自行算出摘要字节时（如流式读大文件用 `DigestInputStream` 边拷贝边摘要，
     * 避免整份文件读进内存），复用同一套格式化，保证两条计算路径产出完全一致的字符串形态。
     */
    fun hex(digestBytes: ByteArray): String = buildString(digestBytes.size * 2) {
        for (b in digestBytes) append("%02x".format(b))
    }
}

package nz.myinspection.core.backup.format

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.Normalizer

/**
 * ★ 加密备份包格式的冻结常量（T5-BACKUP-FORMAT · ADR-0002/0003）。
 *
 * **格式是跨年契约**：今年导出的包必须能被未来任意版本读回。所以这里每个数字都是格式的一部分，
 * 改动 = 新 [FORMAT_VERSION] + 版本评审，不是「调参」。
 *
 * 包形态：
 * ```
 * [明文头 47 字节] [密文块 0] [密文块 1] ... [密文块 n（final）]
 * ```
 * 明文头见 [BackupHeader]；密文块见 [ChunkedGcmOutputStream]：每块 = AES-256-GCM(明文块) || 16 字节 tag，
 * 全部块的明文拼起来是一份 zip 流（首条目 `manifest.json`，其余按 rel_path 全序）。
 *
 * **为什么是分块 AEAD 而不是一路 CipherOutputStream/CipherInputStream**（对卡片上下文包里那句实现草图的
 * 唯一偏离，且是为了兑现卡片自己的硬不变量「全程恒定内存、禁整包入内存」）：
 * JCE 的 AEAD 语义要求「tag 验过之后才交出明文」，因此 GCM **解密**方向会把整份密文缓冲在 Cipher 内部
 * （实测 JDK 17.0.20/SunJCE：`update(1 MiB)` 交出 0 字节，全部在 `doFinal` 才吐出）；Android 侧的
 * Conscrypt 更直接——AEAD 走 `EVP_AEAD_CTX_seal/open`，一次吃下整个缓冲区，加密方向同样如此。
 * 照片总量是 GB 级，那意味着必 OOM。分块 AEAD（STREAM 构造，age / Tink 同款）把它压回恒定内存：
 * 每块独立 nonce = 前缀 || 块序号 || final 标志，于是**块的顺序、块的数量、以及「哪一块是最后一块」
 * 都被 GCM tag 认证**——重排、丢块、截断、追加都会当场 tag 失败。
 * 这仍然只是**组合 javax.crypto 标准件**（AES/GCM/NoPadding + PBKDF2WithHmacSHA256 + SecureRandom），
 * 没有自研密码学原语，也没有新增依赖（卡片 forbid）。
 *
 * ## ★ zip 容器的地位：运输信封，其自描述元数据**非规范性**（冻结契约的一部分）
 *
 * 包内的 zip 只是认证加密层**内部**的运输信封。它的中央目录（central directory）与 EOCD
 * **不是规范的一部分**：读取器按 local header 流式读，**`manifest.json` 是包内容的唯一权威**——
 * 只有它声明过、且逐字节核过 `rel_path` / `size_bytes` / SHA-256、并通过双向完备性检查
 * （包内不得有未声明的条目、manifest 声明的条目不得缺失）的文件才会被交付。
 *
 * **禁止任何未来实现去信任中央目录。** 这条不是省事，是把 zip 历来的
 * 「local header 与 central directory 各说各话」歧义面（Android APK 的 Master Key 类问题）**永久封死**：
 * 只要权威只有一个，两个解析器就不可能对「包里有哪些条目」得出不同答案。
 * 相应地，尾部被删/被插入垃圾/被追加明文的包**会被接受**（它们都带合法 GCM tag，只有握有口令的人造得出，
 * 且尾部字节永远不会被交付）——这是**有意的**契约，由 `BackupZipTailTest` 逐形态钉住，不是没人看过的默认。
 */
object BackupFormat {
    /** 明文头魔数（ASCII 8 字节）。 */
    const val MAGIC_ASCII = "MYINSPBK"

    /** 本版格式版本号。读到别的值 = 明确报错，绝不猜着解析。 */
    const val FORMAT_VERSION = 1

    /** 口令派生算法编号：1 = PBKDF2WithHmacSHA256。2 起留给 Argon2 等升级（需第三方库，走许可闸+版本评审）。 */
    const val KDF_PBKDF2_HMAC_SHA256 = 1

    /** 本版默认迭代数。迭代数写进头，所以它可以逐年上调而不破坏旧包的兼容性。 */
    const val DEFAULT_KDF_ITERATIONS = 210_000

    /** 迭代数下界（测试用小迭代数是合法的——头里带数）。 */
    const val MIN_KDF_ITERATIONS = 1

    /**
     * 迭代数上界：敌意的头能把 u32 填成十亿级，没有上界就是一次解包挂死。
     * 取 400 万 = 默认值的约 20 倍：够容纳未来多年上调，同时把一份被改过的头能耗掉的时间
     * 压在中端机数秒量级。要更高的迭代数就是换 KDF（`kdf_id`）的事，走 format_version 评审。
     */
    const val MAX_KDF_ITERATIONS = 4_000_000

    const val SALT_BYTES = 16

    /** 每包随机的 nonce 前缀；每块的完整 12 字节 nonce = 前缀(7) || 块序号(4, 大端) || final 标志(1)。 */
    const val NONCE_PREFIX_BYTES = 7

    /** 口令校验值：PBKDF2 输出的尾 8 字节，用来把「口令错」与「包被改过」分成两种诊断。 */
    const val VERIFIER_BYTES = 8

    const val KEY_BYTES = 32

    const val GCM_TAG_BITS = 128

    /** GCM nonce 长度（12 = 前缀 7 + 块序号 4 + final 标志 1）。 */
    const val GCM_NONCE_BYTES = 12

    const val GCM_TAG_BYTES = GCM_TAG_BITS / 8

    const val HEADER_BYTES = 8 + 2 + 2 + 4 + SALT_BYTES + NONCE_PREFIX_BYTES + VERIFIER_BYTES

    /** 每个密文块的明文长度（最后一块可短）。属于格式契约：读取器据此切块。 */
    const val CHUNK_PLAINTEXT_BYTES = 64 * 1024

    /** 块序号占 4 字节大端，故最多 2^32 块（约 256 TiB）。越界即拒，绝不让序号回绕（nonce 重用=GCM 全盘失守）。 */
    const val MAX_CHUNK_INDEX = 0xFFFFFFFFL

    /** 包内 manifest 的条目名（必须是 zip 首条目）。 */
    const val MANIFEST_ENTRY = "manifest.json"

    /** 包内数据库快照的条目名（v1 恢复语义 = 整包替换，故它是必需条目）。 */
    const val DB_ENTRY = "db.sqlite"

    const val PHOTOS_AREA = "photos/"

    const val AUDIO_AREA = "audio/"

    const val CONFIGS_AREA = "configs/"

    /** 除 [DB_ENTRY] 外，包内文件只能落在这几个顶层区域；加区域 = 新 format_version。 */
    val FILE_AREAS: List<String> = listOf(PHOTOS_AREA, AUDIO_AREA, CONFIGS_AREA)

    /** manifest 会整份进内存（它是元数据），故对敌意输入设硬上限。 */
    const val MAX_MANIFEST_BYTES = 64 * 1024 * 1024

    const val MAX_REL_PATH_CHARS = 1024

    const val COPY_BUFFER_BYTES = 64 * 1024

    internal val MAGIC_BYTES: ByteArray = MAGIC_ASCII.toByteArray(Charsets.US_ASCII)
}

/** 备份格式层的异常根。都是 [IOException] 的子类：对调用方而言，读写备份就是一次 IO。 */
sealed class BackupException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** 结构/字段非法：不是备份包、版本读不懂、manifest 形态不对、写入方给的声明自相矛盾。 */
class BackupFormatException(message: String, cause: Throwable? = null) : BackupException(message, cause)

/**
 * 口令不对——**或者头里的盐/迭代数/校验值被改过**。这两件事在密码学上不可区分：口令校验值是由
 * 「口令 + 头里的盐与迭代数」算出来的，改任何一样都得到同一个「对不上」。本层不假装能分辨，
 * 只给出一个可行动的信号（让用户重输口令）；若口令确实没错，那就是包坏了，改用别的备份。
 * **无口令找回**（无服务端，ADR-0002），格式层也不留后门。
 */
class WrongPassphraseException(message: String) : BackupException(message)

/**
 * 读取来源那侧的 IO 故障（SAF 授权被收回、文件被拔走、网络盘断线）。
 * 与 [BackupSinkException] 对称，**刻意不属于 [BackupException]**：把来源故障报成「备份包损坏」，
 * 会让用户以为备份废了而去删掉一份其实完好的包。
 */
class BackupSourceException(message: String, cause: Throwable) : IOException(message, cause)

/** 包损坏或被篡改：GCM tag 失败、块被重排/丢失/截断、包内容与 manifest 不符。 */
class BackupCorruptException(message: String, cause: Throwable? = null) : BackupException(message, cause)

/**
 * 调用方那侧的 IO 故障（磁盘满、SAF 授权被收回、sink 主动中止）。
 * **刻意不属于 [BackupException]**：把调用方的故障报成「备份包损坏」，会让用户去删一份其实完好的备份。
 */
class BackupSinkException(message: String, cause: Throwable) : IOException(message, cause)

private val HEX_DIGITS = "0123456789abcdef".toCharArray()

/** 小写十六进制。刻意不用 java.util.HexFormat：它在 Android 上要 API 34，而 minSdk 26（L217）。 */
internal fun toHexLower(bytes: ByteArray): String {
    val chars = CharArray(bytes.size * 2)
    for (i in bytes.indices) {
        val value = bytes[i].toInt() and 0xFF
        chars[i * 2] = HEX_DIGITS[value ushr 4]
        chars[i * 2 + 1] = HEX_DIGITS[value and 0x0F]
    }
    return String(chars)
}

/** 迭代数的**唯一**判定点（头解码与口令派生共用，免得两处规则漂移）。 */
internal fun checkIterations(iterations: Int): Int {
    if (iterations < BackupFormat.MIN_KDF_ITERATIONS || iterations > BackupFormat.MAX_KDF_ITERATIONS) {
        throw BackupFormatException(
            "PBKDF2 迭代数须在 [${BackupFormat.MIN_KDF_ITERATIONS}, ${BackupFormat.MAX_KDF_ITERATIONS}] 内，实得 $iterations",
        )
    }
    return iterations
}

/**
 * rel_path 的**唯一**闸门：zip-slip、绝对路径、盘符、控制字符、非 NFC、白名单外的顶层区域，一律拒。
 * 读取器只会把经此校验过的路径交给调用方，所以恢复时不可能写到归档根之外。
 */
internal fun checkRelPath(path: String): String {
    fun reject(why: String): Nothing = throw BackupFormatException("非法 rel_path（$why）：${safeShow(path)}")
    if (path.isEmpty()) reject("空串")
    if (path.length > BackupFormat.MAX_REL_PATH_CHARS) reject("超过 ${BackupFormat.MAX_REL_PATH_CHARS} 字符")
    if (path != Normalizer.normalize(path, Normalizer.Form.NFC)) reject("必须是 NFC 形态，否则 manifest 里的名字与包里的名字会分家")
    for (ch in path) {
        if (ch.code < 0x20 || ch.code == 0x7F) reject("含控制字符")
        if (ch == '\\') reject("含反斜杠")
        if (ch == ':') reject("含冒号")
    }
    if (path.startsWith("/")) reject("绝对路径")
    if (path.endsWith("/")) reject("目录条目")
    for (segment in path.split('/')) {
        if (segment.isEmpty()) reject("空路径段")
        if (segment == "." || segment == "..") reject("相对段会逃出归档根")
    }
    if (path != BackupFormat.DB_ENTRY && BackupFormat.FILE_AREAS.none { path.startsWith(it) }) {
        reject("顶层区域不在白名单 ${BackupFormat.DB_ENTRY} / ${BackupFormat.FILE_AREAS.joinToString("、")}")
    }
    return path
}

/**
 * 归属由**区域**决定，不由调用方随手填：`db.sqlite` 与 `configs/` 对全库生效（owner 必须为 null），
 * `photos/` 与 `audio/` 必属某个物业（owner 必须非 null）。不钉死这条，按物业过滤就是一句空话——
 * 一张 owner=null 的照片会混进**每一个**按物业包，而一份带 owner 的 configs 会从别的物业包里凭空消失。
 */
internal fun isLibraryAsset(relPath: String): Boolean =
    relPath == BackupFormat.DB_ENTRY || relPath.startsWith(BackupFormat.CONFIGS_AREA)

/**
 * 写侧的 manifest 上限自查：读侧对 manifest 有 [BackupFormat.MAX_MANIFEST_BYTES] 的硬上限，
 * 写侧不自查就会产出「本版自己读不回来」的包——每条目都合法，合起来却越界。
 */
internal fun checkManifestSize(encoded: ByteArray): ByteArray {
    if (encoded.size > BackupFormat.MAX_MANIFEST_BYTES) {
        throw BackupFormatException(
            "manifest 编码后 ${encoded.size} 字节，超过上限 ${BackupFormat.MAX_MANIFEST_BYTES}：" +
                "这样的包同版本读取器会拒收，请分批导出",
        )
    }
    return encoded
}

/** manifest 里的自由文本字段（app_version / property_id）：非空、不超长、NFC、无控制字符。 */
internal fun checkText(value: String, field: String, maxChars: Int): String {
    fun reject(why: String): Nothing = throw BackupFormatException("manifest 字段 $field 非法（$why）：${safeShow(value)}")
    if (value.isEmpty()) reject("空串")
    if (value.length > maxChars) reject("超过 $maxChars 字符")
    if (value != Normalizer.normalize(value, Normalizer.Form.NFC)) reject("必须是 NFC 形态")
    for (ch in value) {
        if (ch.code < 0x20 || ch.code == 0x7F) reject("含控制字符")
    }
    return value
}

/** 错误信息里的用户数据先消毒：带换行的路径能伪造出一整行假日志。 */
internal fun safeShow(raw: String): String {
    val shown = if (raw.length > 120) raw.take(120) + "…" else raw
    return buildString {
        for (ch in shown) append(if (ch.code < 0x20 || ch.code == 0x7F) '?' else ch)
    }
}

/** 有上限地读完一个流。超限即拒——对敌意输入不做无界缓冲。 */
internal fun readAtMost(input: InputStream, limit: Int): ByteArray {
    require(limit >= 0) { "limit 不能为负：$limit" }
    val buffer = ByteArray(limit.coerceIn(1, BackupFormat.COPY_BUFFER_BYTES))
    val collected = ByteArrayOutputStream()
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        if (total > limit) throw BackupFormatException("内容超过 $limit 字节上限（拒绝无界缓冲）")
        collected.write(buffer, 0, read)
    }
    return collected.toByteArray()
}

/**
 * 把调用方的输入流包一层，好让**来源的 IO 故障**（SAF 授权被收回、盘断线）与**包本身损坏**分得开。
 * 不包的话，两者都以 [IOException] 冒出来，读取器只能一律归为「包损坏」——那是会让用户删掉好备份的误诊。
 */
internal class SourceInputStream(private val delegate: InputStream) : InputStream() {
    override fun read(): Int = guard { delegate.read() }

    override fun read(b: ByteArray, off: Int, len: Int): Int = guard { delegate.read(b, off, len) }

    private inline fun guard(block: () -> Int): Int = try {
        block()
    } catch (e: BackupSourceException) {
        throw e
    } catch (e: IOException) {
        throw BackupSourceException("读取备份包来源失败（是来源那侧的 IO 故障，不是包坏了）", e)
    }
}

/** 读满整个缓冲区或读到流尾，返回实际读到的字节数（[InputStream.readNBytes] 要 Android API 33，不能用）。 */
internal fun readUpTo(input: InputStream, buffer: ByteArray): Int {
    var total = 0
    while (total < buffer.size) {
        val read = input.read(buffer, total, buffer.size - total)
        if (read < 0) break
        total += read
    }
    return total
}

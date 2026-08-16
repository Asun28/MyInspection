package nz.myinspection.core.backup.format

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ★ manifest = 包内第一条目、恢复语义的唯一依据。本文件的黄金串**就是 manifest 规范本体**：
 * 键名、键序、字段形态、文件数组的全序，合并即冻结（序列化器复用已冻结的 core/canon）。
 */
class BackupManifestTest {

    private val hDb = "1".repeat(64)
    private val hPhoto = "2".repeat(64)
    private val hAudio = "3".repeat(64)
    private val hConfig = "4".repeat(64)

    private val createdAt = 1_755_400_000_000L

    // ---- 黄金串 1：全量范围、四类内容各一 ----

    private val goldenFull =
        "{\"app_version\":\"1.4.2\",\"created_at\":1755400000000,\"files\":[" +
            "{\"rel_path\":\"audio/2026/note.m4a\",\"sha256\":\"" + hAudio + "\",\"size_bytes\":999}," +
            "{\"rel_path\":\"configs/compliance/nz-rules.json\",\"sha256\":\"" + hConfig + "\",\"size_bytes\":77}," +
            "{\"rel_path\":\"db.sqlite\",\"sha256\":\"" + hDb + "\",\"size_bytes\":4096}," +
            "{\"rel_path\":\"photos/2026/kitchen.jpg\",\"sha256\":\"" + hPhoto + "\",\"size_bytes\":12345}" +
            "],\"format_version\":1,\"scope\":{\"kind\":\"full\"}}"

    private fun fullManifest() = BackupManifest.create(
        createdAtMs = createdAt,
        appVersion = "1.4.2",
        scope = BackupScope.Full,
        // 刻意乱序传入：全序由 manifest 自己保证，不能靠调用方传得巧。
        files = listOf(
            BackupFileEntry("photos/2026/kitchen.jpg", 12345, hPhoto),
            BackupFileEntry("db.sqlite", 4096, hDb),
            BackupFileEntry("configs/compliance/nz-rules.json", 77, hConfig),
            BackupFileEntry("audio/2026/note.m4a", 999, hAudio),
        ),
    )

    @Test
    fun `golden manifest pins key names key order field shapes and file ordering`() {
        assertEquals(goldenFull, fullManifest().canonicalJson)
        assertEquals(
            listOf("audio/2026/note.m4a", "configs/compliance/nz-rules.json", "db.sqlite", "photos/2026/kitchen.jpg"),
            fullManifest().files.map { it.relPath },
        )
    }

    @Test
    fun `golden manifest pins the property scope shape`() {
        val scoped = BackupManifest.create(
            createdAtMs = createdAt,
            appVersion = "1.4.2",
            scope = BackupScope.Property("prop-0001"),
            files = listOf(BackupFileEntry("db.sqlite", 4096, hDb)),
        )
        assertEquals(
            "{\"app_version\":\"1.4.2\",\"created_at\":1755400000000,\"files\":[" +
                "{\"rel_path\":\"db.sqlite\",\"sha256\":\"" + hDb + "\",\"size_bytes\":4096}" +
                "],\"format_version\":1,\"scope\":{\"kind\":\"property\",\"property_id\":\"prop-0001\"}}",
            scoped.canonicalJson,
        )
    }

    @Test
    fun `parse round trips the golden bytes`() {
        val parsed = BackupManifest.parse(goldenFull.toByteArray(Charsets.UTF_8))
        assertEquals(goldenFull, parsed.canonicalJson)
        assertEquals(createdAt, parsed.createdAtMs)
        assertEquals("1.4.2", parsed.appVersion)
        assertEquals(1, parsed.formatVersion)
        assertEquals(BackupScope.Full, parsed.scope)
        assertEquals(4, parsed.files.size)
        assertEquals(4096, parsed.file("db.sqlite")?.sizeBytes)
        assertNull(parsed.file("photos/missing.jpg"))
        val scoped = BackupManifest.parse(
            BackupManifest.create(
                createdAt,
                "1.4.2",
                BackupScope.Property("prop-0001"),
                listOf(BackupFileEntry("db.sqlite", 4096, hDb)),
            ).toBytes(),
        )
        assertEquals(BackupScope.Property("prop-0001"), scoped.scope)
    }

    // ---- parse 的严格性：只收 canonical 形态、只认已知键、只认对的类型 ----

    private val app = "\"app_version\":\"1.0.0\""
    private val created = "\"created_at\":1755400000000"
    private val filesArray = "\"files\":[{\"rel_path\":\"db.sqlite\",\"sha256\":\"" + hDb + "\",\"size_bytes\":4096}]"
    private val formatVersion = "\"format_version\":1"
    private val scopeFull = "\"scope\":{\"kind\":\"full\"}"
    private val minimal = "{$app,$created,$filesArray,$formatVersion,$scopeFull}"

    @Test
    fun `parse accepts the minimal canonical manifest`() {
        assertEquals(minimal, BackupManifest.parse(minimal.toByteArray(Charsets.UTF_8)).canonicalJson)
    }

    @Test
    fun `parse rejects a manifest that is not in canonical form`() {
        // 键序/空白/整数拼写必须逐字节等于 core/canon 的输出——否则「同一份 manifest」有多种字节表示，
        // 写入端与读取端对不上账，防篡改自证也就无从谈起。
        val reordered = "{$created,$app,$filesArray,$formatVersion,$scopeFull}"
        val spaced = "{$app, $created, $filesArray, $formatVersion, $scopeFull}"
        val plusSigned = "{$app,\"created_at\":+1755400000000,$filesArray,$formatVersion,$scopeFull}"
        for (bad in listOf(reordered, spaced, plusSigned)) {
            assertFailsWith<BackupFormatException>(bad) { BackupManifest.parse(bad.toByteArray(Charsets.UTF_8)) }
        }
    }

    @Test
    fun `parse rejects unknown keys anywhere in the manifest`() {
        // 契约是「未知字段一律拒收、绝不忽略」——执行者就是 canonical 形态那一道闸（canonical 输出只含
        // 本版认识的键，多一个就必然对不上字节）。每个变体的未知键都**放在已排序的位置**，以证明
        // 它被拒不是因为顺手违反了键序。
        val topLevel = "{$app,$created,\"extra\":1,$filesArray,$formatVersion,$scopeFull}"
        val inFile = "{$app,$created," +
            "\"files\":[{\"extra\":1,\"rel_path\":\"db.sqlite\",\"sha256\":\"" + hDb + "\",\"size_bytes\":4096}]," +
            "$formatVersion,$scopeFull}"
        val inScope = "{$app,$created,$filesArray,$formatVersion,\"scope\":{\"kind\":\"full\",\"property_id\":\"p\"}}"
        for (bad in listOf(topLevel, inFile, inScope)) {
            assertFailsWith<BackupFormatException>(bad) { BackupManifest.parse(bad.toByteArray(Charsets.UTF_8)) }
        }
    }

    @Test
    fun `parse rejects missing keys wrong types and an unknown scope kind`() {
        val cases = listOf(
            "{$created,$filesArray,$formatVersion,$scopeFull}", // 缺 app_version
            "{$app,$created,$formatVersion,$scopeFull}", // 缺 files
            "{$app,\"created_at\":\"1755400000000\",$filesArray,$formatVersion,$scopeFull}", // 时间成了字符串
            "{$app,$created,\"files\":{},$formatVersion,$scopeFull}", // files 不是数组
            "{$app,$created,\"files\":[{\"rel_path\":\"db.sqlite\",\"sha256\":\"" + hDb + "\",\"size_bytes\":\"4096\"}]," +
                "$formatVersion,$scopeFull}", // size_bytes 成了字符串
            "{$app,$created,$filesArray,\"format_version\":2,$scopeFull}", // 版本不是本版
            "{$app,$created,$filesArray,$formatVersion,\"scope\":{\"kind\":\"partial\"}}", // 未知 scope
            "{$app,$created,$filesArray,$formatVersion,\"scope\":{\"kind\":\"property\"}}", // 缺 property_id
            "[]", // 根不是对象
            "", // 空
        )
        for (bad in cases) {
            assertFailsWith<BackupFormatException>(bad) { BackupManifest.parse(bad.toByteArray(Charsets.UTF_8)) }
        }
    }

    @Test
    fun `parse rejects bytes that are not valid utf8`() {
        // toString(UTF_8) 会把坏字节静默换成替换字符——那样「库里的 manifest」与「包里的字节」对不上而无人知。
        val bytes = minimal.toByteArray(Charsets.UTF_8)
        bytes[minimal.indexOf("1.0.0")] = 0xC3.toByte() // 悬空的 UTF-8 前导字节
        assertFailsWith<BackupFormatException> { BackupManifest.parse(bytes) }
    }

    // ---- create 的不变量 ----

    @Test
    fun `create rejects duplicate rel paths`() {
        assertFailsWith<BackupFormatException> {
            BackupManifest.create(
                createdAt,
                "1.0.0",
                BackupScope.Full,
                listOf(BackupFileEntry("db.sqlite", 4096, hDb), BackupFileEntry("db.sqlite", 4096, hPhoto)),
            )
        }
    }

    @Test
    fun `create requires the database snapshot`() {
        // v1 恢复语义 = 整包替换（ADR-0002）：没有 db.sqlite 的包一旦被拿去恢复就是清库。
        assertFailsWith<BackupFormatException> {
            BackupManifest.create(createdAt, "1.0.0", BackupScope.Full, listOf(BackupFileEntry("photos/a.jpg", 1, hPhoto)))
        }
    }

    @Test
    fun `create rejects a non positive timestamp and a malformed app version`() {
        val ok = listOf(BackupFileEntry("db.sqlite", 4096, hDb))
        assertFailsWith<BackupFormatException> { BackupManifest.create(0, "1.0.0", BackupScope.Full, ok) }
        assertFailsWith<BackupFormatException> { BackupManifest.create(-1, "1.0.0", BackupScope.Full, ok) }
        assertFailsWith<BackupFormatException> { BackupManifest.create(createdAt, "", BackupScope.Full, ok) }
        assertFailsWith<BackupFormatException> {
            BackupManifest.create(createdAt, "1.0" + 0x0A.toChar(), BackupScope.Full, ok)
        }
        assertFailsWith<BackupFormatException> {
            // 非 NFC 的字符串会被 canon 归一，于是对象字段与包内字节分家。
            BackupManifest.create(createdAt, "1.0-cafe" + 0x0301.toChar(), BackupScope.Full, ok)
        }
    }

    @Test
    fun `file entry rejects a malformed hash or a negative size`() {
        assertFailsWith<BackupFormatException> { BackupFileEntry("db.sqlite", 1, "AB".repeat(32)) } // 大写
        assertFailsWith<BackupFormatException> { BackupFileEntry("db.sqlite", 1, "1".repeat(63)) }
        assertFailsWith<BackupFormatException> { BackupFileEntry("db.sqlite", 1, "z".repeat(64)) }
        assertFailsWith<BackupFormatException> { BackupFileEntry("db.sqlite", -1, hDb) }
    }

    // ---- rel_path：zip-slip 的唯一闸门（读取器只吐 manifest 校验过的路径） ----

    @Test
    fun `rel path rejects every escaping or hostile shape`() {
        val hostile = listOf(
            "",
            "..",
            "../secret.jpg",
            "photos/../../etc/passwd",
            "/photos/a.jpg",
            "photos//a.jpg",
            "photos/./a.jpg",
            "photos/",
            "photos" + 0x5C.toChar() + "a.jpg", // 反斜杠
            "C:/photos/a.jpg",
            "manifest.json", // 保留名，不是文件条目
            "evil/a.jpg", // 顶层区域不在白名单
            "db.sqlite/x",
            "photos/a" + 0x0A.toChar() + ".jpg", // 控制字符
            "photos/cafe" + 0x0301.toChar() + ".jpg", // 非 NFC
            "photos/" + "a".repeat(BackupFormat.MAX_REL_PATH_CHARS),
        )
        for (path in hostile) {
            assertFailsWith<BackupFormatException>("必须拒绝：$path") { BackupFileEntry(path, 1, hDb) }
        }
    }

    @Test
    fun `rel path accepts the four declared areas`() {
        val ok = listOf(
            "db.sqlite",
            "photos/2026/kitchen.jpg",
            "audio/2026/note.m4a",
            "configs/compliance/nz-rules.json",
            "photos/caf" + 0x00E9.toChar() + ".jpg", // NFC 形态的非 ASCII 文件名合法
        )
        for (path in ok) {
            assertEquals(path, BackupFileEntry(path, 1, hDb).relPath)
        }
    }

    // ---- scope：读取器据此决定恢复语义 ----

    @Test
    fun `scope decides which owners belong in the archive`() {
        assertTrue(BackupScope.Full.includes(null))
        assertTrue(BackupScope.Full.includes("prop-A"))
        val scoped = BackupScope.Property("prop-A")
        assertTrue(scoped.includes(null), "库级资产（db.sqlite/configs）在按物业包里照收")
        assertTrue(scoped.includes("prop-A"))
        assertFalse(scoped.includes("prop-B"))
        assertFailsWith<BackupFormatException> { BackupScope.Property("") }
        assertFailsWith<BackupFormatException> { BackupScope.Property("p" + 0x00.toChar()) }
    }

    // ---- 小工具：这两个是所有哈希/上限判定的地基 ----

    @Test
    fun `toHexLower matches a published sha256 vector and pads leading zeros`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            toHexLower(MessageDigest.getInstance("SHA-256").digest("abc".toByteArray(Charsets.US_ASCII))),
        )
        assertEquals("00010f10ff", toHexLower(byteArrayOf(0, 1, 15, 16, -1)))
    }

    @Test
    fun `readAtMost refuses to buffer more than the limit`() {
        val body = ByteArray(64) { it.toByte() }
        assertEquals(64, readAtMost(ByteArrayInputStream(body), 64).size)
        assertFailsWith<BackupFormatException> { readAtMost(ByteArrayInputStream(body), 63) }
        assertEquals(64 * 1024 * 1024, BackupFormat.MAX_MANIFEST_BYTES)
    }

    @Test
    fun `manifest exposes its files as an immutable snapshot`() {
        // 只读类型不等于不可变：ArrayList 强转回 MutableList 照样能改（T1-TEMPLATE-ENGINE 的真缺陷之一）。
        val manifest = fullManifest()
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (manifest.files as MutableList<BackupFileEntry>).clear()
        }
    }
}

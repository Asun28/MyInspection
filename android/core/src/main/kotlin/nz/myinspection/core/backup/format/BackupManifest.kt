package nz.myinspection.core.backup.format

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Collections
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import nz.myinspection.core.canon.CanonicalJson

/** manifest 里的一条文件记录：包内相对路径 + 字节数 + 内容 SHA-256（小写十六进制）。 */
data class BackupFileEntry(val relPath: String, val sizeBytes: Long, val sha256: String) {
    init {
        checkRelPath(relPath)
        if (sizeBytes < 0) throw BackupFormatException("size_bytes 不能为负：$sizeBytes（$relPath）")
        if (!SHA256_HEX.matches(sha256)) {
            throw BackupFormatException("sha256 必须是 64 位小写十六进制：$sha256（$relPath）")
        }
    }
}

/**
 * 数据集范围。**读取器据此决定恢复语义**——按物业导出的包绝不能被当成全量恢复（ADR-0002 是整包替换）。
 * v1 简化：按物业包里的 `db.sqlite` 仍是整库快照，范围只由本字段标记（见任务卡上下文包）。
 *
 * **manifest 刻意不记录每个文件的属主**：属主的权威来源是包内那份 DB（照片→巡检→物业），再记一份就是
 * 第二真相源；而 v1 的按物业包本来就带整库快照，「别的物业的文件混进来」在数据层面也无从谈起。
 * 将来若真要做「按物业恢复」，再连同 format_version 一起评审是否加 `owner_property_id` 字段。
 * 同理，**新增 scope 种类（如按 tenancy）必须提 format_version**：老读取器遇到不认识的 kind 一律拒收。
 */
sealed interface BackupScope {
    /** 该资产是否属于本范围。`null` = 库级资产（db.sqlite、configs），任何范围都收。 */
    fun includes(ownerPropertyId: String?): Boolean

    object Full : BackupScope {
        override fun includes(ownerPropertyId: String?): Boolean = true

        override fun toString(): String = "Full"
    }

    data class Property(val propertyId: String) : BackupScope {
        init {
            checkText(propertyId, "scope.property_id", MAX_PROPERTY_ID_CHARS)
        }

        override fun includes(ownerPropertyId: String?): Boolean =
            ownerPropertyId == null || ownerPropertyId == propertyId
    }
}

/**
 * ★ 包内首条目 `manifest.json`。序列化复用**已冻结**的 core/canon（RFC 8785 风格 canonical JSON），
 * 于是「同一份 manifest」只有唯一一种字节表示——[parse] 因此可以要求逐字节等于 canonical 形态，
 * 任何非规范的（哪怕语义相同的）manifest 一律拒收。
 *
 * 只有两个出生点：[create]（本机写包）与 [parse]（读别人的包），两条路都跑同一套不变量校验，
 * 所以「构造得出来的 manifest」必然是合法的（构造器私有 = 伪造不出来）。
 */
class BackupManifest private constructor(
    val formatVersion: Int,
    val createdAtMs: Long,
    val appVersion: String,
    val scope: BackupScope,
    files: List<BackupFileEntry>,
) {
    /** 按 rel_path 全序排好的文件清单（真不可变：只读类型强转不回去）。 */
    val files: List<BackupFileEntry> = Collections.unmodifiableList(ArrayList(files))

    private val byPath: Map<String, BackupFileEntry> = files.associateBy { it.relPath }

    val canonicalJson: String = try {
        CanonicalJson.serialize(toJson())
    } catch (e: IllegalArgumentException) {
        // canon 对良构 Unicode/整数拼写有硬要求；到这里说明输入本身不合法，不是「序列化失败」。
        throw BackupFormatException("manifest 无法 canonical 序列化：${e.message}", e)
    }

    fun toBytes(): ByteArray = canonicalJson.toByteArray(Charsets.UTF_8)

    fun file(relPath: String): BackupFileEntry? = byPath[relPath]

    private fun toJson(): JsonObject = buildJsonObject {
        put("app_version", appVersion)
        put("created_at", createdAtMs)
        put(
            "files",
            buildJsonArray {
                for (file in files) {
                    add(
                        buildJsonObject {
                            put("rel_path", file.relPath)
                            put("sha256", file.sha256)
                            put("size_bytes", file.sizeBytes)
                        },
                    )
                }
            },
        )
        put("format_version", formatVersion)
        put(
            "scope",
            when (scope) {
                is BackupScope.Full -> buildJsonObject { put("kind", "full") }
                is BackupScope.Property -> buildJsonObject {
                    put("kind", "property")
                    put("property_id", scope.propertyId)
                }
            },
        )
    }

    companion object {
        private const val MAX_APP_VERSION_CHARS = 64

        fun create(
            createdAtMs: Long,
            appVersion: String,
            scope: BackupScope,
            files: List<BackupFileEntry>,
        ): BackupManifest {
            if (createdAtMs <= 0) throw BackupFormatException("created_at 必须是正的 epoch 毫秒，实得 $createdAtMs")
            checkText(appVersion, "app_version", MAX_APP_VERSION_CHARS)
            // 全序 = String 自然序（逐 UTF-16 码元），与 core/canon 的键序同一把尺子。
            val sorted = files.sortedBy { it.relPath }
            for (i in 1 until sorted.size) {
                if (sorted[i].relPath == sorted[i - 1].relPath) {
                    throw BackupFormatException("manifest 有重复 rel_path：${sorted[i].relPath}")
                }
            }
            if (sorted.none { it.relPath == BackupFormat.DB_ENTRY }) {
                throw BackupFormatException(
                    "备份包必须含 ${BackupFormat.DB_ENTRY}：v1 恢复语义是整包替换（ADR-0002），缺了数据库的包一旦被恢复就是清库",
                )
            }
            return BackupManifest(BackupFormat.FORMAT_VERSION, createdAtMs, appVersion, scope, sorted)
        }

        fun parse(bytes: ByteArray): BackupManifest {
            val text = decodeUtf8Strict(bytes)
            val root = asObject(parseJson(text), "manifest")
            val declaredVersion = longField(root, "format_version")
            if (declaredVersion != BackupFormat.FORMAT_VERSION.toLong()) {
                throw BackupFormatException("manifest format_version=$declaredVersion，本版只能读 ${BackupFormat.FORMAT_VERSION}")
            }
            val files = asArray(root["files"], "files").map { element ->
                val fileObject = asObject(element, "files[]")
                BackupFileEntry(
                    relPath = stringField(fileObject, "rel_path"),
                    sizeBytes = longField(fileObject, "size_bytes"),
                    sha256 = stringField(fileObject, "sha256"),
                )
            }
            val manifest = create(
                createdAtMs = longField(root, "created_at"),
                appVersion = stringField(root, "app_version"),
                scope = parseScope(asObject(root["scope"], "scope")),
                files = files,
            )
            // 这一句是**唯一**的形态闸，它同时管住了未知字段、键序、空白与整数拼写：canonical 输出只含
            // 本版认识的那几个键，所以多一个字段就必然对不上字节。刻意不再单列一道「未知键」预检——
            // 变异实验证实那道预检删掉后没有任何测试变红（它拦下的输入全都会在这里被拦），留着只会
            // 让「manifest 形态由谁判」出现第二个说法。
            if (manifest.canonicalJson != text) {
                throw BackupFormatException(
                    "manifest 不是 canonical 形态：字段集/键序/空白/整数拼写必须逐字节等于 core/canon 的输出，" +
                        "否则同一份 manifest 会有多种字节表示，写入端与读取端对不上账",
                )
            }
            return manifest
        }

        private fun parseScope(scope: JsonObject): BackupScope = when (val kind = stringField(scope, "kind")) {
            "full" -> BackupScope.Full
            "property" -> BackupScope.Property(stringField(scope, "property_id"))
            else -> throw BackupFormatException("未知的 scope.kind=$kind（本版只认 full / property）")
        }

        private fun parseJson(text: String): JsonElement = try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw BackupFormatException("manifest 不是合法 JSON", e)
        }

        /** 坏字节不做替换字符兜底：静默替换会让「库里的 manifest」与「包里的字节」对不上而无人知。 */
        private fun decodeUtf8Strict(bytes: ByteArray): String = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            throw BackupFormatException("manifest 不是合法 UTF-8", e)
        }

        private fun asObject(element: JsonElement?, where: String): JsonObject =
            element as? JsonObject ?: throw BackupFormatException("$where 必须是 JSON 对象")

        private fun asArray(element: JsonElement?, where: String): JsonArray =
            element as? JsonArray ?: throw BackupFormatException("$where 必须是 JSON 数组")

        private fun stringField(obj: JsonObject, key: String): String {
            val primitive = obj[key] as? JsonPrimitive ?: throw BackupFormatException("manifest 缺少字段 $key")
            if (!primitive.isString) throw BackupFormatException("manifest 字段 $key 必须是字符串")
            return primitive.content
        }

        private fun longField(obj: JsonObject, key: String): Long {
            val primitive = obj[key] as? JsonPrimitive ?: throw BackupFormatException("manifest 缺少字段 $key")
            if (primitive.isString) throw BackupFormatException("manifest 字段 $key 必须是整数、不是字符串")
            return primitive.content.toLongOrNull()
                ?: throw BackupFormatException("manifest 字段 $key 必须是整数：${primitive.content}")
        }
    }
}

private const val MAX_PROPERTY_ID_CHARS = 128

private val SHA256_HEX = Regex("[0-9a-f]{64}")

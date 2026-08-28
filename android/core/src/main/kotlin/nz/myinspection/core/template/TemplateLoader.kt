package nz.myinspection.core.template

import kotlinx.serialization.json.Json
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Collections

/**
 * 模板校验失败。[errors] 是**本次发现的全部问题**（不是第一个）：内容卡有 80–120 条项目，
 * 作者一次跑出全部问题才不用来回 30 趟。每条都点名条目（`<stableId>: <what>`）或点名模板层
 * （`template: <what>`），且**全 ASCII**——它是机检断言面，本地化文案只给人读（L165）。
 */
class TemplateValidationException(val errors: List<String>) :
    IllegalArgumentException("template validation failed: ${errors.joinToString("; ")}")

/**
 * 解析结果：模板文档 + 该模板**自己那份字节**的 SHA-256（入 `template_version.content_hash`）。
 *
 * 构造器 `private`，唯一出生点是 [parse]——它**只收字节**，模板与哈希都由同一份字节算出，
 * 调用方递不进来一个 contentHash。这条不变量（content_hash 必是源字节的 SHA-256）因此由**类型**
 * 保证，而不是靠注释或调用纪律：它是「同版本号不同内容」静默漂移的唯一检出手段，一旦哈希可以
 * 被随手填，那道检出就只是看起来存在。
 *
 * 刻意不是 `data class`：`copy()` 会绕过构造器可见性，让持有者从一份合法结果 copy 出一个假 hash。
 */
class LoadedTemplate private constructor(
    val template: Template,
    val contentHash: String,
) {
    internal companion object {
        /**
         * 唯一出生点：字节 → 严格 UTF-8 解码 → 解析 → 冻结集合 → 校验 → 连同这份字节的哈希封装。
         *
         * @throws java.nio.charset.CharacterCodingException 字节不是合法 UTF-8
         * @throws kotlinx.serialization.SerializationException JSON 语法错误或出现未知字段
         * @throws TemplateValidationException 校验不通过（携带全部问题）
         */
        fun parse(bytes: ByteArray): LoadedTemplate {
            val template = freeze(Json.decodeFromString(Template.serializer(), decodeUtf8Strict(bytes)))
            val errors = TemplateLoader.validate(template)
            if (errors.isNotEmpty()) throw TemplateValidationException(errors)
            return LoadedTemplate(template = template, contentHash = sha256Hex(bytes))
        }
    }
}

/**
 * 模板加载器：读 InputStream → 解析 → 校验 → [LoadedTemplate]。纯 JVM，不碰 android assets
 * （:app 侧后续自己开 assets 流喂进来）。
 */
object TemplateLoader {

    /**
     * 从 [input] 读入整份模板。**先把字节读全**：`content_hash` 是文件字节的 SHA-256，
     * 与解析结果无关（同一份模板改个缩进 = 不同 content_hash），这正是「同版本号不同内容」
     * 静默漂移的检出手段。
     *
     * 不关闭 [input]——流的归属留给调用方（:app 侧从 assets 开的流有自己的生命周期）。
     * 未知字段**不忽略**（kotlinx 默认严格）：`textZH` 这种拼错的键必须当场报错，而不是
     * 静默变成空文案，再由校验器报一句让人摸不着头脑的 "textZh is blank"。
     *
     * @throws java.nio.charset.CharacterCodingException 字节不是合法 UTF-8
     * @throws kotlinx.serialization.SerializationException JSON 语法错误或出现未知字段
     * @throws TemplateValidationException 校验不通过（携带全部问题）
     */
    fun load(input: InputStream): LoadedTemplate = LoadedTemplate.parse(input.readBytes())

    /**
     * 校验一份已解析的模板，返回全部问题；空列表 = 通过。内容卡的 DoD 直接拿它当闸。
     *
     * 发出顺序是契约的一部分（作者从上往下改）：先模板层（含 rooms 数组序），再按 items 数组序逐条，
     * 同一条内按 身份(blank/duplicate) → 必填字段/房间引用 → 评级域 → 拍照规则。
     */
    fun validate(template: Template): List<String> {
        val errors = mutableListOf<String>()

        // 类型越界时**只有评级域**判不了（[TemplateDomains.allowedStatusesFor] 返回 null），故只跳过
        // 「这个评级在不在域内」这一项检查：否则一个拼错的 type 会让 120 条项目各喷一串
        // "status … is not allowed"，把真正的病因淹掉。与类型无关的检查（含 allowedStatuses 空集）照跑。
        val allowedStatuses = TemplateDomains.allowedStatusesFor(template.type)
        if (allowedStatuses == null) errors += "template: unknown type ${template.type}"
        if (template.version < 1) errors += "template: version must be >= 1"
        if (template.items.isEmpty()) errors += "template: items is empty"

        val declaredRoomKeys = mutableSetOf<String>()
        template.rooms.forEachIndexed { index, room ->
            if (room.key.isBlank()) {
                errors += "template: rooms[$index].key is blank"
            } else if (!declaredRoomKeys.add(room.key)) {
                errors += "template: duplicate room key ${room.key}"
            }
        }

        val seenStableIds = mutableSetOf<String>()
        template.items.forEachIndexed { index, item ->
            // stable_id 空 = 这条项目没有身份：历史对齐只认它，空值会让该项在每次版本升级里都对不上。
            // 点不了名就按**位置**标注，其余检查照跑到底——「一次报全」是本校验器对内容卡的承诺，
            // 不能因为某条缺了身份，就把它剩下的毛病藏到下一轮才让作者看见。
            val label = if (item.stableId.isBlank()) "item[$index]" else item.stableId
            if (item.stableId.isBlank()) {
                errors += "$label: stableId is blank"
            } else if (!seenStableIds.add(item.stableId)) {
                errors += "$label: duplicate stableId"
            }
            if (item.area.isBlank()) errors += "$label: area is blank"
            if (item.room.isBlank()) errors += "$label: room is blank"
            if (template.rooms.isNotEmpty() && item.room.isNotBlank() && item.room !in declaredRoomKeys) {
                errors += "$label: room ${item.room} is not declared in rooms"
            }
            if (item.textEn.isBlank()) errors += "$label: textEn is blank"
            if (item.textZh.isBlank()) errors += "$label: textZh is blank"
            if (item.allowedStatuses.isEmpty()) {
                // 空集与模板类型无关（哪一类都不允许"一个合法评级都没有"），故不在下面的域判分支里。
                errors += "$label: allowedStatuses is empty"
            } else if (allowedStatuses != null) {
                item.allowedStatuses.filterNot { it in allowedStatuses }.forEach { status ->
                    errors += "$label: status $status is not allowed for template type ${template.type}"
                }
            }
            val photoRule = item.photoRule
            if (photoRule != null && photoRule !in TemplateDomains.PHOTO_RULES) {
                errors += "$label: unknown photoRule $photoRule"
            }
        }
        return errors
    }
}

// 下面三个是**文件级 private**（不是 TemplateLoader 的成员）：[LoadedTemplate.parse] 与 [TemplateLoader]
// 都要用，而 Kotlin 的 private 成员只对所属类可见、private 顶层声明才是"本文件可见"。

/**
 * 严格 UTF-8 解码：坏字节**抛异常**，不替换成 U+FFFD。
 *
 * `ByteArray.toString(UTF_8)` 走的是替换策略——一份被截断/损坏的模板会"加载成功"，
 * 文案里带着替换字符进库，而 content_hash 记的是真实字节：库里的内容与文件对不上，且没人会知道。
 */
private fun decodeUtf8Strict(bytes: ByteArray): String =
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()

/**
 * 把解析出来的集合包成不可变。kotlinx 反序列化产出的是 `ArrayList`，Kotlin 的 `List` 只是只读**视图**，
 * 强转回 `MutableList` 就能改——而 [LoadedTemplate] 的立身之本是"内容与 contentHash 对得上"，
 * 一份哈希完还能被改的模板等于没有哈希。
 */
private fun freeze(template: Template): Template = template.copy(
    rooms = Collections.unmodifiableList(template.rooms.map { it.copy() }),
    items = Collections.unmodifiableList(
        template.items.map { it.copy(allowedStatuses = Collections.unmodifiableList(it.allowedStatuses)) },
    ),
)

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

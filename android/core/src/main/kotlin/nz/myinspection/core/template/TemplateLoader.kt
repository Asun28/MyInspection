package nz.myinspection.core.template

import kotlinx.serialization.json.Json
import java.io.InputStream
import java.security.MessageDigest

/**
 * 模板校验失败。[errors] 是**本次发现的全部问题**（不是第一个）：内容卡有 80–120 条项目，
 * 作者一次跑出全部问题才不用来回 30 趟。每条都点名条目（`<stableId>: <what>`）或点名模板层
 * （`template: <what>`），且**全 ASCII**——它是机检断言面，本地化文案只给人读（L165）。
 */
class TemplateValidationException(val errors: List<String>) :
    IllegalArgumentException("template validation failed: ${errors.joinToString("; ")}")

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
     * @throws TemplateValidationException 校验不通过（携带全部问题）
     */
    fun load(input: InputStream): LoadedTemplate {
        val bytes = input.readBytes()
        val template = Json.decodeFromString(Template.serializer(), bytes.toString(Charsets.UTF_8))
        val errors = validate(template)
        if (errors.isNotEmpty()) throw TemplateValidationException(errors)
        return LoadedTemplate(template = template, contentHash = sha256Hex(bytes))
    }

    /**
     * 校验一份已解析的模板，返回全部问题；空列表 = 通过。内容卡的 DoD 直接拿它当闸。
     *
     * 发出顺序是契约的一部分（作者从上往下改）：先模板层，再按 items 数组序逐条，
     * 同一条内按 身份(duplicate) → 文案(blank) → 评级域 → 拍照规则。
     */
    fun validate(template: Template): List<String> {
        val errors = mutableListOf<String>()

        // 类型越界时评级域**判不了**（[TemplateDomains.allowedStatusesFor] 返回 null）。此时跳过每条项目的
        // 评级检查：否则一个拼错的 type 会让 120 条项目各喷一串 "status … is not allowed"，把真正的病因淹掉。
        val allowedStatuses = TemplateDomains.allowedStatusesFor(template.type)
        if (allowedStatuses == null) errors += "template: unknown type ${template.type}"
        if (template.version < 1) errors += "template: version must be >= 1"
        if (template.items.isEmpty()) errors += "template: items is empty"

        val seenStableIds = mutableSetOf<String>()
        template.items.forEachIndexed { index, item ->
            // stable_id 空 = 这条项目没有身份：历史对齐只认它，空值会让该项在每次版本升级里都对不上。
            // 既然点不了名，就按位置报，并跳过这条的其余检查（用 " : textEn is blank" 之类点名没有意义）。
            if (item.stableId.isBlank()) {
                errors += "item[$index]: stableId is blank"
                return@forEachIndexed
            }
            val id = item.stableId
            if (!seenStableIds.add(id)) errors += "$id: duplicate stableId"
            if (item.area.isBlank()) errors += "$id: area is blank"
            if (item.room.isBlank()) errors += "$id: room is blank"
            if (item.textEn.isBlank()) errors += "$id: textEn is blank"
            if (item.textZh.isBlank()) errors += "$id: textZh is blank"
            if (allowedStatuses != null) {
                if (item.allowedStatuses.isEmpty()) errors += "$id: allowedStatuses is empty"
                item.allowedStatuses.filterNot { it in allowedStatuses }.forEach { status ->
                    errors += "$id: status $status is not allowed for template type ${template.type}"
                }
            }
            val photoRule = item.photoRule
            if (photoRule != null && photoRule !in TemplateDomains.PHOTO_RULES) {
                errors += "$id: unknown photoRule $photoRule"
            }
        }
        return errors
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

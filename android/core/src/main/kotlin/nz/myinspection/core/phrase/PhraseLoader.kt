package nz.myinspection.core.phrase

import kotlinx.serialization.json.Json
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Collections

/**
 * 短语库校验失败。[errors] 是**本次发现的全部问题**（同 `TemplateValidationException` 的理由：
 * 短语库有 60+ 条，作者需要一次跑出全部问题）。每条都点名短语（`phrase[<index>]: <what>`），
 * 且**全 ASCII**——它是机检断言面，本地化文案只给人读（L165）。
 */
class PhraseValidationException(val errors: List<String>) :
    IllegalArgumentException("phrase library validation failed: ${errors.joinToString("; ")}")

/**
 * 已加载 + 已校验的短语库，并带查询接口（卡片「查询接口」产出）。
 *
 * 构造器 `private`，唯一出生点是 [parse]——同 `LoadedTemplate` 的理由（T1-TEMPLATE-ENGINE 修订）：
 * 若构造器可从包内任意代码调用，"合法短语库"与"经过校验的短语库"这两件事就要靠调用纪律维持，
 * 而不是由类型保证。`parse` 只收字节，解码/冻结/校验一次做完，任何持有 [LoadedPhraseLibrary]
 * 实例的调用方因此不可能绕过校验——不是运行期拒绝，而是构造不出违规实例。
 *
 * 刻意不是 `data class`：`copy()` 会绕过构造器可见性。
 */
class LoadedPhraseLibrary private constructor(val library: PhraseLibrary) {

    /**
     * 按分类取短语，按 [Phrase.sort] 升序。分类越界即 `IllegalArgumentException`（快速失败）：
     * 调用方的分类字面量若拼错，静默返回空列表会是一个不容易被发现的空白弹层，而不是当场报错。
     *
     * 排序用 [List.sortedBy]（stable sort）：同 `sort` 值的短语之间保留 [PhraseLibrary.phrases]
     * 里原来的相对顺序，不依赖任何未声明 `ORDER BY` 的隐式序（L222 同理，只是这里判据是
     * Kotlin 的 List 而非 SQLite 的 SELECT）。
     */
    fun phrasesFor(category: String): List<Phrase> {
        require(category in PhraseDomains.CATEGORIES) { "unknown phrase category: $category" }
        return library.phrases.filter { it.category == category }.sortedBy { it.sort }
    }

    /**
     * 按当前评级推荐短语，跨全部分类。**v1 契约：过滤维度只有 [status]**（卡片原文「按状态过滤
     * 推荐」即完整定义，不叠加任何分类子集）：[Phrase.appliesToStatuses] 为 null（不限评级）或
     * 包含 [status] 的短语入选，不论 [Phrase.category]。内容层面按状态精度分工——正面结论类短语
     * （如"未见异常"）标 `GOOD`、负面结论类标其对应评级、真正跨评级通用的（如"无其他说明，请参见
     * 随附照片"）留 null，靠 appliesToStatuses 本身而非分类把关。
     *
     * [stableId] 是为消费端 **item-context 预留的接口缝**：强制调用方绑定到具体某一项、不允许在
     * 不知道正给哪一项做推荐的情况下调用，但 v1 不参与过滤。item→分类映射需要模板内容数据
     * （不在短语库这份纯 JSON、模板版本无关的独立文件范围内——短语与模板分开演进）且属选择器
     * 逻辑（消费端 T2-CAPTURE-UI 的范围），故 v1 只做非空校验，把真正的 item-context 关联留给
     * 持有"当前模板 + 当前巡检"两者的调用方。[status] 必须落在 [PhraseDomains.STATUSES] 内
     * （含空白）：拼错的评级值当场报错，而不是静默只返回通用短语、让调用方以为过滤生效了。
     *
     * 返回按 (分类, sort) 排序，同 sort 值内保留原数组序（同上 stable-sort 理由）。
     */
    fun suggestFor(stableId: String, status: String): List<Phrase> {
        require(stableId.isNotBlank()) { "stableId must not be blank" }
        require(status in PhraseDomains.STATUSES) { "unknown status: $status" }
        return library.phrases
            .filter { phrase -> phrase.appliesToStatuses?.let { status in it } ?: true }
            .sortedWith(compareBy({ it.category }, { it.sort }))
    }

    internal companion object {
        /**
         * 唯一出生点：字节 → 严格 UTF-8 解码 → 解析 → 冻结集合 → 校验 → 封装。
         *
         * @throws java.nio.charset.CharacterCodingException 字节不是合法 UTF-8
         * @throws kotlinx.serialization.SerializationException JSON 语法错误或出现未知字段
         * @throws PhraseValidationException 校验不通过（携带全部问题）
         */
        fun parse(bytes: ByteArray): LoadedPhraseLibrary {
            val library = freeze(Json.decodeFromString(PhraseLibrary.serializer(), decodeUtf8Strict(bytes)))
            val errors = PhraseLoader.validate(library)
            if (errors.isNotEmpty()) throw PhraseValidationException(errors)
            return LoadedPhraseLibrary(library)
        }
    }
}

/**
 * 短语库加载器：读 InputStream → 解析 → 校验 → [LoadedPhraseLibrary]。纯 JVM，不碰 android assets。
 */
object PhraseLoader {

    /** 不关闭 [input]——流的归属留给调用方，同 `TemplateLoader.load` 的理由。 */
    fun load(input: InputStream): LoadedPhraseLibrary = LoadedPhraseLibrary.parse(input.readBytes())

    /**
     * 校验一份已解析的短语库，返回全部问题；空列表 = 通过。内容卡的 DoD 直接拿它当闸。
     *
     * 发出顺序是契约的一部分：先库层，再按 phrases 数组序逐条，同一条内按
     * 文案(blank) → sort(missing) → 分类域 → 评级域 → 重复(en/shortcut)。
     */
    fun validate(library: PhraseLibrary): List<String> {
        val errors = mutableListOf<String>()

        if (library.version < 1) errors += "phrase-library: version must be >= 1"
        if (library.phrases.isEmpty()) errors += "phrase-library: phrases is empty"

        // en 文案与 shortcut 各自的重复检测各配一份 index 表：两者是独立的重复维度
        // （en 重复 = 两条内容相同；shortcut 重复 = 两条抢同一个展开键，即便文案不同）。
        val firstIndexByEn = mutableMapOf<String, Int>()
        val firstIndexByShortcut = mutableMapOf<String, Int>()

        library.phrases.forEachIndexed { index, phrase ->
            val label = "phrase[$index]"

            if (phrase.en.isBlank()) errors += "$label: en is blank"
            if (phrase.zh.isBlank()) errors += "$label: zh is blank"
            if (phrase.sort == null) errors += "$label: sort is missing"
            if (phrase.category !in PhraseDomains.CATEGORIES) errors += "$label: unknown category ${phrase.category}"

            val applies = phrase.appliesToStatuses
            if (applies != null) {
                if (applies.isEmpty()) {
                    errors += "$label: appliesToStatuses is empty (omit the field to apply to every status)"
                } else {
                    applies.filterNot { it in PhraseDomains.STATUSES }.forEach { status ->
                        errors += "$label: status $status is not a recognized rating value"
                    }
                }
            }

            if (phrase.en.isNotBlank()) {
                val firstIndex = firstIndexByEn[phrase.en]
                if (firstIndex != null) {
                    errors += "$label: duplicate en text (same as phrase[$firstIndex])"
                } else {
                    firstIndexByEn[phrase.en] = index
                }
            }

            val shortcut = phrase.shortcut
            if (shortcut != null) {
                if (shortcut.isBlank()) {
                    errors += "$label: shortcut is blank (omit the field instead of an empty string)"
                } else {
                    val firstIndex = firstIndexByShortcut[shortcut]
                    if (firstIndex != null) {
                        errors += "$label: duplicate shortcut $shortcut (same as phrase[$firstIndex])"
                    } else {
                        firstIndexByShortcut[shortcut] = index
                    }
                }
            }
        }
        return errors
    }
}

// 文件级 private：[LoadedPhraseLibrary.parse] 与 [PhraseLoader] 都要用；同 TemplateLoader.kt 的理由，
// Kotlin 的 private 成员只对所属类可见，private 顶层声明才是"本文件可见"。

/**
 * 严格 UTF-8 解码：坏字节**抛异常**，不替换成 U+FFFD（同 `TemplateLoader.kt` 的
 * `decodeUtf8Strict`：替换策略会让损坏文件"加载成功"且无人知晓，见该处注释）。
 */
private fun decodeUtf8Strict(bytes: ByteArray): String =
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()

/**
 * 把解析出来的集合包成不可变（同 `TemplateLoader.kt` 的 `freeze`：kotlinx 产出的是
 * `ArrayList`/只读视图，强转回 `MutableList` 就能改）。
 */
private fun freeze(library: PhraseLibrary): PhraseLibrary = library.copy(
    phrases = Collections.unmodifiableList(
        library.phrases.map { phrase ->
            val applies = phrase.appliesToStatuses
            if (applies == null) phrase else phrase.copy(appliesToStatuses = Collections.unmodifiableList(applies))
        },
    ),
)

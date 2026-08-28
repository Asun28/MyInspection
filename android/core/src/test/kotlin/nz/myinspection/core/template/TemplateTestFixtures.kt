package nz.myinspection.core.template

/**
 * 本卡唯一的 fixture 模板（non_goals：真实模板内容归 T2-ROUTINE-CONTENT / T6-TEMPLATES-REST）。
 *
 * 刻意**拼字符串**而不是落 `data/templates/` 下的 json 文件：每个坏 fixture 只与好 fixture 差**一处**，
 * 断言面因此恰好等于被测的那条规则（L165）；落成文件的话，要么堆十几份近乎重复的 json，
 * 要么在测试里改文件——两者都会让"到底哪一处让它红了"变模糊。
 * （另：`.gitignore` 把 data 目录内容整体排除，样例 json 要 `git add -f` 才入库，夹具不该依赖那种文件。）
 *
 * [item] / [template] 的枚举类参数收的是**原始 JSON 片段**（不是值），这样 `null`、非数组、
 * 越界值这些坏形态都表达得出来。
 */
internal object TemplateTestFixtures {
    /** 出租类模板的四态评级域，JSON 数组字面量。 */
    const val RENTAL_STATUSES: String = """["GOOD","FAIR","POOR","NOT_APPLICABLE"]"""

    /** 年检模板的五态评级域，JSON 数组字面量。 */
    const val ANNUAL_STATUSES: String = """["NO_ISSUE","MONITOR","MAINTENANCE_ITEM","SIGNIFICANT_DEFECT","NOT_APPLICABLE"]"""

    fun item(
        stableId: String = "KIT-BENCH-01",
        area: String = "INTERIOR",
        room: String = "KITCHEN",
        textEn: String = "Bench tops and splashback",
        textZh: String = "厨房台面与挡水板",
        allowedStatuses: String = RENTAL_STATUSES,
        photoRule: String = "\"ADVERSE_ONLY\"",
    ): String =
        """{"stableId":"$stableId","area":"$area","room":"$room","textEn":"$textEn","textZh":"$textZh","allowedStatuses":$allowedStatuses,"photoRule":$photoRule}"""

    fun template(
        type: String = "ROUTINE",
        version: Int = 1,
        items: List<String> = listOf(item()),
    ): String = """{"type":"$type","version":$version,"items":[${items.joinToString(",")}]}"""

    /** 好 fixture：3 条、2 个房间、photoRule 三种取值各一。 */
    fun routineTemplate(): String = template(
        items = listOf(
            item(),
            item(stableId = "KIT-ROOM-01", textEn = "Kitchen overview", textZh = "厨房整体", photoRule = "\"ROOM_PANORAMA\""),
            item(stableId = "BED-WALL-01", room = "BEDROOM", textEn = "Walls and ceiling", textZh = "墙面与天花", photoRule = "null"),
        ),
    )

    /** T2-ROOM-REPEATABLE 的持久层夹具：房间顺序与 repeatable 两态都可精确断言。 */
    fun routineTemplateWithRooms(): String =
        """{"type":"ROUTINE","version":1,"rooms":[{"key":"BEDROOM","repeatable":true},{"key":"KITCHEN","repeatable":false}],"items":[${
            listOf(
                item(stableId = "BED-WALL-01", room = "BEDROOM", textEn = "Walls and ceiling", textZh = "墙面与天花", photoRule = "null"),
                item(),
                item(stableId = "KIT-ROOM-01", textEn = "Kitchen overview", textZh = "厨房整体", photoRule = "\"ROOM_PANORAMA\""),
            ).joinToString(",")
        }]}"""

    /**
     * content_hash 的黄金向量。**单行**且逐字节钉死：Kotlin 原始字符串会原样保留源文件的换行，
     * 多行 fixture 的哈希会随 CRLF/LF（`.gitattributes` 与各人的检出设置）漂移，那样的"黄金向量"
     * 只是把测试变成薛定谔的红。含一个非 ASCII 字符，顺带钉住"按 UTF-8 字节哈希"。
     */
    const val GOLDEN_JSON: String =
        """{"type":"ROUTINE","version":1,"items":[{"stableId":"KIT-BENCH-01","area":"INTERIOR","room":"KITCHEN","textEn":"Bench","textZh":"台面","allowedStatuses":["GOOD","FAIR","POOR","NOT_APPLICABLE"],"photoRule":null}]}"""

    /** `sha256([GOLDEN_JSON] 的 UTF-8 字节)`，小写十六进制。 */
    const val GOLDEN_SHA256: String = "30f7b40f4e95d896a16f9d4b41e8925452c79006669ca2d27e8a23a5961bc092"
}

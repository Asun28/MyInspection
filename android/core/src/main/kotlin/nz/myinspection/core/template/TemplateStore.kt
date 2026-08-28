package nz.myinspection.core.template

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import nz.myinspection.core.db.ClockMs
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.SystemClockMs
import nz.myinspection.core.db.Uuid7Generator
import java.util.Collections

/**
 * 把一份已加载的模板落进 `template_version` + `check_item_def`，以及反向读回。
 *
 * 只收 [LoadedTemplate]。它的构造器是 private、唯一出生点 [LoadedTemplate.parse] 只收字节，
 * 所以到达这里的东西**必然**已过校验、且 contentHash **必然**是它自己那份源字节的 SHA-256——
 * 这里因此不再重复校验：那会是一段永不触发、也无法写出测试的死代码（同 R3 对无测试守卫的判定）。
 */
class TemplateStore(
    private val db: MyInspectionDatabase,
    private val uuid: Uuid7Generator = Uuid7Generator(),
    private val clock: ClockMs = SystemClockMs,
) {

    /**
     * 单事务写入版本行与全部项定义，返回 `template_version.id`。
     *
     * 事务不可省：半份模板落库（版本行在、项少几条）比整体失败坏得多——报告会静默少项，
     * 而那一版的 (type, version) 已被唯一索引占住，正确的重灌反而再也进不来。
     *
     * 同一 (type, version) 已有活跃行时抛 UNIQUE 约束异常，**不吞**：那正是「同版本号不同内容」
     * 要被人看见的时刻。
     */
    fun persist(loaded: LoadedTemplate): String {
        val versionId = uuid.next()
        val now = clock.nowMs()
        db.transaction {
            db.templateVersionQueries.insert(
                id = versionId,
                type = loaded.template.type,
                version = loaded.template.version.toLong(),
                content_hash = loaded.contentHash,
                created_at = now,
                updated_at = now,
            )
            loaded.template.rooms.forEachIndexed { index, room ->
                val affected = db.templateRoomDefQueries.insert(
                    id = uuid.next(),
                    template_version_id = versionId,
                    room_key = room.key,
                    repeatable = if (room.repeatable) 1L else 0L,
                    sort = index.toLong(),
                    created_at = now,
                    updated_at = now,
                ).value
                check(affected == 1L) {
                    "template_room_def insert affected $affected rows for ${room.key} (guard rejected the write)"
                }
            }
            loaded.template.items.forEachIndexed { index, item ->
                val affected = db.checkItemDefQueries.insert(
                    id = uuid.next(),
                    template_version_id = versionId,
                    stable_id = item.stableId,
                    area = item.area,
                    room = item.room,
                    text_en = item.textEn,
                    text_zh = item.textZh,
                    allowed_statuses = Json.encodeToString(STATUSES, item.allowedStatuses),
                    photo_rule = item.photoRule,
                    // 数组下标即模板序，落 `check_item_def.sort`（报告排版与哈希域都按它定序）。
                    sort = index.toLong(),
                    created_at = now,
                    updated_at = now,
                ).value
                // `check_item_def.insert` 是带 WHERE EXISTS 守卫的 INSERT…SELECT：前提不满足时**0 行、不报错**
                // （L215）。当前构造下前提恒成立——版本行就是同一事务刚插的、还没有任何巡检引用它——
                // 但"守卫没命中"是这条 SQL 真实存在的返回形态，一旦它可达（比如将来允许往既有版本追加项），
                // 静默少行的代价是报告悄悄缺项。故不靠"目前不可达"免检：TemplateStoreTest 用注入驱动
                // 让这条 INSERT 如实返回 0 行，把本检查与随之而来的整体回滚都钉住。
                check(affected == 1L) {
                    "check_item_def insert affected $affected rows for ${item.stableId} (guard rejected the write)"
                }
            }
        }
        return versionId
    }

    /**
     * 按 `template_version.id` 读回模板文档；版本行不存在时返回 null。
     *
     * **刻意不看 `deleted_at`**：报告多年后仍须能一致重渲，一个被软删的模板版本照样要读得出来
     * （同 CheckItemDef.sq「软删的巡检其报告仍须可一致重渲」之理）。项与房间定义都使用
     * including-deleted 查询，避免历史模板因软删静默缺段。
     */
    fun read(templateVersionId: String): Template? {
        val version = db.templateVersionQueries.selectById(templateVersionId).executeAsOneOrNull() ?: return null
        // 读回的集合同样包成不可变，与 [TemplateLoader.load] 的产物一致——否则"从库里读的模板"
        // 比"从文件读的模板"多一条可被强转改写的口子，同一个类型两种保证是更难查的坑。
        val items = Collections.unmodifiableList(
            db.checkItemDefQueries.selectByTemplateVersionIncludingDeleted(templateVersionId).executeAsList().map { row ->
                TemplateItem(
                    stableId = row.stable_id,
                    area = row.area,
                    room = row.room,
                    textEn = row.text_en,
                    textZh = row.text_zh,
                    allowedStatuses = Collections.unmodifiableList(Json.decodeFromString(STATUSES, row.allowed_statuses)),
                    photoRule = row.photo_rule,
                )
            },
        )
        val rooms = Collections.unmodifiableList(
            db.templateRoomDefQueries.selectByTemplateVersionIncludingDeleted(templateVersionId).executeAsList().map { row ->
                TemplateRoom(key = row.room_key, repeatable = row.repeatable == 1L)
            },
        )
        return Template(type = version.type, version = version.version.toInt(), rooms = rooms, items = items)
    }

    private companion object {
        /** `check_item_def.allowed_statuses` 的编码：JSON 字符串数组（schema 注释已定此形态）。 */
        val STATUSES = ListSerializer(String.serializer())
    }
}

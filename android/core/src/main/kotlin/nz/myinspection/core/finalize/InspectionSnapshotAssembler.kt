package nz.myinspection.core.finalize

import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.model.AudioSnapshot
import nz.myinspection.core.model.InspectionItemSnapshot
import nz.myinspection.core.model.InspectionSnapshot
import nz.myinspection.core.model.PhotoSnapshot
import nz.myinspection.core.model.PropertySnapshot
import nz.myinspection.core.model.TemplateSnapshot
import nz.myinspection.core.model.TenancySnapshot

/**
 * `InspectionSnapshot` 的**唯一装配正门**（还清 TD5：`specs/tech-debt-tracker.md`）。
 *
 * ADR-0003 与 `core/model/InspectionSnapshot` 都要求 `items[]`/`photos[]`/`audios[]` 按确定的全序排列，
 * 但排序键本身不进快照——这意味着"用了哪条查询、按什么顺序拼数组"这件事**只存在于调用纪律里，canon 层
 * 验证不了**。TD5 指出的风险正是：未来任何第二条装配路径（备份复验、报告重渲等）如果不经过这个函数、
 * 自己重新拼一遍，只要顺序稍有出入，同一份数据就会算出第二个 `data_hash`——那个哈希写进 PDF 页脚自证
 * 未被事后修改，不确定性一旦存在，自证就是空的。
 *
 * 因此：**任何需要 `InspectionSnapshot` 的调用方都必须经过这个函数**，不得自己重新查询/排序拼装。
 * - `items[]` 顺序 = `inspection_item.selectByInspectionInTemplateOrder` 的返回顺序（模板序 + 确定性兜底，
 *   见该查询注释）——不额外排序，直接按行序号投影。
 * - `photos[]`/`audios[]` 顺序 = 按 `id`（UUIDv7，字符串序）升序（`InspectionSnapshot` 顶部说明的约定）。
 *
 * `finalizedAt` 由调用方传入而非从库里读：finalize 用例在**决定**好即将写入的 `finalized_at` 之后、
 * 真正写库之前，需要先用同一个值算出 `data_hash`（自引用，值一致即自洽），故不能从尚未更新的行读回。
 */
object InspectionSnapshotAssembler {

    fun assemble(database: MyInspectionDatabase, inspectionId: String, finalizedAt: Long?): InspectionSnapshot {
        val inspectionRow = database.inspectionQueries.selectById(inspectionId).executeAsOne()
        val propertyRow = database.propertyQueries.selectById(inspectionRow.property_id).executeAsOne()
        val tenancyRow = inspectionRow.tenancy_id?.let { database.tenancyQueries.selectById(it).executeAsOne() }
        val templateRow = database.templateVersionQueries.selectById(inspectionRow.template_version_id).executeAsOne()

        // 全序装配正门：items[] 必须来自这条查询，顺序即快照顺序，不得再排序/重排。
        val itemRows = database.inspectionItemQueries.selectByInspectionInTemplateOrder(inspectionId).executeAsList()
        val items = itemRows.map { row ->
            InspectionItemSnapshot(
                stableId = row.stable_id,
                status = row.status,
                note = row.note,
                wearOrDamage = row.wear_or_damage,
            )
        }

        val roomInstanceIds = database.roomInstanceQueries.selectByInspection(inspectionId).executeAsList().map { it.id }
        val photos = roomInstanceIds
            .flatMap { database.photoQueries.selectByRoomInstance(it).executeAsList() }
            .sortedBy { it.id }
            .map { row ->
                PhotoSnapshot(
                    contentHash = row.content_hash,
                    source = row.source,
                    exifTimeMs = row.exif_time_ms,
                    isRoomLevel = row.inspection_item_id == null,
                )
            }

        val audios = itemRows
            .flatMap { database.audioQueries.selectByInspectionItem(it.id).executeAsList() }
            .sortedBy { it.id }
            .map { row -> AudioSnapshot(contentHash = row.content_hash) }

        return InspectionSnapshot(
            id = inspectionRow.id,
            type = inspectionRow.type,
            tenancyId = inspectionRow.tenancy_id,
            scheduledAt = inspectionRow.scheduled_at,
            finalizedAt = finalizedAt,
            previousInspectionId = inspectionRow.previous_inspection_id,
            baselineInspectionId = inspectionRow.baseline_inspection_id,
            property = PropertySnapshot(
                id = propertyRow.id,
                address = propertyRow.address,
                kind = propertyRow.kind,
                isBoardingHouse = propertyRow.is_boarding_house == 1L,
            ),
            tenancy = tenancyRow?.let {
                TenancySnapshot(id = it.id, startMs = it.start_ms, endMs = it.end_ms)
            },
            template = TemplateSnapshot(
                id = templateRow.id,
                type = templateRow.type,
                version = templateRow.version,
                contentHash = templateRow.content_hash,
            ),
            items = items,
            photos = photos,
            audios = audios,
        )
    }
}

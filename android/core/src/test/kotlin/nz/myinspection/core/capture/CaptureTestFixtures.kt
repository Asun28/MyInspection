package nz.myinspection.core.capture

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import nz.myinspection.core.db.DbTestFixtures
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.Uuid7Generator

/**
 * 本卡测试专用夹具：把「造一份可走查模板 + 房间 + 项定义」这类样板收敛到一处。
 * 复用 [DbTestFixtures]（同模块 internal，跨包可见）造 property/template_version 这类通用父行。
 */
internal object CaptureTestFixtures {
    const val NOW: Long = DbTestFixtures.NOW
    private val STATUSES = ListSerializer(String.serializer())

    /** 出租类模板的四态评级域（与 TemplateDomains.RENTAL_STATUSES 同集，此处不依赖 template 包）。 */
    val RENTAL_STATUSES = listOf("GOOD", "FAIR", "POOR", "NOT_APPLICABLE")

    /** 年检模板的五态评级域。 */
    val ANNUAL_STATUSES = listOf("NO_ISSUE", "MONITOR", "MAINTENANCE_ITEM", "SIGNIFICANT_DEFECT", "NOT_APPLICABLE")

    fun insertCheckItemDef(
        db: MyInspectionDatabase,
        uuid: Uuid7Generator,
        templateVersionId: String,
        stableId: String,
        room: String,
        sort: Long,
        allowedStatuses: List<String> = RENTAL_STATUSES,
        photoRule: String? = null,
        now: Long = NOW,
    ): String {
        val id = uuid.next()
        db.checkItemDefQueries.insert(
            id = id,
            template_version_id = templateVersionId,
            stable_id = stableId,
            area = "INTERIOR",
            room = room,
            text_en = stableId,
            text_zh = stableId,
            allowed_statuses = Json.encodeToString(STATUSES, allowedStatuses),
            photo_rule = photoRule,
            sort = sort,
            created_at = now,
            updated_at = now,
        )
        return id
    }

    fun insertTemplateRoomDef(
        db: MyInspectionDatabase,
        uuid: Uuid7Generator,
        templateVersionId: String,
        roomKey: String,
        repeatable: Boolean,
        sort: Long,
        now: Long = NOW,
    ): String {
        val id = uuid.next()
        db.templateRoomDefQueries.insert(
            id = id,
            template_version_id = templateVersionId,
            room_key = roomKey,
            repeatable = if (repeatable) 1L else 0L,
            sort = sort,
            created_at = now,
            updated_at = now,
        )
        return id
    }

    /**
     * 两间房：KITCHEN（KIT-ROOM-01 房间全景强制 + KIT-BENCH-01 不利发现强制拍照）、
     * BEDROOM（BED-WALL-01 无拍照要求）。三条项定义的 allowedStatuses 都是出租四态域。
     */
    fun insertRoutineTemplate(
        db: MyInspectionDatabase,
        uuid: Uuid7Generator,
        type: String = "ROUTINE",
        version: Long = 1,
        now: Long = NOW,
    ): String {
        val versionId = DbTestFixtures.insertTemplateVersion(db, uuid, type = type, version = version, now = now)
        insertTemplateRoomDef(db, uuid, versionId, roomKey = "KITCHEN", repeatable = false, sort = 0, now = now)
        insertTemplateRoomDef(db, uuid, versionId, roomKey = "BEDROOM", repeatable = true, sort = 1, now = now)
        insertCheckItemDef(db, uuid, versionId, "KIT-ROOM-01", "KITCHEN", sort = 0, photoRule = "ROOM_PANORAMA", now = now)
        insertCheckItemDef(db, uuid, versionId, "KIT-BENCH-01", "KITCHEN", sort = 1, photoRule = "ADVERSE_ONLY", now = now)
        insertCheckItemDef(db, uuid, versionId, "BED-WALL-01", "BEDROOM", sort = 2, photoRule = null, now = now)
        return versionId
    }

    fun insertTenancy(
        db: MyInspectionDatabase,
        uuid: Uuid7Generator,
        propertyId: String,
        baselineInspectionId: String? = null,
        now: Long = NOW,
    ): String {
        val id = uuid.next()
        db.tenancyQueries.insert(
            id = id, property_id = propertyId, start_ms = now, end_ms = null,
            tenant_name = "Test Tenant", contact = "test@example.com",
            baseline_inspection_id = baselineInspectionId, created_at = now, updated_at = now,
        )
        return id
    }

    /** 直接把一份数据哈希写死 finalize，测试专用（真实 finalize 事务归 T3-FINALIZE）。 */
    fun finalize(db: MyInspectionDatabase, inspectionId: String, now: Long = NOW) {
        db.inspectionQueries.finalizeIfDraft(
            finalized_at = now, data_hash = "test-hash-$inspectionId", updated_at = now, id = inspectionId,
        )
    }

    fun insertRoomPhoto(
        db: MyInspectionDatabase,
        uuid: Uuid7Generator,
        roomInstanceId: String,
        inspectionItemId: String? = null,
        contentHash: String = "hash-${uuid.next()}",
        now: Long = NOW,
    ): String {
        val id = uuid.next()
        db.photoQueries.insert(
            id = id, inspection_item_id = inspectionItemId, room_instance_id = roomInstanceId,
            rel_path = "photos/$id.jpg", content_hash = contentHash, exif_time_ms = now,
            source = "CAMERA", privacy_flag = 0, created_at = now, updated_at = now,
        )
        return id
    }
}

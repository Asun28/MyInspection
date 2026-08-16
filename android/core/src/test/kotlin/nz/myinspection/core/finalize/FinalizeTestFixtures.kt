package nz.myinspection.core.finalize

import nz.myinspection.core.db.DbTestFixtures
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.Uuid7Generator

/**
 * finalize 卡自己的测试夹具，补 [DbTestFixtures]（`nz.myinspection.core.db`，本卡 `allow_paths` 之外，
 * 不可编辑）没有覆盖的几张表：`check_item_def` / `tenancy` / `photo` / `audio` / `property_item_override`。
 * `internal`——两个包同属 :core 测试编译单元，`DbTestFixtures` 本身也是这样跨包复用的。
 */
internal object FinalizeTestFixtures {

    /** `check_item_def.insert` 带守卫：父版本须存在、且**尚未被任何巡检引用**——故须先于建巡检调用。 */
    fun insertCheckItemDef(
        db: MyInspectionDatabase,
        uuid: Uuid7Generator,
        templateVersionId: String,
        stableId: String,
        room: String = "BEDROOM",
        photoRule: String? = null,
        sort: Long = 1,
        now: Long = DbTestFixtures.NOW,
    ): String {
        val id = uuid.next()
        db.checkItemDefQueries.insert(
            id = id, template_version_id = templateVersionId, stable_id = stableId, area = "INTERIOR",
            room = room, text_en = "Item $stableId", text_zh = "项目 $stableId",
            allowed_statuses = """["GOOD","FAIR","POOR","N_A"]""", photo_rule = photoRule, sort = sort,
            created_at = now, updated_at = now,
        )
        return id
    }

    fun insertTenancy(
        db: MyInspectionDatabase,
        uuid: Uuid7Generator,
        propertyId: String,
        startMs: Long,
        endMs: Long? = null,
        tenantName: String? = "J Doe",
        contact: String? = "j@example.com",
        now: Long = DbTestFixtures.NOW,
    ): String {
        val id = uuid.next()
        db.tenancyQueries.insert(
            id = id, property_id = propertyId, start_ms = startMs, end_ms = endMs,
            tenant_name = tenantName, contact = contact, baseline_inspection_id = null,
            created_at = now, updated_at = now,
        )
        return id
    }

    fun insertPropertyItemOverride(
        db: MyInspectionDatabase,
        uuid: Uuid7Generator,
        propertyId: String,
        stableId: String,
        suppressed: Boolean = true,
        now: Long = DbTestFixtures.NOW,
    ): String {
        val id = uuid.next()
        db.propertyItemOverrideQueries.insert(
            id = id, property_id = propertyId, stable_id = stableId,
            suppressed = if (suppressed) 1L else 0L, created_at = now, updated_at = now,
        )
        return id
    }

    /** 房间级全景照片：`inspection_item_id = null`。 */
    fun insertRoomLevelPhoto(
        db: MyInspectionDatabase,
        uuid: Uuid7Generator,
        roomInstanceId: String,
        contentHash: String = "photo-${uuid.next()}",
        now: Long = DbTestFixtures.NOW,
    ): String {
        val id = uuid.next()
        db.photoQueries.insert(
            id = id, inspection_item_id = null, room_instance_id = roomInstanceId,
            rel_path = "$id.jpg", content_hash = contentHash, exif_time_ms = now, source = "CAMERA",
            privacy_flag = 0, created_at = now, updated_at = now,
        )
        return id
    }

    /** 项目级照片：挂在具体检查项上（不利发现证据）。 */
    fun insertItemPhoto(
        db: MyInspectionDatabase,
        uuid: Uuid7Generator,
        roomInstanceId: String,
        inspectionItemId: String,
        contentHash: String = "photo-${uuid.next()}",
        now: Long = DbTestFixtures.NOW,
    ): String {
        val id = uuid.next()
        db.photoQueries.insert(
            id = id, inspection_item_id = inspectionItemId, room_instance_id = roomInstanceId,
            rel_path = "$id.jpg", content_hash = contentHash, exif_time_ms = now, source = "CAMERA",
            privacy_flag = 0, created_at = now, updated_at = now,
        )
        return id
    }

    fun insertAudio(
        db: MyInspectionDatabase,
        uuid: Uuid7Generator,
        inspectionItemId: String,
        contentHash: String = "audio-${uuid.next()}",
        now: Long = DbTestFixtures.NOW,
    ): String {
        val id = uuid.next()
        db.audioQueries.insert(
            id = id, inspection_item_id = inspectionItemId, rel_path = "$id.m4a",
            content_hash = contentHash, created_at = now, updated_at = now,
        )
        return id
    }

    /**
     * 最小"可 finalize"夹具：一间物业、一版模板（单项 `wall.paint`、无强制拍照要求）、一间巡检、
     * 一个房间实例、该项已作答。刚好满足完备性校验（无照片规则可缺）。
     */
    fun buildMinimalCompleteInspection(
        db: MyInspectionDatabase,
        uuid: Uuid7Generator,
        now: Long = DbTestFixtures.NOW,
        stableId: String = "wall.paint",
        status: String = "GOOD",
    ): ReadyInspection {
        val propertyId = DbTestFixtures.insertProperty(db, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(db, uuid, now = now)
        insertCheckItemDef(db, uuid, templateVersionId, stableId = stableId, room = "BEDROOM", photoRule = null, sort = 1, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(db, uuid, propertyId, templateVersionId, now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(db, uuid, inspectionId, roomKey = "BEDROOM", now = now)
        val itemId = DbTestFixtures.insertInspectionItem(db, uuid, inspectionId, roomInstanceId, stableId = stableId, status = status, now = now)
        return ReadyInspection(propertyId, templateVersionId, inspectionId, roomInstanceId, itemId)
    }

    data class ReadyInspection(
        val propertyId: String,
        val templateVersionId: String,
        val inspectionId: String,
        val roomInstanceId: String,
        val itemId: String,
    )
}

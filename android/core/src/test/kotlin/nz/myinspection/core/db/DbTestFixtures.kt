package nz.myinspection.core.db

/**
 * 测试专用最小夹具构造器：把"插入一行满足外键前提的父行"这类重复样板收敛到一处，
 * 各测试类只描述自己真正关心的那一步。都用 [Uuid7Generator] 生成 id、共享固定时间戳，保持确定性。
 */
internal object DbTestFixtures {
    const val NOW: Long = 1_700_000_000_000L

    fun insertProperty(db: MyInspectionDatabase, uuid: Uuid7Generator, now: Long = NOW): String {
        val id = uuid.next()
        db.propertyQueries.insert(id = id, address = "12 Test St", kind = "RENTAL", is_boarding_house = 0, updated_at = now)
        return id
    }

    fun insertTemplateVersion(
        db: MyInspectionDatabase,
        uuid: Uuid7Generator,
        type: String = "ROUTINE",
        version: Long = 1,
        now: Long = NOW,
    ): String {
        val id = uuid.next()
        db.templateVersionQueries.insert(id = id, type = type, version = version, content_hash = "hash-$id", updated_at = now)
        return id
    }

    /** 建一间 DRAFT 巡检（自带 template_version 引用），返回其 id。 */
    fun insertDraftInspection(
        db: MyInspectionDatabase,
        uuid: Uuid7Generator,
        templateVersionId: String,
        tenancyId: String? = null,
        type: String = "ROUTINE",
        previousInspectionId: String? = null,
        baselineInspectionId: String? = null,
        now: Long = NOW,
    ): String {
        val id = uuid.next()
        db.inspectionQueries.insert(
            id = id,
            type = type,
            tenancy_id = tenancyId,
            template_version_id = templateVersionId,
            scheduled_at = now,
            previous_inspection_id = previousInspectionId,
            baseline_inspection_id = baselineInspectionId,
            status = "DRAFT",
            finalized_at = null,
            data_hash = null,
            updated_at = now,
        )
        return id
    }

    fun insertRoomInstance(
        db: MyInspectionDatabase,
        uuid: Uuid7Generator,
        inspectionId: String,
        roomKey: String = "BEDROOM",
        instanceNo: Long = 1,
        now: Long = NOW,
    ): String {
        val id = uuid.next()
        db.roomInstanceQueries.insert(
            id = id, inspection_id = inspectionId, room_key = roomKey, instance_no = instanceNo,
            display_label = "Bedroom", updated_at = now,
        )
        return id
    }

    fun insertInspectionItem(
        db: MyInspectionDatabase,
        uuid: Uuid7Generator,
        inspectionId: String,
        roomInstanceId: String,
        stableId: String = "wall.paint",
        status: String = "GOOD",
        now: Long = NOW,
    ): String {
        val id = uuid.next()
        db.inspectionItemQueries.insert(
            id = id, inspection_id = inspectionId, room_instance_id = roomInstanceId, stable_id = stableId,
            status = status, note = null, wear_or_damage = null, updated_at = now,
        )
        return id
    }
}

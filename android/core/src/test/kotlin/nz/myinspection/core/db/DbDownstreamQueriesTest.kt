package nz.myinspection.core.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 机械写/查路径，供上层模块调用；判断逻辑都不在这里，只有守卫与确定性：
 *  - inspection_item.updateWearOrDamageIfDraft（消费方：采集层）
 *  - property_item_override.setSuppressed / selectByPropertyAndStableId（采集层）
 *  - notice.recordDelivery（通知层）
 *  - photo.softDelete / orphanedAssets / selectActiveAssetsByContentHash（照片管线）
 */
class DbDownstreamQueriesTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: MyInspectionDatabase
    private lateinit var uuid: Uuid7Generator

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MyInspectionDatabase.Schema.create(driver)
        database = MyInspectionDatabase(driver)
        uuid = Uuid7Generator()
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    private val now = DbTestFixtures.NOW

    @Test
    fun `updateWearOrDamageIfDraft writes on a draft inspection and is blocked once finalized`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)
        val itemId = DbTestFixtures.insertInspectionItem(database, uuid, inspectionId, roomInstanceId, now = now)

        val whileDraft = database.inspectionItemQueries.updateWearOrDamageIfDraft(
            wear_or_damage = "DAMAGE", updated_at = now + 1, id = itemId,
        ).value
        assertEquals(1L, whileDraft, "writing wear_or_damage on a DRAFT inspection must succeed")
        assertEquals("DAMAGE", database.inspectionItemQueries.selectById(itemId).executeAsOne().wear_or_damage)

        database.inspectionQueries.finalizeIfDraft(finalized_at = now + 2, data_hash = "h", updated_at = now + 2, id = inspectionId).value

        val afterFinalize = database.inspectionItemQueries.updateWearOrDamageIfDraft(
            wear_or_damage = "FAIR_WEAR", updated_at = now + 3, id = itemId,
        ).value
        assertEquals(0L, afterFinalize, "writing wear_or_damage after finalize must affect 0 rows")
        assertEquals("DAMAGE", database.inspectionItemQueries.selectById(itemId).executeAsOne().wear_or_damage, "the FINALIZED value must be untouched")
    }

    @Test
    fun `property_item_override can be suppressed then restored via the same row`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        assertNull(database.propertyItemOverrideQueries.selectByPropertyAndStableId(propertyId, "hallway.light").executeAsOneOrNull())

        val overrideId = uuid.next()
        database.propertyItemOverrideQueries.insert(id = overrideId, property_id = propertyId, stable_id = "hallway.light", suppressed = 1, created_at = now, updated_at = now)

        val suppressed = database.propertyItemOverrideQueries.selectByPropertyAndStableId(propertyId, "hallway.light").executeAsOne()
        assertEquals(1L, suppressed.suppressed)

        database.propertyItemOverrideQueries.setSuppressed(suppressed = 0, updated_at = now + 1, id = overrideId)
        val restored = database.propertyItemOverrideQueries.selectByPropertyAndStableId(propertyId, "hallway.light").executeAsOne()
        assertEquals(0L, restored.suppressed, "restore must flip suppressed back to 0 on the same row, not create a second row")
        assertEquals(overrideId, restored.id, "restore reuses the existing row rather than inserting a new one")
    }

    private fun insertUnsentNotice(inspectionId: String, scheduledAt: Long = now + 200_000L): String {
        val noticeId = uuid.next()
        database.noticeQueries.insert(
            id = noticeId, inspection_id = inspectionId, full_text = "48h notice text", generated_at = now,
            scheduled_at = scheduledAt, sent_via = null, sent_at = null, lead_hours = 72,
            validation_snapshot = "{\"pass\":true}", updated_at = now,
        )
        return noticeId
    }

    @Test
    fun `notice recordDelivery locks after the first call`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val noticeId = insertUnsentNotice(inspectionId)

        val firstRecord = database.noticeQueries.recordDelivery(
            sent_via = "EMAIL", sent_at = now + 1, lead_hours = 50, validation_snapshot = "{\"pass\":true}", updated_at = now + 1, id = noticeId,
        ).value
        assertEquals(1L, firstRecord, "the first delivery record must succeed")

        val secondRecord = database.noticeQueries.recordDelivery(
            sent_via = "SMS", sent_at = now + 2, lead_hours = 40, validation_snapshot = "{\"pass\":false}", updated_at = now + 2, id = noticeId,
        ).value
        assertEquals(0L, secondRecord, "delivery is locked after the first record — a second call must affect 0 rows")

        val row = database.noticeQueries.selectById(noticeId).executeAsOne()
        assertEquals("EMAIL", row.sent_via, "the second call must not overwrite the first delivery record")
        assertEquals(50L, row.lead_hours)
        assertEquals(now + 1, row.sent_at, "sent_at must be the value bound at the moment of delivery")
    }

    @Test
    fun `notice recordDelivery with a null sent_via or sent_at affects zero rows and does not lock the row`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val noticeId = insertUnsentNotice(inspectionId)

        val nullSentVia = database.noticeQueries.recordDelivery(
            sent_via = null, sent_at = now + 1, lead_hours = 50, validation_snapshot = "{\"pass\":true}", updated_at = now + 1, id = noticeId,
        ).value
        assertEquals(0L, nullSentVia, "a NULL sent_via must not be accepted as a valid delivery record")

        val nullSentAt = database.noticeQueries.recordDelivery(
            sent_via = "EMAIL", sent_at = null, lead_hours = 50, validation_snapshot = "{\"pass\":true}", updated_at = now + 1, id = noticeId,
        ).value
        assertEquals(0L, nullSentAt, "a NULL sent_at must not be accepted as a valid delivery record")

        val stillUnsent = database.noticeQueries.selectById(noticeId).executeAsOne()
        assertNull(stillUnsent.sent_at, "rejected NULL attempts must not lock the row — a real delivery record must still be possible afterwards")

        val realRecord = database.noticeQueries.recordDelivery(
            sent_via = "EMAIL", sent_at = now + 2, lead_hours = 50, validation_snapshot = "{\"pass\":true}", updated_at = now + 2, id = noticeId,
        ).value
        assertEquals(1L, realRecord, "a real delivery record must still succeed after rejected NULL attempts")
    }

    @Test
    fun `notice scheduled_at is stored as its own independent snapshot`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val snapshotScheduledAt = now + 999_000L

        val noticeId = insertUnsentNotice(inspectionId, scheduledAt = snapshotScheduledAt)

        val row = database.noticeQueries.selectById(noticeId).executeAsOne()
        assertEquals(
            snapshotScheduledAt,
            row.scheduled_at,
            "scheduled_at must be its own stored column, not derived live from inspection.scheduled_at at read time",
        )
    }

    @Test
    fun `notice rejects a mismatched sent_via+sent_at pair at insert time`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)

        val sentAtOnly = assertFailsWith<Exception>("sent_at set with sent_via NULL must violate the CHECK constraint") {
            database.noticeQueries.insert(
                id = uuid.next(), inspection_id = inspectionId, full_text = "t", generated_at = now,
                scheduled_at = now + 1000L, sent_via = null, sent_at = now, lead_hours = 72,
                validation_snapshot = "{}", updated_at = now,
            )
        }
        assertTrue(sentAtOnly.message.orEmpty().contains("CHECK", ignoreCase = true), "expected a CHECK violation, got: ${sentAtOnly.message}")

        val sentViaOnly = assertFailsWith<Exception>("sent_via set with sent_at NULL must violate the CHECK constraint") {
            database.noticeQueries.insert(
                id = uuid.next(), inspection_id = inspectionId, full_text = "t", generated_at = now,
                scheduled_at = now + 1000L, sent_via = "EMAIL", sent_at = null, lead_hours = 72,
                validation_snapshot = "{}", updated_at = now,
            )
        }
        assertTrue(sentViaOnly.message.orEmpty().contains("CHECK", ignoreCase = true), "expected a CHECK violation, got: ${sentViaOnly.message}")
    }

    @Test
    fun `supplement prev_hash anchors to the inspection data_hash and stores its own chain_hash`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        database.inspectionQueries.finalizeIfDraft(finalized_at = now + 1, data_hash = "inspection-data-hash", updated_at = now + 1, id = inspectionId).value

        val supplementId = uuid.next()
        database.supplementQueries.insert(
            id = supplementId, inspection_id = inspectionId, created_at = now + 2, text = "landlord to fix gate latch",
            prev_hash = "inspection-data-hash", chain_hash = "chain-hash-1", updated_at = now + 2,
        )

        val row = database.supplementQueries.selectById(supplementId).executeAsOne()
        assertEquals("inspection-data-hash", row.prev_hash, "the first supplement's prev_hash must anchor to the inspection's data_hash, not be NULL")
        assertEquals("chain-hash-1", row.chain_hash, "chain_hash must be a real stored column so a later verifyChain has something to check against")
    }

    @Test
    fun `supplement selectByInspection breaks created_at ties deterministically by id`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        database.inspectionQueries.finalizeIfDraft(finalized_at = now + 1, data_hash = "d0", updated_at = now + 1, id = inspectionId).value

        // Two supplements inserted with the *same* created_at millisecond — sort must not depend on
        // insertion order or SQLite's unspecified tie-handling; it must be deterministic by id.
        val sameMoment = now + 2
        val idA = uuid.next()
        val idB = uuid.next()
        val (first, second) = if (idA < idB) idA to idB else idB to idA
        database.supplementQueries.insert(id = second, inspection_id = inspectionId, created_at = sameMoment, text = "second by id", prev_hash = "d0", chain_hash = "c2", updated_at = sameMoment)
        database.supplementQueries.insert(id = first, inspection_id = inspectionId, created_at = sameMoment, text = "first by id", prev_hash = "d0", chain_hash = "c1", updated_at = sameMoment)

        val ordered = database.supplementQueries.selectByInspection(inspectionId).executeAsList()
        assertEquals(listOf(first, second), ordered.map { it.id }, "tied created_at values must break ties by id, deterministically")
    }

    @Test
    fun `photo softDelete is blocked once the owning inspection is finalized`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)
        val photoId = uuid.next()
        database.photoQueries.insert(
            id = photoId, inspection_item_id = null, room_instance_id = roomInstanceId, rel_path = "a.jpg",
            content_hash = "hash-a", exif_time_ms = null, source = "CAMERA", privacy_flag = 0, created_at = now, updated_at = now,
        )

        database.inspectionQueries.finalizeIfDraft(finalized_at = now + 1, data_hash = "h", updated_at = now + 1, id = inspectionId).value

        val affected = database.photoQueries.softDelete(deleted_at = now + 2, id = photoId).value
        assertEquals(0L, affected, "soft-deleting a photo under a FINALIZED inspection must affect 0 rows")
        assertNull(database.photoQueries.selectById(photoId).executeAsOne().deleted_at)
    }

    @Test
    fun `orphanedAssets lists rel_paths with zero active rows and never a finalized inspection's asset`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)

        // Draft inspection: photo gets soft-deleted -> its rel_path has no active row left, so it orphans.
        val draftInspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val draftRoomId = DbTestFixtures.insertRoomInstance(database, uuid, draftInspectionId, now = now)
        val orphanPhotoId = uuid.next()
        database.photoQueries.insert(
            id = orphanPhotoId, inspection_item_id = null, room_instance_id = draftRoomId, rel_path = "photos/orphan.jpg",
            content_hash = "orphan-hash", exif_time_ms = null, source = "CAMERA", privacy_flag = 0, created_at = now, updated_at = now,
        )
        val deleted = database.photoQueries.softDelete(deleted_at = now + 1, id = orphanPhotoId).value
        assertEquals(1L, deleted, "soft-deleting a photo under a DRAFT inspection must succeed")

        // Finalized inspection: its photo can never be soft-deleted, so its asset can never orphan.
        val finalInspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now + 10)
        val finalRoomId = DbTestFixtures.insertRoomInstance(database, uuid, finalInspectionId, now = now + 10)
        database.photoQueries.insert(
            id = uuid.next(), inspection_item_id = null, room_instance_id = finalRoomId, rel_path = "photos/kept.jpg",
            content_hash = "finalized-hash", exif_time_ms = null, source = "CAMERA", privacy_flag = 0, created_at = now + 10, updated_at = now + 10,
        )
        database.inspectionQueries.finalizeIfDraft(finalized_at = now + 11, data_hash = "h", updated_at = now + 11, id = finalInspectionId).value

        val orphans = database.photoQueries.orphanedAssets().executeAsList()
        assertEquals(listOf("photos/orphan.jpg"), orphans, "only the fully-unreferenced path is orphaned, and the caller gets the physical file to delete")
        assertTrue(orphans.none { it == "photos/kept.jpg" }, "a finalized inspection's photo asset must never be reported as orphaned")
        assertNotNull(database.photoQueries.selectById(orphanPhotoId).executeAsOne().deleted_at)
    }

    @Test
    fun `orphanedAssets judges liveness per rel_path, so a shared hash cannot cover for a deleted path`() {
        // Same content_hash, two different physical files (two different rel_path values) — a real
        // scenario this schema explicitly allows (dedup is scoped per room_instance, not global).
        // One path's row gets soft-deleted while the other path's row stays active. Matching liveness
        // by content_hash alone would let the still-active path "cover for" the deleted one, leaking
        // the orphaned file at path A forever (the exact bug this test pins).
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomA = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = "BEDROOM", instanceNo = 1, now = now)
        val roomB = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = "BEDROOM", instanceNo = 2, now = now)

        val photoAtPathA = uuid.next()
        database.photoQueries.insert(
            id = photoAtPathA, inspection_item_id = null, room_instance_id = roomA, rel_path = "photos/path-a.jpg",
            content_hash = "shared-hash", exif_time_ms = null, source = "CAMERA", privacy_flag = 0, created_at = now, updated_at = now,
        )
        database.photoQueries.insert(
            id = uuid.next(), inspection_item_id = null, room_instance_id = roomB, rel_path = "photos/path-b.jpg",
            content_hash = "shared-hash", exif_time_ms = null, source = "CAMERA", privacy_flag = 0, created_at = now, updated_at = now,
        )
        database.photoQueries.softDelete(deleted_at = now + 1, id = photoAtPathA).value

        val orphans = database.photoQueries.orphanedAssets().executeAsList()
        assertTrue(
            orphans.contains("photos/path-a.jpg"),
            "the deleted path must be reported orphaned even though the same content_hash still has an active row at a different path",
        )
        assertTrue(
            orphans.none { it == "photos/path-b.jpg" },
            "the still-active path must never be reported orphaned",
        )
    }

    /**
     * 反向的那一半：删除目标是**路径**，而 schema 不保证「一个 rel_path 只对应一个
     * content_hash」——唯一索引管的是 (room_instance_id, content_hash)，不管路径。按 (hash, path) 判活时，
     * 软删的 (H1, P) 会让 P 被报成孤儿，尽管活跃的 (H2, P) 还指着同一个物理文件；清理任务照报告删下去，
     * 就把仍在用的文件删了。让活跃行属于 **FINALIZED 巡检**，把后果顶到最严重：巡检证据被抹掉，
     * 而卡里原本的论证（「finalized 的照片不能软删，故其资产恒有活跃行」）在这个形状下正好失效。
     */
    @Test
    fun `orphanedAssets never reports a path that a finalized inspection still references under a different hash`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)

        // 草稿巡检里 (H1, P) 被软删。
        val draftInspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val draftRoomId = DbTestFixtures.insertRoomInstance(database, uuid, draftInspectionId, now = now)
        val deletedRowAtSharedPath = uuid.next()
        database.photoQueries.insert(
            id = deletedRowAtSharedPath, inspection_item_id = null, room_instance_id = draftRoomId,
            rel_path = "photos/shared-path.jpg", content_hash = "hash-1", exif_time_ms = null,
            source = "CAMERA", privacy_flag = 0, created_at = now, updated_at = now,
        )
        assertEquals(1L, database.photoQueries.softDelete(deleted_at = now + 1, id = deletedRowAtSharedPath).value)

        // 另一次巡检里 (H2, **同一个 P**) 仍活跃，且该巡检已 finalize——证据不可再动。
        val finalInspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now + 10)
        val finalRoomId = DbTestFixtures.insertRoomInstance(database, uuid, finalInspectionId, now = now + 10)
        database.photoQueries.insert(
            id = uuid.next(), inspection_item_id = null, room_instance_id = finalRoomId,
            rel_path = "photos/shared-path.jpg", content_hash = "hash-2", exif_time_ms = null,
            source = "CAMERA", privacy_flag = 0, created_at = now + 10, updated_at = now + 10,
        )
        database.inspectionQueries.finalizeIfDraft(finalized_at = now + 11, data_hash = "h", updated_at = now + 11, id = finalInspectionId)

        assertTrue(
            database.photoQueries.orphanedAssets().executeAsList().none { it == "photos/shared-path.jpg" },
            "a path still referenced by an active row must never be handed to the cleanup job, whatever hash that row carries",
        )
    }

    @Test
    fun `purgeContactInfo with a null purged_at affects zero rows and does not corrupt the row`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val tenancyId = uuid.next()
        database.tenancyQueries.insert(
            id = tenancyId, property_id = propertyId, start_ms = now - 86_400_000L, end_ms = now,
            tenant_name = "J Doe", contact = "j@example.com", baseline_inspection_id = null, created_at = now, updated_at = now,
        )

        val nullAttempt = database.tenancyQueries.purgeContactInfo(purged_at = null, updated_at = now + 1, id = tenancyId).value
        assertEquals(0L, nullAttempt, "a NULL purged_at must not be accepted — it would clear contact fields while leaving purged_at NULL")

        val untouched = database.tenancyQueries.selectById(tenancyId).executeAsOne()
        assertEquals("J Doe", untouched.tenant_name, "a rejected NULL attempt must not clear contact fields")
        assertNull(untouched.purged_at)

        val realPurge = database.tenancyQueries.purgeContactInfo(purged_at = now + 2, updated_at = now + 2, id = tenancyId).value
        assertEquals(1L, realPurge, "a real purge must still succeed after a rejected NULL attempt")
        val purged = database.tenancyQueries.selectById(tenancyId).executeAsOne()
        assertNull(purged.tenant_name)
        assertEquals(now + 2, purged.purged_at)
    }

    /**
     * softDelete 的形参是 `Long?`（该参数同时绑可空的 deleted_at 与 NOT NULL 的 updated_at，SQLDelight
     * 按名推断即取可空）。传 NULL 时若无守卫，WHERE 全过、UPDATE 真执行、写 updated_at 时撞 NOT NULL 抛
     * 未受控异常——而本族查询的约定是「守卫不过＝0 行、不落地、可重试」（同 purgeContactInfo 的 NULL 用例）。
     */
    @Test
    fun `photo softDelete with a null deleted_at affects zero rows and leaves the row active`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)
        val photoId = uuid.next()
        database.photoQueries.insert(
            id = photoId, inspection_item_id = null, room_instance_id = roomInstanceId,
            rel_path = "photos/a.jpg", content_hash = "h", exif_time_ms = null, source = "CAMERA",
            privacy_flag = 0, created_at = now, updated_at = now,
        )

        val nullAttempt = database.photoQueries.softDelete(deleted_at = null, id = photoId).value
        assertEquals(0L, nullAttempt, "a NULL deleted_at must be refused as zero rows, not raise a NOT NULL failure")

        val stillActive = database.photoQueries.selectByRoomInstance(roomInstanceId).executeAsList()
        assertEquals(1, stillActive.size, "the rejected NULL attempt must leave the photo active")
        assertEquals(now, stillActive.single().updated_at, "a refused call must not touch updated_at either")

        val realDelete = database.photoQueries.softDelete(deleted_at = now + 1, id = photoId).value
        assertEquals(1L, realDelete, "a real soft delete must still succeed after a refused NULL attempt")
    }

    /**
     * T2-PHOTO-PIPELINE 去重链路的前半步：「哈希已存在就复用该资产、只建新关联」。既有两条查询都不顶用——
     * selectByRoomInstance 要求已知 room_instance_id（去重时恰恰还不知道要挂到哪），orphanedAssets 只回
     * 软删行（去重要的是活着的那些）。
     *
     * 一个用例覆四件事：**全部活跃路径都回**、**顺序确定**、**软删的不回**、**复用后不重复计数**。
     * 插入顺序刻意与期望顺序相反（先 path-b 后 path-a），否则「按 rel_path 排序」和「按插入顺序返回」
     * 两种实现都能让断言通过——排序那句就没被测到（L165）。
     */
    @Test
    fun `selectActiveAssetsByContentHash returns active paths in deterministic order and excludes soft-deleted ones`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        // 三个房间：idx_photo_active 把去重唯一性限定在**单个 room_instance 内**（有意如此，见 Photo.sq
        // 该索引注释：同一照片内容跨巡检/跨房间合法出现）。所以「同哈希、多物理路径」只可能跨房间成立，
        // 而那正是 T2-PHOTO-PIPELINE 要的跨巡检复用场景——同一房间内塞两次同哈希反而该被唯一索引拦下。
        val roomA = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = "BEDROOM", instanceNo = 1, now = now)
        val roomB = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = "BEDROOM", instanceNo = 2, now = now)
        val roomC = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = "KITCHEN", instanceNo = 1, now = now)

        fun addPhoto(roomInstanceId: String, relPath: String, hash: String): String {
            val id = uuid.next()
            val affected = database.photoQueries.insert(
                id = id, inspection_item_id = null, room_instance_id = roomInstanceId,
                rel_path = relPath, content_hash = hash, exif_time_ms = null, source = "CAMERA",
                privacy_flag = 0, created_at = now, updated_at = now,
            ).value
            assertEquals(1L, affected, "fixture photo $relPath must actually be inserted")
            return id
        }

        // 同一哈希两份物理副本（分属两个房间），**倒序插入**；外加一份无关哈希，证明查询按哈希收敛。
        // path-b 全程保持活跃：它是上面「最后一条关联被删后仍幸存的复用目标」那条断言的对照物。
        addPhoto(roomA, "photos/path-b.jpg", "shared-hash")
        val firstAssociationAtPathA = addPhoto(roomB, "photos/path-a.jpg", "shared-hash")
        addPhoto(roomA, "photos/unrelated.jpg", "other-hash")

        assertEquals(
            listOf("photos/path-a.jpg", "photos/path-b.jpg"),
            database.photoQueries.selectActiveAssetsByContentHash("shared-hash").executeAsList(),
            "every active path for the hash must come back, ordered by rel_path rather than by insertion order",
        )
        assertEquals(
            emptyList(),
            database.photoQueries.selectActiveAssetsByContentHash("no-such-hash").executeAsList(),
            "an unknown hash must return nothing, so the caller imports a fresh file instead of reusing a wrong one",
        )

        // 复用：第三个房间的新关联指向查回来的同一份物理文件（跨房间才不撞 idx_photo_active）。
        // DISTINCT 必须把它折叠掉，否则调用方会以为磁盘上有两份。
        val reusedPath = database.photoQueries.selectActiveAssetsByContentHash("shared-hash").executeAsList().first()
        val reuseAssociation = addPhoto(roomC, reusedPath, "shared-hash")
        assertEquals(
            listOf("photos/path-a.jpg", "photos/path-b.jpg"),
            database.photoQueries.selectActiveAssetsByContentHash("shared-hash").executeAsList(),
            "reusing an asset adds an association, not a physical file — the distinct path set must not grow",
        )

        // 存活粒度与 orphanedAssets 一致：路径的死活看**该 rel_path 是否还有活跃行**。
        // path-a 此刻有两条关联（原始 + 复用），删掉其中一条，那份物理文件仍被引用，必须继续可复用。
        database.photoQueries.softDelete(deleted_at = now + 1, id = firstAssociationAtPathA)
        assertEquals(
            listOf("photos/path-a.jpg", "photos/path-b.jpg"),
            database.photoQueries.selectActiveAssetsByContentHash("shared-hash").executeAsList(),
            "dropping one of two associations must not retire the physical file the other one still points at",
        )

        // 而把某个路径的**最后一条**活跃关联删掉后，它不得再被当成可复用目标（否则新关联会指向已被清理的文件）。
        database.photoQueries.softDelete(deleted_at = now + 2, id = reuseAssociation)
        assertEquals(
            listOf("photos/path-b.jpg"),
            database.photoQueries.selectActiveAssetsByContentHash("shared-hash").executeAsList(),
            "a path whose last active association is gone must stop being offered for reuse",
        )
        // 与 orphanedAssets 的口径对上：刚退出复用池的那份物理文件，正是它该报告的孤儿。
        assertTrue(
            database.photoQueries.orphanedAssets().executeAsList().contains("photos/path-a.jpg"),
            "the path that just left the reuse pool is exactly what orphanedAssets must hand to the cleanup job",
        )
    }
}

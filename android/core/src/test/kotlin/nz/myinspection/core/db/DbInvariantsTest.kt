package nz.myinspection.core.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CLAUDE.md 关键不变量的 JVM 内存库回归测试（JdbcSqliteDriver in-memory，非 mock）：
 *  1. finalize 后原始条目只读：对 FINALIZED 巡检既不能 UPDATE 既有 inspection_item，
 *     也不能 INSERT 新的 inspection_item/room_instance/photo/audio；supplement 是唯一的
 *     append-only 例外，不适用此闸。
 *  2. inspection 的 status/finalized_at/data_hash 三者必须联动一致（结构性 CHECK 约束）。
 *  3. 既有租约没有 Ingoing 时，可把某次 Routine 巡检指定为该 tenancy 的基线，且真建一个 EXIT 巡检、
 *     经这个指针解析出同一个 Routine（不是只测指针本身被设对了——那样测不出 Exit 侧是否仍在假设
 *     "必有 INGOING"）。
 *
 * 引用完整性的缺失/错配父行测试（EXISTS 守卫拦孤儿行/跨巡检串接数据）另见 DbReferentialIntegrityTest。
 */
class DbInvariantsTest {
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

    private fun setUpFinalizedInspectionWithItem(): Triple<String, String, String> {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)
        val itemId = DbTestFixtures.insertInspectionItem(database, uuid, inspectionId, roomInstanceId, now = now)
        database.inspectionQueries.finalizeIfDraft(finalized_at = now + 1, data_hash = "deadbeef", updated_at = now + 1, id = inspectionId).value
        return Triple(inspectionId, roomInstanceId, itemId)
    }

    @Test
    fun `update against a finalized inspection affects zero rows`() {
        val (_, _, itemId) = setUpFinalizedInspectionWithItem()

        val affected = database.inspectionItemQueries.updateStatusIfDraft(
            status = "POOR", note = "changed after finalize", updated_at = now + 2, id = itemId,
        ).value

        assertEquals(0L, affected, "updating an item under a FINALIZED inspection must affect 0 rows")
        val stillGood = database.inspectionItemQueries.selectById(itemId).executeAsOne()
        assertEquals("GOOD", stillGood.status)
    }

    @Test
    fun `update against a draft inspection succeeds`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)
        val itemId = DbTestFixtures.insertInspectionItem(database, uuid, inspectionId, roomInstanceId, now = now)

        val affected = database.inspectionItemQueries.updateStatusIfDraft(
            status = "POOR", note = "changed while draft", updated_at = now + 1, id = itemId,
        ).value

        assertEquals(1L, affected, "updating an item under a DRAFT inspection must succeed")
        val updated = database.inspectionItemQueries.selectById(itemId).executeAsOne()
        assertEquals("POOR", updated.status)
    }

    @Test
    fun `insert of a new inspection_item into a finalized inspection affects zero rows`() {
        val (inspectionId, roomInstanceId, _) = setUpFinalizedInspectionWithItem()

        val affected = database.inspectionItemQueries.insert(
            id = uuid.next(), inspection_id = inspectionId, room_instance_id = roomInstanceId,
            stable_id = "ceiling.paint", status = "GOOD", note = null, wear_or_damage = null, created_at = now + 2, updated_at = now + 2,
        ).value

        assertEquals(0L, affected, "inserting a new item under a FINALIZED inspection must affect 0 rows")
    }

    @Test
    fun `insert of a new room_instance into a finalized inspection affects zero rows`() {
        val (inspectionId, _, _) = setUpFinalizedInspectionWithItem()

        val affected = database.roomInstanceQueries.insert(
            id = uuid.next(), inspection_id = inspectionId, room_key = "KITCHEN", instance_no = 1,
            display_label = "Kitchen", created_at = now + 2, updated_at = now + 2,
        ).value

        assertEquals(0L, affected, "inserting a new room instance under a FINALIZED inspection must affect 0 rows")
    }

    @Test
    fun `insert of a new photo into a finalized inspection affects zero rows`() {
        // room_instance created before finalize (legitimate pre-finalize content); the guard is on the
        // photo insert itself, resolved two hops up through room_instance -> inspection.finalized_at.
        val (_, roomInstanceId, _) = setUpFinalizedInspectionWithItem()

        val affected = database.photoQueries.insert(
            id = uuid.next(), inspection_item_id = null, room_instance_id = roomInstanceId,
            rel_path = "late.jpg", content_hash = "latehash", exif_time_ms = null, source = "CAMERA",
            privacy_flag = 0, created_at = now + 2, updated_at = now + 2,
        ).value

        assertEquals(0L, affected, "inserting a new photo under a FINALIZED inspection must affect 0 rows")
    }

    @Test
    fun `insert of a new audio into a finalized inspection affects zero rows`() {
        // inspection_item created before finalize; the guard is on the audio insert itself, resolved
        // two hops up through inspection_item -> inspection.finalized_at.
        val (_, _, itemId) = setUpFinalizedInspectionWithItem()

        val affected = database.audioQueries.insert(
            id = uuid.next(), inspection_item_id = itemId, rel_path = "late.m4a", content_hash = "latehash",
            created_at = now + 2, updated_at = now + 2,
        ).value

        assertEquals(0L, affected, "inserting new audio under a FINALIZED inspection must affect 0 rows")
    }

    @Test
    fun `finalizeIfDraft is a one-shot guard on inspection itself`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)

        val firstFinalize = database.inspectionQueries.finalizeIfDraft(
            finalized_at = now + 1, data_hash = "abc123", updated_at = now + 1, id = inspectionId,
        ).value
        assertEquals(1L, firstFinalize, "finalizing a DRAFT inspection must succeed exactly once")

        val secondFinalize = database.inspectionQueries.finalizeIfDraft(
            finalized_at = now + 2, data_hash = "should-not-land", updated_at = now + 2, id = inspectionId,
        ).value
        assertEquals(0L, secondFinalize, "re-finalizing an already FINALIZED inspection must affect 0 rows")

        val row = database.inspectionQueries.selectById(inspectionId).executeAsOne()
        assertEquals("abc123", row.data_hash, "the second call must not overwrite the original finalize hash")
    }

    @Test
    fun `inspection rejects a FINALIZED row with no finalized_at or data_hash`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val ex = assertFailsWith<Exception>("an incomplete FINALIZED state must violate the CHECK constraint") {
            database.inspectionQueries.insert(
                id = uuid.next(), type = "ROUTINE", property_id = propertyId, tenancy_id = null,
                template_version_id = templateVersionId, scheduled_at = now, previous_inspection_id = null,
                baseline_inspection_id = null, status = "FINALIZED", finalized_at = null, data_hash = null,
                created_at = now, updated_at = now,
            )
        }
        assertTrue(ex.message.orEmpty().contains("CHECK", ignoreCase = true), "expected a CHECK constraint violation, got: ${ex.message}")
    }

    /**
     * 封闭域必须由 CHECK 兑现，而不是只写在列注释里——生成的 API 收任意 String/Long，注释拦不住任何东西，
     * 且 schema 是 ★冻结点，事后补约束要走迁移（R3 第九轮 finding）。
     *
     * **这几张表的 insert 带 EXISTS 守卫（INSERT…SELECT），守卫滤掉的行根本走不到 CHECK**——若父行没建好，
     * 插入只是 0 行、不抛异常，测试就会以「没抛=约束不存在」的相反理由假绿。故下面每例都先备齐合法父行，
     * 只把被测那一列换成非法值。
     */
    private fun assertCheckViolation(what: String, block: () -> Unit) {
        val ex = assertFailsWith<Exception>("$what must violate a CHECK constraint") { block() }
        assertTrue(
            ex.message.orEmpty().contains("CHECK", ignoreCase = true),
            "expected a CHECK constraint violation for $what, got: ${ex.message}",
        )
    }

    @Test
    fun `property rejects an unknown kind and a non-boolean boarding-house flag`() {
        assertCheckViolation("an unrecognised property kind") {
            database.propertyQueries.insert(
                id = uuid.next(), address = "12 Test St", kind = "BOGUS", is_boarding_house = 0,
                created_at = now, updated_at = now,
            )
        }
        assertCheckViolation("a boarding-house flag outside 0/1") {
            database.propertyQueries.insert(
                id = uuid.next(), address = "12 Test St", kind = "RENTAL", is_boarding_house = 2,
                created_at = now, updated_at = now,
            )
        }
    }

    @Test
    fun `template_version rejects an unknown type`() {
        assertCheckViolation("an unrecognised template type") {
            database.templateVersionQueries.insert(
                id = uuid.next(), type = "ROUTIN", version = 1, content_hash = "h",
                created_at = now, updated_at = now,
            )
        }
    }

    @Test
    fun `inspection rejects an unknown type`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        assertCheckViolation("an unrecognised inspection type") {
            database.inspectionQueries.insert(
                id = uuid.next(), type = "BOGUS", property_id = propertyId, tenancy_id = null,
                template_version_id = templateVersionId, scheduled_at = now, previous_inspection_id = null,
                baseline_inspection_id = null, status = "DRAFT", finalized_at = null, data_hash = null,
                created_at = now, updated_at = now,
            )
        }
    }

    @Test
    fun `check_item_def rejects an unknown photo_rule but accepts NULL`() {
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        assertCheckViolation("an unrecognised photo_rule") {
            database.checkItemDefQueries.insert(
                id = uuid.next(), template_version_id = templateVersionId, stable_id = "wall.paint",
                area = "INTERIOR", room = "BEDROOM", text_en = "Walls", text_zh = "墙面",
                allowed_statuses = """["GOOD"]""", photo_rule = "BOGUS", sort = 1,
                created_at = now, updated_at = now,
            )
        }
        // NULL 是合法的「无强制拍照要求」，约束不得把它一并挡掉。
        val affected = database.checkItemDefQueries.insert(
            id = uuid.next(), template_version_id = templateVersionId, stable_id = "wall.paint",
            area = "INTERIOR", room = "BEDROOM", text_en = "Walls", text_zh = "墙面",
            allowed_statuses = """["GOOD"]""", photo_rule = null, sort = 1,
            created_at = now, updated_at = now,
        ).value
        assertEquals(1L, affected, "photo_rule NULL means 'no mandatory photo' and must remain insertable")
    }

    @Test
    fun `inspection_item rejects an unknown wear_or_damage but accepts NULL`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)

        assertCheckViolation("an unrecognised wear_or_damage classification") {
            database.inspectionItemQueries.insert(
                id = uuid.next(), inspection_id = inspectionId, room_instance_id = roomInstanceId,
                stable_id = "wall.paint", status = "GOOD", note = null, wear_or_damage = "BOGUS",
                created_at = now, updated_at = now,
            )
        }
        // NULL = 非 Exit 或与 Ingoing 无差异，必须仍可插入。
        val affected = database.inspectionItemQueries.insert(
            id = uuid.next(), inspection_id = inspectionId, room_instance_id = roomInstanceId,
            stable_id = "wall.trim", status = "GOOD", note = null, wear_or_damage = null,
            created_at = now, updated_at = now,
        ).value
        assertEquals(1L, affected, "wear_or_damage NULL is the normal non-Exit case and must remain insertable")
    }

    @Test
    fun `photo rejects an unknown source and a non-boolean privacy_flag`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)

        assertCheckViolation("an unrecognised photo source") {
            database.photoQueries.insert(
                id = uuid.next(), inspection_item_id = null, room_instance_id = roomInstanceId,
                rel_path = "a.jpg", content_hash = "h", exif_time_ms = null, source = "SCANNER",
                privacy_flag = 0, created_at = now, updated_at = now,
            )
        }
        assertCheckViolation("a privacy_flag outside 0/1") {
            database.photoQueries.insert(
                id = uuid.next(), inspection_item_id = null, room_instance_id = roomInstanceId,
                rel_path = "b.jpg", content_hash = "h", exif_time_ms = null, source = "CAMERA",
                privacy_flag = 2, created_at = now, updated_at = now,
            )
        }
    }

    @Test
    fun `property_item_override rejects a non-boolean suppressed flag`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        assertCheckViolation("a suppressed flag outside 0/1") {
            database.propertyItemOverrideQueries.insert(
                id = uuid.next(), property_id = propertyId, stable_id = "wall.paint", suppressed = 2,
                created_at = now, updated_at = now,
            )
        }
    }

    @Test
    fun `inspection rejects an unknown status value`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val ex = assertFailsWith<Exception>("an unrecognised status must violate the CHECK constraint") {
            database.inspectionQueries.insert(
                id = uuid.next(), type = "ROUTINE", property_id = propertyId, tenancy_id = null,
                template_version_id = templateVersionId, scheduled_at = now, previous_inspection_id = null,
                baseline_inspection_id = null, status = "BOGUS", finalized_at = null, data_hash = null,
                created_at = now, updated_at = now,
            )
        }
        assertTrue(ex.message.orEmpty().contains("CHECK", ignoreCase = true), "expected a CHECK constraint violation, got: ${ex.message}")
    }

    @Test
    fun `tenancy with no Ingoing can designate a Routine inspection as its baseline, and an EXIT resolves it`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val tenancyId = uuid.next()
        // 既有租约：app 装机时租约已在进行中，从未建过 Ingoing 巡检（真实用户情形，见 findings.md #2）。
        database.tenancyQueries.insert(
            id = tenancyId, property_id = propertyId, start_ms = now - 86_400_000L, end_ms = null,
            tenant_name = "J Doe", contact = "j@example.com", baseline_inspection_id = null, created_at = now, updated_at = now,
        )
        val routineInspectionId = DbTestFixtures.insertDraftInspection(
            database, uuid, propertyId, templateVersionId, tenancyId = tenancyId, now = now,
        )

        val before = database.tenancyQueries.selectById(tenancyId).executeAsOne()
        assertNull(before.baseline_inspection_id, "no baseline designated yet")

        database.tenancyQueries.updateBaselineInspection(
            baseline_inspection_id = routineInspectionId, updated_at = now + 1, id = tenancyId,
        )

        val afterDesignation = database.tenancyQueries.selectById(tenancyId).executeAsOne()
        assertEquals(routineInspectionId, afterDesignation.baseline_inspection_id, "Routine inspection must resolve as the tenancy baseline")

        // 真建一个 EXIT 巡检，其 baseline_inspection_id 从 tenancy 指针解析写入（模拟 T2/T3 应用层在建
        // EXIT 时会做的事：读 tenancy.baseline_inspection_id，不假设存在 type=INGOING 的巡检）。
        val exitInspectionId = DbTestFixtures.insertDraftInspection(
            database, uuid, propertyId, templateVersionId, tenancyId = tenancyId, type = "EXIT",
            previousInspectionId = routineInspectionId,
            baselineInspectionId = afterDesignation.baseline_inspection_id,
            now = now + 2,
        )

        val exit = database.inspectionQueries.selectById(exitInspectionId).executeAsOne()
        assertEquals(
            routineInspectionId,
            exit.baseline_inspection_id,
            "EXIT must resolve its baseline via the tenancy pointer, not assume an INGOING inspection exists",
        )
    }

    @Test
    fun `purgeContactInfo clears contact fields, keeps the row, and always records a timestamp`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val tenancyId = uuid.next()
        database.tenancyQueries.insert(
            id = tenancyId, property_id = propertyId, start_ms = now - 86_400_000L, end_ms = now,
            tenant_name = "J Doe", contact = "j@example.com", baseline_inspection_id = null, created_at = now, updated_at = now,
        )

        database.tenancyQueries.purgeContactInfo(purged_at = now + 1, updated_at = now + 1, id = tenancyId)

        val purged = database.tenancyQueries.selectById(tenancyId).executeAsOne()
        assertNull(purged.tenant_name, "tenant_name must be cleared")
        assertNull(purged.contact, "contact must be cleared")
        assertEquals(now + 1, purged.purged_at, "purged_at must be recorded for a real purge call")
    }
}

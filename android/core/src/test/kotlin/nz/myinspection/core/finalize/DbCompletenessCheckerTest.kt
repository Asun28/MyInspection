package nz.myinspection.core.finalize

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import nz.myinspection.core.db.DbTestFixtures
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.Uuid7Generator
import nz.myinspection.core.template.TemplateDomains

/**
 * [DbCompletenessChecker]：finalize 的默认完备性判定（本卡自带的 [CompletenessPort] 实现）。
 * 逐条覆盖"缺状态"、两级拍照规则的"缺照片"、抑制项被跳过、以及全部满足时判完备。
 */
class DbCompletenessCheckerTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: MyInspectionDatabase
    private lateinit var uuid: Uuid7Generator
    private val now = DbTestFixtures.NOW

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

    @Test
    fun `a fully answered inspection with no photo rule is complete`() {
        val ready = FinalizeTestFixtures.buildMinimalCompleteInspection(database, uuid, now)

        val result = DbCompletenessChecker(database).check(ready.inspectionId)

        assertTrue(result.isComplete, "no missing status and no photo rule to violate")
        assertEquals(emptyList(), result.itemsMissingStatus)
        assertEquals(emptyList(), result.itemsMissingMandatoryPhoto)
    }

    @Test
    fun `a template item with no answered inspection_item row is reported as missing status`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        FinalizeTestFixtures.insertCheckItemDef(database, uuid, templateVersionId, stableId = "wall.paint", room = "BEDROOM", sort = 1, now = now)
        FinalizeTestFixtures.insertCheckItemDef(database, uuid, templateVersionId, stableId = "ceiling.paint", room = "BEDROOM", sort = 2, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = "BEDROOM", now = now)
        // 只回答了 wall.paint，ceiling.paint 还没人碰过。
        DbTestFixtures.insertInspectionItem(database, uuid, inspectionId, roomInstanceId, stableId = "wall.paint", now = now)

        val result = DbCompletenessChecker(database).check(inspectionId)

        assertEquals(listOf(MissingItem(roomInstanceId, "ceiling.paint")), result.itemsMissingStatus)
        assertTrue(result.itemsMissingMandatoryPhoto.isEmpty())
    }

    @Test
    fun `ROOM_PANORAMA rule requires at least one room-level photo, item-level photo does not satisfy it`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        FinalizeTestFixtures.insertCheckItemDef(
            database, uuid, templateVersionId, stableId = "room.panorama", room = "BEDROOM",
            photoRule = "ROOM_PANORAMA", sort = 1, now = now,
        )
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = "BEDROOM", now = now)
        val itemId = DbTestFixtures.insertInspectionItem(database, uuid, inspectionId, roomInstanceId, stableId = "room.panorama", now = now)

        val beforePhoto = DbCompletenessChecker(database).check(inspectionId)
        assertEquals(listOf(MissingItem(roomInstanceId, "room.panorama")), beforePhoto.itemsMissingMandatoryPhoto)

        // 挂一张项目级照片（不是房间级）——不满足房间全景要求。
        FinalizeTestFixtures.insertItemPhoto(database, uuid, roomInstanceId, itemId, now = now)
        val stillMissing = DbCompletenessChecker(database).check(inspectionId)
        assertEquals(listOf(MissingItem(roomInstanceId, "room.panorama")), stillMissing.itemsMissingMandatoryPhoto)

        // 补一张房间级照片——满足。
        FinalizeTestFixtures.insertRoomLevelPhoto(database, uuid, roomInstanceId, now = now)
        val afterPhoto = DbCompletenessChecker(database).check(inspectionId)
        assertTrue(afterPhoto.itemsMissingMandatoryPhoto.isEmpty())
    }

    /**
     * ROOM_PANORAMA 是房间级要求，独立于该房间下具体哪一项是否已作答——同一次 `check()` 必须
     * 同时报出"缺状态"与"缺房间照片"，不能让用户先补完状态、重跑一次 finalize 才发现还缺照片
     * （T1-TEMPLATE-ENGINE 修过的同一类"校验器提前 return"缺陷，见 CompletenessPort 顶部说明）。
     */
    @Test
    fun `ROOM_PANORAMA deficiency is reported in the same pass as a missing-status item, not gated behind it`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        FinalizeTestFixtures.insertCheckItemDef(
            database, uuid, templateVersionId, stableId = "room.panorama", room = "BEDROOM",
            photoRule = "ROOM_PANORAMA", sort = 1, now = now,
        )
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = "BEDROOM", now = now)
        // 房间已建，room.panorama 既没人作答，也没有房间级照片。

        val result = DbCompletenessChecker(database).check(inspectionId)

        assertEquals(listOf(MissingItem(roomInstanceId, "room.panorama")), result.itemsMissingStatus)
        assertEquals(listOf(MissingItem(roomInstanceId, "room.panorama")), result.itemsMissingMandatoryPhoto)
    }

    /**
     * ADVERSE_ONLY 的"不利发现"分类表——这两个集合按定义须与 [DbCompletenessChecker] 私有的
     * `RENTAL_ADVERSE`/`ANNUAL_ADVERSE` 一致，但**不从生产代码读取**（那样自己抄自己，测不出实现改错）。
     * 结构安全网见下面两条"partition exactly covers"测试：域外的幻造标签（比如误写 "N_A"）进不了这两个
     * 集合——它们必须是 [TemplateDomains.RENTAL_STATUSES]/[TemplateDomains.ANNUAL_STATUSES] 的子集且
     * 与各自的非不利子集**并集恰好等于冻结域**，域将来新增一个标签，这条并集断言会先红，逼着这里更新。
     */
    private val RENTAL_ADVERSE_LABELS = setOf("FAIR", "POOR")
    private val RENTAL_NON_ADVERSE_LABELS = setOf("GOOD", "NOT_APPLICABLE")
    private val ANNUAL_ADVERSE_LABELS = setOf("MONITOR", "MAINTENANCE_ITEM", "SIGNIFICANT_DEFECT")
    private val ANNUAL_NON_ADVERSE_LABELS = setOf("NO_ISSUE", "NOT_APPLICABLE")

    @Test
    fun `the adverse-vs-not partition exactly covers the frozen RENTAL_STATUSES domain`() {
        assertEquals(
            TemplateDomains.RENTAL_STATUSES,
            RENTAL_ADVERSE_LABELS + RENTAL_NON_ADVERSE_LABELS,
            "every label in the frozen rental domain must be classified as adverse or not — " +
                "a domain change (add/rename/remove a status) must update this partition",
        )
        assertTrue((RENTAL_ADVERSE_LABELS intersect RENTAL_NON_ADVERSE_LABELS).isEmpty())
    }

    @Test
    fun `the adverse-vs-not partition exactly covers the frozen ANNUAL_STATUSES domain`() {
        assertEquals(
            TemplateDomains.ANNUAL_STATUSES,
            ANNUAL_ADVERSE_LABELS + ANNUAL_NON_ADVERSE_LABELS,
            "every label in the frozen annual domain must be classified as adverse or not — " +
                "a domain change (add/rename/remove a status) must update this partition",
        )
        assertTrue((ANNUAL_ADVERSE_LABELS intersect ANNUAL_NON_ADVERSE_LABELS).isEmpty())
    }

    /**
     * 按上面的分类表，逐一核对 [DbCompletenessChecker] 对 [TemplateDomains.RENTAL_STATUSES]/
     * [TemplateDomains.ANNUAL_STATUSES] **每一个**标签的实际判定——status 直接来自冻结域的迭代，
     * 不是手抄的字符串字面量，故不存在"域外幻造标签"这类拼写漂移的空间。
     */
    @Test
    fun `ADVERSE_ONLY classification matches the partition for every status in both frozen domains`() {
        var caseIndex = 0
        fun assertCase(inspectionType: String, status: String, adverse: Boolean) {
            val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
            val templateVersionId = DbTestFixtures.insertTemplateVersion(
                database, uuid, type = inspectionType, version = (++caseIndex).toLong(), now = now,
            )
            val stableId = "item.$caseIndex"
            FinalizeTestFixtures.insertCheckItemDef(
                database, uuid, templateVersionId, stableId = stableId, room = "BEDROOM",
                photoRule = "ADVERSE_ONLY", sort = 1, now = now,
            )
            val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, type = inspectionType, now = now)
            val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = "BEDROOM", now = now)
            DbTestFixtures.insertInspectionItem(database, uuid, inspectionId, roomInstanceId, stableId = stableId, status = status, now = now)

            val result = DbCompletenessChecker(database).check(inspectionId)
            val expected = if (adverse) listOf(MissingItem(roomInstanceId, stableId)) else emptyList()
            assertEquals(expected, result.itemsMissingMandatoryPhoto, "inspectionType=$inspectionType status=$status")
        }

        for (status in TemplateDomains.RENTAL_STATUSES) {
            assertCase("ROUTINE", status, adverse = status in RENTAL_ADVERSE_LABELS)
        }
        for (status in TemplateDomains.ANNUAL_STATUSES) {
            assertCase("ANNUAL", status, adverse = status in ANNUAL_ADVERSE_LABELS)
        }
    }

    @Test
    fun `ADVERSE_ONLY rule is satisfied once an evidence photo is attached to the flagged item`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, type = "EXIT", now = now)
        FinalizeTestFixtures.insertCheckItemDef(
            database, uuid, templateVersionId, stableId = "wall.paint", room = "BEDROOM",
            photoRule = "ADVERSE_ONLY", sort = 1, now = now,
        )
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, type = "EXIT", now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = "BEDROOM", now = now)
        val itemId = DbTestFixtures.insertInspectionItem(database, uuid, inspectionId, roomInstanceId, stableId = "wall.paint", status = "POOR", now = now)

        val missing = DbCompletenessChecker(database).check(inspectionId)
        assertEquals(listOf(MissingItem(roomInstanceId, "wall.paint")), missing.itemsMissingMandatoryPhoto)

        FinalizeTestFixtures.insertItemPhoto(database, uuid, roomInstanceId, itemId, now = now)
        val satisfied = DbCompletenessChecker(database).check(inspectionId)
        assertTrue(satisfied.itemsMissingMandatoryPhoto.isEmpty())
    }

    @Test
    fun `a suppressed property item is excluded from completeness entirely`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        FinalizeTestFixtures.insertCheckItemDef(database, uuid, templateVersionId, stableId = "garage.door", room = "GARAGE", sort = 1, now = now)
        FinalizeTestFixtures.insertPropertyItemOverride(database, uuid, propertyId, stableId = "garage.door", suppressed = true, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        // 该物业没有车库——房间实例干脆不建，或即便建了也不该出现在缺状态清单里；这里两种都测最直接的一种：
        // 房间实例存在但该 stable_id 被抑制。
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = "GARAGE", now = now)

        val result = DbCompletenessChecker(database).check(inspectionId)

        assertTrue(result.isComplete, "suppressed stable_id must not surface as a missing item")
        assertEquals(emptyList(), result.itemsMissingStatus)
    }

    /**
     * `room_instance.selectByInspection`（冻结物，不可改）没有 `ORDER BY`。这里用显式 id（绕开
     * [Uuid7Generator]）把"插入顺序"与"id 顺序"故意错开——先插入 id 更大的房间，再插入 id 更小的
     * 房间，这样若实现没有显式排序，输出会先出插入序第一的那间（id 更大），与断言的 id 升序相反，
     * 才是能造出反例的差异测试（不是插入序恰好等于 id 序的巧合钉子）。
     */
    @Test
    fun `missing-item lists across multiple rooms are ordered by room_instance id, not insertion order`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        FinalizeTestFixtures.insertCheckItemDef(database, uuid, templateVersionId, stableId = "wall.paint", room = "BEDROOM", sort = 1, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val largeId = "zzzzzzzz-0000-7000-8000-000000000001"
        val smallId = "00000000-0000-7000-8000-000000000002"
        // 先插入 id 更大的房间，再插入 id 更小的房间——插入序与 id 序相反。
        database.roomInstanceQueries.insert(
            id = largeId, inspection_id = inspectionId, room_key = "BEDROOM", instance_no = 1,
            display_label = "Bedroom 1", created_at = now, updated_at = now,
        )
        database.roomInstanceQueries.insert(
            id = smallId, inspection_id = inspectionId, room_key = "BEDROOM", instance_no = 2,
            display_label = "Bedroom 2", created_at = now, updated_at = now,
        )
        // 两间房都没人回答 wall.paint。

        val result = DbCompletenessChecker(database).check(inspectionId)

        assertEquals(
            listOf(MissingItem(smallId, "wall.paint"), MissingItem(largeId, "wall.paint")),
            result.itemsMissingStatus,
            "list order must follow room_instance.id ascending, not insertion order",
        )
    }
}

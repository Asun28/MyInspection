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
     * 两个 `room_key` 相同的房间实例（`instance_no` 分别 1/2——唯一索引是 `(inspection_id, room_key,
     * instance_no)`，不是单纯 `room_key`，故这两行今天就能合法共存），只有其中一个缺全景照片：
     * 判定必须按房间**实例**身份区分，只报那一个缺的、不能因为共享 room_key 就把另一个已合规的实例也
     * 误报成缺——`computeMissingPhotos` 的 `MissingRoomPhoto` 本就带 `roomInstanceId`，按它匹配而不是
     * 降级成 `roomKey` 匹配，才不会丢弃 capture 已经算好的实例级精度。
     */
    @Test
    fun `two room instances sharing a room_key are judged independently for the panorama requirement`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        FinalizeTestFixtures.insertCheckItemDef(
            database, uuid, templateVersionId, stableId = "room.panorama", room = "BEDROOM",
            photoRule = "ROOM_PANORAMA", sort = 1, now = now,
        )
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomA = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = "BEDROOM", instanceNo = 1, now = now)
        val roomB = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = "BEDROOM", instanceNo = 2, now = now)
        DbTestFixtures.insertInspectionItem(database, uuid, inspectionId, roomA, stableId = "room.panorama", now = now)
        DbTestFixtures.insertInspectionItem(database, uuid, inspectionId, roomB, stableId = "room.panorama", now = now)
        // 只给 roomA 补房间级照片；roomB 仍缺。
        FinalizeTestFixtures.insertRoomLevelPhoto(database, uuid, roomA, now = now)

        val result = DbCompletenessChecker(database).check(inspectionId)

        assertEquals(
            listOf(MissingItem(roomB, "room.panorama")),
            result.itemsMissingMandatoryPhoto,
            "only the instance actually missing its panorama photo should be reported, not both instances of the shared room_key",
        )
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
                photoRule = "ADVERSE_ONLY", sort = 1, type = inspectionType, now = now,
            )
            val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, type = inspectionType, now = now)
            val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = "BEDROOM", now = now)
            DbTestFixtures.insertInspectionItem(database, uuid, inspectionId, roomInstanceId, stableId = stableId, status = status, now = now)

            val result = DbCompletenessChecker(database).check(inspectionId)
            val expected = if (adverse) listOf(MissingItem(roomInstanceId, stableId)) else emptyList()
            assertEquals(expected, result.itemsMissingMandatoryPhoto, "inspectionType=$inspectionType status=$status")
            assertTrue(result.itemsWithInvalidStatus.isEmpty(), "every legal domain status must classify cleanly, not as invalid: $status")
        }

        for (status in TemplateDomains.RENTAL_STATUSES) {
            assertCase("ROUTINE", status, adverse = status in RENTAL_ADVERSE_LABELS)
        }
        for (status in TemplateDomains.ANNUAL_STATUSES) {
            assertCase("ANNUAL", status, adverse = status in ANNUAL_ADVERSE_LABELS)
        }
    }

    /**
     * fail-closed 的另一半：一个不在冻结域内的状态字符串（`Supplement.sq` 式的直连 SQL 腐坏模拟，
     * 卡文 dod_assert 已明确认可这条路径——`inspection_item.status` 本身无 CHECK，`updateStatusIfDraft`
     * 也不校验域，故这类腐坏在当前 schema 下并非无法触达，只是不该被这层分类器悄悄当"非不利"放行）。
     * 分类不出来必须显式报告，而不是让 ADVERSE_ONLY 的分类退化成"能匹配上不利集合才算数、匹配不上就
     * 放行"——那正是 fail-open。
     */
    @Test
    fun `an ADVERSE_ONLY item with a status outside the frozen domain is reported as invalid, blocking finalize`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, type = "ROUTINE", now = now)
        FinalizeTestFixtures.insertCheckItemDef(
            database, uuid, templateVersionId, stableId = "wall.paint", room = "BEDROOM",
            photoRule = "ADVERSE_ONLY", sort = 1, now = now,
        )
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, type = "ROUTINE", now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = "BEDROOM", now = now)
        val itemId = DbTestFixtures.insertInspectionItem(database, uuid, inspectionId, roomInstanceId, stableId = "wall.paint", status = "GOOD", now = now)
        // 绕过谓词直连 SQL，把状态改成一个不在 RENTAL_STATUSES 域内的字符串——updateStatusIfDraft 本身
        // 不校验域，故这条腐坏路径在当前 schema 下是可触达的，不是纯假设场景。
        driver.execute(null, "UPDATE inspection_item SET status = 'BOGUS_STATUS' WHERE id = '$itemId'", 0)

        val result = DbCompletenessChecker(database).check(inspectionId)

        assertEquals(listOf(MissingItem(roomInstanceId, "wall.paint")), result.itemsWithInvalidStatus)
        assertTrue(result.itemsMissingMandatoryPhoto.isEmpty(), "an unclassifiable status is reported once, not doubled into the photo list too")
        assertTrue(!result.isComplete, "an invalid status must block finalize")
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

    /**
     * 需求 §5「不利发现强制备注」——权威在 `core/capture` 的 `computeMissingNotes`，本类只是委派。
     * POOR（不利发现）+ 已补拍强制照片，但备注仍空白：必须仍被拒，且拒的理由是"缺备注"而不是被误判成
     * "缺照片"（照片那关已经过了）。补上备注（哪怕是短语库那种一点即得的短句）后完备。
     */
    @Test
    fun `an adverse item with the required photo but a blank note is rejected for the missing note, not the photo`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, type = "EXIT", now = now)
        FinalizeTestFixtures.insertCheckItemDef(
            database, uuid, templateVersionId, stableId = "wall.paint", room = "BEDROOM",
            photoRule = "ADVERSE_ONLY", sort = 1, now = now,
        )
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, type = "EXIT", now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = "BEDROOM", now = now)
        val itemId = DbTestFixtures.insertInspectionItem(database, uuid, inspectionId, roomInstanceId, stableId = "wall.paint", status = "POOR", now = now)
        FinalizeTestFixtures.insertItemPhoto(database, uuid, roomInstanceId, itemId, now = now)

        val withBlankNote = DbCompletenessChecker(database).check(inspectionId)
        assertEquals(listOf(MissingItem(roomInstanceId, "wall.paint")), withBlankNote.itemsMissingNote)
        assertTrue(withBlankNote.itemsMissingMandatoryPhoto.isEmpty(), "the photo requirement is already satisfied; only the note is missing")
        assertTrue(!withBlankNote.isComplete)

        database.inspectionItemQueries.updateStatusIfDraft(status = "POOR", note = "scuffed corner, needs repainting", updated_at = now + 1, id = itemId)
        val withNote = DbCompletenessChecker(database).check(inspectionId)
        assertTrue(withNote.itemsMissingNote.isEmpty())
        assertTrue(withNote.isComplete)
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
     * 反例：一条 `suppressed=0` 的 override 行——不是"没有 override"，是"曾经/仍然有一条 override 记录，
     * 只是当前值是恢复态"（`setSuppressed` 的恢复路径正是置 0，不是软删这一行）。只判"是否存在 override
     * 行"而不看它的 `suppressed` 值，会把这种恢复态误当成仍在抑制——该项其实必须照常出现在缺状态清单里。
     */
    @Test
    fun `a property_item_override with suppressed=0 does not exclude the item`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        FinalizeTestFixtures.insertCheckItemDef(database, uuid, templateVersionId, stableId = "garage.door", room = "GARAGE", sort = 1, now = now)
        FinalizeTestFixtures.insertPropertyItemOverride(database, uuid, propertyId, stableId = "garage.door", suppressed = false, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = "GARAGE", now = now)

        val result = DbCompletenessChecker(database).check(inspectionId)

        assertEquals(listOf(MissingItem(roomInstanceId, "garage.door")), result.itemsMissingStatus, "a restored (unsuppressed) override must not hide the item")
    }

    /**
     * 独立对照模板算出"应有哪些房间"，不从 `room_instance` 现有的行反推——两间房的模板里，一间从未被
     * 实例化（模拟建巡检时的房间实例化环节漏掉了它），必须点名报出，而不是被"现状即基准"的循环判定
     * 悄悄放过。
     */
    @Test
    fun `a template room that was never instantiated is reported as missing, by name`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        FinalizeTestFixtures.insertCheckItemDef(database, uuid, templateVersionId, stableId = "wall.paint", room = "BEDROOM", sort = 1, now = now)
        FinalizeTestFixtures.insertCheckItemDef(database, uuid, templateVersionId, stableId = "bench.top", room = "KITCHEN", sort = 2, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        // 只建了 BEDROOM；KITCHEN 一间房实例都没有，即便它在模板里有一个未被抑制的项。
        DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = "BEDROOM", now = now)

        val result = DbCompletenessChecker(database).check(inspectionId)

        assertEquals(listOf("KITCHEN"), result.roomsMissingInstance)
        assertTrue(!result.isComplete, "a missing room must make the inspection incomplete")
    }

    @Test
    fun `an inspection with every template room instantiated has no missing rooms`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        FinalizeTestFixtures.insertCheckItemDef(database, uuid, templateVersionId, stableId = "wall.paint", room = "BEDROOM", sort = 1, now = now)
        FinalizeTestFixtures.insertCheckItemDef(database, uuid, templateVersionId, stableId = "bench.top", room = "KITCHEN", sort = 2, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = "BEDROOM", now = now)
        DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = "KITCHEN", instanceNo = 1, now = now)

        val result = DbCompletenessChecker(database).check(inspectionId)

        assertEquals(emptyList(), result.roomsMissingInstance)
    }

    /**
     * 房间下**每一项**都被本物业抑制时（"这个物业没有车库"），该房间不建实例是正常态，不该被报成
     * "缺房间"——这与上面「独立对照模板」的原则并不矛盾：抑制过滤先于房间推导发生，被抑制的项根本
     * 不参与"这间房该不该存在"的判断。
     */
    @Test
    fun `a room whose every item is suppressed and has no instance is not reported as missing`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        FinalizeTestFixtures.insertCheckItemDef(database, uuid, templateVersionId, stableId = "garage.door", room = "GARAGE", sort = 1, now = now)
        FinalizeTestFixtures.insertPropertyItemOverride(database, uuid, propertyId, stableId = "garage.door", suppressed = true, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        // GARAGE 一个 room_instance 都没建——该物业没有车库，这是预期状态，不是遗漏。

        val result = DbCompletenessChecker(database).check(inspectionId)

        assertEquals(emptyList(), result.roomsMissingInstance)
        assertTrue(result.isComplete)
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

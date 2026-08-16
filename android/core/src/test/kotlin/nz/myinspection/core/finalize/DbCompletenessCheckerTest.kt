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

/**
 * [DbCompletenessChecker]：finalize 的默认完备性判定（本卡自带的 [CompletenessPort] 实现，
 * 集成缝见 TD9）。逐条覆盖"缺状态"、两级拍照规则的"缺照片"、抑制项被跳过、以及全部满足时判完备。
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

    @Test
    fun `ADVERSE_ONLY rule only requires a photo when the answered status is adverse for the inspection type`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, type = "ROUTINE", now = now)
        FinalizeTestFixtures.insertCheckItemDef(
            database, uuid, templateVersionId, stableId = "wall.paint", room = "BEDROOM",
            photoRule = "ADVERSE_ONLY", sort = 1, now = now,
        )
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, type = "ROUTINE", now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = "BEDROOM", now = now)
        DbTestFixtures.insertInspectionItem(database, uuid, inspectionId, roomInstanceId, stableId = "wall.paint", status = "GOOD", now = now)

        val goodResult = DbCompletenessChecker(database).check(inspectionId)
        assertTrue(goodResult.itemsMissingMandatoryPhoto.isEmpty(), "GOOD is not adverse for a rental template; no photo required")
    }

    @Test
    fun `ADVERSE_ONLY rule rejects a POOR rental item with no evidence photo`() {
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
    fun `ADVERSE_ONLY rule uses the annual three-tier defect set, not the rental four grades`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, type = "ANNUAL", now = now)
        FinalizeTestFixtures.insertCheckItemDef(
            database, uuid, templateVersionId, stableId = "smoke.alarm", room = "HALLWAY",
            photoRule = "ADVERSE_ONLY", sort = 1, now = now,
        )
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, type = "ANNUAL", now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = "HALLWAY", now = now)
        // "POOR" isn't a valid annual-tier label; annual adverse = MONITOR/MAINTENANCE_ITEM/SIGNIFICANT_DEFECT.
        DbTestFixtures.insertInspectionItem(database, uuid, inspectionId, roomInstanceId, stableId = "smoke.alarm", status = "SIGNIFICANT_DEFECT", now = now)

        val missing = DbCompletenessChecker(database).check(inspectionId)
        assertEquals(listOf(MissingItem(roomInstanceId, "smoke.alarm")), missing.itemsMissingMandatoryPhoto)
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
}

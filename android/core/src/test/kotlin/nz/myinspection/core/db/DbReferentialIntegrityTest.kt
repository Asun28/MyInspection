package nz.myinspection.core.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 逻辑外键没有物理 REFERENCES 约束兜底，插入侧的 finalize 守卫必须自己证明父行**真的存在且属于同一条
 * 链路**——不能只测「父行是 DRAFT」这一面。一个标量子查询 `(SELECT … WHERE id = :x) IS NULL` 在
 * `:x` 指向不存在的行时也会返回 NULL，而 `NULL IS NULL` 为真，guard 反而放行：这就是孤儿行的口子。
 * 这里逐条验证：父行缺失、或父行存在但不属于同一条巡检/房间链路时，插入必须是 0 行受影响（而不是
 * 抛异常——这些查询本来就是「守卫失败=0 行」的设计，混进无关的巡检数据不该触发存储层错误，只是不落地）。
 */
class DbReferentialIntegrityTest {
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
    fun `room_instance insert against a nonexistent inspection affects zero rows`() {
        val affected = database.roomInstanceQueries.insert(
            id = uuid.next(), inspection_id = uuid.next() /* never inserted */, room_key = "BEDROOM",
            instance_no = 1, display_label = "Bedroom 1", created_at = now, updated_at = now,
        ).value
        assertEquals(0L, affected, "a room_instance can never attach to an inspection id that does not exist")
    }

    @Test
    fun `inspection_item insert against a nonexistent inspection affects zero rows`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)

        val affected = database.inspectionItemQueries.insert(
            id = uuid.next(), inspection_id = uuid.next() /* never inserted */, room_instance_id = roomInstanceId,
            stable_id = "wall.paint", status = "GOOD", note = null, wear_or_damage = null, created_at = now, updated_at = now,
        ).value
        assertEquals(0L, affected, "an inspection_item can never attach to an inspection id that does not exist")
    }

    @Test
    fun `inspection_item insert against a room_instance owned by a different inspection affects zero rows`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionA = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val inspectionB = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomInstanceOfB = DbTestFixtures.insertRoomInstance(database, uuid, inspectionB, now = now)

        // room_instance genuinely exists and is active, but belongs to inspection B, not A.
        val affected = database.inspectionItemQueries.insert(
            id = uuid.next(), inspection_id = inspectionA, room_instance_id = roomInstanceOfB,
            stable_id = "wall.paint", status = "GOOD", note = null, wear_or_damage = null, created_at = now, updated_at = now,
        ).value
        assertEquals(0L, affected, "an inspection_item must not be able to borrow a room_instance from a different inspection")
    }

    @Test
    fun `photo insert against a nonexistent room_instance affects zero rows`() {
        val affected = database.photoQueries.insert(
            id = uuid.next(), inspection_item_id = null, room_instance_id = uuid.next() /* never inserted */,
            rel_path = "a.jpg", content_hash = "hash1", exif_time_ms = null, source = "CAMERA",
            privacy_flag = 0, created_at = now, updated_at = now,
        ).value
        assertEquals(0L, affected, "a photo can never attach to a room_instance id that does not exist")
    }

    @Test
    fun `photo insert against an inspection_item owned by a different room_instance affects zero rows`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomA = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = "BEDROOM", instanceNo = 1, now = now)
        val roomB = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = "BEDROOM", instanceNo = 2, now = now)
        val itemInRoomA = DbTestFixtures.insertInspectionItem(database, uuid, inspectionId, roomA, now = now)

        // inspection_item genuinely exists and is active, but belongs to room A, not room B.
        val affected = database.photoQueries.insert(
            id = uuid.next(), inspection_item_id = itemInRoomA, room_instance_id = roomB,
            rel_path = "a.jpg", content_hash = "hash1", exif_time_ms = null, source = "CAMERA",
            privacy_flag = 0, created_at = now, updated_at = now,
        ).value
        assertEquals(0L, affected, "a photo must not be able to claim an inspection_item that belongs to a different room_instance")
    }

    @Test
    fun `audio insert against a nonexistent inspection_item affects zero rows`() {
        val affected = database.audioQueries.insert(
            id = uuid.next(), inspection_item_id = uuid.next() /* never inserted */, rel_path = "a.m4a",
            content_hash = "hash1", created_at = now, updated_at = now,
        ).value
        assertEquals(0L, affected, "audio can never attach to an inspection_item id that does not exist")
    }
}

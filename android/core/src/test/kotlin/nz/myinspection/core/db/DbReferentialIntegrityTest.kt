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

    /**
     * 四张挂在巡检下的子表都用 `INSERT…SELECT…WHERE EXISTS` 守卫。**下面所有「affects zero rows」用例，
     * 一个恒返回 0 行的坏守卫全都能过**——正路径必须单独钉住，否则整组拒绝断言证明不了任何东西（L165）。
     * 一次覆四张表：合法 DRAFT 链路下各插一行，断言影响 1 行**且真能读回来**（只断言 1 行不足以排除
     * 「写进去但落到别的行/别的链路」）。
     */
    @Test
    fun `all four guarded child inserts succeed and are retrievable on a draft inspection`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)

        val roomInstanceId = uuid.next()
        assertEquals(
            1L,
            database.roomInstanceQueries.insert(
                id = roomInstanceId, inspection_id = inspectionId, room_key = "BEDROOM", instance_no = 1,
                display_label = "Bedroom 1", created_at = now, updated_at = now,
            ).value,
            "a room_instance under a DRAFT inspection must actually insert",
        )

        val itemId = uuid.next()
        assertEquals(
            1L,
            database.inspectionItemQueries.insert(
                id = itemId, inspection_id = inspectionId, room_instance_id = roomInstanceId,
                stable_id = "wall.paint", status = "GOOD", note = null, wear_or_damage = null,
                created_at = now, updated_at = now,
            ).value,
            "an inspection_item under a DRAFT inspection must actually insert",
        )

        val photoId = uuid.next()
        assertEquals(
            1L,
            database.photoQueries.insert(
                id = photoId, inspection_item_id = itemId, room_instance_id = roomInstanceId,
                rel_path = "photos/a.jpg", content_hash = "h", exif_time_ms = null, source = "CAMERA",
                privacy_flag = 0, created_at = now, updated_at = now,
            ).value,
            "a photo under a DRAFT inspection must actually insert",
        )

        val audioId = uuid.next()
        assertEquals(
            1L,
            database.audioQueries.insert(
                id = audioId, inspection_item_id = itemId, rel_path = "audio/a.m4a",
                content_hash = "h", created_at = now, updated_at = now,
            ).value,
            "audio under a DRAFT inspection must actually insert",
        )

        // 读回来，确认落的是这条链路而不是别处。
        assertEquals(itemId, database.inspectionItemQueries.selectById(itemId).executeAsOne().id)
        assertEquals(roomInstanceId, database.photoQueries.selectById(photoId).executeAsOne().room_instance_id)
        assertEquals(itemId, database.audioQueries.selectById(audioId).executeAsOne().inspection_item_id)
    }

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

    private fun insertCheckItemDef(templateVersionId: String, stableId: String): Long =
        database.checkItemDefQueries.insert(
            id = uuid.next(), template_version_id = templateVersionId, stable_id = stableId,
            area = "INTERIOR", room = "BEDROOM", text_en = "Walls", text_zh = "墙面",
            allowed_statuses = """["GOOD","FAIR","POOR"]""", photo_rule = null, sort = 1,
            created_at = now, updated_at = now,
        ).value

    /**
     * 模板版本「被任何巡检引用后不可变」——**光「不提供 update 查询」拦不住**：check_item_def 是
     * template_version 的子行，往已被引用的版本里 insert 一条新 stable_id，等于在 version 与
     * content_hash 都不变的前提下改掉了那一版的实际内容，破坏「历史对齐只靠 stable_id + 模板版本」
     * 这条关键不变量（多年后按当时模板重渲报告会失真）。故 insert 自带守卫。
     *
     * 三例必须成组读：前两例证明守卫拦得住，第三例证明它**不是把一切都拦住**——少了第三例，一个恒
     * 返回 0 行的坏守卫也能让前两例全绿（L165：断言面要恰好等于被测契约）。
     */
    @Test
    fun `check_item_def insert against a nonexistent template_version affects zero rows`() {
        val affected = insertCheckItemDef(uuid.next() /* never inserted */, "wall.paint")
        assertEquals(0L, affected, "a check_item_def can never attach to a template_version id that does not exist")
    }

    @Test
    fun `check_item_def insert into a template_version already referenced by an inspection affects zero rows`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        // 版本先被一次巡检引用——此刻起该版本的内容就是历史事实，不能再变。
        DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)

        val affected = insertCheckItemDef(templateVersionId, "wall.paint")
        assertEquals(
            0L, affected,
            "once an inspection references a template_version, adding an item would change that version's contents " +
                "without changing its version number or content_hash",
        )
    }

    @Test
    fun `check_item_def insert into an unreferenced template_version affects one row`() {
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)

        val affected = insertCheckItemDef(templateVersionId, "wall.paint")
        assertEquals(1L, affected, "loading a template must still work before any inspection references that version")
    }
}

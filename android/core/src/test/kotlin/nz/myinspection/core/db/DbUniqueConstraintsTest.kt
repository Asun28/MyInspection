package nz.myinspection.core.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 软删除唯一性一律用**部分唯一索引**（`CREATE UNIQUE INDEX … WHERE deleted_at IS NULL`），不用表级
 * `UNIQUE(业务键, deleted_at)`：SQLite 的 `UNIQUE` 把 `NULL` 视为互不相等，`deleted_at` 恒为 `NULL` 的
 * 活跃行之间表级约束形同虚设、根本拦不住重复（R3 评审指出，本卡最初六张表全部踩了这个坑，已改正）。
 *
 * 这里对每一条部分唯一索引都验证：插入两行拥有相同业务键的**活跃**记录，第二行必须被数据库真实拒绝
 * （不是"看起来对"——SQLite 抛出的约束异常里带 `UNIQUE constraint failed`，逐条核对这句话，防止某条
 * 用例其实是撞了别的错误、而非真的验证了目标约束，L165：断言面必须恰好等于被测契约）。
 */
class DbUniqueConstraintsTest {
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

    private fun assertUniqueViolation(block: () -> Unit) {
        val ex = assertFailsWith<Exception>("expected a UNIQUE constraint violation") { block() }
        assertTrue(
            ex.message.orEmpty().contains("UNIQUE", ignoreCase = true),
            "expected a UNIQUE constraint violation, got: ${ex.message}",
        )
    }

    @Test
    fun `template_version rejects a second active row for the same type+version`() {
        database.templateVersionQueries.insert(id = uuid.next(), type = "ROUTINE", version = 1, content_hash = "h1", updated_at = now)
        assertUniqueViolation {
            database.templateVersionQueries.insert(id = uuid.next(), type = "ROUTINE", version = 1, content_hash = "h2", updated_at = now)
        }
    }

    @Test
    fun `check_item_def rejects a second active row for the same template_version+stable_id`() {
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        database.checkItemDefQueries.insert(
            id = uuid.next(), template_version_id = templateVersionId, stable_id = "wall.paint",
            area = "Bedroom", room = "BEDROOM", text_en = "Wall paint", text_zh = "墙面油漆",
            allowed_statuses = "[\"GOOD\",\"FAIR\",\"POOR\",\"NOT_APPLICABLE\"]", updated_at = now,
        )
        assertUniqueViolation {
            database.checkItemDefQueries.insert(
                id = uuid.next(), template_version_id = templateVersionId, stable_id = "wall.paint",
                area = "Bedroom", room = "BEDROOM", text_en = "dup", text_zh = "dup",
                allowed_statuses = "[]", updated_at = now,
            )
        }
    }

    @Test
    fun `room_instance rejects a second active row for the same inspection+room_key+instance_no`() {
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, templateVersionId, now = now)
        database.roomInstanceQueries.insert(
            id = uuid.next(), inspection_id = inspectionId, room_key = "BEDROOM", instance_no = 1,
            display_label = "Bedroom 1", updated_at = now,
        )
        assertUniqueViolation {
            database.roomInstanceQueries.insert(
                id = uuid.next(), inspection_id = inspectionId, room_key = "BEDROOM", instance_no = 1,
                display_label = "dup", updated_at = now,
            )
        }
    }

    @Test
    fun `inspection_item rejects a second active row for the same inspection+room_instance+stable_id`() {
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, templateVersionId, now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)
        DbTestFixtures.insertInspectionItem(database, uuid, inspectionId, roomInstanceId, now = now)
        assertUniqueViolation {
            database.inspectionItemQueries.insert(
                id = uuid.next(), inspection_id = inspectionId, room_instance_id = roomInstanceId,
                stable_id = "wall.paint", status = "POOR", note = null, wear_or_damage = null, updated_at = now,
            )
        }
    }

    @Test
    fun `photo rejects a second active row for the same room_instance+content_hash`() {
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, templateVersionId, now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)
        database.photoQueries.insert(
            id = uuid.next(), inspection_item_id = null, room_instance_id = roomInstanceId,
            rel_path = "a.jpg", content_hash = "hash1", exif_time_ms = null, source = "CAMERA",
            privacy_flag = 0, updated_at = now,
        )
        assertUniqueViolation {
            database.photoQueries.insert(
                id = uuid.next(), inspection_item_id = null, room_instance_id = roomInstanceId,
                rel_path = "b.jpg", content_hash = "hash1", exif_time_ms = null, source = "CAMERA",
                privacy_flag = 0, updated_at = now,
            )
        }
    }

    @Test
    fun `property_item_override rejects a second active row for the same property+stable_id`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        database.propertyItemOverrideQueries.insert(id = uuid.next(), property_id = propertyId, stable_id = "wall.paint", suppressed = 1, updated_at = now)
        assertUniqueViolation {
            database.propertyItemOverrideQueries.insert(id = uuid.next(), property_id = propertyId, stable_id = "wall.paint", suppressed = 1, updated_at = now)
        }
    }
}

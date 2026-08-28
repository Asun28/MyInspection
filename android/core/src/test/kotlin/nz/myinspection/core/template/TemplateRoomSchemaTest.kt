package nz.myinspection.core.template

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import nz.myinspection.core.db.MyInspectionDatabase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Schema v2 的迁移、闭集形态、布尔约束与活跃业务键唯一性。 */
class TemplateRoomSchemaTest {
    private lateinit var driver: JdbcSqliteDriver

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `schema v2 exposes the exact room definition columns`() {
        MyInspectionDatabase.Schema.create(driver)

        assertEquals(2L, MyInspectionDatabase.Schema.version)
        assertEquals(
            listOf("id", "template_version_id", "room_key", "repeatable", "sort", "created_at", "updated_at", "deleted_at"),
            columnNames("template_room_def"),
        )
    }

    @Test
    fun `repeatable accepts only integer zero and one`() {
        MyInspectionDatabase.Schema.create(driver)
        insertVersion("v1", "ROUTINE", 1)

        insertRoom("r0", "v1", "KITCHEN", 0)
        insertRoom("r1", "v1", "BEDROOM", 1)
        assertFailsWith<Exception> { insertRoom("r2", "v1", "LOUNGE", 2) }
        assertFailsWith<Exception> { insertRoom("rn", "v1", "GARAGE", -1) }
    }

    @Test
    fun `active room keys are unique per template version only`() {
        MyInspectionDatabase.Schema.create(driver)
        insertVersion("v1", "ROUTINE", 1)
        insertVersion("v2", "ROUTINE", 2)
        insertRoom("r1", "v1", "KITCHEN", 0)

        assertFailsWith<Exception> { insertRoom("r2", "v1", "KITCHEN", 1) }
        insertRoom("r3", "v2", "KITCHEN", 1)
        assertEquals(2L, scalarLong("SELECT COUNT(*) FROM template_room_def WHERE room_key = 'KITCHEN'"))
    }

    @Test
    fun `migration preserves every existing check item column and value`() {
        createV1CheckItemTable()
        val columnsBefore = columnNames("check_item_def")
        val rowsBefore = checkItemProjection()

        MyInspectionDatabase.Schema.migrate(driver, 1, 2)

        assertEquals(columnsBefore, columnNames("check_item_def"))
        assertEquals(rowsBefore, checkItemProjection())
        assertEquals(
            listOf("id", "template_version_id", "room_key", "repeatable", "sort", "created_at", "updated_at", "deleted_at"),
            columnNames("template_room_def"),
        )
    }

    private fun createV1CheckItemTable() {
        driver.execute(
            null,
            """CREATE TABLE check_item_def (
                id TEXT NOT NULL PRIMARY KEY,
                template_version_id TEXT NOT NULL,
                stable_id TEXT NOT NULL,
                area TEXT NOT NULL,
                room TEXT NOT NULL,
                text_en TEXT NOT NULL,
                text_zh TEXT NOT NULL,
                allowed_statuses TEXT NOT NULL,
                photo_rule TEXT,
                sort INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                CHECK (photo_rule IS NULL OR photo_rule IN ('ROOM_PANORAMA', 'ADVERSE_ONLY'))
            )""".trimIndent(),
            0,
        )
        listOf("KITCHEN", "BEDROOM", "LOUNGE").forEachIndexed { index, room ->
            driver.execute(
                null,
                """INSERT INTO check_item_def VALUES (
                    'i$index', 'v1', 'STABLE-$index', 'INTERIOR', '$room', 'en$index', 'zh$index',
                    '["GOOD"]', NULL, $index, 1700000000000, 1700000000000, NULL
                )""".trimIndent(),
                0,
            )
        }
    }

    private fun insertVersion(id: String, type: String, version: Int) {
        driver.execute(
            null,
            "INSERT INTO template_version (id, type, version, content_hash, created_at, updated_at) VALUES ('$id', '$type', $version, 'hash', 1, 1)",
            0,
        )
    }

    private fun insertRoom(id: String, versionId: String, key: String, repeatable: Int) {
        driver.execute(
            null,
            "INSERT INTO template_room_def (id, template_version_id, room_key, repeatable, sort, created_at, updated_at) VALUES ('$id', '$versionId', '$key', $repeatable, 0, 1, 1)",
            0,
        )
    }

    private fun columnNames(table: String): List<String> =
        driver.executeQuery(null, "PRAGMA table_info($table)", { cursor ->
            val names = mutableListOf<String>()
            while (cursor.next().value) names += cursor.getString(1)!!
            QueryResult.Value(names)
        }, 0).value

    private fun checkItemProjection(): List<String> =
        driver.executeQuery(
            null,
            "SELECT stable_id, room, sort FROM check_item_def ORDER BY sort, id",
            { cursor ->
                val rows = mutableListOf<String>()
                while (cursor.next().value) rows += "${cursor.getString(0)}|${cursor.getString(1)}|${cursor.getLong(2)}"
                QueryResult.Value(rows)
            },
            0,
        ).value

    private fun scalarLong(sql: String): Long =
        driver.executeQuery(null, sql, { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0)!! else 0L)
        }, 0).value
}

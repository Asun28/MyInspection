package nz.myinspection.core.template

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import nz.myinspection.core.db.MyInspectionDatabase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Schema v2 的迁移、闭集形态、布尔约束与活跃业务键唯一性。
 * Review-repair receipt: independently deleting the parent EXISTS and the inspection NOT EXISTS made
 * `guarded room inserts reject every invalid parent lifecycle` fail at expected 0 / actual 1; the SQL
 * restored byte-exact. Before the history indexes, the plan test failed on SCAN + TEMP B-TREE.
 */
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
    fun `schema v4 exposes the exact room definition columns`() {
        MyInspectionDatabase.Schema.create(driver)

        assertEquals(4L, MyInspectionDatabase.Schema.version)
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
    fun `guarded room inserts reject every invalid parent lifecycle`() {
        MyInspectionDatabase.Schema.create(driver)
        val queries = MyInspectionDatabase(driver).templateRoomDefQueries

        assertEquals(0L, guardedRoomInsert(queries, "missing-room", "missing-version"))
        assertEquals(0L, scalarLong("SELECT COUNT(*) FROM template_room_def WHERE id = 'missing-room'"))

        insertVersion("deleted-version", "ROUTINE", 1)
        driver.execute(null, "UPDATE template_version SET deleted_at = 2 WHERE id = 'deleted-version'", 0)
        assertEquals(0L, guardedRoomInsert(queries, "deleted-room", "deleted-version"))
        assertEquals(0L, scalarLong("SELECT COUNT(*) FROM template_room_def WHERE id = 'deleted-room'"))

        insertVersion("referenced-version", "ROUTINE", 2)
        driver.execute(
            null,
            """INSERT INTO inspection (
                id, type, property_id, template_version_id, scheduled_at, status, created_at, updated_at
            ) VALUES ('inspection-1', 'ROUTINE', 'property-1', 'referenced-version', 1, 'DRAFT', 1, 1)""".trimIndent(),
            0,
        )
        assertEquals(0L, guardedRoomInsert(queries, "referenced-room", "referenced-version"))
        assertEquals(0L, scalarLong("SELECT COUNT(*) FROM template_room_def WHERE id = 'referenced-room'"))

        insertVersion("active-version", "ROUTINE", 3)
        assertEquals(1L, guardedRoomInsert(queries, "active-room", "active-version"))
        assertEquals(1L, scalarLong("SELECT COUNT(*) FROM template_room_def WHERE id = 'active-room'"))
    }

    @Test
    fun `historical template reads use covering order indexes without temporary sorts`() {
        MyInspectionDatabase.Schema.create(driver)

        val itemPlan = queryPlanDetails(
            "SELECT * FROM check_item_def WHERE template_version_id = 'version-1' ORDER BY sort ASC, id ASC",
        )
        val roomPlan = queryPlanDetails(
            "SELECT * FROM template_room_def WHERE template_version_id = 'version-1' ORDER BY sort ASC, id ASC",
        )

        assertEquals(
            listOf("SEARCH check_item_def USING INDEX idx_check_item_def_history (template_version_id=?)"),
            itemPlan,
        )
        assertEquals(
            listOf("SEARCH template_room_def USING INDEX idx_template_room_def_history (template_version_id=?)"),
            roomPlan,
        )
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

    private fun guardedRoomInsert(
        queries: nz.myinspection.core.db.TemplateRoomDefQueries,
        id: String,
        versionId: String,
    ): Long = queries.insert(
        id = id,
        template_version_id = versionId,
        room_key = "KITCHEN",
        repeatable = 0L,
        sort = 0L,
        created_at = 1L,
        updated_at = 1L,
    ).value

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

    private fun queryPlanDetails(sql: String): List<String> =
        driver.executeQuery(null, "EXPLAIN QUERY PLAN $sql", { cursor ->
            val details = mutableListOf<String>()
            while (cursor.next().value) details += cursor.getString(3)!!
            QueryResult.Value(details)
        }, 0).value
}

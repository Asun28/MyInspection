package nz.myinspection.core.template

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import nz.myinspection.core.db.ClockMs
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.Uuid7Generator
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 入库/读回的往返测试，跑在 JdbcSqliteDriver 内存库上（真 SQLite，非 mock——`check_item_def` 的
 * 守卫与唯一索引都是真的在起作用）。
 */
class TemplateStoreTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: MyInspectionDatabase
    private lateinit var store: TemplateStore

    private val now = 1_700_000_000_000L

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MyInspectionDatabase.Schema.create(driver)
        database = MyInspectionDatabase(driver)
        store = TemplateStore(database, Uuid7Generator(), ClockMs { now })
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    private fun loadRoutine(): LoadedTemplate =
        TemplateLoader.load(TemplateTestFixtures.routineTemplate().byteInputStream())

    @Test
    fun `persist then read round-trips the whole template`() {
        val loaded = loadRoutine()

        val versionId = store.persist(loaded)

        assertEquals(loaded.template, store.read(versionId))
    }

    @Test
    fun `persist records the type, version and content hash on template_version`() {
        val loaded = loadRoutine()

        val versionId = store.persist(loaded)

        val row = database.templateVersionQueries.selectById(versionId).executeAsOne()
        assertEquals("ROUTINE", row.type)
        assertEquals(1L, row.version)
        assertEquals(loaded.contentHash, row.content_hash)
        assertEquals(now, row.created_at)
    }

    @Test
    fun `persisted sort follows the template array order`() {
        // 往返相等测不到这条：`selectByTemplateVersion` 的次级排序键是 id，而 UUIDv7 单调，
        // 于是"全部 sort 都写 0"照样按插入序读回来。sort 要单独逐值断言。
        val versionId = store.persist(loadRoutine())

        val defs = database.checkItemDefQueries.selectByTemplateVersion(versionId).executeAsList()
        assertEquals(listOf("KIT-BENCH-01", "KIT-ROOM-01", "BED-WALL-01"), defs.map { it.stable_id })
        assertEquals(listOf(0L, 1L, 2L), defs.map { it.sort })
    }

    @Test
    fun `allowed statuses land as a JSON array on check_item_def`() {
        val versionId = store.persist(loadRoutine())

        val first = database.checkItemDefQueries.selectByTemplateVersion(versionId).executeAsList().first()
        assertEquals("""["GOOD","FAIR","POOR","NOT_APPLICABLE"]""", first.allowed_statuses)
        assertEquals("ADVERSE_ONLY", first.photo_rule)
    }

    @Test
    fun `read returns null for an unknown template version`() {
        assertNull(store.read("no-such-version"))
    }

    @Test
    fun `the same type and version cannot be persisted twice`() {
        val loaded = loadRoutine()
        store.persist(loaded)

        // 唯一索引的存在本身另有测试（DbUniqueConstraintsTest）；这里测的是 store **不吞**它——
        // 「同版本号不同内容」正是要被人看见的时刻。
        val ex = assertFailsWith<Exception> { store.persist(loaded) }
        assertTrue(
            ex.message.orEmpty().contains("UNIQUE", ignoreCase = true),
            "expected a UNIQUE constraint violation, got: ${ex.message}",
        )
        assertEquals(1, database.templateVersionQueries.selectActive().executeAsList().size)
    }

    @Test
    fun `a failure part-way through leaves no half template behind`() {
        // 刻意绕开 TemplateLoader 直接造一份带重复 stable_id 的模板（校验器本来会拦下它）：
        // 只有这样才能让**第二条**项定义在数据库层撞唯一索引，从而检验"版本行 + 前面几条项定义"
        // 是否被回滚。半份模板比整体失败坏得多：报告静默少项，而那一版的 (type, version)
        // 已被占住，正确的重灌再也进不来。
        val duplicated = Template(
            type = "ROUTINE",
            version = 7,
            items = listOf(
                TemplateItem(stableId = "KIT-BENCH-01", area = "INTERIOR", room = "KITCHEN", textEn = "a", textZh = "甲", allowedStatuses = listOf("GOOD")),
                TemplateItem(stableId = "KIT-BENCH-01", area = "INTERIOR", room = "KITCHEN", textEn = "b", textZh = "乙", allowedStatuses = listOf("GOOD")),
            ),
        )

        assertFailsWith<Exception> { store.persist(LoadedTemplate(duplicated, contentHash = "deadbeef")) }

        assertEquals(emptyList(), database.templateVersionQueries.selectActive().executeAsList())
    }
}

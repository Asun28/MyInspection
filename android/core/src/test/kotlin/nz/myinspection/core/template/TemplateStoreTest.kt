package nz.myinspection.core.template

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
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
        // 在**第二条**项定义上注入驱动级失败（磁盘/连接在写到一半时出错——校验拦不住的那类），
        // 检验"版本行 + 已写入的前几条项定义"是否整体回滚。半份模板比整体失败坏得多：
        // 报告会静默少项，而那一版的 (type, version) 已被唯一索引占住，正确的重灌再也进不来。
        val storeOnFailingDriver = storeOn(ItemInsertFault.Mode.THROW)

        assertFailsWith<IllegalStateException> { storeOnFailingDriver.persist(loadRoutine()) }

        assertEquals(emptyList(), database.templateVersionQueries.selectActive().executeAsList())
    }

    @Test
    fun `an item definition that writes zero rows aborts the whole persist`() {
        // `check_item_def.insert` 是带 WHERE EXISTS 守卫的 INSERT…SELECT：前提不满足时**0 行、不报错**（L215）。
        // 让驱动如实返回 0 行（而不是抛异常）——这是那条守卫真实的失败形态，也是 persist 里
        // `affected == 1L` 检查唯一能被触发的路径。少了这道检查，模板会静默少一条项目。
        val storeOnFailingDriver = storeOn(ItemInsertFault.Mode.RETURN_ZERO_ROWS)

        val ex = assertFailsWith<IllegalStateException> { storeOnFailingDriver.persist(loadRoutine()) }

        assertTrue(
            ex.message.orEmpty().contains("affected 0 rows"),
            "expected the affected-rows guard to fire, got: ${ex.message}",
        )
        assertEquals(emptyList(), database.templateVersionQueries.selectActive().executeAsList())
    }

    private fun storeOn(mode: ItemInsertFault.Mode): TemplateStore =
        TemplateStore(MyInspectionDatabase(ItemInsertFault(driver, mode)), Uuid7Generator(), ClockMs { now })

    @Test
    fun `persist refuses a template that would not pass validation`() {
        // LoadedTemplate 的构造器是 internal：模块外造不出，只有 :core 内（含本测试）能故意造一份非法的。
        // 持久化边界仍自己再校验一次——数据库里不该出现引擎自己会拒的模板，哪怕它是从模块内递进来的。
        val invalid = LoadedTemplate(Template(type = "ROUTINE", version = 1, items = emptyList()), contentHash = "deadbeef")

        val ex = assertFailsWith<TemplateValidationException> { store.persist(invalid) }

        assertEquals(listOf("template: items is empty"), ex.errors)
        assertEquals(emptyList(), database.templateVersionQueries.selectActive().executeAsList())
    }
}

/**
 * 在第 2 条 `check_item_def` INSERT 上注入故障的驱动包装：把"写到一半出问题"变成可测事件。
 * 两种形态对应两类真实失败——[Mode.THROW] = 驱动/磁盘报错；[Mode.RETURN_ZERO_ROWS] = SQL 执行成功
 * 但 WHERE EXISTS 守卫未命中、**0 行受影响且不报错**（L215 那类静默失败）。
 * 其余成员一律委托（`by delegate`），故除注入点外行为与真驱动完全一致。
 */
private class ItemInsertFault(
    private val delegate: SqlDriver,
    private val mode: Mode,
) : SqlDriver by delegate {
    enum class Mode { THROW, RETURN_ZERO_ROWS }

    private var itemInserts = 0

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> {
        if (sql.contains("INSERT INTO check_item_def") && ++itemInserts == 2) {
            when (mode) {
                Mode.THROW -> throw IllegalStateException("injected driver failure on the 2nd item definition")
                Mode.RETURN_ZERO_ROWS -> return QueryResult.Value(0L)
            }
        }
        return delegate.execute(identifier, sql, parameters, binders)
    }
}

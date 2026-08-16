package nz.myinspection.core.finalize

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import nz.myinspection.core.canon.canonicalJson
import nz.myinspection.core.canon.sha256Hex
import nz.myinspection.core.db.DbTestFixtures
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.Uuid7Generator
import nz.myinspection.core.model.InspectionItemSnapshot

/**
 * [InspectionSnapshotAssembler]：还清 TD5（`specs/tech-debt-tracker.md`）——canonical 数组序契约此前
 * 只存在于注释里、canon 层本身验证不了。这里用一个跨层黄金测试钉住："唯一装配正门"按模板全序拼出的
 * `items[]`，与调用方自己按插入顺序瞎拼出来的 `items[]`，canonical JSON / data_hash 必须不同——
 * 顺序是哈希域的一部分，不是装配细节。
 */
class InspectionSnapshotAssemblerTest {
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
    fun `items are assembled in template sort order regardless of insertion order (TD5)`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        // sort 与创建顺序刻意错开：wall.paint 的模板序在前（sort=1），但下面先建 ceiling 的 check_item_def。
        FinalizeTestFixtures.insertCheckItemDef(database, uuid, templateVersionId, stableId = "ceiling.paint", sort = 2, now = now)
        FinalizeTestFixtures.insertCheckItemDef(database, uuid, templateVersionId, stableId = "wall.paint", sort = 1, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)
        // inspection_item 的插入顺序同样与模板序相反：ceiling 先答，wall 后答。
        DbTestFixtures.insertInspectionItem(database, uuid, inspectionId, roomInstanceId, stableId = "ceiling.paint", status = "GOOD", now = now)
        DbTestFixtures.insertInspectionItem(database, uuid, inspectionId, roomInstanceId, stableId = "wall.paint", status = "FAIR", now = now)

        val snapshot = InspectionSnapshotAssembler.assemble(database, inspectionId, finalizedAt = now + 1)

        // 正门必须按模板序（wall 先、ceiling 后），不是插入序（ceiling 先、wall 后）。
        assertEquals(listOf("wall.paint", "ceiling.paint"), snapshot.items.map { it.stableId })

        val correctOrderHash = sha256Hex(canonicalJson(snapshot))
        val shuffledSnapshot = snapshot.copy(items = snapshot.items.reversed())
        val shuffledHash = sha256Hex(canonicalJson(shuffledSnapshot))

        assertNotEquals(
            correctOrderHash, shuffledHash,
            "items[] 顺序必须参与哈希——插入序与模板序在这份夹具里刻意不同，若两个哈希相等说明顺序被悄悄忽略了",
        )
    }

    @Test
    fun `a draft inspection with no tenancy projects a null tenancy snapshot`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)

        val snapshot = InspectionSnapshotAssembler.assemble(database, inspectionId, finalizedAt = null)

        assertNull(snapshot.tenancy)
        assertNull(snapshot.finalizedAt)
    }

    @Test
    fun `an inspection with a tenancy projects the tenancy snapshot without contact fields`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val tenancyId = FinalizeTestFixtures.insertTenancy(database, uuid, propertyId, startMs = now - 1000, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, tenancyId = tenancyId, now = now)

        val snapshot = InspectionSnapshotAssembler.assemble(database, inspectionId, finalizedAt = null)

        assertEquals(tenancyId, snapshot.tenancy?.id)
        assertEquals(now - 1000, snapshot.tenancy?.startMs)
    }

    @Test
    fun `photos are ordered by id across room instances, not by room creation order`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomA = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = "BEDROOM", instanceNo = 1, now = now)
        val roomB = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = "BEDROOM", instanceNo = 2, now = now)
        // roomB 的照片先生成（UUIDv7 id 更小），roomA 的照片后生成（id 更大）；装配按房间**创建序**
        // flatMap 会先出 roomA 后出 roomB——若正门没有显式按 id 排序，输出顺序就会与 id 升序相反，
        // 这条断言才真的分得出"排了序"与"凑巧插入序也对"。
        val earlierId = FinalizeTestFixtures.insertRoomLevelPhoto(database, uuid, roomB, contentHash = "earlier", now = now)
        val laterId = FinalizeTestFixtures.insertRoomLevelPhoto(database, uuid, roomA, contentHash = "later", now = now)
        check(earlierId < laterId) { "UUIDv7 生成序假设不成立，夹具需要重新设计" }

        val snapshot = InspectionSnapshotAssembler.assemble(database, inspectionId, finalizedAt = null)

        assertEquals(listOf("earlier", "later"), snapshot.photos.map { it.contentHash })
    }

    @Test
    fun `audios are ordered by id across items, not by template order`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        FinalizeTestFixtures.insertCheckItemDef(database, uuid, templateVersionId, stableId = "wall.paint", sort = 1, now = now)
        FinalizeTestFixtures.insertCheckItemDef(database, uuid, templateVersionId, stableId = "ceiling.paint", sort = 2, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)
        val wallItemId = DbTestFixtures.insertInspectionItem(database, uuid, inspectionId, roomInstanceId, stableId = "wall.paint", now = now)
        val ceilingItemId = DbTestFixtures.insertInspectionItem(database, uuid, inspectionId, roomInstanceId, stableId = "ceiling.paint", now = now)
        // 模板序把 wall（sort=1）排在 ceiling（sort=2）前面；这里让 ceiling 的音频先生成（id 更小），
        // wall 的音频后生成（id 更大）——按模板序 flatMap 会先出 wall 后出 ceiling，同样与 id 升序相反。
        val earlierId = FinalizeTestFixtures.insertAudio(database, uuid, ceilingItemId, contentHash = "earlier", now = now)
        val laterId = FinalizeTestFixtures.insertAudio(database, uuid, wallItemId, contentHash = "later", now = now)
        check(earlierId < laterId) { "UUIDv7 生成序假设不成立，夹具需要重新设计" }

        val snapshot = InspectionSnapshotAssembler.assemble(database, inspectionId, finalizedAt = null)

        assertEquals(listOf("earlier", "later"), snapshot.audios.map { it.contentHash })
    }

    @Test
    fun `an item's note and wear_or_damage carry through to the snapshot`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, type = "EXIT", now = now)
        FinalizeTestFixtures.insertCheckItemDef(database, uuid, templateVersionId, stableId = "wall.paint", sort = 1, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, type = "EXIT", now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)
        val itemId = DbTestFixtures.insertInspectionItem(database, uuid, inspectionId, roomInstanceId, stableId = "wall.paint", status = "POOR", now = now)
        database.inspectionItemQueries.updateStatusIfDraft(status = "POOR", note = "scuffed corner", updated_at = now + 1, id = itemId)
        database.inspectionItemQueries.updateWearOrDamageIfDraft(wear_or_damage = "DAMAGE", updated_at = now + 1, id = itemId)

        val snapshot = InspectionSnapshotAssembler.assemble(database, inspectionId, finalizedAt = null)

        assertEquals(
            listOf(InspectionItemSnapshot(stableId = "wall.paint", status = "POOR", note = "scuffed corner", wearOrDamage = "DAMAGE")),
            snapshot.items,
        )
    }
}

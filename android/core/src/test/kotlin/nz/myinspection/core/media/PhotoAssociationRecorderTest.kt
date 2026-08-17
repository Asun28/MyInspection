package nz.myinspection.core.media

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import nz.myinspection.core.db.ClockMs
import nz.myinspection.core.db.DbTestFixtures
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.Uuid7Generator

/**
 * 「落盘 + 入库」的入库半步：一次 ingest 必须留下一条带齐元数据的 `photo` 行，写不成时必须把本次刚落的
 * 字节撤销掉——那份文件没有任何 photo 行引用，`photo.orphanedAssets()` 永远发现不了它（那条查询只找被
 * 软删的行），漏撤即是一份谁也回收不了的垃圾。
 */
class PhotoAssociationRecorderTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: MyInspectionDatabase
    private lateinit var uuid: Uuid7Generator
    private lateinit var recorder: PhotoAssociationRecorder

    /** 撤销动作的调用记录——「该撤的撤了、不该撤的一次都没碰」两侧都要能断言。 */
    private val discardCalls = mutableListOf<String>()
    private val discardOk = NewAssetDiscard { relPath -> discardCalls.add(relPath); true }
    private val discardFails = NewAssetDiscard { relPath -> discardCalls.add(relPath); false }
    private val discardThrows = NewAssetDiscard { relPath -> discardCalls.add(relPath); throw IOException("simulated disk failure") }

    /** `template_version` 有 UNIQUE(type, version)，同一个测试类里建多间巡检要各给一个版本号。 */
    private var nextTemplateVersion = 0L

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MyInspectionDatabase.Schema.create(driver)
        database = MyInspectionDatabase(driver)
        uuid = Uuid7Generator()
        recorder = PhotoAssociationRecorder(database, ClockMs { RECORDED_AT })
        // TestNG 复用同一个测试类实例：这两项状态若不重置，会跨测试方法互相污染。
        discardCalls.clear()
        nextTemplateVersion = 0
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    private data class Fixture(val propertyId: String, val inspectionId: String, val roomInstanceId: String)

    private fun draftFixture(roomKey: String = "BEDROOM"): Fixture {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, version = ++nextTemplateVersion)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, roomKey = roomKey)
        return Fixture(propertyId, inspectionId, roomInstanceId)
    }

    private fun finalize(inspectionId: String) {
        database.inspectionQueries.finalizeIfDraft(
            finalized_at = DbTestFixtures.NOW + 1, data_hash = "h", updated_at = DbTestFixtures.NOW + 1, id = inspectionId,
        )
    }

    private fun newPlan(fixture: Fixture, photoId: String, hash: String) = PhotoIngestPlan.WriteNewAsset(
        relPath = MediaPaths.photoRelPath(fixture.propertyId, fixture.inspectionId, photoId),
        contentHash = hash,
    )

    @Test
    fun `a landed new asset becomes a row carrying rel_path, hash, exif time, source and privacy flag`() {
        val fixture = draftFixture()
        val photoId = uuid.next()
        val plan = newPlan(fixture, photoId, "hash-a")

        val result = recorder.record(
            plan = plan,
            photoId = photoId,
            target = PhotoTarget(roomInstanceId = fixture.roomInstanceId, inspectionItemId = null, privacyFlag = true),
            source = PhotoSource.IMPORTED,
            exifTimeMs = EXIF_TIME_MS,
            discard = discardOk,
        )

        assertEquals(PhotoAssociationResult.Recorded(photoId, plan.relPath, reusedExistingAsset = false), result)
        val row = database.photoQueries.selectById(photoId).executeAsOne()
        assertEquals(plan.relPath, row.rel_path)
        assertEquals("hash-a", row.content_hash)
        assertEquals(fixture.roomInstanceId, row.room_instance_id)
        assertNull(row.inspection_item_id, "a room-level panorama hangs off the room only")
        assertEquals(EXIF_TIME_MS, row.exif_time_ms, "拍摄时间必须与巡检时间分开入库（需求 §5）")
        assertEquals("IMPORTED", row.source)
        assertEquals(1L, row.privacy_flag, "含租客物品的标记必须落库，报告据此排除")
        assertEquals(RECORDED_AT, row.created_at)
        assertEquals(RECORDED_AT, row.updated_at)
        assertEquals(emptyList(), discardCalls, "成功入库不得触发任何撤销")
    }

    @Test
    fun `a camera photo attached to an item lands with source CAMERA and privacy flag 0`() {
        val fixture = draftFixture()
        val itemId = uuid.next()
        database.inspectionItemQueries.insert(
            id = itemId, inspection_id = fixture.inspectionId, room_instance_id = fixture.roomInstanceId,
            stable_id = "wall-1", status = "GOOD", note = null, wear_or_damage = null,
            created_at = DbTestFixtures.NOW, updated_at = DbTestFixtures.NOW,
        )
        val photoId = uuid.next()

        recorder.record(
            plan = newPlan(fixture, photoId, "hash-cam"),
            photoId = photoId,
            target = PhotoTarget(roomInstanceId = fixture.roomInstanceId, inspectionItemId = itemId, privacyFlag = false),
            source = PhotoSource.CAMERA,
            exifTimeMs = null,
            discard = discardOk,
        )

        val row = database.photoQueries.selectById(photoId).executeAsOne()
        assertEquals(itemId, row.inspection_item_id)
        assertEquals("CAMERA", row.source, "来源标记必须如实反映管线，不能写死一个值")
        assertEquals(0L, row.privacy_flag)
        assertNull(row.exif_time_ms, "相机路径没有拍摄时间可用时留空，不得伪造一个")
    }

    @Test
    fun `reusing an existing asset adds a SECOND association to the same file and never discards it`() {
        val first = draftFixture()
        val firstPhotoId = uuid.next()
        val firstPlan = newPlan(first, firstPhotoId, "shared-hash")
        recorder.record(firstPlan, firstPhotoId, PhotoTarget(first.roomInstanceId), PhotoSource.CAMERA, null, discardOk)

        // 跨房间/跨巡检的同内容照片是合法的（同一缺陷 Ingoing 与 Exit 各拍一次），复用的是**同一份**物理文件。
        val second = draftFixture(roomKey = "KITCHEN")
        val secondPhotoId = uuid.next()
        val reusePlan = PhotoIngestPlan.ReuseExistingAsset(relPath = firstPlan.relPath, contentHash = "shared-hash")

        val result = recorder.record(reusePlan, secondPhotoId, PhotoTarget(second.roomInstanceId), PhotoSource.IMPORTED, null, discardOk)

        assertEquals(PhotoAssociationResult.Recorded(secondPhotoId, firstPlan.relPath, reusedExistingAsset = true), result)
        assertEquals(firstPlan.relPath, database.photoQueries.selectById(firstPhotoId).executeAsOne().rel_path)
        assertEquals(firstPlan.relPath, database.photoQueries.selectById(secondPhotoId).executeAsOne().rel_path)
        assertEquals(
            listOf(firstPlan.relPath),
            database.photoQueries.selectActiveAssetsByContentHash("shared-hash").executeAsList(),
            "两条关联、一份物理文件——去重复用不再复制字节，只建关联",
        )
        assertEquals(emptyList(), discardCalls)
    }

    @Test
    fun `a finalized inspection rejects the row and the just-landed bytes are discarded`() {
        val fixture = draftFixture()
        finalize(fixture.inspectionId)
        val photoId = uuid.next()
        val plan = newPlan(fixture, photoId, "hash-a")

        val result = recorder.record(plan, photoId, PhotoTarget(fixture.roomInstanceId), PhotoSource.IMPORTED, null, discardOk)

        assertEquals(PhotoAssociationResult.RejectedByGuard(plan.relPath, orphanedFileRemains = false), result)
        assertNull(database.photoQueries.selectById(photoId).executeAsOneOrNull(), "守卫拒绝时不得有行落库")
        assertEquals(listOf(plan.relPath), discardCalls, "行没写成，本次刚落的字节必须被撤销，否则它是一份谁也发现不了的垃圾")
    }

    @Test
    fun `an inspection_item borrowed from another room rejects the row and discards the bytes`() {
        val fixture = draftFixture()
        val otherRoomId = DbTestFixtures.insertRoomInstance(database, uuid, fixture.inspectionId, roomKey = "KITCHEN")
        val itemInOtherRoom = uuid.next()
        database.inspectionItemQueries.insert(
            id = itemInOtherRoom, inspection_id = fixture.inspectionId, room_instance_id = otherRoomId,
            stable_id = "sink-1", status = "GOOD", note = null, wear_or_damage = null,
            created_at = DbTestFixtures.NOW, updated_at = DbTestFixtures.NOW,
        )
        val photoId = uuid.next()
        val plan = newPlan(fixture, photoId, "hash-a")

        val result = recorder.record(
            plan, photoId,
            PhotoTarget(roomInstanceId = fixture.roomInstanceId, inspectionItemId = itemInOtherRoom),
            PhotoSource.CAMERA, null, discardOk,
        )

        assertEquals(PhotoAssociationResult.RejectedByGuard(plan.relPath, orphanedFileRemains = false), result)
        assertNull(database.photoQueries.selectById(photoId).executeAsOneOrNull())
        assertEquals(listOf(plan.relPath), discardCalls)
    }

    @Test
    fun `a rejected REUSE never discards the shared file — those bytes belong to another association`() {
        val donor = draftFixture()
        val donorPhotoId = uuid.next()
        val donorPlan = newPlan(donor, donorPhotoId, "shared-hash")
        recorder.record(donorPlan, donorPhotoId, PhotoTarget(donor.roomInstanceId), PhotoSource.CAMERA, null, discardOk)

        val finalized = draftFixture(roomKey = "KITCHEN")
        finalize(finalized.inspectionId)
        val photoId = uuid.next()
        val reusePlan = PhotoIngestPlan.ReuseExistingAsset(relPath = donorPlan.relPath, contentHash = "shared-hash")

        val result = recorder.record(reusePlan, photoId, PhotoTarget(finalized.roomInstanceId), PhotoSource.IMPORTED, null, discardOk)

        assertEquals(PhotoAssociationResult.RejectedByGuard(donorPlan.relPath, orphanedFileRemains = false), result)
        assertEquals(emptyList(), discardCalls, "删掉复用目标就是删掉 donor 关联仍在引用的证据文件")
        assertEquals(donorPlan.relPath, database.photoQueries.selectById(donorPhotoId).executeAsOne().rel_path)
    }

    @Test
    fun `a compensation that could not remove the file is reported, not swallowed`() {
        val fixture = draftFixture()
        finalize(fixture.inspectionId)
        val photoId = uuid.next()
        val plan = newPlan(fixture, photoId, "hash-a")

        val result = recorder.record(plan, photoId, PhotoTarget(fixture.roomInstanceId), PhotoSource.IMPORTED, null, discardFails)

        assertEquals(PhotoAssociationResult.RejectedByGuard(plan.relPath, orphanedFileRemains = true), result)
        assertEquals(listOf(plan.relPath), discardCalls)
    }

    @Test
    fun `a hash that raced into the same room after the lookup propagates and still discards the new bytes`() {
        // 唯一索引 (room_instance_id, content_hash) WHERE deleted_at IS NULL：查重与入库之间若有第二条
        // ingest 抢先落了同哈希，insert 抛异常而非返回 0 行——这条路径同样不能把字节留在磁盘上。
        val fixture = draftFixture()
        val incumbentId = uuid.next()
        recorder.record(newPlan(fixture, incumbentId, "racing-hash"), incumbentId, PhotoTarget(fixture.roomInstanceId), PhotoSource.CAMERA, null, discardOk)
        discardCalls.clear()

        val photoId = uuid.next()
        val plan = newPlan(fixture, photoId, "racing-hash")

        assertFailsWith<Exception> {
            recorder.record(plan, photoId, PhotoTarget(fixture.roomInstanceId), PhotoSource.IMPORTED, null, discardOk)
        }
        assertEquals(listOf(plan.relPath), discardCalls, "异常路径同样要补偿——否则一次并发写入就永久留下一份未追踪文件")
        assertNull(database.photoQueries.selectById(photoId).executeAsOneOrNull())
    }

    @Test
    fun `a failed compensation on the exception path is attached as a suppressed cause`() {
        val fixture = draftFixture()
        val incumbentId = uuid.next()
        recorder.record(newPlan(fixture, incumbentId, "racing-hash"), incumbentId, PhotoTarget(fixture.roomInstanceId), PhotoSource.CAMERA, null, discardOk)

        val photoId = uuid.next()
        val plan = newPlan(fixture, photoId, "racing-hash")

        val thrown = assertFailsWith<Exception> {
            recorder.record(plan, photoId, PhotoTarget(fixture.roomInstanceId), PhotoSource.IMPORTED, null, discardFails)
        }
        val suppressed = assertIs<IllegalStateException>(thrown.suppressed.single())
        assertEquals("compensation failed, ${plan.relPath} is now untracked", suppressed.message)
    }

    @Test
    fun `a same-photoId retry never discards the file the winning row already references`() {
        // 同一个 photoId 重试：先到的那次已把行写成、字节也已发布到同一个路径（rel_path 由 photoId 派生）。
        // 本次 insert 撞主键失败，若照常补偿就是删掉赢家正在引用的证据——赢家若已 FINALIZED，删的就是巡检证据。
        val fixture = draftFixture()
        val photoId = uuid.next()
        val plan = newPlan(fixture, photoId, "hash-a")
        recorder.record(plan, photoId, PhotoTarget(fixture.roomInstanceId), PhotoSource.IMPORTED, null, discardOk)
        discardCalls.clear()

        assertFailsWith<Exception> {
            recorder.record(plan, photoId, PhotoTarget(fixture.roomInstanceId), PhotoSource.IMPORTED, null, discardOk)
        }

        assertEquals(emptyList(), discardCalls, "该路径仍有活跃行引用，补偿必须放手——删下去就是删掉赢家的证据")
        assertEquals(plan.relPath, database.photoQueries.selectById(photoId).executeAsOne().rel_path)
    }

    @Test
    fun `a discard that throws is reported as a failed compensation, not raised out of a guard rejection`() {
        val fixture = draftFixture()
        finalize(fixture.inspectionId)
        val photoId = uuid.next()
        val plan = newPlan(fixture, photoId, "hash-a")

        val result = recorder.record(plan, photoId, PhotoTarget(fixture.roomInstanceId), PhotoSource.IMPORTED, null, discardThrows)

        assertEquals(PhotoAssociationResult.RejectedByGuard(plan.relPath, orphanedFileRemains = true), result)
        assertEquals(listOf(plan.relPath), discardCalls)
    }

    @Test
    fun `a discard that throws on the exception path does not replace the original failure`() {
        val fixture = draftFixture()
        val incumbentId = uuid.next()
        recorder.record(newPlan(fixture, incumbentId, "racing-hash"), incumbentId, PhotoTarget(fixture.roomInstanceId), PhotoSource.CAMERA, null, discardOk)

        val photoId = uuid.next()
        val plan = newPlan(fixture, photoId, "racing-hash")

        val thrown = assertFailsWith<Exception> {
            recorder.record(plan, photoId, PhotoTarget(fixture.roomInstanceId), PhotoSource.IMPORTED, null, discardThrows)
        }
        assertFalse(thrown is IOException, "冒泡的必须仍是入库失败本身，不是补偿动作的 IO 异常")
        assertIs<IllegalStateException>(thrown.suppressed.single())
    }

    @Test
    fun `resolvePathContext derives property and inspection from the room, so a caller cannot mismatch them`() {
        val fixture = draftFixture()
        val other = draftFixture(roomKey = "KITCHEN")

        val context = recorder.resolvePathContext(fixture.roomInstanceId)
        assertEquals(PhotoPathContext(fixture.propertyId, fixture.inspectionId), context)
        assertEquals(
            PhotoPathContext(other.propertyId, other.inspectionId),
            recorder.resolvePathContext(other.roomInstanceId),
            "另一个房间必须解析到它自己的巡检，而不是上一个的",
        )
        assertFailsWith<IllegalStateException> { recorder.resolvePathContext("no-such-room") }
    }

    private companion object {
        const val RECORDED_AT = 1_700_000_555_000L
        const val EXIF_TIME_MS = 1_699_999_111_222L
    }
}

package nz.myinspection.core.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CLAUDE.md 关键不变量的 JVM 内存库回归测试（JdbcSqliteDriver in-memory，非 mock）：
 *  1. finalize 后原始条目只读：对 FINALIZED 巡检既不能 UPDATE 既有 inspection_item，
 *     也不能 INSERT 新的 inspection_item/room_instance/photo/audio；supplement 是唯一的
 *     append-only 例外，不适用此闸。
 *  2. inspection 的 status/finalized_at/data_hash 三者必须联动一致（结构性 CHECK 约束）。
 *  3. 既有租约没有 Ingoing 时，可把某次 Routine 巡检指定为该 tenancy 的基线；EXIT 消费夹具读取这个
 *     已存指针并把同一个 Routine 写入自己的 baseline_inspection_id，不另行假设“必有 INGOING”。
 *
 * 引用完整性的缺失/错配父行测试（EXISTS 守卫拦孤儿行/跨巡检串接数据）另见 DbReferentialIntegrityTest。
 */
class DbInvariantsTest {
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
     * Read the inspection type CHECK itself so a future schema type is automatically added to both baseline
     * negative matrices. This makes "new enum without synchronized authority tests" impossible to pass vacuously.
     */
    private fun inspectionTypes(): Set<String> {
        val schemaSql = driver.executeQuery(
            null,
            "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'inspection'",
            { cursor ->
                check(cursor.next().value) { "inspection schema row is missing" }
                QueryResult.Value(cursor.getString(0)!!)
            },
            0,
        ).value
        val values = checkNotNull(
            Regex("""CHECK\s*\(\s*type\s+IN\s*\(([^)]*)\)\s*\)""").find(schemaSql),
        ) { "inspection type CHECK is missing" }.groupValues[1]
        return values.split(',').map { it.trim().trim('\'') }.toSet()
    }

    private fun createV2TenancySchema(target: JdbcSqliteDriver) {
        target.execute(
            null,
            """CREATE TABLE tenancy (
                id TEXT NOT NULL PRIMARY KEY,
                property_id TEXT NOT NULL,
                start_ms INTEGER NOT NULL,
                end_ms INTEGER,
                tenant_name TEXT,
                contact TEXT,
                baseline_inspection_id TEXT,
                purged_at INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER
            )""".trimIndent(),
            0,
        )
        target.execute(null, "CREATE INDEX idx_tenancy_property ON tenancy (property_id)", 0)
        target.execute(null, "CREATE INDEX idx_tenancy_baseline_inspection ON tenancy (baseline_inspection_id)", 0)
    }

    private fun setUpFinalizedInspectionWithItem(): Triple<String, String, String> {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)
        val itemId = DbTestFixtures.insertInspectionItem(database, uuid, inspectionId, roomInstanceId, now = now)
        database.inspectionQueries.finalizeIfDraft(finalized_at = now + 1, data_hash = "deadbeef", updated_at = now + 1, id = inspectionId).value
        return Triple(inspectionId, roomInstanceId, itemId)
    }

    @Test
    fun `update against a finalized inspection affects zero rows`() {
        val (_, _, itemId) = setUpFinalizedInspectionWithItem()

        val affected = database.inspectionItemQueries.updateStatusIfDraft(
            status = "POOR", note = "changed after finalize", updated_at = now + 2, id = itemId,
        ).value

        assertEquals(0L, affected, "updating an item under a FINALIZED inspection must affect 0 rows")
        val stillGood = database.inspectionItemQueries.selectById(itemId).executeAsOne()
        assertEquals("GOOD", stillGood.status)
    }

    @Test
    fun `update against a draft inspection succeeds`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)
        val itemId = DbTestFixtures.insertInspectionItem(database, uuid, inspectionId, roomInstanceId, now = now)

        val affected = database.inspectionItemQueries.updateStatusIfDraft(
            status = "POOR", note = "changed while draft", updated_at = now + 1, id = itemId,
        ).value

        assertEquals(1L, affected, "updating an item under a DRAFT inspection must succeed")
        val updated = database.inspectionItemQueries.selectById(itemId).executeAsOne()
        assertEquals("POOR", updated.status)
    }

    @Test
    fun `insert of a new inspection_item into a finalized inspection affects zero rows`() {
        val (inspectionId, roomInstanceId, _) = setUpFinalizedInspectionWithItem()

        val affected = database.inspectionItemQueries.insert(
            id = uuid.next(), inspection_id = inspectionId, room_instance_id = roomInstanceId,
            stable_id = "ceiling.paint", status = "GOOD", note = null, wear_or_damage = null, created_at = now + 2, updated_at = now + 2,
        ).value

        assertEquals(0L, affected, "inserting a new item under a FINALIZED inspection must affect 0 rows")
    }

    @Test
    fun `insert of a new room_instance into a finalized inspection affects zero rows`() {
        val (inspectionId, _, _) = setUpFinalizedInspectionWithItem()

        val affected = database.roomInstanceQueries.insert(
            id = uuid.next(), inspection_id = inspectionId, room_key = "KITCHEN", instance_no = 1,
            display_label = "Kitchen", created_at = now + 2, updated_at = now + 2,
        ).value

        assertEquals(0L, affected, "inserting a new room instance under a FINALIZED inspection must affect 0 rows")
    }

    @Test
    fun `insert of a new photo into a finalized inspection affects zero rows`() {
        // room_instance created before finalize (legitimate pre-finalize content); the guard is on the
        // photo insert itself, resolved two hops up through room_instance -> inspection.finalized_at.
        val (_, roomInstanceId, _) = setUpFinalizedInspectionWithItem()

        val affected = database.photoQueries.insert(
            id = uuid.next(), inspection_item_id = null, room_instance_id = roomInstanceId,
            rel_path = "late.jpg", content_hash = "latehash", exif_time_ms = null, source = "CAMERA",
            privacy_flag = 0, created_at = now + 2, updated_at = now + 2,
        ).value

        assertEquals(0L, affected, "inserting a new photo under a FINALIZED inspection must affect 0 rows")
    }

    @Test
    fun `insert of a new audio into a finalized inspection affects zero rows`() {
        // inspection_item created before finalize; the guard is on the audio insert itself, resolved
        // two hops up through inspection_item -> inspection.finalized_at.
        val (_, _, itemId) = setUpFinalizedInspectionWithItem()

        val affected = database.audioQueries.insert(
            id = uuid.next(), inspection_item_id = itemId, rel_path = "late.m4a", content_hash = "latehash",
            created_at = now + 2, updated_at = now + 2,
        ).value

        assertEquals(0L, affected, "inserting new audio under a FINALIZED inspection must affect 0 rows")
    }

    @Test
    fun `finalizeIfDraft is a one-shot guard on inspection itself`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)

        val firstFinalize = database.inspectionQueries.finalizeIfDraft(
            finalized_at = now + 1, data_hash = "abc123", updated_at = now + 1, id = inspectionId,
        ).value
        assertEquals(1L, firstFinalize, "finalizing a DRAFT inspection must succeed exactly once")

        val secondFinalize = database.inspectionQueries.finalizeIfDraft(
            finalized_at = now + 2, data_hash = "should-not-land", updated_at = now + 2, id = inspectionId,
        ).value
        assertEquals(0L, secondFinalize, "re-finalizing an already FINALIZED inspection must affect 0 rows")

        val row = database.inspectionQueries.selectById(inspectionId).executeAsOne()
        assertEquals("abc123", row.data_hash, "the second call must not overwrite the original finalize hash")
    }

    @Test
    fun `inspection rejects a FINALIZED row with no finalized_at or data_hash`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val ex = assertFailsWith<Exception>("an incomplete FINALIZED state must violate the CHECK constraint") {
            database.inspectionQueries.insert(
                id = uuid.next(), type = "ROUTINE", property_id = propertyId, tenancy_id = null,
                template_version_id = templateVersionId, scheduled_at = now, previous_inspection_id = null,
                baseline_inspection_id = null, status = "FINALIZED", finalized_at = null, data_hash = null,
                created_at = now, updated_at = now,
            )
        }
        assertTrue(ex.message.orEmpty().contains("CHECK", ignoreCase = true), "expected a CHECK constraint violation, got: ${ex.message}")
    }

    /**
     * 封闭域必须由 CHECK 兑现，而不是只写在列注释里——生成的 API 收任意 String/Long，注释拦不住任何东西，
     * 且 schema 冻结后补约束要走迁移。
     *
     * **这几张表的 insert 带 EXISTS 守卫（INSERT…SELECT），守卫滤掉的行根本走不到 CHECK**——若父行没建好，
     * 插入只是 0 行、不抛异常，测试就会以「没抛=约束不存在」的相反理由假绿。故下面每例都先备齐合法父行，
     * 只把被测那一列换成非法值。
     */
    private fun assertCheckViolation(what: String, block: () -> Unit) {
        val ex = assertFailsWith<Exception>("$what must violate a CHECK constraint") { block() }
        assertTrue(
            ex.message.orEmpty().contains("CHECK", ignoreCase = true),
            "expected a CHECK constraint violation for $what, got: ${ex.message}",
        )
    }

    @Test
    fun `property rejects an unknown kind and a non-boolean boarding-house flag`() {
        assertCheckViolation("an unrecognised property kind") {
            database.propertyQueries.insert(
                id = uuid.next(), address = "12 Test St", kind = "BOGUS", is_boarding_house = 0,
                created_at = now, updated_at = now,
            )
        }
        assertCheckViolation("a boarding-house flag outside 0/1") {
            database.propertyQueries.insert(
                id = uuid.next(), address = "12 Test St", kind = "RENTAL", is_boarding_house = 2,
                created_at = now, updated_at = now,
            )
        }
    }

    @Test
    fun `template_version rejects an unknown type`() {
        assertCheckViolation("an unrecognised template type") {
            database.templateVersionQueries.insert(
                id = uuid.next(), type = "ROUTIN", version = 1, content_hash = "h",
                created_at = now, updated_at = now,
            )
        }
    }

    @Test
    fun `inspection rejects an unknown type`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        assertCheckViolation("an unrecognised inspection type") {
            database.inspectionQueries.insert(
                id = uuid.next(), type = "BOGUS", property_id = propertyId, tenancy_id = null,
                template_version_id = templateVersionId, scheduled_at = now, previous_inspection_id = null,
                baseline_inspection_id = null, status = "DRAFT", finalized_at = null, data_hash = null,
                created_at = now, updated_at = now,
            )
        }
    }

    @Test
    fun `check_item_def rejects an unknown photo_rule but accepts NULL`() {
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        assertCheckViolation("an unrecognised photo_rule") {
            database.checkItemDefQueries.insert(
                id = uuid.next(), template_version_id = templateVersionId, stable_id = "wall.paint",
                area = "INTERIOR", room = "BEDROOM", text_en = "Walls", text_zh = "墙面",
                allowed_statuses = """["GOOD"]""", photo_rule = "BOGUS", sort = 1,
                created_at = now, updated_at = now,
            )
        }
        // NULL 是合法的「无强制拍照要求」，约束不得把它一并挡掉。
        val affected = database.checkItemDefQueries.insert(
            id = uuid.next(), template_version_id = templateVersionId, stable_id = "wall.paint",
            area = "INTERIOR", room = "BEDROOM", text_en = "Walls", text_zh = "墙面",
            allowed_statuses = """["GOOD"]""", photo_rule = null, sort = 1,
            created_at = now, updated_at = now,
        ).value
        assertEquals(1L, affected, "photo_rule NULL means 'no mandatory photo' and must remain insertable")
    }

    @Test
    fun `inspection_item rejects an unknown wear_or_damage but accepts NULL`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)

        assertCheckViolation("an unrecognised wear_or_damage classification") {
            database.inspectionItemQueries.insert(
                id = uuid.next(), inspection_id = inspectionId, room_instance_id = roomInstanceId,
                stable_id = "wall.paint", status = "GOOD", note = null, wear_or_damage = "BOGUS",
                created_at = now, updated_at = now,
            )
        }
        // NULL = 非 Exit 或与 Ingoing 无差异，必须仍可插入。
        val affected = database.inspectionItemQueries.insert(
            id = uuid.next(), inspection_id = inspectionId, room_instance_id = roomInstanceId,
            stable_id = "wall.trim", status = "GOOD", note = null, wear_or_damage = null,
            created_at = now, updated_at = now,
        ).value
        assertEquals(1L, affected, "wear_or_damage NULL is the normal non-Exit case and must remain insertable")
    }

    @Test
    fun `photo rejects an unknown source and a non-boolean privacy_flag`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)

        assertCheckViolation("an unrecognised photo source") {
            database.photoQueries.insert(
                id = uuid.next(), inspection_item_id = null, room_instance_id = roomInstanceId,
                rel_path = "a.jpg", content_hash = "h", exif_time_ms = null, source = "SCANNER",
                privacy_flag = 0, created_at = now, updated_at = now,
            )
        }
        assertCheckViolation("a privacy_flag outside 0/1") {
            database.photoQueries.insert(
                id = uuid.next(), inspection_item_id = null, room_instance_id = roomInstanceId,
                rel_path = "b.jpg", content_hash = "h", exif_time_ms = null, source = "CAMERA",
                privacy_flag = 2, created_at = now, updated_at = now,
            )
        }
    }

    /**
     * sent_via 的域定义在需求方卡片（T4-NOTICES：SMS/EMAIL/LETTER），不在本列注释里——所以上一轮按列注释
     * 扫描封闭域时它被漏掉了。这条测试连同 CHECK 一起补上。NULL 是合法的「尚未发送」，必须仍可插入。
     */
    @Test
    fun `notice rejects an unknown sent_via but accepts the unsent NULL state`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)

        assertCheckViolation("an unrecognised delivery method") {
            database.noticeQueries.insert(
                id = uuid.next(), inspection_id = inspectionId, full_text = "notice", generated_at = now,
                scheduled_at = now + 172_800_000L, sent_via = "CARRIER_PIGEON", sent_at = now,
                lead_hours = 48, validation_snapshot = "{}", updated_at = now,
            )
        }
        database.noticeQueries.insert(
            id = uuid.next(), inspection_id = inspectionId, full_text = "notice", generated_at = now,
            scheduled_at = now + 172_800_000L, sent_via = null, sent_at = null,
            lead_hours = 48, validation_snapshot = "{}", updated_at = now,
        )
        assertEquals(
            1, database.noticeQueries.selectByInspection(inspectionId).executeAsList().size,
            "a generated-but-unsent notice (both delivery columns NULL) must remain insertable",
        )
    }

    @Test
    fun `property_item_override rejects a non-boolean suppressed flag`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        assertCheckViolation("a suppressed flag outside 0/1") {
            database.propertyItemOverrideQueries.insert(
                id = uuid.next(), property_id = propertyId, stable_id = "wall.paint", suppressed = 2,
                created_at = now, updated_at = now,
            )
        }
    }

    @Test
    fun `inspection rejects an unknown status value`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val ex = assertFailsWith<Exception>("an unrecognised status must violate the CHECK constraint") {
            database.inspectionQueries.insert(
                id = uuid.next(), type = "ROUTINE", property_id = propertyId, tenancy_id = null,
                template_version_id = templateVersionId, scheduled_at = now, previous_inspection_id = null,
                baseline_inspection_id = null, status = "BOGUS", finalized_at = null, data_hash = null,
                created_at = now, updated_at = now,
            )
        }
        assertTrue(ex.message.orEmpty().contains("CHECK", ignoreCase = true), "expected a CHECK constraint violation, got: ${ex.message}")
    }

    @Test
    fun `initial baseline accepts only an active INGOING on the same active tenancy and an empty pointer`() {
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, type = "INGOING", now = now)

        fun newTenancy(propertyId: String): String = uuid.next().also { tenancyId ->
            database.tenancyQueries.insert(
                id = tenancyId, property_id = propertyId, start_ms = now - 86_400_000L, end_ms = null,
                tenant_name = "J Doe", contact = "j@example.com", baseline_inspection_id = null,
                created_at = now, updated_at = now,
            )
        }

        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val tenancyId = newTenancy(propertyId)
        val ingoingId = DbTestFixtures.insertDraftInspection(
            database, uuid, propertyId, templateVersionId, tenancyId = tenancyId, type = "INGOING", now = now,
        )
        assertEquals(
            1L,
            database.tenancyQueries.assignInitialIngoingBaseline(ingoingId, now + 1, tenancyId).value,
            "the valid initial INGOING transition must succeed",
        )
        assertEquals(ingoingId, database.tenancyQueries.selectAnyById(tenancyId).executeAsOne().baseline_inspection_id)

        fun assertRejected(
            label: String,
            type: String = "INGOING",
            crossProperty: Boolean = false,
            crossTenancy: Boolean = false,
            deletedInspection: Boolean = false,
            deletedTenancy: Boolean = false,
        ) {
            val targetPropertyId = DbTestFixtures.insertProperty(database, uuid, now)
            val candidatePropertyId = if (crossProperty) DbTestFixtures.insertProperty(database, uuid, now) else targetPropertyId
            val targetTenancyId = newTenancy(targetPropertyId)
            val candidateTenancyId = if (crossTenancy) newTenancy(targetPropertyId) else targetTenancyId
            val candidateId = DbTestFixtures.insertDraftInspection(
                database, uuid, candidatePropertyId, templateVersionId,
                tenancyId = candidateTenancyId, type = type, now = now,
            )
            if (deletedInspection) {
                driver.execute(null, "UPDATE inspection SET deleted_at = ${now + 1} WHERE id = '$candidateId'", 0)
            }
            if (deletedTenancy) {
                driver.execute(null, "UPDATE tenancy SET deleted_at = ${now + 1} WHERE id = '$targetTenancyId'", 0)
            }

            assertEquals(
                0L,
                database.tenancyQueries.assignInitialIngoingBaseline(candidateId, now + 2, targetTenancyId).value,
                label,
            )
            assertNull(database.tenancyQueries.selectAnyById(targetTenancyId).executeAsOne().baseline_inspection_id, label)
        }

        run {
            val targetPropertyId = DbTestFixtures.insertProperty(database, uuid, now)
            val targetTenancyId = newTenancy(targetPropertyId)
            DbTestFixtures.insertDraftInspection(
                database, uuid, targetPropertyId, templateVersionId,
                tenancyId = targetTenancyId, type = "INGOING", now = now,
            )
            val missingCandidateId = uuid.next()

            assertEquals(
                0L,
                database.tenancyQueries.assignInitialIngoingBaseline(missingCandidateId, now + 2, targetTenancyId).value,
                "a qualifying INGOING must not let a different missing candidate id become the baseline",
            )
            assertNull(database.tenancyQueries.selectAnyById(targetTenancyId).executeAsOne().baseline_inspection_id)
        }

        inspectionTypes().filterNot { it == "INGOING" }.forEach { type ->
            assertRejected("$type is not an initial INGOING baseline", type = type)
        }
        assertRejected("a cross-property inspection must be rejected", crossProperty = true)
        assertRejected("a cross-tenancy inspection must be rejected", crossTenancy = true)
        assertRejected("a deleted inspection must be rejected", deletedInspection = true)
        assertRejected("a deleted tenancy must be rejected", deletedTenancy = true)

        val replacementId = DbTestFixtures.insertDraftInspection(
            database, uuid, propertyId, templateVersionId, tenancyId = tenancyId, type = "INGOING", now = now + 3,
        )
        assertEquals(
            0L,
            database.tenancyQueries.assignInitialIngoingBaseline(replacementId, now + 3, tenancyId).value,
            "an existing baseline pointer is immutable",
        )
        assertEquals(ingoingId, database.tenancyQueries.selectAnyById(tenancyId).executeAsOne().baseline_inspection_id)
    }

    @Test
    fun `fallback baseline accepts only a finalized ROUTINE on the same active tenancy and an empty pointer`() {
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)

        fun newTenancy(propertyId: String): String = uuid.next().also { tenancyId ->
            database.tenancyQueries.insert(
                id = tenancyId, property_id = propertyId, start_ms = now - 86_400_000L, end_ms = null,
                tenant_name = "J Doe", contact = "j@example.com", baseline_inspection_id = null,
                created_at = now, updated_at = now,
            )
        }

        fun finalize(inspectionId: String) {
            assertEquals(
                1L,
                database.inspectionQueries.finalizeIfDraft(now + 1, "hash-$inspectionId", now + 1, inspectionId).value,
            )
        }

        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val tenancyId = newTenancy(propertyId)
        val routineInspectionId = DbTestFixtures.insertDraftInspection(
            database, uuid, propertyId, templateVersionId, tenancyId = tenancyId, type = "ROUTINE", now = now,
        )
        finalize(routineInspectionId)
        assertEquals(
            1L,
            database.tenancyQueries.assignFinalizedRoutineFallbackBaseline(routineInspectionId, now + 2, tenancyId).value,
            "the valid finalized ROUTINE fallback must succeed",
        )
        val designatedBaseline = database.tenancyQueries.selectAnyById(tenancyId).executeAsOne().baseline_inspection_id
        assertEquals(routineInspectionId, designatedBaseline)

        // Exercise the historical EXIT consumer shape: it reads the tenancy pointer and persists that exact
        // snapshot on the new EXIT row; it never searches for an INGOING implicitly.
        val exitInspectionId = DbTestFixtures.insertDraftInspection(
            database, uuid, propertyId, templateVersionId, tenancyId = tenancyId, type = "EXIT",
            previousInspectionId = routineInspectionId, baselineInspectionId = designatedBaseline, now = now + 3,
        )
        assertEquals(
            routineInspectionId,
            database.inspectionQueries.selectById(exitInspectionId).executeAsOne().baseline_inspection_id,
        )

        fun assertRejected(
            label: String,
            type: String = "ROUTINE",
            finalized: Boolean = true,
            crossProperty: Boolean = false,
            crossTenancy: Boolean = false,
            deletedInspection: Boolean = false,
            deletedTenancy: Boolean = false,
            existingIngoing: Boolean = false,
            deletedExistingIngoing: Boolean = false,
        ) {
            val targetPropertyId = DbTestFixtures.insertProperty(database, uuid, now)
            val candidatePropertyId = if (crossProperty) DbTestFixtures.insertProperty(database, uuid, now) else targetPropertyId
            val targetTenancyId = newTenancy(targetPropertyId)
            val candidateTenancyId = if (crossTenancy) newTenancy(targetPropertyId) else targetTenancyId
            if (existingIngoing) {
                val existingIngoingId = DbTestFixtures.insertDraftInspection(
                    database, uuid, targetPropertyId, templateVersionId,
                    tenancyId = targetTenancyId, type = "INGOING", now = now,
                )
                if (deletedExistingIngoing) {
                    driver.execute(null, "UPDATE inspection SET deleted_at = ${now + 1} WHERE id = '$existingIngoingId'", 0)
                }
            }
            val candidateId = DbTestFixtures.insertDraftInspection(
                database, uuid, candidatePropertyId, templateVersionId,
                tenancyId = candidateTenancyId, type = type, now = now,
            )
            if (finalized) finalize(candidateId)
            if (deletedInspection) {
                driver.execute(null, "UPDATE inspection SET deleted_at = ${now + 1} WHERE id = '$candidateId'", 0)
            }
            if (deletedTenancy) {
                driver.execute(null, "UPDATE tenancy SET deleted_at = ${now + 1} WHERE id = '$targetTenancyId'", 0)
            }

            assertEquals(
                0L,
                database.tenancyQueries.assignFinalizedRoutineFallbackBaseline(candidateId, now + 2, targetTenancyId).value,
                label,
            )
            assertNull(database.tenancyQueries.selectAnyById(targetTenancyId).executeAsOne().baseline_inspection_id, label)
        }

        run {
            val targetPropertyId = DbTestFixtures.insertProperty(database, uuid, now)
            val targetTenancyId = newTenancy(targetPropertyId)
            val qualifyingId = DbTestFixtures.insertDraftInspection(
                database, uuid, targetPropertyId, templateVersionId,
                tenancyId = targetTenancyId, type = "ROUTINE", now = now,
            )
            finalize(qualifyingId)
            val missingCandidateId = uuid.next()

            assertEquals(
                0L,
                database.tenancyQueries.assignFinalizedRoutineFallbackBaseline(missingCandidateId, now + 2, targetTenancyId).value,
                "a qualifying ROUTINE must not let a different missing candidate id become the baseline",
            )
            assertNull(database.tenancyQueries.selectAnyById(targetTenancyId).executeAsOne().baseline_inspection_id)
        }

        inspectionTypes().filterNot { it == "ROUTINE" }.forEach { type ->
            assertRejected("$type is not a ROUTINE fallback", type = type)
        }
        assertRejected("a DRAFT ROUTINE must be rejected", finalized = false)
        assertRejected("a cross-property ROUTINE must be rejected", crossProperty = true)
        assertRejected("a cross-tenancy ROUTINE must be rejected", crossTenancy = true)
        assertRejected("a deleted ROUTINE must be rejected", deletedInspection = true)
        assertRejected("a deleted tenancy must be rejected", deletedTenancy = true)
        assertRejected("an active INGOING history disqualifies fallback", existingIngoing = true)
        assertRejected(
            "a soft-deleted INGOING remains history and disqualifies fallback",
            existingIngoing = true,
            deletedExistingIngoing = true,
        )

        fun assertAllowedWithUnrelatedIngoing(label: String, crossProperty: Boolean, crossTenancy: Boolean) {
            val targetPropertyId = DbTestFixtures.insertProperty(database, uuid, now)
            val otherPropertyId = if (crossProperty) DbTestFixtures.insertProperty(database, uuid, now) else targetPropertyId
            val targetTenancyId = newTenancy(targetPropertyId)
            val otherTenancyId = if (crossTenancy) newTenancy(targetPropertyId) else targetTenancyId
            DbTestFixtures.insertDraftInspection(
                database, uuid, otherPropertyId, templateVersionId,
                tenancyId = otherTenancyId, type = "INGOING", now = now,
            )
            val candidateId = DbTestFixtures.insertDraftInspection(
                database, uuid, targetPropertyId, templateVersionId,
                tenancyId = targetTenancyId, type = "ROUTINE", now = now,
            )
            finalize(candidateId)

            assertEquals(
                1L,
                database.tenancyQueries.assignFinalizedRoutineFallbackBaseline(candidateId, now + 2, targetTenancyId).value,
                label,
            )
            assertEquals(candidateId, database.tenancyQueries.selectAnyById(targetTenancyId).executeAsOne().baseline_inspection_id)
        }

        assertAllowedWithUnrelatedIngoing(
            "an INGOING on another property does not disqualify this tenancy",
            crossProperty = true,
            crossTenancy = false,
        )
        assertAllowedWithUnrelatedIngoing(
            "an INGOING on another tenancy does not disqualify this tenancy",
            crossProperty = false,
            crossTenancy = true,
        )

        val replacementId = DbTestFixtures.insertDraftInspection(
            database, uuid, propertyId, templateVersionId, tenancyId = tenancyId, type = "ROUTINE", now = now + 3,
        )
        finalize(replacementId)
        assertEquals(
            0L,
            database.tenancyQueries.assignFinalizedRoutineFallbackBaseline(replacementId, now + 4, tenancyId).value,
            "an existing baseline pointer is immutable",
        )
        assertEquals(routineInspectionId, database.tenancyQueries.selectAnyById(tenancyId).executeAsOne().baseline_inspection_id)
    }

    @Test
    fun `purgeContactInfo clears contact fields, keeps the row, and always records a timestamp`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val tenancyId = uuid.next()
        database.tenancyQueries.insert(
            id = tenancyId, property_id = propertyId, start_ms = now - 86_400_000L, end_ms = now,
            tenant_name = "J Doe", contact = "j@example.com", baseline_inspection_id = null, created_at = now, updated_at = now,
        )

        database.tenancyQueries.purgeContactInfo(purged_at = now + 1, updated_at = now + 1, id = tenancyId)

        val purged = database.tenancyQueries.selectById(tenancyId).executeAsOne()
        assertNull(purged.tenant_name, "tenant_name must be cleared")
        assertNull(purged.contact, "contact must be cleared")
        assertEquals(now + 1, purged.purged_at, "purged_at must be recorded for a real purge call")
    }

    @Test
    fun `purged tenancy contact fields are a terminal database state`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val tenancyId = uuid.next()
        database.tenancyQueries.insert(
            id = tenancyId, property_id = propertyId, start_ms = now - 86_400_000L, end_ms = now,
            tenant_name = "J Doe", contact = "j@example.com", baseline_inspection_id = null,
            created_at = now, updated_at = now,
        )
        assertEquals(1L, database.tenancyQueries.purgeContactInfo(now + 1, now + 1, tenancyId).value)

        listOf(
            "tenant_name = 'Restored'" to "tenant_name",
            "contact = 'restored@example.com'" to "contact",
        ).forEach { (assignment, field) ->
            val ex = assertFailsWith<Exception> {
                driver.execute(null, "UPDATE tenancy SET $assignment WHERE id = '$tenancyId'", 0)
            }
            assertTrue(ex.message.orEmpty().contains("CHECK", ignoreCase = true), "expected $field backfill to fail the terminal CHECK")
            val row = database.tenancyQueries.selectAnyById(tenancyId).executeAsOne()
            assertNull(row.tenant_name)
            assertNull(row.contact)
            assertEquals(now + 1, row.purged_at)
        }
    }

    @Test
    fun `v2 to v3 migration preserves every valid tenancy field and recreates its indexes`() {
        val v2Driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            createV2TenancySchema(v2Driver)
            v2Driver.execute(
                null,
                """INSERT INTO tenancy (
                    id, property_id, start_ms, end_ms, tenant_name, contact, baseline_inspection_id,
                    purged_at, created_at, updated_at, deleted_at
                ) VALUES ('tenancy-1', 'property-1', 1, 2, 'J Doe', 'j@example.com', 'inspection-1', NULL, 3, 4, 5)""".trimIndent(),
                0,
            )

            MyInspectionDatabase.Schema.migrate(v2Driver, 2, 3)

            val row = v2Driver.executeQuery(
                null,
                """SELECT id, property_id, start_ms, end_ms, tenant_name, contact,
                    baseline_inspection_id, purged_at, created_at, updated_at, deleted_at
                    FROM tenancy""".trimIndent(),
                { cursor ->
                    check(cursor.next().value)
                    QueryResult.Value(
                        listOf(
                            cursor.getString(0), cursor.getString(1), cursor.getLong(2), cursor.getLong(3),
                            cursor.getString(4), cursor.getString(5), cursor.getString(6), cursor.getLong(7),
                            cursor.getLong(8), cursor.getLong(9), cursor.getLong(10),
                        ),
                    )
                },
                0,
            ).value
            assertEquals(
                listOf("tenancy-1", "property-1", 1L, 2L, "J Doe", "j@example.com", "inspection-1", null, 3L, 4L, 5L),
                row,
            )

            val indexes = v2Driver.executeQuery(
                null,
                "PRAGMA index_list(tenancy)",
                { cursor ->
                    val names = mutableSetOf<String>()
                    while (cursor.next().value) names += cursor.getString(1)!!
                    QueryResult.Value(names)
                },
                0,
            ).value
            assertTrue(indexes.contains("idx_tenancy_property"))
            assertTrue(indexes.contains("idx_tenancy_baseline_inspection"))
        } finally {
            v2Driver.close()
        }
    }

    @Test
    fun `v2 to v3 migration fails closed on an already-inconsistent purged tenancy`() {
        fun assertMigrationRejected(tenantNameSql: String, contactSql: String, field: String) {
            val v2Driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            try {
                createV2TenancySchema(v2Driver)
                v2Driver.execute(
                    null,
                    """INSERT INTO tenancy (
                        id, property_id, start_ms, end_ms, tenant_name, contact, baseline_inspection_id,
                        purged_at, created_at, updated_at, deleted_at
                    ) VALUES ('tenancy-1', 'property-1', 1, 2, $tenantNameSql, $contactSql, NULL, 3, 1, 1, NULL)""".trimIndent(),
                    0,
                )

                val ex = assertFailsWith<Exception> {
                    MyInspectionDatabase.Schema.migrate(v2Driver, 2, 3)
                }
                assertTrue(ex.message.orEmpty().contains("CHECK", ignoreCase = true), "migration must reject retained $field")
            } finally {
                v2Driver.close()
            }
        }

        assertMigrationRejected("'J Doe'", "NULL", "tenant_name")
        assertMigrationRejected("NULL", "'j@example.com'", "contact")
    }
}

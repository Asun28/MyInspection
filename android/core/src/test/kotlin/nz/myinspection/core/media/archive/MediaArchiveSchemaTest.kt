package nz.myinspection.core.media.archive

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import nz.myinspection.core.db.MyInspectionDatabase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Real-SQL contract for the reviewed additive v4 -> v5 archive schema. */
class MediaArchiveSchemaTest {
    private lateinit var driver: JdbcSqliteDriver

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MyInspectionDatabase.Schema.create(driver)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `schema v5 exposes four exact text-and-integer tables`() {
        assertEquals(5L, MyInspectionDatabase.Schema.version)
        assertTable(
            "local_asset_state",
            listOf("rel_path", "content_hash", "byte_size", "state", "changed_at", "reason"),
        )
        assertTable(
            "report_export_receipt",
            listOf("id", "inspection_id", "audience", "quality", "rel_path", "content_hash", "byte_size", "exported_at"),
        )
        assertTable(
            "verified_backup_receipt",
            listOf(
                "id", "destination_kind", "destination_ref", "object_ref", "version_ref",
                "exported_at", "verified_at", "scope_kind", "scope_property_id", "revoked_at",
            ),
        )
        assertTable(
            "verified_backup_receipt_entry",
            listOf("receipt_id", "rel_path", "content_hash", "byte_size"),
        )
    }

    @Test
    fun `asset state rejects open-domain and incomplete rows`() {
        listOf("PRESENT", "ARCHIVED", "RESTORING").forEachIndexed { index, state ->
            insertState("media/$index.jpg", state = state)
        }
        assertEquals(3L, count("local_asset_state"))

        assertFailsWith<Exception> { insertState("media/lower.jpg", state = "present") }
        assertFailsWith<Exception> { insertState("media/space.jpg", state = "ARCHIVED ") }
        assertFailsWith<Exception> { insertState("media/negative.jpg", state = "PRESENT", byteSize = -1) }
        assertFailsWith<Exception> { insertState("media/empty-reason.jpg", state = "PRESENT", reason = "") }
    }

    @Test
    fun `report audience quality and tuple are closed`() {
        insertReport("r1", "inspection-1", "LANDLORD", "LOW")
        insertReport("r2", "inspection-1", "TENANT", "EXTRA_HIGH")
        assertFailsWith<Exception> { insertReport("r3", "inspection-2", "OWNER", "LOW") }
        assertFailsWith<Exception> { insertReport("r4", "inspection-2", "LANDLORD", "ULTRA") }
        assertFailsWith<Exception> { insertReport("r5", "inspection-1", "LANDLORD", "LOW") }
        assertEquals(2L, count("report_export_receipt"))
    }

    @Test
    fun `backup receipt requires six core fields but version remains nullable`() {
        val fields = listOf(
            "destination_kind", "destination_ref", "object_ref", "exported_at", "verified_at", "scope_kind",
        )
        fields.forEachIndexed { index, field ->
            assertFailsWith<Exception>("$field must be required") {
                driver.execute(
                    null,
                    """INSERT INTO verified_backup_receipt (
                        id, destination_kind, destination_ref, object_ref, version_ref,
                        exported_at, verified_at, scope_kind, scope_property_id, revoked_at
                    ) VALUES ('missing-$index', 'SAF', 'tree://root', 'backup.mibk', NULL,
                        1700000000000, 1700000000000, 'full', NULL, NULL)""".trimIndent()
                        .replace("'$field'", "'$field'"),
                    0,
                )
                driver.execute(null, "UPDATE verified_backup_receipt SET $field = NULL WHERE id = 'missing-$index'", 0)
            }
        }

        insertReceipt("nullable-version", versionRef = null, scopeKind = "full", propertyId = null)
        assertNull(
            driver.executeQuery(
                null,
                "SELECT version_ref FROM verified_backup_receipt WHERE id = 'nullable-version'",
                { cursor ->
                    cursor.next()
                    QueryResult.Value(cursor.getString(0))
                },
                0,
            ).value,
        )
    }

    @Test
    fun `scope shape and entry tuple fail closed`() {
        insertReceipt("full", scopeKind = "full", propertyId = null)
        insertReceipt("property", scopeKind = "property", propertyId = "property-1")
        assertFailsWith<Exception> { insertReceipt("unknown", scopeKind = "account", propertyId = null) }
        assertFailsWith<Exception> { insertReceipt("full-with-property", scopeKind = "full", propertyId = "property-1") }
        assertFailsWith<Exception> { insertReceipt("property-without-id", scopeKind = "property", propertyId = null) }
        assertFailsWith<Exception> { insertReceipt("property-empty-id", scopeKind = "property", propertyId = "") }

        insertEntry("full", "media/a.jpg", byteSize = 100)
        assertFailsWith<Exception> { insertEntry("full", "media/a.jpg", byteSize = 101) }
        assertFailsWith<Exception> { insertEntry("full", "media/b.jpg", byteSize = -1) }
        assertEquals(1L, count("verified_backup_receipt_entry"))
    }

    @Test
    fun `generated query surface preserves identity and deterministic order`() {
        val queries = MyInspectionDatabase(driver).mediaArchiveQueries

        assertEquals(1L, queries.insertLocalAssetState("media/a.jpg", HASH_A, 100, "PRESENT", NOW, "captured").value)
        assertEquals(0L, queries.insertLocalAssetState("media/a.jpg", HASH_B, 101, "ARCHIVED", NOW + 1, "wrong identity").value)
        assertEquals(
            0L,
            queries.updateLocalAssetStateIfIdentityMatches(
                state = "ARCHIVED", changed_at = NOW + 2, reason = "mismatch",
                rel_path = "media/a.jpg", content_hash = HASH_B, byte_size = 100,
            ).value,
        )
        assertEquals(
            1L,
            queries.updateLocalAssetStateIfIdentityMatches(
                state = "ARCHIVED", changed_at = NOW + 3, reason = "archived",
                rel_path = "media/a.jpg", content_hash = HASH_A, byte_size = 100,
            ).value,
        )
        val state = queries.selectLocalAssetStateByPath("media/a.jpg").executeAsOne()
        assertEquals(listOf(HASH_A, 100L, "ARCHIVED", NOW + 3), listOf(state.content_hash, state.byte_size, state.state, state.changed_at))
        assertEquals(listOf("media/a.jpg"), queries.selectArchivedAssetStates().executeAsList().map { it.rel_path })

        assertEquals(1L, queries.insertReportExportReceipt("report-b", "inspection-1", "TENANT", "HIGH", "b.pdf", HASH_B, 20, NOW).value)
        assertEquals(1L, queries.insertReportExportReceipt("report-a", "inspection-1", "LANDLORD", "LOW", "a.pdf", HASH_A, 10, NOW).value)
        assertEquals(listOf("report-a", "report-b"), queries.selectReportExportReceiptsByInspection("inspection-1").executeAsList().map { it.id })

        assertEquals(
            1L,
            queries.insertVerifiedBackupReceipt(
                id = "receipt-1", destination_kind = "SAF", destination_ref = "tree://root",
                object_ref = "backup.mibk", version_ref = null, exported_at = NOW, verified_at = NOW,
                scope_kind = "full", scope_property_id = null, revoked_at = null,
            ).value,
        )
        assertEquals(1L, queries.insertVerifiedBackupReceiptEntry("receipt-1", "media/a.jpg", HASH_A, 100).value)
        assertEquals(listOf("media/a.jpg"), queries.selectVerifiedBackupReceiptEntries("receipt-1").executeAsList().map { it.rel_path })
        assertEquals(listOf("receipt-1"), queries.selectCandidateReceiptEntriesByPath("media/a.jpg").executeAsList().map { it.receipt_id })
        assertEquals(1L, queries.revokeVerifiedBackupReceipt(NOW, "receipt-1").value)
        assertEquals(NOW, queries.selectVerifiedBackupReceiptById("receipt-1").executeAsOne().revoked_at)

        seedOwnedPhoto("property-1", "inspection-1", "room-1", "photo-1", HASH_A)
        seedOwnedPhoto("property-2", "inspection-2", "room-2", "photo-2", HASH_B)
        seedOwnedPhoto("property-3", "inspection-3", "room-3", "photo-3", HASH_A, deletedPhoto = true)
        seedOwnedPhoto("property-4", "inspection-4", "room-4", "photo-4", HASH_A, deletedRoom = true)
        seedOwnedPhoto("property-5", "inspection-5", "room-5", "photo-5", HASH_A, deletedInspection = true)
        assertEquals(
            listOf("property-1" to HASH_A, "property-2" to HASH_B),
            queries.selectActiveAssetIdentitiesByPath("media/a.jpg").executeAsList().map { it.property_id to it.content_hash },
        )
        assertEquals(listOf("media/a.jpg"), queries.selectAllLocalAssetStates().executeAsList().map { it.rel_path })
        assertEquals(listOf("report-a", "report-b"), queries.selectAllReportExportReceipts().executeAsList().map { it.id })
        assertEquals(listOf("receipt-1"), queries.selectAllVerifiedBackupReceipts().executeAsList().map { it.id })
        assertEquals(listOf("receipt-1|media/a.jpg"), queries.selectAllVerifiedBackupReceiptEntries().executeAsList().map { "${it.receipt_id}|${it.rel_path}" })
        assertEquals(1L, queries.deleteReportExportReceipt("report-a").value)
        assertEquals(listOf("report-b"), queries.selectReportExportReceiptsByInspection("inspection-1").executeAsList().map { it.id })
    }

    @Test
    fun `migration from v4 preserves finalized evidence rows and columns`() {
        driver.close()
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        createV4EvidenceTables()
        val photoColumns = columnInfo("photo")
        val inspectionColumns = columnInfo("inspection")
        val photoBefore = rowStrings("SELECT * FROM photo ORDER BY id", 11)
        val inspectionBefore = rowStrings("SELECT * FROM inspection ORDER BY id", 14)

        MyInspectionDatabase.Schema.migrate(driver, 4, 5)

        assertEquals(photoColumns, columnInfo("photo"))
        assertEquals(inspectionColumns, columnInfo("inspection"))
        assertEquals(photoBefore, rowStrings("SELECT * FROM photo ORDER BY id", 11))
        assertEquals(inspectionBefore, rowStrings("SELECT * FROM inspection ORDER BY id", 14))
        assertEquals(4L, listOf("local_asset_state", "report_export_receipt", "verified_backup_receipt", "verified_backup_receipt_entry").count { columnInfo(it).isNotEmpty() }.toLong())
    }

    private fun assertTable(table: String, expectedColumns: List<String>) {
        val info = columnInfo(table)
        assertEquals(expectedColumns, info.map { it.first }, "$table column set/order drifted")
        assertEquals(setOf("TEXT", "INTEGER"), info.map { it.second }.toSet(), "$table may contain only TEXT/INTEGER")
    }

    private fun columnInfo(table: String): List<Pair<String, String>> =
        driver.executeQuery(null, "PRAGMA table_info($table)", { cursor ->
            val rows = mutableListOf<Pair<String, String>>()
            while (cursor.next().value) rows += cursor.getString(1)!! to cursor.getString(2)!!
            QueryResult.Value(rows)
        }, 0).value

    private fun rowStrings(sql: String, columnCount: Int): List<String> =
        driver.executeQuery(null, sql, { cursor ->
            val rows = mutableListOf<String>()
            while (cursor.next().value) {
                val columns = (0 until columnCount).joinToString("|") { index ->
                    cursor.getString(index) ?: cursor.getLong(index)?.toString() ?: "NULL"
                }
                rows += columns
            }
            QueryResult.Value(rows)
        }, 0).value

    private fun seedOwnedPhoto(
        propertyId: String,
        inspectionId: String,
        roomId: String,
        photoId: String,
        hash: String,
        deletedPhoto: Boolean = false,
        deletedRoom: Boolean = false,
        deletedInspection: Boolean = false,
    ) {
        val templateId = "template-$propertyId"
        val templateVersion = propertyId.substringAfterLast('-').toInt()
        driver.execute(null, "INSERT INTO property (id,address,kind,is_boarding_house,created_at,updated_at) VALUES ('$propertyId','1 Test St','RENTAL',0,1,1)", 0)
        driver.execute(null, "INSERT INTO template_version (id,type,version,content_hash,created_at,updated_at) VALUES ('$templateId','ROUTINE',$templateVersion,'template-hash-$propertyId',1,1)", 0)
        driver.execute(null, "INSERT INTO inspection (id,type,property_id,template_version_id,scheduled_at,status,created_at,updated_at) VALUES ('$inspectionId','ROUTINE','$propertyId','$templateId',1,'DRAFT',1,1)", 0)
        driver.execute(null, "INSERT INTO room_instance (id,inspection_id,room_key,instance_no,display_label,created_at,updated_at) VALUES ('$roomId','$inspectionId','BEDROOM',1,'Bedroom',1,1)", 0)
        driver.execute(null, "INSERT INTO photo (id,room_instance_id,rel_path,content_hash,source,privacy_flag,created_at,updated_at) VALUES ('$photoId','$roomId','media/a.jpg','$hash','CAMERA',0,1,1)", 0)
        if (deletedPhoto) driver.execute(null, "UPDATE photo SET deleted_at = 9 WHERE id = '$photoId'", 0)
        if (deletedRoom) driver.execute(null, "UPDATE room_instance SET deleted_at = 9 WHERE id = '$roomId'", 0)
        if (deletedInspection) driver.execute(null, "UPDATE inspection SET deleted_at = 9 WHERE id = '$inspectionId'", 0)
    }

    private fun createV4EvidenceTables() {
        driver.execute(
            null,
            """CREATE TABLE inspection (
                id TEXT NOT NULL PRIMARY KEY, type TEXT NOT NULL, property_id TEXT NOT NULL, tenancy_id TEXT,
                template_version_id TEXT NOT NULL, scheduled_at INTEGER NOT NULL, previous_inspection_id TEXT,
                baseline_inspection_id TEXT, status TEXT NOT NULL, finalized_at INTEGER, data_hash TEXT,
                created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER
            )""".trimIndent(),
            0,
        )
        driver.execute(
            null,
            """CREATE TABLE photo (
                id TEXT NOT NULL PRIMARY KEY, inspection_item_id TEXT, room_instance_id TEXT NOT NULL,
                rel_path TEXT NOT NULL, content_hash TEXT NOT NULL, exif_time_ms INTEGER, source TEXT NOT NULL,
                privacy_flag INTEGER NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
                deleted_at INTEGER
            )""".trimIndent(),
            0,
        )
        driver.execute(null, "INSERT INTO inspection VALUES ('inspection-1','ROUTINE','property-1',NULL,'template-1',1,NULL,NULL,'FINALIZED',2,'data-hash',1,2,NULL)", 0)
        driver.execute(null, "INSERT INTO photo VALUES ('photo-1',NULL,'room-1','media/a.jpg','$HASH_A',NULL,'CAMERA',0,1,1,NULL)", 0)
    }

    private fun insertState(
        relPath: String,
        state: String,
        byteSize: Long = 100,
        reason: String = "a",
    ) {
        driver.execute(
            null,
            "INSERT INTO local_asset_state VALUES (?, ?, ?, ?, ?, ?)",
            6,
        ) {
            bindString(0, relPath)
            bindString(1, HASH_A)
            bindLong(2, byteSize)
            bindString(3, state)
            bindLong(4, NOW)
            bindString(5, reason)
        }
    }

    private fun insertReport(id: String, inspectionId: String, audience: String, quality: String) {
        driver.execute(
            null,
            "INSERT INTO report_export_receipt VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            8,
        ) {
            bindString(0, id)
            bindString(1, inspectionId)
            bindString(2, audience)
            bindString(3, quality)
            bindString(4, "reports/$id.pdf")
            bindString(5, HASH_A)
            bindLong(6, 200)
            bindLong(7, NOW)
        }
    }

    private fun insertReceipt(
        id: String,
        versionRef: String? = "v1",
        scopeKind: String,
        propertyId: String?,
    ) {
        driver.execute(
            null,
            "INSERT INTO verified_backup_receipt VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)",
            9,
        ) {
            bindString(0, id)
            bindString(1, "SAF")
            bindString(2, "tree://root")
            bindString(3, "$id.mibk")
            bindString(4, versionRef)
            bindLong(5, NOW)
            bindLong(6, NOW)
            bindString(7, scopeKind)
            bindString(8, propertyId)
        }
    }

    private fun insertEntry(receiptId: String, relPath: String, byteSize: Long) {
        driver.execute(
            null,
            "INSERT INTO verified_backup_receipt_entry VALUES (?, ?, ?, ?)",
            4,
        ) {
            bindString(0, receiptId)
            bindString(1, relPath)
            bindString(2, HASH_A)
            bindLong(3, byteSize)
        }
    }

    private fun count(table: String): Long =
        driver.executeQuery(null, "SELECT COUNT(*) FROM $table", { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getLong(0)!!)
        }, 0).value

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val HASH_B = "baaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}

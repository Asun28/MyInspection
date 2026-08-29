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
        val queries = MyInspectionDatabase(driver).mediaArchiveQueries
        listOf("PRESENT", "ARCHIVED", "RESTORING").forEachIndexed { index, state ->
            assertEquals(1L, queries.insertLocalAssetState("media/$index.jpg", HASH_A, 100, state, NOW, "a").value)
        }
        assertEquals(3L, count("local_asset_state"))
        assertFailsWith<Exception> {
            queries.insertLocalAssetState("media/0.jpg", HASH_B, 101, "ARCHIVED", NOW + 1, "valid duplicate key").value
        }
        assertEquals(3L, count("local_asset_state"))

        assertFailsWith<Exception> { queries.insertLocalAssetState("", HASH_A, 100, "PRESENT", NOW, "a").value }
        assertFailsWith<Exception> { queries.insertLocalAssetState("media/empty-hash.jpg", "", 100, "PRESENT", NOW, "a").value }
        assertFailsWith<Exception> { queries.insertLocalAssetState("media/lower.jpg", HASH_A, 100, "present", NOW, "a").value }
        assertFailsWith<Exception> { queries.insertLocalAssetState("media/space.jpg", HASH_A, 100, "ARCHIVED ", NOW, "a").value }
        assertFailsWith<Exception> { queries.insertLocalAssetState("media/negative.jpg", HASH_A, -1, "PRESENT", NOW, "a").value }
        assertFailsWith<Exception> { queries.insertLocalAssetState("media/empty-reason.jpg", HASH_A, 100, "PRESENT", NOW, "").value }
        assertFailsWith<Exception> { queries.insertLocalAssetState("media/0.jpg", "", 100, "PRESENT", NOW, "a").value }
        assertEquals(3L, count("local_asset_state"), "invalid duplicate input must not be mistaken for a valid path conflict")
    }

    @Test
    fun `report audience quality and tuple are closed`() {
        listOf("LOW", "MEDIUM", "HIGH", "EXTRA_HIGH").forEachIndexed { index, quality ->
            insertReport("r-landlord-$index", "inspection-1", "LANDLORD", quality)
        }
        insertReport("r-tenant", "inspection-1", "TENANT", "LOW")
        assertFailsWith<Exception> { insertReport("r3", "inspection-2", "OWNER", "LOW") }
        assertFailsWith<Exception> { insertReport("r4", "inspection-2", "LANDLORD", "ULTRA") }
        assertFailsWith<Exception> { insertReport("r5", "inspection-1", "LANDLORD", "LOW") }
        assertFailsWith<Exception> { insertReport("", "inspection-3", "LANDLORD", "LOW") }
        assertFailsWith<Exception> { insertReport("r6", "", "LANDLORD", "LOW") }
        assertFailsWith<Exception> { insertReport("r7", "inspection-3", "LANDLORD", "LOW", relPath = "") }
        assertFailsWith<Exception> { insertReport("r8", "inspection-3", "LANDLORD", "LOW", hash = "") }
        assertFailsWith<Exception> { insertReport("r9", "inspection-3", "LANDLORD", "LOW", byteSize = -1) }
        assertFailsWith<Exception> { insertReport("r10", "inspection-3", "LANDLORD", "LOW", exportedAt = 0) }
        assertFailsWith<Exception> { insertReport("r11", "inspection-3", "LANDLORD", "LOW", exportedAt = -1) }
        assertFailsWith<Exception> { insertReport("r-landlord-0", "inspection-9", "TENANT", "HIGH") }
        assertEquals(5L, count("report_export_receipt"))
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
                        1700000000000, 1700000000000, 'full', NULL, NULL)""".trimIndent(),
                    0,
                )
                driver.execute(null, "UPDATE verified_backup_receipt SET $field = NULL WHERE id = 'missing-$index'", 0)
            }
        }

        assertFailsWith<Exception> { insertReceipt("", scopeKind = "full", propertyId = null) }
        assertFailsWith<Exception> { insertReceipt("empty-kind", destinationKind = "", scopeKind = "full", propertyId = null) }
        assertFailsWith<Exception> { insertReceipt("empty-ref", destinationRef = "", scopeKind = "full", propertyId = null) }
        assertFailsWith<Exception> { insertReceipt("empty-object", objectRef = "", scopeKind = "full", propertyId = null) }
        assertFailsWith<Exception> { insertReceipt("empty-version", versionRef = "", scopeKind = "full", propertyId = null) }
        assertFailsWith<Exception> { insertReceipt("zero-exported", exportedAt = 0, scopeKind = "full", propertyId = null) }
        assertFailsWith<Exception> { insertReceipt("negative-exported", exportedAt = -1, scopeKind = "full", propertyId = null) }
        assertFailsWith<Exception> { insertReceipt("zero-verified", verifiedAt = 0, scopeKind = "full", propertyId = null) }
        assertFailsWith<Exception> { insertReceipt("negative-verified", verifiedAt = -1, scopeKind = "full", propertyId = null) }

        insertReceipt("nullable-version", versionRef = null, scopeKind = "full", propertyId = null)
        assertFailsWith<Exception> {
            insertReceipt("nullable-version", objectRef = "another.mibk", scopeKind = "full", propertyId = null)
        }
        assertEquals(fields.size.toLong() + 1, count("verified_backup_receipt"))
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
        assertFailsWith<Exception> { insertEntry("", "media/no-receipt.jpg", byteSize = 1) }
        assertFailsWith<Exception> { insertEntry("full", "", byteSize = 1) }
        assertFailsWith<Exception> { insertEntry("full", "media/no-hash.jpg", byteSize = 1, hash = "") }
        assertFailsWith<Exception> { insertEntry("full", "media/b.jpg", byteSize = -1) }
        assertEquals(1L, count("verified_backup_receipt_entry"))
    }

    @Test
    fun `local state queries preserve identity filters and deterministic order`() {
        val queries = MyInspectionDatabase(driver).mediaArchiveQueries

        assertEquals(1L, queries.insertLocalAssetState("media/z.jpg", HASH_A, 100, "ARCHIVED", NOW, "captured").value)
        assertEquals(1L, queries.insertLocalAssetState("media/y.jpg", HASH_B, 300, "ARCHIVED", NOW, "captured").value)
        assertEquals(1L, queries.insertLocalAssetState("media/a.jpg", HASH_A, 100, "ARCHIVED", NOW, "captured").value)
        assertEquals(1L, queries.insertLocalAssetState("media/m.jpg", HASH_B, 200, "PRESENT", NOW, "captured").value)
        assertEquals(1L, queries.insertLocalAssetState("media/b.jpg", HASH_B, 100, "RESTORING", NOW, "restore").value)
        assertFailsWith<Exception> {
            queries.insertLocalAssetState("media/z.jpg", HASH_B, 101, "ARCHIVED", NOW + 1, "wrong identity").value
        }
        assertEquals(
            0L,
            queries.updateLocalAssetStateIfIdentityMatches(
                state = "PRESENT", changed_at = NOW + 1, reason = "wrong path",
                rel_path = "media/missing.jpg", content_hash = HASH_A, byte_size = 100,
            ).value,
        )
        assertEquals(
            0L,
            queries.updateLocalAssetStateIfIdentityMatches(
                state = "PRESENT", changed_at = NOW + 2, reason = "wrong hash",
                rel_path = "media/z.jpg", content_hash = HASH_B, byte_size = 100,
            ).value,
        )
        assertEquals(
            0L,
            queries.updateLocalAssetStateIfIdentityMatches(
                state = "PRESENT", changed_at = NOW + 3, reason = "wrong size",
                rel_path = "media/z.jpg", content_hash = HASH_A, byte_size = 101,
            ).value,
        )
        assertEquals(
            1L,
            queries.updateLocalAssetStateIfIdentityMatches(
                state = "PRESENT", changed_at = NOW + 4, reason = "returned",
                rel_path = "media/z.jpg", content_hash = HASH_A, byte_size = 100,
            ).value,
        )
        val state = queries.selectLocalAssetStateByPath("media/z.jpg").executeAsOne()
        assertEquals(listOf(HASH_A, 100L, "PRESENT", NOW + 4), listOf(state.content_hash, state.byte_size, state.state, state.changed_at))
        assertEquals(listOf("media/a.jpg", "media/y.jpg"), queries.selectArchivedAssetStates().executeAsList().map { it.rel_path })
        assertEquals(
            listOf("media/a.jpg", "media/b.jpg", "media/m.jpg", "media/y.jpg", "media/z.jpg"),
            queries.selectAllLocalAssetStates().executeAsList().map { it.rel_path },
        )
    }

    @Test
    fun `report and backup receipt queries filter join revoke and order`() {
        val queries = MyInspectionDatabase(driver).mediaArchiveQueries

        insertReport("report-tenant", "inspection-1", "TENANT", "LOW")
        insertReport("report-extra", "inspection-1", "LANDLORD", "EXTRA_HIGH")
        insertReport("report-high", "inspection-1", "LANDLORD", "HIGH")
        insertReport("report-low", "inspection-1", "LANDLORD", "LOW")
        insertReport("report-medium", "inspection-1", "LANDLORD", "MEDIUM")
        insertReport("report-other", "inspection-2", "LANDLORD", "LOW")
        assertEquals(
            listOf("report-low", "report-medium", "report-high", "report-extra", "report-tenant"),
            queries.selectReportExportReceiptsByInspection("inspection-1").executeAsList().map { it.id },
        )
        assertEquals(
            listOf("report-extra", "report-high", "report-low", "report-medium", "report-other", "report-tenant"),
            queries.selectAllReportExportReceipts().executeAsList().map { it.id },
        )

        insertReceipt("b-new", verifiedAt = NOW, scopeKind = "full", propertyId = null)
        insertReceipt("z-old", verifiedAt = NOW - 10, scopeKind = "full", propertyId = null)
        insertReceipt("a-new", verifiedAt = NOW, scopeKind = "full", propertyId = null)
        insertReceipt("other", verifiedAt = NOW + 10, scopeKind = "full", propertyId = null)
        insertEntry("b-new", "media/a.jpg", 100, HASH_B)
        insertEntry("z-old", "media/a.jpg", 100, HASH_A)
        insertEntry("a-new", "media/z.jpg", 1, HASH_A)
        insertEntry("a-new", "media/a.jpg", 100, HASH_A)
        insertEntry("a-new", "media/b.jpg", 1, HASH_A)
        insertEntry("other", "media/other.jpg", 1, HASH_A)
        insertEntry("missing-receipt", "media/a.jpg", 100, HASH_A)
        assertEquals(
            listOf("media/z.jpg", "media/b.jpg", "media/a.jpg"),
            queries.selectVerifiedBackupReceiptEntries("a-new").executeAsList().map { it.rel_path },
        )
        assertEquals(
            listOf("a-new", "b-new", "z-old"),
            queries.selectCandidateReceiptEntriesByPath("media/a.jpg").executeAsList().map { it.receipt_id },
        )
        assertEquals("a-new", queries.selectVerifiedBackupReceiptById("a-new").executeAsOne().id)
        assertEquals(listOf("a-new", "b-new", "other", "z-old"), queries.selectAllVerifiedBackupReceipts().executeAsList().map { it.id })
        assertEquals(
            listOf(
                "a-new|media/a.jpg", "a-new|media/b.jpg", "a-new|media/z.jpg", "b-new|media/a.jpg",
                "missing-receipt|media/a.jpg", "other|media/other.jpg", "z-old|media/a.jpg",
            ),
            queries.selectAllVerifiedBackupReceiptEntries().executeAsList().map { "${it.receipt_id}|${it.rel_path}" },
        )
        assertEquals(1L, queries.revokeVerifiedBackupReceipt(NOW + 1, "a-new").value)
        assertEquals(0L, queries.revokeVerifiedBackupReceipt(NOW + 2, "a-new").value)
        assertEquals(NOW + 1, queries.selectVerifiedBackupReceiptById("a-new").executeAsOne().revoked_at)
        assertNull(queries.selectVerifiedBackupReceiptById("b-new").executeAsOne().revoked_at)
    }

    @Test
    fun `active ownership query filters every join lifecycle and path then sorts`() {
        val queries = MyInspectionDatabase(driver).mediaArchiveQueries

        seedOwnedPhoto("property-2", "inspection-2", "room-2", "photo-2", HASH_B)
        seedOwnedPhoto("property-1", "inspection-1", "room-1", "photo-1", HASH_B)
        insertPhoto("photo-1a", "room-1", "media/a.jpg", HASH_A)
        seedOwnedPhoto("property-3", "inspection-3", "room-3", "photo-3", HASH_A, relPath = "media/other.jpg")
        seedOwnedPhoto("property-4", "inspection-4", "room-4", "photo-4", HASH_A, deletedPhoto = true)
        seedOwnedPhoto("property-5", "inspection-5", "room-5", "photo-5", HASH_A, deletedRoom = true)
        seedOwnedPhoto("property-6", "inspection-6", "room-6", "photo-6", HASH_A, deletedInspection = true)
        assertEquals(
            listOf("property-1" to HASH_A, "property-1" to HASH_B, "property-2" to HASH_B),
            queries.selectActiveAssetIdentitiesByPath("media/a.jpg").executeAsList().map { it.property_id to it.content_hash },
        )
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
        relPath: String = "media/a.jpg",
    ) {
        val templateId = "template-$propertyId"
        val templateVersion = propertyId.substringAfterLast('-').toInt()
        driver.execute(null, "INSERT INTO property (id,address,kind,is_boarding_house,created_at,updated_at) VALUES ('$propertyId','1 Test St','RENTAL',0,1,1)", 0)
        driver.execute(null, "INSERT INTO template_version (id,type,version,content_hash,created_at,updated_at) VALUES ('$templateId','ROUTINE',$templateVersion,'template-hash-$propertyId',1,1)", 0)
        driver.execute(null, "INSERT INTO inspection (id,type,property_id,template_version_id,scheduled_at,status,created_at,updated_at) VALUES ('$inspectionId','ROUTINE','$propertyId','$templateId',1,'DRAFT',1,1)", 0)
        driver.execute(null, "INSERT INTO room_instance (id,inspection_id,room_key,instance_no,display_label,created_at,updated_at) VALUES ('$roomId','$inspectionId','BEDROOM',1,'Bedroom',1,1)", 0)
        insertPhoto(photoId, roomId, relPath, hash)
        if (deletedPhoto) driver.execute(null, "UPDATE photo SET deleted_at = 9 WHERE id = '$photoId'", 0)
        if (deletedRoom) driver.execute(null, "UPDATE room_instance SET deleted_at = 9 WHERE id = '$roomId'", 0)
        if (deletedInspection) driver.execute(null, "UPDATE inspection SET deleted_at = 9 WHERE id = '$inspectionId'", 0)
    }

    private fun insertPhoto(photoId: String, roomId: String, relPath: String, hash: String) {
        driver.execute(
            null,
            "INSERT INTO photo (id,room_instance_id,rel_path,content_hash,source,privacy_flag,created_at,updated_at) VALUES (?, ?, ?, ?, 'CAMERA', 0, 1, 1)",
            4,
        ) {
            bindString(0, photoId)
            bindString(1, roomId)
            bindString(2, relPath)
            bindString(3, hash)
        }
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

    private fun insertReport(
        id: String,
        inspectionId: String,
        audience: String,
        quality: String,
        relPath: String = "reports/$id.pdf",
        hash: String = HASH_A,
        byteSize: Long = 200,
        exportedAt: Long = NOW,
    ): Long = MyInspectionDatabase(driver).mediaArchiveQueries.insertReportExportReceipt(
        id, inspectionId, audience, quality, relPath, hash, byteSize, exportedAt,
    ).value

    private fun insertReceipt(
        id: String,
        versionRef: String? = "v1",
        scopeKind: String,
        propertyId: String?,
        destinationKind: String = "SAF",
        destinationRef: String = "tree://root",
        objectRef: String = "$id.mibk",
        exportedAt: Long = NOW,
        verifiedAt: Long = NOW,
    ): Long = MyInspectionDatabase(driver).mediaArchiveQueries.insertVerifiedBackupReceipt(
        id = id,
        destination_kind = destinationKind,
        destination_ref = destinationRef,
        object_ref = objectRef,
        version_ref = versionRef,
        exported_at = exportedAt,
        verified_at = verifiedAt,
        scope_kind = scopeKind,
        scope_property_id = propertyId,
        revoked_at = null,
    ).value

    private fun insertEntry(receiptId: String, relPath: String, byteSize: Long, hash: String = HASH_A): Long =
        MyInspectionDatabase(driver).mediaArchiveQueries
            .insertVerifiedBackupReceiptEntry(receiptId, relPath, hash, byteSize)
            .value

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

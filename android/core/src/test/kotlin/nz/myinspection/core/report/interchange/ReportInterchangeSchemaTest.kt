package nz.myinspection.core.report.interchange

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.media.PhotoQualityProfile
import nz.myinspection.core.media.archive.ArchiveAssetIdentity
import nz.myinspection.core.media.archive.ArchiveEligibility
import nz.myinspection.core.media.archive.ArchiveIneligibility
import nz.myinspection.core.media.archive.MediaArchiveDbFixture
import nz.myinspection.core.report.Audience
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Literal SQLite vectors for the reviewed v5 -> v6 persistence boundary. */
class ReportInterchangeSchemaTest {
    private lateinit var driver: JdbcSqliteDriver
    private var temporary: Path? = null

    @BeforeTest fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MyInspectionDatabase.Schema.create(driver)
    }

    @AfterTest fun tearDown() {
        driver.close()
        temporary?.let(Files::delete)
        temporary = null
    }

    @Test fun `migration preserves every old receipt field and admits both formats`() {
        openV5()
        val oldColumns = "id,inspection_id,audience,quality,rel_path,content_hash,byte_size,exported_at"
        var n = 0
        for (audience in listOf("LANDLORD", "TENANT")) {
            for (quality in listOf("LOW", "MEDIUM", "HIGH", "EXTRA_HIGH")) {
                sql("INSERT INTO report_export_receipt VALUES ('r$n','i','$audience','$quality','reports/$n.pdf','old-hash-$n',$n,${n + 1})")
                n++
            }
        }
        val before = rows("SELECT $oldColumns FROM report_export_receipt ORDER BY id", 8)
        val otherTables = rows("SELECT name,sql FROM sqlite_master WHERE type='table' AND name<>'report_export_receipt' ORDER BY name", 2)
        MyInspectionDatabase.Schema.migrate(driver, 5, 6)
        assertEquals(before, rows("SELECT $oldColumns FROM report_export_receipt ORDER BY id", 8))
        assertEquals(List(8) { listOf("PDF") }, rows("SELECT format FROM report_export_receipt ORDER BY id", 1))
        assertEquals(otherTables, rows("SELECT name,sql FROM sqlite_master WHERE type='table' AND name NOT IN ('report_export_receipt','report_import_receipt') ORDER BY name", 2))
        assertImportContract()
        assertExportContract()
    }

    @Test fun `fresh v6 has the same closed receipt constraints as migration`() {
        assertEquals(6L, MyInspectionDatabase.Schema.version)
        assertImportContract()
        assertExportContract()
    }

    @Test fun `HTML cannot satisfy either missing PDF audience through the real ledger`() {
        MediaArchiveDbFixture().use { fixture ->
            val ledger = fixture.ledger()
            fun addHtml(audience: String) {
                fixture.driver.execute(null, "INSERT INTO report_export_receipt VALUES ('html-$audience','i','$audience','NONE','reports/$audience.html','html-hash',17,2,'HTML')", 0)
            }
            addHtml("LANDLORD")
            addHtml("TENANT")
            assertEquals(ArchiveEligibility.Ineligible(ArchiveIneligibility.EXPORT_RECEIPT_MISSING), ledger.cleanupEligible("i"))
            assertTrue(fixture.db.mediaArchiveQueries.selectReportExportReceiptsByInspection("i").executeAsList().isEmpty())
            assertTrue(fixture.db.mediaArchiveQueries.selectAllReportExportReceipts().executeAsList().isEmpty())
            ledger.recordReportExport("i", Audience.LANDLORD, PhotoQualityProfile.MEDIUM, ArchiveAssetIdentity("reports/l.pdf", HASH, 11))
            assertEquals(ArchiveEligibility.Ineligible(ArchiveIneligibility.EXPORT_RECEIPT_MISSING), ledger.cleanupEligible("i"))
            ledger.recordReportExport("other", Audience.TENANT, PhotoQualityProfile.LOW, ArchiveAssetIdentity("reports/other.pdf", HASH, 12))
            assertEquals(ArchiveEligibility.Ineligible(ArchiveIneligibility.EXPORT_RECEIPT_MISSING), ledger.cleanupEligible("i"))
            fixture.clock.now++
            ledger.recordReportExport("i", Audience.TENANT, PhotoQualityProfile.HIGH, ArchiveAssetIdentity("reports/t.pdf", HASH, 13))
            assertEquals(ArchiveEligibility.Eligible, ledger.cleanupEligible("i"))
            assertEquals(listOf("LANDLORD", "TENANT"), fixture.db.mediaArchiveQueries.selectReportExportReceiptsByInspection("i").executeAsList().map { it.audience })
        }
    }

    @Test fun `typed receipt queries preserve values isolate identities and order formats`() {
        val queries = MyInspectionDatabase(driver).reportInterchangeQueries
        assertEquals(1L, queries.insertImportReceipt("typed", HASH, 17, "DOCX-EXTRACT-1", OTHER_HASH, "2026-09-08", "{\"version\":1}", THIRD_HASH, 42).value)
        queries.insertImportReceipt("other", OTHER_HASH, 18, "DOCX-EXTRACT-1", HASH, "2026-09-09", "{}", HASH, 43)
        val receipt = queries.selectImportReceiptByInspection("typed").executeAsOne()
        assertEquals(listOf("typed", HASH, "17", "DOCX-EXTRACT-1", OTHER_HASH, "2026-09-08", "{\"version\":1}", THIRD_HASH, "42"),
            listOf(receipt.inspection_id, receipt.source_sha256, receipt.source_byte_size.toString(), receipt.extractor_version, receipt.manifest_sha256, receipt.source_date, receipt.mapping_receipt_json, receipt.mapping_sha256, receipt.imported_at.toString()))
        assertEquals(receipt, queries.selectImportReceiptBySource(HASH).executeAsOne())
        assertEquals(null, queries.selectImportReceiptBySource(THIRD_HASH).executeAsOneOrNull())
        assertEquals(null, queries.selectImportReceiptByInspection("absent").executeAsOneOrNull())
        assertFailsWith<Exception> { queries.insertImportReceipt("new", HASH, 17, "DOCX-EXTRACT-1", OTHER_HASH, "2026-09-08", "{}", THIRD_HASH, 42) }
        for (audience in listOf("TENANT", "LANDLORD")) {
            for ((format, quality) in listOf("HTML" to "NONE", "PDF" to "EXTRA_HIGH", "PDF" to "HIGH", "PDF" to "MEDIUM", "PDF" to "LOW")) {
                val id = "$audience-$quality"
                queries.insertFormatExportReceipt(id, "typed", audience, quality, "reports/$id", "hash-$id", 9, 4, format)
            }
        }
        queries.insertFormatExportReceipt("unrelated", "other", "LANDLORD", "NONE", "reports/other", "hash-other", 8, 3, "HTML")
        val exports = queries.selectFormatExportReceiptsByInspection("typed").executeAsList()
        assertEquals(listOf("LANDLORD-LOW", "LANDLORD-MEDIUM", "LANDLORD-HIGH", "LANDLORD-EXTRA_HIGH", "LANDLORD-NONE", "TENANT-LOW", "TENANT-MEDIUM", "TENANT-HIGH", "TENANT-EXTRA_HIGH", "TENANT-NONE"), exports.map { it.id })
        assertEquals(listOf("PDF", "PDF", "PDF", "PDF", "HTML", "PDF", "PDF", "PDF", "PDF", "HTML"), exports.map { it.format })
        for (row in exports) {
            assertEquals("typed", row.inspection_id)
            assertEquals("reports/${row.id}", row.rel_path)
            assertEquals("hash-${row.id}", row.content_hash)
            assertEquals(9L, row.byte_size)
            assertEquals(4L, row.exported_at)
        }
        assertTrue(queries.selectFormatExportReceiptsByInspection("absent").executeAsList().isEmpty())
    }

    @Test fun `hash storage rejects NUL padding and hidden suffixes through bound values`() {
        val queries = MyInspectionDatabase(driver).reportInterchangeQueries
        for (badHash in listOf("a".repeat(63) + 0.toChar(), HASH + 0.toChar() + "hidden")) {
            for (index in 0..2) {
                val hashes = mutableListOf(HASH, OTHER_HASH, THIRD_HASH).apply { this[index] = badHash }
                assertFailsWith<Exception>("hash $index rejects NUL") {
                    queries.insertImportReceipt("nul", hashes[0], 17, "DOCX-EXTRACT-1", hashes[1], "2026-09-08", "{}", hashes[2], 42)
                }
                assertEquals(null, queries.selectImportReceiptByInspection("nul").executeAsOneOrNull())
            }
        }
    }

    private fun assertExportContract() {
        for (audience in listOf("LANDLORD", "TENANT")) {
            for (quality in listOf("LOW", "MEDIUM", "HIGH", "EXTRA_HIGH")) {
                export("$audience-$quality", audience, "PDF", quality)
            }
            export("$audience-html", audience, "HTML", "NONE")
        }
        assertEquals(10, rows("SELECT id FROM report_export_receipt WHERE inspection_id='export-i'", 1).size)
        export("separate-inspection", "LANDLORD", "HTML", "NONE", "another-i")
        assertFailsWith<Exception> { export("duplicate-tuple", "LANDLORD", "HTML", "NONE") }
        assertFailsWith<Exception> { export("duplicate-pdf", "LANDLORD", "PDF", "MEDIUM") }
        assertFailsWith<Exception> { export("LANDLORD-html", "TENANT", "PDF", "LOW", "id-conflict") }
        for ((format, quality) in listOf("PDF" to "NONE", "HTML" to "LOW", "HTML" to "MEDIUM", "HTML" to "HIGH", "HTML" to "EXTRA_HIGH", "DOCX" to "LOW", "pdf" to "LOW", "HTML" to "none", "" to "NONE", "PDF" to "ULTRA")) {
            assertFailsWith<Exception>("$format/$quality must reject") { export("invalid", "LANDLORD", format, quality, "invalid-i") }
        }
        assertFailsWith<Exception> { export("invalid", "OWNER", "HTML", "NONE") }
        assertFailsWith<Exception> { sql("INSERT INTO report_export_receipt VALUES ('null','x','LANDLORD','NONE','r','h',0,1,NULL)") }
    }

    private fun assertImportContract() {
        assertEquals(
            listOf("inspection_id", "source_sha256", "source_byte_size", "extractor_version", "manifest_sha256", "source_date", "mapping_receipt_json", "mapping_sha256", "imported_at"),
            rows("PRAGMA table_info(report_import_receipt)", 2).map { it[1] },
        )
        insertImport()
        val original = rows("SELECT * FROM report_import_receipt", 9)
        assertEquals(listOf(listOf("i", HASH, "17", "DOCX-EXTRACT-1", OTHER_HASH, "2026-09-08", "{\"version\":1}", THIRD_HASH, "42")), original)
        for (verb in listOf("INSERT", "INSERT OR IGNORE", "INSERT OR REPLACE")) {
            assertFailsWith<Exception>("$verb duplicate inspection") { insertImport(mapOf("source_sha256" to OTHER_HASH), verb) }
            assertFailsWith<Exception>("$verb duplicate source") { insertImport(mapOf("inspection_id" to "different"), verb) }
            assertEquals(original, rows("SELECT * FROM report_import_receipt", 9))
        }
        val changes = mapOf("inspection_id" to "'changed'", "source_sha256" to "'$OTHER_HASH'", "source_byte_size" to "18", "extractor_version" to "'DOCX-EXTRACT-2'", "manifest_sha256" to "'$HASH'", "source_date" to "'2026-09-09'", "mapping_receipt_json" to "'{}'", "mapping_sha256" to "'$HASH'", "imported_at" to "43")
        for ((column, value) in changes) {
            assertFailsWith<Exception>("immutable $column") { sql("UPDATE report_import_receipt SET $column=$value") }
            assertEquals(original, rows("SELECT * FROM report_import_receipt", 9))
        }
        for (recursive in listOf("OFF", "ON")) {
            sql("PRAGMA recursive_triggers=$recursive")
            for (mode in listOf("IGNORE", "REPLACE")) {
                assertFailsWith<Exception> { sql("UPDATE OR $mode report_import_receipt SET source_byte_size=19") }
                assertFailsWith<Exception> { insertImport(mapOf("source_sha256" to THIRD_HASH), "INSERT OR $mode") }
            }
        }
        assertFailsWith<Exception> { sql("DELETE FROM report_import_receipt") }
        assertEquals(original, rows("SELECT * FROM report_import_receipt", 9))
        insertImport(mapOf("inspection_id" to "second", "source_sha256" to OTHER_HASH))
        assertEquals(2, rows("SELECT * FROM report_import_receipt", 9).size)
        val invalid = listOf(
            "inspection_id" to "", "source_sha256" to "short", "source_sha256" to "A".repeat(64), "source_sha256" to "g".repeat(64),
            "source_byte_size" to "0", "source_byte_size" to "-1", "source_byte_size" to "1.5", "extractor_version" to "", "extractor_version" to "../path", "extractor_version" to "X".repeat(65),
            "manifest_sha256" to "short", "manifest_sha256" to "g".repeat(64), "source_date" to "2026-9-08", "source_date" to "abcd-ef-gh",
            "mapping_receipt_json" to "", "mapping_sha256" to "short", "mapping_sha256" to "g".repeat(64), "imported_at" to "0", "imported_at" to "-1", "imported_at" to "1.5",
        )
        for ((column, value) in invalid) {
            assertFailsWith<Exception>("invalid $column=$value") { insertImport(mapOf("inspection_id" to "invalid", "source_sha256" to THIRD_HASH, column to value)) }
        }
        for (column in changes.keys) {
            val valid = importValues(mapOf("inspection_id" to "invalid", "source_sha256" to THIRD_HASH))
            val index = changes.keys.indexOf(column)
            for (badSql in listOf("NULL", "CAST(${valid[index]} AS BLOB)")) {
                valid[index] = badSql
                assertFailsWith<Exception>("$column rejects $badSql") { sql("INSERT INTO report_import_receipt VALUES (${valid.joinToString()})") }
            }
        }
        assertEquals(2, rows("SELECT * FROM report_import_receipt", 9).size)
    }

    private fun export(id: String, audience: String, format: String, quality: String, inspection: String = "export-i") =
        sql("INSERT INTO report_export_receipt VALUES ('$id','$inspection','$audience','$quality','reports/r','artifact-hash',0,1,'$format')")

    private fun importValues(overrides: Map<String, String>): MutableList<String> = linkedMapOf(
        "inspection_id" to "i", "source_sha256" to HASH, "source_byte_size" to "17", "extractor_version" to "DOCX-EXTRACT-1",
        "manifest_sha256" to OTHER_HASH, "source_date" to "2026-09-08", "mapping_receipt_json" to "{\"version\":1}", "mapping_sha256" to THIRD_HASH, "imported_at" to "42",
    ).apply { putAll(overrides) }.values.map { "'${it.replace("'", "''")}'" }.toMutableList()

    private fun insertImport(overrides: Map<String, String> = emptyMap(), verb: String = "INSERT") =
        sql("$verb INTO report_import_receipt VALUES (${importValues(overrides).joinToString()})")

    private fun openV5() {
        driver.close()
        val file = Files.createTempFile("interchange-v5-", ".db")
        temporary = file
        Files.copy(Path.of("src/main/sqldelight/databases/5.db"), file, StandardCopyOption.REPLACE_EXISTING)
        driver = JdbcSqliteDriver("jdbc:sqlite:$file")
        assertEquals(emptyList(), rows("SELECT name FROM sqlite_master WHERE name='report_import_receipt'", 1))
        assertEquals(emptyList(), rows("SELECT * FROM report_export_receipt", 8))
    }

    private fun sql(statement: String) { driver.execute(null, statement, 0) }
    private fun rows(statement: String, columns: Int): List<List<String?>> = driver.executeQuery(null, statement, { cursor ->
        val result = mutableListOf<List<String?>>()
        while (cursor.next().value) result += (0 until columns).map { cursor.getString(it) }
        QueryResult.Value(result)
    }, 0).value

    private companion object {
        const val HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val OTHER_HASH = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        const val THIRD_HASH = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
    }
}

/*
R4 receipt (2026-09-08): 32 isolated SQL source mutations; all failed fresh runtime tests.
Every run executed :core:compileTestKotlin then :core:test --rerun --tests nz.myinspection.core.report.interchange.*
using --offline --daemon --no-build-cache. Compilation failures are not kills; each run had a fresh
TestNG failure record. Baseline and byte-exact restored runs passed. SHA-256 matches the staged LF bytes.
Pinned API evidence: cached SQLDelight sqlite-driver 2.3.2 JdbcSqliteDriver signature; sql-psi 0.7.3
CreateTriggerMixin binds lowercase new/old. Generated queries compiled and ran on real SQLite.
MediaArchive.sq SHA-256 718365c8d8ea0b61b1907644f6dafb0b8ff48c48188a2ac4ce2a8f14cee36ce4
ReportInterchange.sq SHA-256 ddf9d17615570d2bff472db0cadb5e832e3ac53b51a37ee57e104552ccdfeda5
5.sqm SHA-256 d3ad127358f9f9e35a6925a956df3cccab7c99d79e967a0f6aa9601dffdcc604
M01-copy-hash | 5.sqm | quality,rel_path,content_hash,byte_size,exported_at,'PDF' -> quality,rel_path,content_hash || '-wrong',byte_size,exported_at,'PDF' | migration preserves every old receipt field and admits both formats
M02-drop-old-row | 5.sqm | FROM report_export_receipt_v5; -> FROM report_export_receipt_v5 WHERE id <> 'r4'; | migration preserves every old receipt field and admits both formats
M03-export-identity | MediaArchive.sq | CREATE UNIQUE INDEX idx_report_export_receipt_identity -> CREATE INDEX idx_report_export_receipt_identity | fresh v6 has the same closed receipt constraints as migration
M04-open-format | MediaArchive.sq | (format = 'PDF' AND quality IN -> (1 AND quality IN | fresh v6 has the same closed receipt constraints as migration
M05-html-quality | MediaArchive.sq | (format = 'HTML' AND quality = 'NONE') -> (format = 'HTML') | fresh v6 has the same closed receipt constraints as migration
M06-legacy-write | MediaArchive.sq | :exported_at, 'PDF' -> :exported_at, 'HTML' | HTML cannot satisfy either missing PDF audience through the real ledger
M07-eligibility-filter | MediaArchive.sq | WHERE inspection_id = :inspection_id AND format = 'PDF' -> WHERE inspection_id = :inspection_id | HTML cannot satisfy either missing PDF audience through the real ledger
M08-all-pdf-filter | MediaArchive.sq | WHERE format = 'PDF' ORDER BY id ASC; -> ORDER BY id ASC; | HTML cannot satisfy either missing PDF audience through the real ledger
M09-duplicate-inspection | ReportInterchange.sq | new.inspection_id IN (SELECT inspection_id FROM report_import_receipt) -> 0 | fresh v6 has the same closed receipt constraints as migration
M10-duplicate-source | ReportInterchange.sq | new.source_sha256 IN (SELECT source_sha256 FROM report_import_receipt) -> 0 | fresh v6 has the same closed receipt constraints as migration
M11-update-guard | ReportInterchange.sq | CREATE TRIGGER report_import_receipt_no_update BEFORE UPDATE ON report_import_receipt BEGIN SELECT RAISE(ABORT, 'Immutable import provenance'); END; -> (deleted) | fresh v6 has the same closed receipt constraints as migration
M12-delete-guard | ReportInterchange.sq | CREATE TRIGGER report_import_receipt_no_delete BEFORE DELETE ON report_import_receipt BEGIN SELECT RAISE(ABORT, 'Immutable import provenance'); END; -> (deleted) | fresh v6 has the same closed receipt constraints as migration
M13-update-guard | 5.sqm | CREATE TRIGGER report_import_receipt_no_update BEFORE UPDATE ON report_import_receipt BEGIN SELECT RAISE(ABORT, 'Immutable import provenance'); END; -> (deleted) | migration preserves every old receipt field and admits both formats
M14-inspection_id-check | ReportInterchange.sq | CHECK (typeof(inspection_id) = 'text' AND length(inspection_id) > 0), -> CHECK (1), | fresh v6 has the same closed receipt constraints as migration
M15-source_sha256-check | ReportInterchange.sq | CHECK (typeof(source_sha256) = 'text' AND length(source_sha256) = 64 AND length(CAST(source_sha256 AS BLOB)) = 64 AND source_sha256 NOT GLOB '*[^0-9a-f]*'), -> CHECK (1), | fresh v6 has the same closed receipt constraints as migration
M16-source_byte_size-check | ReportInterchange.sq | CHECK (typeof(source_byte_size) = 'integer' AND source_byte_size > 0), -> CHECK (1), | fresh v6 has the same closed receipt constraints as migration
M17-extractor_version-check | ReportInterchange.sq | CHECK (typeof(extractor_version) = 'text' AND length(extractor_version) BETWEEN 1 AND 64 AND length(CAST(extractor_version AS BLOB)) = length(extractor_version) AND extractor_version NOT GLOB '*[^A-Z0-9_-]*'), -> CHECK (1), | fresh v6 has the same closed receipt constraints as migration
M18-manifest_sha256-check | ReportInterchange.sq | CHECK (typeof(manifest_sha256) = 'text' AND length(manifest_sha256) = 64 AND length(CAST(manifest_sha256 AS BLOB)) = 64 AND manifest_sha256 NOT GLOB '*[^0-9a-f]*'), -> CHECK (1), | fresh v6 has the same closed receipt constraints as migration
M19-source_date-check | ReportInterchange.sq | CHECK (typeof(source_date) = 'text' AND length(CAST(source_date AS BLOB)) = 10 AND source_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'), -> CHECK (1), | fresh v6 has the same closed receipt constraints as migration
M20-mapping_receipt_json-check | ReportInterchange.sq | CHECK (typeof(mapping_receipt_json) = 'text' AND length(mapping_receipt_json) > 0), -> CHECK (1), | fresh v6 has the same closed receipt constraints as migration
M21-mapping_sha256-check | ReportInterchange.sq | CHECK (typeof(mapping_sha256) = 'text' AND length(mapping_sha256) = 64 AND length(CAST(mapping_sha256 AS BLOB)) = 64 AND mapping_sha256 NOT GLOB '*[^0-9a-f]*'), -> CHECK (1), | fresh v6 has the same closed receipt constraints as migration
M22-imported_at-check | ReportInterchange.sq | CHECK (typeof(imported_at) = 'integer' AND imported_at > 0) -> CHECK (1) | fresh v6 has the same closed receipt constraints as migration
M23-hash-nul-padding | ReportInterchange.sq | AND length(source_sha256) = 64 -> (deleted) | hash storage rejects NUL padding and hidden suffixes through bound values
M24-hash-nul-suffix | ReportInterchange.sq | AND length(CAST(source_sha256 AS BLOB)) = 64 -> (deleted) | hash storage rejects NUL padding and hidden suffixes through bound values
M25-source-query | ReportInterchange.sq | WHERE source_sha256 = :source_sha256; -> WHERE manifest_sha256 = :source_sha256; | typed receipt queries preserve values isolate identities and order formats
M26-inspection-query | ReportInterchange.sq | WHERE inspection_id = :inspection_id; -> WHERE inspection_id <> :inspection_id; | typed receipt queries preserve values isolate identities and order formats
M27-export-query | ReportInterchange.sq | WHERE inspection_id = :inspection_id ORDER BY -> WHERE inspection_id <> :inspection_id ORDER BY | typed receipt queries preserve values isolate identities and order formats
M28-audience-order | ReportInterchange.sq | CASE audience WHEN 'LANDLORD' THEN 0 WHEN 'TENANT' THEN 1 END -> CASE audience WHEN 'LANDLORD' THEN 1 WHEN 'TENANT' THEN 0 END | typed receipt queries preserve values isolate identities and order formats
M29-format-order | ReportInterchange.sq | CASE format WHEN 'PDF' THEN 0 WHEN 'HTML' THEN 1 END -> CASE format WHEN 'PDF' THEN 1 WHEN 'HTML' THEN 0 END | typed receipt queries preserve values isolate identities and order formats
M30-quality-order | ReportInterchange.sq | WHEN 'LOW' THEN 0 WHEN 'MEDIUM' THEN 1 -> WHEN 'LOW' THEN 1 WHEN 'MEDIUM' THEN 0 | typed receipt queries preserve values isolate identities and order formats
M31-import-forwarding | ReportInterchange.sq | :mapping_receipt_json, :mapping_sha256, :imported_at -> :mapping_receipt_json, :mapping_sha256, :imported_at + 1 | typed receipt queries preserve values isolate identities and order formats
M32-export-forwarding | ReportInterchange.sq | :content_hash, :byte_size, :exported_at, :format -> :content_hash, :byte_size + 1, :exported_at, :format | typed receipt queries preserve values isolate identities and order formats
*/

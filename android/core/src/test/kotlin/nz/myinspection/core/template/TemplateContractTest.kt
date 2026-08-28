package nz.myinspection.core.template

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import nz.myinspection.core.canon.supplementChainHash
import nz.myinspection.core.db.ClockMs
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.Uuid7Generator
import nz.myinspection.core.db.Uuid7RandomSource
import nz.myinspection.core.model.SupplementSnapshot
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Cross-file contract evidence for the reviewed schema-comment, documentation, and deterministic-test edges.
 * A12 mutation receipt: deleting only `README.md` from the exact resource include and rerunning the README
 * test with cache disabled exited 1 at `/README.md test resource is missing`; before the test receipt was
 * appended elsewhere, build.gradle restored byte-exact to SHA-256 7eca4c9c...e7953a8.
 */
class TemplateContractTest {
    @Test
    fun `template content hash is the SHA-256 of each source byte sequence`() {
        val compact = TemplateTestFixtures.GOLDEN_JSON.toByteArray()
        val indented = TemplateTestFixtures.GOLDEN_JSON
            .replace("\"items\":[", "\"items\":  [")
            .toByteArray()

        val compactHash = TemplateLoader.load(compact.inputStream()).contentHash
        val indentedHash = TemplateLoader.load(indented.inputStream()).contentHash

        assertNotEquals(compactHash, indentedHash)
        assertEquals(sha256(compact), compactHash)
        assertEquals(sha256(indented), indentedHash)
    }

    @Test
    fun `template version comment names raw bytes and rejects the old canonical JSON domain`() {
        val line = sourceLine("TemplateVersion.sq", "content_hash TEXT NOT NULL")

        assertTrue(line.contains("SHA-256(template file raw bytes)"), line)
        assertTrue(!line.contains("canonical JSON"), line)
    }

    @Test
    fun `supplement comment and golden vector name exactly the two-field snapshot domain`() {
        val line = sourceLine("Supplement.sq", "chain_hash TEXT NOT NULL")
        assertTrue(line.contains("SHA-256(canonical({created_at, text}) + prev_hash)"), line)
        assertTrue(!line.contains("canonical(本行)"), line)

        val prev = "0".repeat(64)
        val snapshot = SupplementSnapshot(createdAt = 1_700_000_000_000L, text = "x")
        val golden = "eabab685d19b7e1261b32b63f3a452816b8716c83c5b05e92eb625bf6420eb5a"
        assertEquals(golden, supplementChainHash(prev, snapshot))
        assertNotEquals(golden, supplementChainHash(prev, snapshot.copy(createdAt = snapshot.createdAt + 1)))
        assertNotEquals(golden, supplementChainHash(prev, snapshot.copy(text = "y")))

        data class Row(val id: String, val inspectionId: String, val snapshot: SupplementSnapshot)
        val first = Row("row-a", "inspection-a", snapshot)
        val second = Row("row-b", "inspection-b", snapshot)
        assertEquals(supplementChainHash(prev, first.snapshot), supplementChainHash(prev, second.snapshot))
    }

    @Test
    fun `README room JSON block is an executable valid template`() {
        val readme = requireResource("/README.md").decodeToString()
        val match = Regex("(?s)## 房间定义.*?```json\\R(.*?)\\R```").find(readme)
            ?: error("README.md is missing the 房间定义 json block")

        val loaded = TemplateLoader.load(match.groupValues[1].toByteArray().inputStream())
        assertEquals(emptyList<String>(), TemplateLoader.validate(loaded.template))
        assertEquals(listOf(TemplateRoom("BEDROOM", true), TemplateRoom("KITCHEN", false)), loaded.template.rooms)
    }

    @Test
    fun `real template resources retain their pre-card raw byte hashes and no runtime rooms are inserted`() {
        val routineBytes = requireResource("/routine-v1.json")
        assertEquals(
            "0abb0dbe5b71970ee79c5fadc488d8f581d5a0bc4ef78feb204e7a5b753964fb",
            sha256(routineBytes),
        )
        assertEquals(
            "d82b10eb57319221f416ad5e38ef1a00a69ce30558b669ac64db46a913ca2ac7",
            sha256(requireResource("/phrases-v1.json")),
        )
        val existing = TemplateLoader.load(routineBytes.inputStream())
        assertEquals(emptyList<TemplateRoom>(), existing.template.rooms)
        assertEquals(emptyList<String>(), TemplateLoader.validate(existing.template))

        val (export, roomInstanceCount) = deterministicExport()
        assertTrue(export.isNotEmpty())
        assertEquals(0L, roomInstanceCount)
    }

    @Test
    fun `fixed clock and UUID source produce byte-identical full table exports`() {
        val first = deterministicExport()
        val second = deterministicExport()

        assertEquals(first, second)
    }

    private fun deterministicExport(): Pair<String, Long> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            MyInspectionDatabase.Schema.create(driver)
            val fixedClock = ClockMs { 1_700_000_000_000L }
            val store = TemplateStore(
                MyInspectionDatabase(driver),
                uuid = Uuid7Generator(fixedClock, FixedUuid7RandomSource(seed = 0x1234L)),
                clock = fixedClock,
            )
            store.persist(TemplateLoader.load(TemplateTestFixtures.routineTemplateWithRooms().byteInputStream()))

            val export = listOf(
                exportRows(
                    driver,
                    "SELECT id, type, version, content_hash, created_at, updated_at, deleted_at FROM template_version ORDER BY id",
                    7,
                ),
                exportRows(
                    driver,
                    "SELECT id, template_version_id, stable_id, area, room, text_en, text_zh, allowed_statuses, photo_rule, sort, created_at, updated_at, deleted_at FROM check_item_def ORDER BY id",
                    13,
                ),
                exportRows(
                    driver,
                    "SELECT id, template_version_id, room_key, repeatable, sort, created_at, updated_at, deleted_at FROM template_room_def ORDER BY id",
                    8,
                ),
            ).joinToString("\n--table--\n")
            return export to scalarLong(driver, "SELECT COUNT(*) FROM room_instance")
        } finally {
            driver.close()
        }
    }

    private fun exportRows(driver: JdbcSqliteDriver, sql: String, columns: Int): String =
        driver.executeQuery(null, sql, { cursor ->
            val rows = mutableListOf<String>()
            while (cursor.next().value) {
                rows += (0 until columns).joinToString("|") { cursor.getString(it) ?: "<null>" }
            }
            QueryResult.Value(rows.joinToString("\n"))
        }, 0).value

    private fun scalarLong(driver: JdbcSqliteDriver, sql: String): Long =
        driver.executeQuery(null, sql, { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0)!! else 0L)
        }, 0).value

    private fun sourceLine(fileName: String, contains: String): String {
        val path = Path.of(
            System.getProperty("user.dir"),
            "src/main/sqldelight/nz/myinspection/core/db/$fileName",
        )
        return Files.readAllLines(path).single { it.contains(contains) }
    }

    private fun requireResource(path: String): ByteArray =
        javaClass.getResourceAsStream(path)?.use { it.readBytes() }
            ?: error("$path test resource is missing")

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}

private class FixedUuid7RandomSource(seed: Long) : Uuid7RandomSource {
    private var state = seed

    override fun nextLong(): Long {
        state = state * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
        return state
    }
}

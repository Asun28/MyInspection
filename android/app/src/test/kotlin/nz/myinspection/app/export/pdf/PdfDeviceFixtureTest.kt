package nz.myinspection.app.export.pdf

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import nz.myinspection.core.canon.canonicalJson
import nz.myinspection.core.media.ContentHash
import nz.myinspection.core.model.PropertySnapshot
import nz.myinspection.core.report.Audience
import nz.myinspection.core.report.BilingualText
import nz.myinspection.core.report.StatusDefinition
import nz.myinspection.core.report.content.PrivatePhotoScope
import nz.myinspection.core.report.content.ReportOrigin
import org.testng.SkipException

/**
 * Portable cases feed synthetic rows through the real preflight and builder and prove guards and shape. The real80
 * digests are compared only by the env-gated run at the end: build() checks them against the production constants,
 * then the test checks them against literals typed here.
 */
class PdfDeviceFixtureTest {
    private val expectedNative = "f8573b3252196b7ac36201755a2e6dbb9fcd91b2485899679aa5230c32751023"
    private val expectedSemantic = "af17258a955afaa0dc73bab853db8ed9fa2424b043274f31bafa01c974a7287c"
    private val approvedManifest = "8721160680e73a2ce3570666ac416e31515bbd16fefb2a106e180790884ccad5"

    @Test
    fun `preflight binds eighty verified files under the fixture root`() = withRoot { root ->
        val fixture = PdfFixtureManifest.preflight(approvedManifest, synthetic(root), root)
        assertEquals(approvedManifest, fixture.manifestSha256)
        assertEquals((1..80).toList(), fixture.photos.map { it.ordinal })
        assertTrue(fixture.photos.all { it.file.canonicalFile.parentFile == root.canonicalFile })
        assertEquals(fixture.photos.map { it.row.filename }, fixture.photos.map { it.file.name })
    }

    @Test
    fun `preflight refuses each inconsistent manifest with its own code`() = withRoot { root ->
        val good = synthetic(root)
        val row = good.rows[5]
        val dup = row.copy(category = good.rows[6].category)
        val zeros = "0".repeat(64)
        val cases: List<Triple<String, String, (FixtureManifest) -> FixtureManifest>> = listOf(
            Triple("MANIFEST-DIGEST", zeros, { it }),
            Triple("FIXTURE-ID", approvedManifest, { it.copy(fixtureId = "myinspection-real81") }),
            Triple("AUTHORIZATION", approvedManifest, { it.copy(authorization = " ") }),
            Triple("PHOTO-COUNT", approvedManifest, { it.copy(photoCount = 79) }),
            Triple("PHOTO-COUNT", approvedManifest, { it.copy(rows = it.rows.drop(1)) }),
            Triple("DISTINCT-ID", approvedManifest, { it.replace(6, dup.copy(filename = "dup.bin", sha256 = zeros)) }),
            Triple("DISTINCT-FILENAME", approvedManifest, { it.replace(6, dup.copy(photoId = "dup", sha256 = zeros)) }),
            Triple("DISTINCT-HASH", approvedManifest, { it.replace(6, dup.copy(photoId = "dup", filename = "dup.bin")) }),
            Triple("CATEGORY-COUNTS", approvedManifest, { it.copy(categoryCounts = it.categoryCounts + ("nameplate" to 20)) }),
            Triple("CATEGORY-COUNTS", approvedManifest, { it.replace(5, row.copy(category = "nameplate_")) }),
            Triple("DIMENSIONS", approvedManifest, { it.replace(5, row.copy(width = 0)) }),
            Triple("DIMENSIONS", approvedManifest, { it.replace(5, row.copy(height = -1)) }),
            Triple("DIMENSIONS", approvedManifest, { it.replace(5, row.copy(bytes = 0)) }),
            Triple("LICENSE", approvedManifest, { it.replace(5, row.copy(licenseName = "CC BY-SA 4.0")) }),
            Triple("LICENSE", approvedManifest, { it.replace(5, row.copy(licenseName = "CC BY-NC 2.0")) }),
            Triple("CONTENT-CHECK", approvedManifest, { it.replace(5, row.copy(contentCheck = "pending")) }),
            Triple("FILENAME", approvedManifest, { it.replace(5, row.copy(filename = "")) }),
            Triple("FILENAME", approvedManifest, { it.replace(5, row.copy(filename = ".")) }),
            Triple("FILENAME", approvedManifest, { it.replace(5, row.copy(filename = "..")) }),
            Triple("FILENAME", approvedManifest, { it.replace(5, row.copy(filename = "sub/" + row.filename)) }),
            Triple("FILENAME", approvedManifest, { it.replace(5, row.copy(filename = "sub\\" + row.filename)) }),
            Triple("FILENAME", approvedManifest, { it.replace(5, row.copy(filename = "../" + row.filename)) }),
            Triple("FILENAME", approvedManifest, { it.replace(5, row.copy(filename = File(root, row.filename).absolutePath)) }),
            Triple("FILENAME", approvedManifest, { it.replace(5, row.copy(filename = row.filename + ":alt")) }),
            Triple("FILE-MISSING", approvedManifest, { it.replace(5, row.copy(filename = "absent.bin")) }),
            Triple("FILE-BYTES", approvedManifest, { it.replace(5, row.copy(sha256 = zeros)) }),
            Triple("FILE-BYTES", approvedManifest, { it.replace(5, row.copy(bytes = row.bytes + 1)) }),
        )
        cases.forEachIndexed { index, (code, digest, mutate) ->
            val refusal = assertFailsWith<FixtureRefusal>("case $index") {
                PdfFixtureManifest.preflight(digest, mutate(good), root)
            }
            assertTrue(refusal.message!!.startsWith("[FIXTURE-$code]"), "case $index: ${refusal.message}")
        }
        File(root, good.rows[7].filename).appendBytes(byteArrayOf(1))
        val changed = assertFailsWith<FixtureRefusal> { PdfFixtureManifest.preflight(approvedManifest, good, root) }
        assertTrue(changed.message!!.startsWith("[FIXTURE-FILE-BYTES]"), changed.message)
    }

    /** The public route hashes the bytes it read and refuses them before org.json (an Android stub here) is called. */
    @Test
    fun `reader refuses manifest bytes that are not the approved ones`() = withRoot { root ->
        File(root, PdfFixtureManifest.APPROVED_MANIFEST_FILENAME).writeText("{}")
        val refusal = assertFailsWith<FixtureRefusal> { AndroidFixtureManifestReader.preflight(root) }
        assertTrue(refusal.message!!.startsWith("[FIXTURE-MANIFEST-DIGEST]"), refusal.message)
    }

    /** The entry itself must be a regular file: a directory, a link or a junction under that name is refused, not followed. */
    @Test
    fun `preflight refuses an entry that is not a regular file under the root`() = withRoot { root ->
        val good = synthetic(root)
        val entry = File(root, good.rows[3].filename)
        val elsewhere = File(root, "elsewhere").apply { mkdirs() }
        File(elsewhere, entry.name).writeBytes(entry.readBytes())
        entry.delete()
        entry.mkdir()
        val directory = assertFailsWith<FixtureRefusal> { PdfFixtureManifest.preflight(approvedManifest, good, root) }
        assertTrue(directory.message!!.startsWith("[FIXTURE-FILENAME]"), directory.message)
        entry.delete()
        try {
            Files.createSymbolicLink(entry.toPath(), File(elsewhere, entry.name).toPath())
        } catch (e: IOException) {
            val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
            val junction = if (windows) ProcessBuilder("cmd", "/c", "mklink", "/J", entry.path, elsewhere.path).start() else null
            val output = junction?.inputStream?.bufferedReader()?.use { it.readText() }
            if (junction == null || junction.waitFor() != 0) throw SkipException("no symlink (${e.message}) and no junction ($output)")
        }
        val alias = assertFailsWith<FixtureRefusal> { PdfFixtureManifest.preflight(approvedManifest, good, root) }
        assertTrue(alias.message!!.startsWith("[FIXTURE-FILENAME]"), alias.message)
    }

    @Test
    fun `builder places exactly eighty photos across the four fixed groups`() = withRoot { root ->
        val fixture = PdfFixtureManifest.preflight(approvedManifest, synthetic(root), root)
        val input = PdfDeviceFixture.buildUnverified(fixture)
        val content = input.content
        assertEquals(Audience.LANDLORD, content.audience)
        assertEquals(PrivatePhotoScope.EXCLUDED, content.privatePhotoScope)
        assertEquals(ReportOrigin.NATIVE, content.origin)
        assertNull(content.importProvenance)
        assertEquals(
            listOf(
                BilingualText("Panoramas", "房间全景"), BilingualText("Low light", "低光场景"),
                BilingualText("Fine textures", "精细纹理"), BilingualText("Nameplates", "设备铭牌"),
            ),
            content.rooms.map { it.label },
        )
        assertEquals(listOf(20, 0, 0, 0), content.rooms.map { it.photos.size })
        assertEquals(listOf(0, 20, 21, 19), content.rooms.map { room -> room.items.sumOf { it.photos.size } })
        val placed = content.rooms.flatMap { room -> room.photos + room.items.flatMap { it.photos } }
        assertEquals(80, placed.size)
        listOf("room_panorama", "low_light", "high_texture", "nameplate").forEachIndexed { index, category ->
            val room = content.rooms[index]
            assertEquals(
                fixture.photos.filter { it.row.category == category }.map { it.row.sha256 },
                (room.photos + room.items.flatMap { it.photos }).map { it.contentHash },
                "room $index holds exactly the $category rows in manifest order",
            )
        }
        assertEquals(placed.map { it.contentHash }.toSet(), input.report.canonical.photos.map { it.contentHash }.toSet())
        assertEquals(20, input.report.canonical.photos.count { it.isRoomLevel })
        assertTrue(input.report.canonical.photos.all { it.source == "imported" && it.exifTimeMs == null })
        assertTrue(placed.none { it.privacy })
        assertEquals(fixture.photos.map { it.file }, input.descriptors.map { it.file })
        assertEquals((1..80).toList(), input.descriptors.map { it.manifestOrdinal })
        assertEquals(fixture.photos.map { it.row.sha256 }, input.descriptors.map { it.contentHash })
        val holders = content.rooms.flatMap { room ->
            room.photos.map { listOf(it.id, it.reference, it.contentHash, room.id, null) } +
                room.items.flatMap { item -> item.photos.map { listOf(it.id, it.reference, it.contentHash, room.id, item.id) } }
        }
        assertEquals(
            holders.sortedBy { it[0] as String },
            input.descriptors.map { listOf(it.photoId, it.reference, it.contentHash, it.roomId, it.itemId) }.sortedBy { it[0] as String },
            "each descriptor binds exactly the content photo, reference, hash and slot it describes",
        )
    }

    @Test
    fun `references and UUIDv7 identities derive from manifest order`() = withRoot { root ->
        val fixture = PdfFixtureManifest.preflight(approvedManifest, synthetic(root), root)
        val input = PdfDeviceFixture.buildUnverified(fixture)
        val references = input.descriptors.map { it.reference }
        assertEquals(80, references.toSet().size)
        assertTrue(references.all { Regex("[1-4]\\.(R|1)\\.\\d{2}").matches(it) })
        assertEquals("1.R.01", references[0])
        assertEquals("2.1.01", references[1])
        assertEquals("3.1.21", references[79])
        val ids = input.descriptors.map { it.photoId } + input.report.rooms.map { it.id } +
            input.report.rooms.flatMap { room -> room.items.map { it.id } } +
            listOf(input.report.canonical.id, input.report.canonical.property.id, input.report.canonical.template.id)
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { UUID.fromString(it).let { u -> u.version() == 7 && u.variant() == 2 } && it == it.lowercase() })
        assertEquals("01a0aca9-bc00-7000-8000-000000001001", input.descriptors[0].photoId)
        assertEquals("01a0aca9-bc00-7000-8000-000000001050", input.descriptors[79].photoId)
        assertEquals(input.descriptors.map { it.photoId }, input.descriptors.map { it.photoId }.sorted())
        val photos = input.report.rooms.flatMap { room -> room.photos + room.items.flatMap { it.photos } }.sortedBy { it.id }
        assertEquals(1789599601000L, photos[0].capturedAt)
        assertEquals(1789599680000L, photos[79].capturedAt)
    }

    @Test
    fun `canonical items follow template order while rooms follow group order`() = withRoot { root ->
        val input = PdfDeviceFixture.buildUnverified(PdfFixtureManifest.preflight(approvedManifest, synthetic(root), root))
        assertEquals(listOf("BED-LIGHT-01", "GEN-METER-01", "HAL-WALL-01"), input.report.canonical.items.map { it.stableId })
        assertEquals(listOf("GOOD", "POOR", "FAIR"), input.report.canonical.items.map { it.status })
        assertEquals(
            listOf("BED-LIGHT-01", "HAL-WALL-01", "GEN-METER-01"),
            input.content.rooms.flatMap { room -> room.items.map { it.stableId } },
        )
        assertEquals(
            listOf(
                BilingualText("Lighting samples", "灯光样本") to "Test fixture only / 仅供测试",
                BilingualText("Texture samples", "纹理样本") to "Test texture detail / 测试纹理细节",
                BilingualText("Nameplate samples", "铭牌样本") to "Test small print / 测试铭牌小字",
            ),
            input.content.rooms.flatMap { room -> room.items.map { it.label to it.note } },
        )
        assertEquals(listOf("FAIR", "POOR"), input.content.summary.adverseItems.map { it.status })
        assertEquals(
            listOf(
                StatusDefinition("GOOD", BilingualText("Good", "良好"), BilingualText("No issue observed", "未观察到问题")),
                StatusDefinition("FAIR", BilingualText("Fair", "一般"), BilingualText("Wear is visible", "可见正常损耗")),
                StatusDefinition("POOR", BilingualText("Poor", "较差"), BilingualText("Attention is needed", "需要处理")),
                StatusDefinition(
                    "NOT_APPLICABLE", BilingualText("Not applicable", "不适用"), BilingualText("This item does not apply", "本检查项不适用"),
                ),
            ),
            input.content.statusDefinitions,
        )
    }

    /** Internal members compile to mangled JVM names; a private constructor leaves at most a synthetic accessor. */
    @Test
    fun `verified types expose no public factory and their collections are read-only`() = withRoot { root ->
        listOf(
            PdfFixtureManifest::class.java to "preflight",
            AuthorizedFixture.Companion::class.java to "preflight",
            AuthorizedPhoto.Companion::class.java to "verify",
        ).forEach { (type, name) -> assertTrue(type.methods.none { it.name == name }, "${type.name}.$name must stay internal") }
        listOf(AuthorizedFixture::class.java, AuthorizedPhoto::class.java).forEach { type ->
            assertTrue(type.constructors.all { it.isSynthetic }, "${type.name} must have no public constructor")
        }
        val fixture = PdfFixtureManifest.preflight(approvedManifest, synthetic(root), root)
        val input = PdfDeviceFixture.buildUnverified(fixture)
        assertFailsWith<UnsupportedOperationException> { (fixture.photos as MutableList<AuthorizedPhoto>).removeAt(0) }
        assertFailsWith<UnsupportedOperationException> { (input.descriptors as MutableList<FixturePhotoDescriptor>).removeAt(0) }
        assertFailsWith<UnsupportedOperationException> {
            (PdfFixtureManifest.REQUIRED_CATEGORY_COUNTS as MutableMap<String, Int>)["nameplate"] = 20
        }
        assertEquals(80, fixture.photos.size)
        assertEquals(80, input.descriptors.size)
    }

    @Test
    fun `fixed identity literals match the frozen mapping and the real template bytes`() = withRoot { root ->
        val report = PdfDeviceFixture.buildUnverified(PdfFixtureManifest.preflight(approvedManifest, synthetic(root), root)).report
        val canonical = report.canonical
        assertEquals("01a0aca9-bc00-7000-8000-000000000001", canonical.id)
        assertEquals("01a0aca9-bc00-7000-8000-000000000003", canonical.template.id)
        assertEquals("ROUTINE" to 2L, canonical.type to canonical.template.version)
        assertEquals(1789603200000L to 1789603260000L, canonical.scheduledAt to canonical.finalizedAt)
        assertEquals(
            PropertySnapshot("01a0aca9-bc00-7000-8000-000000000002", "PDF fixture only / 仅用于 PDF 测试", "RENTAL", false),
            canonical.property,
        )
        assertEquals((1..4).map { "01a0aca9-bc00-7000-8000-00000000010$it" }, report.rooms.map { it.id })
        assertEquals((2..4).map { "01a0aca9-bc00-7000-8000-00000000020$it" }, report.rooms.flatMap { room -> room.items.map { it.id } })
        assertNull(canonical.tenancy)
        val template = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "data/templates/routine-v2.json") }.firstOrNull { it.isFile }
            ?: fail("routine-v2.json not found above ${File("").absolutePath}")
        assertEquals("fd06639f0b7117b1b3cbbf74a71b19cf0f0cc2ee4e374256ad967ad2bdcfe078", PdfDeviceFixture.TEMPLATE_CONTENT_HASH)
        assertEquals(PdfDeviceFixture.TEMPLATE_CONTENT_HASH, ContentHash.sha256Hex(template.readBytes()))
        assertEquals(PdfDeviceFixture.TEMPLATE_CONTENT_HASH, canonical.template.contentHash)
    }

    @Test
    fun `expected digests are distinct literals and drift is refused by name`() = withRoot { root ->
        assertEquals(expectedNative, PdfDeviceFixture.EXPECTED.nativeDataHash)
        assertEquals(expectedSemantic, PdfDeviceFixture.EXPECTED.semanticFingerprint)
        assertNotEquals(PdfDeviceFixture.EXPECTED.nativeDataHash, PdfDeviceFixture.EXPECTED.semanticFingerprint)
        val fixture = PdfFixtureManifest.preflight(approvedManifest, synthetic(root), root)
        val drift = assertFailsWith<FixtureRefusal> { PdfDeviceFixture.build(fixture) }
        assertTrue(drift.message!!.startsWith("[FIXTURE-NATIVE-DRIFT]"), drift.message)
        val content = PdfDeviceFixture.buildUnverified(fixture).content
        val own = FixedDigests(content.nativeIntegrity.dataHash, content.semanticFingerprint)
        PdfDeviceFixture.verifyFixedDigests(content, own)
        val semantic = assertFailsWith<FixtureRefusal> {
            PdfDeviceFixture.verifyFixedDigests(content, own.copy(semanticFingerprint = expectedSemantic))
        }
        assertTrue(semantic.message!!.startsWith("[FIXTURE-SEMANTIC-DRIFT]"), semantic.message)
        val native = assertFailsWith<FixtureRefusal> {
            PdfDeviceFixture.verifyFixedDigests(content, own.copy(nativeDataHash = expectedNative))
        }
        assertTrue(native.message!!.startsWith("[FIXTURE-NATIVE-DRIFT]"), native.message)
    }

    /**
     * Explicit real-input run: skipped unless requested, never skipped once requested. Run it with
     * `--rerun-tasks --no-build-cache`: Gradle's Test cache key ignores the environment, so an unchanged
     * classpath would otherwise restore the previous run's result whatever this variable says.
     */
    @Test
    fun `real80 approved input reproduces the frozen digests`() {
        val dir = System.getenv("MYINSPECTION_REAL80_DIR")
            ?: throw SkipException("set MYINSPECTION_REAL80_DIR to run the explicit real80 comparison")
        val bytes = File(dir, PdfFixtureManifest.APPROVED_MANIFEST_FILENAME).readBytes()
        assertEquals(approvedManifest, ContentHash.sha256Hex(bytes), "manifest bytes are not the approved ones")
        val fixture = PdfFixtureManifest.preflight(approvedManifest, parseManifest(bytes), File(dir, "photos"))
        val input = PdfDeviceFixture.build(fixture)
        assertEquals(expectedNative, input.content.nativeIntegrity.dataHash)
        assertEquals(expectedSemantic, input.content.semanticFingerprint)
        assertEquals(12564, canonicalJson(input.report.canonical).toByteArray().size)
        println("[REAL80-FIXTURE] manifest=$approvedManifest photos=${fixture.photos.size} " +
            "native=${input.content.nativeIntegrity.dataHash} semantic=${input.content.semanticFingerprint}")
    }

    private fun withRoot(block: (File) -> Unit) {
        val root = Files.createTempDirectory("real80-synthetic").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }

    /** 80 rows in the real distribution but interleaved, with gaps in the ids, over generated files. */
    private fun synthetic(root: File): FixtureManifest {
        val categories = List(19) { listOf("room_panorama", "low_light", "high_texture", "nameplate") }.flatten() +
            listOf("room_panorama", "low_light", "high_texture", "high_texture")
        val rows = categories.mapIndexed { index, category ->
            val id = index + 1 + (index / 6)
            val body = ByteArray(64 + index) { ((index * 7 + it) and 0xFF).toByte() }
            val name = "syn-%03d.bin".format(id)
            File(root, name).writeBytes(body)
            FixtureManifestRow(
                photoId = "syn-%03d".format(id), category = category, filename = name, bytes = body.size.toLong(),
                sha256 = ContentHash.sha256Hex(body), width = 100 + index, height = 50,
                licenseName = listOf("CC BY 4.0", "CC0", "Public domain", "CC BY 2.0 de")[index % 4],
                contentCheck = "agent_visual_review_pass",
            )
        }
        return FixtureManifest("myinspection-real80", "synthetic test rows", 80, rows.groupingBy { it.category }.eachCount(), rows)
    }

    private fun FixtureManifest.replace(index: Int, row: FixtureManifestRow) =
        copy(rows = rows.toMutableList().apply { set(index, row) })

    @Suppress("UNCHECKED_CAST")
    private fun parseManifest(bytes: ByteArray): FixtureManifest {
        val m = MiniJson(bytes.toString(Charsets.UTF_8)).parse() as Map<String, Any?>
        val counts = (m["category_counts"] as Map<String, Any?>).mapValues { (it.value as Long).toInt() }
        val rows = (m["photos"] as List<Map<String, Any?>>).map { p ->
            FixtureManifestRow(
                p["photo_id"] as String, p["category"] as String, p["filename"] as String, p["bytes"] as Long,
                p["sha256"] as String, (p["width"] as Long).toInt(), (p["height"] as Long).toInt(),
                p["license_name"] as String, p["content_check"] as String,
            )
        }
        return FixtureManifest(m["fixture_id"] as String, m["authorization"] as String, (m["photo_count"] as Long).toInt(), counts, rows)
    }
}

/**
 * Test-only minimal JSON reader for the explicit run, not a validating parser: it only ever reads manifest bytes
 * whose SHA-256 was checked first. The device path reads the manifest with org.json.
 */
private class MiniJson(private val s: String) {
    private var i = 0
    fun parse(): Any? = value().also { ws(); require(i == s.length) { "trailing input at $i" } }
    private fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
    private fun value(): Any? { ws(); return when (s[i]) {
        '{' -> obj(); '[' -> arr(); '"' -> str()
        't' -> lit("true", true); 'f' -> lit("false", false); 'n' -> lit("null", null); else -> num()
    } }
    private fun obj(): Map<String, Any?> { i++; val m = LinkedHashMap<String, Any?>(); ws(); if (s[i] == '}') { i++; return m }
        while (true) { ws(); val k = str(); ws(); require(s[i++] == ':'); require(m.put(k, value()) == null) { "duplicate key $k" }
            ws(); when (s[i++]) { ',' -> Unit; '}' -> return m; else -> error("bad object at $i") } } }
    private fun arr(): List<Any?> { i++; val l = ArrayList<Any?>(); ws(); if (s[i] == ']') { i++; return l }
        while (true) { l += value(); ws(); when (s[i++]) { ',' -> Unit; ']' -> return l; else -> error("bad array at $i") } } }
    private fun str(): String { require(s[i++] == '"'); val b = StringBuilder()
        while (true) { when (val c = s[i++]) { '"' -> return b.toString()
            '\\' -> b.append(when (val e = s[i++]) { 'n' -> '\n'; 't' -> '\t'; 'r' -> '\r'; 'b' -> 8.toChar(); 'f' -> 12.toChar()
                'u' -> s.substring(i, i + 4).toInt(16).toChar().also { i += 4 }; else -> e })
            else -> b.append(c) } } }
    private fun lit(t: String, v: Any?): Any? { require(s.startsWith(t, i)) { "bad literal at $i" }; i += t.length; return v }
    private fun num(): Any { val st = i; while (i < s.length && s[i] in "+-.eE0123456789") i++
        return s.substring(st, i).let { it.toLongOrNull() ?: it.toDouble() } }
}

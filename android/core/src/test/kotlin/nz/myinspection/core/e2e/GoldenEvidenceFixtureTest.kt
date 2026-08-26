package nz.myinspection.core.e2e

import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GoldenEvidenceFixtureTest {
    @Test
    fun `fixture pins the real routine template and deterministic inspection identity`() {
        val fixture = GoldenEvidenceFixtureLoader.load()

        assertEquals(1, fixture.fixtureVersion)
        assertEquals(
            TemplateFixture(
                resource = "/routine-v1.json",
                type = "ROUTINE",
                version = 1,
                expectedId = "0193ba74-3c00-72af-8def-012311223344",
                expectedContentHash = "0abb0dbe5b71970ee79c5fadc488d8f581d5a0bc4ef78feb204e7a5b753964fb",
                panoramaRooms = listOf(
                    "LOUNGE", "KITCHEN-DINING", "BATHROOM", "LAUNDRY", "BEDROOM", "GENERAL", "EXTERIOR",
                ),
            ),
            fixture.template,
        )
        assertNotNull(javaClass.getResourceAsStream(fixture.template.resource)).use { input ->
            val actual = MessageDigest.getInstance("SHA-256").digest(input.readBytes()).toHex()
            assertEquals(fixture.template.expectedContentHash, actual, "the referenced real template bytes must stay frozen")
        }
        assertEquals(
            PropertyFixture(
                id = "property-golden-001",
                address = "12 Golden Evidence Lane, Wellington",
                kind = "RENTAL",
                isBoardingHouse = false,
            ),
            fixture.property,
        )
        assertEquals(TenancyFixture("tenancy-golden-001", 1_700_000_000_000L, null), fixture.tenancy)
        assertEquals(
            InspectionFixture(
                expectedId = "0193ba74-3c01-72af-8def-012311223344",
                type = "ROUTINE",
                scheduledAt = 1_734_000_000_001L,
                finalizedAt = 1_734_000_001_001L,
                defaultStatus = "GOOD",
                itemOverrides = listOf(
                    ItemAnswerFixture("LNG-WALL-01", "POOR", "PUBLIC_OBJECTIVE_GOLDEN_SENTINEL"),
                ),
            ),
            fixture.inspection,
        )
    }

    @Test
    fun `fixture pins byte-exact room item and private photo evidence`() {
        val fixture = GoldenEvidenceFixtureLoader.load()

        assertEquals(9, fixture.photos.size)
        assertEquals(
            listOf(
                photo(
                    "room-lounge", "ROOM", "LOUNGE", "ffd84c4f554e4745ffd9",
                    "693d20df1e0cee173de5a4b1db19ca901d0a866301d04a83ea2bb001e82486fd",
                    "CAMERA", 1_734_000_000_011L, false, "ROOM-LOUNGE-1",
                ),
                photo(
                    "room-kitchen-dining", "ROOM", "KITCHEN-DINING", "ffd84b49544348454effd9",
                    "dc1b2173cc0ed67a72468de41f5c3fadea0255383c934147024fb9b99241bc5b",
                    "CAMERA", 1_734_000_000_021L, false, "ROOM-KITCHEN-DINING-1",
                ),
                photo(
                    "room-bathroom", "ROOM", "BATHROOM", "ffd842415448524f4f4dffd9",
                    "b59ec3d45c5ff869898063e34d39417b997c337f9d4135e5402b346b43f73ef2",
                    "CAMERA", 1_734_000_000_031L, false, "ROOM-BATHROOM-1",
                ),
                photo(
                    "room-laundry", "ROOM", "LAUNDRY", "ffd84c41554e445259ffd9",
                    "8bf6e9fa10db33300d55bf9fc8104c829f34685b1ad9a60416da098d16218629",
                    "CAMERA", 1_734_000_000_041L, false, "ROOM-LAUNDRY-1",
                ),
                photo(
                    "room-bedroom", "ROOM", "BEDROOM", "ffd8424544524f4f4dffd9",
                    "6fc318355dea42422fd912e8d7bbd65e9e570e355ddea6d64abee1975c44bfa3",
                    "CAMERA", 1_734_000_000_051L, false, "ROOM-BEDROOM-1",
                ),
                photo(
                    "room-general", "ROOM", "GENERAL", "ffd847454e4552414cffd9",
                    "5625a32ed368ee94d9418e11822797c6e9590ee78b76e5c441c5c2e7234a7f2a",
                    "CAMERA", 1_734_000_000_061L, false, "ROOM-GENERAL-1",
                ),
                photo(
                    "room-exterior", "ROOM", "EXTERIOR", "ffd84558544552494f52ffd9",
                    "3f1ad9ebe309715d181dac0b9d71ae1992462340355db110e5313e5fb2aa250f",
                    "CAMERA", 1_734_000_000_071L, false, "ROOM-EXTERIOR-1",
                ),
                photo(
                    "item-public", "ITEM", "LNG-WALL-01", "ffd85055424c4943ffd9",
                    "2a5d00c8b74598678643cd3f6cb09e8165401f61f5df2e6da62509a8ebb5903e",
                    "CAMERA", 1_734_000_000_101L, false, "PUBLIC-EVIDENCE-1",
                ),
                photo(
                    "item-private", "ITEM", "LNG-WALL-01", "ffd850524956415445ffd9",
                    "2d5afc3f067fd489c2e4fa2fc45a3e66266cac0f1426eee7bf886132da16c48c",
                    "IMPORTED", 1_734_000_000_111L, true, "PRIVATE_PHOTO_GOLDEN_SENTINEL",
                ),
            ),
            fixture.photos,
        )
        fixture.photos.forEach { photo ->
            assertTrue(photo.bytesHex.startsWith("ffd8"), "${photo.key} must start with a JPEG SOI marker")
            assertTrue(photo.bytesHex.endsWith("ffd9"), "${photo.key} must end with a JPEG EOI marker")
            val actual = MessageDigest.getInstance("SHA-256").digest(photo.bytesHex.hexToBytes()).toHex()
            assertEquals(photo.expectedContentHash, actual, "${photo.key} bytes and frozen digest must agree")
        }
    }

    @Test
    fun `fixture freezes the independently precomputed canonical data hash`() {
        val fixture = GoldenEvidenceFixtureLoader.load()
        val canonicalBytes = independentCanonicalBytes(fixture)
        val actual = MessageDigest.getInstance("SHA-256").digest(canonicalBytes).toHex()

        assertEquals("67889661e35bfa7dbc060b5d2e97d2428c3e75f8563c455c50558135a6bee2d0", fixture.expectedDataHash)
        assertEquals(fixture.expectedDataHash, actual, "the frozen digest must match the independently built canonical preimage")
        assertTrue(Regex("[0-9a-f]{64}").matches(fixture.expectedDataHash))
    }

    @Test
    fun `fixture defines positive landlord output and targeted tenant redaction expectations`() {
        val report = GoldenEvidenceFixtureLoader.load().report

        assertEquals("LNG-WALL-01", report.remediationStableId)
        assertContentEquals(
            listOf(
                "PUBLIC_OBJECTIVE_GOLDEN_SENTINEL",
                "LANDLORD_ONLY_GOLDEN_SENTINEL",
                "PRIVATE_PHOTO_GOLDEN_SENTINEL",
            ),
            report.landlordExpectedSentinels,
        )
        assertContentEquals(listOf("PUBLIC_OBJECTIVE_GOLDEN_SENTINEL"), report.tenantExpectedSentinels)
        assertContentEquals(
            listOf("LANDLORD_ONLY_GOLDEN_SENTINEL", "PRIVATE_PHOTO_GOLDEN_SENTINEL"),
            report.tenantForbiddenSentinels,
        )
        assertTrue(report.landlordIncludePrivacyPhotos)
        assertEquals(false, report.tenantIncludePrivacyPhotos)
    }

    private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun photo(
        key: String,
        targetKind: String,
        targetKey: String,
        bytesHex: String,
        hash: String,
        source: String,
        exifTimeMs: Long,
        privacy: Boolean,
        reference: String,
    ) = PhotoEvidenceFixture(
        key = key,
        target = PhotoTargetFixture(targetKind, targetKey),
        bytesHex = bytesHex,
        expectedContentHash = hash,
        source = source,
        exifTimeMs = exifTimeMs,
        privacy = privacy,
        reportReference = reference,
    )

    /** Independent fixture oracle: no production canonical serializer or hash helper is called. */
    private fun independentCanonicalBytes(fixture: GoldenEvidenceFixture): ByteArray {
        val template = assertNotNull(javaClass.getResourceAsStream(fixture.template.resource)).use { input ->
            Json.parseToJsonElement(input.readBytes().toString(Charsets.UTF_8)).jsonObject
        }
        val overrides = fixture.inspection.itemOverrides.associateBy { it.stableId }
        val items = template.getValue("items").jsonArray.map { element ->
            val stableId = element.jsonObject.getValue("stableId").jsonPrimitive.content
            val answer = overrides[stableId]
            obj(
                "stable_id" to JsonPrimitive(stableId),
                "status" to JsonPrimitive(answer?.status ?: fixture.inspection.defaultStatus),
                "note" to (answer?.note?.let(::JsonPrimitive) ?: JsonNull),
                "wear_or_damage" to JsonNull,
            )
        }
        val photos = fixture.photos.map { photo ->
            obj(
                "content_hash" to JsonPrimitive(photo.expectedContentHash),
                "source" to JsonPrimitive(photo.source),
                "exif_time_ms" to JsonPrimitive(photo.exifTimeMs),
                "is_room_level" to JsonPrimitive(photo.target.kind == "ROOM"),
            )
        }
        val root = obj(
            "id" to JsonPrimitive(fixture.inspection.expectedId),
            "type" to JsonPrimitive(fixture.inspection.type),
            "tenancy_id" to JsonPrimitive(fixture.tenancy.id),
            "scheduled_at" to JsonPrimitive(fixture.inspection.scheduledAt),
            "finalized_at" to JsonPrimitive(fixture.inspection.finalizedAt),
            "previous_inspection_id" to JsonNull,
            "baseline_inspection_id" to JsonNull,
            "property" to obj(
                "id" to JsonPrimitive(fixture.property.id),
                "address" to JsonPrimitive(fixture.property.address),
                "kind" to JsonPrimitive(fixture.property.kind),
                "is_boarding_house" to JsonPrimitive(fixture.property.isBoardingHouse),
            ),
            "tenancy" to obj(
                "id" to JsonPrimitive(fixture.tenancy.id),
                "start" to JsonPrimitive(fixture.tenancy.startMs),
                "end" to (fixture.tenancy.endMs?.let(::JsonPrimitive) ?: JsonNull),
            ),
            "template" to obj(
                "id" to JsonPrimitive(fixture.template.expectedId),
                "type" to JsonPrimitive(fixture.template.type),
                "version" to JsonPrimitive(fixture.template.version),
                "content_hash" to JsonPrimitive(fixture.template.expectedContentHash),
            ),
            "items" to JsonArray(items),
            "photos" to JsonArray(photos),
            "audios" to JsonArray(emptyList()),
        )
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    private fun obj(vararg entries: Pair<String, JsonElement>): JsonObject = JsonObject(
        entries.sortedBy { it.first }.associateTo(linkedMapOf()) { it.first to it.second },
    )
}

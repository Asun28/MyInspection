package nz.myinspection.core.e2e

import java.security.MessageDigest
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
        assertEquals("/routine-v1.json", fixture.template.resource)
        assertEquals("ROUTINE", fixture.template.type)
        assertEquals(1, fixture.template.version)
        assertEquals("0193ba74-3c00-72af-8def-012311223344", fixture.template.expectedId)
        assertEquals("0abb0dbe5b71970ee79c5fadc488d8f581d5a0bc4ef78feb204e7a5b753964fb", fixture.template.expectedContentHash)
        assertContentEquals(
            listOf("LOUNGE", "KITCHEN-DINING", "BATHROOM", "LAUNDRY", "BEDROOM", "GENERAL", "EXTERIOR"),
            fixture.template.panoramaRooms,
        )
        assertNotNull(javaClass.getResourceAsStream(fixture.template.resource)).use { input ->
            val actual = MessageDigest.getInstance("SHA-256").digest(input.readBytes()).toHex()
            assertEquals(fixture.template.expectedContentHash, actual, "the referenced real template bytes must stay frozen")
        }

        assertEquals("property-golden-001", fixture.property.id)
        assertEquals("tenancy-golden-001", fixture.tenancy.id)
        assertEquals("0193ba74-3c01-72af-8def-012311223344", fixture.inspection.expectedId)
        assertEquals(1_734_000_000_001L, fixture.inspection.scheduledAt)
        assertEquals(1_734_000_001_001L, fixture.inspection.finalizedAt)
        assertEquals("GOOD", fixture.inspection.defaultStatus)
        assertEquals(
            listOf(ItemAnswerFixture("LNG-WALL-01", "POOR", "PUBLIC_OBJECTIVE_GOLDEN_SENTINEL")),
            fixture.inspection.itemOverrides,
        )
    }

    @Test
    fun `fixture pins byte-exact room item and private photo evidence`() {
        val fixture = GoldenEvidenceFixtureLoader.load()

        assertEquals(9, fixture.photos.size)
        assertContentEquals(
            listOf(
                "room-lounge" to "693d20df1e0cee173de5a4b1db19ca901d0a866301d04a83ea2bb001e82486fd",
                "room-kitchen-dining" to "dc1b2173cc0ed67a72468de41f5c3fadea0255383c934147024fb9b99241bc5b",
                "room-bathroom" to "b59ec3d45c5ff869898063e34d39417b997c337f9d4135e5402b346b43f73ef2",
                "room-laundry" to "8bf6e9fa10db33300d55bf9fc8104c829f34685b1ad9a60416da098d16218629",
                "room-bedroom" to "6fc318355dea42422fd912e8d7bbd65e9e570e355ddea6d64abee1975c44bfa3",
                "room-general" to "5625a32ed368ee94d9418e11822797c6e9590ee78b76e5c441c5c2e7234a7f2a",
                "room-exterior" to "3f1ad9ebe309715d181dac0b9d71ae1992462340355db110e5313e5fb2aa250f",
                "item-public" to "2a5d00c8b74598678643cd3f6cb09e8165401f61f5df2e6da62509a8ebb5903e",
                "item-private" to "2d5afc3f067fd489c2e4fa2fc45a3e66266cac0f1426eee7bf886132da16c48c",
            ),
            fixture.photos.map { it.key to it.expectedContentHash },
        )
        fixture.photos.forEach { photo ->
            assertTrue(photo.bytesHex.startsWith("ffd8"), "${photo.key} must start with a JPEG SOI marker")
            assertTrue(photo.bytesHex.endsWith("ffd9"), "${photo.key} must end with a JPEG EOI marker")
            val actual = MessageDigest.getInstance("SHA-256").digest(photo.bytesHex.hexToBytes()).toHex()
            assertEquals(photo.expectedContentHash, actual, "${photo.key} bytes and frozen digest must agree")
        }
        assertEquals(PhotoTargetFixture("ITEM", "LNG-WALL-01"), fixture.photos[7].target)
        assertEquals(PhotoTargetFixture("ITEM", "LNG-WALL-01"), fixture.photos[8].target)
        assertEquals(false, fixture.photos[7].privacy)
        assertEquals(true, fixture.photos[8].privacy)
        assertEquals("PRIVATE_PHOTO_GOLDEN_SENTINEL", fixture.photos[8].reportReference)
    }

    @Test
    fun `fixture freezes the independently precomputed canonical data hash`() {
        val fixture = GoldenEvidenceFixtureLoader.load()

        assertEquals("67889661e35bfa7dbc060b5d2e97d2428c3e75f8563c455c50558135a6bee2d0", fixture.expectedDataHash)
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
}

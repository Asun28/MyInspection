package nz.myinspection.core.content

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nz.myinspection.core.canon.canonicalJson
import nz.myinspection.core.canon.sha256Hex
import nz.myinspection.core.model.InspectionItemSnapshot
import nz.myinspection.core.model.InspectionSnapshot
import nz.myinspection.core.model.PropertySnapshot
import nz.myinspection.core.model.TemplateSnapshot
import nz.myinspection.core.template.LoadedTemplate
import nz.myinspection.core.template.TemplateItem
import nz.myinspection.core.template.TemplateRoom
import nz.myinspection.core.template.TemplateValidationException
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.fail

/**
 * Content acceptance fixtures, not a new runtime cross-version validator.
 * Mutation receipt (2026-09-07): final UTF-8/LF routine-v2.json SHA-256
 * fd06639f0b7117b1b3cbbf74a71b19cf0f0cc2ee4e374256ad967ad2bdcfe078.
 * Command from worktree root: cmd /c android\gradlew.bat -p android --offline --no-daemon -q
 * --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.content.RoutineContextV2Test"
 * Each mutant exited 1 with a fresh AssertionError in the named test, not a compile failure:
 * - old text drift / old item reorder / summary photoRule change / missing HAL-WALL-01:
 *   `v1 bytes and every old item field and relative position remain unchanged`.
 * - HALLWAY repeatable=true:
 *   `explicit v2 rooms cover the historical keys and Hallway with Bedroom repeatable`.
 * Exact source bytes restored; the same command exited 0 with all four tests passing.
 * Earlier CRLF receipts were superseded and are not evidence for these final source bytes.
 */
class RoutineContextV2Test {
    private val statuses = listOf("GOOD", "FAIR", "POOR", "NOT_APPLICABLE")

    private fun bytes(name: String): ByteArray =
        javaClass.getResourceAsStream("/$name")?.use { it.readBytes() }
            ?: fail("Required template resource missing: $name")

    private fun v2(): LoadedTemplate = LoadedTemplate.parse(bytes("routine-v2.json"))

    @Test
    fun `v1 bytes and every old item field and relative position remain unchanged`() {
        val oldBytes = bytes("routine-v1.json")
        val digest = MessageDigest.getInstance("SHA-256").digest(oldBytes)
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        assertEquals("0abb0dbe5b71970ee79c5fadc488d8f581d5a0bc4ef78feb204e7a5b753964fb", digest)
        val old = LoadedTemplate.parse(oldBytes).template
        assertEquals(83, old.items.size)
        assertEquals(emptyList(), old.rooms)
        val current = v2().template
        assertEquals("ROUTINE", current.type)
        assertEquals(2, current.version)
        val oldIds = old.items.map { it.stableId }.toSet()
        // Whole data-class equality includes area, room, both texts, status order and photo rule.
        // This catches deletion, field drift, duplicate old IDs and reordered historical items.
        assertEquals(old.items, current.items.filter { it.stableId in oldIds })
        assertEquals(additions(), current.items.filterNot { it.stableId in oldIds })
    }

    @Test
    fun `explicit v2 rooms cover the historical keys and Hallway with Bedroom repeatable`() {
        assertEquals(
            listOf(
                TemplateRoom("LOUNGE"),
                TemplateRoom("KITCHEN-DINING"),
                TemplateRoom("BATHROOM"),
                TemplateRoom("LAUNDRY"),
                TemplateRoom("BEDROOM", repeatable = true),
                TemplateRoom("GENERAL"),
                TemplateRoom("EXTERIOR"),
                TemplateRoom("HALLWAY"),
            ),
            v2().template.rooms,
        )
    }

    @Test
    fun `summary status and note are ordinary native hash-covered item fields`() {
        val loaded = v2()
        val summary = loaded.template.items.single { it.stableId == "GEN-SUMMARY-01" }
        assertEquals(statuses, summary.allowedStatuses)
        val item = InspectionItemSnapshot(summary.stableId, "GOOD", "Synthetic summary / 合成摘要", null)
        val snapshot = InspectionSnapshot(
            id = "inspection-fixture",
            type = "ROUTINE",
            tenancyId = null,
            scheduledAt = 1000L,
            finalizedAt = 2000L,
            previousInspectionId = null,
            baselineInspectionId = null,
            property = PropertySnapshot("property-fixture", "Synthetic address", "HOUSE", false),
            tenancy = null,
            template = TemplateSnapshot("template-fixture", "ROUTINE", 2L, loaded.contentHash),
            items = listOf(item),
            photos = emptyList(),
            audios = emptyList(),
        )
        val canonical = canonicalJson(snapshot)
        val projected = Json.parseToJsonElement(canonical).jsonObject.getValue("items").jsonArray.single().jsonObject
        assertEquals("GEN-SUMMARY-01", projected.getValue("stable_id").jsonPrimitive.content)
        assertEquals("GOOD", projected.getValue("status").jsonPrimitive.content)
        assertEquals("Synthetic summary / 合成摘要", projected.getValue("note").jsonPrimitive.content)
        val originalHash = sha256Hex(canonical)
        fun hashWith(changed: InspectionItemSnapshot): String =
            sha256Hex(canonicalJson(snapshot.copy(items = listOf(changed))))
        assertNotEquals(originalHash, sha256Hex(canonicalJson(snapshot.copy(items = emptyList()))))
        assertNotEquals(originalHash, hashWith(item.copy(note = null)))
        assertNotEquals(originalHash, hashWith(item.copy(note = "Changed synthetic summary")))
        assertNotEquals(originalHash, hashWith(item.copy(status = "FAIR")))
    }

    @Test
    fun `existing loader rejects duplicated IDs and a missing translation in v2`() {
        val template = v2().template
        val duplicate = template.copy(items = template.items + template.items.first())
        val duplicateError = assertFailsWith<TemplateValidationException> {
            LoadedTemplate.parse(Json.encodeToString(duplicate).toByteArray(Charsets.UTF_8))
        }
        assertEquals(listOf("${template.items.first().stableId}: duplicate stableId"), duplicateError.errors)
        val summaryIndex = template.items.indexOfFirst { it.stableId == "GEN-SUMMARY-01" }
        val missingTranslation = template.copy(items = template.items.mapIndexed { index, item ->
            if (index == summaryIndex) item.copy(textZh = "") else item
        })
        val translationError = assertFailsWith<TemplateValidationException> {
            LoadedTemplate.parse(Json.encodeToString(missingTranslation).toByteArray(Charsets.UTF_8))
        }
        assertEquals(listOf("GEN-SUMMARY-01: textZh is blank"), translationError.errors)
    }

    private fun additions(): List<TemplateItem> = listOf(
        hallway("WALL", "Walls and ceiling condition", "墙面与天花板状况"),
        hallway("DOOR", "Doors and door furniture condition", "房门及门五金状况"),
        hallway("LIGHT", "Light fittings and switches", "灯具与开关"),
        hallway("POWER", "Power points and sockets", "电源插座"),
        hallway("FLOOR", "Floor and floor coverings condition", "地板及地面铺装状况"),
        hallway("WIN", "Windows and window locks", "窗户与窗锁"),
        hallway("BLIND", "Blinds and curtains", "百叶帘与窗帘"),
        hallway("PANO", "Overall hallway photo record", "走廊整体影像记录", "ROOM_PANORAMA"),
        TemplateItem(
            stableId = "GEN-SUMMARY-01",
            area = "INTERIOR",
            room = "GENERAL",
            textEn = "Inspection summary",
            textZh = "巡检摘要",
            allowedStatuses = statuses,
            photoRule = null,
        ),
    )

    private fun hallway(
        suffix: String,
        english: String,
        chinese: String,
        photoRule: String = "ADVERSE_ONLY",
    ): TemplateItem = TemplateItem(
        stableId = "HAL-$suffix-01",
        area = "INTERIOR",
        room = "HALLWAY",
        textEn = english,
        textZh = chinese,
        allowedStatuses = statuses,
        photoRule = photoRule,
    )
}

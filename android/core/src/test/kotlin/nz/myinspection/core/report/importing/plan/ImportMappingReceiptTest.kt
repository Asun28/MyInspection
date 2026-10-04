package nz.myinspection.core.report.importing.plan

import kotlin.test.*
import org.testng.annotations.Test
import kotlinx.serialization.json.*
import nz.myinspection.core.report.importing.docx.extract.*
import nz.myinspection.core.report.importing.plan.ImportSourceCategory as Category

class ImportMappingReceiptTest {
    @Test fun `literal canonical bytes and independent versioned hash vector`() {
        val review = ImportReview.start(pInput(pManifest()))
        val receipt = receipt(review)
        // Independently calculated using Python hashlib + struct manifest fields + sorted JSON.
        val expected = """{"context":{"has_active_draft":false,"manifest_digest":"dade39f717f3be4181d418a24a67814806e63b7f73f37dc559aa06aaa886993a","property_id":"property-1","report_date":"2026-09-08","rooms":[{"instance_no":1,"room_key":"KITCHEN"},{"instance_no":1,"room_key":"GENERAL"}],"source_sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","suppressed_stable_ids":[],"template":{"content_hash":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","id":"routine-v2","type":"ROUTINE","version":2},"tenancy_id":"tenancy-1"},"sources":[],"summary_status":null,"targets":[{"status":null,"target":{"instance_no":1,"room_key":"KITCHEN","stable_id":"KIT-BENCH-01"}},{"status":null,"target":{"instance_no":1,"room_key":"KITCHEN","stable_id":"KIT-WALL-01"}},{"status":null,"target":{"instance_no":1,"room_key":"GENERAL","stable_id":"GEN-SUMMARY-01"}}],"version":1}"""
        assertEquals(expected, receipt.canonicalJson)
        assertEquals("a29abbc2d21b91995beb4121a51028de42a906e31aacfd0483f8eb52ca901cf5", receipt.sha256)
        assertEquals(3, review.unratedTargets.size)
    }

    @Test fun `every decision alias reason and warning is recorded without source payload`() {
        val secret = pText(3, "Private Author https://vendor.example C:\\private\\source.docx")
        val image = ExtractedImage("word/media/secret.jpg", "c".repeat(64), 100, 100)
        val report = pManifest(items = listOf(pItem("Bench", 1).copy(comment = pText(2, "Secret comment"))),
            fragments = listOf(ExtractedFragment(FragmentRole.ITEM, pText(1, "Bench")),
                ExtractedFragment(FragmentRole.NARRATIVE, pText(5, "Private narrative"))),
            identity = listOf(IdentityCandidate("vendor-author", secret)), summary = listOf(pText(8, "Private summary")),
            captions = listOf(CaptionCandidate("private-number", pText(6, "Private caption"))), images = listOf(image, image.copy(part = "word/media/excluded.jpg")),
            placements = listOf(DrawingPlacement(pText(9, "").source, DrawingKind.INLINE, image.part)),
            warnings = listOf(ExtractionWarning(ExtractionWarningCode.URL_EXCLUDED, secret.source),
                ExtractionWarning(ExtractionWarningCode.IMAGE_REVIEW_REQUIRED, SourceLocation(image.part, 0))))
        val choices = listOf(
            pId(Category.ITEM) to ImportDecision.Item(pBench, "FAIR"),
            pId(Category.FRAGMENT, 1) to ImportDecision.Note(pBench),
            pId(Category.IDENTITY) to ImportDecision.Exclude(ImportExclusionReason.PROVENANCE),
            pId(Category.SUMMARY) to ImportDecision.Summary,
            pId(Category.CAPTION) to ImportDecision.Note(pWall),
            pId(Category.IMAGE) to ImportDecision.Photo(pBench, ImportPrivacy.NO_TENANT_BELONGINGS),
            pId(Category.IMAGE, 1) to ImportDecision.Exclude(ImportExclusionReason.PRIVACY, ImportPrivacy.TENANT_BELONGINGS),
        )
        fun reviewed(order: List<Pair<ImportSourceId, ImportDecision>>) = order.fold(ImportReview.start(pInput(report))) { review, (id, choice) ->
            review.decide(id, choice)
        }.selectSummaryStatus("GOOD")
        val review = reviewed(choices)
        val receipt = receipt(review)
        val reverse = receipt(reviewed(choices.reversed()))
        assertEquals(receipt.canonicalJson, reverse.canonicalJson)
        assertEquals(receipt.sha256, reverse.sha256)
        listOf("Private", "Secret", "private", "vendor", "word/", "https:", "source.docx", "caption", "narrative").forEach {
            assertFalse(receipt.canonicalJson.contains(it), "Leaked source payload: $it")
        }
        val json = Json.parseToJsonElement(receipt.canonicalJson).jsonObject
        val sources = json.getValue("sources").jsonArray.map { it.jsonObject }
        assertEquals(listOf("ITEM:0", "FRAGMENT:0", "FRAGMENT:1", "IDENTITY:0", "SUMMARY:0", "CAPTION:0", "IMAGE:0", "IMAGE:1", "PLACEMENT:0", "WARNING:0", "WARNING:1"),
            sources.map { it.getValue("id").jsonPrimitive.content })
        fun source(id: String) = sources.single { it.getValue("id").jsonPrimitive.content == id }
        assertEquals("ITEM:0", source("FRAGMENT:0").getValue("owner").jsonPrimitive.content)
        assertEquals("IMAGE:0", source("PLACEMENT:0").getValue("owner").jsonPrimitive.content)
        val item = source("ITEM:0").getValue("decision").jsonObject
        assertEquals("FAIR", item.getValue("status").jsonPrimitive.content)
        assertEquals("KIT-BENCH-01", item.getValue("target").jsonObject.getValue("stable_id").jsonPrimitive.content)
        assertEquals("NOTE", source("CAPTION:0").getValue("decision").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("KIT-WALL-01", source("CAPTION:0").getValue("decision").jsonObject.getValue("target").jsonObject.getValue("stable_id").jsonPrimitive.content)
        assertEquals("SUMMARY", source("SUMMARY:0").getValue("decision").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("GOOD", json.getValue("summary_status").jsonPrimitive.content)
        assertEquals("NO_TENANT_BELONGINGS", source("IMAGE:0").getValue("decision").jsonObject.getValue("privacy").jsonPrimitive.content)
        assertEquals("TENANT_BELONGINGS", source("IMAGE:1").getValue("decision").jsonObject.getValue("privacy").jsonPrimitive.content)
        assertEquals("PRIVACY", source("IMAGE:1").getValue("reason").jsonPrimitive.content)
        assertEquals("PROVENANCE", source("IDENTITY:0").getValue("reason").jsonPrimitive.content)
        assertEquals("URL_EXCLUDED", source("WARNING:0").getValue("warning").jsonPrimitive.content)
        assertEquals("URL", source("WARNING:0").getValue("reason").jsonPrimitive.content)
        assertEquals("CONFIRMED", source("WARNING:1").getValue("state").jsonPrimitive.content)
        assertEquals("EXCLUDED", source("IDENTITY:0").getValue("state").jsonPrimitive.content)
        assertEquals(report.normalizedDigest, json.getValue("context").jsonObject.getValue("manifest_digest").jsonPrimitive.content)
        assertEquals(listOf("FAIR", null, "GOOD"), json.getValue("targets").jsonArray.map { it.jsonObject.getValue("status").jsonPrimitive.contentOrNull })
        val changedReceipts = listOf(review.selectSummaryStatus("FAIR"),
            review.replace(pId(Category.IMAGE), ImportDecision.Photo(pBench, ImportPrivacy.TENANT_BELONGINGS)),
            review.replace(pId(Category.CAPTION), ImportDecision.Note(pBench))).map { receipt(it).sha256 }
        assertEquals(3, changedReceipts.distinct().size)
        assertFalse(receipt.sha256 in changedReceipts)
    }

    @Test fun `material context source and decisions change the receipt commitment`() {
        val empty = pManifest()
        val base = receipt(ImportReview.start(pInput(empty)))
        listOf(pInput(empty, property = "property-2"), pInput(empty, tenancy = "tenancy-2"),
            pInput(empty, date = "2026-09-09"), pInput(empty, sha = "d".repeat(64)),
            pInput(empty, binding = pBinding(id = "new-template")), pInput(empty, binding = pBinding(hash = "e".repeat(64))),
            pInput(empty, suppressed = setOf("KIT-WALL-01")),
            pInput(empty, rooms = pInput(empty).roomInstances.reversed())).forEach {
            assertNotEquals(base.sha256, receipt(ImportReview.start(it)).sha256)
        }
        val report = pManifest(items = listOf(pItem("Bench", 1)))
        val review = ImportReview.start(pInput(report)).decide(pId(Category.ITEM), ImportDecision.Item(pBench, "GOOD"))
        val original = receipt(review).sha256
        listOf(review.replace(pId(Category.ITEM), ImportDecision.Item(pBench, "FAIR")),
            review.replace(pId(Category.ITEM), ImportDecision.Item(pWall, "GOOD")),
            review.replace(pId(Category.ITEM), ImportDecision.Exclude(ImportExclusionReason.DUPLICATE)),
            review.replace(pId(Category.ITEM), ImportDecision.Exclude(ImportExclusionReason.NOT_RELEVANT)),
            ImportReview.start(pInput(pManifest(items = listOf(pItem("Bench", 1).copy(comment = pText(8, "different bytes"))))))
                .decide(pId(Category.ITEM), ImportDecision.Item(pBench, "GOOD"))).map { receipt(it).sha256 }.let {
            assertEquals(it.size, it.distinct().size)
            assertFalse(original in it)
        }
        val suppressed = setOf("KIT-WALL-01", "KIT-BENCH-01")
        val rooms = listOf(ImportRoomInstance("GENERAL", 1, "GENERAL"))
        assertEquals(receipt(ImportReview.start(pInput(empty, suppressed = suppressed, rooms = rooms))).canonicalJson,
            receipt(ImportReview.start(pInput(empty, suppressed = suppressed.reversed().toSet(), rooms = rooms))).canonicalJson)
    }

    private fun receipt(review: ImportReview) = ImportMappingReceipt.create(review, ImportReviewPreview.capture(review))
}

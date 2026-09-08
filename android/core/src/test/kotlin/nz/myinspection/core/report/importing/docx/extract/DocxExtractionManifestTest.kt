package nz.myinspection.core.report.importing.docx.extract

import java.nio.charset.CharacterCodingException
import kotlin.test.*

class DocxExtractionManifestTest {
    private val at = SourceLocation("word/document.xml", 7, 2)
    private val rawName = " \tCafe" + 0x0301.toChar() + "\r\n 東京 😀  "
    private val name = ExtractedText(at, rawName)
    private val status = ExtractedText(SourceLocation("word/header1.xml", 8), " Fair\t")
    private val comment = ExtractedText(SourceLocation("word/footer1.xml", 9, 1), "  Check\n hinge ")
    private val item = ExtractedItem("Kitchen", name, status, comment)
    private val fragment = ExtractedFragment(FragmentRole.ITEM, name)
    private val identity = IdentityCandidate("address", name)
    private val caption = CaptionCandidate("01", comment)
    private val image = ExtractedImage("word/media/写真.png", "0123456789abcdef".repeat(4), 640, 480)
    private val placement = DrawingPlacement(at, DrawingKind.ANCHORED, image.part)
    private val warning = ExtractionWarning(ExtractionWarningCode.AMBIGUOUS_COLUMNS, at)
    private val items = listOf(item, item.copy(room = null, status = null, comment = null))
    private val fragments = listOf(fragment, ExtractedFragment(FragmentRole.UNKNOWN, status))
    private val identities = listOf(identity, IdentityCandidate("date", status))
    private val summary = listOf(comment, name)
    private val captions = listOf(caption, CaptionCandidate("02", name))
    private val images = listOf(image, ExtractedImage("word/media/unknown", "f".repeat(64), null, null))
    private val placements = listOf(placement, DrawingPlacement(at.copy(ordinal = 10, occurrence = 0), DrawingKind.INLINE, null))
    private val warnings = listOf(warning, ExtractionWarning(ExtractionWarningCode.MISSING_IMAGE, null))

    private fun manifest(
        items: List<ExtractedItem> = this.items,
        fragments: List<ExtractedFragment> = this.fragments,
        warnings: List<ExtractionWarning> = this.warnings,
        identity: List<IdentityCandidate> = identities,
        summary: List<ExtractedText> = this.summary,
        captions: List<CaptionCandidate> = this.captions,
        images: List<ExtractedImage> = this.images,
        placements: List<DrawingPlacement> = this.placements,
    ) = DocxExtractionManifest(items, fragments, warnings, identity, summary, captions, images, placements)

    private fun changed(label: String, value: DocxExtractionManifest) {
        assertNotEquals(manifest().normalizedDigest, value.normalizedDigest, label)
    }

    @Test fun normalizationPreservesRawSpellingAndSource() {
        assertEquals(rawName, name.raw)
        assertEquals(SourceLocation("word/document.xml", 7, 2), name.source)
        assertEquals("Café 東京 😀", name.normalized)
        assertEquals("Fair", status.normalized)
        assertEquals("Check hinge", comment.normalized)
        assertEquals("", ExtractedText(at, " \t\r\n ").normalized)
    }

    @Test fun foldingUsesOnlyTheSixAsciiWhitespaceCharacters() {
        for (code in listOf(0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x20)) {
            val raw = "left" + code.toChar() + code.toChar() + "right"
            val result = manifest(summary = listOf(ExtractedText(at, raw))).summaryCandidates.single()
            assertEquals(raw, result.raw)
            assertEquals("left right", result.normalized, "ASCII U+${code.toString(16)}")
        }
        val nonAscii = listOf(0x85, 0xA0, 0x1680) + (0x2000..0x200A) + listOf(0x2028, 0x2029, 0x202F, 0x205F, 0x3000)
        for (code in nonAscii) {
            val raw = "left" + code.toChar() + code.toChar() + "right"
            val canonical = when (code) { 0x2000 -> 0x2002; 0x2001 -> 0x2003; else -> code }.toChar()
            val result = manifest(summary = listOf(ExtractedText(at, raw))).summaryCandidates.single()
            assertEquals(raw, result.raw)
            assertEquals("left" + canonical + canonical + "right", result.normalized, "Non-ASCII U+${code.toString(16)}")
        }
        val raw = "left" + 0xA0.toChar() + "middle" + 0x2003.toChar() + "right"
        val result = manifest(emptyList(), emptyList(), emptyList(), emptyList(),
            listOf(ExtractedText(at, raw)), emptyList(), emptyList(), emptyList())
        // Independent literal UTF-8/BE32 vector: 22 fields, 230 bytes.
        assertEquals("b9f8e3cc32268a7fef2de0c2d8f773dd7107986fc5642984860afd95757a5ba8", result.normalizedDigest)
    }

    @Test fun emptyDigestMatchesIndependentWireVector() {
        // Python struct.pack('>i') with literal fields: 17 fields, 151 bytes.
        val empty = manifest(emptyList(), emptyList(), emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals("DOCX-EXTRACT-1", empty.extractorVersion)
        assertEquals("dade39f717f3be4181d418a24a67814806e63b7f73f37dc559aa06aaa886993a", empty.normalizedDigest)
    }

    @Test fun nonemptyDigestMatchesIndependentWireVector() {
        // Independent literal UTF-8/BE32 vector: 121 fields, 1472 bytes; includes nulls and non-ASCII text.
        assertEquals("28708488ce97a58cccf00b6445f6f7a066b83d15e79da6d07d99a9448c63d886", manifest().normalizedDigest)
    }

    @Test fun allEightCollectionsCopyInputsBeforePublishingReadOnlyViews() {
        val inputItems = items.toMutableList()
        val inputFragments = fragments.toMutableList()
        val inputWarnings = warnings.toMutableList()
        val inputIdentity = identities.toMutableList()
        val inputSummary = summary.toMutableList()
        val inputCaptions = captions.toMutableList()
        val inputImages = images.toMutableList()
        val inputPlacements = placements.toMutableList()
        val result = manifest(inputItems, inputFragments, inputWarnings, inputIdentity,
            inputSummary, inputCaptions, inputImages, inputPlacements)
        val digest = result.normalizedDigest
        val expected = listOf(items, fragments, warnings, identities, summary, captions, images, placements)
        val published = listOf(result.items, result.fragments, result.warnings, result.identity,
            result.summaryCandidates, result.captions, result.images, result.placements)
        listOf(inputItems, inputFragments, inputWarnings, inputIdentity, inputSummary,
            inputCaptions, inputImages, inputPlacements).forEach { it.clear() }
        assertEquals(expected, published)
        for (values in published) {
            @Suppress("UNCHECKED_CAST")
            val mutable = values as MutableList<Any?>
            assertFailsWith<UnsupportedOperationException> { mutable.clear() }
            assertFailsWith<UnsupportedOperationException> { mutable.add(values.first()) }
            assertFailsWith<UnsupportedOperationException> { mutable[0] = values.last() }
        }
        assertEquals(expected, published)
        assertEquals(digest, result.normalizedDigest)
    }

    @Test fun eachCollectionOrderAndMultiplicityAffectsTheDigest() {
        val reordered = listOf(
            "items" to manifest(items = items.reversed()),
            "fragments" to manifest(fragments = fragments.reversed()),
            "warnings" to manifest(warnings = warnings.reversed()),
            "identity" to manifest(identity = identities.reversed()),
            "summary" to manifest(summary = summary.reversed()),
            "captions" to manifest(captions = captions.reversed()),
            "images" to manifest(images = images.reversed()),
            "placements" to manifest(placements = placements.reversed()),
        )
        val duplicated = listOf(
            "items" to manifest(items = items + item),
            "fragments" to manifest(fragments = fragments + fragment),
            "warnings" to manifest(warnings = warnings + warning),
            "identity" to manifest(identity = identities + identity),
            "summary" to manifest(summary = summary + name),
            "captions" to manifest(captions = captions + caption),
            "images" to manifest(images = images + image),
            "placements" to manifest(placements = placements + placement),
        )
        for ((label, value) in reordered + duplicated) changed(label, value)
    }

    @Test fun scalarEvidenceFieldsEachAffectTheDigest() {
        val cases = listOf(
            "room" to manifest(items = listOf(item.copy(room = "Hall"), items[1])),
            "fragment role" to manifest(fragments = listOf(fragment.copy(role = FragmentRole.ROOM), fragments[1])),
            "identity field" to manifest(identity = listOf(identity.copy(field = "title"), identities[1])),
            "caption number" to manifest(captions = listOf(caption.copy(number = "1"), captions[1])),
            "image part" to manifest(images = listOf(image.copy(part = "word/media/other.png"), images[1])),
            "image SHA" to manifest(images = listOf(image.copy(sha256 = "a".repeat(64)), images[1])),
            "image width" to manifest(images = listOf(image.copy(width = 641), images[1])),
            "image height" to manifest(images = listOf(image.copy(height = 481), images[1])),
            "drawing kind" to manifest(placements = listOf(placement.copy(kind = DrawingKind.INLINE), placements[1])),
            "drawing image" to manifest(placements = listOf(placement.copy(imagePart = "word/media/other.png"), placements[1])),
            "warning code" to manifest(warnings = listOf(warning.copy(code = ExtractionWarningCode.IMAGE_REVIEW_REQUIRED), warnings[1])),
        )
        for ((label, value) in cases) changed(label, value)
        for (source in listOf(at.copy(part = "word/header2.xml"), at.copy(ordinal = 8), at.copy(occurrence = 3))) {
            changed("drawing source $source", manifest(placements = listOf(placement.copy(source = source), placements[1])))
            changed("warning source $source", manifest(warnings = listOf(warning.copy(source = source), warnings[1])))
        }
    }

    @Test fun everyTextSlotCommitsSourceCoordinatesAndRawSpelling() {
        val slots: List<Pair<ExtractedText, (ExtractedText) -> DocxExtractionManifest>> = listOf(
            name to { text -> manifest(items = listOf(item.copy(name = text), items[1])) },
            status to { text -> manifest(items = listOf(item.copy(status = text), items[1])) },
            comment to { text -> manifest(items = listOf(item.copy(comment = text), items[1])) },
            name to { text -> manifest(fragments = listOf(fragment.copy(text = text), fragments[1])) },
            name to { text -> manifest(identity = listOf(identity.copy(text = text), identities[1])) },
            comment to { text -> manifest(summary = listOf(text, name)) },
            comment to { text -> manifest(captions = listOf(caption.copy(text = text), captions[1])) },
        )
        for ((index, slot) in slots.withIndex()) {
            val (text, build) = slot
            val source = text.source
            for (variant in listOf(text.copy(raw = text.raw + "!"),
                text.copy(source = source.copy(part = "word/other.xml")),
                text.copy(source = source.copy(ordinal = source.ordinal + 1)),
                text.copy(source = source.copy(occurrence = source.occurrence + 1)))) {
                changed("text slot $index: $variant", build(variant))
            }
        }
    }

    @Test fun absentValuesRemainDistinctFromEmptyTextAndZeroDimensions() {
        val blank = ExtractedText(at, "")
        val absent = items[1]
        val cases = listOf(
            "room" to manifest(items = listOf(item, absent.copy(room = ""))),
            "status" to manifest(items = listOf(item, absent.copy(status = blank))),
            "comment" to manifest(items = listOf(item, absent.copy(comment = blank))),
            "width" to manifest(images = listOf(image, images[1].copy(width = 0))),
            "height" to manifest(images = listOf(image, images[1].copy(height = 0))),
            "image reference" to manifest(placements = listOf(placement, placements[1].copy(imagePart = ""))),
            "warning source" to manifest(warnings = listOf(warning, warnings[1].copy(source = SourceLocation("", 0)))),
        )
        for ((label, value) in cases) changed(label, value)
    }

    @Test fun lengthPrefixesDistinguishAdjacentFieldPartitions() {
        val first = manifest(images = listOf(image.copy(part = "a", sha256 = "bc")))
        val second = manifest(images = listOf(image.copy(part = "ab", sha256 = "c")))
        assertNotEquals(first.normalizedDigest, second.normalizedDigest)
    }

    @Test fun malformedUtf16IsRejectedInEveryStringCarrier() {
        val invalid = listOf(0xD800.toChar().toString(), 0xDC00.toChar().toString(),
            "before" + 0xD800.toChar() + "after", 0xDC00.toChar().toString() + 0xD800.toChar())
        for (raw in invalid) {
            val badText = name.copy(raw = raw)
            val badSource = at.copy(part = raw)
            val cases: List<() -> DocxExtractionManifest> = listOf(
                { manifest(items = listOf(item.copy(room = raw))) },
                { manifest(items = listOf(item.copy(name = badText))) },
                { manifest(items = listOf(item.copy(status = badText))) },
                { manifest(items = listOf(item.copy(comment = badText))) },
                { manifest(fragments = listOf(fragment.copy(text = badText))) },
                { manifest(identity = listOf(identity.copy(field = raw))) },
                { manifest(identity = listOf(identity.copy(text = badText))) },
                { manifest(summary = listOf(badText)) },
                { manifest(captions = listOf(caption.copy(number = raw))) },
                { manifest(captions = listOf(caption.copy(text = badText))) },
                { manifest(images = listOf(image.copy(part = raw))) },
                { manifest(images = listOf(image.copy(sha256 = raw))) },
                { manifest(placements = listOf(placement.copy(imagePart = raw))) },
                { manifest(placements = listOf(placement.copy(source = badSource))) },
                { manifest(warnings = listOf(warning.copy(source = badSource))) },
                { manifest(summary = listOf(name.copy(source = badSource))) },
            )
            for ((index, build) in cases.withIndex()) {
                assertFailsWith<CharacterCodingException>("string carrier $index") { build() }
            }
        }
    }
}

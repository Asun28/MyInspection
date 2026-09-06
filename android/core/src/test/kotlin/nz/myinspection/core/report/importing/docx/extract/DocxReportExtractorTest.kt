package nz.myinspection.core.report.importing.docx.extract

import kotlin.test.*
import java.security.MessageDigest
import nz.myinspection.core.report.importing.docx.`package`.DocxPackageException
import nz.myinspection.core.report.importing.docx.`package`.DocxPackageReason

class DocxReportExtractorTest {
    private val fixture = DocxExtractorFixture
    private fun extract(parts: Map<String, ByteArray>) = DocxReportExtractor().extract(fixture.read(parts))

    @Test fun realTableCellsPreserveNullableStatusAndRawSpelling() {
        val result = extract(fixture.parts(fixture.p("Kitchen") + "<w:tbl>" +
            fixture.row("  Window latch  ", "  FaIr  ", "Slight resistance") +
            fixture.row("Sink", "", "Water flows") + "</w:tbl>"))
        assertEquals(listOf("  Window latch  ", "Sink"), result.items.map { it.name.raw })
        assertEquals("Window latch", result.items[0].name.normalized)
        assertEquals("  FaIr  ", result.items[0].status?.raw)
        assertEquals("Slight resistance", result.items[0].comment?.raw)
        assertNull(result.items[1].status)
        assertEquals("Water flows", result.items[1].comment?.raw)
        assertEquals(listOf("Kitchen", "Kitchen"), result.items.map { it.room })
    }

    @Test fun everyStoryIsVisitedIncludingUnreferencedHeadersAndFooters() {
        val result = extract(fixture.parts(fixture.p("Body observation")))
        val texts = result.fragments.map { it.text.raw }
        assertTrue("Body observation" in texts)
        for (i in 1..12) {
            assertTrue("Unique header observation $i" in texts, "header $i lost")
            assertTrue("Unique footer observation $i" in texts, "footer $i lost")
        }
        assertEquals(25, result.fragments.map { it.text.source.part }.distinct().size)
        assertTrue(result.warnings.any { it.code == ExtractionWarningCode.UNRESOLVED_TEXT })
    }

    @Test fun pageAndAuthorFieldsDoNotSwallowAdjacentEvidence() {
        val fields = "<w:p><w:r><w:t>Before </w:t></w:r>" +
            "<w:fldSimple w:instr='PAGE'><w:r><w:t>999</w:t></w:r></w:fldSimple>" +
            "<w:r><w:fldChar w:fldCharType='begin'/></w:r><w:r><w:instrText> NUMPAGES </w:instrText></w:r>" +
            "<w:r><w:fldChar w:fldCharType='separate'/></w:r><w:r><w:t>888</w:t></w:r>" +
            "<w:r><w:fldChar w:fldCharType='end'/></w:r><w:r><w:t> after</w:t></w:r></w:p>" +
            "<w:fldSimple w:instr='AUTHOR'>${fixture.p("Excluded private author")}</w:fldSimple>" +
            fixture.p("See https://synthetic.invalid/secret for context") +
            "<w:sdt><w:sdtPr><w:tag w:val='MSIP_Label_Synthetic'/></w:sdtPr><w:sdtContent>" +
            fixture.p("Excluded sensitivity value") + "</w:sdtContent></w:sdt>"
        val result = extract(fixture.parts(fields))
        val text = (result.fragments.map { it.text } + result.items.flatMap { listOfNotNull(it.name, it.status, it.comment) } +
            result.identity.map { it.text } + result.summaryCandidates + result.captions.map { it.text }).joinToString("|") { it.raw }
        assertTrue("Before  after" in text)
        for (secret in listOf("999", "888", "Excluded private author", "synthetic.invalid", "Excluded sensitivity value")) {
            assertFalse(secret in text, "excluded source field survived")
        }
        assertTrue("See " in text && " for context" in text)
        assertTrue(result.warnings.any { it.code == ExtractionWarningCode.PAGINATION_EXCLUDED })
        assertTrue(result.warnings.any { it.code == ExtractionWarningCode.METADATA_EXCLUDED })
        assertTrue(result.warnings.any { it.code == ExtractionWarningCode.URL_EXCLUDED })
    }

    @Test fun nestedTextboxesAndUnknownTableCellsRemainReviewable() {
        val nested = "<w:p><w:r><w:t>Outer observation</w:t><w:drawing><wp:anchor><w:txbxContent>" +
            fixture.p("Nested observation") + "</w:txbxContent></wp:anchor></w:drawing></w:r></w:p>" +
            "<w:tbl><w:tr><w:tc>${fixture.p("One-cell note")}</w:tc></w:tr></w:tbl>"
        val result = extract(fixture.parts(nested))
        assertEquals(listOf("Outer observation", "Nested observation", "One-cell note"),
            result.fragments.filter { it.text.source.part == "word/document.xml" }.map { it.text.raw })
        val note = result.fragments.single { it.text.raw == "One-cell note" }.text
        assertTrue(result.warnings.any { it.code == ExtractionWarningCode.UNRESOLVED_TEXT && it.source == note.source })
    }

    @Test fun multipleNamesInOneTableCellRemainFragmentsWithTheirOwnBlocker() {
        val row = "<w:tr><w:tc>${fixture.p("First candidate")}${fixture.p("Second candidate")}</w:tc>" +
            "<w:tc>${fixture.p("Fair")}</w:tc><w:tc>${fixture.p("Unassigned note")}</w:tc></w:tr>"
        val result = extract(fixture.parts("<w:tbl>$row</w:tbl>"))
        assertTrue(result.items.isEmpty())
        assertEquals(listOf("First candidate", "Second candidate", "Fair", "Unassigned note"),
            result.fragments.filter { it.text.source.part == "word/document.xml" }.map { it.text.raw })
        val first = result.fragments.first().text.source
        assertTrue(result.warnings.any { it.code == ExtractionWarningCode.UNRESOLVED_TEXT && it.source == first })
    }

    @Test fun trailingRoomHeadingAffectsFollowingItemOnly() {
        val rows = "<w:tr><w:tc>${fixture.p("Old room item")}${fixture.p("Kitchen")}</w:tc>" +
            "<w:tc>${fixture.p("Good")}</w:tc><w:tc>${fixture.p("Old observation")}</w:tc></w:tr>"
        val result = extract(fixture.parts(fixture.p("Lounge") + "<w:tbl>" + rows +
            fixture.row("New room item", "Good", "New observation") + "</w:tbl>"))
        assertEquals(listOf("Lounge", "Kitchen"), result.items.map { it.room })
    }

    @Test fun unknownFieldResultsRemainUnresolved() {
        val unknown = "<w:p><w:fldSimple w:instr='QUOTE'><w:r><w:t>Cached observation</w:t></w:r></w:fldSimple></w:p>"
        assertTrue(extract(fixture.parts(unknown)).fragments.any { it.text.raw == "Cached observation" })
    }
    @Test fun openFieldsRejectSafelyInsteadOfSwallowingLaterObservations() {
        val broken = "<w:p><w:r><w:fldChar w:fldCharType='begin'/><w:instrText>PAGE</w:instrText>" +
            "<w:fldChar w:fldCharType='separate'/><w:t>7</w:t></w:r></w:p>" + fixture.p("Real observation after broken field")
        val error = assertFailsWith<IllegalArgumentException> { extract(fixture.parts(broken)) }
        assertEquals("DOCX_FIELD_STRUCTURE", error.message)
        assertNull(error.cause)
    }

    @Test fun fragmentedSampleKeeps64ItemNamesAndOnly24CellBasedRows() {
        val result = extract(fixture.sample())
        assertEquals(64, result.items.size)
        val names = buildList {
            (1..10).forEach { add("Outer feature $it") }; (1..6).forEach { add("Passage feature $it") }
            (1..14).forEach { add("Washroom feature $it") }; (1..7).forEach { add("Sleeping feature $it") }
            (1..2).forEach { add("Second sleeping feature $it") }; (0..23).forEach { add("  Tabular feature $it  ") }
            add("Overall impression")
        }
        assertEquals(names, result.items.map { it.name.raw })
        assertEquals(24, result.items.count { it.comment?.raw?.startsWith("Table observation") == true })
        assertTrue(result.items.take(39).all { it.status == null && it.comment == null })
        assertEquals(22, result.items.count { it.status != null })
        assertEquals("Overall impression", result.items.last().name.raw)
        assertEquals(40, result.fragments.count { it.text.raw.startsWith("Unassigned ") })
        assertTrue(result.fragments.any { it.text.raw == "Undecided spelling" })
        assertTrue(result.warnings.any { it.code == ExtractionWarningCode.AMBIGUOUS_COLUMNS })
        assertEquals(listOf("42 Synthetic Lane", "3 September 2026", "Inspection (03/09/2026)"), result.identity.map { it.text.raw })
        assertEquals("Original synthetic summary.", result.summaryCandidates.single().raw)
        assertTrue(result.warnings.any { it.code == ExtractionWarningCode.UNRESOLVED_NARRATIVE })
        assertEquals(89, result.captions.size)
        assertEquals((1..89).map { it.toString().padStart(3, '0') }, result.captions.map { it.number })
        assertEquals(67, result.images.size)
        assertEquals(68, result.placements.size)
        assertEquals(2, result.placements.count { it.imagePart == "word/media/photo67.png" })
        assertTrue(result.placements.any { it.kind == DrawingKind.INLINE })
        assertTrue(result.placements.any { it.kind == DrawingKind.ANCHORED })
        assertTrue(result.warnings.any { it.code == ExtractionWarningCode.AMBIGUOUS_CAPTIONS })
        assertEquals(15, result.warnings.count { it.code == ExtractionWarningCode.LAYOUT_IMAGE_EXCLUDED })
    }

    @Test fun damagedAndRepeatedCaptionTextIsNeverCorrectedOrPaired() {
        val raw = "047-Room Gamma 2 2048-Room Gamma 2 3"
        val parts = fixture.parts(fixture.p("Images") + fixture.p(raw) + fixture.p("ABC-Area Alpha 11") + fixture.p("4 5"))
        parts["word/footer1.xml"] = fixture.story("ftr", fixture.p(raw)).toByteArray()
        val result = extract(parts)
        assertEquals(listOf("047", "2048", "047", "2048"), result.captions.map { it.number })
        assertEquals(4, result.captions.map { it.text.source }.distinct().size)
        assertEquals(2, result.fragments.count { it.text.raw == raw })
        assertTrue(result.fragments.any { it.text.raw == "ABC-Area Alpha 11" })
        assertTrue(result.fragments.any { it.text.raw == "4 5" })
        assertTrue(result.warnings.any { it.code == ExtractionWarningCode.AMBIGUOUS_CAPTIONS })
    }

    @Test fun unmarkedSignoffStaysInsideBlockedNarrativeCandidates() {
        val result = extract(fixture.parts(fixture.p("Comments X Summary") + fixture.p("Synthetic report narrative") +
            fixture.p("Anonymous signoff candidate") + fixture.p("Images")))
        assertEquals(listOf("Synthetic report narrative", "Anonymous signoff candidate"), result.summaryCandidates.map { it.raw })
        assertTrue(result.warnings.any { it.code == ExtractionWarningCode.UNRESOLVED_NARRATIVE })
    }

    @Test fun missingDrawingTargetsAndMalformedImageHeadersRemainBlockers() {
        val parts = fixture.parts(fixture.drawing("absent") + fixture.drawing("bad", "anchor"))
        parts["word/media/bad.png"] = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        parts["word/_rels/document.xml.rels"] = fixture.relationships(fixture.relationship("bad", "media/bad.png", "image")).toByteArray()
        val result = extract(parts)
        assertEquals(2, result.placements.size)
        assertNull(result.placements[0].imagePart)
        assertEquals(1, result.images.size)
        assertNull(result.images.single().width)
        assertTrue(result.warnings.any { it.code == ExtractionWarningCode.MISSING_IMAGE })
        assertTrue(result.warnings.any { it.code == ExtractionWarningCode.IMAGE_REVIEW_REQUIRED })
    }

    @Test fun retainedImageDimensionsPreserveClosedPixelBounds() {
        val parts = fixture.parts(fixture.drawing("photo"))
        parts["word/media/photo.jpg"] = fixture.image(32, 4, "jpg")
        parts["word/_rels/document.xml.rels"] = fixture.relationships(fixture.relationship("photo", "media/photo.jpg", "image")).toByteArray()
        val result = extract(parts)
        assertEquals(32, result.images.single().width)
        assertEquals(32, result.images.single().height)
        val huge = fixture.image(32, 1)
        huge[16] = 127
        parts["word/media/huge.png"] = fixture.repairPngCrc(huge)
        val error = assertFailsWith<IllegalArgumentException> { extract(parts) }
        assertEquals("DOCX_IMAGE_PIXELS", error.message)
    }

    @Test fun digestIgnoresZipAndRelationshipDeclarationOrderButBindsRawEvidence() {
        val parts = fixture.sample()
        val first = extract(parts)
        val reordered = parts.entries.reversed().associate { it.toPair() }
        assertEquals(first.normalizedDigest, DocxReportExtractor().extract(fixture.read(reordered, false)).normalizedDigest)
        val relName = "word/_rels/document.xml.rels"
        val reversedRelations = parts.toMutableMap().apply {
            this[relName] = fixture.relationships(Regex("<Relationship\\s[^>]+/>").findAll(getValue(relName).toString(Charsets.UTF_8))
                .map { it.value }.toList().reversed().joinToString("")).toByteArray()
        }
        assertEquals(first.normalizedDigest, extract(reversedRelations).normalizedDigest)
        assertTrue(first.normalizedDigest.matches(Regex("[a-f0-9]{64}")))
        parts["word/document.xml"] = parts.getValue("word/document.xml").toString(Charsets.UTF_8)
            .replace("  FaIr  ", " FaIr  ").toByteArray()
        assertNotEquals(first.normalizedDigest, extract(parts).normalizedDigest)
        assertNotEquals(first.normalizedDigest, extract(fixture.sample().apply { this["word/media/photo1.png"] = fixture.image(32, 321) }).normalizedDigest)
        val changedOrder = fixture.sample().apply {
            this["word/document.xml"] = getValue("word/document.xml").toString(Charsets.UTF_8).replace("Outer feature 1<", "swap<")
                .replace("Outer feature 2<", "Outer feature 1<").replace("swap<", "Outer feature 2<").toByteArray()
        }
        assertNotEquals(first.normalizedDigest, extract(changedOrder).normalizedDigest)
        assertFailsWith<UnsupportedOperationException> { (first.items as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (first.placements as MutableList).clear() }
    }

    @Test fun emptyManifestMatchesIndependentlySerializedDigest() {
        val parts = fixture.parts("")
        for (i in 1..12) for ((kind, root) in listOf("header" to "hdr", "footer" to "ftr"))
            parts["word/$kind$i.xml"] = fixture.story(root, "").toByteArray()
        // Independent .NET BE32/UTF8 serialization: 17 fields, 151 bytes.
        assertEquals("dade39f717f3be4181d418a24a67814806e63b7f73f37dc559aa06aaa886993a", extract(parts).normalizedDigest)
    }
    @Test fun labelledIsoDateCannotBeStolenByCaptionHeuristics() {
        val result = extract(fixture.parts(fixture.p("INSPECTION DATE") + fixture.p("2026-09-06") + fixture.p("Ordinary observation")))
        assertEquals(listOf("2026-09-06"), result.identity.map { it.text.raw })
        assertTrue(result.captions.isEmpty())
    }
    @Test fun truncatedOrCorruptTinyPngHeadersAreNotDiscarded() {
        val valid = fixture.image(1, 1)
        for (bytes in listOf(valid.copyOf(24), valid.copyOf().apply { this[32] = (this[32].toInt() xor 1).toByte() })) {
            val result = extract(fixture.parts().apply { this["word/media/tiny.png"] = bytes })
            assertEquals(1, result.images.size)
            assertNull(result.images.single().width)
        }
    }
    @Test fun nestedParagraphSegmentsKeepTheirActualEncounterOrder() {
        val body = "<w:p><w:r><w:t>Before</w:t><w:drawing><wp:anchor><w:txbxContent>${fixture.p("Inside")}" +
            "</w:txbxContent></wp:anchor></w:drawing><w:t>After</w:t></w:r></w:p>"
        val fragments = extract(fixture.parts(body)).fragments.filter { it.text.source.part == "word/document.xml" }
        assertEquals(listOf("Before", "Inside", "After"), fragments.map { it.text.raw })
        assertEquals(3, fragments.map { it.text.source }.distinct().size)
    }
    @Test fun explicitStoryReferenceOrderSurvivesRelationshipReordering() {
        val body = "<w:sectPr><w:headerReference r:id='second'/><w:headerReference r:id='first'/></w:sectPr>"
        val parts = fixture.parts(body)
        parts["word/_rels/document.xml.rels"] = fixture.relationships(fixture.relationship("first", "header1.xml", "header") +
            fixture.relationship("second", "header2.xml", "header")).toByteArray()
        assertEquals(listOf("word/header2.xml", "word/header1.xml"), extract(parts).fragments.take(2).map { it.text.source.part })
    }
    @Test fun nestedSensitivityControlDoesNotEraseItsOrdinaryParent() {
        val body = "<w:sdt><w:sdtContent>${fixture.p("Visible observation")}<w:sdt><w:sdtPr>" +
            "<w:tag w:val='MSIP_Label_Synthetic'/></w:sdtPr><w:sdtContent>${fixture.p("Excluded label")}" +
            "</w:sdtContent></w:sdt></w:sdtContent></w:sdt>"
        val texts = extract(fixture.parts(body)).fragments.map { it.text.raw }
        assertTrue("Visible observation" in texts)
        assertFalse("Excluded label" in texts)
    }
    @Test fun additionalPaginationAndInfoAuthorFieldsExcludeOnlyTheirCache() {
        val body = listOf("SECTIONPAGES", "PAGEREF mark", "INFO AUTHOR").joinToString("") { instruction ->
            "<w:p><w:fldSimple w:instr='$instruction'><w:r><w:t>Excluded cache</w:t></w:r></w:fldSimple>" +
                "<w:r><w:t>Adjacent evidence</w:t></w:r></w:p>"
        }
        val result = extract(fixture.parts(body))
        assertEquals(listOf("Adjacent evidence", "Adjacent evidence", "Adjacent evidence"),
            result.fragments.filter { it.text.source.part == "word/document.xml" }.map { it.text.raw })
    }
    @Test fun tableColumnLabelsArePreservedAsLabelsWithoutInventingAnItem() {
        val result = extract(fixture.parts("<w:tbl>" + fixture.row("Feature", "Status", "Comments") +
            fixture.row("Original item", "Fair", "Original note") + "</w:tbl>"))
        assertEquals(listOf("Original item"), result.items.map { it.name.raw })
        assertEquals(listOf(FragmentRole.LABEL, FragmentRole.LABEL, FragmentRole.LABEL), result.fragments.take(3).map { it.role })
    }
    @Test fun establishedItemContextWinsOverNumericCaptionPattern() {
        val result = extract(fixture.parts(fixture.p("Kitchen") + fixture.p("Gap 100-mm wide")))
        assertEquals(listOf("Gap 100-mm wide"), result.items.map { it.name.raw })
        assertTrue(result.captions.isEmpty())
    }
    @Test fun establishedNarrativeContextWinsOverNumericCaptionPattern() {
        val result = extract(fixture.parts(fixture.p("Comments / Summary") + fixture.p("A 100-mm gap needs review")))
        assertEquals(listOf("A 100-mm gap needs review"), result.summaryCandidates.map { it.raw })
        assertTrue(result.captions.isEmpty())
    }
    @Test fun trackedDeletionCannotBecomeACurrentItem() {
        val error = assertFailsWith<IllegalArgumentException> { extract(fixture.parts(fixture.p("Kitchen") +
            "<w:del><w:p><w:r><w:delText>Deleted observation</w:delText></w:r></w:p></w:del>")) }
        assertEquals("DOCX_TRACKED_CONTENT", error.message)
    }
    @Test fun orphanWordTextCannotDisappearFromASuccessfulManifest() {
        val error = assertFailsWith<IllegalArgumentException> { extract(fixture.parts("<w:r><w:t>Orphan observation</w:t></w:r>")) }
        assertEquals("DOCX_UNSUPPORTED_TEXT", error.message)
    }
    @Test fun unsupportedDrawingTextCannotDisappearFromASuccessfulManifest() {
        val error = assertFailsWith<IllegalArgumentException> { extract(fixture.parts("<a:p><a:r><a:t>Drawing observation</a:t></a:r></a:p>")) }
        assertEquals("DOCX_UNSUPPORTED_TEXT", error.message)
    }
    @Test fun unseparatedAuthorFieldRejectsInsteadOfLeakingItsCache() {
        val body = "<w:p><w:r><w:fldChar w:fldCharType='begin'/><w:instrText>AUTHOR</w:instrText>" +
            "<w:t>Excluded author</w:t><w:fldChar w:fldCharType='end'/></w:r></w:p>"
        assertEquals("DOCX_FIELD_STRUCTURE", assertFailsWith<IllegalArgumentException> { extract(fixture.parts(body)) }.message)
    }
    @Test fun complexFieldPhasesCannotLeakOrReclassifyCachedContent() {
        val separate = "<w:fldChar w:fldCharType='separate'/>"
        for (content in listOf("<w:t>Excluded author</w:t>$separate", "$separate$separate",
                "$separate<w:instrText>QUOTE</w:instrText>", "")) {
            val body = "<w:p><w:r><w:fldChar w:fldCharType='begin'/><w:instrText>AUTHOR</w:instrText>" +
                content + "<w:fldChar w:fldCharType='end'/></w:r></w:p>"
            assertEquals("DOCX_FIELD_STRUCTURE", assertFailsWith<IllegalArgumentException> { extract(fixture.parts(body)) }.message)
        }
    }
    private fun assertImageReview(bytes: ByteArray, format: String, forged: Boolean = false, width: Int? = null): DocxExtractionManifest {
        val attempt = runCatching { DocxReportExtractor().extract(if (forged) fixture.forgedImage(bytes, format)
            else fixture.read(fixture.imageParts(bytes, format))) }
        assertTrue(attempt.isSuccess, "Unqualified image must remain reviewable")
        val result = attempt.getOrThrow()
        assertEquals(1, result.images.size)
        assertEquals(width, result.images.single().width)
        assertEquals(width, result.images.single().height)
        assertTrue(result.warnings.any { it.code == ExtractionWarningCode.IMAGE_REVIEW_REQUIRED })
        assertFalse(result.warnings.any { it.code == ExtractionWarningCode.LAYOUT_IMAGE_EXCLUDED })
        return result
    }
    @Test fun forgedSignaturesAreRejectedByReaderAndRetainedByExtractor() {
        for (format in listOf("png", "jpg")) {
            val valid = fixture.image(1, 1, format)
            for (bytes in listOf(valid.copyOf().apply { this[0] = 0 }, valid.copyOfRange(if (format == "png") 8 else 2, valid.size))) {
                val error = assertFailsWith<DocxPackageException> { fixture.read(fixture.parts().apply { this["word/media/bad.$format"] = bytes }) }
                assertEquals(DocxPackageReason.UNSUPPORTED_CONTENT, error.reason)
                assertImageReview(bytes, format, forged = true)
            }
        }
    }
    @Test fun validCrcCannotMakeInvalidPngHeaderFieldsIntoLayoutShims() {
        for ((offset, value) in listOf(24 to 1, 25 to 1, 26 to 1, 27 to 1, 28 to 2, 16 to 128)) {
            assertImageReview(fixture.repairPngCrc(fixture.image(1, 1).apply { this[offset] = value.toByte() }), "png")
        }
    }
    @Test fun malformedJpegFramesCannotBecomeLayoutShims() {
        val valid = fixture.image(1, 1, "jpg")
        val sof = (0 until valid.size - 1).first { valid[it] == 255.toByte() && valid[it + 1] == 192.toByte() }
        val app = (0 until valid.size - 1).first { valid[it] == 255.toByte() && valid[it + 1] == 224.toByte() }
        for (marker in listOf(0, 2, 220, 240)) assertImageReview(valid.copyOf().apply { this[app + 1] = marker.toByte() }, "jpg")
        for ((offset, value) in listOf(1 to 195, 3 to 8, 4 to 12, 9 to 0, 9 to 2, 11 to 0, 11 to 16, 11 to 81, 12 to 4, 13 to 1)) {
            assertImageReview(valid.copyOf().apply { this[sof + offset] = value.toByte() }, "jpg")
        }
        assertImageReview(valid, "jpg", width = 1)
    }
    @Test fun unqualifiedPayloadsKeepEveryPlacementAndBindTheirBytesInTheDigest() {
        for (format in listOf("png", "jpg")) {
            val digests = fixture.incompleteImages(format).map { bytes ->
                val result = assertImageReview(bytes, format, width = 1)
                val part = "word/media/bad.$format"
                assertEquals(listOf(part, part), result.placements.map { it.imagePart })
                assertEquals(listOf(DrawingKind.INLINE, DrawingKind.ANCHORED), result.placements.map { it.kind })
                assertEquals(2, result.placements.map { it.source }.distinct().size)
                assertEquals(listOf(ExtractionWarning(ExtractionWarningCode.IMAGE_REVIEW_REQUIRED, SourceLocation(part, 0))),
                    result.warnings.filter { it.code == ExtractionWarningCode.IMAGE_REVIEW_REQUIRED })
                assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, result.images.single().sha256)
                assertEquals(result.normalizedDigest, extract(fixture.imageParts(bytes, format)).normalizedDigest)
                result.normalizedDigest
            }
            assertEquals(4, digests.toSet().size)
        }
    }
    @Test fun qualifiedPngExclusionAlsoRemovesItsDrawingPlacements() {
        val result = extract(fixture.imageParts(fixture.image(24, 1), "png"))
        assertTrue(result.images.isEmpty())
        assertTrue(result.placements.isEmpty())
        assertEquals(listOf(ExtractionWarning(ExtractionWarningCode.LAYOUT_IMAGE_EXCLUDED, SourceLocation("word/media/bad.png", 0))),
            result.warnings.filter { it.code in setOf(ExtractionWarningCode.LAYOUT_IMAGE_EXCLUDED, ExtractionWarningCode.IMAGE_REVIEW_REQUIRED) })
    }
}
/* Historical R4: .review/r4-final/summary.json retains 51 faults and their SHA/XML;
 * unchanged visitor segments are audited separately. .review/r4-image/summary.json
 * retains 18 image-header faults, superseded by the qualification predecessor.
 * Current image integration evidence: .review/r4-qualification/summary.json.
 */

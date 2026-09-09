package nz.myinspection.core.report.importing.docx.extract

import kotlin.test.*
import nz.myinspection.core.report.importing.docx.extract.ExtractionWarningCode.*
import java.security.MessageDigest
import nz.myinspection.core.report.importing.docx.image.DocxImagePixelLimitException
import nz.myinspection.core.report.importing.docx.`package`.DocxPackageException
import nz.myinspection.core.report.importing.docx.`package`.DocxPackageReason

import nz.myinspection.core.report.importing.docx.`package`.DocxPackage
import nz.myinspection.core.report.importing.docx.`package`.DocxPart
import nz.myinspection.core.report.importing.docx.`package`.DocxPartKind

class DocxReportExtractorTest {
    private fun extract(parts: Map<String, ByteArray>) = DocxReportExtractor().extract(read(parts))
    private fun extractBody(xml: String) = extract(parts(xml))
    private fun DocxExtractionManifest.bodyFragments() = fragments.filter { it.text.source.part == DOCUMENT_PART }
    private fun DocxExtractionManifest.rawFragments() = fragments.map { it.text.raw }
    private fun DocxExtractionManifest.warnings(code: ExtractionWarningCode) = warnings.filter { it.code == code }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun assertWarning(result: DocxExtractionManifest, code: ExtractionWarningCode) =
        assertTrue(result.warnings.any { it.code == code }, code.name)

    private fun assertRejected(body: String, message: String) =
        assertEquals(message, assertFailsWith<IllegalArgumentException> { extractBody(body) }.message)
    private fun assertUnsupportedText(body: String) = assertRejected(body, "DOCX_UNSUPPORTED_TEXT")

    @Test fun runTokensPreserveHyphensAndExcludeLegacyPages() {
        val result = extractBody("<w:p><w:r><w:t>A</w:t><w:noBreakHyphen/><w:softHyphen/><w:pgNum/><w:t>B</w:t></w:r></w:p>")
        assertEquals("A\u2011\u00adB", result.fragments.first().text.raw)
        assertWarning(result, PAGINATION_EXCLUDED)
        for (child in listOf("<w:r><w:t>X</w:t></w:r>", "<w:drawing/>", "X"))
            assertUnsupportedText("<w:p><w:r><w:pgNum>$child</w:pgNum></w:r></w:p>")
    }
    @Test fun unsupportedRunContentRejectsClosed() {
        for (element in listOf("sym w:font='Wingdings' w:char='F0FC'", "dayShort", "monthLong", "yearLong", "tab xmlns:w='urn:x'",
                "annotationRef", "footnoteRef", "endnoteRef", "separator", "continuationSeparator", "ptab", "ruby", "contentPart", "delInstrText")) {
            assertUnsupportedText("<w:p><w:r><w:$element/></w:r></w:p>")
        }
    }

    @Test fun realTableCellsPreserveNullableStatusAndRawSpelling() {
        val result = extractBody(p("Kitchen") + "<w:tbl>" +
            row("  Window latch  ", "  FaIr  ", "Slight resistance") +
            row("Sink", "", "Water flows") + "</w:tbl>")
        assertEquals(listOf("  Window latch  ", "Sink"), result.items.map { it.name.raw })
        assertEquals("Window latch", result.items[0].name.normalized)
        assertEquals("  FaIr  ", result.items[0].status?.raw)
        assertEquals("Slight resistance", result.items[0].comment?.raw)
        assertNull(result.items[1].status)
        assertEquals("Water flows", result.items[1].comment?.raw)
        assertEquals(listOf("Kitchen", "Kitchen"), result.items.map { it.room })
    }

    @Test fun everyStoryIsVisitedIncludingUnreferencedHeadersAndFooters() {
        val result = extractBody(p("Body observation"))
        val texts = result.rawFragments()
        assertTrue("Body observation" in texts)
        for (i in 1..12) {
            assertTrue("Unique header observation $i" in texts, "header $i lost")
            assertTrue("Unique footer observation $i" in texts, "footer $i lost")
        }
        assertEquals(25, result.fragments.map { it.text.source.part }.distinct().size)
        assertWarning(result, UNRESOLVED_TEXT)
    }

    @Test fun pageAndAuthorFieldsDoNotSwallowAdjacentEvidence() {
        val fields = "<w:p><w:r><w:t>Before </w:t></w:r>" +
            field("PAGE", run("999"), true) +
            field(" NUMPAGES ", "<w:r><w:t>888</w:t><w:noBreakHyphen/><w:softHyphen/></w:r>", false) +
            "<w:r><w:t> after</w:t></w:r></w:p>" +
            field("AUTHOR", p("Excluded private author"), true) +
            listOf("https://", "ftp://", "file://", "mailto:", "tel:", "data:", "urn:", "custom+scheme://", "www.")
                .joinToString("") { p("See ${it}synthetic.invalid for context; Note:water Profile:aluminium Hotel:damaged") } +
            "<w:sdt><w:sdtPr><w:tag w:val='MSIP_Label_Synthetic'/></w:sdtPr><w:sdtContent>" +
            p("Excluded sensitivity value") + "</w:sdtContent></w:sdt>"
        val result = extractBody(fields)
        val text = (result.fragments.map { it.text } + result.items.flatMap { listOfNotNull(it.name, it.status, it.comment) } +
            result.identity.map { it.text } + result.summaryCandidates + result.captions.map { it.text }).joinToString("|") { it.raw }
        assertTrue("Before  after" in text)
        for (secret in listOf("999", "888", "Excluded private author", "synthetic.invalid", "Excluded sensitivity value")) {
            assertFalse(secret in text, "excluded source field survived")
        }
        assertTrue("See " in text && " for context; Note:water Profile:aluminium Hotel:damaged" in text)
        assertWarning(result, PAGINATION_EXCLUDED)
        assertWarning(result, METADATA_EXCLUDED)
        assertWarning(result, URL_EXCLUDED)
    }

    @Test fun nestedTextboxesAndUnknownTableCellsRemainReviewable() {
        val nested = "<w:p><w:r><w:t>Outer observation</w:t><w:drawing><wp:anchor><w:txbxContent>" +
            p("Nested observation") + "</w:txbxContent></wp:anchor></w:drawing></w:r></w:p>" +
            "<w:tbl><w:tr><w:tc>${p("One-cell note")}</w:tc></w:tr></w:tbl>"
        val result = extractBody(nested)
        assertEquals(listOf("Outer observation", "Nested observation", "One-cell note"),
            result.bodyFragments().map { it.text.raw })
        val note = result.fragments.single { it.text.raw == "One-cell note" }.text
        assertTrue(result.warnings.any { it.code == UNRESOLVED_TEXT && it.source == note.source })
    }

    @Test fun multipleNamesInOneTableCellRemainFragmentsWithTheirOwnBlocker() {
        val row = "<w:tr><w:tc>${p("First candidate")}${p("Second candidate")}</w:tc>" +
            "<w:tc>${p("Fair")}</w:tc><w:tc>${p("Unassigned note")}</w:tc></w:tr>"
        val result = extractBody("<w:tbl>$row</w:tbl>")
        assertTrue(result.items.isEmpty())
        assertEquals(listOf("First candidate", "Second candidate", "Fair", "Unassigned note"),
            result.bodyFragments().map { it.text.raw })
        val first = result.fragments.first().text.source
        assertTrue(result.warnings.any { it.code == UNRESOLVED_TEXT && it.source == first })
    }

    @Test fun trailingRoomHeadingAffectsFollowingItemOnly() {
        val rows = "<w:tr><w:tc>${p("Old room item")}${p("Kitchen")}</w:tc>" +
            "<w:tc>${p("Good")}</w:tc><w:tc>${p("Old observation")}</w:tc></w:tr>"
        val result = extractBody(p("Lounge") + "<w:tbl>" + rows +
            row("New room item", "Good", "New observation") + "</w:tbl>")
        assertEquals(listOf("Lounge", "Kitchen"), result.items.map { it.room })
    }

    @Test fun unknownFieldResultsRemainUnresolved() {
        for (instruction in listOf("QUOTE", "HYPERLINK ../source", "QUOTE mailto:synthetic.invalid")) for (simple in listOf(true, false)) {
            val result = extractBody(p("Kitchen") + "<w:p>" +
                field(instruction, run("Cached observation"), simple) + "</w:p>")
            assertTrue(result.fragments.any { it.text.raw == "Cached observation" })
            val cached = result.items.single().name.source
            assertTrue(result.warnings.any { it.code == UNRESOLVED_TEXT && it.source == cached })
            assertEquals(instruction != "QUOTE", result.warnings.any { it.code == URL_EXCLUDED })
        }
    }
    @Test fun openFieldsRejectSafelyInsteadOfSwallowingLaterObservations() {
        val broken = "<w:p><w:r><w:fldChar w:fldCharType='begin'/><w:instrText>PAGE</w:instrText>" +
            "<w:fldChar w:fldCharType='separate'/><w:t>7</w:t></w:r></w:p>" + p("Real observation after broken field")
        val error = assertFailsWith<IllegalArgumentException> { extractBody(broken) }
        assertEquals("DOCX_FIELD_STRUCTURE", error.message)
        assertNull(error.cause)
    }

    @Test fun fragmentedSampleKeeps64ItemNamesAndOnly24CellBasedRows() {
        val result = extract(sample())
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
        assertWarning(result, AMBIGUOUS_COLUMNS)
        assertEquals(listOf("42 Synthetic Lane", "3 September 2026", "Inspection (03/09/2026)"), result.identity.map { it.text.raw })
        assertEquals("Original synthetic summary.", result.summaryCandidates.single().raw)
        assertWarning(result, UNRESOLVED_NARRATIVE)
        assertEquals(89, result.captions.size)
        assertEquals((1..89).map { it.toString().padStart(3, '0') }, result.captions.map { it.number })
        assertEquals(82, result.images.size)
        assertEquals(83, result.placements.size)
        assertEquals((1..67).map { "word/media/photo$it.png" }.toSet() +
            (1..15).map { "word/media/small$it.png" }.toSet(), result.images.map { it.part }.toSet())
        assertEquals(2, result.placements.count { it.imagePart == "word/media/photo67.png" })
        assertTrue(result.placements.any { it.kind == DrawingKind.INLINE })
        assertTrue(result.placements.any { it.kind == DrawingKind.ANCHORED })
        assertWarning(result, AMBIGUOUS_CAPTIONS)
        val imageWarnings = result.warnings(IMAGE_REVIEW_REQUIRED)
        assertEquals(82, imageWarnings.size)
        assertEquals(result.images.map { it.part }.toSet(), imageWarnings.mapNotNull { it.source?.part }.toSet())
        assertFalse(result.warnings.any { it.code == LAYOUT_IMAGE_EXCLUDED })
    }

    @Test fun damagedAndRepeatedCaptionTextIsNeverCorrectedOrPaired() {
        val raw = "047-Room Gamma 2 2048-Room Gamma 2 3"
        val parts = parts(p("Images") + p(raw) + p("ABC-Area Alpha 11") + p("4 5"))
        parts["word/footer1.xml"] = story("ftr", p(raw)).toByteArray()
        val result = extract(parts)
        assertEquals(listOf("047", "2048", "047", "2048"), result.captions.map { it.number })
        assertEquals(4, result.captions.map { it.text.source }.distinct().size)
        assertEquals(2, result.fragments.count { it.text.raw == raw })
        assertTrue(result.fragments.any { it.text.raw == "ABC-Area Alpha 11" })
        assertTrue(result.fragments.any { it.text.raw == "4 5" })
        assertWarning(result, AMBIGUOUS_CAPTIONS)
    }

    @Test fun unmarkedSignoffStaysInsideBlockedNarrativeCandidates() {
        val result = extractBody(p("Comments X Summary") + p("Synthetic report narrative") +
            p("Anonymous signoff candidate") + p("Images"))
        assertEquals(listOf("Synthetic report narrative", "Anonymous signoff candidate"), result.summaryCandidates.map { it.raw })
        assertWarning(result, UNRESOLVED_NARRATIVE)
    }

    @Test fun missingDrawingTargetsAndMalformedImageHeadersRemainBlockers() {
        val parts = parts(drawing("absent") + drawing("bad", "anchor"))
        parts["word/media/bad.png"] = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        parts["word/_rels/document.xml.rels"] = relationships(relationship("bad", "media/bad.png", "image")).toByteArray()
        val result = extract(parts)
        assertEquals(2, result.placements.size)
        assertNull(result.placements[0].imagePart)
        assertEquals(1, result.images.size)
        assertNull(result.images.single().width)
        assertWarning(result, MISSING_IMAGE)
        assertWarning(result, IMAGE_REVIEW_REQUIRED)
    }

    @Test fun retainedImageDimensionsPreserveClosedPixelBounds() {
        val parts = parts(drawing("photo"))
        parts["word/media/photo.jpg"] = image(32, 4, "jpg")
        parts["word/_rels/document.xml.rels"] = relationships(relationship("photo", "MEDIA/Photo.JPG", "image")).toByteArray()
        val result = extract(parts)
        assertEquals(32, result.images.single().width)
        assertEquals(32, result.images.single().height)
        val huge = image(32, 1)
        huge[16] = 127
        parts["word/media/huge.png"] = repairPngCrc(huge)
        val error = assertFailsWith<DocxImagePixelLimitException> { extract(parts) }
        assertEquals("DOCX_IMAGE_PIXELS", error.message)
        assertNull(error.cause)
    }

    @Test fun digestIgnoresZipAndRelationshipDeclarationOrderButBindsRawEvidence() {
        val parts = sample()
        val first = extract(parts)
        val reordered = parts.entries.reversed().associate { it.toPair() }
        assertEquals(first.normalizedDigest, DocxReportExtractor().extract(read(reordered, false)).normalizedDigest)
        val relName = "word/_rels/document.xml.rels"
        val reversedRelations = parts.toMutableMap().apply {
            this[relName] = relationships(Regex("<Relationship\\s[^>]+/>").findAll(getValue(relName).toString(Charsets.UTF_8))
                .map { it.value }.toList().reversed().joinToString("")).toByteArray()
        }
        assertEquals(first.normalizedDigest, extract(reversedRelations).normalizedDigest)
        assertTrue(first.normalizedDigest.matches(Regex("[a-f0-9]{64}")))
        parts[DOCUMENT_PART] = parts.getValue(DOCUMENT_PART).toString(Charsets.UTF_8)
            .replace("  FaIr  ", " FaIr  ").toByteArray()
        assertNotEquals(first.normalizedDigest, extract(parts).normalizedDigest)
        assertNotEquals(first.normalizedDigest, extract(sample().apply { this["word/media/photo1.png"] = image(32, 321) }).normalizedDigest)
        val changedOrder = sample().apply {
            this[DOCUMENT_PART] = getValue(DOCUMENT_PART).toString(Charsets.UTF_8).replace("Outer feature 1<", "swap<")
                .replace("Outer feature 2<", "Outer feature 1<").replace("swap<", "Outer feature 2<").toByteArray()
        }
        assertNotEquals(first.normalizedDigest, extract(changedOrder).normalizedDigest)
        assertFailsWith<UnsupportedOperationException> { (first.items as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (first.placements as MutableList).clear() }
    }

    @Test fun emptyManifestMatchesIndependentlySerializedDigest() {
        val parts = parts("")
        for (i in 1..12) for ((kind, root) in listOf("header" to "hdr", "footer" to "ftr"))
            parts["word/$kind$i.xml"] = story(root, "").toByteArray()
        // Independent .NET BE32/UTF8 serialization: 17 fields, 151 bytes.
        assertEquals("dade39f717f3be4181d418a24a67814806e63b7f73f37dc559aa06aaa886993a", extract(parts).normalizedDigest)
    }
    @Test fun labelledIsoDateCannotBeStolenByCaptionHeuristics() {
        val result = extractBody(p("INSPECTION DATE") + p("2026-09-06") + p("Ordinary observation"))
        assertEquals(listOf("2026-09-06"), result.identity.map { it.text.raw })
        assertTrue(result.captions.isEmpty())
    }
    @Test fun truncatedOrCorruptTinyPngHeadersAreNotDiscarded() {
        val valid = image(1, 1)
        for (bytes in listOf(valid.copyOf(24), valid.copyOf().apply { this[32] = (this[32].toInt() xor 1).toByte() })) {
            val result = extract(parts().apply { this["word/media/tiny.png"] = bytes })
            assertEquals(1, result.images.size)
            assertNull(result.images.single().width)
        }
    }
    @Test fun nestedParagraphSegmentsKeepTheirActualEncounterOrder() {
        val body = "<w:p><w:r><w:t>Before</w:t><w:drawing><wp:anchor><w:txbxContent>${p("Inside")}" +
            "</w:txbxContent></wp:anchor></w:drawing><w:t>After</w:t></w:r></w:p>"
        val fragments = extractBody(body).bodyFragments()
        assertEquals(listOf("Before", "Inside", "After"), fragments.map { it.text.raw })
        assertEquals(3, fragments.map { it.text.source }.distinct().size)
    }
    @Test fun explicitStoryReferenceOrderSurvivesRelationshipReordering() {
        val body = "<w:sectPr><w:headerReference r:id='second'/><w:headerReference r:id='first'/></w:sectPr>"
        val parts = parts(body)
        parts["word/_rels/document.xml.rels"] = relationships(relationship("first", "Header1.XML", "header") +
            relationship("second", "HEADER2.xml", "header")).toByteArray()
        assertEquals(listOf("word/header2.xml", "word/header1.xml"), extract(parts).fragments.take(2).map { it.text.source.part })
    }
    @Test fun nestedSensitivityControlDoesNotEraseItsOrdinaryParent() {
        val body = "<w:sdt><w:sdtContent>${p("Visible observation")}<w:sdt><w:sdtPr>" +
            "<w:tag w:val='MSIP_Label_Synthetic'/></w:sdtPr><w:sdtContent>${p("Excluded label")}" +
            "</w:sdtContent></w:sdt></w:sdtContent></w:sdt>"
        val texts = extractBody(body).rawFragments()
        assertTrue("Visible observation" in texts)
        assertFalse("Excluded label" in texts)
    }
    @Test fun additionalPaginationAndInfoAuthorFieldsExcludeOnlyTheirCache() {
        val body = listOf("SECTIONPAGES", "PAGEREF mark", "INFO AUTHOR").joinToString("") { instruction ->
            "<w:p>" + field(instruction, run("Excluded cache"), true) +
                "<w:r><w:t>Adjacent evidence</w:t></w:r></w:p>"
        }
        val result = extractBody(body)
        assertEquals(listOf("Adjacent evidence", "Adjacent evidence", "Adjacent evidence"),
            result.bodyFragments().map { it.text.raw })
    }
    @Test fun tableColumnLabelsArePreservedAsLabelsWithoutInventingAnItem() {
        val result = extractBody("<w:tbl>" + row("Feature", "Status", "Comments") +
            row("Original item", "Fair", "Original note") + "</w:tbl>")
        assertEquals(listOf("Original item"), result.items.map { it.name.raw })
        assertEquals(listOf(FragmentRole.LABEL, FragmentRole.LABEL, FragmentRole.LABEL), result.fragments.take(3).map { it.role })
    }
    @Test fun establishedItemContextWinsOverNumericCaptionPattern() {
        val result = extractBody(p("Kitchen") + p("Gap 100-mm wide"))
        assertEquals(listOf("Gap 100-mm wide"), result.items.map { it.name.raw })
        assertTrue(result.captions.isEmpty())
    }
    @Test fun establishedNarrativeContextWinsOverNumericCaptionPattern() {
        val result = extractBody(p("Comments / Summary") + p("A 100-mm gap needs review"))
        assertEquals(listOf("A 100-mm gap needs review"), result.summaryCandidates.map { it.raw })
        assertTrue(result.captions.isEmpty())
    }
    @Test fun trackedDeletionCannotBecomeACurrentItem() {
        val error = assertFailsWith<IllegalArgumentException> { extractBody(p("Kitchen") +
            "<w:del><w:p><w:r><w:delText>Deleted observation</w:delText></w:r></w:p></w:del>") }
        assertEquals("DOCX_TRACKED_CONTENT", error.message)
    }
    @Test fun orphanWordTextCannotDisappearFromASuccessfulManifest() {
        assertUnsupportedText(run("Orphan observation"))
        for (node in listOf("w:p", "w:tc", "w:r", "w:body", "w:pPr", "a:ext", "a:instrText")) {
            assertUnsupportedText("<w:p><$node>Hidden observation</$node></w:p>")
        }
    }
    @Test fun unsupportedDrawingTextCannotDisappearFromASuccessfulManifest() {
        assertUnsupportedText("<a:p><a:r><a:t>Drawing observation</a:t></a:r></a:p>")
    }
    @Test fun unseparatedAuthorFieldRejectsInsteadOfLeakingItsCache() {
        val body = "<w:p><w:r><w:fldChar w:fldCharType='begin'/><w:instrText>AUTHOR</w:instrText>" +
            "<w:t>Excluded author</w:t><w:fldChar w:fldCharType='end'/></w:r></w:p>"
        assertRejected(body, "DOCX_FIELD_STRUCTURE")
    }
    @Test fun complexFieldPhasesCannotLeakOrReclassifyCachedContent() {
        val separate = "<w:fldChar w:fldCharType='separate'/>"
        for (content in listOf("<w:t>Excluded author</w:t>$separate", "<w:noBreakHyphen/>$separate", "<w:softHyphen/>$separate", "$separate$separate",
                "$separate<w:instrText>QUOTE</w:instrText>", "$separate<w:fldChar/>", "$separate<w:fldChar w:fldCharType='unknown'/>", "")) {
            val body = "<w:p><w:r><w:fldChar w:fldCharType='begin'/><w:instrText>AUTHOR</w:instrText>" +
                content + "<w:fldChar w:fldCharType='end'/></w:r></w:p>"
            assertRejected(body, "DOCX_FIELD_STRUCTURE")
        }
    }
    private fun assertRetainedPlacements(result: DocxExtractionManifest, part: String, bytes: ByteArray) {
        assertEquals(listOf(part, part), result.placements.map { it.imagePart })
        assertEquals(listOf(DrawingKind.INLINE, DrawingKind.ANCHORED), result.placements.map { it.kind })
        assertEquals(2, result.placements.map { it.source }.distinct().size)
        assertEquals(listOf(ExtractionWarning(IMAGE_REVIEW_REQUIRED, SourceLocation(part, 0))),
            result.warnings(IMAGE_REVIEW_REQUIRED))
        assertEquals(sha256(bytes), result.images.single().sha256)
    }
    private fun assertImageReview(bytes: ByteArray, format: String, forged: Boolean = false, width: Int? = null): DocxExtractionManifest {
        val attempt = runCatching { DocxReportExtractor().extract(if (forged) forgedImage(bytes, format)
            else read(imageParts(bytes, format))) }
        assertTrue(attempt.isSuccess, "Unqualified image must remain reviewable")
        val result = attempt.getOrThrow()
        assertEquals(1, result.images.size)
        assertEquals(width, result.images.single().width)
        assertEquals(width, result.images.single().height)
        assertWarning(result, IMAGE_REVIEW_REQUIRED)
        assertFalse(result.warnings.any { it.code == LAYOUT_IMAGE_EXCLUDED })
        return result
    }
    @Test fun forgedSignaturesAreRejectedByReaderAndRetainedByExtractor() {
        for (format in listOf("png", "jpg")) {
            val valid = image(1, 1, format)
            for (bytes in listOf(valid.copyOf().apply { this[0] = 0 }, valid.copyOfRange(if (format == "png") 8 else 2, valid.size))) {
                val error = assertFailsWith<DocxPackageException> { read(parts().apply { this["word/media/bad.$format"] = bytes }) }
                assertEquals(DocxPackageReason.UNSUPPORTED_CONTENT, error.reason)
                assertImageReview(bytes, format, forged = true)
            }
        }
    }
    @Test fun validCrcCannotMakeInvalidPngHeaderFieldsIntoLayoutShims() {
        for ((offset, value) in listOf(24 to 1, 25 to 1, 26 to 1, 27 to 1, 28 to 2, 16 to 128)) {
            assertImageReview(repairPngCrc(image(1, 1).apply { this[offset] = value.toByte() }), "png")
        }
    }
    @Test fun malformedJpegFramesCannotBecomeLayoutShims() {
        val valid = image(1, 1, "jpg")
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
            val digests = incompleteImages(format).map { bytes ->
                val result = assertImageReview(bytes, format, width = 1)
                val part = "word/media/bad.$format"
                assertRetainedPlacements(result, part, bytes)
                assertEquals(result.normalizedDigest, extract(imageParts(bytes, format)).normalizedDigest)
                result.normalizedDigest
            }
            assertEquals(4, digests.toSet().size)
        }
    }
    @Test fun validSmallSubstantivePngRetainsImageAndInlineAnchorPlacements() {
        val imagePart = "word/media/small-substantive.png"
        val bytes = image(24, 1)
        val parts = parts(drawing("small-substantive") + drawing("small-substantive", "anchor")).apply {
            this[imagePart] = bytes
            this["word/_rels/document.xml.rels"] = relationships(
                relationship("small-substantive", "media/small-substantive.png", "image")
            ).toByteArray()
        }
        val result = extract(parts)
        assertWarning(result, AMBIGUOUS_CAPTIONS)
        assertEquals(1, result.images.size)
        assertEquals(imagePart, result.images.single().part)
        assertEquals(24, result.images.single().width)
        assertEquals(24, result.images.single().height)
        assertRetainedPlacements(result, imagePart, bytes)
        assertFalse(result.warnings.any { it.code == LAYOUT_IMAGE_EXCLUDED })
    }
    @Test fun unsupportedDrawingsRejectInsteadOfDisappearing() {
        val extra = "<a:graphic><a:graphicData><a:blip r:embed='absent'/></a:graphicData></a:graphic>"
        for (content in listOf("", "<a:graphic><a:graphicData/></a:graphic>") +
                listOf("inline", "anchor").flatMap { listOf("<wp:$it/>$extra", "$extra<wp:$it/>") }) {
            assertEquals("DOCX_DRAWING_STRUCTURE", assertFailsWith<IllegalArgumentException> {
                extractBody("<w:p><w:r><w:drawing>$content</w:drawing></w:r></w:p>")
            }.message)
        }
    }
    @Test fun emptyDrawingFramesRemainUnresolvedPlacements() {
        for (frames in listOf("inline", "anchor", "inline,inline", "anchor,anchor", "inline,anchor", "anchor,inline")) {
            val kinds = frames.split(',')
            val body = kinds.joinToString("") { "<wp:$it/>" }
            val result = extractBody("<w:p><w:r><w:drawing>$body</w:drawing></w:r></w:p>")
            assertEquals(kinds.mapIndexed { i, kind -> DrawingPlacement(SourceLocation(DOCUMENT_PART, 0, i),
                if (kind == "inline") DrawingKind.INLINE else DrawingKind.ANCHORED, null) }, result.placements)
            assertEquals(result.placements.map { it.source }, result.warnings(MISSING_IMAGE).map { it.source })
        }
    }
    private fun unresolvedIdentity(result: DocxExtractionManifest) {
        assertTrue(result.warnings.any { it.code == UNRESOLVED_TEXT && it.source == SourceLocation(DOCUMENT_PART, 0) })
    }
    @Test fun identityCannotCrossStructuralOrExcludedValueBoundaries() {
        for (between in listOf("<w:tbl/>", "<w:tbl>${row("Latch", "fair", "Note")}</w:tbl>",
                "<w:p/>", p("https://synthetic.invalid/value"), p("Feature"), p("Kitchen"),
                field("AUTHOR", p("Excluded author"), true))) {
            val result = extractBody(p("PROPERTY ADDRESS") + between + p("Unrelated observation"))
            assertTrue(result.identity.isEmpty())
            assertTrue(result.fragments.any { it.text.raw == "Unrelated observation" })
            unresolvedIdentity(result)
        }
        val result = extractBody(p("PROPERTY ADDRESS") + "<w:sdt><w:sdtContent>" +
            p("Unrelated observation") + "</w:sdtContent></w:sdt>")
        assertTrue(result.identity.isEmpty())
        assertTrue(result.fragments.any { it.text.raw == "Unrelated observation" })
        unresolvedIdentity(result)
    }
    @Test fun repeatedIdentityLabelsExpireBeforeAnAdjacentValue() {
        for ((field, value) in listOf("PROPERTY ADDRESS" to "42 Synthetic Lane", "INSPECTION DATE" to "2026-09-06")) {
            val result = extractBody(p("PROPERTY ADDRESS") + p(field) + p(value))
            assertEquals(listOf(field to value), result.identity.map { it.field to it.text.raw })
            unresolvedIdentity(result)
        }
    }
    @Test fun identityLabelAtStoryEndRemainsExplicitlyUnresolved() {
        val result = extractBody(p("PROPERTY ADDRESS"))
        assertTrue(result.identity.isEmpty())
        unresolvedIdentity(result)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    @Test fun hostileXmlIsRejectedBeforeExternalAccess() {
        val target = java.io.File("docx-entity-probe.txt").absoluteFile
        val targets = listOf(target.toURI().toASCIIString(), "http://xml-probe.invalid/entity")
        val body = story("document", "<w:body>${p("Synthetic XML control")}</w:body>")
        fun extractXml(xml: String) = DocxReportExtractor().extract(DocxPackage(listOf(
            DocxPart(DOCUMENT_PART, DocxPartKind.DOCUMENT, xml.toByteArray()))))
        val hostile = listOf("<!DOCTYPE w:document>" + body,
            "<!DOCTYPE w:document [<!ENTITY a 'xxxx'><!ENTITY b '&a;&a;&a;&a;'>]>" +
                body.replace("Synthetic XML control", "&b;")) + targets.flatMap { systemId -> listOf(
            "<!DOCTYPE w:document [<!ENTITY external SYSTEM '$systemId'>]>" +
                body.replace("Synthetic XML control", "&external;"),
            "<!DOCTYPE w:document [<!ENTITY % external SYSTEM '$systemId'>%external;]>" + body,
            "<!DOCTYPE w:document SYSTEM '$systemId'>" + body)
        }
        val previous = System.getSecurityManager()
        var forbiddenCalls = 0
        val guard = object : SecurityManager() {
            override fun checkPermission(permission: java.security.Permission?) = Unit
            private fun reject(): Nothing { forbiddenCalls++; throw SecurityException("Forbidden XML I/O") }
            override fun checkRead(file: String?) {
                if (file != null && java.io.File(file).absoluteFile == target) reject()
            }
            override fun checkConnect(host: String?, port: Int) = reject()
        }
        try {
            System.setSecurityManager(guard)
            assertFailsWith<SecurityException> { target.inputStream().close() }
            assertFailsWith<SecurityException> { java.net.URL(targets.last()).openStream().close() }
            assertTrue(forbiddenCalls >= 2)
            forbiddenCalls = 0
            assertEquals(listOf("Synthetic XML control"), extractXml(body).rawFragments())
            for (xml in hostile) {
                assertEquals("DOCX_XML", assertFailsWith<IllegalArgumentException> { extractXml(xml) }.message)
                assertEquals(0, forbiddenCalls, "XML must reject before external access")
            }
        } finally { System.setSecurityManager(previous) }
    }
}

/* R4 at c05f2579: 90 kills; control42. Full core969/4 old skips:
 * identity-parent test/guard removed, unique test restored.
 * XML/hashes: .review/extractor-r4/, .review/extractor-full-core-pruning/.
 */

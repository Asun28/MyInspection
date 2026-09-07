package nz.myinspection.core.report.importing.docx.`package`

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

class DocxPackageReaderTest {
    private val word = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private val rel = "http://schemas.openxmlformats.org/package/2006/relationships"
    private val office = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/"
    private fun story(text: String = "ordinary report") = "<w:document xmlns:w=\"$word\"><w:body><w:p><w:r><w:t>$text</w:t></w:r></w:p></w:body></w:document>"
    private fun relationships(target: String = "word/document.xml", type: String = "officeDocument", mode: String = "") =
        "<Relationships xmlns=\"$rel\"><Relationship Id=\"rId1\" Type=\"$office$type\" Target=\"$target\" $mode/></Relationships>"
    private fun parts(document: String = story()): LinkedHashMap<String, ByteArray> = linkedMapOf(
        "[Content_Types].xml" to ("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"png\" ContentType=\"image/png\"/>" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            "<Override PartName=\"/word/header1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml\"/>" +
            "<Override PartName=\"/word/footer1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml\"/></Types>").toByteArray(),
        "_rels/.rels" to relationships().toByteArray(),
        "word/document.xml" to document.toByteArray(),
        "word/header1.xml" to "<w:hdr xmlns:w=\"$word\"><w:p/></w:hdr>".toByteArray(),
        "word/footer1.xml" to "<w:ftr xmlns:w=\"$word\"><w:p/></w:ftr>".toByteArray(),
        "word/_rels/document.xml.rels" to relationships("media/image1.png", "image").toByteArray(),
        "word/media/image1.png" to byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10, 1, 2, 3),
    )
    private fun zip(parts: Map<String, ByteArray> = parts(), stored: Boolean = true): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out ->
            parts.forEach { (name, data) ->
                val entry = ZipEntry(name)
                if (stored) {
                    entry.method = ZipEntry.STORED
                    entry.size = data.size.toLong()
                    entry.crc = CRC32().apply { update(data) }.value
                }
                out.putNextEntry(entry)
                out.write(data)
                out.closeEntry()
            }
        }
        return bytes.toByteArray()
    }
    private fun reject(code: DocxPackageReason, bytes: ByteArray, limits: DocxPackageLimits = DocxPackageLimits()): DocxPackageException {
        val error = assertFailsWith<DocxPackageException> { DocxPackageReader(limits).read(ByteArrayInputStream(bytes)) }
        assertEquals(code, error.reason)
        assertTrue(error.entriesSeen >= 0)
        assertTrue(error.expandedBytes >= 0)
        assertNull(error.cause)
        assertEquals("${error.reason}: entries=${error.entriesSeen}, bytes=${error.expandedBytes}", error.message)
        return error
    }

    @Test fun validStoredAndDeflatedPackagesHaveStableBoundedOutput() {
        val source = parts()
        for (stored in listOf(true, false)) {
            val result = DocxPackageReader().read(ByteArrayInputStream(zip(source.entries.reversed().associate { it.toPair() }, stored)))
            assertEquals(listOf("_rels/.rels", "word/_rels/document.xml.rels", "word/document.xml", "word/footer1.xml", "word/header1.xml", "word/media/image1.png"), result.parts.map { it.name })
            assertEquals(listOf(DocxPartKind.RELATIONSHIPS, DocxPartKind.RELATIONSHIPS, DocxPartKind.DOCUMENT, DocxPartKind.FOOTER, DocxPartKind.HEADER, DocxPartKind.IMAGE), result.parts.map { it.kind })
            result.parts.forEach { part -> assertContentEquals(source.getValue(part.name), part.copyBytes()) }
            result.parts.last().copyBytes().fill(0)
            assertContentEquals(source.getValue("word/media/image1.png"), result.parts.last().copyBytes())
            assertFailsWith<UnsupportedOperationException> { (result.parts as MutableList).clear() }
        }
    }

    @Test fun traversalAndAmbiguousPathsAreRejected() {
        for (name in listOf("../private.png", "/private.png", "word/../private.png", "word\\private.png", "word/%2e%2e/private.png", "C:/private.png", "word//private.png", "word/./private.png")) {
            reject(DocxPackageReason.UNSAFE_PATH, zip(parts().apply { put(name, byteArrayOf(1)) }))
        }
    }
    @Test fun duplicateNormalizedNamesAreRejected() {
        reject(DocxPackageReason.DUPLICATE_PART, zip(parts().apply { put("WORD/DOCUMENT.XML", story().toByteArray()) }))
    }
    @Test fun encryptedZipFlagsAreRejected() {
        val bytes = zip()
        patch16(bytes, 6, 1)
        patch16(bytes, central(bytes) + 8, 1)
        reject(DocxPackageReason.ENCRYPTED, bytes)
    }
    @Test fun activePartsCannotMasqueradeAsImagesOrTypes() {
        reject(DocxPackageReason.UNSUPPORTED_PART, zip(parts().apply { put("word/unknown.xml", "<payload/>".toByteArray()) }))
        for (name in listOf("word/vbaProject.bin", "word/embeddings/oleObject1.bin", "word/activeX/activeX1.xml", "word/media/evil.svg")) {
            reject(DocxPackageReason.UNSUPPORTED_PART, zip(parts().apply { put(name, "private active content".toByteArray()) }))
        }
        reject(DocxPackageReason.UNSUPPORTED_CONTENT, zip(parts().apply {
            this["[Content_Types].xml"] = getValue("[Content_Types].xml").toString(Charsets.UTF_8)
                .replace("application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml", "application/vnd.ms-word.document.macroEnabled.main+xml").toByteArray()
        }))
        reject(DocxPackageReason.UNSUPPORTED_CONTENT, zip(parts().apply {
            this["[Content_Types].xml"] = getValue("[Content_Types].xml").toString(Charsets.UTF_8)
                .replace("</Types>", "<Default Extension='bin' ContentType='application/vnd.ms-office.vbaProject'/></Types>").toByteArray()
        }))
        reject(DocxPackageReason.UNSUPPORTED_CONTENT, zip(parts().apply { this["word/media/image1.png"] = "<script>private</script>".toByteArray() }))
    }
    @Test fun activeXmlElementsAndProcessingInstructionsAreRejected() {
        for (element in listOf("object", "control", "altChunk", "OLEObject", "script")) {
            reject(DocxPackageReason.UNSUPPORTED_CONTENT, zip(parts(story().replace("<w:p>", "<w:$element/><w:p>"))))
        }
        reject(DocxPackageReason.UNSUPPORTED_CONTENT, zip(parts("<?xml-stylesheet href='private-url'?>" + story())))
    }
    @Test fun xIncludeElementsAreRejectedByNamespace() {
        for (element in listOf("include", "fallback")) {
            val document = story().replace("<w:p>",
                "<foreign:$element xmlns:foreign='http://www.w3.org/2001/XInclude' href='https://private.invalid/source'/><w:p>")
            reject(DocxPackageReason.UNSUPPORTED_CONTENT, zip(parts(document)))
        }
    }
    @Test fun externalRelationshipsAreRejectedRegardlessOfMode() {
        for ((target, mode) in listOf("word/document.xml" to "TargetMode='External'", "https://private.example/doc" to "TargetMode='External'", "file:///private" to "", "//private.example/a" to "", "../private.xml" to "")) {
            reject(DocxPackageReason.UNSAFE_RELATIONSHIP, zip(parts().apply { this["_rels/.rels"] = relationships(target, mode = mode).toByteArray() }))
        }
    }
    @Test fun unsupportedAndDanglingRelationshipsAreRejected() {
        reject(DocxPackageReason.UNSAFE_RELATIONSHIP, zip(parts().apply { this["_rels/.rels"] = relationships(type = "oleObject").toByteArray() }))
        reject(DocxPackageReason.UNSAFE_RELATIONSHIP, zip(parts().apply { this["word/_rels/document.xml.rels"] = relationships("media/missing.png", "image").toByteArray() }))
        val duplicate = relationships().replace("</Relationships>",
            "<Relationship Id='rId1' Type='${office}officeDocument' Target='word/document.xml'/></Relationships>")
        reject(DocxPackageReason.UNSAFE_RELATIONSHIP, zip(parts().apply { this["_rels/.rels"] = duplicate.toByteArray() }))
    }
    @Test fun dtdAndEntitiesAreRejectedBeforeExpansionInBothEncodings() {
        for (doctype in listOf("<!DOCTYPE w:document>", "<!DOCTYPE w:document [<!ENTITY secret SYSTEM 'file:///private-secret'>]>", "<!DOCTYPE w:document [<!ENTITY secret 'private-document-text'>]>")) {
            val xml = doctype + story(if (doctype.contains("<!ENTITY")) "&secret;" else "ordinary text")
            for (encoding in listOf(Charsets.UTF_8, Charsets.UTF_16)) {
                reject(DocxPackageReason.DTD_OR_ENTITY, zip(parts().apply { this["word/document.xml"] = xml.toByteArray(encoding) }))
            }
        }
    }
    @Test fun malformedXmlIsRejectedWithoutParserDiagnostics() {
        val previous = System.err
        val diagnostics = ByteArrayOutputStream()
        try {
            System.setErr(java.io.PrintStream(diagnostics, true, "UTF-8"))
            reject(DocxPackageReason.MALFORMED_XML, zip(parts(story("private &private_entity; text"))))
            reject(DocxPackageReason.MALFORMED_XML, zip(parts(story().replace("</w:body>", ""))))
            reject(DocxPackageReason.MALFORMED_XML, zip(parts().apply { this["word/header1.xml"] = "<private-raw-path".toByteArray() }))
            assertEquals("", diagnostics.toString("UTF-8"), "parser diagnostics must stay private")
        } finally { System.setErr(previous) }
    }
    @Test fun entryCountBoundUsesActualDirectoryEntries() {
        reject(DocxPackageReason.ENTRY_COUNT, zip(), DocxPackageLimits(maxEntries = 6))
    }
    @Test fun entryByteBoundUsesExpandedBytesDespiteForgedMetadata() {
        reject(DocxPackageReason.ENTRY_BYTES, zip(parts(story("x".repeat(4000)))), DocxPackageLimits(maxEntryBytes = 1500))
        val input = zip(parts(story("x".repeat(4000))), stored = false)
        // The document's central uncompressed-size claim is smaller than its actual DEFLATE output.
        claimDocumentSize(input, 1)
        reject(DocxPackageReason.ENTRY_BYTES, input, DocxPackageLimits(maxEntryBytes = 1500))
    }
    @Test fun totalByteBoundIsIndependentOfEntryBound() {
        reject(DocxPackageReason.TOTAL_BYTES, zip(), DocxPackageLimits(maxTotalBytes = 1200))
    }
    @Test fun compressionRatioBoundUsesRealInflation() {
        reject(DocxPackageReason.COMPRESSION_RATIO, zip(parts(story("x".repeat(10000))), stored = false), DocxPackageLimits(maxCompressionRatio = 10))
    }
    @Test fun xmlDepthBoundUsesNestedElements() {
        reject(DocxPackageReason.XML_DEPTH, zip(parts(story())), DocxPackageLimits(maxXmlDepth = 4))
    }
    @Test fun defaultXmlElementBudgetCoversTheWholePackage() {
        val source = parts("<w:document xmlns:w='$word'><w:body>${"<w:p/>".repeat(100000)}</w:body></w:document>")
        source["word/header1.xml"] = "<w:hdr xmlns:w='$word'>${"<w:p/>".repeat(100000)}</w:hdr>".toByteArray()
        val error = assertFailsWith<DocxPackageException> { DocxPackageReader().read(ByteArrayInputStream(zip(source))) }
        assertEquals("XML_NODES", error.reason.name)
    }
    @Test fun xmlElementBudgetIncludesAllPartsAndAcceptsTheExactBoundary() {
        // Six XML parts contain 6 + 2 + 5 + 2 + 2 + 2 elements; each is individually below 18.
        val bytes = zip()
        assertEquals(6, DocxPackageReader(DocxPackageLimits(maxXmlNodes = 19)).read(ByteArrayInputStream(bytes)).parts.size)
        reject(DocxPackageReason.XML_NODES, bytes, DocxPackageLimits(maxXmlNodes = 18))
    }
    @Test fun textNodeBoundCountsSplitSaxCallbacksAndCdata() {
        reject(DocxPackageReason.XML_TEXT, zip(parts(story("ab<![CDATA[cdef]]>gh"))), DocxPackageLimits(maxTextNodeChars = 7))
        reject(DocxPackageReason.XML_TEXT, zip(parts(story("x".repeat(20000)))), DocxPackageLimits(maxTextNodeChars = 18000))
    }
    @Test fun archiveXmlAndImageByteBoundsAreIndependent() {
        reject(DocxPackageReason.ARCHIVE_BYTES, zip(), DocxPackageLimits(maxArchiveBytes = 100))
        reject(DocxPackageReason.XML_BYTES, zip(), DocxPackageLimits(maxXmlBytes = 100))
        reject(DocxPackageReason.IMAGE_BYTES, zip(), DocxPackageLimits(maxImageBytes = 10))
    }
    @Test fun corruptZipMetadataCrcAndTruncationNeverYieldParts() {
        val original = zip()
        val crc = original.copyOf().apply { this[toString(Charsets.ISO_8859_1).indexOf("ordinary report")] = 'x'.code.toByte() }
        reject(DocxPackageReason.MALFORMED_ZIP, crc)
        reject(DocxPackageReason.MALFORMED_ZIP, original.copyOf(original.size - 1))
        reject(DocxPackageReason.MALFORMED_ZIP, original.copyOf().apply { this[central(this) + 47] = 'c'.code.toByte() })
        reject(DocxPackageReason.MALFORMED_ZIP, original + byteArrayOf(1))
        val inflatedSize = zip(stored = false)
        claimDocumentSize(inflatedSize, parts().getValue("word/document.xml").size + 1)
        reject(DocxPackageReason.MALFORMED_ZIP, inflatedSize)
    }
    @Test fun knownContentTypesCannotBeAssignedToTheWrongPart() {
        val source = parts()
        source["[Content_Types].xml"] = source.getValue("[Content_Types].xml").toString(Charsets.UTF_8)
            .replace("wordprocessingml.document.main+xml", "wordprocessingml.header+xml").toByteArray()
        reject(DocxPackageReason.UNSUPPORTED_CONTENT, zip(source))
    }
    @Test fun missingRequiredPartsAndWrongXmlRootsAreRejected() {
        reject(DocxPackageReason.INVALID_PACKAGE, zip(parts().apply { remove("_rels/.rels") }))
        reject(DocxPackageReason.INVALID_PACKAGE, zip(parts().apply { remove("word/document.xml") }))
        reject(DocxPackageReason.INVALID_PACKAGE, zip(parts().apply { this["_rels/.rels"] = "<Relationships xmlns='$rel'/>".toByteArray() }))
        reject(DocxPackageReason.INVALID_PACKAGE, zip(parts("<private/>")))
    }
    @Test fun sourceReadsAreBoundedAndOwnedByCallerAndErrorsAreRedacted() {
        var readCount = 0
        var closed = false
        val source = object : InputStream() {
            override fun read(): Int { readCount++; return 0 }
            override fun close() { closed = true }
        }
        assertFailsWith<DocxPackageException> { DocxPackageReader(DocxPackageLimits(maxArchiveBytes = 100)).read(source) }
        assertEquals(101, readCount)
        assertFalse(closed)
        val failure = assertFailsWith<DocxPackageException> {
            DocxPackageReader().read(object : InputStream() { override fun read(): Int = throw IOException("private source path") })
        }
        assertEquals(DocxPackageReason.SOURCE_READ, failure.reason)
        assertNull(failure.cause)
        assertFalse(failure.toString().contains("private"))
    }
    @Test fun predefinedEntitiesStayInertAndSupportedInternalTargetsResolve() {
        val source = parts(story("one &amp; two &#65;"))
        source["word/_rels/document.xml.rels"] = relationships("media/image1.png", "image", "TargetMode='Internal'").toByteArray()
        assertEquals(6, DocxPackageReader().read(ByteArrayInputStream(zip(source))).parts.size)
    }
    @Test fun providerRuntimeReadFailuresAreRedacted() {
        for (cause in listOf(SecurityException("private content URI"), IllegalStateException("private provider path"), IllegalArgumentException("private source"))) {
            val error = assertFailsWith<DocxPackageException> {
                DocxPackageReader().read(object : InputStream() { override fun read(): Int = throw cause })
            }
            assertEquals(DocxPackageReason.SOURCE_READ, error.reason)
            assertNull(error.cause)
            assertFalse(error.toString().contains("private"))
        }
    }
    @Test fun ordinaryWordMetadataIsValidatedButNotExposed() {
        val source = parts()
        var declarations = source.getValue("[Content_Types].xml").toString(Charsets.UTF_8)
        val auxiliary = listOf(
            Triple("word/styles.xml", "wordprocessingml.styles", "<w:styles xmlns:w='$word'/>"),
            Triple("word/numbering.xml", "wordprocessingml.numbering", "<w:numbering xmlns:w='$word'/>"),
            Triple("word/fontTable.xml", "wordprocessingml.fontTable", "<w:fonts xmlns:w='$word'/>"),
            Triple("word/settings.xml", "wordprocessingml.settings", "<w:settings xmlns:w='$word'/>"),
            Triple("word/theme/theme1.xml", "theme", "<a:theme xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main' name='Office'/>"),
            Triple("docProps/core.xml", "core", "<cp:coreProperties xmlns:cp='http://schemas.openxmlformats.org/package/2006/metadata/core-properties'/>"),
            Triple("docProps/app.xml", "extended-properties", "<Properties xmlns='http://schemas.openxmlformats.org/officeDocument/2006/extended-properties'/>"),
        )
        auxiliary.forEachIndexed { index, (name, suffix, xml) ->
            val type = if (suffix == "core") "application/vnd.openxmlformats-package.core-properties+xml"
                else "application/vnd.openxmlformats-officedocument.$suffix+xml"
            source[name] = xml.toByteArray()
            declarations = declarations.replace("</Types>", "<Override PartName='/$name' ContentType='$type'/></Types>")
            val relationType = if (suffix == "core") "$rel/metadata/core-properties" else office + suffix.substringAfter('.')
            val relationPart = if (name.startsWith("docProps")) "_rels/.rels" else "word/_rels/document.xml.rels"
            val target = if (relationPart == "_rels/.rels") name else name.removePrefix("word/")
            source[relationPart] = source.getValue(relationPart).toString(Charsets.UTF_8).replace("</Relationships>",
                "<Relationship Id='aux$index' Type='$relationType' Target='$target'/></Relationships>").toByteArray()
        }
        source["[Content_Types].xml"] = declarations.toByteArray()
        assertEquals(6, DocxPackageReader().read(ByteArrayInputStream(zip(source))).parts.size)
        source["docProps/core.xml"] = "<!DOCTYPE metadata><metadata/>".toByteArray()
        reject(DocxPackageReason.DTD_OR_ENTITY, zip(source))
    }
    /* TD174 R4, 2026-09-07: ten independent faults compiled and failed real tests (94 tests/run).
     * Final production SHA-256:
     * DocxPackageReader.kt f402d9094807c6500a31da9d9a58a08296e5d80530d084c1392a4375c9a9a747
     * DocxXmlBoundary.kt 9f752de380a744e7eb6b3f3ec49aea6f3a131c50d3d868bbe1d5b1e2288d3fae
     * M1 exit 1 -> customPropertiesAreInertAndDiscardedForBothZipMethodsAndXmlEncodings
     * M2 exit 1 -> customPartPathContentTypeAndRootAreExact
     * M3 exit 1 -> customPartPathContentTypeAndRootAreExact
     * M4 exit 1 -> customPartPathContentTypeAndRootAreExact
     * M5 exit 1 -> customPropertiesAreInertAndDiscardedForBothZipMethodsAndXmlEncodings
     * M6 exit 1 -> customPropertiesRequireOneInternalRootBinding
     * M7 exit 1 -> customPropertiesRequireOneInternalRootBinding
     * M8 exit 1 -> customPropertiesRequireOneInternalRootBinding
     * M9 exit 1 -> discardedCustomPropertiesStillRejectUnsafeAndMalformedXmlWithoutLeakingDiagnostics
     * M10 exit 1 -> customPropertiesNeverBecomeExtractionEvidence
     * Exact substitutions, commands, original test hashes and fresh XML are in
     * .review/T3-DOCX-CUSTOM-PROPERTIES-r4 (preserved in the owning local task evidence).
     * New tests retain distinct binding, resource, encoding, rejection and manifest obligations;
     * no whole-test redundancy candidate was identified. Historical R4 is not reused as current evidence.
     */
    private val customNamespace = "http://schemas.openxmlformats.org/officeDocument/2006/custom-properties"
    private val customType = "application/vnd.openxmlformats-officedocument.custom-properties+xml"
    private fun customXml(body: String = "") = "<Properties xmlns='$customNamespace'>$body</Properties>"
    private fun customRelationship(id: String = "custom", target: String = "docProps/custom.xml", mode: String = "") =
        "<Relationship Id='$id' Type='${office}custom-properties' Target='$target' $mode/>"
    private fun customParts(xml: String = customXml()): LinkedHashMap<String, ByteArray> = parts().apply {
        this["docProps/custom.xml"] = xml.toByteArray()
        this["[Content_Types].xml"] = getValue("[Content_Types].xml").toString(Charsets.UTF_8)
            .replace("</Types>", "<Override PartName='/docProps/custom.xml' ContentType='$customType'/></Types>").toByteArray()
        this["_rels/.rels"] = getValue("_rels/.rels").toString(Charsets.UTF_8)
            .replace("</Relationships>", customRelationship() + "</Relationships>").toByteArray()
    }
    private fun replaceRootCustom(source: LinkedHashMap<String, ByteArray>, replacement: String) {
        source["_rels/.rels"] = source.getValue("_rels/.rels").toString(Charsets.UTF_8)
            .replace(customRelationship(), replacement).toByteArray()
    }

    @Test fun customPropertiesAreInertAndDiscardedForBothZipMethodsAndXmlEncodings() {
        val markers = listOf("PRIVATE_PROPERTY_NAME_731", "PRIVATE_PROPERTY_VALUE_842", "PRIVATE_COMMENT_953")
        val xml = customXml("<!--${markers[2]}--><property name='${markers[0]}' pid='2'>" +
            "<vt:lpwstr xmlns:vt='http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes'>" +
            "${markers[1]} https://private.invalid/metadata file:///private/path</vt:lpwstr></property>" +
            "<property><number>12</number><boolean>true</boolean><date>2026-09-07</date></property>")
        val expected = DocxPackageReader().read(ByteArrayInputStream(zip())).parts
        for (stored in listOf(true, false)) for (encoding in listOf(Charsets.UTF_8, Charsets.UTF_16)) {
            val source = customParts(xml).apply { this["docProps/custom.xml"] = xml.toByteArray(encoding) }
            val actual = DocxPackageReader().read(ByteArrayInputStream(zip(source, stored))).parts
            assertEquals(expected.map { it.name to it.kind }, actual.map { it.name to it.kind })
            actual.forEach { part ->
                markers.forEach { assertFalse(part.copyBytes().toString(Charsets.UTF_8).contains(it), part.name) }
                if (part.name != "_rels/.rels") {
                    assertContentEquals(expected.single { it.name == part.name }.copyBytes(), part.copyBytes())
                }
            }
        }
        assertEquals(expected.size, DocxPackageReader().read(ByteArrayInputStream(zip(customParts()))).parts.size)
        val explicit = customParts().also { replaceRootCustom(it, customRelationship(mode = "TargetMode='Internal'")) }
        assertEquals(expected.size, DocxPackageReader().read(ByteArrayInputStream(zip(explicit))).parts.size)
    }

    @Test fun customPartPathContentTypeAndRootAreExact() {
        for (path in listOf("docProps/custom2.xml", "docProps/private.xml", "word/custom.xml", "docProps/_rels/custom.xml.rels")) {
            reject(DocxPackageReason.UNSUPPORTED_PART, zip(customParts().apply { this[path] = customXml().toByteArray() }))
        }
        val duplicate = customParts().apply { this["DOCPROPS/CUSTOM.XML"] = customXml().toByteArray() }
        reject(DocxPackageReason.DUPLICATE_PART, zip(duplicate))
        val wrongType = customParts().apply {
            this["[Content_Types].xml"] = getValue("[Content_Types].xml").toString(Charsets.UTF_8)
                .replace(customType, "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml").toByteArray()
        }
        reject(DocxPackageReason.UNSUPPORTED_CONTENT, zip(wrongType))
        for (xml in listOf("<Properties/>", "<properties xmlns='$customNamespace'/>",
            "<Properties xmlns='http://schemas.openxmlformats.org/officeDocument/2006/extended-properties'/>",
            "<Other xmlns='$customNamespace'/>")) {
            reject(DocxPackageReason.INVALID_PACKAGE, zip(customParts(xml)))
        }
        val upper = customParts().apply { this["DOCPROPS/CUSTOM.XML"] = remove("docProps/custom.xml")!! }
        assertEquals(6, DocxPackageReader().read(ByteArrayInputStream(zip(upper))).parts.size)
    }

    @Test fun customPropertiesRequireOneInternalRootBinding() {
        val bindings = listOf("", customRelationship() + customRelationship("custom2"),
            customRelationship(mode = "TargetMode='External'"), customRelationship(mode = "TargetMode='external'"),
            customRelationship().replace("custom-properties", "extended-properties"),
            customRelationship(target = "docProps/missing.xml"), customRelationship(target = "word/document.xml"),
            customRelationship(target = "https://private.invalid/custom.xml"), customRelationship(target = "/docProps/custom.xml"),
            customRelationship(target = "../docProps/custom.xml"), customRelationship(target = "docProps/./custom.xml"))
        bindings.forEach { replacement ->
            reject(DocxPackageReason.UNSAFE_RELATIONSHIP, zip(customParts().also { replaceRootCustom(it, replacement) }))
        }
        val dangling = customParts().apply {
            remove("docProps/custom.xml")
            this["[Content_Types].xml"] = parts().getValue("[Content_Types].xml")
        }
        reject(DocxPackageReason.UNSAFE_RELATIONSHIP, zip(dangling))
        val wrongOwner = customParts().also { replaceRootCustom(it, "") }.apply {
            this["word/_rels/document.xml.rels"] = getValue("word/_rels/document.xml.rels").toString(Charsets.UTF_8)
                .replace("</Relationships>", customRelationship(target = "../docProps/custom.xml") + "</Relationships>").toByteArray()
        }
        reject(DocxPackageReason.UNSAFE_RELATIONSHIP, zip(wrongOwner))
    }

    @Test fun discardedCustomPropertiesStillRejectUnsafeAndMalformedXmlWithoutLeakingDiagnostics() {
        for (encoding in listOf(Charsets.UTF_8, Charsets.UTF_16)) {
            for (declaration in listOf("<!DOCTYPE Properties>",
                "<!DOCTYPE Properties [<!ENTITY secret SYSTEM 'file:///PRIVATE_CUSTOM_SECRET'>]>",
                "<!DOCTYPE Properties [<!ENTITY secret 'PRIVATE_CUSTOM_SECRET'>]>")) {
                val source = customParts().apply { this["docProps/custom.xml"] = (declaration + customXml()).toByteArray(encoding) }
                reject(DocxPackageReason.DTD_OR_ENTITY, zip(source))
            }
        }
        for (body in listOf("<?private PRIVATE_CUSTOM_SECRET?>", "<x:include xmlns:x='http://www.w3.org/2001/XInclude' href='PRIVATE_CUSTOM_SECRET'/>",
            "<script>PRIVATE_CUSTOM_SECRET</script>", "<object/>", "<control/>", "<altChunk/>", "<OLEObject/>")) {
            reject(DocxPackageReason.UNSUPPORTED_CONTENT, zip(customParts(customXml(body))))
        }
        val previous = System.err
        val diagnostics = ByteArrayOutputStream()
        try {
            System.setErr(java.io.PrintStream(diagnostics, true, "UTF-8"))
            for (xml in listOf(customXml("<private>PRIVATE_CUSTOM_SECRET"), customXml("&PRIVATE_CUSTOM_SECRET;"))) {
                val failure = reject(DocxPackageReason.MALFORMED_XML, zip(customParts(xml)))
                assertFalse(failure.toString().contains("PRIVATE_CUSTOM_SECRET"))
            }
            assertEquals("", diagnostics.toString("UTF-8"))
        } finally { System.setErr(previous) }
    }

    @Test fun customPartXmlBudgetsAcceptExactLimitsAndRejectOneBeyond() {
        val depthBytes = zip(customParts(customXml("<a><b><c><d><e/></d></c></b></a>")))
        assertEquals(6, DocxPackageReader(DocxPackageLimits(maxXmlDepth = 6)).read(ByteArrayInputStream(depthBytes)).parts.size)
        reject(DocxPackageReason.XML_DEPTH, depthBytes, DocxPackageLimits(maxXmlDepth = 5))
        val textBytes = zip(customParts(customXml("<property>" + "x".repeat(30) + "<![CDATA[yz]]></property>")))
        assertEquals(6, DocxPackageReader(DocxPackageLimits(maxTextNodeChars = 32)).read(ByteArrayInputStream(textBytes)).parts.size)
        reject(DocxPackageReason.XML_TEXT, textBytes, DocxPackageLimits(maxTextNodeChars = 31))
        // Original fixture has 19 elements; custom adds its override, relationship, root and two children.
        val nodeBytes = zip(customParts(customXml("<a/><b/>")))
        assertEquals(6, DocxPackageReader(DocxPackageLimits(maxXmlNodes = 24)).read(ByteArrayInputStream(nodeBytes)).parts.size)
        reject(DocxPackageReason.XML_NODES, nodeBytes, DocxPackageLimits(maxXmlNodes = 23))
        val source = customParts(customXml("<property>" + "z".repeat(2500) + "</property>"))
        val size = source.getValue("docProps/custom.xml").size
        val bytes = zip(source)
        assertEquals(6, DocxPackageReader(DocxPackageLimits(maxXmlBytes = size)).read(ByteArrayInputStream(bytes)).parts.size)
        reject(DocxPackageReason.XML_BYTES, bytes, DocxPackageLimits(maxXmlBytes = size - 1))
    }

    @Test fun customPartStillConsumesZipBudgetsAndMustPassCrc() {
        val source = customParts(customXml("<property>" + "z".repeat(2500) + "</property>"))
        val bytes = zip(source)
        val size = source.getValue("docProps/custom.xml").size
        val total = source.values.sumOf { it.size.toLong() }
        val exact = DocxPackageLimits(maxArchiveBytes = bytes.size, maxEntries = source.size, maxEntryBytes = size, maxTotalBytes = total)
        assertEquals(6, DocxPackageReader(exact).read(ByteArrayInputStream(bytes)).parts.size)
        reject(DocxPackageReason.ARCHIVE_BYTES, bytes, exact.copy(maxArchiveBytes = bytes.size - 1))
        reject(DocxPackageReason.ENTRY_COUNT, bytes, exact.copy(maxEntries = source.size - 1))
        reject(DocxPackageReason.ENTRY_BYTES, bytes, exact.copy(maxEntryBytes = size - 1))
        reject(DocxPackageReason.TOTAL_BYTES, bytes, exact.copy(maxTotalBytes = total - 1))
        val compressed = customParts(customXml("<property>" + "z".repeat(20000) + "</property>"))
        reject(DocxPackageReason.COMPRESSION_RATIO, zip(compressed, false), DocxPackageLimits(maxCompressionRatio = 20))
        val corrupt = bytes.copyOf()
        val marker = corrupt.toString(Charsets.ISO_8859_1).indexOf("z".repeat(50))
        assertTrue(marker >= 0)
        corrupt[marker] = 'y'.code.toByte()
        reject(DocxPackageReason.MALFORMED_ZIP, corrupt)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    @Test fun validAndHostileReadsCannotWriteOrConnect() {
        val original = System.getSecurityManager()
        val thread = Thread.currentThread()
        var forbiddenCalls = 0
        val guard = object : SecurityManager() {
            override fun checkPermission(permission: java.security.Permission?) = Unit
            private fun rejectSideEffect() {
                if (Thread.currentThread() === thread) { forbiddenCalls++; throw SecurityException("forbidden side effect") }
            }
            override fun checkWrite(file: String?) = rejectSideEffect()
            override fun checkWrite(file: java.io.FileDescriptor?) = rejectSideEffect()
            override fun checkDelete(file: String?) = rejectSideEffect()
            override fun checkConnect(host: String?, port: Int) = rejectSideEffect()
        }
        try {
            System.setSecurityManager(guard)
            assertEquals(6, DocxPackageReader().read(ByteArrayInputStream(zip())).parts.size)
            reject(DocxPackageReason.DTD_OR_ENTITY, zip(parts("<!DOCTYPE w:document SYSTEM 'http://private.invalid/test'>" + story())))
            assertEquals(0, forbiddenCalls)
        } finally { System.setSecurityManager(original) }
    }
    private fun central(bytes: ByteArray) = (bytes.size - 22).let { at ->
        (0..3).sumOf { (bytes[at + 16 + it].toInt() and 255) shl (8 * it) }
    }
    private fun u16(bytes: ByteArray, at: Int) = (bytes[at].toInt() and 255) or ((bytes[at + 1].toInt() and 255) shl 8)
    private fun patch16(bytes: ByteArray, at: Int, value: Int) { repeat(2) { bytes[at + it] = (value ushr (8 * it)).toByte() } }
    private fun patch32(bytes: ByteArray, at: Int, value: Int) { repeat(4) { bytes[at + it] = (value ushr (8 * it)).toByte() } }
    private fun claimDocumentSize(input: ByteArray, size: Int) {
        var at = central(input)
        repeat(2) { at += 46 + u16(input, at + 28) + u16(input, at + 30) + u16(input, at + 32) }
        patch32(input, at + 24, size)
        val local = (0..3).sumOf { (input[at + 42 + it].toInt() and 255) shl (8 * it) }
        val compressed = (0..3).sumOf { (input[at + 20 + it].toInt() and 255) shl (8 * it) }
        val descriptor = local + 30 + u16(input, local + 26) + u16(input, local + 28) + compressed
        patch32(input, descriptor + 12, size)
    }
}

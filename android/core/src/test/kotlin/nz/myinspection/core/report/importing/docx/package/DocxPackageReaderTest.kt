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

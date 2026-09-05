package nz.myinspection.core.report.importing.docx.`package`

import java.io.ByteArrayInputStream
import java.io.IOException
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import org.xml.sax.ext.DefaultHandler2

internal class DocxXmlBoundary(private val limits: DocxPackageLimits, private val fail: (DocxPackageReason) -> Nothing) {
    private val word = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private val rel = "http://schemas.openxmlformats.org/package/2006/relationships"
    private val types = "http://schemas.openxmlformats.org/package/2006/content-types"
    private val office = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/"
    private val defaults = HashMap<String, String>()
    private val overrides = HashMap<String, String>()
    private var elements = 0L

    fun validate(parts: Map<String, ByteArray>) {
        if (!parts.keys.containsAll(listOf("[content_types].xml", "_rels/.rels", "word/document.xml"))) fail(DocxPackageReason.INVALID_PACKAGE)
        parts.forEach { (name, bytes) ->
            if (partKind(name) == DocxPartKind.IMAGE) {
                val signature = if (name.endsWith(".png")) byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10) else byteArrayOf(255.toByte(), 216.toByte(), 255.toByte())
                if (bytes.size < signature.size || !bytes.copyOfRange(0, signature.size).contentEquals(signature)) fail(DocxPackageReason.UNSUPPORTED_CONTENT)
            } else parse(name, bytes, parts)
        }
        parts.keys.filter { it != "[content_types].xml" }.forEach { name ->
            if ((overrides[name] ?: defaults[name.substringAfterLast('.')]) != contentType(name)) fail(DocxPackageReason.UNSUPPORTED_CONTENT)
        }
    }

    private fun parse(name: String, bytes: ByteArray, parts: Map<String, ByteArray>) {
        val handler = object : DefaultHandler2() {
            var depth = 0
            var text = 0L
            var rootRelationship = false
            val ids = HashSet<String>()
            override fun startDTD(name: String?, publicId: String?, systemId: String?) = fail(DocxPackageReason.DTD_OR_ENTITY)
            override fun resolveEntity(publicId: String?, systemId: String?): InputSource = fail(DocxPackageReason.DTD_OR_ENTITY)
            override fun resolveEntity(name: String?, publicId: String?, baseURI: String?, systemId: String?): InputSource = fail(DocxPackageReason.DTD_OR_ENTITY)
            override fun skippedEntity(name: String?) = fail(DocxPackageReason.DTD_OR_ENTITY)
            override fun warning(e: SAXParseException?) = fail(DocxPackageReason.MALFORMED_XML)
            override fun error(e: SAXParseException?) = fail(DocxPackageReason.MALFORMED_XML)
            override fun fatalError(e: SAXParseException?) = fail(DocxPackageReason.MALFORMED_XML)
            override fun processingInstruction(target: String?, data: String?) = fail(DocxPackageReason.UNSUPPORTED_CONTENT)
            override fun startElement(uri: String, local: String, qName: String, attributes: Attributes) {
                if (++elements > limits.maxXmlNodes) fail(DocxPackageReason.XML_NODES)
                if (++depth > limits.maxXmlDepth) fail(DocxPackageReason.XML_DEPTH)
                text = 0
                if (uri == "http://www.w3.org/2001/XInclude") fail(DocxPackageReason.UNSUPPORTED_CONTENT)
                if (local in setOf("object", "control", "altChunk", "OLEObject", "script")) fail(DocxPackageReason.UNSUPPORTED_CONTENT)
                if (depth == 1) {
                    val expected = when {
                        name == "[content_types].xml" -> types to "Types"
                        partKind(name) == DocxPartKind.RELATIONSHIPS -> rel to "Relationships"
                        partKind(name) == DocxPartKind.DOCUMENT -> word to "document"
                        partKind(name) == DocxPartKind.HEADER -> word to "hdr"
                        partKind(name) == DocxPartKind.FOOTER -> word to "ftr"
                        else -> null
                    }
                    if (expected != null && expected != uri to local) fail(DocxPackageReason.INVALID_PACKAGE)
                } else if (name == "[content_types].xml") {
                    if (depth != 2 || uri != types || local !in listOf("Default", "Override")) fail(DocxPackageReason.UNSUPPORTED_CONTENT)
                    val value = attributes.getValue("", "ContentType") ?: fail(DocxPackageReason.UNSUPPORTED_CONTENT)
                    if (value !in parts.keys.mapNotNull(::contentType) + "application/xml") fail(DocxPackageReason.UNSUPPORTED_CONTENT)
                    if (local == "Default") {
                        val extension = attributes.getValue("", "Extension") ?: fail(DocxPackageReason.UNSUPPORTED_CONTENT)
                        if (!extension.matches(Regex("[a-z0-9]+")) || defaults.put(extension, value) != null) fail(DocxPackageReason.UNSUPPORTED_CONTENT)
                    } else {
                        val path = attributes.getValue("", "PartName") ?: fail(DocxPackageReason.UNSUPPORTED_CONTENT)
                        if (!path.startsWith('/')) fail(DocxPackageReason.UNSUPPORTED_CONTENT)
                        val target = safeName(path.drop(1), fail)
                        if (target !in parts || overrides.put(target, value) != null) fail(DocxPackageReason.UNSUPPORTED_CONTENT)
                    }
                } else if (partKind(name) == DocxPartKind.RELATIONSHIPS) {
                    if (depth != 2 || uri != rel || local != "Relationship") fail(DocxPackageReason.UNSAFE_RELATIONSHIP)
                    val id = attributes.getValue("", "Id")
                    if (id.isNullOrEmpty() || !ids.add(id)) fail(DocxPackageReason.UNSAFE_RELATIONSHIP)
                    val mode = attributes.getValue("", "TargetMode")
                    if (mode != null && mode != "Internal") fail(DocxPackageReason.UNSAFE_RELATIONSHIP)
                    val raw = attributes.getValue("", "Target") ?: fail(DocxPackageReason.UNSAFE_RELATIONSHIP)
                    val target = try { safeName(raw, fail) } catch (_: DocxPackageException) { fail(DocxPackageReason.UNSAFE_RELATIONSHIP) }
                    val resolved = (if (name == "_rels/.rels") "" else "word/") + target
                    val type = attributes.getValue("", "Type")
                    if (resolved !in parts || type != relationshipType(resolved)) fail(DocxPackageReason.UNSAFE_RELATIONSHIP)
                    if (name == "_rels/.rels" && resolved == "word/document.xml") rootRelationship = true
                }
            }
            override fun endElement(uri: String?, localName: String?, qName: String?) { depth--; text = 0 }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                text += length
                if (text > limits.maxTextNodeChars) fail(DocxPackageReason.XML_TEXT)
            }
            override fun ignorableWhitespace(ch: CharArray, start: Int, length: Int) = characters(ch, start, length)
            override fun endDocument() {
                if (name == "_rels/.rels" && !rootRelationship) fail(DocxPackageReason.INVALID_PACKAGE)
            }
        }
        try {
            // Android's ExpatReader supports these standard SAX controls and lexical startDTD.
            // Xerces-only disallow-doctype-decl and StAX are deliberately not required on ART.
            val reader = SAXParserFactory.newInstance().apply { isNamespaceAware = true; isValidating = false }.newSAXParser().xmlReader
            reader.setFeature("http://xml.org/sax/features/external-general-entities", false)
            reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
            reader.contentHandler = handler
            reader.entityResolver = handler
            reader.errorHandler = handler
            reader.parse(InputSource(ByteArrayInputStream(bytes)))
        } catch (_: ParserConfigurationException) { fail(DocxPackageReason.XML_UNAVAILABLE) }
        catch (_: SAXException) { fail(DocxPackageReason.MALFORMED_XML) }
        catch (_: IOException) { fail(DocxPackageReason.MALFORMED_XML) }
    }
    private fun relationshipType(name: String): String? = when (partKind(name)) {
        DocxPartKind.DOCUMENT -> office + "officeDocument"
        DocxPartKind.HEADER -> office + "header"
        DocxPartKind.FOOTER -> office + "footer"
        DocxPartKind.IMAGE -> office + "image"
        else -> when {
            name == "docprops/core.xml" -> "http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties"
            name == "docprops/app.xml" -> office + "extended-properties"
            name.matches(Regex("word/theme/theme[0-9]+\\.xml")) -> office + "theme"
            name.matches(Regex("word/(styles|settings|websettings|fonttable|numbering)\\.xml")) -> office + when (name) {
                "word/fonttable.xml" -> "fontTable"
                "word/websettings.xml" -> "webSettings"
                else -> name.substringAfter('/').substringBefore('.')
            }
            else -> null
        }
    }
}

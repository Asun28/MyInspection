package nz.myinspection.core.report.importing.docx.extract

import java.io.ByteArrayInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import org.xml.sax.ext.DefaultHandler2
import nz.myinspection.core.report.importing.docx.`package`.DocxPart

internal const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
internal class Element(val uri: String, val name: String, val attrs: Map<String, String>, val parent: Element?) {
    val children = ArrayList<Element>()
    val value = StringBuilder()
    fun isWord(local: String) = uri == W && name == local
    fun descendants(): Sequence<Element> = sequence { yield(this@Element); children.forEach { yieldAll(it.descendants()) } }
    fun ancestor(local: String): Element? = generateSequence(parent) { it.parent }.firstOrNull { it.isWord(local) }
    fun attr(local: String) = attrs["$W|$local"] ?: attrs["|$local"]
}

internal fun parse(part: DocxPart): Element {
    var root: Element? = null
    var current: Element? = null
    val handler = object : DefaultHandler2() {
        override fun startElement(uri: String, local: String, qName: String, attrs: Attributes) {
            val node = Element(uri, local, (0 until attrs.length).associate { "${attrs.getURI(it)}|${attrs.getLocalName(it)}" to attrs.getValue(it) }, current)
            current?.children?.add(node)
            if (root == null) root = node
            current = node
        }
        override fun endElement(uri: String?, local: String?, qName: String?) { current = current?.parent }
        override fun characters(chars: CharArray, start: Int, length: Int) { current?.value?.append(chars, start, length) }
        override fun startDTD(name: String?, publicId: String?, systemId: String?) { throw IllegalArgumentException("DOCX_XML") }
        override fun resolveEntity(publicId: String?, systemId: String?): InputSource = throw IllegalArgumentException("DOCX_XML")
        override fun error(e: SAXParseException?): Unit = throw IllegalArgumentException("DOCX_XML")
        override fun fatalError(e: SAXParseException?): Unit = throw IllegalArgumentException("DOCX_XML")
    }
    try {
        val reader = SAXParserFactory.newInstance().apply { isNamespaceAware = true; isValidating = false }.newSAXParser().xmlReader
        reader.setFeature("http://xml.org/sax/features/external-general-entities", false)
        reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
        reader.contentHandler = handler
        reader.entityResolver = handler
        reader.errorHandler = handler
        reader.parse(InputSource(ByteArrayInputStream(part.copyBytes())))
        return requireNotNull(root)
    } catch (_: Exception) { throw IllegalArgumentException("DOCX_XML") }
}

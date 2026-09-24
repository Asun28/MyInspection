package nz.myinspection.core.report.importing.docx.extract

import java.io.File
import java.net.URL
import java.security.Permission
import kotlin.test.*
import nz.myinspection.core.report.importing.docx.`package`.DocxPart
import nz.myinspection.core.report.importing.docx.`package`.DocxPartKind

// R4 fresh replay (2026-09-08): 11 source + 2 I/O-counter mutants failed named assertions.
// Source SHA-256: 73bfa6f6a26732ba7da5b1774ea47f86011e8584e507bda3054ece02dcff17de.
// Removing ancestorChoosesNearestWordParentAndExcludesSelf let ancestor-self survive the
// full core suite (895 tests, 4 existing skips); restored this unique guard and original bytes.
// Recipes, per-mutant XML and exact baseline/restoration hashes: .review/xml-r4/.
class DocxXmlTreeTest {
    private val word = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    // Production parts have already passed the reader's byte/depth/node/text limits.
    // These synthetic parts bypass that boundary to test this parser independently.
    private fun xml(text: String) = parse(DocxPart("word/document.xml", DocxPartKind.DOCUMENT, text.toByteArray()))

    @Test fun expandedNamesKeepNamespaceCollisionsDistinct() {
        val root = xml("""<document xmlns="$word" xmlns:q="$word" xmlns:x="urn:other">
            <q:p q:val="word" val="plain" x:val="foreign" xml:space="preserve"/>
            <x:p val="fallback"/><p xmlns="" x:val="foreign-only"/>
        </document>""")
        assertTrue(root.isWord("document"))
        assertFalse(root.isWord("p"))
        assertEquals(3, root.children.size)
        val (wordNode, foreign, unqualified) = root.children
        assertEquals(listOf(word, "urn:other", ""), root.children.map { it.uri })
        assertEquals(listOf("p", "p", "p"), root.children.map { it.name })
        assertEquals(listOf(true, false, false), root.children.map { it.isWord("p") })
        assertEquals(mapOf("$word|val" to "word", "|val" to "plain", "urn:other|val" to "foreign",
            "http://www.w3.org/XML/1998/namespace|space" to "preserve"), wordNode.attrs)
        assertEquals("word", wordNode.attr("val"))
        assertEquals("fallback", foreign.attr("val"))
        assertNull(unqualified.attr("val"))
        assertNull(wordNode.attr("missing"))
    }

    @Test fun characterEventsPreserveRawTextWithoutFlatteningChildren() {
        val longText = "Māori 房屋 ".repeat(1500)
        val root = xml("<w:p xmlns:w='$word'>head<![CDATA[<&>]]>&amp;&#x1F3E0;" +
            "<w:r>$longText</w:r> tail<!-- ignored --><?note ignored?><w:r>last</w:r>end</w:p>")
        assertEquals("head<&>&" + String(Character.toChars(0x1F3E0)) + " tailend", root.value.toString())
        assertEquals(listOf(longText, "last"), root.children.map { it.value.toString() })
        assertEquals(2, root.children.size)
    }

    @Test fun traversalIncludesSelfAndKeepsEncounterOrderAndParents() {
        val root = xml("<w:document xmlns:w='$word'><w:p><w:r/><w:t/></w:p><w:tbl><w:tr/></w:tbl><w:p/></w:document>")
        val nodes = root.descendants().toList()
        assertEquals(listOf("document", "p", "r", "t", "tbl", "tr", "p"), nodes.map { it.name })
        assertSame(root, nodes.first())
        assertNull(root.parent)
        assertSame(root, nodes[1].parent)
        assertSame(nodes[1], nodes[2].parent)
        assertSame(nodes[1], nodes[3].parent)
        assertSame(root, nodes[4].parent)
        assertSame(nodes[4], nodes[5].parent)
        assertSame(root, nodes[6].parent)
        assertEquals(listOf(nodes[4], nodes[5]), nodes[4].descendants().toList())
        assertEquals(listOf(nodes[6]), nodes[6].descendants().toList())
    }

    @Test fun ancestorChoosesNearestWordParentAndExcludesSelf() {
        val root = xml("<w:document xmlns:w='$word' xmlns:x='urn:other'><w:p><x:p><w:p><w:t/></w:p></x:p></w:p></w:document>")
        val nodes = root.descendants().toList()
        assertEquals(listOf("document", "p", "p", "p", "t"), nodes.map { it.name })
        assertSame(nodes[3], nodes[4].ancestor("p"))
        assertSame(nodes[1], nodes[3].ancestor("p"))
        assertSame(root, nodes[4].ancestor("document"))
        assertNull(nodes[1].ancestor("p"))
        assertNull(root.ancestor("document"))
        assertNull(nodes[4].ancestor("missing"))
    }

    @Test fun malformedXmlFailsWithTheBoundaryError() {
        val malformed = listOf("", "<p>", "<p></r>", "<p/><p/>", "<w:p/>", "<p>&missing;</p>",
            "<p a='one' a='two'/>", "<p xmlns:a='urn:x' xmlns:b='urn:x' a:v='one' b:v='two'/>",
            "<p>${0.toChar()}</p>")
        for (input in malformed) {
            val error = assertFailsWith<IllegalArgumentException> { xml(input) }
            assertEquals("DOCX_XML", error.message)
            assertNull(error.cause)
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    @Test fun hostileXmlRejectsEveryDtdBeforeFileOrNetworkAccess() {
        val target = File("docx-xml-tree-probe.txt").absoluteFile
        val targets = listOf("file" to target.toURI().toASCIIString(), "network" to "http://xml-probe.invalid/entity")
        val body = "<w:p xmlns:w='$word'>Synthetic XML control</w:p>"
        val hostile = listOf("doctype" to ("<!DOCTYPE w:p>" + body),
            "internal-general" to ("<!DOCTYPE w:p [<!ENTITY a 'internal'>]>" +
                body.replace("Synthetic XML control", "&a;")),
            "internal-expansion" to ("<!DOCTYPE w:p [<!ENTITY a 'xxxx'><!ENTITY b '&a;&a;&a;&a;'>]>" +
                body.replace("Synthetic XML control", "&b;"))) + targets.flatMap { (kind, systemId) -> listOf(
            "$kind-general" to ("<!DOCTYPE w:p [<!ENTITY external SYSTEM '$systemId'>]>" +
                body.replace("Synthetic XML control", "&external;")),
            "$kind-parameter" to ("<!DOCTYPE w:p [<!ENTITY % external SYSTEM '$systemId'>%external;]>" + body),
            "$kind-subset" to ("<!DOCTYPE w:p SYSTEM '$systemId'>" + body)) }
        val previous = System.getSecurityManager()
        var fileCalls = 0
        var networkCalls = 0
        val guard = object : SecurityManager() {
            override fun checkPermission(permission: Permission?) {
                if (permission != null) previous?.checkPermission(permission)
            }
            override fun checkRead(file: String?) {
                if (file != null && File(file).absoluteFile == target) {
                    fileCalls++
                    throw SecurityException("Forbidden XML file read")
                }
                if (file != null) previous?.checkRead(file)
            }
            override fun checkConnect(host: String?, port: Int): Unit {
                networkCalls++
                throw SecurityException("Forbidden XML network access")
            }
        }
        try {
            // JDK 17 only: calibrate actual I/O entry points before trusting zero counters.
            System.setSecurityManager(guard)
            assertFailsWith<SecurityException> { target.inputStream().close() }
            assertTrue(fileCalls > 0)
            assertFailsWith<SecurityException> { URL(targets.last().second).openStream().close() }
            assertTrue(networkCalls > 0)
            fileCalls = 0
            networkCalls = 0
            assertEquals("Synthetic XML control", xml(body).value.toString())
            assertEquals(0, fileCalls)
            assertEquals(0, networkCalls)
            val violations = mutableListOf<String>()
            for ((name, input) in hostile) {
                fileCalls = 0
                networkCalls = 0
                val error = runCatching { xml(input) }.exceptionOrNull()
                if (error !is IllegalArgumentException || error.message != "DOCX_XML" || error.cause != null) {
                    violations += "$name: expected DOCX_XML rejection, got $error"
                }
                if (fileCalls != 0 || networkCalls != 0) {
                    violations += "$name: attempted file=$fileCalls network=$networkCalls"
                }
            }
            assertEquals(emptyList(), violations)
        } finally {
            System.setSecurityManager(previous)
        }
        assertSame(previous, System.getSecurityManager())
    }
}

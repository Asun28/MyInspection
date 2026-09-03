package nz.myinspection.core.report.html

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import java.lang.reflect.Modifier
import kotlin.test.assertTrue
import nz.myinspection.core.report.content.ReportContentPhoto

/** A1-A4: what may become embeddable evidence, how a refusal is shaped, and the ceiling the port is told. */
class ReportImageSourceTest {

    private val jpeg = byteArrayOf(-1, -40, -1, -32)
    private val photo = ReportContentPhoto(
        id = "photo-1",
        contentHash = "hash-1",
        source = "camera",
        reference = "1.2.1",
        capturedAt = 1_755_303_000_000L,
        privacy = false,
    )

    // ---- A1: what may exist at all ------------------------------------------------------------------

    @Test
    fun `the allowlist is the three raster formats a browser decodes without a plugin`() {
        for (type in listOf("image/jpeg", "image/png", "image/webp")) {
            assertTrue(EmbeddedImage.isSupportedMediaType(type), type)
            assertEquals(type, EmbeddedImage(type, jpeg).mediaType)
        }
        assertFalse(EmbeddedImage.isSupportedMediaType("image/gif"))
    }

    /**
     * The allowlist is a predicate and holds no collection, so there is nothing to obtain and cast to
     * `MutableSet` in order to admit a type this class refuses. `setOf` would return a JVM
     * `LinkedHashSet` and make exactly that possible; this asserts the shape rather than trusting it.
     */
    @Test
    fun `no collection of allowed types is reachable, so none can be mutated into admitting SVG`() {
        val exposed = (EmbeddedImage.Companion::class.java.methods.map { it.returnType } +
            EmbeddedImage::class.java.declaredFields.filter { Modifier.isStatic(it.modifiers) }
                .map { it.type })
        assertEquals(emptyList(), exposed.filter { Collection::class.java.isAssignableFrom(it) })
    }

    /**
     * SVG is excluded by name and with the reason attached, so that its absence reads as a decision
     * rather than as an oversight: it is a document that can carry script, not a picture.
     */
    @Test
    fun `svg is excluded because it is a scriptable document, not a picture`() {
        assertFalse(EmbeddedImage.isSupportedMediaType("image/svg+xml"))
        assertFailsWith<RejectedEvidenceException> { EmbeddedImage("image/svg+xml", jpeg) }
    }

    @Test
    fun `a media type outside the allowlist and a picture with no bytes are both refused`() {
        assertFailsWith<RejectedEvidenceException> { EmbeddedImage("application/pdf", jpeg) }
        assertFailsWith<RejectedEvidenceException> { EmbeddedImage("image/gif", jpeg) }
        assertFailsWith<RejectedEvidenceException> { EmbeddedImage("image/jpeg", ByteArray(0)) }
    }

    // ---- A2: the shape of a refusal -----------------------------------------------------------------

    /**
     * The refusal happens inside the port, while `read` is running, so it reaches a caller as a thrown
     * exception. Its own type is what lets that caller catch exactly this and nothing else: a blanket
     * catch around a port call would swallow a real defect in the port and yield a silently incomplete
     * report. It stays an `IllegalArgumentException` so an unaware caller still sees an argument error.
     */
    @Test
    fun `a refusal is its own type and still an argument error`() {
        // Typed as Throwable on purpose. Declared as the exception class, breaking the subtype relation
        // makes the check below a compile error rather than a failing test, and a mutation killed by the
        // compiler proves nothing about what the suite actually verifies (L282).
        val refusal: Throwable = assertFailsWith<RejectedEvidenceException> { EmbeddedImage("image/gif", jpeg) }
        assertTrue(refusal is IllegalArgumentException)
        assertEquals(
            "image/gif",
            runCatching { EmbeddedImage("image/gif", jpeg) }
                .exceptionOrNull()!!.message!!.substringAfterLast(' '),
        )
    }

    /**
     * A document budget below the per-image budget is the caller wiring the renderer wrongly, not a
     * picture being turned away, so it is deliberately NOT the refusal type - the renderer catches the
     * refusal, and catching this too would hide a misconfiguration behind silently missing photographs.
     */
    @Test
    fun `a misconfigured bound is an ordinary argument error, never a refusal`() {
        val error: Throwable = assertFailsWith<IllegalArgumentException> {
            HtmlImageBounds(maxImageBytes = 8, maxTotalImageBytes = 4)
        }
        assertFalse(error is RejectedEvidenceException)
        assertFailsWith<IllegalArgumentException> { HtmlImageBounds(maxImageBytes = 0) }
    }

    @Test
    fun `equal bounds are legal, since one picture may fill the whole document budget`() {
        assertEquals(4, HtmlImageBounds(maxImageBytes = 4, maxTotalImageBytes = 4).maxTotalImageBytes)
    }

    // ---- A3: the ceiling travels with the request ----------------------------------------------------

    /**
     * The ceiling is an argument, not something the implementation is trusted to remember, so a port can
     * decline an oversized file before reading it. A bound checked only after the bytes arrive cannot
     * prevent the allocation it exists to prevent.
     */
    @Test
    fun `the port is handed the ceiling it must respect`() {
        var seen = -1
        val source = ReportImageSource { _, maxBytes ->
            seen = maxBytes
            if (maxBytes < jpeg.size) null else EmbeddedImage("image/jpeg", jpeg)
        }
        assertEquals(null, source.read(photo, 3))
        assertEquals(3, seen)
        assertEquals("image/jpeg", source.read(photo, 4096)!!.mediaType)
        assertEquals(4096, seen)
    }
}
/*
 * R4 receipt. 10 single-point mutations, each applied alone to ReportImageSource.kt at SHA-256
 * 3cb5d96a74f357f2f6848485f610640c6af1afe4eb4500b2d413a76af26de9fc and restored to that same hash before
 * the next. 10 killed, 0 survived, 0 compile-kills, 0 no-runs.
 *
 * Every kill is a failing test. The harness runs the unmutated suite first as a positive control and
 * records a kill only when Gradle reports that tests ran and failed - a compile error, a failing test and
 * a shell that cannot start the wrapper all exit 1, so a kill has to be positively attributed and never
 * inferred from an exit code (L282).
 *
 * allowlist  P1 accepts any media type; P2 accepts a picture with no bytes; P5 admits SVG; P6 drops webp;
 *            P10 publishes a set of allowed types again - `setOf` returns a JVM LinkedHashSet, so a
 *            caller could cast it to MutableSet and add the very type this class refuses.
 * refusal    P3 stops the refusal being an argument error; P4 throws it as a plain argument error so a
 *            narrow catch would miss it.
 * bounds     P7 accepts a non-positive per-image bound; P8 accepts a document bound below the per-image
 *            bound; P9 refuses equal bounds, which would stop one picture ever filling the document.
 *
 * P3 was a COMPILE-KILL on the first pass, and that counted for nothing: capturing the exception as its
 * declared class made `error is RejectedEvidenceException` an incompatible-types error the moment the
 * subtype relation broke, so the compiler killed the mutant before any assertion ran. Both captures are
 * now typed `Throwable`, the check compiles under every mutation, and P3 fails on behaviour instead.
 */

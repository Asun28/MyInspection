package nz.myinspection.core.report.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import nz.myinspection.core.report.Audience

/** A1-A3: where one exported report lives, and whether a stored relative path is one of ours. */
class PdfArtifactPathsTest {
    /** A1. Written out rather than rebuilt from the object under test, which would compare it with itself. */
    @Test
    fun `a report path names the property, the inspection, the audience and the quality`() {
        assertEquals(
            "reports/prop-0001/insp-0001-landlord-medium.pdf",
            PdfArtifactPaths.reportRelPath("prop-0001", "insp-0001", Audience.LANDLORD, PdfExportQuality.MEDIUM),
        )
        assertEquals(
            "reports/prop-0001/insp-0001-tenant-extra_high.pdf",
            PdfArtifactPaths.reportRelPath("prop-0001", "insp-0001", Audience.TENANT, PdfExportQuality.EXTRA_HIGH),
        )
    }

    /**
     * A1. Why both labels are in the name: a verified High archive copy must survive someone re-exporting
     * the same inspection at Medium to share, and the landlord copy must survive the tenant copy. Eight
     * artifacts of one inspection, eight names.
     */
    @Test
    fun `no two audience and quality combinations of one inspection share a path`() {
        val paths = Audience.entries.flatMap { audience ->
            PdfExportQuality.entries.map { PdfArtifactPaths.reportRelPath("prop-0001", "insp-0001", audience, it) }
        }
        assertEquals(8, paths.size, "the enums no longer declare two audiences and four qualities")
        assertEquals(paths.size, paths.toSet().size, "an audience or quality label is missing from the name")
    }

    /**
     * A2. Derivation and recognition are one pattern, and its tokens are built from these same enums - so
     * this loop, not a fixed list of eight names, is what fails if a new audience or quality reaches only
     * one of the two halves. The two rejected tokens are the other side of that: nothing the enums do not
     * declare is recognised.
     */
    @Test
    fun `recognition covers every audience and quality the enums declare, and nothing else`() {
        Audience.entries.forEach { audience ->
            PdfExportQuality.entries.forEach { quality ->
                val derived = PdfArtifactPaths.reportRelPath("prop-0001", "insp-0001", audience, quality)
                assertTrue(PdfArtifactPaths.isReportRelPathShape(derived), "derived path not recognised: $derived")
            }
        }
        assertFalse(PdfArtifactPaths.isReportRelPathShape("reports/prop-0001/insp-0001-agent-medium.pdf"))
        assertFalse(PdfArtifactPaths.isReportRelPathShape("reports/prop-0001/insp-0001-landlord-ultra.pdf"))
    }

    /**
     * A3, derivation side. The same guard `MediaPaths` puts on photo segments: these are UUIDv7 in
     * practice, so this is about a corrupt or hostile value smuggling a separator or a traversal segment
     * into a path the caller is about to write a file to.
     */
    @Test
    fun `a segment that could escape or reshape the path is refused`() {
        listOf("", " ", ".", "..", "a/b", "a\\b").forEach { bad ->
            assertFailsWith<IllegalArgumentException>("property segment <$bad> was accepted") {
                PdfArtifactPaths.reportRelPath(bad, "insp-0001", Audience.LANDLORD, PdfExportQuality.LOW)
            }
            assertFailsWith<IllegalArgumentException>("inspection segment <$bad> was accepted") {
                PdfArtifactPaths.reportRelPath("prop-0001", bad, Audience.LANDLORD, PdfExportQuality.LOW)
            }
        }
    }

    /**
     * A3, recognition side. A stored path reaches this predicate from a database column that constrains
     * nothing to have been produced here, so publishing over it, deleting it or counting it as a verified
     * export has to be gated on the shape first - the same reason `MediaPaths.isPhotoRelPathShape` exists.
     */
    @Test
    fun `a foreign, malformed or mis-extensioned path is not recognised`() {
        listOf(
            "photos/prop-0001/insp-0001/photo-1.jpg",
            "exports/prop-0001/insp-0001-landlord-medium.pdf",
            "../reports/prop-0001/insp-0001-landlord-medium.pdf",
            "reports/prop-0001/insp-0001-landlord-medium.html",
            "reports/prop-0001/insp-0001-landlord-medium.pdf.bak",
            "reports/prop-0001/insp-0001-landlord.pdf",
            "reports/prop-0001/insp-0001-medium.pdf",
            "reports/prop-0001/sub/insp-0001-landlord-medium.pdf",
            "reports//insp-0001-landlord-medium.pdf",
            "reports/./insp-0001-landlord-medium.pdf",
            "reports/../insp-0001-landlord-medium.pdf",
            // The property segment is regular text, but on Windows this separator reaches a file API as a
            // directory boundary, so it is refused for the same reason a forward slash is.
            "reports/prop-0001\\sub/insp-0001-landlord-medium.pdf",
            // The inspection half of the name gets the same two guards as the property segment; without
            // them a traversal or a blank segment is a shape the pattern alone happily matches.
            "reports/prop-0001/..-landlord-medium.pdf",
            "reports/prop-0001/ -landlord-medium.pdf",
            // A trailing newline, built from its code point because an escape here is invisible when the
            // file is read. `$` alone would accept this - it matches before a final line terminator -
            // and matchEntire is what does not, because the terminator is left unconsumed.
            "reports/prop-0001/insp-0001-landlord-medium.pdf" + 10.toChar(),
        ).forEach { assertFalse(PdfArtifactPaths.isReportRelPathShape(it), "foreign path recognised: $it") }
    }
}

/*
 * R4 mutation receipt. 19 single-point mutations of PdfArtifactPaths.kt, each applied alone to the file at
 * SHA-256 7226b3c4bd07b879efb550f8876cc98997cab1de89384e7757fd6c2b11687576 and restored to that same hash
 * afterwards. A kill is `cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks
 * --no-build-cache :core:test --tests "nz.myinspection.core.report.*"` exiting 1. Every mutant was also run
 * alone through `:core:compileTestKotlin` and exited 0, so no kill here is a compile error wearing a test
 * failure's exit code. 19 killed, 0 survived.
 *
 * A1 | M1 drop the quality token from the name; M2 swap the audience and quality tokens; M3 drop the
 *    `reports/` root; M4 drop the property segment. Killed by the two written-out paths and by eight
 *    combinations having to be eight names.
 * A1/A2 | M5 makes Audience.storedValue the raw enum name, which changes derivation and pattern together
 *    so the two stay consistent. The recognition loop still passes and the written-out path is what fails -
 *    which is why the expected names here are literals rather than rebuilt from the object under test.
 * A2 | M6 builds the pattern's audience token from `it.name`; M7 admits only `entries.take(1)`. Both are
 *    killed by the loop over Audience.entries x PdfExportQuality.entries - the same loop that fails if a
 *    future enum value ever reaches one half of the pattern and not the other. M8 and M9 hardcode the two
 *    tokens loose (`[^-]+`, `[a-z_]+`), decoupling them from the enums; killed by the `agent` and `ultra`
 *    rejections.
 * A3 | M10 and M11 drop a requireSafeSegment call; M12 to M16 drop one clause of isSafeSegment (blank,
 *    forward slash, backslash, "." and ".."); M17 and M18 drop the recognition-side re-check of the
 *    property and the inspection segment; M19 replaces matchEntire with find.
 *    M18 is why `reports/prop-0001/..-landlord-medium.pdf` and the blank-segment case are in the negative
 *    list: the pattern alone matches both, and without the group-2 re-check they are recognised. M19 is
 *    killed by the trailing-newline case alone, since the anchored pattern still refuses the others.
 * Deliberately not run: widening either capture group to `(.+)` or `([^/]*)`. isSafeSegment rejects
 *    everything the wider group would newly capture, so those are equivalent mutants rather than survivors.
 *    The regex and the segment guard are two independent gates on purpose, the pairing MediaPaths uses.
 */

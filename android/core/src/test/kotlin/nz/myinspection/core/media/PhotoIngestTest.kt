package nz.myinspection.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PhotoIngestTest {
    @Test
    fun `no existing active asset for the hash produces WriteNewAsset at the single derived path`() {
        val plan = PhotoIngest.plan(
            propertyId = "prop-1", inspectionId = "insp-1", photoId = "photo-1",
            contentHash = "hash-a", existingActiveRelPaths = emptyList(),
        )
        val writeNew = assertIs<PhotoIngestPlan.WriteNewAsset>(plan)
        assertEquals(MediaPaths.photoRelPath("prop-1", "insp-1", "photo-1"), writeNew.relPath, "must go through the single path derivation point, not a hand-built string")
        assertEquals("hash-a", writeNew.contentHash)
    }

    @Test
    fun `an existing active asset for the hash produces ReuseExistingAsset at that asset's path, never a new file`() {
        val plan = PhotoIngest.plan(
            propertyId = "prop-1", inspectionId = "insp-1", photoId = "photo-1",
            contentHash = "hash-a", existingActiveRelPaths = listOf("photos/prop-1/insp-0/existing.jpg"),
        )
        val reuse = assertIs<PhotoIngestPlan.ReuseExistingAsset>(plan)
        assertEquals("photos/prop-1/insp-0/existing.jpg", reuse.relPath, "must reuse the caller-supplied physical asset, not derive a fresh one")
        assertEquals("hash-a", reuse.contentHash)
    }

    @Test
    fun `with multiple active paths for the hash, the caller's first entry wins deterministically`() {
        // selectActiveAssetsByPropertyAndContentHash already returns them ORDER BY rel_path ASC; PhotoIngest must
        // not re-sort or pick differently — it just takes the caller's first entry.
        val plan = PhotoIngest.plan(
            propertyId = "prop-1", inspectionId = "insp-1", photoId = "photo-1",
            contentHash = "hash-a",
            existingActiveRelPaths = listOf("photos/prop-1/insp-1/a.jpg", "photos/prop-1/insp-1/b.jpg"),
        )
        assertEquals("photos/prop-1/insp-1/a.jpg", assertIs<PhotoIngestPlan.ReuseExistingAsset>(plan).relPath)
    }

    @Test
    fun `a candidate owned by another property is skipped and a new property-local path is written`() {
        val plan = PhotoIngest.plan(
            propertyId = "prop-b", inspectionId = "insp-b", photoId = "photo-b",
            contentHash = "shared-hash",
            existingActiveRelPaths = listOf("photos/prop-a/insp-a/existing.jpg"),
        )

        val writeNew = assertIs<PhotoIngestPlan.WriteNewAsset>(plan)
        assertEquals("photos/prop-b/insp-b/photo-b.jpg", writeNew.relPath)
    }

    @Test
    fun `candidate order cannot let a foreign-property path displace a local reusable asset`() {
        val candidates = listOf(
            "photos/prop-a/insp-a/foreign.jpg",
            "photos/prop-b/insp-old/local.jpg",
        )

        for (orderedCandidates in listOf(candidates, candidates.reversed())) {
            val plan = PhotoIngest.plan(
                propertyId = "prop-b", inspectionId = "insp-b", photoId = "photo-b",
                contentHash = "shared-hash",
                existingActiveRelPaths = orderedCandidates,
            )
            assertEquals(
                "photos/prop-b/insp-old/local.jpg",
                assertIs<PhotoIngestPlan.ReuseExistingAsset>(plan).relPath,
            )
        }
    }

    @Test
    fun `a shape-invalid candidate (corrupted or cross-namespace rel_path) is skipped, never reused`() {
        // A photo row's rel_path has no schema constraint forcing this shape — a corrupted/cross-table
        // row (e.g. an audio path) must not be trusted as a reusable physical asset just because it came
        // back from the active-hash lookup.
        val planIgnoringBadFirst = PhotoIngest.plan(
            propertyId = "prop-1", inspectionId = "insp-1", photoId = "photo-1",
            contentHash = "hash-a",
            existingActiveRelPaths = listOf("audio/x/y/z.m4a", "photos/prop-1/insp-1/valid.jpg"),
        )
        assertEquals(
            "photos/prop-1/insp-1/valid.jpg",
            assertIs<PhotoIngestPlan.ReuseExistingAsset>(planIgnoringBadFirst).relPath,
            "the shape-invalid first entry must be skipped in favor of the next shape-valid one",
        )

        val planAllBad = PhotoIngest.plan(
            propertyId = "prop-1", inspectionId = "insp-1", photoId = "photo-1",
            contentHash = "hash-a", existingActiveRelPaths = listOf("audio/x/y/z.m4a"),
        )
        val writeNew = assertIs<PhotoIngestPlan.WriteNewAsset>(planAllBad)
        assertEquals(MediaPaths.photoRelPath("prop-1", "insp-1", "photo-1"), writeNew.relPath, "no valid candidate must fall through to WriteNewAsset, not reuse the corrupted entry")
    }

    @Test
    fun `verifyReuseExists keeps ReuseExistingAsset when the asset actually exists`() {
        val plan = PhotoIngestPlan.ReuseExistingAsset(relPath = "photos/prop-1/insp-0/existing.jpg", contentHash = "hash-a")
        val verified = PhotoIngest.verifyReuseExists(plan, "prop-1", "insp-1", "photo-1") { true }
        assertEquals(plan, verified, "an existing reuse target must be left unchanged")
    }

    @Test
    fun `verifyReuseExists falls back to WriteNewAsset when the DB row lied and the file is gone`() {
        val plan = PhotoIngestPlan.ReuseExistingAsset(relPath = "photos/prop-1/insp-0/gone.jpg", contentHash = "hash-a")
        val verified = PhotoIngest.verifyReuseExists(plan, "prop-1", "insp-1", "photo-1") { false }
        val writeNew = assertIs<PhotoIngestPlan.WriteNewAsset>(verified)
        assertEquals(MediaPaths.photoRelPath("prop-1", "insp-1", "photo-1"), writeNew.relPath, "fallback path must go through the single derivation point")
        assertEquals("hash-a", writeNew.contentHash, "the content hash must survive the fallback — it's still the same content, just needing a fresh file")
    }

    @Test
    fun `verifyReuseExists never probes existence for a plan that is already WriteNewAsset`() {
        val plan = PhotoIngestPlan.WriteNewAsset(relPath = MediaPaths.photoRelPath("prop-1", "insp-1", "photo-1"), contentHash = "hash-a")
        var probed = false
        val verified = PhotoIngest.verifyReuseExists(plan, "prop-1", "insp-1", "photo-1") { probed = true; true }
        assertEquals(plan, verified)
        assertEquals(false, probed, "no reuse candidate to verify — the existence check must not be invoked at all")
    }
}

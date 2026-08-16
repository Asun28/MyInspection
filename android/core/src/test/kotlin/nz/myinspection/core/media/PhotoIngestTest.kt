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
        // selectActiveAssetsByContentHash already returns them ORDER BY rel_path ASC; PhotoIngest must
        // not re-sort or pick differently — it just takes the caller's first entry.
        val plan = PhotoIngest.plan(
            propertyId = "prop-1", inspectionId = "insp-1", photoId = "photo-1",
            contentHash = "hash-a", existingActiveRelPaths = listOf("photos/a.jpg", "photos/b.jpg"),
        )
        assertEquals("photos/a.jpg", assertIs<PhotoIngestPlan.ReuseExistingAsset>(plan).relPath)
    }
}

package nz.myinspection.app.export.pdf

import java.io.File
import nz.myinspection.core.media.ContentHash
import org.json.JSONObject

/**
 * Narrow Android reader for the approved manifest and the public route to an AuthorizedFixture: it maps the JSON to
 * typed rows and hands them, with the digest of those same bytes, to the pure-JVM preflight. The fixture directory
 * holds the manifest beside a `photos/` folder, mirroring the controlled local collection; nothing outside those
 * two names is ever opened.
 */
object AndroidFixtureManifestReader {
    /** The digest of the bytes read here is refused before parsing, so org.json only ever sees the approved bytes. */
    fun preflight(fixtureDir: File): AuthorizedFixture {
        val bytes = File(fixtureDir, PdfFixtureManifest.APPROVED_MANIFEST_FILENAME).readBytes()
        val digest = ContentHash.sha256Hex(bytes)
        PdfFixtureManifest.requireApprovedDigest(digest)
        return PdfFixtureManifest.preflight(digest, read(bytes), File(fixtureDir, "photos"))
    }

    fun read(bytes: ByteArray): FixtureManifest {
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        val counts = root.getJSONObject("category_counts")
        val photos = root.getJSONArray("photos")
        return FixtureManifest(
            fixtureId = root.getString("fixture_id"),
            authorization = root.getString("authorization"),
            photoCount = root.getInt("photo_count"),
            categoryCounts = counts.keys().asSequence().associateWith { counts.getInt(it) },
            rows = (0 until photos.length()).map { index ->
                val p = photos.getJSONObject(index)
                FixtureManifestRow(
                    photoId = p.getString("photo_id"), category = p.getString("category"), filename = p.getString("filename"),
                    bytes = p.getLong("bytes"), sha256 = p.getString("sha256"), width = p.getInt("width"),
                    height = p.getInt("height"), licenseName = p.getString("license_name"), contentCheck = p.getString("content_check"),
                )
            },
        )
    }
}

package nz.myinspection.app.export.pdf

import java.io.File
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.StandardOpenOption.READ
import java.nio.file.attribute.BasicFileAttributes
import java.util.Collections
import nz.myinspection.core.media.ContentHash

/** Refusal of the fixture input; every message starts with an ASCII `[FIXTURE-<CODE>]` sentinel. */
class FixtureRefusal(code: String, detail: String) : IllegalStateException("[FIXTURE-$code] $detail")

/** The manifest fields the preflight consumes; URLs, authors, descriptions and formats stay unread. */
data class FixtureManifestRow(
    val photoId: String,
    val category: String,
    val filename: String,
    val bytes: Long,
    val sha256: String,
    val width: Int,
    val height: Int,
    val licenseName: String,
    val contentCheck: String,
)

data class FixtureManifest(
    val fixtureId: String,
    val authorization: String,
    val photoCount: Int,
    val categoryCounts: Map<String, Int>,
    val rows: List<FixtureManifestRow>,
)

/**
 * One accepted row bound to the file whose bytes were hashed against it; [ordinal] is 1-based manifest order. The
 * constructor is private and there is no copy: the only factory, the internal [verify], checks the file first.
 */
class AuthorizedPhoto private constructor(val ordinal: Int, val row: FixtureManifestRow, val file: File) {
    companion object {
        internal fun verify(ordinal: Int, row: FixtureManifestRow, rootDir: File) =
            AuthorizedPhoto(ordinal, row, PdfFixtureManifest.verifiedFile(row, rootDir))
    }
}

/**
 * Verified fixture input. The constructor is private and its only caller is the internal preflight below, which
 * hashes every named file against its row; [photos] is a read-only copy. That preflight compares the manifest
 * digest it is handed: outside this module the only route to it is AndroidFixtureManifestReader.preflight, which
 * hashes the bytes it then parses, while tests inside the module hand it synthetic rows.
 */
class AuthorizedFixture private constructor(val manifestSha256: String, photos: List<AuthorizedPhoto>) {
    val photos: List<AuthorizedPhoto> = Collections.unmodifiableList(ArrayList(photos))

    companion object {
        internal fun preflight(manifestSha256: String, manifest: FixtureManifest, root: File): AuthorizedFixture {
            with(PdfFixtureManifest) {
                requireApprovedDigest(manifestSha256)
                refuseUnless(manifest.fixtureId == FIXTURE_ID, "FIXTURE-ID", "fixture_id is ${manifest.fixtureId}")
                refuseUnless(manifest.authorization.isNotBlank(), "AUTHORIZATION", "no authorization statement")
                refuseUnless(manifest.photoCount == PHOTO_COUNT && manifest.rows.size == PHOTO_COUNT, "PHOTO-COUNT",
                    "declared ${manifest.photoCount}, rows ${manifest.rows.size}, required $PHOTO_COUNT")
                refuseUnless(manifest.rows.distinctBy { it.photoId }.size == PHOTO_COUNT, "DISTINCT-ID", "duplicate photo_id")
                refuseUnless(manifest.rows.distinctBy { it.filename }.size == PHOTO_COUNT, "DISTINCT-FILENAME", "duplicate filename")
                refuseUnless(manifest.rows.distinctBy { it.sha256 }.size == PHOTO_COUNT, "DISTINCT-HASH", "duplicate sha256")
                val actual = manifest.rows.groupingBy { it.category }.eachCount()
                refuseUnless(manifest.categoryCounts == REQUIRED_CATEGORY_COUNTS && actual == REQUIRED_CATEGORY_COUNTS,
                    "CATEGORY-COUNTS", "declared ${manifest.categoryCounts}, rows $actual, required $REQUIRED_CATEGORY_COUNTS")
                val rootDir = root.canonicalFile
                val photos = manifest.rows.mapIndexed { index, row -> AuthorizedPhoto.verify(index + 1, row, rootDir) }
                return AuthorizedFixture(manifestSha256, photos)
            }
        }
    }
}

/**
 * Debug-only preflight of the authorized real80 manifest. It accepts only the approved manifest digest, exactly
 * 80 distinct rows in the approved category distribution, and only files that sit directly under the fixture
 * root with the recorded size and SHA-256. It never lists directories or reads anything the manifest does not name.
 */
object PdfFixtureManifest {
    const val FIXTURE_ID = "myinspection-real80"
    const val APPROVED_MANIFEST_FILENAME = "manifest-approved-20260917.json"
    const val APPROVED_MANIFEST_SHA256 = "8721160680e73a2ce3570666ac416e31515bbd16fefb2a106e180790884ccad5"
    internal val REQUIRED_CATEGORY_COUNTS: Map<String, Int> = Collections.unmodifiableMap(
        mapOf("room_panorama" to 20, "low_light" to 20, "high_texture" to 21, "nameplate" to 19),
    )
    internal const val PHOTO_COUNT = 80
    private const val CONTENT_CHECK_PASS = "agent_visual_review_pass"
    private val CC_BY = Regex("CC BY [0-9]\\.[0-9]( [a-z]{2})?")

    /** Takes the manifest digest as given; the public route, AndroidFixtureManifestReader.preflight, derives it. */
    internal fun preflight(manifestSha256: String, manifest: FixtureManifest, root: File): AuthorizedFixture =
        AuthorizedFixture.preflight(manifestSha256, manifest, root)

    internal fun requireApprovedDigest(manifestSha256: String) =
        refuseUnless(manifestSha256 == APPROVED_MANIFEST_SHA256, "MANIFEST-DIGEST", "not the approved manifest bytes")

    internal fun verifiedFile(row: FixtureManifestRow, rootDir: File): File {
        val id = row.photoId
        refuseUnless(row.width > 0 && row.height > 0 && row.bytes > 0, "DIMENSIONS", "$id has a non-positive size")
        refuseUnless(row.licenseName == "CC0" || row.licenseName == "Public domain" || CC_BY.matches(row.licenseName),
            "LICENSE", "$id license ${row.licenseName} is outside public domain / CC0 / CC BY")
        refuseUnless(row.contentCheck == CONTENT_CHECK_PASS, "CONTENT-CHECK", "$id content_check is ${row.contentCheck}")
        refuseUnless(isSafeSegment(row.filename), "FILENAME", "$id filename is not a plain file name")
        val file = File(rootDir, row.filename)
        // Read the entry's own attributes: a link or junction planted under the root is refused, never followed.
        val entry = try {
            Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        } catch (_: NoSuchFileException) {
            null
        }
        refuseUnless(entry != null, "FILE-MISSING", "$id file ${row.filename} is absent")
        refuseUnless(entry!!.isRegularFile, "FILENAME", "$id is not a regular file directly under the fixture root")
        // One handle opened with NOFOLLOW_LINKS: a link swapped in after the check is not followed, and whatever this
        // handle reads is what gets hashed against the row.
        val body = Files.newByteChannel(file.toPath(), READ, NOFOLLOW_LINKS).use { Channels.newInputStream(it).readBytes() }
        refuseUnless(body.size.toLong() == row.bytes && ContentHash.sha256Hex(body) == row.sha256, "FILE-BYTES",
            "$id bytes differ from the manifest (${body.size} bytes read)")
        return file
    }

    /**
     * No separators and no ':' (an NTFS alternate data stream must not stand in for the file). The empty name, `.`
     * and `..` need no clause of their own: they name directories, which the regular-file check above refuses.
     */
    private fun isSafeSegment(name: String): Boolean = name.none { it == '/' || it == '\\' || it == ':' }

    internal fun refuseUnless(condition: Boolean, code: String, detail: String) {
        if (!condition) throw FixtureRefusal(code, detail)
    }
}

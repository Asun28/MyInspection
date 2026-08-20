package nz.myinspection.core.media

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.attribute.BasicFileAttributes

/** The minimal DB fact the sidecar recovery path needs; the Android worker supplies it from `selectById(photoId)`. */
data class PendingPhotoReference(val relPath: String, val active: Boolean)

/**
 * Recovers only sidecars shaped as `photos/<property>/<inspection>/<photo>.jpg.pending`.
 *
 * A path is considered adopted only when that exact photo id has an active row pointing to that exact relative path.
 * This intentionally does not guess at cross-id aliases: those are a separately tracked schema-era repair.
 */
class PendingPhotoAssetCleanup private constructor(
    private val mediaRoot: File,
    private val findPhoto: (photoId: String) -> PendingPhotoReference?,
    private val deleter: OrphanFileDeleter,
    private val listChildren: (File) -> Array<File>?,
    private val readAttributes: (File) -> BasicFileAttributes?,
    private val openLease: (File) -> PendingPhotoLeaseHandle,
) {
    constructor(
        mediaRoot: File,
        findPhoto: (photoId: String) -> PendingPhotoReference?,
        deleter: OrphanFileDeleter,
    ) : this(
        mediaRoot,
        findPhoto,
        deleter,
        { directory -> directory.listFiles() },
        ::readNoFollowAttributes,
        { marker -> PendingPhotoLease.openExisting(marker) },
    )

    companion object {
        private const val PENDING_SUFFIX = ".jpg.pending"

        private fun readNoFollowAttributes(file: File): BasicFileAttributes? = try {
            Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        } catch (_: NoSuchFileException) {
            null
        }

        /** Test seam for proving a canonical-root rejection happens before any descendant is listed. */
        internal fun withListedChildren(
            mediaRoot: File,
            findPhoto: (photoId: String) -> PendingPhotoReference?,
            deleter: OrphanFileDeleter,
            listChildren: (File) -> Array<File>?,
            readAttributes: (File) -> BasicFileAttributes?,
        ): PendingPhotoAssetCleanup = PendingPhotoAssetCleanup(
            mediaRoot,
            findPhoto,
            deleter,
            listChildren,
            readAttributes,
            { marker -> PendingPhotoLease.openExisting(marker) },
        )

        /** Test seam for proving an unknown cleanup primary owns any lease-close failure. */
        internal fun withLeaseOpener(
            mediaRoot: File,
            findPhoto: (photoId: String) -> PendingPhotoReference?,
            deleter: OrphanFileDeleter,
            openLease: (File) -> PendingPhotoLeaseHandle,
        ): PendingPhotoAssetCleanup = PendingPhotoAssetCleanup(
            mediaRoot,
            findPhoto,
            deleter,
            { directory -> directory.listFiles() },
            ::readNoFollowAttributes,
            openLease,
        )
    }

    fun run(): CleanupResult {
        val deleted = mutableListOf<String>()
        val failed = mutableListOf<FailedDeletion>()
        val rejected = mutableListOf<String>()
        val readopted = mutableListOf<String>()

        for (candidate in pendingCandidates()) {
            if (!MediaPaths.isPhotoRelPathShape(candidate.relPath) || !isWithinMediaRoot(candidate.marker)) {
                rejected += candidate.markerRelPath
                continue
            }

            when (val result = recover(candidate)) {
                is RecoveryResult.Deleted -> deleted += result.relPath
                is RecoveryResult.Readopted -> readopted += result.relPath
                is RecoveryResult.Failed -> failed += FailedDeletion(candidate.relPath, result.cause)
            }
        }
        return CleanupResult(deleted, failed, rejected, readopted)
    }

    private fun recover(candidate: PendingCandidate): RecoveryResult {
        var lease: PendingPhotoLeaseHandle? = null
        var primary: Throwable? = null
        var result: RecoveryResult? = null
        try {
            lease = openLease(candidate.marker)
            val adopted = findPhoto(candidate.photoId)?.let { it.active && it.relPath == candidate.relPath } == true
            result = if (adopted) {
                RecoveryResult.Readopted(candidate.relPath)
            } else if (deleter.delete(candidate.relPath)) {
                RecoveryResult.Deleted(candidate.relPath)
            } else {
                RecoveryResult.Failed(null)
            }
        } catch (failure: Throwable) {
            primary = failure
            result = RecoveryResult.Failed(failure)
            if (failure !is IOException && failure !is SecurityException) throw failure
        } finally {
            val heldLease = lease
            if (heldLease != null) {
                try {
                    if (result !is RecoveryResult.Failed) {
                        if (!heldLease.closeAfter(PendingPhotoLeaseDisposition.RECORDED)) {
                            result = RecoveryResult.Failed(null)
                        }
                    } else {
                        heldLease.close()
                    }
                } catch (closeFailure: Throwable) {
                    val existing = primary
                    if (existing == null) {
                        if (isExpectedEnvironmentFailure(closeFailure)) {
                            result = RecoveryResult.Failed(closeFailure)
                        } else {
                            throw closeFailure
                        }
                    } else {
                        existing.addSuppressed(closeFailure)
                    }
                }
            }
        }

        return checkNotNull(result)
    }

    private fun isExpectedEnvironmentFailure(failure: Throwable): Boolean =
        failure is IOException || failure is SecurityException

    private fun pendingCandidates(): List<PendingCandidate> {
        val root = scanRoot()
        val photos = File(root, "photos")
        val photoAttributes = readAttributes(photos) ?: return emptyList()
        val photoDirectory = containedDirectory(photos, root, photoAttributes, "photos")
            ?: throw IOException("photo namespace is not an in-root directory: ${photos.path}")

        val candidates = mutableListOf<PendingCandidate>()
        for (property in containedDirectories(photoDirectory, root, "property")) {
            for (inspection in containedDirectories(property, root, "inspection")) {
                for (marker in containedMarkers(inspection, root)) {
                    val assetName = marker.name.removeSuffix(".pending")
                    val relPath = "photos/${property.name}/${inspection.name}/$assetName"
                    candidates += PendingCandidate(
                        marker = marker,
                        markerRelPath = "photos/${property.name}/${inspection.name}/${marker.name}",
                        relPath = relPath,
                        photoId = assetName.removeSuffix(".jpg"),
                    )
                }
            }
        }
        return candidates.sortedBy(PendingCandidate::markerRelPath)
    }

    private fun scanRoot(): File {
        val attributes = readAttributes(mediaRoot) ?: return mediaRoot.absoluteFile
        if (isUnsafeAlias(attributes)) {
            throw IllegalStateException("media root is an unsafe link: ${mediaRoot.path}")
        }
        if (!attributes.isDirectory) throw IOException("media root is not a directory: ${mediaRoot.path}")
        return mediaRoot.canonicalFile
    }

    private fun containedDirectories(directory: File, root: File, level: String): List<File> =
        childrenOf(directory).mapNotNull { candidate ->
            containedDirectory(candidate, root, level)
        }

    private fun containedMarkers(directory: File, root: File): List<File> =
        childrenOf(directory).mapNotNull { candidate ->
            if (!candidate.name.endsWith(PENDING_SUFFIX)) return@mapNotNull null
            val attributes = readAttributes(candidate) ?: return@mapNotNull null
            val canonical = candidate.canonicalFile
            if (isUnsafeAlias(attributes) || !isWithinMediaRoot(root, canonical)) {
                throw IllegalStateException("unsafe pending marker: ${candidate.path}")
            }
            candidate.takeIf { isPlainFile(attributes) }
        }

    private fun containedDirectory(candidate: File, root: File, level: String): File? {
        val attributes = readAttributes(candidate) ?: return null
        return containedDirectory(candidate, root, attributes, level)
    }

    private fun containedDirectory(
        candidate: File,
        root: File,
        attributes: BasicFileAttributes,
        level: String,
    ): File? {
        val canonical = candidate.canonicalFile
        if (isUnsafeAlias(attributes) || !isWithinMediaRoot(root, canonical)) {
            throw IllegalStateException("unsafe $level directory: ${candidate.path}")
        }
        return candidate.takeIf { isPlainDirectory(attributes) }
    }

    private fun childrenOf(directory: File): Array<File> = listChildren(directory)
        ?: throw IOException("could not list photo namespace: ${directory.path}")

    private fun isWithinMediaRoot(file: File): Boolean {
        val root = mediaRoot.canonicalFile
        val candidate = file.canonicalFile
        return isWithinMediaRoot(root, candidate)
    }

    private fun isWithinMediaRoot(root: File, candidate: File): Boolean {
        return candidate.path.startsWith(root.path + File.separator)
    }

    private fun isPlainDirectory(attributes: BasicFileAttributes): Boolean =
        attributes.isDirectory && !attributes.isSymbolicLink && !attributes.isOther

    private fun isPlainFile(attributes: BasicFileAttributes): Boolean =
        attributes.isRegularFile && !attributes.isSymbolicLink && !attributes.isOther

    private fun isUnsafeAlias(attributes: BasicFileAttributes): Boolean =
        attributes.isSymbolicLink || attributes.isOther

    private data class PendingCandidate(
        val marker: File,
        val markerRelPath: String,
        val relPath: String,
        val photoId: String,
    )

    private sealed interface RecoveryResult {
        data class Deleted(val relPath: String) : RecoveryResult
        data class Readopted(val relPath: String) : RecoveryResult
        data class Failed(val cause: Throwable?) : RecoveryResult
    }

}

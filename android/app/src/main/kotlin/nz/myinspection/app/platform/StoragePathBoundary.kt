package nz.myinspection.app.platform

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/** Path containment at checking time; this does not grant authority over later filesystem I/O. */
internal class StoragePathBoundary private constructor(
    private val rootPath: Path,
    private val deviceProtectedPath: Path,
) {
    val directory: File = rootPath.toFile()

    fun resolveChild(candidate: File): File? = try {
        val child = resolveStorageDirectory(candidate)
        if (child == rootPath || !child.startsWith(rootPath) || child.startsWith(deviceProtectedPath)) {
            null
        } else {
            child.toFile()
        }
    } catch (_: Exception) {
        null
    }

    companion object {
        fun create(candidate: File, appDataDir: File, deviceProtectedDataDir: File): StoragePathBoundary? = try {
            check(candidate.path.isNotBlank())
            check(appDataDir.path.isNotBlank())
            check(deviceProtectedDataDir.path.isNotBlank())
            val candidatePath = resolveStorageDirectory(candidate)
            val appPath = resolveStorageDirectory(appDataDir)
            val dpPath = resolveStorageDirectory(deviceProtectedDataDir)
            if (candidatePath == appPath || !candidatePath.startsWith(appPath) || candidatePath.startsWith(dpPath)) {
                null
            } else {
                StoragePathBoundary(candidatePath, dpPath)
            }
        } catch (_: Exception) {
            null
        }
    }
}

internal fun resolveStorageDirectory(
    directory: File,
    readAttributes: (Path) -> BasicFileAttributes = ::readStorageAttributes,
): Path {
    val absolute = directory.toPath().toAbsolutePath()
    val filesystemRoot = checkNotNull(absolute.root)
    readAttributes(filesystemRoot)
    var current = existingStorageDirectory(filesystemRoot, readAttributes)
    for (segment in absolute) {
        when (segment.toString()) {
            "." -> Unit
            ".." -> current = current.parent ?: current
            else -> {
                val next = current.resolve(segment)
                val present = try {
                    readAttributes(next)
                    true
                } catch (_: NoSuchFileException) {
                    false
                }
                // A missing segment can be followed by ".." and another existing alias.
                current = if (present) existingStorageDirectory(next, readAttributes) else next
            }
        }
    }
    return current
}

private fun existingStorageDirectory(
    path: Path,
    readAttributes: (Path) -> BasicFileAttributes,
): Path {
    // Target lookup failures must not be classified as a missing directory entry.
    val real = path.toRealPath()
    check(readAttributes(real).isDirectory)
    return real
}

private fun readStorageAttributes(path: Path): BasicFileAttributes =
    Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)

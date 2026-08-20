package nz.myinspection.core.media

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/** Validates every lexical parent without following aliases, then deletes only the final directory entry. */
object NoFollowLeafDeletion {
    fun isSafe(root: File, relPath: String): Boolean = validatedLeaf(root, relPath) != null

    fun delete(root: File, relPath: String): Boolean = delete(root, relPath, beforeDelete = {})

    internal fun delete(root: File, relPath: String, beforeDelete: (Path) -> Unit): Boolean {
        val first = validatedLeaf(root, relPath) ?: return false
        beforeDelete(first)
        val boundary = validatedLeaf(root, relPath) ?: return false
        if (boundary != first) return false
        return Files.deleteIfExists(boundary) || !Files.exists(boundary, NOFOLLOW_LINKS)
    }

    private fun validatedLeaf(root: File, relPath: String): Path? {
        val requestedRoot = root.toPath().toAbsolutePath().normalize()
        if (!isPlainDirectory(requestedRoot)) return null
        val lexicalRoot = requestedRoot.toRealPath()

        val relative = lexicalRoot.fileSystem.getPath(relPath)
        if (relative.isAbsolute) return null
        val leaf = lexicalRoot.resolve(relative).normalize()
        if (leaf == lexicalRoot || !leaf.startsWith(lexicalRoot)) return null

        var parent = lexicalRoot
        for (segment in lexicalRoot.relativize(checkNotNull(leaf.parent))) {
            parent = parent.resolve(segment)
            if (!isPlainDirectory(parent) || parent.toRealPath() != parent) return null
        }

        val leafAttributes = readAttributes(leaf) ?: return leaf
        return leaf.takeIf {
            leafAttributes.isRegularFile && !leafAttributes.isSymbolicLink && !leafAttributes.isOther
        }
    }

    private fun isPlainDirectory(path: Path): Boolean {
        val attributes = readAttributes(path) ?: return false
        return attributes.isDirectory && !attributes.isSymbolicLink && !attributes.isOther
    }

    private fun readAttributes(path: Path): BasicFileAttributes? = try {
        Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
    } catch (_: NoSuchFileException) {
        null
    }
}

package nz.myinspection.core.media

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/** Validates every lexical parent without following aliases, then deletes only the final directory entry. */
object NoFollowLeafDeletion {
    fun isSafe(root: File, relPath: String): Boolean = validatedLeaf(root, relPath) !is LeafState.Unsafe

    fun delete(root: File, relPath: String): Boolean = delete(root, relPath, beforeDelete = {})

    internal fun delete(root: File, relPath: String, beforeDelete: (Path) -> Unit): Boolean {
        val first = validatedLeaf(root, relPath)
        if (first is LeafState.Unsafe) return false
        if (first is LeafState.Absent) return true
        first as LeafState.Present
        beforeDelete(first.path)
        return when (val boundaryState = validatedLeaf(root, relPath)) {
            is LeafState.Absent -> true
            is LeafState.Unsafe -> false
            is LeafState.Present -> {
                val boundary = boundaryState.path
                if (boundary != first.path) return false
                Files.deleteIfExists(boundary) || !Files.exists(boundary, NOFOLLOW_LINKS)
            }
        }
    }

    private fun validatedLeaf(root: File, relPath: String): LeafState {
        val requestedRoot = root.toPath().toAbsolutePath().normalize()
        val relative = requestedRoot.fileSystem.getPath(relPath)
        if (relative.isAbsolute) return LeafState.Unsafe
        val requestedLeaf = requestedRoot.resolve(relative).normalize()
        if (requestedLeaf == requestedRoot || !requestedLeaf.startsWith(requestedRoot)) return LeafState.Unsafe

        val requestedRootAttributes = readAttributes(requestedRoot)
            ?: return LeafState.Absent(requestedLeaf)
        if (!isPlainDirectory(requestedRootAttributes)) return LeafState.Unsafe
        val lexicalRoot = requestedRoot.toRealPath()

        val leaf = lexicalRoot.resolve(relative).normalize()
        if (leaf == lexicalRoot || !leaf.startsWith(lexicalRoot)) return LeafState.Unsafe

        var parent = lexicalRoot
        for (segment in lexicalRoot.relativize(checkNotNull(leaf.parent))) {
            parent = parent.resolve(segment)
            val attributes = readAttributes(parent) ?: return LeafState.Absent(leaf)
            if (!isPlainDirectory(attributes) || parent.toRealPath() != parent) return LeafState.Unsafe
        }

        val leafAttributes = readAttributes(leaf) ?: return LeafState.Absent(leaf)
        return if (leafAttributes.isRegularFile && !leafAttributes.isSymbolicLink && !leafAttributes.isOther) {
            LeafState.Present(leaf)
        } else {
            LeafState.Unsafe
        }
    }

    private fun isPlainDirectory(attributes: BasicFileAttributes): Boolean {
        return attributes.isDirectory && !attributes.isSymbolicLink && !attributes.isOther
    }

    private fun readAttributes(path: Path): BasicFileAttributes? = try {
        Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
    } catch (_: NoSuchFileException) {
        null
    }

    private sealed interface LeafState {
        data class Present(val path: Path) : LeafState
        data class Absent(val path: Path) : LeafState
        data object Unsafe : LeafState
    }
}

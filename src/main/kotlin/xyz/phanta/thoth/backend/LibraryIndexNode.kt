package xyz.phanta.thoth.backend

import se.vidstige.jadb.JadbDevice
import se.vidstige.jadb.RemoteFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

abstract class LibraryIndexNode(val path: GeneralPath, val modifyTime: Long) {

    companion object {

        fun walkLocal(path: Path, root: GeneralPath): LibraryIndexNode = if (Files.isDirectory(path)) {
            LibraryIndexDirNode(GeneralPath(path).relativize(root), Files.getLastModifiedTime(path).toMillis(),
                Files.walk(path, 1)
                    .filter { it != path }
                    .collect(Collectors.toMap({ it.fileName.toString() }) { walkLocal(it, root) })
            )
        } else {
            LibraryIndexFileNode(GeneralPath(path).relativize(root), Files.getLastModifiedTime(path).toMillis())
        }

        fun walkRemote(device: JadbDevice, path: RemoteFile, root: GeneralPath, parent: GeneralPath): LibraryIndexNode {
            val childPath = parent.resolve(path.path)
            return if (path.isDirectory) {
                LibraryIndexDirNode(childPath, path.lastModified, device.list(root.resolve(childPath).toString())
                    .filter { isValidPath(it.path) }
                    .associate { it.path to walkRemote(device, it, root, childPath) })
            } else {
                LibraryIndexFileNode(childPath, path.lastModified)
            }
        }

    }

    abstract fun calculateDiff(index: LibraryIndexNode, parent: DiffEntry?): DiffEntry?

    abstract fun buildFullDiff(delta: DiffDelta, res: DiffResolution, parent: DiffEntry?): DiffEntry

}

class LibraryIndexFileNode(path: GeneralPath, modifyTime: Long) : LibraryIndexNode(path, modifyTime) {

    override fun calculateDiff(index: LibraryIndexNode, parent: DiffEntry?): DiffEntry? {
//        val remoteTime = (index as LibraryIndexFileNode).modifyTime
//        return if (remoteTime != modifyTime) {
//            FileDiffEntry(path, ModifyTimeMismatch(modifyTime, remoteTime), parent).also {
//                it.resolution.value =
//                        if (remoteTime > modifyTime) DiffResolution.ACCEPT_REMOTE else DiffResolution.ACCEPT_LOCAL
//            }
//        } else {
//            null
//        }
        return null
    }

    override fun buildFullDiff(delta: DiffDelta, res: DiffResolution, parent: DiffEntry?): DiffEntry =
        FileDiffEntry(path, delta, parent).also { it.resolution.value = res }

    override fun toString(): String = path.fileName

}

class LibraryIndexDirNode(path: GeneralPath, modifyTime: Long, private val children: Map<String, LibraryIndexNode>) :
    LibraryIndexNode(path, modifyTime) {

    override fun calculateDiff(index: LibraryIndexNode, parent: DiffEntry?): DiffEntry? {
        val dirIndex = index as LibraryIndexDirNode
        val childDiffs = mutableListOf<DiffEntry>()
        val diff = DirDiffEntry(path, Heterogeneous, childDiffs)
        children.forEach {
            val remote = dirIndex.children[it.key]
            if (remote != null) {
                it.value.calculateDiff(remote, diff)?.let { childDiff -> childDiffs += childDiff }
            } else {
                childDiffs += it.value.buildFullDiff(RemoteNonexistent, DiffResolution.ACCEPT_LOCAL, diff)
            }
        }
        dirIndex.children.forEach {
            if (children[it.key] == null) {
                childDiffs += it.value.buildFullDiff(LocalNonexistent, DiffResolution.ACCEPT_REMOTE, diff)
            }
        }
        childDiffs.sortBy { it.path.fileName }
        return if (childDiffs.isEmpty()) null else diff
    }

    override fun buildFullDiff(delta: DiffDelta, res: DiffResolution, parent: DiffEntry?): DiffEntry {
        val childDiffs = mutableListOf<DiffEntry>()
        val diff = DirDiffEntry(path, delta, childDiffs)
        children.forEach { childDiffs += it.value.buildFullDiff(delta, res, diff) }
        return diff
    }

    override fun toString(): String =
        "${path.fileName} {\n${children.values.joinToString("\n").split("\n").joinToString("\n") { "  $it" }}\n}"

}

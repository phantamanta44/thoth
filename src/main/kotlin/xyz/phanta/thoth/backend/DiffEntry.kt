package xyz.phanta.thoth.backend

import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty

abstract class DiffEntry(val path: GeneralPath, val delta: DiffDelta) {

    val resolution: ObjectProperty<DiffResolution> = SimpleObjectProperty(DiffResolution.NONE)

    abstract fun visitFileNodes(visitor: (FileDiffEntry) -> Unit)

    abstract fun visitDirNodes(visitor: (DirDiffEntry) -> Unit)

}

class FileDiffEntry(path: GeneralPath, delta: DiffDelta, private val parent: DiffEntry?) : DiffEntry(path, delta) {

    init {
        if (parent != null) {
            resolution.addListener { _, _, value ->
                if (parent.resolution.value != value) parent.resolution.value = DiffResolution.HETEROGENEOUS
            }
        }
    }

    override fun visitFileNodes(visitor: (FileDiffEntry) -> Unit) = visitor(this)

    override fun visitDirNodes(visitor: (DirDiffEntry) -> Unit) {
        // NO-OP
    }

}

class DirDiffEntry(path: GeneralPath, delta: DiffDelta, val children: List<DiffEntry>) : DiffEntry(path, delta) {

    init {
        resolution.addListener { _, _, value ->
            if (value != DiffResolution.HETEROGENEOUS) children.forEach { it.resolution.value = value }
        }
    }

    override fun visitFileNodes(visitor: (FileDiffEntry) -> Unit) = children.forEach { it.visitFileNodes(visitor) }

    override fun visitDirNodes(visitor: (DirDiffEntry) -> Unit) {
        visitor(this)
        children.forEach { it.visitDirNodes(visitor) }
    }

}

enum class DiffResolution(val operatorStr: String) {

    ACCEPT_LOCAL("<-"), ACCEPT_REMOTE("->"), HETEROGENEOUS(".."), NONE("//")

}

sealed class DiffDelta

object LocalNonexistent : DiffDelta()

object RemoteNonexistent : DiffDelta()

class ModifyTimeMismatch(private val local: Long, private val remote: Long) : DiffDelta() {

    val remoteNewer: Boolean
        get() = remote > local

}

object Heterogeneous : DiffDelta()

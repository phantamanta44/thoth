package xyz.phanta.thoth.backend

import se.vidstige.jadb.RemoteFile
import java.nio.file.Path

private val PATH_REGEX = Regex("""^[^"*/:<>?\\|&%]*$""")

open class GeneralPath(val segments: List<String>) {

    val fileName: String
        get() = segments.lastOrNull() ?: "/"
    val isRoot: Boolean
        get() = segments.isEmpty()

    constructor(path: String) : this(path.split('/'))

    constructor(path: Path) : this(path.map { it.toString() })

    init {
        segments.forEach {
            if (!it.matches(PATH_REGEX)) {
                throw IllegalArgumentException("Bad path segment: $it")
            }
        }
    }

    fun parent(): GeneralPath = GeneralPath(segments.subList(0, segments.size - 1))

    fun resolve(segment: String): GeneralPath = GeneralPath(segments + segment)

    fun resolve(path: GeneralPath): GeneralPath = if (path is GeneralPathSized) {
        GeneralPathSized(segments + path.segments, path.size)
    } else {
        GeneralPath(segments + path.segments)
    }

    open fun relativize(root: GeneralPath): GeneralPath
            = GeneralPath(segments.subList(root.segments.size, segments.size))

    fun resolveAgainst(path: Path): Path = segments.fold(path) { current, segment -> current.resolve(segment) }

    fun withSize(size: Long): GeneralPathSized = GeneralPathSized(segments, size)

    open fun toRemote(): RemoteFile = RemoteFile(toString())

    override fun toString(): String = segments.joinToString("/")

}

class GeneralPathSized(segments: List<String>, val size: Long) : GeneralPath(segments) {

    constructor(path: String, size: Long) : this(path.split('/'), size)

    constructor(path: Path, size : Long) : this(path.map { it.toString() }, size)

    override fun relativize(root: GeneralPath): GeneralPath
            = GeneralPathSized(segments.subList(root.segments.size, segments.size), size)

    override fun toRemote(): RemoteFile = RemoteFileSized(this)

}

private class RemoteFileSized(path: GeneralPathSized) : RemoteFile(path.toString()) {

    private val providedSize: Long = path.size

    override fun getSize(): Int = providedSize.toInt()

}

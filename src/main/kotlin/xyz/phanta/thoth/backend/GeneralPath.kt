package xyz.phanta.thoth.backend

import java.nio.file.Path

private val PATH_REGEX = Regex("""^[^"*/<>?\\|&%]*$""")

open class GeneralPath(val segments: List<String>) {

    val fileName: String
        get() = segments.lastOrNull() ?: "/"
    val isRoot: Boolean
        get() = segments.isEmpty()

    constructor(path: String) : this(splitSegments(path))

    constructor(path: Path) : this(path.map { it.toString() })

    init {
        segments.forEachIndexed { index, segment ->
            if (!segment.matches(PATH_REGEX)) {
                throw IllegalArgumentException("Bad path segment at index $index: $segment")
            }
        }
    }

    fun parent(): GeneralPath = GeneralPath(segments.subList(0, segments.size - 1))

    fun resolve(segment: String): GeneralPath = GeneralPath(segments + segment)

    fun resolve(path: GeneralPath): GeneralPath = GeneralPath(segments + path.segments)

    fun resolve(path: GeneralPathSized): GeneralPathSized = GeneralPathSized(segments + path.segments, path.size)

    open fun relativize(root: GeneralPath): GeneralPath
            = GeneralPath(segments.subList(root.segments.size, segments.size))

    fun resolveAgainst(path: Path): Path = segments.fold(path) { current, segment -> current.resolve(segment) }

    fun withSize(size: Long): GeneralPathSized = GeneralPathSized(segments, size)

    override fun toString(): String = segments.joinToString("/")

}

class GeneralPathSized(segments: List<String>, val size: Long) : GeneralPath(segments) {

    constructor(path: String, size: Long) : this(splitSegments(path), size)

    constructor(path: Path, size : Long) : this(path.map { it.toString() }, size)

    override fun relativize(root: GeneralPath): GeneralPathSized
            = GeneralPathSized(segments.subList(root.segments.size, segments.size), size)

}

private fun splitSegments(path: String): List<String> = path.split('/', '\\')

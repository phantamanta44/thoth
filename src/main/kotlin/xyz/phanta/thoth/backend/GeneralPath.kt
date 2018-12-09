package xyz.phanta.thoth.backend

import java.nio.file.Path

private val PATH_REGEX = Regex("""^[^"*/:<>?\\|&%]*$""")

class GeneralPath(private val segments: List<String>) {

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

    fun resolve(path: GeneralPath): GeneralPath = GeneralPath(segments + path.segments)

    fun relativize(root: GeneralPath): GeneralPath = GeneralPath(segments.subList(root.segments.size, segments.size))

    fun resolveAgainst(path: Path): Path = segments.fold(path) { current, segment -> current.resolve(segment) }

    override fun toString(): String = segments.joinToString("/")

}

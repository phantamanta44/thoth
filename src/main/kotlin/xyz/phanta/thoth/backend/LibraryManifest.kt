package xyz.phanta.thoth.backend

import com.moandjiezana.toml.Toml
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class LibraryManifest private constructor(mf: Toml) {

    companion object {

        fun findManifest(arg: String?): LibraryManifest {
            // first, try command line arg
            if (arg != null) {
                return LibraryManifest(Toml().read(File(arg)))
            }

            // next, try working dir
            return LibraryManifest(Toml().read(File("manifest.toml")))
        }

    }

    val local: Path
    val remote: GeneralPath

    init {
        mf.getString("local").let {
            if (it == null) throw NoSuchElementException("No local dir specified!")
            local = Paths.get(it)
        }
        mf.getString("remote").let {
            if (it == null) throw NoSuchElementException("No remote dir specified!")
            remote = GeneralPath(it)
        }
    }

}

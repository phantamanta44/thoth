package xyz.phanta.thoth.backend

import se.vidstige.jadb.JadbDevice
import java.nio.file.Files
import kotlin.system.measureTimeMillis

class SyncEngine(device: JadbDevice, private val manifest: LibraryManifest) {

    private val operator: AdbOperator = AdbOperator(device)

    private lateinit var index: LibraryIndexNode
    private var diffTree: DiffEntry? = null

    private fun scanRemote() {
        index = LibraryIndexDirNode(manifest.remote, -1, operator.list(manifest.remote)
            .filter { isValidPath(it.path) }
            .associate {
                it.path to LibraryIndexNode.walkRemote(operator, it, manifest.remote, GeneralPath(listOf()))
            })
    }

    fun buildDiffTree(localIndex: LibraryIndexNode): DiffEntry? {
        val scanTime = measureTimeMillis {
            println("Scanning device ${operator.serial}...")
            scanRemote()
            println("Building diff tree...")
            diffTree = localIndex.calculateDiff(index, null)
        }
        println("Diff'd in $scanTime ms")
        return diffTree
    }

    fun sync() {
        val syncTime = measureTimeMillis {
            diffTree!!.run {
                println("Synchronizing files...")
                visitFileNodes {
                    val local = it.path.resolveAgainst(manifest.local)
                    val remote = manifest.remote.resolve(it.path)
                    print("- $local ${it.resolution.value.operatorStr} $remote :: ")
                    when (it.resolution.value) {
                        DiffResolution.ACCEPT_LOCAL -> {
                            if (Files.exists(local)) {
                                println("Pushing local to remote")
                                operator.push(it.path.resolveAgainst(manifest.local),
                                    manifest.remote.resolve(it.path).toRemote())
                            } else {
                                println("Destroying remote")
                                operator.rmFile(manifest.remote.resolve(it.path))
                            }
                        }
                        DiffResolution.ACCEPT_REMOTE -> {
                            if (operator.fileExists(remote)) {
                                println("Pulling remote to local")
                                val localPath = it.path.resolveAgainst(manifest.local)
                                Files.createDirectories(localPath.parent)
                                operator.pull(manifest.remote.resolve(it.path).toRemote(), localPath)
                            } else {
                                println("Destroying local")
                                Files.deleteIfExists(it.path.resolveAgainst(manifest.local))
                            }
                        }
                        else -> {
                            println("No operation")
                        }
                    }
                }
                println("Synchronizing dirs...")
                visitDirNodes {
                    val local = it.path.resolveAgainst(manifest.local)
                    val remote = manifest.remote.resolve(it.path)
                    print("- $local ${it.resolution.value.operatorStr} $remote :: ")
                    when (it.resolution.value) {
                        DiffResolution.ACCEPT_LOCAL -> {
                            if (Files.exists(local)) {
                                println("Creating remote dir")
                                operator.mkDir(manifest.remote.resolve(it.path))
                            } else {
                                println("Destroying remote dir")
                                operator.rmDir(manifest.remote.resolve(it.path))
                            }
                        }
                        DiffResolution.ACCEPT_REMOTE -> {
                            if (operator.fileExists(remote)) {
                                println("Creating local dir")
                                Files.createDirectories(it.path.resolveAgainst(manifest.local))
                            } else {
                                println("Destroying local dir")
                                Files.deleteIfExists(it.path.resolveAgainst(manifest.local))
                            }
                        }
                        else -> {
                            println("No operation")
                        }
                    }
                }
            }
        }
        println("Synchronized in $syncTime ms")
    }

}

fun isValidPath(path: String) = path != "." && path != ".."

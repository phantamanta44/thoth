package xyz.phanta.thoth.backend

import se.vidstige.jadb.JadbDevice
import se.vidstige.jadb.RemoteFile
import java.nio.file.Files
import kotlin.system.measureTimeMillis

class SyncEngine(private val device: JadbDevice, private val manifest: LibraryManifest) {

    private lateinit var index: LibraryIndexNode
    private var diffTree: DiffEntry? = null

    private fun scanRemote() {
        index = LibraryIndexDirNode(manifest.remote, -1, device.list(manifest.remote.toString())
            .filter { isValidPath(it.path) }
            .associate {
                it.path to LibraryIndexNode.walkRemote(
                    device,
                    it,
                    manifest.remote,
                    GeneralPath(listOf())
                )
            })
    }

    fun buildDiffTree(localIndex: LibraryIndexNode): DiffEntry? {
        val scanTime = measureTimeMillis {
            println("Scanning device ${device.serial}...")
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
                                device.push(
                                    it.path.resolveAgainst(manifest.local).toFile(),
                                    RemoteFile(manifest.remote.resolve(it.path).toString())
                                )
                            } else {
                                println("Destroying remote")
                                device.execute("rm", manifest.remote.resolve(it.path).toString())
                            }
                        }
                        DiffResolution.ACCEPT_REMOTE -> {
                            if (device.fileExists(remote)) {
                                println("Pulling remote to local")
                                val localPath = it.path.resolveAgainst(manifest.local)
                                Files.createDirectories(localPath.parent)
                                device.pull(
                                    RemoteFile(manifest.remote.resolve(it.path).toString()),
                                    localPath.toFile()
                                )
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
                                device.execute("mkdir", "-p", manifest.remote.resolve(it.path).toString())
                            } else {
                                println("Destroying remote dir")
                                device.execute("rm", "-rf", manifest.remote.resolve(it.path).toString())
                            }
                        }
                        DiffResolution.ACCEPT_REMOTE -> {
                            if (device.fileExists(remote)) {
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

fun JadbDevice.fileExists(path: GeneralPath) = list(path.parent().toString()).any { it.path == path.fileName }

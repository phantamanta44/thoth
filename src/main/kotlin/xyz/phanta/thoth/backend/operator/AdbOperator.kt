package xyz.phanta.thoth.backend.operator

import se.vidstige.jadb.JadbDevice
import se.vidstige.jadb.RemoteFile
import xyz.phanta.thoth.backend.GeneralPath
import xyz.phanta.thoth.backend.GeneralPathSized
import xyz.phanta.thoth.backend.RemoteOperator
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

class AdbOperator(private val device: JadbDevice) : RemoteOperator<RemoteFile> {

    companion object {

        private val lock: Lock = ReentrantLock()

    }

    override val identifier: String
        get() = "adb:${device.serial}"

    override fun list(path: GeneralPath): List<RemoteFile> = device.list(path.toString())

    override fun fileExists(path: GeneralPath): Boolean = list(path.parent()).any { it.path == path.fileName }

    override fun sizeOf(file: RemoteFile): Long = file.size.toLong()

    override fun modifyTimeOf(file: RemoteFile): Long = file.lastModified

    override fun fileNameOf(file: RemoteFile): String = file.path

    override fun isDirectory(file: RemoteFile): Boolean = file.isDirectory

    override fun rmFile(path: GeneralPath) = execute({ unsafeRmFile(path) })

    private fun unsafeRmFile(path: GeneralPath) {
        device.execute("rm", "-f", path.toString())
    }

    override fun mkDir(path: GeneralPath) = execute({ unsafeMkDir(path) })

    private fun unsafeMkDir(path: GeneralPath) {
        device.execute("mkdir", "-p", path.toString())
    }

    override fun rmDir(path: GeneralPath) = execute({ unsafeRmDir(path) })

    private fun unsafeRmDir(path: GeneralPath) {
        device.execute("rm", "-rf", path.toString())
    }

    override fun pull(from: GeneralPathSized, to: Path) = execute({
        unsafePull(from, to)
    }, {
        Files.delete(to)
    }, timeoutFor(from.size))

    private fun unsafePull(from: GeneralPathSized, to: Path) {
        device.pull(RemoteFile(from.toString()), to.toFile())
    }

    override fun push(from: Path, to: GeneralPathSized) = execute({
        unsafePush(from, to)
    }, {
        device.execute("rm", "-f", to.toString())
    }, timeoutFor(Files.size(from)))

    private fun unsafePush(from: Path, to: GeneralPathSized) {
        device.push(from.toFile(), RemoteFile(to.toString()))
    }

    private fun timeoutFor(bytes: Long): Long = bytes / 100L

    private fun execute(action: JadbDevice.() -> Unit, failureCallback: (JadbDevice.() -> Unit)? = null, timeout: Long = 3000L) {
        lock.withLock {
            try {
                val adbThread = thread(name = "ADB Operator") { action(device) }
                adbThread.join(timeout)
                if (adbThread.isAlive) {
                    @Suppress("DEPRECATION")
                    adbThread.stop()
                    throw TimeoutException("Operation timed out!")
                }
                return
            } catch (e: Throwable) {
                println("ADB operation raised exception!")
                e.printStackTrace(System.out)
                failureCallback?.let {
                    try {
                        it(device)
                    } catch (e: Throwable) {
                        println("Failure callback raised exception! Uh oh.")
                        e.printStackTrace(System.out)
                    }
                }
            }
        }
    }

}

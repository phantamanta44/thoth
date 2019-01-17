package xyz.phanta.thoth.backend

import se.vidstige.jadb.JadbDevice
import se.vidstige.jadb.RemoteFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

class AdbOperator(private val device: JadbDevice) {

    companion object {

        private val lock: Lock = ReentrantLock()

    }

    val serial: String
        get() = device.serial

    fun list(path: GeneralPath): List<RemoteFile> = device.list(path.toString())

    fun fileExists(path: GeneralPath) = list(path.parent()).any { it.path == path.fileName }

    fun rmFile(path: GeneralPath) = execute({ unsafeRmFile(path) })

    private fun unsafeRmFile(path: GeneralPath) {
        device.execute("rm", "-f", path.toString())
    }

    fun mkDir(path: GeneralPath) = execute({ unsafeMkDir(path) })

    private fun unsafeMkDir(path: GeneralPath) {
        device.execute("mkdir", "-p", path.toString())
    }

    fun rmDir(path: GeneralPath) = execute({ unsafeRmDir(path) })

    private fun unsafeRmDir(path: GeneralPath) {
        device.execute("rm", "-rf", path.toString())
    }

    fun pull(from: RemoteFile, to: Path) = execute({
        unsafePull(from, to)
    }, {
        Files.delete(to)
    }, timeoutFor(from.size.toLong()))

    private fun unsafePull(from: RemoteFile, to: Path) {
        device.pull(from, to.toFile())
    }

    fun push(from: Path, to: RemoteFile) = execute({
        unsafePush(from, to)
    }, {
        device.execute("rm", "-f", to.path)
    }, timeoutFor(Files.size(from)))

    private fun unsafePush(from: Path, to: RemoteFile) {
        device.push(from.toFile(), to)
    }

    private fun timeoutFor(bytes: Long): Long = bytes / 100L

    private fun execute(action: JadbDevice.() -> Unit, failureCallback: (JadbDevice.() -> Unit)? = null, timeout: Long = 3000L) {
        lock.lockInterruptibly()
        try {
            val adbThread = thread(name = "ADB Operator") { action(device) }
            adbThread.join(timeout)
            if (adbThread.isAlive) {
                @Suppress("DEPRECATION")
                adbThread.stop()
                throw TimeoutException("Operation timed out!")
            }
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
        } finally {
            lock.unlock()
        }
    }

}

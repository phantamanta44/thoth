package xyz.phanta.thoth.application

import se.vidstige.jadb.JadbDevice
import xyz.phanta.thoth.backend.RemoteOperator
import xyz.phanta.thoth.backend.operator.AdbOperator
import xyz.phanta.thoth.backend.operator.FsOperator

interface RemoteTarget {

    val identifier: String

    fun createOperator(): RemoteOperator<*>

}

class AdbTarget(private val device: JadbDevice) : RemoteTarget {

    override val identifier: String
        get() = "adb:${device.serial}"

    override fun createOperator(): AdbOperator = AdbOperator(device)

}

class FileSystemTarget : RemoteTarget {

    override val identifier: String
        get() = "local filesystem"

    override fun createOperator(): FsOperator = FsOperator()

}

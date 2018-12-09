package xyz.phanta.thoth.application

import javafx.application.Application
import javafx.application.Platform
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.event.EventHandler
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.stage.Stage
import se.vidstige.jadb.DeviceDetectionListener
import se.vidstige.jadb.JadbConnection
import se.vidstige.jadb.JadbDevice
import xyz.phanta.thoth.application.ui.MainWindowController
import xyz.phanta.thoth.backend.GeneralPath
import xyz.phanta.thoth.backend.LibraryIndexNode
import xyz.phanta.thoth.backend.LibraryManifest
import xyz.phanta.thoth.backend.SyncEngine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis

class MainApp : Application() {

    private val jadb: JadbConnection = JadbConnection()
    private lateinit var manifest: LibraryManifest
    private lateinit var localIndex: LibraryIndexNode
    private val mainWindow: MainWindowController = MainWindowController(this)
    private val diffExists: SimpleBooleanProperty = SimpleBooleanProperty(false)
    private val busy: SimpleBooleanProperty = SimpleBooleanProperty(true)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private var engine: SyncEngine? = null

    val propDiffExists: ReadOnlyBooleanProperty
        get() = diffExists
    val propBusy: ReadOnlyBooleanProperty
        get() = busy

    override fun start(root: Stage) {
        val initTime = measureTimeMillis {
            // parse manifest
            println("Parsing manifest...")
            manifest = LibraryManifest.findManifest(parameters.unnamed.firstOrNull())

            // create main window
            println("Creating window...")
            val loader = FXMLLoader(javaClass.getResource("/ui/main_window.fxml"))
            loader.setController(mainWindow)
            root.scene = Scene(loader.load())
            root.sizeToScene()
            root.title = "Thoth"
            root.isResizable = true
            root.onCloseRequest = EventHandler { executor.shutdownNow() }
            root.show()

            // attempt to start adb daemon
            println("Starting ADB daemon...")
            Runtime.getRuntime().exec("adb start-server").waitFor().let {
                if (it != 0) throw IllegalStateException("Bad ADB daemon init exit value: $it")
            }

            // scan for connected devices
            println("Scanning for devices...")
            mainWindow.onDevicesConnected(jadb.devices)
            jadb.createDeviceWatcher(object : DeviceDetectionListener {
                override fun onDetect(devices: MutableList<JadbDevice>) = mainWindow.onDevicesConnected(devices)

                override fun onException(e: Exception) {
                    // NO-OP
                }
            })
            // TODO figure out why this doesn't work
            // TODO handle disconnected devices

            // enable main window
            busy.value = false
        }
        println("Initialized in $initTime ms")
    }

    fun getManifest(): LibraryManifest = manifest

    fun setActiveDevice(device: JadbDevice?) {
        diffExists.value = false
        if (device == null) {
            Alert(Alert.AlertType.ERROR, "No device selected!", ButtonType.OK).show()
        } else {
            busy.value = true
            doAsync {
                println("Building local index...")
                localIndex = LibraryIndexNode.walkLocal(manifest.local, GeneralPath(manifest.local))

                engine = SyncEngine(device, manifest).also {
                    it.buildDiffTree(localIndex).let { diffRoot ->
                        if (diffRoot != null) {
                            Platform.runLater {
                                mainWindow.renderDiffTree(diffRoot)
                                diffExists.value = true
                                busy.value = false
                            }
                        } else {
                            Platform.runLater {
                                Alert(
                                    Alert.AlertType.INFORMATION,
                                    "Local and remote libraries are identical.",
                                    ButtonType.OK
                                ).show()
                                busy.value = false
                            }
                        }
                    }
                }
            }
        }
    }

    fun performSync() {
        diffExists.value = false
        busy.value = true
        doAsync {
            engine!!.sync()
            Platform.runLater { busy.value = false }
        }
    }

    private fun doAsync(task: () -> Unit) = executor.submit {
        try {
            task()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

}

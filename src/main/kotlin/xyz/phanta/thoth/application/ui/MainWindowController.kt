package xyz.phanta.thoth.application.ui

import javafx.beans.binding.Bindings
import javafx.beans.value.WeakChangeListener
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.util.Callback
import javafx.util.StringConverter
import se.vidstige.jadb.JadbDevice
import xyz.phanta.thoth.application.MainApp
import xyz.phanta.thoth.backend.*
import java.nio.file.Path
import java.util.concurrent.Callable

class MainWindowController(private val app: MainApp) {

    @FXML
    private lateinit var deviceList: ChoiceBox<JadbDevice>
    @FXML
    private lateinit var btnDiff: Button
    @FXML
    private lateinit var fileTree: TreeTableView<DiffEntry>
    @FXML
    private lateinit var bottomContainer: VBox
    @FXML
    private lateinit var progressBar: ProgressBar
    @FXML
    private lateinit var btnPatch: Button

    @FXML
    fun initialize() {
        deviceList.converter = object : StringConverter<JadbDevice>() {
            override fun toString(device: JadbDevice): String = device.serial

            override fun fromString(deviceName: String): JadbDevice = deviceList.items.first { it.serial == deviceName }
        }
        fileTree.columns[0].cellFactory = Callback { LocalDiffCell(app.getManifest().local) }
        fileTree.columns[1].cellFactory = Callback { RemoteDiffCell(app.getManifest().remote) }

        deviceList.disableProperty().bind(app.propBusy)
        btnDiff.disableProperty().bind(app.propBusy)
        fileTree.disableProperty().bind(app.propBusy)
        progressBar.progressProperty().bind(
            Bindings.createDoubleBinding(Callable { if (app.propBusy.value) -1.0 else 0.0 }, app.propBusy)
        )
        btnPatch.disableProperty().bind(app.propDiffExists.not())
    }

    @FXML
    fun diffClicked() {
        fileTree.root = null
        app.setActiveDevice(deviceList.value)
    }

    @FXML
    fun patchClicked() {
        fileTree.root = null
        app.performSync()
    }

    fun onDevicesConnected(device: List<JadbDevice>) {
        deviceList.items.addAll(device)
    }

    fun renderDiffTree(diffRoot: DiffEntry) {
        println("Rendering diff tree...")
        fileTree.root = TreeItem(diffRoot)
        if (diffRoot is DirDiffEntry) diffRoot.children.forEach { renderDiffNode(it, fileTree.root) }
        println("Done")
    }

    private fun renderDiffNode(node: DiffEntry, parent: TreeItem<DiffEntry>) {
        val treeNode = TreeItem(node)
        parent.children += treeNode
        if (node is DirDiffEntry) node.children.forEach { renderDiffNode(it, treeNode) }
    }

}

private abstract class DiffCell(private val side: DiffResolution) : TreeTableCell<DiffEntry, DiffEntry>() {

    override fun updateItem(item: DiffEntry?, empty: Boolean) {
        super.updateItem(item, empty)
        val diff = treeTableRow.treeItem?.value
        if (diff != null) {
            text = stringifyDiff(diff)
            val checkbox = CheckBox()
            checkbox.isSelected = diff.resolution.value == side
            checkbox.selectedProperty().addListener { _, _, value ->
                if (diff.resolution.value == side) {
                    if (!value && diff.resolution.value != DiffResolution.HETEROGENEOUS) {
                        diff.resolution.value = DiffResolution.NONE
                    }
                } else if (value) {
                    diff.resolution.value = side
                }
            }
            diff.resolution.addListener(WeakChangeListener { _, _, value ->
                if (value == side) {
                    if (!checkbox.isSelected) checkbox.isSelected = true
                } else if (checkbox.isSelected) {
                    checkbox.isSelected = false
                }
            })
            graphic = checkbox
            background = Background(BackgroundFill(colourDiff(diff), null, null))
        } else {
            text = null
            graphic = null
            background = null
        }
    }

    abstract fun stringifyDiff(diff: DiffEntry): String

    abstract fun colourDiff(diff: DiffEntry): Color

}

private class RemoteDiffCell(val root: GeneralPath) : DiffCell(DiffResolution.ACCEPT_REMOTE) {

    override fun stringifyDiff(diff: DiffEntry): String = (if (diff.path.isRoot) root else diff.path).toString()

    override fun colourDiff(diff: DiffEntry): Color = when (diff.delta) {
        LocalNonexistent, Heterogeneous -> Color.TRANSPARENT
        RemoteNonexistent -> Color.PALEVIOLETRED
        is ModifyTimeMismatch -> if (diff.delta.remoteNewer) Color.TRANSPARENT else Color.PALEGOLDENROD
    }

}

private class LocalDiffCell(val root: Path) : DiffCell(DiffResolution.ACCEPT_LOCAL) {

    override fun stringifyDiff(diff: DiffEntry): String =
        if (diff.path.isRoot) root.toString() else diff.path.toString()

    override fun colourDiff(diff: DiffEntry): Color = when (diff.delta) {
        RemoteNonexistent, Heterogeneous -> Color.TRANSPARENT
        LocalNonexistent -> Color.PALEVIOLETRED
        is ModifyTimeMismatch -> if (diff.delta.remoteNewer) Color.PALEGOLDENROD else Color.TRANSPARENT
    }

}

package io.github.deficuet.alsv

import com.badlogic.gdx.backends.lwjgl.LwjglApplication
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration
import com.badlogic.gdx.graphics.Texture.TextureFilter
import javafx.stage.Stage
import javafx.beans.property.SimpleStringProperty
import javafx.event.Event
import javafx.event.EventHandler
import javafx.event.EventTarget
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.input.KeyEvent
import javafx.scene.control.skin.TableColumnHeader
import javafx.scene.input.InputEvent
import javafx.scene.input.MouseEvent
import tornadofx.*
import java.awt.Toolkit

class ALSpineViewerApp: App(ALSpineViewerUI::class) {
    override fun start(stage: Stage) {
        if (!cacheFolder.exists()) cacheFolder.mkdir()
        with(stage) {
            isResizable = false
            x = 128.0
            y = 128.0
        }
        super.start(stage)
    }

    override fun stop() {
        val view = find(ALSpineViewerUI::class, scope)
        view.windowApp.exit()
        super.stop()
    }
}

class ALSpineViewerUI: View("碧蓝动态立绘浏览器") {
    init {
        LwjglApplicationConfiguration.disableAudio = true
    }
    private val dpi by lazy {
        val os = System.getProperty("os.name")
        if ("Windows" in os) {
            Toolkit.getDefaultToolkit().screenResolution / 96f
        } else if ("OS X" in os) {
            val o = Toolkit.getDefaultToolkit().getDesktopProperty("apple.awt.contentScaleFactor")
            if (o is Float && o.toInt() > 2) 2f else 1f
        } else 1f
    }
    private val uiScale by lazy { if (dpi >= 2f) 2 else 1 }

    val window = ALSpineViewerWindow(this, uiScale)
    val windowApp = LwjglApplication(
        window,
        LwjglApplicationConfiguration().apply {
            width = 1024 * uiScale; height = 768 * uiScale
            x = 576; y = 128
            title = "Azur Lane Spine Painting Viewer"
            allowSoftwareMode = true
        }
    )

    private val functions = BackendFunctions(this)
    val faceList = observableListOf<String>()

    val taskNameStr = SimpleStringProperty("当前任务：空闲中")
    private val scaleLabel = SimpleStringProperty("1.00")
    private val zoomLabel = SimpleStringProperty("1.00")
    private val speedLabel = SimpleStringProperty("1.00")
    val controls = mutableListOf<Node>()

    var taskNameLabel: Label by singleAssign()
    var scaleSlider: Slider by singleAssign()
    var zoomSlider: Slider by singleAssign()
    var speedSlider: Slider by singleAssign()
    var flipXCheckbox: CheckBox by singleAssign()
    var flipYCheckbox: CheckBox by singleAssign()
    var loopCheckbox: CheckBox by singleAssign()
    var drawAxisCheckbox: CheckBox by singleAssign()
    var faceListView: ListView<String> by singleAssign()
    var lastSelection = ""
    private var keepOnTopCheckbox: CheckBox by singleAssign()

    override val root = vbox {
        hbox {
            alignment = Pos.CENTER_LEFT
            vboxConstraints {
                marginLeft = 16.0; marginTop = 16.0; marginRight = 16.0
            }
            button("导入文件") {
                minWidth = 80.0; minHeight = 30.0
                action {
                    isDisable = true
                    primaryStage.isAlwaysOnTop = false
                    val f = functions.importFile()
                    primaryStage.isAlwaysOnTop = keepOnTopCheckbox.isSelected
                    if (f != null) {
                        runAsync {
                            functions.extractFile(f)
                            isDisable = false
                        }
                    } else {
                        isDisable = false
                    }
                }
            }
            taskNameLabel = label(taskNameStr) {
                hboxConstraints {
                    marginLeft = 12.0
                }
            }
        }
        hbox {
            alignment = Pos.CENTER_LEFT
            vboxConstraints {
                marginTop = 12.0; marginLeft = 16.0; marginRight = 16.0
            }
            label("缩放：")
            label(zoomLabel)
            zoomSlider = slider(0.01, 10.0, 1.0, Orientation.HORIZONTAL) {
                minWidth = 150.0
                hboxConstraints { marginLeft = 8.0 }
                addEventFilter(KeyEvent.ANY, Event::consume)
                valueProperty().addListener { _, _, new ->
                    zoomLabel.value = "%.2f".format(new).slice(0..3)
                    window.camera.zoom = 1 / new.toFloat()
                }
            }
            button("重置") {
                hboxConstraints { marginLeft = 8.0 }
                action {
                    window.resetCamera()
                    val x = window.camera.position.x
                    zoomSlider.value = 1.0
                    window.camera.position.x = x
                }
            }
        }
        hbox {
            alignment = Pos.CENTER_LEFT
            vboxConstraints {
                marginTop = 12.0; marginLeft = 16.0
            }
            label("比例：")
            label(scaleLabel)
            scaleSlider = slider(0.1, 3.0, 1.0, Orientation.HORIZONTAL) {
                minWidth = 150.0
                hboxConstraints { marginLeft = 8.0 }
                addEventFilter(KeyEvent.ANY, Event::consume)
                valueProperty().addListener { _, _, new ->
                    scaleLabel.value = "%.2f".format(new)
                }
                onMouseReleased = EventHandler {
                    windowApp.postRunnable {
                        window.loadSkeleton()
                    }
                }
            }
            button("重置") {
                hboxConstraints { marginLeft = 8.0 }
                action {
                    scaleSlider.value = 1.0
                    window.resetCamera()
                    windowApp.postRunnable {
                        window.loadSkeleton()
                    }
                }
            }
            isDisable = true
        }.also { controls.add(it) }
        hbox {
            alignment = Pos.CENTER_LEFT
            vboxConstraints {
                marginTop = 12.0; marginLeft = 16.0
            }
            label("速度：")
            label(speedLabel)
            speedSlider = slider(0.0, 3.0, 1.0, Orientation.HORIZONTAL) {
                minWidth = 150.0
                hboxConstraints { marginLeft = 8.0 }
                addEventFilter(KeyEvent.ANY, Event::consume)
                valueProperty().addListener { _, _, new ->
                    speedLabel.value = "%.2f".format(new)
                }
            }
            button("重置") {
                hboxConstraints { marginLeft = 8.0 }
                action { speedSlider.value = 1.0 }
            }
            isDisable = true
        }.also { controls.add(it) }
        hbox {
            vboxConstraints {
                marginLeft = 16.0; marginTop = 12.0
            }
            label("翻转：")
            flipXCheckbox = checkbox("沿X轴")
            flipYCheckbox = checkbox("沿Y轴") {
                hboxConstraints { marginLeft = 16.0 }
            }
            isDisable = true
        }.also { controls.add(it) }
        hbox {
            vboxConstraints {
                marginLeft = 16.0; marginTop = 12.0
            }
            label("材质渲染：")
            checkbox("线性过滤") {
                isSelected = true
                action {
                    windowApp.postRunnable {
                        val filter = if (isSelected) TextureFilter.Linear else TextureFilter.Nearest
                        window.animGroupList.forEach { group ->
                            group.atlas.textures.forEach { tex ->
                                tex.setFilter(filter, filter)
                            }
                        }
                    }
                }
            }
            isDisable = true
        }.also { controls.add(it) }
        hbox {
            alignment = Pos.CENTER_LEFT
            vboxConstraints {
                marginTop = 12.0; marginLeft = 16.0
            }
            label("其他设置：")
            vbox {
                hbox {
                    keepOnTopCheckbox = checkbox("窗口置顶") {
                        action {
                            primaryStage.isAlwaysOnTop = isSelected
                        }
                    }
                    loopCheckbox = checkbox("循环") {
                        isSelected = true
                        hboxConstraints { marginLeft = 16.0 }
                        action {
                            windowApp.postRunnable {
                                window.animGroupList.forEach {
                                    it.state.setAnimation(0, it.anim, isSelected)
                                }
                            }
                        }
                    }
                }
                hbox {
                    vboxConstraints {
                        marginTop = 12.0
                    }
                    drawAxisCheckbox = checkbox("显示坐标轴") {
                        isSelected = true
                    }
                }
            }
            isDisable = true
        }.also { controls.add(it) }
        separator {
            vboxConstraints { marginTop = 12.0; marginLeft = 16.0; marginRight = 16.0 }
        }
        hbox {
            vboxConstraints {
                marginTop = 12.0; marginLeft = 16.0
                marginRight = 16.0; marginBottom = 16.0
            }
            label("差分：")
            faceListView = listview(faceList) {
                hboxConstraints { marginLeft = 16.0; marginRight = 0.0 }
                maxHeight = 144.0; maxWidth = 216.0
                onUserSelectModified(clickCount = 1) { item ->
                    if (item != lastSelection) {
                        windowApp.postRunnable {
                            with(window.mainAnimState) {
                                setAnimation(1, item, loopCheckbox.isSelected)
                            }
                        }
                        lastSelection = item
                    }
                }
                addEventFilter(KeyEvent.KEY_PRESSED) { it.consume() }
            }
            isDisable = true
        }.also { controls.add(it) }
    }
}

/**
 * Modified [tornadofx.isInsideRow]
 */
fun EventTarget.isValidRowModified(): Boolean {
    return when {
        this !is Node -> false
        this is TableColumnHeader -> false
        this is TableRow<*> -> !this.isEmpty
        this is TableView<*> || this is TreeTableRow<*>
                || this is TreeTableView<*> || this is ListCell<*> -> true
        this.parent != null -> this.parent.isValidRowModified()
        else -> false
    }
}

/**
 * Modified [tornadofx.onUserSelect]
 */
fun <T> ListView<T>.onUserSelectModified(clickCount: Int, action: (T) -> Unit) {
    val isSelected = { event: InputEvent ->
        event.target.isValidRowModified() && !selectionModel.isEmpty
    }
    addEventFilter(MouseEvent.MOUSE_CLICKED) { event ->
        if (event.clickCount == clickCount && isSelected(event)) {
            action(selectedItem!!)
        }
    }
    addEventFilter(KeyEvent.KEY_PRESSED) { it.consume() }
}

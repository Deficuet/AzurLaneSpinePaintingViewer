package io.github.deficuet.alsv

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Interpolation.linear
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.esotericsoftware.spine.*
import io.github.deficuet.tools.file.deleteDirectory
import io.github.deficuet.unitykt.cast
import javafx.application.Platform
import net.mamoe.yamlkt.Yaml
import kotlin.math.sign

class ALSpineViewerWindow(
    private val ui: ALSpineViewerUI,
    private val uiScale: Int
): ApplicationAdapter() {
    private lateinit var stage: Stage
    private lateinit var batch: PolygonSpriteBatch
    private lateinit var renderer: SkeletonRenderer
    private lateinit var debugRenderer: SkeletonRendererDebug
    lateinit var camera: OrthographicCamera

    val infoList = mutableListOf<SkeletonAtlasFilesPair>()
    val animGroupList = mutableListOf<SkeletonAnimationGroup>()

    override fun create() {
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            e.printStackTrace()
            Runtime.getRuntime().halt(0)
        }
        stage = Stage(ScreenViewport())
        batch = PolygonSpriteBatch(3100)
        renderer = SkeletonRenderer().apply {
            premultipliedAlpha = true
        }
        debugRenderer = SkeletonRendererDebug()
        camera = OrthographicCamera()
        resetCamera()
        Gdx.input.inputProcessor = InputMultiplexer(
            stage, object : InputAdapter() {
                private var offsetX = 0
                private var offsetY = 0

                override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                    offsetX = screenX
                    offsetY = Gdx.graphics.height - 1 - screenY
                    return false
                }

                override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
                    val dy = Gdx.graphics.height - 1 - screenY
                    with(camera.position) {
                        x -= (screenX - offsetX) * camera.zoom
                        y -= (dy - offsetY) * camera.zoom
                    }
                    offsetX = screenX
                    offsetY = dy
                    return false
                }

                override fun scrolled(amountX: Float, amountY: Float): Boolean {
                    var zoom = ui.zoomSlider.value
                    val min = ui.zoomSlider.min
                    val max = ui.zoomSlider.max
                    val speed = minOf(1.2, (zoom - min) / (max - min) * 3.5)
                    zoom -= linear.apply(0.02f, 0.2f, speed.toFloat()) * sign(amountY)
                    Platform.runLater { ui.zoomSlider.value = MathUtils.clamp(zoom, min, max) }
                    return false
                }
            }
        )
    }

    fun resetCamera() {
        with(camera.position) {
            x = 0f
            y = Gdx.graphics.height / 4f
        }
    }

    fun loadSkeleton() {
        animGroupList.clear()
        infoList.forEach {
            val atlas = TextureAtlas(
                TextureAtlas.TextureAtlasData(it.atlasFile, it.atlasFile.parent(), false)
            )
            val skd = SkeletonBinary(atlas).apply {
                scale = ui.scaleSlider.value.toFloat()
            }.readSkeletonData(it.skeletonFile)
            animGroupList.add(
                SkeletonAnimationGroup(
                    Skeleton(skd), atlas,
                    AnimationState(AnimationStateData(skd)).apply {
                        data.defaultMix = it.defaultMix
                        setAnimation(
                            0, skd.findAnimation(it.startingAnimName),
                            ui.loopCheckbox.isSelected
                        )
                    },
                    skd.findAnimation(it.startingAnimName)
                )
            )
        }
    }

    override fun render() {
        Gdx.gl.glClearColor(0.56f, 0.56f, 0.56f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        val delta = minOf(
            Gdx.graphics.deltaTime, 0.032f
        ) * ui.speedSlider.value.toFloat()
        camera.update()
        batch.projectionMatrix.set(camera.combined)
        val shapes = debugRenderer.shapeRenderer.apply {
            projectionMatrix = camera.combined
            if (ui.drawAxisCheckbox.isSelected) {
                color = Color.DARK_GRAY
                begin(ShapeRenderer.ShapeType.Line)
                line(0f, -2.14748262E9f, 0f, 2.14748262E9f)
                line(-2.14748262E9f, 0f, 2.14748262E9f, 0f)
                end()
            }
        }
        animGroupList.forEach {
            val sk = it.skeleton; val state = it.state
            sk.setFlip(ui.flipXCheckbox.isSelected, ui.flipYCheckbox.isSelected)
            sk.update(delta)
            state.apply {
                update(delta)
                apply(sk)
            }
            sk.updateWorldTransform()
        }
        batch.begin()
        animGroupList.forEach {
            renderer.draw(batch, it.skeleton)
        }
        batch.end()
        if (animGroupList.isNotEmpty()) {
            val e = animGroupList[0].state.getCurrent(0)
            shapes.apply {
                projectionMatrix.setToOrtho2D(
                    0f, 0f,
                    Gdx.graphics.width.toFloat(),
                    Gdx.graphics.height.toFloat()
                )
                updateMatrices()
                begin(ShapeRenderer.ShapeType.Line)
                val x = Gdx.graphics.width * e.animationTime / e.animationEnd
                color = Color.CYAN
                line(x, 0f, x, 12f)
                val markX = Gdx.graphics.width * (if (e.mixDuration == 0f) 1f
                    else minOf(1f, e.mixTime / e.mixDuration))
                color = Color.RED
                line(markX, 0f, markX, 12f)
                end()
            }
        }
    }

    override fun resize(width: Int, height: Int) {
        val x = camera.position.x
        val y = camera.position.y
        camera.setToOrtho(false)
        camera.position.set(x, y, 0f)
        stage.viewport.cast<ScreenViewport>().apply {
            unitsPerPixel = 1f / uiScale
            update(width, height, true)
        }
    }

    override fun dispose() {
        batch.dispose()
        stage.dispose()
        configFile.writeText(
            Yaml.encodeToString(Configurations.serializer(), configs)
        )
        deleteDirectory(cachePath)
    }
}

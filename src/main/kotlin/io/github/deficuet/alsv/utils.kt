package io.github.deficuet.alsv

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.esotericsoftware.spine.Animation
import com.esotericsoftware.spine.AnimationState
import com.esotericsoftware.spine.Skeleton
import io.github.deficuet.unitykt.UnityAssetManager
import io.github.deficuet.unitykt.classes.AssetBundle
import io.github.deficuet.unitykt.classes.MonoBehaviour
import io.github.deficuet.unitykt.firstObjectOf
import io.github.deficuet.unitykt.pptr.getAs
import javafx.application.Platform
import kotlinx.serialization.Serializable
import net.mamoe.yamlkt.Yaml
import tornadofx.*
import java.io.File
import java.nio.file.Files
import java.util.concurrent.FutureTask
import kotlin.io.path.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import javafx.scene.paint.Color as ColorFX

data class SkeletonAtlasFilesPair(
    val skeletonFile: FileHandle,
    val atlasFile: FileHandle,
    val startingAnimName: String,
    val defaultMix: Float,
    val sortingOrder: Short
)

data class SkeletonAnimationGroup(
    val skeleton: Skeleton,
    val atlas: TextureAtlas,
    val state: AnimationState,
    val anim: Animation
)

@Serializable
data class Configurations(
    var assetSystemRoot: String = "",
    var importFilesPath: String = ""
)

val defaultConfig by lazy { Configurations() }

val cacheFolder = File("./cache")
val cachePath: String get() = cacheFolder.absolutePath

val configFile = File("alsv.yml")

val configs = if (configFile.exists()) {
    Yaml.decodeFromString(Configurations.serializer(), configFile.readText())
} else {
    configFile.writeText(
        Yaml.encodeToString(Configurations.serializer(), defaultConfig)
    )
    defaultConfig
}

val dependencies: Map<String, List<String>> by lazy {
    UnityAssetManager.new().use { manager ->
        val depContext = manager.loadFile(Path(configs.assetSystemRoot).resolve("dependencies"))
        val bundle = depContext.objectList.firstObjectOf<AssetBundle>()
        val mono = bundle.mContainer.values.first()[0].asset.getAs<MonoBehaviour>()
        mono.toTypeTreeJson()!!.let { json ->
            val keys = json.getJSONArray("m_Keys")
            val values = json.getJSONArray("m_Values")
            val table = mutableMapOf<String, List<String>>()
            for (i in 0 until keys.length()) {
                val key = keys.getString(i)
                val value = values.getJSONObject(i).getJSONArray("m_Dependencies").map { it.toString() }
                table[key] = value
            }
            table
        }
    }
}

fun File.withDefaultPath(defaultPath: String = "C:/Users"): File {
    return if (exists() && isDirectory) this else File(defaultPath)
}

fun deleteDirectory(pathString: String) {
    val path = Path(pathString)
    Files.newDirectoryStream(path).use { stream ->
        stream.forEach {
            when {
                it.isDirectory() -> deleteDirectory(it.toString())
                it.isRegularFile() -> it.deleteExisting()
            }
        }
    }
    path.deleteExisting()
}

fun <P: View, T> runBlockingFX(gui: P, task: P.() -> T): T? {
    return try {
        if (Platform.isFxApplicationThread()) {
            gui.task()
        } else {
            val future = FutureTask { gui.task() }
            Platform.runLater(future)
            future.get()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

val errorTextFill: ColorFX = ColorFX.rgb(187, 0, 17)

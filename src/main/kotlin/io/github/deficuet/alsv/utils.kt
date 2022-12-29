package io.github.deficuet.alsv

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.esotericsoftware.spine.Animation
import com.esotericsoftware.spine.AnimationState
import com.esotericsoftware.spine.Skeleton
import kotlinx.serialization.Serializable
import net.mamoe.yamlkt.Yaml
import java.io.File

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
    var importFilesPath: String
)

val defaultConfig by lazy {
    Configurations(
        importFilesPath = "C:/Users"
    )
}

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

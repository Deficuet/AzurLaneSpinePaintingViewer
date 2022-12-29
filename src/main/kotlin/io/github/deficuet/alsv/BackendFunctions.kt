package io.github.deficuet.alsv

import com.badlogic.gdx.files.FileHandle
import javax.imageio.ImageIO
import javafx.stage.FileChooser
import java.io.File
import tornadofx.*
import org.json.JSONObject
import io.github.deficuet.unitykt.*
import io.github.deficuet.unitykt.data.*
import io.github.deficuet.tools.image.flipY
import javafx.application.Platform

class BackendFunctions(private val ui: ALSpineViewerUI) {
    fun importFile(): File? {
        val files = chooseFile(
            "选择文件", arrayOf(
                FileChooser.ExtensionFilter("All types", "*.*")
            ), File(configs.importFilesPath)
        )
        if (files.isEmpty()) return null
        val file = files[0]
        configs.importFilesPath = file.parent
        ui.paintingNameLabel.value = "当前任务：${file.nameWithoutExtension}"
        return file
    }

    fun extractFile(file: File) {
        val newInfoList = UnityAssetManager().use { manager ->
            val context = manager.loadFile(file.absolutePath)
            val folderPath = "$cachePath/${file.nameWithoutExtension}".also {
                File(it).apply { mkdir() }
            }
            context.objects.firstObjectOf<AssetBundle>()
                .mContainer[0].second.asset.getObjAs<GameObject>()
                .mComponents.firstObjectOf<MonoBehaviour>()
                .typeTreeJson!!.getJSONArray("prefabItem").map { item ->
                    val itemGameObj = context.objects.objectFromPathID<GameObject>(
                        item.cast<JSONObject>().getLong("m_PathID")
                    )
                    val graphicJson = itemGameObj.mComponents.firstObjectOf<MonoBehaviour>().typeTreeJson!!
                    val tex = context.objects.objectFromPathID<Material>(
                        graphicJson.getJSONObject("m_Material").getLong("m_PathID")
                    ).mSavedProperties.mTexEnvs[0].second.mTexture.getObjAs<Texture2D>()
                    ImageIO.write(
                        tex.image.flipY(), "png",
                        File("$folderPath/${tex.mName}.png")
                    )
                    val skeletonJson = context.objects.objectFromPathID<MonoBehaviour>(
                        graphicJson.getJSONObject("skeletonDataAsset").getLong("m_PathID")
                    ).typeTreeJson!!
                    val skeletonBinary = context.objects.objectFromPathID<TextAsset>(
                        skeletonJson.getJSONObject("skeletonJSON").getLong("m_PathID")
                    )
                    val skeletonFile = File("$folderPath/${skeletonBinary.mName}").apply {
                        writeBytes(skeletonBinary.mScript)
                    }
                    val atlasBinary = context.objects.objectFromPathID<TextAsset>(
                        context.objects.objectFromPathID<MonoBehaviour>(
                            skeletonJson.getJSONArray("atlasAssets")[0].cast<JSONObject>().getLong("m_PathID")
                        ).typeTreeJson!!.getJSONObject("atlasFile").getLong("m_PathID")
                    )
                    val atlasFile = File("$folderPath/${atlasBinary.mName}").apply {
                        writeBytes(atlasBinary.mScript)
                    }
                    SkeletonAtlasFilesPair(
                        FileHandle(skeletonFile), FileHandle(atlasFile),
                        graphicJson.getString("startingAnimation"),
                        skeletonJson.getFloat("defaultMix"),
                        itemGameObj.mComponents.firstObjectOf<Canvas>().mSortingOrder
                    )
                }.sortedBy { it.sortingOrder }
        }
        with(ui.window) {
            infoList.clear()
            infoList.addAll(newInfoList)
            resetCamera()
            ui.windowApp.postRunnable { loadSkeleton() }
        }
        Platform.runLater { ui.controls.forEach { it.isDisable = false } }
    }
}
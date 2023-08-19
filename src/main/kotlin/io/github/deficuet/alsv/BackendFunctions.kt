package io.github.deficuet.alsv

import com.badlogic.gdx.files.FileHandle
import javax.imageio.ImageIO
import javafx.stage.FileChooser
import java.io.File
import tornadofx.*
import org.json.JSONObject
import io.github.deficuet.unitykt.*
import io.github.deficuet.unitykt.data.*
import io.github.deficuet.jimage.flipY
import javafx.application.Platform
import javafx.scene.paint.Color

class BackendFunctions(private val ui: ALSpineViewerUI) {
    fun importFile(): File? {
        ui.lastSelection = ""
        val files = chooseFile(
            "选择文件", arrayOf(
                FileChooser.ExtensionFilter("All types", "*.*")
            ), File(configs.importFilesPath)
        )
        if (files.isEmpty()) return null
        val file = files[0]
        configs.importFilesPath = file.parent
        return file
    }

    fun extractFile(file: File) {
        val res = File("${file}_res")
        if (!res.exists()) {
            Platform.runLater {
                ui.taskNameLabel.textFill = Color.web("#BB0011")
                ui.taskNameStr.value = "找不到文件：${res.nameWithoutExtension}"
            }
            return
        }
        Platform.runLater {
            ui.taskNameLabel.textFill = Color.BLACK
            ui.taskNameStr.value = "加载中：${file.nameWithoutExtension}"
        }
        val newInfoList = UnityAssetManager().use { manager ->
            val context = manager.loadFile(file.absolutePath)
            manager.loadFile(res.absolutePath)
            val folderPath = "$cachePath/${file.nameWithoutExtension}".also {
                File(it).apply { mkdir() }
            }
            context.objectList.firstObjectOf<AssetBundle>()
                .mContainer[0].second.asset.getObjAs<GameObject>()
                .mComponents.firstObjectOf<MonoBehaviour>()
                .typeTreeJson!!.getJSONArray("prefabItem").map { item ->
                    val itemGameObj = manager.objectList.findWithPathID<GameObject>(
                        item.cast<JSONObject>().getLong("m_PathID")
                    )
                    val graphicJson = itemGameObj.mComponents.firstObjectOf<MonoBehaviour>().typeTreeJson!!
                    val tex = manager.objectList.findWithPathID<Material>(
                        graphicJson.getJSONObject("m_Material").getLong("m_PathID")
                    ).mSavedProperties.mTexEnvs[0].second.mTexture.getObjAs<Texture2D>()
                    ImageIO.write(
                        tex.image.flipY(), "png",
                        File("$folderPath/${tex.mName}.png")
                    )
                    val skeletonJson = manager.objectList.findWithPathID<MonoBehaviour>(
                        graphicJson.getJSONObject("skeletonDataAsset").getLong("m_PathID")
                    ).typeTreeJson!!
                    val skeletonBinary = manager.objectList.findWithPathID<TextAsset>(
                        skeletonJson.getJSONObject("skeletonJSON").getLong("m_PathID")
                    )
                    val skeletonFile = File("$folderPath/${skeletonBinary.mName}").apply {
                        writeBytes(skeletonBinary.mScript)
                    }
                    val atlasBinary = manager.objectList.findWithPathID<TextAsset>(
                        manager.objectList.findWithPathID<MonoBehaviour>(
                            skeletonJson.getJSONArray("atlasAssets")[0]
                                .cast<JSONObject>().getLong("m_PathID")
                        ).typeTreeJson!!.getJSONObject("atlasFile").getLong("m_PathID")
                    )
                    val atlasFile = File("$folderPath/${atlasBinary.mName}").apply {
                        writeBytes(atlasBinary.mScript)
                    }
                    SkeletonAtlasFilesPair(
                        FileHandle(skeletonFile), FileHandle(atlasFile),
                        graphicJson.getString("startingAnimation"),
                        skeletonJson.getFloat("defaultMix"),
                        itemGameObj.mComponents.allObjectsOf<Canvas>()
                            .firstOrNull()?.mSortingOrder ?: 0
                    )
                }.sortedBy { it.sortingOrder }
        }
        Platform.runLater {
            ui.faceList.clear()
            ui.taskNameStr.value = "当前任务：${file.nameWithoutExtension}"
            ui.controls.forEach { it.isDisable = false }
        }
        with(ui.window) {
            infoList.clear()
            infoList.addAll(newInfoList)
            resetCamera()
            ui.windowApp.postRunnable { loadSkeleton() }
        }
    }
}
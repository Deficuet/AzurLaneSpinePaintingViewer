package io.github.deficuet.alsv

import com.badlogic.gdx.files.FileHandle
import io.github.deficuet.jimage.flipY
import io.github.deficuet.unitykt.UnityAssetManager
import io.github.deficuet.unitykt.classes.*
import io.github.deficuet.unitykt.firstObjectOf
import io.github.deficuet.unitykt.pptr.firstObjectOf
import io.github.deficuet.unitykt.pptr.firstOfOrNull
import io.github.deficuet.unitykt.pptr.getAs
import io.github.deficuet.unitykt.pptr.getObj
import javafx.stage.FileChooser
import org.json.JSONObject
import tornadofx.*
import java.io.File
import javax.imageio.ImageIO
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo
import javafx.scene.paint.Color as ColorFX

class Functions(private val ui: ALSpineViewerUI) {
    fun importFile(): File? {
        ui.lastSelection = ""
        val files = chooseFile(
            "选择文件", arrayOf(
                FileChooser.ExtensionFilter("All types", "*.*")
            ),
            File(configs.importFilesPath)
                .withDefaultPath(Path(configs.assetSystemRoot).resolve("spinepainting").pathString)
                .withDefaultPath()
        )
        if (files.isEmpty()) return null
        val file = files[0]
        configs.importFilesPath = file.parent
        return file
    }

    fun extractFile(file: File) {
        runBlockingFX(ui) {
            controls.forEach { it.isDisable = true }
            dependenciesList.clear()
            faceList.clear()
        }
        val rootPath = Path(configs.assetSystemRoot)
        val dependenciesPathList = dependencies[
            file.toPath().relativeTo(rootPath).joinToString("/")
        ]
        if (dependenciesPathList == null) {
            runBlockingFX(ui) {
                taskNameLabel.textFill = errorTextFill
                taskNameStr.value = "dependencies文件已过时"
            }
            return
        }
        val dependenciesTable = dependenciesPathList.associateWith { rootPath.resolve(it) }
        val dependenciesCheckTable = dependenciesPathList.associateWith {
            dependenciesTable.getValue(it).exists()
        }
        runBlockingFX(ui) {
            dependenciesList.addAll(dependenciesPathList)
            dependenciesColumn.cellFormat {
                text = it
                textFill = when (dependenciesCheckTable.getValue(it)) {
                    true -> ColorFX.BLUE
                    else -> errorTextFill
                }
                tooltip(it)
            }
        }
        if (dependenciesCheckTable.values.any { !it }) {
            runBlockingFX(ui) {
                taskNameLabel.textFill = errorTextFill
                taskNameStr.value = "依赖项缺失"
            }
            return
        }
        runBlockingFX(ui) {
            taskNameLabel.textFill = ColorFX.BLACK
            taskNameStr.value = "加载中：${file.nameWithoutExtension}"
        }
        val newInfoList = UnityAssetManager.new(rootPath).use { manager ->
            val context = manager.loadFile(file.absolutePath)
            manager.loadFiles(*dependenciesTable.values.toTypedArray())
            val folderPath = "$cachePath/${file.nameWithoutExtension}".also {
                File(it).apply { mkdir() }
            }
            val bundle = context.objectList.firstObjectOf<AssetBundle>()
            bundle.mContainer.values.first()[0].asset.getAs<GameObject>()
                .mComponents.firstObjectOf<MonoBehaviour>()
                .toTypeTreeJson()!!.getJSONArray("prefabItem").map { itemInfo ->
                    val itemGameObj = with(itemInfo as JSONObject) {
                        bundle.createPPtr<GameObject>(
                            getInt("m_FileID"),
                            getLong("m_PathID")
                        ).getObj()
                    }
                    val graphicJson = itemGameObj.mComponents.firstObjectOf<MonoBehaviour>().toTypeTreeJson()!!
                    val skeletonMono = with(graphicJson.getJSONObject("skeletonDataAsset")) {
                        bundle.createPPtr<MonoBehaviour>(
                            getInt("m_FileID"),
                            getLong("m_PathID")
                        ).getObj()
                    }
                    val skeletonJson = skeletonMono.toTypeTreeJson()!!
                    val skeletonBinary = with(skeletonJson.getJSONObject("skeletonJSON")) {
                        skeletonMono.createPPtr<TextAsset>(
                            getInt("m_FileID"),
                            getLong("m_PathID")
                        ).getObj()
                    }
                    val skeletonFile = File("$folderPath/${skeletonBinary.mName}").apply {
                        writeBytes(skeletonBinary.mScript)
                    }
                    val atlasMono = with(skeletonJson.getJSONArray("atlasAssets")[0] as JSONObject) {
                        skeletonMono.createPPtr<MonoBehaviour>(
                            getInt("m_FileID"),
                            getLong("m_PathID")
                        ).getObj()
                    }
                    val atlasJson = atlasMono.toTypeTreeJson()!!
                    val atlasBinary = with(atlasJson.getJSONObject("atlasFile")) {
                        atlasMono.createPPtr<TextAsset>(
                            getInt("m_FileID"),
                            getLong("m_PathID")
                        ).getObj()
                    }
                    val atlasFile = File("$folderPath/${atlasBinary.mName}").apply {
                        writeBytes(atlasBinary.mScript)
                    }
                    atlasJson.getJSONArray("materials").forEach { materialInfo ->
                        val material = with(materialInfo as JSONObject) {
                            atlasMono.createPPtr<Material>(
                                getInt("m_FileID"),
                                getLong("m_PathID")
                            ).getObj()
                        }
                        val tex = material.mSavedProperties.mTexEnvs
                            .values.first()[0].mTexture.getAs<Texture2D>()
                        ImageIO.write(
                            tex.getImage()!!.flipY().apply(true), "png",
                            File("$folderPath/${tex.mName}.png")
                        )
                    }
                    SkeletonAtlasFilesPair(
                        FileHandle(skeletonFile), FileHandle(atlasFile),
                        graphicJson.getString("startingAnimation"),
                        skeletonJson.getFloat("defaultMix"),
                        itemGameObj.mComponents.firstOfOrNull<Canvas>()?.mSortingOrder ?: 0
                    )
                }.sortedBy { it.sortingOrder }
        }
        runBlockingFX(ui) {
            taskNameStr.value = "当前任务：${file.nameWithoutExtension}"
            controls.forEach { it.isDisable = false }
        }
        with(ui.window) {
            infoList.clear()
            infoList.addAll(newInfoList)
            resetCamera()
            ui.windowApp.postRunnable { loadSkeleton() }
        }
    }

    companion object {
        fun importAssetSystemRoot(): File? {
            val folder = chooseDirectory(
                "选择文件夹",
                File(configs.assetSystemRoot).withDefaultPath()
            ) ?: return null
            configs.assetSystemRoot = folder.absolutePath
            return folder
        }
    }
}
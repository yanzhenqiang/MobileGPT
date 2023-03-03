package org.autojs.autojs.ui.build

import android.app.Application
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stardust.app.GlobalAppContext
import com.stardust.autojs.project.Asset
import com.stardust.autojs.project.Constant
import com.stardust.autojs.project.ProjectConfig
import com.stardust.pio.PFiles
import com.stardust.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.Pref
import org.autojs.autoxjs.R
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.tool.*
import java.io.File
import java.net.URLDecoder

class BuildViewModelFactory(
    private val application: Application,
    private val source: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass == BuildViewModel::class.java) {
            return BuildViewModel(this.application, source) as T
        }
        return super.create(modelClass)
    }
}

/**
 * @author wilinz
 * @date 2022/5/23
 */
class BuildViewModel(private val app: Application, private var source: String) :
    AndroidViewModel(app) {

    companion object {
        const val TAG = "BuildViewModel"
        private val REGEX_PACKAGE_NAME =
            Regex("^([A-Za-z][A-Za-z\\d_]*\\.)+([A-Za-z][A-Za-z\\d_]*)$")
    }

    var directory: String? = null
    var isSingleFile: Boolean = false
    var oldProjectConfig: ProjectConfig? = null
    var projectConfig: ProjectConfig = ProjectConfig()
    var isOldProjectConfigExist: Boolean = false

    var buildDialogText by mutableStateOf("")
    var isShowBuildDialog by mutableStateOf(false)
    var isShowBuildSuccessfullyDialog by mutableStateOf(false)
    var outApk: File? = null

    //文件
    var sourcePath by mutableStateOf("")
    var outputPath by mutableStateOf("")
//    var customOcrModelPath by mutableStateOf("")


    //配置
    var appName by mutableStateOf("")
    var packageName by mutableStateOf("")
    var versionName by mutableStateOf("1.0.0")
    var versionCode by mutableStateOf("1")
    var icon by mutableStateOf<Uri?>(null)


    //编译选项
    //--abi
    var abiList by mutableStateOf(
        Constant.Abi.abis.joinToString(", ")
    )

    //--so
    var isRequiredOpenCv by mutableStateOf(false)
    var isRequired7Zip by mutableStateOf(false)
    var isRequiredTerminalEmulator by mutableStateOf(true)

    //--assets
    var isRequiredDefaultOcrModelData by mutableStateOf(false)

    //--so and assets
    var isRequiredMlKitOCR by mutableStateOf(false)

    //运行配置
    var mainScriptFile by mutableStateOf("main.js")
    var isHideLauncher by mutableStateOf(false)
    var isStableMode by mutableStateOf(false)
    var isHideLogs by mutableStateOf(false)
    var isVolumeUpControl by mutableStateOf(false)
    var displaySplash by mutableStateOf(true)//todo

    //--特殊权限
    var isRequiredAccessibilityServices by mutableStateOf(false)
    var isRequiredBackgroundStart by mutableStateOf(false)
    var isRequiredDrawOverlay by mutableStateOf(false)

    //--
    var splashText by mutableStateOf(app.getString(R.string.powered_by_autojs))
    var splashIcon by mutableStateOf<Uri?>(null)
    var serviceDesc by mutableStateOf(app.getString(R.string.text_accessibility_service_description))


    val isConfigurationHasChanged: Boolean
        get() {
            return if (oldProjectConfig == null) true
            else {
                syncToProjectConfig()
                Log.d(TAG, oldProjectConfig.toString())
                Log.d(TAG, projectConfig.toString())
                oldProjectConfig != projectConfig
            }
        }

    init {
        setIsSingleFile()
        mainScriptFile = getMainScriptName()
    }

    fun saveAsProject() {
        isSingleFile = false

        if (directory == Pref.getScriptDirPath()) {
            val newProjectDir = File(directory!!, PFiles.getNameWithoutExtension(source))
            newProjectDir.mkdirs()
            val newSourceFile = File(newProjectDir, PFiles.getName(source))
            File(source).renameTo(newSourceFile)

            val oldDirectory = directory
            source = newSourceFile.path
            directory = newProjectDir.path
            sourcePath = source
            outputPath = File(directory!!, projectConfig.buildDir).path
            saveConfig(onCompletion = {
                deleteOldFile(oldDirectory!!)
                toast(app, app.getString(R.string.text_save_project_to, directory))
            })
        } else {
            outputPath = File(directory!!, projectConfig.buildDir).path
            saveConfig(onCompletion = {
                deleteOldFile(directory!!)
                toast(getApplication(), R.string.text_save_successfully)
            })
        }
    }

    private fun deleteOldFile(oldDirectory: String) {
        File(oldDirectory, getConfigName1(true)).delete()
        PFiles.deleteRecursively(File(oldDirectory, getResourcePath(true)))
    }

    private fun setIsSingleFile() {
        val sourceFile = File(source)
        isSingleFile = if (sourceFile.isFile) {
            !File(sourceFile.parentFile, ProjectConfig.CONFIG_FILE_NAME).exists()
        } else false
    }

    fun saveConfig(
        onCompletion: () -> Unit = {
            toast(
                getApplication(),
                R.string.text_save_successfully
            )
        }
    ) {
        syncToProjectConfig()
        CoroutineScope(Dispatchers.Main).launch {
            writeProjectConfigAndRefreshView()
            onCompletion()
        }
    }

    suspend fun writeProjectConfigAndRefreshView() {
        withContext(Dispatchers.IO) {
            saveLogo()
            saveSplashIcon()
            PFiles.write(
                ProjectConfig.configFileOfDir(directory!!, configName),
                projectConfig.toJson()
            )
        }
        withContext(Dispatchers.Main) {
            oldProjectConfig = projectConfig.copy()
            val item = ExplorerFileItem(source, null)
            Explorers.workspace().notifyItemChanged(item, item)
        }
    }

    val configName: String
        get() = if (isSingleFile) {
            //test.js对应test_config.json
            PFiles.getNameWithoutExtension(source) + "_config.json"
        } else ProjectConfig.CONFIG_FILE_NAME

    fun getConfigName1(isSingleFile: Boolean = this.isSingleFile): String {
        return if (isSingleFile) {
            //test.js对应test_config.json
            PFiles.getNameWithoutExtension(source) + "_config.json"
        } else ProjectConfig.CONFIG_FILE_NAME
    }

    /**
     * 从viewModel保存配置
     */
    fun syncToProjectConfig() {
        if (
            projectConfig.mainScript.isNullOrEmpty()
            && source.isNotEmpty()
            && PFiles.isFile(source)
        ) {
            projectConfig.mainScript = File(source).name
        }

        val viewModel = this
        projectConfig.apply {
            sourcePath = viewModel.sourcePath
            projectDirectory = directory!!
            outputPath = viewModel.outputPath
            displaySplash = viewModel.displaySplash
            assets = updateAssets(assets)
            libs = updateLibs(libs).toMutableList()
            abis = updateAbiList(abis).toMutableList()
            if (ignoredDirs.isEmpty()) ignoredDirs = listOf(buildDir)
            name = viewModel.appName
            versionCode = viewModel.versionCode.toInt()
            versionName = viewModel.versionName
            packageName = viewModel.packageName
            mainScript = viewModel.mainScriptFile
            icon = viewModel.icon?.toRelativePathOrString()
            launchConfig.apply {
                isStableMode = viewModel.isStableMode
                isHideLauncher = viewModel.isHideLauncher
                isHideLogs = viewModel.isHideLogs
                isVolumeUpControl = viewModel.isVolumeUpControl
                splashText = viewModel.splashText
                splashIcon = viewModel.splashIcon?.toRelativePathOrString()
                serviceDesc = viewModel.serviceDesc
                permissions = updatePermissions(permissions)
            }
        }
    }

    private fun Uri.toRelativePathOrString(): String {
        val uriString = this.toString()
        if (uriString.startsWith("file://")) {
            val path =
                URLDecoder.decode(uriString.replace("file://", ""), Charsets.UTF_8.toString())
            if (path.startsWith(directory!!)) {
                return path.replace(Regex("^${directory!!}/"), "")
            }
        }
        return uriString
    }

    private fun updateAssets(oldAsset: List<Asset>): List<Asset> {
        val assetsList = oldAsset.toMutableList()
        if (isRequiredMlKitOCR) {
            assetsList.addIfNotExist(
                Asset(
                    form = Constant.Protocol.ASSETS + Constant.Assets.GOOGLE_ML_KIT_OCR,
                    to = Constant.Assets.GOOGLE_ML_KIT_OCR
                )
            )
        }
        if (!isSingleFile) {
            assetsList.addIfNotExist(
                Asset(
                    form = directory!!,
                    to = Constant.Assets.PROJECT
                )
            )
        }
        return assetsList.distinct()
    }

    private fun updateAbiList(oldAbis: List<String>): List<String> {
        val newAbis = abiList.split(",").map { it.trim() }
        return oldAbis.toMutableList().apply { addAllIfNotExist(newAbis) }.distinct()
    }

    private fun updateLibs(oldLibs: List<String>): List<String> {
        val libList = oldLibs.toMutableList()
        if (isRequiredOpenCv) libList.addAllIfNotExist(Constant.Libraries.OPEN_CV)
        if (isRequiredMlKitOCR) libList.addAllIfNotExist(Constant.Libraries.GOOGLE_ML_KIT_OCR)
        if (isRequiredTerminalEmulator) libList.addAllIfNotExist(Constant.Libraries.TERMINAL_EMULATOR)
        return libList.distinct()
    }

    private fun updatePermissions(oldPermissions: List<String>): List<String> {
        val permissionList = oldPermissions.toMutableList()
        if (isRequiredAccessibilityServices) permissionList.addIfNotExist(Constant.Permissions.ACCESSIBILITY_SERVICES)
        if (isRequiredBackgroundStart) permissionList.addIfNotExist(Constant.Permissions.BACKGROUND_START)
        if (isRequiredDrawOverlay) permissionList.addIfNotExist(Constant.Permissions.DRAW_OVERLAY)
        return permissionList.distinct()
    }


    private fun getMainScriptName(): String {
        return if (source.endsWith(".js")) {
            PFiles.getName(source)
        } else "main.js"
    }

    private fun getUri(uriString: String): Uri {
        return parseUriOrNull(uriString) ?: File(directory, uriString).toUri()
    }

    private fun setAbis(projectConfig: ProjectConfig) {
        abiList = if (Constant.Abi.abis.any { projectConfig.abis.contains(it) }) {
            projectConfig.abis.joinToString(", ")
        } else Constant.Abi.abis.joinToString(", ")
    }

    private fun setAssetsAndLibs(projectConfig: ProjectConfig) {
        var isRequiredMlKitOCRLibs = false
        var isRequiredMlKitOCRModels = false
        projectConfig.libs.let {
            when {
                it.containsAll(Constant.Libraries.GOOGLE_ML_KIT_OCR) -> isRequiredMlKitOCRLibs = true
                it.containsAll(Constant.Libraries.OPEN_CV) -> isRequiredOpenCv = true
            }
        }
        projectConfig.assets.forEach {
            if (it.form == "${Constant.Protocol.ASSETS}${Constant.Assets.GOOGLE_ML_KIT_OCR}") {
                isRequiredMlKitOCRModels = true
            }
        }

        isRequiredMlKitOCR = isRequiredMlKitOCRLibs && isRequiredMlKitOCRModels
    }

    private fun setPermissions(projectConfig: ProjectConfig) {
        projectConfig.launchConfig.permissions.forEach {
            when (it) {
                Constant.Permissions.ACCESSIBILITY_SERVICES -> isRequiredAccessibilityServices =
                    true
                Constant.Permissions.BACKGROUND_START -> isRequiredBackgroundStart = true
                Constant.Permissions.DRAW_OVERLAY -> isRequiredDrawOverlay = true
            }
        }
    }

    private suspend fun saveLogo() {
        val uri = icon?.let { saveIcon(it, "logo")?.toUri() }
        icon = uri
        projectConfig.icon = uri?.toRelativePathOrString()
    }


    private suspend fun saveSplashIcon() {
        val uri = splashIcon?.let {
            saveIcon(it, "splashIcon")?.toUri()
        }
        splashIcon = uri
        projectConfig.launchConfig.splashIcon = uri?.toRelativePathOrString()
    }


    private suspend fun saveIcon(uri: Uri, iconName: String): File? {
        val path = getIconPath(iconName)
        val file = File(directory!!, path)
        if (file.toUri().toString() != uri.toString()) {
            return saveIcon(app, uri, file)
        }
        return file
    }

    private fun getIconPath(iconName: String): String {
        var iconName1 = iconName
        if (!iconName1.endsWith(".png")) iconName1 += ".png"
        return "${getResourcePath()}/$iconName1"
    }

    private fun getResourcePath(isSingleFile: Boolean = this.isSingleFile): String {
        return if (isSingleFile) {
            "res_${PFiles.getNameWithoutExtension(source)}"
        } else "res"
    }

    private fun setOutputPath() {
        oldProjectConfig?.outputPath?.let {
            outputPath = it
        } ?: kotlin.run {
            var dir: String = if (isSingleFile) directory!!
            else File(source, projectConfig.buildDir).path
            if (dir.startsWith(app.filesDir.path)) {
                dir = PFiles.join(Pref.getScriptDirPath(), "build")
            }
            outputPath = dir
        }
    }

    private fun checkPackageNameValid(text: String): Boolean =
        REGEX_PACKAGE_NAME.matches(text)

    private suspend fun onBuildFailed(error: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(
                app,
                app.getString(R.string.text_build_failed) + error.message,
                Toast.LENGTH_SHORT
            ).show()
            Log.e(BuildActivity.TAG, "Build failed", error)
        }
    }

    private suspend fun onBuildSuccessful(outApk: File) {
        withContext(Dispatchers.Main) {
            this@BuildViewModel.outApk = outApk
            isShowBuildSuccessfullyDialog = true
        }
    }

    fun checkInputs(viewModel: BuildViewModel = this): Boolean {
        var inputValid = true
        inputValid = inputValid and checkPackageNameValid(viewModel.packageName)
        inputValid = inputValid and viewModel.sourcePath.isNotEmpty()
        inputValid = inputValid and viewModel.outputPath.isNotEmpty()
        inputValid = inputValid and viewModel.appName.isNotEmpty()
        inputValid = inputValid and viewModel.versionCode.isNotEmpty()
        inputValid = inputValid and viewModel.versionName.isNotEmpty()
        inputValid = inputValid and viewModel.packageName.isNotEmpty()
        return inputValid
    }

}
package org.autojs.autojs.ui.build

import android.app.Activity
import android.content.Context
import android.os.Environment
import android.text.InputType
import android.util.Log
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.stardust.app.DialogUtils
import com.stardust.pio.PFile
import com.stardust.pio.PFiles
import com.stardust.util.IntentUtil
import org.autojs.autojs.Pref
import org.autojs.autoxjs.R
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.theme.dialog.ThemeColorMaterialDialogBuilder
import org.autojs.autojs.ui.compose.widget.ProgressDialog
import org.autojs.autojs.ui.filechooser.FileChooserDialogBuilder
import org.autojs.autojs.ui.shortcut.ShortcutIconSelectResult
import java.io.File

/**
 * @author wilinz
 * @date 2022/5/23
 */
@Composable
fun BuildPage(model: BuildViewModel = viewModel()) {
    val context = LocalContext.current

    var isShowSaveDialog by rememberSaveable {
        mutableStateOf(false)
    }
    BackHandler(!isShowSaveDialog) {
        finish(
            isConfigurationHasChanged = model.isConfigurationHasChanged,
            onIsShowSaveDialogChange = { isShowSaveDialog = it },
            context = context
        )
    }
    AskSaveDialog(
        isShowDialog = isShowSaveDialog,
        isShowSaveAsProject = model.isSingleFile,
        onDismissRequest = { isShowSaveDialog = false },
        onSaveClick = {
            model.saveConfig()
            if (context is Activity) context.finish()
        },
        onSaveAsProjectClick = {
            model.saveAsProject()
            if (context is Activity) context.finish()
        },
        onExitClick = { if (context is Activity) context.finish() }
    )
    ProgressDialog(
        isShowDialog = model.isShowBuildDialog,
        onDismissRequest = { model.isShowBuildDialog = false }) {
        Text(text = model.buildDialogText)
    }
    SuccessDialog(
        isShowDialog = model.isShowBuildSuccessfullyDialog,
        onDismissRequest = { model.isShowBuildSuccessfullyDialog = false },
        outApkPath = model.outApk?.path
    )
    Scaffold(
        topBar = {
            TopBar(model = model, onIsShowSaveDialogChange = { isShowSaveDialog = it })
        },
        floatingActionButtonPosition = FabPosition.End
    ) {
        Column(
            Modifier
                .padding(it)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FileCard(model)
            ConfigCard(model)
            PackagingOptionCard(model)
            RunConfigCard(model)
        }
    }
}

private fun finish(
    isConfigurationHasChanged: Boolean,
    onIsShowSaveDialogChange: (Boolean) -> Unit,
    context: Context
) {
    if (isConfigurationHasChanged) onIsShowSaveDialogChange(true)
    else if (context is Activity) context.finish()
}

@Composable
private fun TopBar(model: BuildViewModel, onIsShowSaveDialogChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    TopAppBar(
        title = { Text(text = stringResource(id = R.string.text_build_apk)) },
        navigationIcon = {
            IconButton(onClick = {
                finish(
                    isConfigurationHasChanged = model.isConfigurationHasChanged,
                    onIsShowSaveDialogChange = onIsShowSaveDialogChange,
                    context = context
                )
            }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(id = R.string.desc_back)
                )
            }
        },
        actions = {
            CompositionLocalProvider(
                LocalContentAlpha provides ContentAlpha.high,
            ) {
                var isShowDialog by rememberSaveable {
                    mutableStateOf(false)
                }
                IconButton(onClick = {
                    if (model.isSingleFile) isShowDialog = true
                    else model.saveConfig()
                }) {
                    Icon(
                        painterResource(id = R.drawable.ic_save),
                        contentDescription = stringResource(id = R.string.text_save)
                    )
                }
                SaveDialog(
                    isShowDialog = isShowDialog,
                    onDismissRequest = { isShowDialog = false },
                    onSaveAsProjectClick = { model.saveAsProject();isShowDialog = false },
                    onSaveClick = { model.saveConfig();isShowDialog = false })
            }
        }
    )
}

@Composable
private fun PackagingOptionCard(
    model: BuildViewModel
) {
    val context = LocalContext.current
    Card() {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(text = stringResource(R.string.text_packaging_options))
            NextActionTextField(
                value = model.abiList,
                onValueChange = { model.abiList = it },
                label = { Text(text = stringResource(id = R.string.text_abi)) }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = model.isRequiredOpenCv,
                    onCheckedChange = { model.isRequiredOpenCv = it })
                Text(text = stringResource(id = R.string.text_required_opencv))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = model.isRequiredMlKitOCR,
                    onCheckedChange = { model.isRequiredMlKitOCR = it })
                Text(text = stringResource(id = R.string.text_required_google_mlkit_ocr))
            }
            //目前必须为true
            /*Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = model.isRequiredTerminalEmulator,
                    onCheckedChange = { model.isRequiredTerminalEmulator = it })
                Text(text = stringResource(id = R.string.text_required_terminal_emulator))
            }*/
        }
    }
}

@Composable
private fun RunConfigCard(model: BuildViewModel) {
    val context = LocalContext.current
    val selectIconLauncher = rememberLauncherForActivityResult(
        contract = ShortcutIconSelectResult(),
        onResult = {
            it?.let { model.splashIcon = it }
        }
    )

    Card() {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(text = stringResource(id = R.string.text_run_config))
            NextActionTextField(
                value = model.mainScriptFile,
                onValueChange = { model.mainScriptFile = it },
                label = { Text(text = stringResource(id = R.string.text_main_file_name)) },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = model.isHideLauncher,
                    onCheckedChange = { model.isHideLauncher = it })
                Text(text = stringResource(id = R.string.text_hideLaucher))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = model.isHideLogs,
                    onCheckedChange = { model.isHideLogs = it })
                Text(text = stringResource(id = R.string.text_hideLogs))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = model.isVolumeUpControl,
                    onCheckedChange = { model.isVolumeUpControl = it })
                Text(text = stringResource(id = R.string.text_volumeUpcontrol))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = model.isRequiredAccessibilityServices,
                    onCheckedChange = { model.isRequiredAccessibilityServices = it })
                Text(text = stringResource(id = R.string.text_required_accessibility_service))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = model.isRequiredBackgroundStart,
                    onCheckedChange = { model.isRequiredBackgroundStart = it })
                Text(text = stringResource(id = R.string.text_required_background_start))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = model.isRequiredDrawOverlay,
                    onCheckedChange = { model.isRequiredDrawOverlay = it })
                Text(text = stringResource(id = R.string.text_required_draw_overlay))
            }
            MyTextField(
                value = model.splashText,
                onValueChange = { model.splashText = it },
                label = { Text(text = stringResource(id = R.string.text_splash_text)) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = stringResource(id = R.string.text_splash_icon))

            val modifier = Modifier
                .padding(8.dp)
                .size(64.dp)
                .clickable {
                    selectIconLauncher.launch(null)
                }

            if (model.splashIcon == null) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_add_white_48dp),
                    contentDescription = stringResource(R.string.apk_icon),
                    modifier = modifier,
                    tint = MaterialTheme.colors.primary
                )
            } else {
                Image(
                    painter = rememberAsyncImagePainter(model = model.splashIcon),
                    contentDescription = stringResource(R.string.apk_icon),
                    modifier = modifier,
                )
            }


            MyTextField(
                value = model.serviceDesc,
                onValueChange = { model.serviceDesc = it },
                label = { Text(text = stringResource(id = R.string.text_service_desc_text)) },
            )
        }
    }
}

@Composable
private fun ConfigCard(model: BuildViewModel) {
    val context = LocalContext.current
    val selectIconLauncher = rememberLauncherForActivityResult(
        contract = ShortcutIconSelectResult(),
        onResult = {
            it?.let { model.icon = it }
        }
    )

    Card() {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(text = stringResource(id = R.string.text_config))
            NextActionTextField(
                value = model.appName,
                onValueChange = { model.appName = it },
                label = { Text(text = stringResource(id = R.string.text_app_name)) }
            )
            NextActionTextField(
                value = model.packageName,
                onValueChange = { model.packageName = it },
                label = { Text(text = stringResource(id = R.string.text_package_name)) }
            )
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    NextActionTextField(
                        value = model.versionName,
                        onValueChange = { model.versionName = it },
                        label = { Text(text = stringResource(id = R.string.text_version_name)) }
                    )

                    val regex = remember {
                        Regex("^$|^[0-9]{0,20}$")
                    }
                    NextActionTextField(
                        value = model.versionCode,
                        onValueChange = { if (regex.matches(it)) model.versionCode = it },
                        label = { Text(text = stringResource(id = R.string.text_version_code)) }
                    )
                }
                Column(Modifier.padding(top = 8.dp)) {
                    Text(text = stringResource(id = R.string.text_icon))
                    Box {
                        val modifier = Modifier
                            .padding(8.dp)
                            .size(64.dp)
                            .align(Alignment.Center)
                            .clickable {
                                selectIconLauncher.launch(null)
                            }

                        if (model.icon == null) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_add_white_48dp),
                                contentDescription = stringResource(R.string.apk_icon),
                                modifier = modifier,
                                tint = MaterialTheme.colors.primary
                            )
                        } else {
                            Image(
                                painter = rememberAsyncImagePainter(model = model.icon),
                                contentDescription = stringResource(R.string.apk_icon),
                                modifier = modifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileCard(model: BuildViewModel) {
    val context = LocalContext.current
    Card() {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(text = stringResource(id = R.string.text_file))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MyTextField(
                    value = model.sourcePath,
                    onValueChange = { model.sourcePath = it },
                    label = { Text(text = stringResource(id = R.string.text_source_file_path)) },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        selectSourceFilePath(
                            context = context,
                            scriptPath = model.sourcePath,
                            onResult = { model.sourcePath = it.absolutePath },
                        )
                    },
                ) {
                    Text(text = stringResource(id = R.string.text_select))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MyTextField(
                    value = model.outputPath,
                    onValueChange = { model.outputPath = it },
                    label = { Text(text = stringResource(id = R.string.text_output_apk_path)) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    selectOutputDirPath(
                        context = context,
                        outputPath = model.outputPath,
                        onResult = { model.outputPath = it.absolutePath },
                    )
                }) {
                    Text(text = stringResource(id = R.string.text_select))
                }
            }

//            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
//                MyTextField(
//                    value = model.customOcrModelPath,
//                    onValueChange = { model.customOcrModelPath = it },
//                    label = { Text(text = stringResource(id = R.string.text_custom_ocr_model_path)) },
//                    modifier = Modifier.weight(1f),
//                )
//                TextButton(onClick = {
//                    selectCustomOcrModelPath(
//                        context = context,
//                        scriptPath = model.sourcePath,
//                        onResult = { model.customOcrModelPath = it.absolutePath },
//                    )
//                }) {
//                    Text(text = stringResource(id = R.string.text_select))
//                }
//            }

        }
    }
}

@Composable
fun NextActionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    maxLines: Int = Int.MAX_VALUE,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape =
        MaterialTheme.shapes.small.copy(bottomEnd = ZeroCornerSize, bottomStart = ZeroCornerSize),
    colors: TextFieldColors = TextFieldDefaults.textFieldColors(backgroundColor = Color.Transparent)
) {
    val focusManager = LocalFocusManager.current
    MyTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions.copy(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = {
            focusManager.moveFocus(FocusDirection.Next)
        }),
        singleLine = true,
        maxLines = maxLines,
        interactionSource = interactionSource,
        shape = shape,
        colors = colors
    )
}

@Composable
fun MyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    singleLine: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape =
        MaterialTheme.shapes.small.copy(bottomEnd = ZeroCornerSize, bottomStart = ZeroCornerSize),
    colors: TextFieldColors = TextFieldDefaults.textFieldColors(
        backgroundColor = Color.Transparent,
        textColor = MaterialTheme.colors.onSurface
    )
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        interactionSource = interactionSource,
        shape = shape,
        colors = colors
    )
}

@Composable
fun AddIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    Surface(color = Color(0xff727379), shape = RoundedCornerShape(4.dp)) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            interactionSource = interactionSource
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_add_white_48dp),
                contentDescription = stringResource(R.string.desc_add_icon)
            )
        }
    }
}

private fun selectSourceFilePath(context: Context, scriptPath: String, onResult: (File) -> Unit) {
    val initialDir = File(scriptPath).parent
    FileChooserDialogBuilder(context)
        .title(R.string.text_source_file_path)
        .dir(
            Environment.getExternalStorageDirectory().path,
            initialDir ?: Pref.getScriptDirPath()
        )
        .singleChoice { file: PFile -> onResult(file) }
        .show()
}

private fun selectCustomOcrModelPath(
    context: Context,
    scriptPath: String,
    onResult: (File) -> Unit
) {
    val initialDir =
        File(scriptPath).parent?.takeIf { File(it).exists() } ?: Pref.getScriptDirPath()
    FileChooserDialogBuilder(context)
        .title(R.string.text_custom_ocr_model_path)
        .dir(initialDir)
        .chooseDir()
        .singleChoice { file: PFile -> onResult(file) }
        .show()
}

private fun selectOutputDirPath(context: Context, outputPath: String, onResult: (File) -> Unit) {
    val initialDir =
        if (File(outputPath).exists()) outputPath else Pref.getScriptDirPath()
    FileChooserDialogBuilder(context)
        .title(R.string.text_output_apk_path)
        .dir(initialDir)
        .chooseDir()
        .singleChoice { dir: PFile -> onResult(dir) }
        .show()
}

@Composable
fun SuccessDialog(
    isShowDialog: Boolean,
    onDismissRequest: () -> Unit,
    outApkPath: String?
) {
    val context = LocalContext.current
    if (isShowDialog && outApkPath != null) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = stringResource(id = R.string.text_build_successfully)) },
            text = {
                Text(text = stringResource(id = R.string.format_build_successfully, outApkPath))
            },
            confirmButton = {
                TextButton(onClick = {
                    onDismissRequest()
                    IntentUtil.installApkOrToast(
                        context,
                        outApkPath,
                        AppFileProvider.AUTHORITY
                    )
                }) {
                    Text(text = stringResource(id = R.string.text_install))
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismissRequest() }) {
                    Text(text = stringResource(id = R.string.text_cancel))
                }
            }
        )
    }
}

@Composable
fun AskSaveDialog(
    isShowDialog: Boolean,
    isShowSaveAsProject: Boolean,
    onDismissRequest: () -> Unit,
    onSaveClick: () -> Unit,
    onSaveAsProjectClick: () -> Unit,
    onExitClick: () -> Unit
) {
    if (isShowDialog) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(
                    text = stringResource(id = R.string.text_alert),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.edit_exit_without_save_warn),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            buttons = {
                Column {
                    Divider()
                    TextButton(onClick = onSaveClick, modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(id = R.string.text_save_and_exit))
                    }
                    if (isShowSaveAsProject) {
                        Divider()
                        TextButton(
                            onClick = onSaveAsProjectClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = stringResource(id = R.string.text_save_as_project_and_exit))
                        }
                    }
                    Divider()
                    TextButton(onClick = onExitClick, modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(id = R.string.text_exit_directly))
                    }
                    Divider()
                    TextButton(onClick = onDismissRequest, modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(id = R.string.cancel))
                    }
                }
            }
        )
    }
}

@Composable
fun SaveDialog(
    isShowDialog: Boolean,
    onDismissRequest: () -> Unit,
    onSaveAsProjectClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    if (isShowDialog) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(
                    text = stringResource(id = R.string.text_save),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.text_select_save_mode),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            buttons = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Divider()
                    TextButton(onClick = onSaveClick, modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(id = R.string.text_save))
                    }
                    Divider()
                    TextButton(onClick = onSaveAsProjectClick, modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(id = R.string.text_save_as_project))
                    }
                    Divider()
                    TextButton(onClick = onDismissRequest, modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(id = R.string.text_cancel))
                    }
                }
            }
        )
    }
}
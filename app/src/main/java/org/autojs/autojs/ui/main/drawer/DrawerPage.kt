package org.autojs.autojs.ui.main.drawer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import org.autojs.autojs.ui.build.MyTextField
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.preference.PreferenceManager
import coil.compose.rememberAsyncImagePainter
import com.stardust.app.GlobalAppContext
import com.stardust.app.permission.DrawOverlaysPermission
import com.stardust.app.permission.DrawOverlaysPermission.launchCanDrawOverlaysSettings
import com.stardust.enhancedfloaty.FloatyService
import com.stardust.notification.NotificationListenerService
import com.stardust.util.IntentUtil
import com.stardust.view.accessibility.AccessibilityService
import kotlinx.coroutines.*
import org.autojs.autojs.Pref
import org.autojs.autojs.autojs.AutoJs
import org.autojs.autojs.devplugin.DevPlugin
import org.autojs.autojs.external.foreground.ForegroundService
import org.autojs.autojs.tool.AccessibilityServiceTool
import org.autojs.autojs.tool.WifiTool
import org.autojs.autojs.ui.compose.theme.AutoXJsTheme
import org.autojs.autojs.ui.compose.widget.MyIcon
import org.autojs.autojs.ui.compose.widget.MySwitch
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autoxjs.R

private const val URL_DEV_PLUGIN = "https://github.com/kkevsekk1/Auto.js-VSCode-Extension"

@Composable
fun DrawerPage() {
    val context = LocalContext.current
    rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxSize()
    ) {
        Spacer(
            modifier = Modifier
                .windowInsetsTopHeight(WindowInsets.statusBars)
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(
                    painter = rememberAsyncImagePainter(R.drawable.autojs_logo1),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                )
            }
            AccessibilityServiceSwitch()
            NotificationUsageRightSwitch()
            FloatingWindowSwitch()
            ConnectComputerSwitch()
        }
        Spacer(
            modifier = Modifier
                .height(1.dp)
                .fillMaxWidth()
                .background(AutoXJsTheme.colors.divider)
        )
        BottomButtons()
        Spacer(
            modifier = Modifier
                .windowInsetsBottomHeight(WindowInsets.navigationBars)
        )
    }
}

@Composable
private fun BottomButtons() {
    val context = LocalContext.current
    var lastBackPressedTime = remember {
        0L
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        TextButton(
            modifier = Modifier.weight(1f), onClick = {
                val currentTime = System.currentTimeMillis()
                val interval = currentTime - lastBackPressedTime
                if (interval > 2000) {
                    lastBackPressedTime = currentTime
                    Toast.makeText(
                        context,
                        context.getString(R.string.text_press_again_to_exit),
                        Toast.LENGTH_SHORT
                    ).show()
                } else exitCompletely(context)
            },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colors.onBackground)
        ) {
            MyIcon(imageVector = Icons.Default.ExitToApp, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(id = R.string.text_exit))
        }
    }
}

fun exitCompletely(context: Context) {
    if (context is Activity) context.finish()
    FloatyWindowManger.hideCircularMenu()
    ForegroundService.stop(context)
    context.stopService(Intent(context, FloatyService::class.java))
    AutoJs.getInstance().scriptEngineService.stopAll()
}

@Composable
private fun ConnectComputerSwitch() {
    val context = LocalContext.current
    var enable by remember {
        mutableStateOf(DevPlugin.isActive)
    }
    var showDialog by remember {
        mutableStateOf(false)
    }

    val scope = rememberCoroutineScope()
    LaunchedEffect(key1 = Unit, block = {
        DevPlugin.connectState.collect {
            withContext(Dispatchers.Main) {
                when (it.state) {
                    DevPlugin.State.CONNECTED -> enable = true
                    DevPlugin.State.DISCONNECTED -> enable = false
                }
            }
        }
    })
    SwitchItem(
        icon = {
            MyIcon(
                painterResource(id = R.drawable.ic_debug),
                null
            )
        },
        text = {
            Text(
                text = stringResource(
                    id = if (!enable) R.string.text_connect_computer
                    else R.string.text_connected_to_computer
                )
            )
        },
        checked = enable,
        onCheckedChange = {
            if (it) {
                showDialog = true
            } else {
                scope.launch { DevPlugin.close() }
            }
        }
    )
    if (showDialog) {
        ConnectComputerDialog(
            onDismissRequest = { showDialog = false }
        )
    }

}

@Composable
private fun ConnectComputerDialog(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = { onDismissRequest() }) {
        var host by remember {
            mutableStateOf(Pref.getServerAddressOrDefault(WifiTool.getRouterIp(context)))
        }
        Surface(shape = RoundedCornerShape(4.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(text = stringResource(id = R.string.text_server_address))
                MyTextField(
                    value = host,
                    onValueChange = { host = it },
                    modifier = Modifier.padding(vertical = 16.dp),
                    placeholder = {
                        Text(text = host)
                    }
                )
                Row(Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            IntentUtil.browse(context, URL_DEV_PLUGIN)
                        }
                    ) {
                        Text(text = stringResource(id = R.string.text_help))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        onDismissRequest()
                        Pref.saveServerAddress(host)
                        connectServer(getUrl(host))
                    }) {
                        Text(text = stringResource(id = R.string.ok))
                    }
                }
            }
        }

    }
}

@OptIn(DelicateCoroutinesApi::class)
@SuppressLint("HardwareIds")
private fun connectServer(
    url: String,
) {
    GlobalScope.launch { DevPlugin.connect(url) }
}

private fun getUrl(host: String): String {
    var url1 = host
    if (!url1.matches(Regex("^(ws|wss)://.*"))) {
        url1 = "ws://${url1}"
    }
    if (!url1.matches(Regex("^.+://.+?:.+$"))) {
        url1 += ":${DevPlugin.SERVER_PORT}"
    }
    return url1
}

@Composable
private fun FloatingWindowSwitch() {
    val context = LocalContext.current

    var isFloatingWindowShowing by remember {
        mutableStateOf(FloatyWindowManger.isCircularMenuShowing())
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            if (DrawOverlaysPermission.isCanDrawOverlays(context)) FloatyWindowManger.showCircularMenu()
            isFloatingWindowShowing = FloatyWindowManger.isCircularMenuShowing()
        }
    )
    SwitchItem(
        icon = {
            MyIcon(
                painterResource(id = R.drawable.ic_overlay),
                null
            )
        },
        text = { Text(text = stringResource(id = R.string.text_floating_window)) },
        checked = isFloatingWindowShowing,
        onCheckedChange = {
            if (isFloatingWindowShowing) {
                FloatyWindowManger.hideCircularMenu()
            } else {
                if (DrawOverlaysPermission.isCanDrawOverlays(context)) FloatyWindowManger.showCircularMenu()
                else launcher.launchCanDrawOverlaysSettings(context.packageName)
            }
            isFloatingWindowShowing = FloatyWindowManger.isCircularMenuShowing()
            Pref.setFloatingMenuShown(isFloatingWindowShowing)
        }
    )
}

@Composable
private fun NotificationUsageRightSwitch() {
    LocalContext.current
    var isNotificationListenerEnable by remember {
        mutableStateOf(notificationListenerEnable())
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            isNotificationListenerEnable = notificationListenerEnable()
        }
    )
    SwitchItem(
        icon = {
            MyIcon(
                Icons.Default.Notifications,
                null
            )
        },
        text = { Text(text = stringResource(id = R.string.text_notification_permission)) },
        checked = isNotificationListenerEnable,
        onCheckedChange = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                launcher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } else isNotificationListenerEnable = it
        }
    )
}

private fun notificationListenerEnable(): Boolean = NotificationListenerService.instance != null

@Composable
private fun AccessibilityServiceSwitch() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDialog by remember {
        mutableStateOf(false)
    }
    var isAccessibilityServiceEnabled by remember {
        mutableStateOf(AccessibilityServiceTool.isAccessibilityServiceEnabled(context))
    }
    val accessibilitySettingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (AccessibilityServiceTool.isAccessibilityServiceEnabled(context)) {
                isAccessibilityServiceEnabled = true
            } else {
                isAccessibilityServiceEnabled = false
                Toast.makeText(
                    context,
                    R.string.text_accessibility_service_is_not_enable,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    SwitchItem(
        icon = {
            MyIcon(
                Icons.Default.Settings,
                contentDescription = null,
            )
        },
        text = { Text(text = stringResource(id = R.string.text_accessibility_service)) },
        checked = isAccessibilityServiceEnabled,
        onCheckedChange = {
            if (!isAccessibilityServiceEnabled) {
                showDialog = true
            } else {
                isAccessibilityServiceEnabled = !AccessibilityService.disable()
            }
        }
    )

    if (showDialog) {
        AlertDialog(
            title = { Text(text = stringResource(id = R.string.text_need_to_enable_accessibility_service)) },
            onDismissRequest = { showDialog = false },
            text = {
                Text(
                    text = stringResource(
                        R.string.explain_accessibility_permission,
                        GlobalAppContext.appName
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    accessibilitySettingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) {
                    Text(text = stringResource(id = R.string.text_go_to_open))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(text = stringResource(id = R.string.text_cancel))
                }
            },
        )
    }
}

@Composable
fun SwitchItem(
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.padding(8.dp)) {
            icon()
        }
        Box(modifier = Modifier.weight(1f)) {
            text()
        }
        MySwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
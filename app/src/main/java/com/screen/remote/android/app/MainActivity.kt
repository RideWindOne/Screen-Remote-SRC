package com.screen.remote.android.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import com.screen.remote.android.core.common.util.ApiCompatHelper
import com.screen.remote.android.core.common.util.compat.canDrawOverlaysCompat
import com.screen.remote.android.core.common.manager.LanguageManager
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.data.datastore.PreferencesManager
import com.screen.remote.android.feature.session.ui.MainScreen
import com.screen.remote.android.feature.remote.input.RemoteHardwareKeyEventHandler
import com.screen.remote.android.feature.remote.input.RemoteHardwareKeyEventHost
import com.screen.remote.android.feature.settings.viewmodel.SettingsViewModel
import com.screen.remote.android.feature.settings.ui.DebugLogOverlay
import com.screen.remote.android.core.designsystem.theme.ScreenRemoteTheme
import com.screen.remote.android.app.deeplink.ScreenRemoteDeepLink
import com.screen.remote.android.app.deeplink.parseScreenRemoteDeepLink
import com.screen.remote.android.infrastructure.adb.mdns.MdnsSessionDiscoveryManager

class MainActivity : ComponentActivity(), RemoteHardwareKeyEventHost {
    private val overlayPermissionGranted = mutableStateOf(false)
    private val pendingDeepLink = mutableStateOf<ScreenRemoteDeepLink?>(null)
    private var remoteHardwareKeyEventHandler: RemoteHardwareKeyEventHandler? = null

    override fun setRemoteHardwareKeyEventHandler(handler: RemoteHardwareKeyEventHandler?) {
        remoteHardwareKeyEventHandler = handler
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean = remoteHardwareKeyEventHandler?.onKeyEvent(event) == true || super.onKeyDown(keyCode, event)

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean = remoteHardwareKeyEventHandler?.onKeyEvent(event) == true || super.onKeyUp(keyCode, event)

    override fun onResume() {
        super.onResume()
        overlayPermissionGranted.value = canDrawOverlaysCompat(this)
        MdnsSessionDiscoveryManager.get().onAppForegrounded()
    }

    override fun onPause() {
        MdnsSessionDiscoveryManager.get().onAppBackgrounded()
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptDeepLink(intent)

        runCatching {
            val startupHooksClass = Class.forName("com.screen.remote.android.debug.DebugUsbAdbCommands")
            val method = startupHooksClass.getMethod("handleActivityLaunch", ComponentActivity::class.java)
            method.invoke(null, this)
        }

        // 设置 Edge-to-Edge（手动管理，不使用 enableEdgeToEdge()）
        ApiCompatHelper.setDecorFitsSystemWindows(window, decorFitsSystemWindows = false)

        setContent {
            // 获取 SettingsViewModel 以读取主题和语言设置
            val preferencesManager = PreferencesManager(this)
            val settingsViewModel = ViewModelProvider(
                this,
                SettingsViewModel.provideFactory(preferencesManager)
            )[SettingsViewModel::class.java]
            val settings by settingsViewModel.settings.collectAsState()
            val runtimeLoggingSuppressed by LogManager.runtimeLoggingSuppressed.collectAsState()
            val canDrawOverlays by overlayPermissionGranted
            val deepLink by pendingDeepLink
            val overlayPermissionLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    overlayPermissionGranted.value = canDrawOverlaysCompat(this)
                }

            LaunchedEffect(settings.enableDebugMode) {
                val permissionGranted = canDrawOverlaysCompat(this@MainActivity)
                overlayPermissionGranted.value = permissionGranted
                if (settings.enableDebugMode && !permissionGranted) {
                    overlayPermissionLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:$packageName".toUri(),
                        ),
                    )
                }
            }
            
            // 初始化语言管理器
            LaunchedEffect(settings.language) {
                LanguageManager.setLanguage(settings.language)
            }
            
            ScreenRemoteTheme(themeMode = settings.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MainScreen(
                            deepLink = deepLink,
                            onDeepLinkConsumed = { pendingDeepLink.value = null },
                        )
                        DebugLogOverlay(
                            enabled = settings.enableDebugMode && canDrawOverlays && !runtimeLoggingSuppressed,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptDeepLink(intent)
    }

    private fun acceptDeepLink(source: Intent) {
        val data = source.dataString ?: return
        val parsed = parseScreenRemoteDeepLink(data)
        if (parsed == null) {
            LogManager.w("DeepLink", "Unsupported Screen Remote URL: $data")
            return
        }
        pendingDeepLink.value = parsed
        setIntent(Intent(source).setData(null))
        LogManager.i("DeepLink", "Accepted Screen Remote URL: $data")
    }
}

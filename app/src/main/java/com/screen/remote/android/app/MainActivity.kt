package com.screen.remote.android.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
import androidx.lifecycle.ViewModelProvider
import com.screen.remote.android.core.common.util.ApiCompatHelper
import com.screen.remote.android.core.common.manager.LanguageManager
import com.screen.remote.android.core.data.datastore.PreferencesManager
import com.screen.remote.android.feature.session.ui.MainScreen
import com.screen.remote.android.feature.settings.viewmodel.SettingsViewModel
import com.screen.remote.android.feature.settings.ui.DebugLogOverlay
import com.screen.remote.android.core.designsystem.theme.ScreenRemoteTheme

class MainActivity : ComponentActivity() {
    private val overlayPermissionGranted = mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        overlayPermissionGranted.value = Settings.canDrawOverlays(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            val canDrawOverlays by overlayPermissionGranted
            val overlayPermissionLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    overlayPermissionGranted.value = Settings.canDrawOverlays(this)
                }

            LaunchedEffect(settings.enableDebugMode) {
                val permissionGranted = Settings.canDrawOverlays(this@MainActivity)
                overlayPermissionGranted.value = permissionGranted
                if (settings.enableDebugMode && !permissionGranted) {
                    overlayPermissionLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName"),
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
                        MainScreen()
                        DebugLogOverlay(
                            enabled = settings.enableDebugMode && canDrawOverlays,
                        )
                    }
                }
            }
        }
    }
}

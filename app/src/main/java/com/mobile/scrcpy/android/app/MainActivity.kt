package com.mobile.scrcpy.android.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.mobile.scrcpy.android.core.common.util.ApiCompatHelper
import com.mobile.scrcpy.android.core.common.manager.LanguageManager
import com.mobile.scrcpy.android.core.data.datastore.PreferencesManager
import com.mobile.scrcpy.android.feature.session.ui.feature.session.MainScreen
import com.mobile.scrcpy.android.feature.settings.viewmodel.SettingsViewModel
import com.mobile.scrcpy.android.core.designsystem.theme.ScreenRemoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            
            // 初始化语言管理器
            LaunchedEffect(settings.language) {
                LanguageManager.setLanguage(settings.language)
            }
            
            ScreenRemoteTheme(themeMode = settings.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

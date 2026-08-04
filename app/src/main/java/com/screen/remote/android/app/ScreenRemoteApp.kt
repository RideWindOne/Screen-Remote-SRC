package com.screen.remote.android.app

import android.app.Application
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.HapticFeedbackManager
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.data.datastore.LocalDecoderCache
import com.screen.remote.android.core.data.datastore.PreferencesManager
import com.screen.remote.android.core.telemetry.TelemetryJournal
import com.screen.remote.android.core.telemetry.TelemetryManager
import com.screen.remote.android.core.telemetry.TelemetryPreferences
import com.screen.remote.android.infrastructure.adb.AdbRuntimeProvider
import com.screen.remote.android.infrastructure.adb.connection.AdbConnectionManager
import com.screen.remote.android.infrastructure.adb.key.core.adb.AdbKeyManager
import com.screen.remote.android.infrastructure.adb.mdns.MdnsSessionDiscoveryManager
import dadb.android.runtime.ExperimentalDadbAndroidApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalDadbAndroidApi::class)
class ScreenRemoteApp : Application() {
    lateinit var adbConnectionManager: AdbConnectionManager
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 初始化日志管理器（启用文件日志）
        LogManager.init(this, true)
        TelemetryJournal.init(this)
        appScope.launch {
            PreferencesManager(this@ScreenRemoteApp).settingsFlow.collectLatest { settings ->
                LogManager.applySettings(settings)
            }
        }
        appScope.launch {
            TelemetryPreferences(this@ScreenRemoteApp).stateFlow.collectLatest { telemetry ->
                TelemetryJournal.setEnabled(telemetry.enabled)
            }
        }
        appScope.launch {
            TelemetryManager.pingOnStartup(this@ScreenRemoteApp)
        }

        // 初始化触感反馈管理器
        HapticFeedbackManager.init(this)

        // 初始化本地解码器缓存
        LocalDecoderCache.init(this)

        // app 显式提供 runtime root，dadb-android 只负责在该目录下工作
        AdbRuntimeProvider.init(
            context = this,
            rootDir = AdbKeyManager.defaultStorageRoot(this),
        )

        // 初始化全局 ADB 连接管理器
        adbConnectionManager = AdbConnectionManager.getInstance(this)
        MdnsSessionDiscoveryManager.initialize(this)

        LogManager.i(LogTags.SCREEN_REMOTE_APP, "Start connecting")
    }

    companion object {
        lateinit var instance: ScreenRemoteApp
            private set
    }
}

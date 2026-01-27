package com.mobile.scrcpy.android.app

import android.app.Application
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.common.manager.HapticFeedbackManager
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnectionManager

class ScreenRemoteApp : Application() {
    lateinit var adbConnectionManager: AdbConnectionManager
        private set
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // 初始化日志管理器（启用文件日志）
        LogManager.init(this, true)
        
        // 初始化触感反馈管理器
        HapticFeedbackManager.init(this)
        
        // 初始化全局 ADB 连接管理器
        adbConnectionManager = AdbConnectionManager.getInstance(this)
        
        LogManager.i(LogTags.SCREEN_REMOTE_APP, "应用启动")
    }
    
    companion object {
        lateinit var instance: ScreenRemoteApp
            private set
    }
}

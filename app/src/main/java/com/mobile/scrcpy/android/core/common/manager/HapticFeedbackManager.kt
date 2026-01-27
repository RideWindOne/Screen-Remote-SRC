package com.mobile.scrcpy.android.core.common.manager

import android.content.Context
import android.util.Log
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.data.datastore.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 全局触感反馈管理器
 * 在应用启动时初始化，避免每次连接都读取设置
 */
object HapticFeedbackManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * 触感反馈开关状态（全局）
     */
    @Volatile
    var isEnabled: Boolean = true
        private set
    
    /**
     * 初始化触感反馈管理器
     * 在 Application.onCreate() 中调用
     */
    fun init(context: Context) {
        scope.launch {
            try {
                // 从设置中读取触感反馈开关状态
                val preferencesManager = PreferencesManager(context)
                val settings = preferencesManager.settingsFlow.first()
                isEnabled = settings.enableFloatingHapticFeedback
                
                Log.d(LogTags.APP, "🔧 触感反馈管理器初始化: ${if (isEnabled) "开启" else "关闭"}")
            } catch (e: Exception) {
                Log.e(LogTags.APP, "❌ 触感反馈管理器初始化失败: ${e.message}", e)
                // 初始化失败时使用默认值（开启）
                isEnabled = true
            }
        }
    }
    
    /**
     * 更新触感反馈开关状态
     * 在设置页面修改时调用
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        Log.d(LogTags.APP, "🔧 触感反馈开关更新: ${if (enabled) "开启" else "关闭"}")
    }
}

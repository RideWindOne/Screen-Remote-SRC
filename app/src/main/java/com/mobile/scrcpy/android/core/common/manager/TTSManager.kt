package com.mobile.scrcpy.android.core.common.manager

import android.annotation.SuppressLint
import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.i18n.CodecTexts
import java.util.Locale

/**
 * TTS 管理器 - 单例模式
 * 用于管理全局 TTS 实例，避免重复初始化
 * 
 * 注意：TTS 仅在需要时（如 AudioCodecTestScreen）才初始化，不在应用启动时初始化
 */
@SuppressLint("StaticFieldLeak") // 使用 applicationContext，不会造成内存泄漏
object TTSManager {
    private var context: Context? = null
    private var ttsInstance: TextToSpeech? = null
    private var isInitialized = false
    private var isInitializing = false
    
    /**
     * 初始化 TTS（懒加载）
     * @param context 应用上下文
     * @param showToast 是否显示 Toast 提示
     * 
     * 注意：仅在需要时调用，不在应用启动时初始化
     */
    fun init(
        context: Context,
        showToast: Boolean = false,
    ) {
        if (isInitialized || isInitializing) {
            return
        }
        
        // 使用 applicationContext 避免内存泄漏
        this.context = context.applicationContext
        isInitializing = true
        
        try {
            ttsInstance = TextToSpeech(this.context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsInstance?.apply {
                        language = Locale.US
                        setSpeechRate(1.0f)
                        setPitch(1.0f)
                    }
                    isInitialized = true
                    LogManager.d(LogTags.TTS_MANAGER, "TTS 初始化成功")
                    
                    if (showToast) {
                        Toast.makeText(
                            this.context,
                            CodecTexts.CODEC_TTS_INIT_SUCCESS.get(),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    LogManager.w(LogTags.TTS_MANAGER, "TTS 初始化失败，可能未安装 TTS 引擎")
                    ttsInstance = null
                    
                    if (showToast) {
                        Toast.makeText(
                            this.context,
                            CodecTexts.CODEC_TTS_INIT_FAILED.get(),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                isInitializing = false
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.TTS_MANAGER, "TTS 初始化异常: ${e.message}", e)
            isInitializing = false
            ttsInstance = null
            
            if (showToast) {
                Toast.makeText(
                    this.context,
                    CodecTexts.CODEC_TTS_INIT_FAILED.get(),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * 获取 TTS 实例
     * @return TTS 实例，如果未初始化则返回 null
     */
    fun getInstance(): TextToSpeech? {
        return if (isInitialized) ttsInstance else null
    }
    
    /**
     * 设置 TTS 监听器
     */
    fun setOnUtteranceProgressListener(listener: UtteranceProgressListener) {
        ttsInstance?.setOnUtteranceProgressListener(listener)
    }
    
    /**
     * 释放 TTS 资源
     * 注意：通常不需要手动调用，除非应用退出
     */
    fun shutdown() {
        try {
            ttsInstance?.shutdown()
            ttsInstance = null
            isInitialized = false
            LogManager.d(LogTags.TTS_MANAGER, "TTS 已释放")
        } catch (e: Exception) {
            LogManager.e(LogTags.TTS_MANAGER, "TTS 释放失败: ${e.message}", e)
        }
    }
    
    /**
     * 检查 TTS 是否已初始化
     */
    fun isReady(): Boolean = isInitialized
}

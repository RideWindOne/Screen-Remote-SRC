package com.mobile.scrcpy.android.feature.remote.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnectionManager
import com.mobile.scrcpy.android.infrastructure.scrcpy.client.feature.scrcpy.ScrcpyClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 设备控制 ViewModel
 * 职责：触摸/按键/文本输入、滑动手势、Shell 命令、屏幕唤醒
 */
class ControlViewModel(
    private val scrcpyClient: ScrcpyClient,
    private val adbConnectionManager: AdbConnectionManager
) : ViewModel() {

    // ============ 按键控制 ============

    suspend fun sendKeyEvent(keyCode: Int): Result<Boolean> {
        return scrcpyClient.sendKeyEvent(keyCode)
    }

    suspend fun sendKeyEvent(keyCode: Int, action: Int, metaState: Int): Result<Boolean> {
        return scrcpyClient.sendKeyEvent(keyCode, action, 0, metaState)
    }

    suspend fun sendText(text: String): Result<Boolean> {
        return scrcpyClient.sendText(text)
    }

    // ============ 触摸控制 ============

    suspend fun sendTouchEvent(
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float = 1.0f
    ): Result<Boolean> {
        return scrcpyClient.sendTouchEvent(action, pointerId, x, y, screenWidth, screenHeight, pressure)
    }

    /**
     * 发送滑动手势
     * @param startX 起始 X 坐标
     * @param startY 起始 Y 坐标
     * @param endX 结束 X 坐标
     * @param endY 结束 Y 坐标
     * @param duration 滑动持续时间（毫秒）
     */
    suspend fun sendSwipeGesture(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Long = 300
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val resolution = scrcpyClient.videoResolution.value
                ?: return@withContext Result.failure(Exception("无法获取视频分辨率"))
            val (screenWidth, screenHeight) = resolution

            // 计算滑动步数（每 16ms 一帧，约 60fps）
            val steps = (duration / 16).toInt().coerceAtLeast(10)
            val pointerId = 0L

            // 发送按下事件
            sendTouchEvent(0, pointerId, startX, startY, screenWidth, screenHeight)
            delay(16)

            // 发送移动事件
            for (i in 1..steps) {
                val progress = i.toFloat() / steps
                val currentX = (startX + (endX - startX) * progress).toInt()
                val currentY = (startY + (endY - startY) * progress).toInt()
                sendTouchEvent(2, pointerId, currentX, currentY, screenWidth, screenHeight)
                delay(16)
            }

            // 发送抬起事件
            sendTouchEvent(1, pointerId, endX, endY, screenWidth, screenHeight)

            Result.success(true)
        } catch (e: Exception) {
            LogManager.e(LogTags.CONTROL_VM, "发送滑动手势失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ============ 屏幕控制 ============

    /**
     * 唤醒远程设备屏幕
     */
    suspend fun wakeUpScreen(): Result<Boolean> {
        return scrcpyClient.wakeUpScreen()
    }

    // ============ Shell 命令 ============

    /**
     * 执行 Shell 命令
     * @param command Shell 命令
     * @return 命令执行结果
     */
    suspend fun executeShellCommand(command: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // 获取当前设备 ID
                val deviceId = scrcpyClient.getCurrentDeviceId()
                    ?: return@withContext Result.failure(Exception("未连接设备"))

                // 获取 ADB 连接
                val connection = adbConnectionManager.getConnection(deviceId)
                    ?: return@withContext Result.failure(Exception("Device connection lost"))

                // 执行 Shell 命令
                connection.executeShell(command)
            } catch (e: Exception) {
                LogManager.e(LogTags.CONTROL_VM, "执行 Shell 命令失败: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    // ============ Factory ============

    companion object {
        fun provideFactory(
            scrcpyClient: ScrcpyClient,
            adbConnectionManager: AdbConnectionManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ControlViewModel(scrcpyClient, adbConnectionManager) as T
            }
        }
    }
}

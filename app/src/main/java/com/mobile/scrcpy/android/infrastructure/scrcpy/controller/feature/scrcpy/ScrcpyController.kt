package com.mobile.scrcpy.android.infrastructure.scrcpy.controller.feature.scrcpy

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.core.i18n.RemoteTexts
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnectionManager
import com.mobile.scrcpy.android.infrastructure.scrcpy.protocol.feature.scrcpy.ScrcpyProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.Socket

/**
 * Scrcpy 控制器 - 负责发送控制消息（触摸、按键、文本）
 */
class ScrcpyController(
    private val adbConnectionManager: AdbConnectionManager,
    private val getDeviceId: () -> String?,
    private val getControlSocket: () -> Socket?,
    private val clearControlSocket: () -> Unit,
    private val localPort: Int
) {

    /**
     * 发送触摸事件（支持多指触摸）
     * 按照 scrcpy 标准控制消息格式
     */
    suspend fun sendTouchEvent(
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float = 1.0f
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val deviceId = getDeviceId() ?: return@withContext Result.failure(
                Exception(AdbTexts.ERROR_DEVICE_NOT_CONNECTED.get())
            )

            // scrcpy 控制消息格式：SC_CONTROL_MSG_TYPE_INJECT_TOUCH_EVENT = 2
            val buffer = ByteArray(32)
            var offset = 0

            buffer[offset++] = ScrcpyProtocol.MSG_TYPE_INJECT_TOUCH_EVENT.toByte()
            buffer[offset++] = action.toByte()
            ScrcpyProtocol.writeLong(buffer, offset, pointerId)
            offset += 8
            ScrcpyProtocol.writeInt(buffer, offset, x)
            offset += 4
            ScrcpyProtocol.writeInt(buffer, offset, y)
            offset += 4
            ScrcpyProtocol.writeShort(buffer, offset, screenWidth)
            offset += 2
            ScrcpyProtocol.writeShort(buffer, offset, screenHeight)
            offset += 2
            val pressureInt = (pressure * 0xFFFF).toInt().coerceIn(0, 0xFFFF)
            ScrcpyProtocol.writeShort(buffer, offset, pressureInt)
            offset += 2
            ScrcpyProtocol.writeInt(buffer, offset, 0) // action_button
            offset += 4
            ScrcpyProtocol.writeInt(buffer, offset, 0) // buttons

            sendControlMessage(buffer, deviceId)
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "发送触摸事件失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 发送按键事件
     */
    suspend fun sendKeyEvent(
        keyCode: Int,
        action: Int = -1, // -1 表示发送完整的按下+释放事件
        repeat: Int = 0,
        metaState: Int = 0
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val deviceId = getDeviceId() ?: return@withContext Result.failure(
            Exception(AdbTexts.ERROR_DEVICE_NOT_CONNECTED.get())
        )

        // ✅ 检查控制连接是否就绪
        if (getControlSocket() == null || getControlSocket()?.isClosed == true) {
            return@withContext Result.failure(Exception(RemoteTexts.ERROR_CONTROL_NOT_READY.get()))
        }

        return@withContext try {
            // 如果 action = -1，发送完整的按键事件（按下+释放）
            if (action == -1) {
                // 发送按下事件
                sendSingleKeyEvent(keyCode, 0, repeat, metaState, deviceId)
                delay(10) // 短暂延迟
                // 发送释放事件
                sendSingleKeyEvent(keyCode, 1, repeat, metaState, deviceId)
                return@withContext Result.success(true)
            } else {
                // 发送单个事件
                return@withContext sendSingleKeyEvent(keyCode, action, repeat, metaState, deviceId)
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "发送按键事件失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun sendSingleKeyEvent(
        keyCode: Int,
        action: Int,
        repeat: Int = 0,
        metaState: Int = 0,
        deviceId: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // scrcpy 控制消息格式：SC_CONTROL_MSG_TYPE_INJECT_KEYCODE = 0
            val buffer = ByteArray(14)
            var offset = 0

            buffer[offset++] = ScrcpyProtocol.MSG_TYPE_INJECT_KEYCODE.toByte()
            buffer[offset++] = action.toByte()
            ScrcpyProtocol.writeInt(buffer, offset, keyCode)
            offset += 4
            ScrcpyProtocol.writeInt(buffer, offset, repeat)
            offset += 4
            ScrcpyProtocol.writeInt(buffer, offset, metaState)

            sendControlMessage(buffer, deviceId)
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "发送按键事件失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 发送文本
     */
    suspend fun sendText(text: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val deviceId = getDeviceId() ?: return@withContext Result.failure(
            Exception(AdbTexts.ERROR_DEVICE_NOT_CONNECTED.get())
        )

        LogManager.d(LogTags.SCRCPY_CLIENT, "发送文本: '$text'")

        return@withContext try {
            // scrcpy 控制消息格式：SC_CONTROL_MSG_TYPE_INJECT_TEXT = 1
            val textBytes = text.toByteArray(Charsets.UTF_8)

            if (textBytes.size > 300) {
                return@withContext Result.failure(Exception(RemoteTexts.ERROR_TEXT_TOO_LONG.get()))
            }

            val buffer = ByteArray(5 + textBytes.size)
            var offset = 0

            buffer[offset++] = ScrcpyProtocol.MSG_TYPE_INJECT_TEXT.toByte()
            ScrcpyProtocol.writeInt(buffer, offset, textBytes.size)
            offset += 4
            System.arraycopy(textBytes, 0, buffer, offset, textBytes.size)

            sendControlMessage(buffer, deviceId)
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "发送文本失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 设置远程设备剪贴板并自动粘贴（支持中文）
     */
    suspend fun setClipboardAndPaste(text: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val deviceId = getDeviceId() ?: return@withContext Result.failure(
            Exception(AdbTexts.ERROR_DEVICE_NOT_CONNECTED.get())
        )

        LogManager.d(LogTags.SCRCPY_CLIENT, "通过剪贴板注入文本: '$text'")

        return@withContext try {
            val connection = adbConnectionManager.getConnection(deviceId)
                ?: return@withContext Result.failure(Exception(AdbTexts.ERROR_DEVICE_CONNECTION_LOST.get()))

            // 方案：使用 ADB 设置剪贴板 + 发送粘贴按键
            // 1. 设置剪贴板内容
            val base64Text = android.util.Base64.encodeToString(
                text.toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP
            )
            val setClipboardCmd = "am broadcast -a clipper.set -e text \"$base64Text\" 2>/dev/null || " +
                    "service call clipboard 1 i32 0 s16 com.android.shell s16 \"$text\""

            val clipResult = connection.executeShell(setClipboardCmd)
            if (clipResult.isFailure) {
                LogManager.w(LogTags.SCRCPY_CLIENT, "设置剪贴板失败，尝试直接粘贴")
            }

            // 2. 发送粘贴按键事件 (Ctrl+V: keycode 279 或使用 KEYCODE_PASTE)
            delay(100) // 等待剪贴板设置完成

            // 发送 KEYCODE_PASTE (279)
            sendKeyEvent(279) // KEYCODE_PASTE

            LogManager.d(LogTags.SCRCPY_CLIENT, "✓ 文本注入成功")
            Result.success(true)
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "注入文本失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 唤醒屏幕（增强版）
     */
    suspend fun wakeUpScreen(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // ✅ 检查连接状态
            if (getControlSocket() == null || getControlSocket()?.isClosed == true) {
                LogManager.w(LogTags.SCRCPY_CLIENT, "⚠️ ${RemoteTexts.ERROR_CONTROL_NOT_READY.get()}")
                return@withContext Result.failure(Exception(RemoteTexts.ERROR_CONTROL_NOT_READY.get()))
            }

            // 方法1: 发送 KEYCODE_WAKEUP (224)
            sendKeyEvent(224) // KEYCODE_WAKEUP
            delay(50)

            LogManager.d(LogTags.SCRCPY_CLIENT, "✅ ${RemoteTexts.SCRCPY_SCREEN_WAKE_SIGNAL_SENT.get()}")
            Result.success(true)
        } catch (e: Exception) {
            LogManager.w(LogTags.SCRCPY_CLIENT, "${RemoteTexts.SCRCPY_WAKE_SCREEN_FAILED.get()}: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 发送控制消息（优先使用 Socket，失败时回退到 ADB）
     */
    private suspend fun sendControlMessage(buffer: ByteArray, deviceId: String): Result<Boolean> {
        // 优先使用控制 socket
        val socket = getControlSocket()
        if (socket != null && socket.isConnected && !socket.isClosed) {
            try {
                val outputStream = socket.getOutputStream()
                outputStream.write(buffer)
                outputStream.flush()
                LogManager.d(LogTags.SCRCPY_CLIENT, "控制消息已发送")
                return Result.success(true)
            } catch (e: Exception) {
                LogManager.w(LogTags.SCRCPY_CLIENT, "控制 socket 发送失败，回退到 ADB: ${e.message}")
                try {
                    socket.close()
                } catch (_: Exception) {
                }
                clearControlSocket()
            }
        }

        // 回退到 ADB shell
        val connection = adbConnectionManager.getConnection(deviceId)
            ?: return Result.failure(Exception(AdbTexts.ERROR_DEVICE_CONNECTION_LOST.get()))

        val hexString = buffer.joinToString("") { "%02x".format(it) }
        val command = "echo -n '$hexString' | xxd -r -p | nc 127.0.0.1 $localPort"

        val result = connection.executeShell(command)
        return if (result.isSuccess) {
            LogManager.d(LogTags.SCRCPY_CLIENT, "控制消息已发送（通过 ADB）")
            Result.success(true)
        } else {
            LogManager.e(LogTags.SCRCPY_CLIENT, "控制消息发送失败")
            Result.failure(Exception(RemoteTexts.ERROR_SEND_FAILED.get()))
        }
    }
}

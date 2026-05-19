package com.mobile.scrcpy.android.infrastructure.scrcpy.controller

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.ControlDebugLog
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.core.i18n.RemoteTexts
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnectionManager
import com.mobile.scrcpy.android.infrastructure.adb.shell.AdbShellManager.execute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.Socket

/**
 * Scrcpy 控制器 - 对外暴露触摸、按键、文本等控制用例。
 *
 * 消息编码和发送队列都下沉到协作对象，控制器本身只保留用例编排职责。
 */
class ScrcpyController(
    private val adbConnectionManager: AdbConnectionManager,
    private val getDeviceId: () -> String?,
    getControlSocket: () -> Socket?,
    clearControlSocket: () -> Unit,
    localPort: Int,
) {
    private val transport =
        ScrcpyControllerTransport(
            getControlSocket = getControlSocket,
            clearControlSocket = clearControlSocket,
            localPort = localPort,
        )

    fun start(deviceId: String) {
        transport.start(deviceId)
    }

    fun isRunning(): Boolean = transport.isRunning()

    fun stop() {
        transport.stop()
    }

    fun destroy() {
        transport.destroy()
    }

    fun sendTouchEvent(
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float = 1.0f,
    ): Result<Boolean> {
        requireDeviceId() ?: return Result.failure(
            Exception(AdbTexts.ERROR_DEVICE_NOT_CONNECTED.get()),
        )

        return try {
            transport.enqueueTouch(
                action = action,
                pointerId = pointerId,
                x = x,
                y = y,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                pressure = pressure,
            )
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "发送触摸事件失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun sendKeyEvent(
        keyCode: Int,
        action: Int = -1,
        repeat: Int = 0,
        metaState: Int = 0,
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            requireDeviceId() ?: return@withContext Result.failure(
                Exception(AdbTexts.ERROR_DEVICE_NOT_CONNECTED.get()),
            )

            ensureControlSocketReady() ?: return@withContext Result.failure(
                Exception(RemoteTexts.ERROR_CONTROL_NOT_READY.get()),
            )

            try {
                if (action == -1) {
                    val downResult = sendSingleKeyEvent(keyCode, 0, repeat, metaState)
                    if (downResult.isFailure) {
                        return@withContext downResult
                    }
                    delay(10)
                    sendSingleKeyEvent(keyCode, 1, repeat, metaState)
                } else {
                    sendSingleKeyEvent(keyCode, action, repeat, metaState)
                }
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "发送按键事件失败: ${e.message}", e)
                Result.failure(e)
            }
        }

    suspend fun sendText(text: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            requireDeviceId() ?: return@withContext Result.failure(
                Exception(AdbTexts.ERROR_DEVICE_NOT_CONNECTED.get()),
            )

            ControlDebugLog.d(LogTags.SCRCPY_CLIENT) { "发送文本: '$text'" }

            try {
                transport.enqueueText(text)
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "发送文本失败: ${e.message}", e)
                Result.failure(e)
            }
        }

    suspend fun setClipboardAndPaste(text: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val deviceId =
                requireDeviceId() ?: return@withContext Result.failure(
                    Exception(AdbTexts.ERROR_DEVICE_NOT_CONNECTED.get()),
                )

            ControlDebugLog.d(LogTags.SCRCPY_CLIENT) { "通过剪贴板注入文本: '$text'" }

            try {
                val connection =
                    adbConnectionManager.getConnection(deviceId)
                        ?: return@withContext Result.failure(Exception(AdbTexts.ERROR_DEVICE_CONNECTION_LOST.get()))

                val base64Text =
                    android.util.Base64.encodeToString(
                        text.toByteArray(Charsets.UTF_8),
                        android.util.Base64.NO_WRAP,
                    )
                val setClipboardCmd =
                    "am broadcast -a clipper.set -e text \"$base64Text\" 2>/dev/null || " +
                        "service call clipboard 1 i32 0 s16 com.android.shell s16 \"$text\""

                val clipResult = execute(connection, setClipboardCmd)
                if (clipResult.isFailure) {
                    LogManager.w(LogTags.SCRCPY_CLIENT, "设置剪贴板失败，尝试直接粘贴")
                }

                delay(100)
                sendKeyEvent(279)

                ControlDebugLog.d(LogTags.SCRCPY_CLIENT) { "文本注入成功" }
                Result.success(true)
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "注入文本失败: ${e.message}", e)
                Result.failure(e)
            }
        }

    suspend fun setDisplayPower(on: Boolean): Result<Boolean> =
        withContext(Dispatchers.IO) {
            requireDeviceId() ?: return@withContext Result.failure(
                Exception(AdbTexts.ERROR_DEVICE_NOT_CONNECTED.get()),
            )

            ensureControlSocketReady() ?: return@withContext Result.failure(
                Exception(RemoteTexts.ERROR_CONTROL_NOT_READY.get()),
            )

            try {
                transport.enqueueDisplayPower(on)
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "发送屏幕电源控制失败: ${e.message}", e)
                Result.failure(e)
            }
        }

    suspend fun wakeUpScreen(
        screenWidth: Int = 720,
        screenHeight: Int = 1280,
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                if (!transport.hasReadySocket()) {
                    LogManager.w(LogTags.SCRCPY_CLIENT, RemoteTexts.ERROR_CONTROL_NOT_READY.get())
                    return@withContext Result.failure(Exception(RemoteTexts.ERROR_CONTROL_NOT_READY.get()))
                }

                sendTouchEvent(0, 0, 100, 100, screenWidth, screenHeight, 1.0f)
                delay(10)
                sendTouchEvent(2, 0, 200, 200, screenWidth, screenHeight, 1.0f)
                delay(10)
                sendTouchEvent(1, 0, 200, 200, screenWidth, screenHeight, 0f)
                ControlDebugLog.d(LogTags.SCRCPY_CLIENT) { "已发送滑动事件触发画面刷新" }
                Result.success(true)
            } catch (e: Exception) {
                try {
                    delay(50)
                    sendKeyEvent(224)
                    delay(50)
                    ControlDebugLog.d(LogTags.SCRCPY_CLIENT) { RemoteTexts.SCRCPY_SCREEN_WAKE_SIGNAL_SENT.get() }
                    Result.success(true)
                } catch (wakeError: Exception) {
                    LogManager.w(
                        LogTags.SCRCPY_CLIENT,
                        "${RemoteTexts.SCRCPY_WAKE_SCREEN_FAILED.get()}: ${wakeError.message}",
                    )
                    Result.failure(wakeError)
                }
            }
        }

    private suspend fun sendSingleKeyEvent(
        keyCode: Int,
        action: Int,
        repeat: Int = 0,
        metaState: Int = 0,
    ): Result<Boolean> =
        try {
            transport.enqueueKey(
                action = action,
                keyCode = keyCode,
                repeat = repeat,
                metaState = metaState,
            )
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "发送按键事件失败: ${e.message}", e)
            Result.failure(e)
        }

    private fun requireDeviceId(): String? = getDeviceId()

    private fun ensureControlSocketReady(): Socket? {
        val socket = transport.currentSocket()
        ControlDebugLog.d(LogTags.SCRCPY_CLIENT) {
            "发送按键 socket=${socket != null}, closed=${socket?.isClosed}, connected=${socket?.isConnected}"
        }
        if (socket == null || socket.isClosed || !socket.isConnected) {
            LogManager.e(LogTags.SCRCPY_CLIENT, RemoteTexts.ERROR_CONTROL_NOT_READY.get())
            return null
        }
        return socket
    }
}

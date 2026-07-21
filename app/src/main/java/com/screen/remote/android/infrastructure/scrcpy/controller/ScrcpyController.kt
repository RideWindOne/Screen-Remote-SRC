package com.screen.remote.android.infrastructure.scrcpy.controller

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.manager.LogManager.dControl
import com.screen.remote.android.core.common.manager.SessionIssueTracker
import com.screen.remote.android.core.i18n.AdbTexts
import com.screen.remote.android.core.i18n.RemoteTexts
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
    private val getDeviceId: () -> String?,
    getControlSocket: () -> Socket?,
    clearControlSocket: () -> Unit,
    localPort: Int,
    onClipboardReceived: (String) -> Unit,
    issueTracker: SessionIssueTracker,
) {
    private val transport =
        ScrcpyControllerTransport(
            getControlSocket = getControlSocket,
            clearControlSocket = clearControlSocket,
            localPort = localPort,
            onClipboardReceived = onClipboardReceived,
            issueTracker = issueTracker,
        )

    fun start(
        deviceId: String,
        gameMode: Boolean,
    ) {
        transport.start(deviceId, gameMode)
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
            LogManager.e(LogTags.SCRCPY_CLIENT, "Failed to send touch event: ${e.message}", e)
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
                LogManager.e(LogTags.SCRCPY_CLIENT, "Failed to send key event: ${e.message}", e)
                Result.failure(e)
            }
        }

    suspend fun sendText(text: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            requireDeviceId() ?: return@withContext Result.failure(
                Exception(AdbTexts.ERROR_DEVICE_NOT_CONNECTED.get()),
            )

            dControl(LogTags.SCRCPY_CLIENT) { "Send text: '$text'" }

            try {
                transport.enqueueText(text)
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "Failed to send text: ${e.message}", e)
                Result.failure(e)
            }
        }

    suspend fun setClipboardAndPaste(text: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            requireDeviceId() ?: return@withContext Result.failure(
                Exception(AdbTexts.ERROR_DEVICE_NOT_CONNECTED.get()),
            )

            ensureControlSocketReady() ?: return@withContext Result.failure(
                Exception(RemoteTexts.ERROR_CONTROL_NOT_READY.get()),
            )

            dControl(LogTags.SCRCPY_CLIENT) {
                "通过剪贴板注入文本: utf8Bytes=${text.toByteArray(Charsets.UTF_8).size}"
            }

            try {
                transport.enqueueClipboard(text, paste = true)
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "Failed to inject text: ${e.message}", e)
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
                LogManager.e(LogTags.SCRCPY_CLIENT, "Failed to send screen power control: ${e.message}", e)
                Result.failure(e)
            }
        }

    suspend fun startApp(name: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            requireDeviceId() ?: return@withContext Result.failure(
                Exception(AdbTexts.ERROR_DEVICE_NOT_CONNECTED.get()),
            )

            ensureControlSocketReady() ?: return@withContext Result.failure(
                Exception(RemoteTexts.ERROR_CONTROL_NOT_READY.get()),
            )

            val normalizedName = name.trim()
            try {
                transport.enqueueStartApp(normalizedName)
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "Failed to send message when starting remote App: ${e.message}", e)
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
                    LogManager.w(LogTags.SCRCPY_CLIENT, RemoteTexts.ERROR_CONTROL_NOT_READY.english)
                    return@withContext Result.failure(Exception(RemoteTexts.ERROR_CONTROL_NOT_READY.get()))
                }

                sendTouchEvent(0, 0, 100, 100, screenWidth, screenHeight, 1.0f)
                delay(10)
                sendTouchEvent(2, 0, 200, 200, screenWidth, screenHeight, 1.0f)
                delay(10)
                sendTouchEvent(1, 0, 200, 200, screenWidth, screenHeight, 0f)
                dControl(LogTags.SCRCPY_CLIENT) { "A sliding event has been sent to trigger a screen refresh" }
                Result.success(true)
            } catch (e: Exception) {
                try {
                    delay(50)
                    sendKeyEvent(224)
                    delay(50)
                    dControl(LogTags.SCRCPY_CLIENT) { RemoteTexts.SCRCPY_SCREEN_WAKE_SIGNAL_SENT.english }
                    Result.success(true)
                } catch (wakeError: Exception) {
                    LogManager.w(
                        LogTags.SCRCPY_CLIENT,
                        "${RemoteTexts.SCRCPY_WAKE_SCREEN_FAILED.english}: ${wakeError.message}",
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
            LogManager.e(LogTags.SCRCPY_CLIENT, "Failed to send key event: ${e.message}", e)
            Result.failure(e)
        }

    private fun requireDeviceId(): String? = getDeviceId()

    private fun ensureControlSocketReady(): Socket? {
        val socket = transport.currentSocket()
        dControl(LogTags.SCRCPY_CLIENT) {
            "发送按键 socket=${socket != null}, closed=${socket?.isClosed}, connected=${socket?.isConnected}"
        }
        if (socket == null || socket.isClosed || !socket.isConnected) {
            LogManager.e(LogTags.SCRCPY_CLIENT, RemoteTexts.ERROR_CONTROL_NOT_READY.english)
            return null
        }
        return socket
    }
}

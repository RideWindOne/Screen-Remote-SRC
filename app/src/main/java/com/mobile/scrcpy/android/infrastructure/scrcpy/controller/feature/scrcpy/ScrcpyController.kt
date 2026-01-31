package com.mobile.scrcpy.android.infrastructure.scrcpy.controller.feature.scrcpy

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.ScrcpyConstants
import com.mobile.scrcpy.android.core.common.event.ControllerError
import com.mobile.scrcpy.android.core.common.event.MouseButtonDown
import com.mobile.scrcpy.android.core.common.event.MouseButtonUp
import com.mobile.scrcpy.android.core.common.event.MouseMotion
import com.mobile.scrcpy.android.core.common.event.ScrcpyEventBus
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.core.i18n.RemoteTexts
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnectionManager
import com.mobile.scrcpy.android.infrastructure.adb.shell.AdbShellManager.execute
import com.mobile.scrcpy.android.infrastructure.scrcpy.protocol.feature.scrcpy.ScrcpyProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Socket

/**
 * Scrcpy 控制器 - 负责发送控制消息（触摸、按键、文本）
 *
 * ✅ 消息队列机制：异步解耦 + 背压控制（参考 scrcpy 原生 sc_controller）
 * ✅ 专用协程作用域：独立发送线程，保证顺序
 * ✅ 消息丢弃策略：队列满时自动丢弃可丢弃消息（触摸移动）
 *
 * 集成事件系统：
 * - 推送 ControllerError 事件（控制消息发送失败）
 * - 推送 KeyDown/KeyUp 事件（按键事件）
 * - 推送 MouseButtonDown/MouseButtonUp/MouseMotion 事件（触摸事件）
 */
class ScrcpyController(
    private val adbConnectionManager: AdbConnectionManager,
    private val getDeviceId: () -> String?,
    private val getControlSocket: () -> Socket?,
    private val clearControlSocket: () -> Unit,
    private val localPort: Int,
) {
    // ✅ 消息队列：容量 60（参考 scrcpy SC_CONTROL_MSG_QUEUE_LIMIT）
    private var messageQueue = Channel<ControlMessage>(capacity = ScrcpyConstants.CONTROL_MSG_QUEUE_LIMIT)

    // ✅ 专用协程作用域：独立于 UI 线程
    private val controlScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ✅ 发送任务
    private var senderJob: Job? = null

    /**
     * 控制消息封装
     */
    private class ControlMessage(
        val buffer: ByteArray,
        val isDroppable: Boolean, // 是否可丢弃（触摸移动事件可丢弃）
    )

    /**
     * 启动控制消息发送线程
     */
    fun start(deviceId: String) {
        if (senderJob?.isActive == true) {
            LogManager.d(LogTags.SCRCPY_CLIENT, "控制消息发送线程已在运行: $deviceId")
            return
        }

        senderJob =
            controlScope.launch {
                LogManager.d(LogTags.SDL, "控制消息发送线程已启动")
                while (isActive) {
                    try {
                        // 阻塞等待消息（类似 scrcpy 的 sc_cond_wait）
                        val message = messageQueue.receive()

                        // 发送消息
                        val socket = getControlSocket()
                        if (socket != null && socket.isConnected && !socket.isClosed) {
                            try {
                                socket.getOutputStream().apply {
                                    write(message.buffer)
                                    flush()
                                }
                            } catch (e: Exception) {
                                LogManager.e(LogTags.SCRCPY_CLIENT, "Socket 发送失败: ${e.message}")
                                // 推送控制器错误事件
                                ScrcpyEventBus.pushEvent(ControllerError(e.message ?: "Send failed"))
                                try {
                                    socket.close()
                                } catch (_: Exception) {
                                }
                                clearControlSocket()
                            }
                        } else {
                            LogManager.w(LogTags.SCRCPY_CLIENT, "控制 Socket 未就绪，消息已丢弃")
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            LogManager.e(LogTags.SCRCPY_CLIENT, "控制消息发送异常: ${e.message}")
                        }
                    }
                }
                LogManager.d(LogTags.SDL, "控制消息发送线程已停止: $deviceId")
            }
    }

    /**
     * 检查发送线程是否正在运行
     */
    fun isRunning(): Boolean = senderJob?.isActive == true

    /**
     * 停止控制消息发送线程
     */
    fun stop() {
        senderJob?.cancel()
        senderJob = null
        messageQueue.close()
        // 重新创建 Channel，为下次启动做准备
        messageQueue = Channel(capacity = ScrcpyConstants.CONTROL_MSG_QUEUE_LIMIT)
        LogManager.d(LogTags.SDL, "控制消息发送线程已取消")
    }

    /**
     * 清理资源
     */
    fun destroy() {
        stop()
        controlScope.cancel()
    }

    /**
     * 发送触摸事件（支持多指触摸）
     * 按照 scrcpy 标准控制消息格式
     *
     * ✅ 触摸移动事件可丢弃（action = 2）
     */
    suspend fun sendTouchEvent(
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float = 1.0f,
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val deviceId = // TODO
                    getDeviceId() ?: return@withContext Result.failure(
                        Exception(AdbTexts.ERROR_DEVICE_NOT_CONNECTED.get()),
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

                // ✅ 触摸移动事件（action = 2）可丢弃
                val isDroppable = (action == 2)
                val result = enqueueControlMessage(buffer, isDroppable)

                // 推送触摸事件到事件系统
                when (action) {
                    0 -> ScrcpyEventBus.pushEvent(MouseButtonDown(x.toFloat(), y.toFloat(), 1))
                    1 -> ScrcpyEventBus.pushEvent(MouseButtonUp(x.toFloat(), y.toFloat(), 1))
                    2 -> ScrcpyEventBus.pushEvent(MouseMotion(x.toFloat(), y.toFloat()))
                }

                result
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "发送触摸事件失败: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * 发送按键事件
     *
     * ✅ 按键事件永不丢弃（关键消息）
     */
    suspend fun sendKeyEvent(
        keyCode: Int,
        action: Int = -1, // -1 表示发送完整的按下+释放事件
        repeat: Int = 0,
        metaState: Int = 0,
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val deviceId =
                getDeviceId() ?: return@withContext Result.failure(
                    Exception(AdbTexts.ERROR_DEVICE_NOT_CONNECTED.get()),
                )

            // ✅ 检查控制连接是否就绪
            val socket = getControlSocket()
            LogManager.d(
                LogTags.SCRCPY_CLIENT,
                "发送按键 keyCode=$keyCode, socket=${socket != null}, closed=${socket?.isClosed}, connected=${socket?.isConnected}",
            )
            if (socket == null || socket.isClosed || !socket.isConnected) {
                LogManager.e(LogTags.SCRCPY_CLIENT, RemoteTexts.ERROR_CONTROL_NOT_READY.get())
                return@withContext Result.failure(Exception(RemoteTexts.ERROR_CONTROL_NOT_READY.get()))
            }

            return@withContext try {
                // 如果 action = -1，发送完整的按键事件（按下+释放）
                if (action == -1) {
                    // 发送按下事件
                    sendSingleKeyEvent(keyCode, 0, repeat, metaState)
                    delay(10) // 短暂延迟
                    // 发送释放事件
                    sendSingleKeyEvent(keyCode, 1, repeat, metaState)
                    return@withContext Result.success(true)
                } else {
                    // 发送单个事件
                    return@withContext sendSingleKeyEvent(keyCode, action, repeat, metaState)
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
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
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

                // ✅ 按键事件永不丢弃
                enqueueControlMessage(buffer, isDroppable = false)
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "发送按键事件失败: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * 发送文本
     *
     * ✅ 文本事件永不丢弃（关键消息）
     */
    suspend fun sendText(text: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val deviceId = // TODO
                getDeviceId() ?: return@withContext Result.failure(
                    Exception(AdbTexts.ERROR_DEVICE_NOT_CONNECTED.get()),
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

                // ✅ 文本事件永不丢弃
                enqueueControlMessage(buffer, isDroppable = false)
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "发送文本失败: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * 设置远程设备剪贴板并自动粘贴（支持中文）
     *
     * ✅ 剪贴板事件永不丢弃（关键消息）
     */
    suspend fun setClipboardAndPaste(text: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val deviceId =
                getDeviceId() ?: return@withContext Result.failure(
                    Exception(AdbTexts.ERROR_DEVICE_NOT_CONNECTED.get()),
                )

            LogManager.d(LogTags.SCRCPY_CLIENT, "通过剪贴板注入文本: '$text'")

            return@withContext try {
                val connection =
                    adbConnectionManager.getConnection(deviceId)
                        ?: return@withContext Result.failure(Exception(AdbTexts.ERROR_DEVICE_CONNECTION_LOST.get()))

                // 方案：使用 ADB 设置剪贴板 + 发送粘贴按键
                // 1. 设置剪贴板内容
                val base64Text =
                    android.util.Base64.encodeToString(
                        text.toByteArray(Charsets.UTF_8),
                        android.util.Base64.NO_WRAP,
                    )
                val setClipboardCmd =
                    "am broadcast -a clipper.set -e text \"$base64Text\" 2>/dev/null || " +
                        "service call clipboard 1 i32 0 s16 com.android.shell s16 \"$text\""

                val clipResult =
                    execute(
                        connection,
                        setClipboardCmd,
                    )
                if (clipResult.isFailure) {
                    LogManager.w(LogTags.SCRCPY_CLIENT, "设置剪贴板失败，尝试直接粘贴")
                }

                // 2. 发送粘贴按键事件 (Ctrl+V: keycode 279 或使用 KEYCODE_PASTE)
                delay(100) // 等待剪贴板设置完成

                // 发送 KEYCODE_PASTE (279) - 永不丢弃
                sendKeyEvent(279) // KEYCODE_PASTE

                LogManager.d(LogTags.SCRCPY_CLIENT, "文本注入成功")
                Result.success(true)
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "注入文本失败: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * 唤醒屏幕（通过滑动事件触发画面刷新）
     */
    suspend fun wakeUpScreen(
        screenWidth: Int = 720,
        screenHeight: Int = 1280,
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                // ✅ 检查连接状态
                if (getControlSocket() == null || getControlSocket()?.isClosed == true) {
                    LogManager.w(LogTags.SCRCPY_CLIENT, RemoteTexts.ERROR_CONTROL_NOT_READY.get())
                    return@withContext Result.failure(Exception(RemoteTexts.ERROR_CONTROL_NOT_READY.get()))
                }

                // 从 (100, 100) 滑动到 (200, 200)
                sendTouchEvent(0, 0, 100, 100, screenWidth, screenHeight, 1.0f) // 按下
                delay(10)
                sendTouchEvent(2, 0, 200, 200, screenWidth, screenHeight, 1.0f) // 移动
                delay(10)
                sendTouchEvent(1, 0, 200, 200, screenWidth, screenHeight, 0f) // 抬起
                LogManager.d(LogTags.SCRCPY_CLIENT, "已发送滑动事件触发画面刷新")
                Result.success(true)
            } catch (e: Exception) {
                try {
                    // 降级方案：额外发送唤醒按键
                    delay(50)
                    sendKeyEvent(224)
                    delay(50)
                    LogManager.d(LogTags.SCRCPY_CLIENT, RemoteTexts.SCRCPY_SCREEN_WAKE_SIGNAL_SENT.get())
                    Result.success(true)
                } catch (e: Exception) {
                    LogManager.w(LogTags.SCRCPY_CLIENT, "${RemoteTexts.SCRCPY_WAKE_SCREEN_FAILED.get()}: ${e.message}")
                    Result.failure(e)
                }
            }
        }

    /**
     * 消息入队（参考 scrcpy sc_controller_push_msg）
     *
     * ✅ 队列未满：直接入队
     * ✅ 队列已满 + 可丢弃：丢弃消息
     * ✅ 队列已满 + 不可丢弃：强制入队（阻塞等待）
     */
    private suspend fun enqueueControlMessage(
        buffer: ByteArray,
        isDroppable: Boolean,
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val message = ControlMessage(buffer, isDroppable)

            return@withContext try {
                if (isDroppable) {
                    // 可丢弃消息：非阻塞入队，队列满时丢弃
                    val result = messageQueue.trySend(message)
                    if (result.isFailure) {
                        LogManager.w(LogTags.SCRCPY_CLIENT, "控制队列已满，丢弃可丢弃消息")
                    }
                    Result.success(true)
                } else {
                    // 不可丢弃消息：阻塞入队，保证发送
                    messageQueue.send(message)
                    Result.success(true)
                }
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "消息入队失败: ${e.message}")
                Result.failure(e)
            }
        }
}

package com.mobile.scrcpy.android.feature.remote.presentation

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.common.manager.ShellDebugLog
import com.mobile.scrcpy.android.feature.remote.model.RemoteUiLayoutBounds
import com.mobile.scrcpy.android.feature.remote.model.RemoteUiLayoutSnapshot
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnectionManager
import com.mobile.scrcpy.android.infrastructure.adb.connection.logShellCommandFailure
import com.mobile.scrcpy.android.infrastructure.adb.connection.logShellStreamOpen
import com.mobile.scrcpy.android.infrastructure.adb.connection.logShellStreamReady
import com.mobile.scrcpy.android.infrastructure.adb.connection.shellLogPreview
import com.mobile.scrcpy.android.infrastructure.adb.shell.AdbShellManager.execute
import com.mobile.scrcpy.android.infrastructure.scrcpy.client.ScrcpyClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 设备控制 ViewModel
 * 职责：触摸/按键/文本输入、滑动手势、Shell 命令、屏幕唤醒
 */
class ControlViewModel(
    private val scrcpyClient: ScrcpyClient,
    private val adbConnectionManager: AdbConnectionManager,
) : ViewModel() {
    companion object {
        private const val UI_LAYOUT_DUMP_PATH = "/data/local/tmp/scrcpy-mobile-layout.xml"
        private const val UI_LAYOUT_DUMP_START = "__SCRCPY_UI_LAYOUT_BEGIN__"
        private const val UI_LAYOUT_DUMP_END = "__SCRCPY_UI_LAYOUT_END__"
        private const val UI_LAYOUT_DUMP_COMMAND =
            "uiautomator dump $UI_LAYOUT_DUMP_PATH >/dev/null 2>&1 && " +
                "(echo $UI_LAYOUT_DUMP_START && cat $UI_LAYOUT_DUMP_PATH && echo $UI_LAYOUT_DUMP_END)"
        private val DISPLAY_SIZE_OVERRIDE_REGEX = Regex("""Override size:\s*(\d+)x(\d+)""")
        private val DISPLAY_SIZE_PHYSICAL_REGEX = Regex("""Physical size:\s*(\d+)x(\d+)""")

        fun provideFactory(
            scrcpyClient: ScrcpyClient,
            adbConnectionManager: AdbConnectionManager,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ControlViewModel(scrcpyClient, adbConnectionManager) as T
            }
    }

    // ============ 按键控制 ============

    suspend fun sendKeyEvent(keyCode: Int): Result<Boolean> = scrcpyClient.sendKeyEvent(keyCode)

    suspend fun sendKeyEvent(
        keyCode: Int,
        action: Int,
        metaState: Int,
    ): Result<Boolean> = scrcpyClient.sendKeyEvent(keyCode, action, 0, metaState)

    suspend fun sendText(text: String): Result<Boolean> = scrcpyClient.sendText(text)

    // ============ 触摸控制 ============

    fun sendTouchEvent(
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float = 1.0f,
    ): Result<Boolean> = scrcpyClient.sendTouchEvent(action, pointerId, x, y, screenWidth, screenHeight, pressure)

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
        duration: Long = 300,
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val resolution =
                    scrcpyClient.videoResolution.value
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
    suspend fun wakeUpScreen(): Result<Boolean> = scrcpyClient.wakeUpScreen()

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
                val deviceId =
                    scrcpyClient.getCurrentDeviceId()
                        ?: return@withContext Result.failure(Exception("未连接设备"))

                // 获取 ADB 连接
                val connection =
                    adbConnectionManager.getConnection(deviceId)
                        ?: return@withContext Result.failure(Exception("Device connection lost"))

                // 执行 Shell 命令
                execute(
                    connection,
                    command,
                )
            } catch (e: Exception) {
                LogManager.e(LogTags.CONTROL_VM, "执行 Shell 命令失败: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    suspend fun captureTargetDeviceScreenshot(): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val deviceId =
                    scrcpyClient.getCurrentDeviceId()
                        ?: return@withContext Result.failure(Exception("未连接设备"))

                val connection =
                    adbConnectionManager.getConnection(deviceId)
                        ?: return@withContext Result.failure(Exception("Device connection lost"))

                val sdkInt =
                    execute(
                        connection = connection,
                        command = "getprop ro.build.version.sdk",
                        retryOnFailure = false,
                        reportToEventBus = false,
                    ).getOrNull()?.trim()?.toIntOrNull()
                val systemScreenshotKeyCode = if ((sdkInt ?: 0) >= 35) 318 else 120
                val systemScreenshotKeyName = if ((sdkInt ?: 0) >= 35) "KEYCODE_SCREENSHOT" else "KEYCODE_SYSRQ"

                val systemResult =
                    execute(
                        connection = connection,
                        command = "input keyevent $systemScreenshotKeyCode",
                        retryOnFailure = false,
                        reportToEventBus = false,
                    )
                if (systemResult.isSuccess) {
                    LogManager.d(
                        LogTags.CONTROL_VM,
                        "已触发系统截图: key=$systemScreenshotKeyName, sdk=${sdkInt ?: "unknown"}",
                    )
                    return@withContext Result.success(systemScreenshotKeyName)
                }
                Result.failure(
                    IllegalStateException(
                        systemResult.exceptionOrNull()?.message ?: "系统截图触发失败",
                    ),
                )
            } catch (e: Exception) {
                LogManager.e(LogTags.CONTROL_VM, "目标设备截图失败: ${e.message}", e)
                Result.failure(e)
            }
        }

    suspend fun uploadFileToDevice(
        context: Context,
        uri: Uri,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val deviceId =
                    scrcpyClient.getCurrentDeviceId()
                        ?: return@withContext Result.failure(Exception("未连接设备"))

                val connection =
                    adbConnectionManager.getConnection(deviceId)
                        ?: return@withContext Result.failure(Exception("Device connection lost"))

                val originalName = resolveDisplayName(context, uri)
                val safeName = sanitizeFileName(originalName.ifBlank { "upload-${System.currentTimeMillis()}" })
                val tempFile = copyUriToTempFile(context, uri, safeName)
                val remoteDir = "/sdcard/Download"
                val remotePath = "$remoteDir/$safeName"

                try {
                    execute(
                        connection = connection,
                        command = "mkdir -p $remoteDir",
                        retryOnFailure = false,
                        reportToEventBus = false,
                    ).getOrElse { error ->
                        return@withContext Result.failure(error)
                    }

                    connection.pushFile(tempFile.absolutePath, remotePath).getOrElse { error ->
                        return@withContext Result.failure(error)
                    }
                } finally {
                    tempFile.delete()
                }

                Result.success(remotePath)
            } catch (e: Exception) {
                LogManager.e(LogTags.CONTROL_VM, "上传文件到目标设备失败: ${e.message}", e)
                Result.failure(e)
            }
        }

    suspend fun captureCurrentUiLayout(): Result<RemoteUiLayoutSnapshot> =
        withContext(Dispatchers.IO) {
            try {
                val deviceId =
                    scrcpyClient.getCurrentDeviceId()
                        ?: return@withContext Result.failure(Exception("未连接设备"))

                val connection =
                    adbConnectionManager.getConnection(deviceId)
                        ?: return@withContext Result.failure(Exception("Device connection lost"))

                val rawOutput =
                    connection.openShellStream(UI_LAYOUT_DUMP_COMMAND)?.use { shellStream ->
                        logShellStreamOpen(LogTags.CONTROL_VM, UI_LAYOUT_DUMP_COMMAND)
                        logShellStreamReady(LogTags.CONTROL_VM, UI_LAYOUT_DUMP_COMMAND)
                        val response = shellStream.readAll()
                        ShellDebugLog.d(LogTags.CONTROL_VM) {
                            "ui-layout shell result: exit=${response.exitCode} stdout=${shellLogPreview(response.output)} stderr=${shellLogPreview(response.errorOutput)}"
                        }
                        if (response.exitCode != 0) {
                            return@withContext Result.failure(
                                IllegalStateException(
                                    response.errorOutput.ifBlank {
                                        "布局抓取失败，exit=${response.exitCode}"
                                    },
                                ),
                            )
                        }
                        response.allOutput
                    }
                        ?: execute(
                            connection = connection,
                            command = UI_LAYOUT_DUMP_COMMAND,
                            retryOnFailure = false,
                            reportToEventBus = false,
                        ).getOrElse { error ->
                            logShellCommandFailure(LogTags.CONTROL_VM, UI_LAYOUT_DUMP_COMMAND, error)
                            return@withContext Result.failure(error)
                        }

                val layoutXml =
                    extractUiLayoutXml(rawOutput)
                        ?: return@withContext Result.failure(
                            IllegalStateException("未获取到当前页面布局数据"),
                        )

                val parsedSnapshot = RemoteUiLayoutParser.parseSnapshot(layoutXml)
                val displayBounds = queryTargetDisplayBounds(connection)
                val snapshot =
                    if (displayBounds != null) {
                        parsedSnapshot.copy(viewportBounds = displayBounds)
                    } else {
                        parsedSnapshot
                    }
                LogManager.d(
                    LogTags.CONTROL_VM,
                    "布局抓取完成: nodes=${snapshot.nodes.size}, viewport=${snapshot.viewportBounds.width}x${snapshot.viewportBounds.height}",
                )
                Result.success(snapshot)
            } catch (e: Exception) {
                LogManager.e(LogTags.CONTROL_VM, "抓取当前页面布局失败: ${e.message}", e)
                Result.failure(e)
            }
        }

    suspend fun isTargetDeviceKeyboardVisible(): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val deviceId =
                    scrcpyClient.getCurrentDeviceId()
                        ?: return@withContext Result.failure(Exception("未连接设备"))

                val connection =
                    adbConnectionManager.getConnection(deviceId)
                        ?: return@withContext Result.failure(Exception("Device connection lost"))

                val output =
                    execute(
                        connection = connection,
                        command = "dumpsys input_method",
                        retryOnFailure = false,
                        reportToEventBus = false,
                    ).getOrElse { error ->
                        return@withContext Result.failure(error)
                    }

                val isVisible =
                    output.contains("mInputShown=true") ||
                        output.contains("mWindowVisible=true") ||
                        output.contains("mInputViewStarted=true") ||
                        output.contains("mImeWindowVis=3")

                Result.success(isVisible)
            } catch (e: Exception) {
                LogManager.e(LogTags.CONTROL_VM, "检查目标设备键盘可见性失败: ${e.message}", e)
                Result.failure(e)
            }
        }

    private fun extractUiLayoutXml(rawOutput: String): String? {
        val startIndex = rawOutput.indexOf(UI_LAYOUT_DUMP_START)
        val endIndex = rawOutput.indexOf(UI_LAYOUT_DUMP_END)
        if (startIndex >= 0 && endIndex > startIndex) {
            return rawOutput
                .substring(startIndex + UI_LAYOUT_DUMP_START.length, endIndex)
                .trim()
                .takeIf { it.isNotBlank() }
        }

        val xmlStart =
            rawOutput.indexOf("<?xml").takeIf { it >= 0 }
                ?: rawOutput.indexOf("<hierarchy").takeIf { it >= 0 }
                ?: return null

        return rawOutput.substring(xmlStart).trim().takeIf { it.isNotBlank() }
    }

    private suspend fun queryTargetDisplayBounds(
        connection: com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnection,
    ): RemoteUiLayoutBounds? {
        val wmSizeOutput =
            execute(
                connection = connection,
                command = "wm size",
                retryOnFailure = false,
                reportToEventBus = false,
            ).getOrNull()
                ?: return null

        val overrideMatch = DISPLAY_SIZE_OVERRIDE_REGEX.find(wmSizeOutput)
        val physicalMatch = DISPLAY_SIZE_PHYSICAL_REGEX.find(wmSizeOutput)
        val match = overrideMatch ?: physicalMatch ?: return null
        val width = match.groupValues[1].toIntOrNull() ?: return null
        val height = match.groupValues[2].toIntOrNull() ?: return null
        if (width <= 0 || height <= 0) {
            return null
        }

        return RemoteUiLayoutBounds(
            left = 0,
            top = 0,
            right = width,
            bottom = height,
        )
    }

    private fun resolveDisplayName(
        context: Context,
        uri: Uri,
    ): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                return cursor.getString(nameIndex).orEmpty()
            }
        }
        return uri.lastPathSegment.orEmpty()
    }

    private fun copyUriToTempFile(
        context: Context,
        uri: Uri,
        fileName: String,
    ): File {
        val tempDir = File(context.cacheDir, "remote-upload").apply { mkdirs() }
        val tempFile = File(tempDir, "${System.currentTimeMillis()}-$fileName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("无法读取选择的文件。")
        return tempFile
    }

    private fun sanitizeFileName(fileName: String): String =
        fileName
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .ifBlank { "upload-${System.currentTimeMillis()}" }
}

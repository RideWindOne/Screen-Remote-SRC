package com.screen.remote.android.infrastructure.adb.connection

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.manager.ShellDebugLog
import com.screen.remote.android.core.i18n.AdbTexts

internal object AdbEncoderShellStreamReader {
    fun read(shellStream: dadb.AdbShellStream): String {
        val output = StringBuilder()
        val errorOutput = StringBuilder()
        var lineCount = 0
        var hasReceivedData = false

        try {
            while (lineCount < MAX_LINES) {
                val packet =
                    try {
                        shellStream.read()
                    } catch (e: java.io.EOFException) {
                        handleUnexpectedEof(
                            hasReceivedData = hasReceivedData,
                            output = output,
                            errorOutput = errorOutput,
                        )
                    }

                hasReceivedData = true

                when (packet) {
                    is dadb.AdbShellPacket.StdOut -> {
                        val text = String(packet.payload, Charsets.UTF_8)
                        ShellDebugLog.d(LogTags.ADB_CONNECTION) {
                            "encoder shell packet type=stdout length=${text.length} payload=${shellLogPreview(text)}"
                        }
                        output.append(text)
                        lineCount++
                        LogManager.d(LogTags.ADB_CONNECTION, "stdout: $text")

                        if (text.contains("List of audio encoders:")) {
                            break
                        }
                    }

                    is dadb.AdbShellPacket.StdError -> {
                        val text = String(packet.payload, Charsets.UTF_8)
                        ShellDebugLog.d(LogTags.ADB_CONNECTION) {
                            "encoder shell packet type=stderr length=${text.length} payload=${shellLogPreview(text)}"
                        }
                        errorOutput.append(text)
                        LogManager.w(LogTags.ADB_CONNECTION, "scrcpy-server stderr: $text")
                    }

                    is dadb.AdbShellPacket.Exit -> {
                        val exitCode = if (packet.payload.isNotEmpty()) packet.payload[0].toInt() else 0
                        ShellDebugLog.d(LogTags.ADB_CONNECTION) { "encoder shell packet type=exit exitCode=$exitCode" }
                        LogManager.d(
                            LogTags.ADB_CONNECTION,
                            "${AdbTexts.ADB_SHELL_STREAM_EXIT.get()}, exitCode: $exitCode",
                        )
                        if (exitCode != 0) {
                            throw Exception(
                                if (errorOutput.isNotEmpty()) {
                                    "scrcpy-server 执行失败 (exitCode=$exitCode)\nstderr: $errorOutput"
                                } else {
                                    "scrcpy-server 执行失败 (exitCode=$exitCode)，无错误输出"
                                },
                            )
                        }
                        break
                    }

                }
            }
        } catch (e: Exception) {
            if (e.message?.contains("scrcpy-server") == true) {
                throw e
            }
            LogManager.w(
                LogTags.ADB_CONNECTION,
                "${AdbTexts.ADB_READ_OUTPUT_ERROR.get()}: ${e.javaClass.simpleName} - ${e.message ?: "未知错误"}",
                e,
            )
            throw e
        } finally {
            try {
                shellStream.close()
            } catch (e: Exception) {
                LogManager.w(LogTags.ADB_CONNECTION, "关闭 shell stream 失败: ${e.message}")
            }
        }

        return output.toString()
    }

    private fun handleUnexpectedEof(
        hasReceivedData: Boolean,
        output: StringBuilder,
        errorOutput: StringBuilder,
    ): Nothing {
        if (!hasReceivedData) {
            LogManager.w(
                LogTags.ADB_CONNECTION,
                "${AdbTexts.ADB_READ_OUTPUT_ERROR.get()}: scrcpy-server 立即退出，未输出任何内容",
            )
            throw Exception(
                "scrcpy-server 启动失败：进程立即退出，未输出任何内容。可能原因：\n" +
                    "1. scrcpy-server.jar 文件损坏\n" +
                    "2. 设备不支持该版本的 scrcpy\n" +
                    "3. Android 版本过低",
            )
        }

        val errorMessage =
            if (errorOutput.isNotEmpty()) {
                "scrcpy-server 启动失败\nstderr: $errorOutput"
            } else if (output.isNotEmpty()) {
                "scrcpy-server 输出不完整\nstdout: $output"
            } else {
                "scrcpy-server 启动失败，未收到任何输出"
            }
        LogManager.w(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_READ_OUTPUT_ERROR.get()}: $errorMessage")
        throw Exception(errorMessage)
    }

    private const val MAX_LINES = 200
}

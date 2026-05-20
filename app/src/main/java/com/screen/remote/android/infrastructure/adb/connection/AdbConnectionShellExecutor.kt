package com.screen.remote.android.infrastructure.adb.connection

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.i18n.AdbTexts
import dadb.AdbShellStream
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketException

internal class AdbConnectionShellExecutor(
    private val dadb: Dadb,
    private val deviceId: String,
) {
    suspend fun execute(
        command: String,
        retryOnFailure: Boolean,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                logShellCommandStart(LogTags.ADB_CONNECTION, command)
                val response = dadb.shell(command)
                logShellCommandResult(
                    tag = LogTags.ADB_CONNECTION,
                    command = command,
                    exitCode = response.exitCode,
                    output = response.output,
                    errorOutput = response.errorOutput,
                )
                Result.success(response.output)
            } catch (e: ConnectException) {
                logShellCommandFailure(LogTags.ADB_CONNECTION, command, e)
                LogManager.d(
                    LogTags.ADB_CONNECTION,
                    "${AdbTexts.ADB_DISCONNECTED_ECONNREFUSED.get()} (ECONNREFUSED)，${AdbTexts.ADB_CANNOT_EXECUTE_COMMAND.get()}: $command - ${e.message}",
                )
                Result.failure(Exception(AdbTexts.ERROR_ADB_CONNECTION_DISCONNECTED.get(), e))
            } catch (e: EOFException) {
                logShellCommandFailure(LogTags.ADB_CONNECTION, command, e)
                retryShellCommand(command, retryOnFailure, e)
            } catch (e: SocketException) {
                logShellCommandFailure(LogTags.ADB_CONNECTION, command, e)
                if (e.message?.contains("ECONNREFUSED", ignoreCase = true) == true) {
                    LogManager.d(
                        LogTags.ADB_CONNECTION,
                        "${AdbTexts.ADB_SOCKET_EXCEPTION.get()} (ECONNREFUSED): $command - ${e.message}",
                    )
                    Result.failure(Exception(AdbTexts.ERROR_ADB_CONNECTION_DISCONNECTED.get(), e))
                } else {
                    retryShellCommand(command, retryOnFailure, e)
                }
            } catch (e: Exception) {
                logShellCommandFailure(LogTags.ADB_CONNECTION, command, e)
                LogManager.e(
                    LogTags.ADB_CONNECTION,
                    "${AdbTexts.ADB_EXECUTE_COMMAND_FAILED.get()}: device=$deviceId, msg=${e.message}",
                    e,
                )
                Result.failure(e)
            }
        }

    suspend fun executeAsync(command: String) =
        withContext(Dispatchers.IO) {
            try {
                logShellCommandStart(LogTags.ADB_CONNECTION, command)
                dadb.openShell(command)
                logShellStreamReady(LogTags.ADB_CONNECTION, command)
            } catch (e: Exception) {
                logShellCommandFailure(LogTags.ADB_CONNECTION, command, e)
                LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_ASYNC_EXECUTE_FAILED.get()}: ${e.message}", e)
            }
        }

    suspend fun openStream(command: String): AdbShellStream? =
        withContext(Dispatchers.IO) {
            try {
                logShellStreamOpen(LogTags.ADB_CONNECTION, command)
                dadb.openShell(command).also {
                    logShellStreamReady(LogTags.ADB_CONNECTION, command)
                }
            } catch (e: Exception) {
                logShellCommandFailure(LogTags.ADB_CONNECTION, command, e)
                LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_OPEN_SHELL_STREAM_FAILED.get()}: ${e.message}", e)
                null
            }
        }

    suspend fun openPtyStream(command: String = ""): AdbShellStream? =
        withContext(Dispatchers.IO) {
            try {
                logShellStreamOpen(LogTags.ADB_CONNECTION, command.ifBlank { "<interactive pty>" })
                dadb.openPtyShell(command).also {
                    logShellStreamReady(LogTags.ADB_CONNECTION, command.ifBlank { "<interactive pty>" })
                }
            } catch (e: Exception) {
                logShellCommandFailure(LogTags.ADB_CONNECTION, command.ifBlank { "<interactive pty>" }, e)
                LogManager.w(LogTags.ADB_CONNECTION, "PTY shell 打开失败，回退到 raw shell: ${e.message}")
                runCatching {
                    dadb.openShell(command).also {
                        logShellStreamReady(LogTags.ADB_CONNECTION, command.ifBlank { "<interactive raw>" })
                    }
                }.getOrElse { fallbackError ->
                    LogManager.e(
                        LogTags.ADB_CONNECTION,
                        "${AdbTexts.ADB_OPEN_SHELL_STREAM_FAILED.get()}: ${fallbackError.message}",
                        fallbackError,
                    )
                    null
                }
            }
        }

    private suspend fun retryShellCommand(
        command: String,
        retryOnFailure: Boolean,
        originalError: Exception,
    ): Result<String> {
        if (!retryOnFailure) {
            LogManager.d(
                LogTags.ADB_CONNECTION,
                "${AdbTexts.ADB_CONNECTION_CLOSED.get()}，${AdbTexts.ADB_CANNOT_EXECUTE_COMMAND.get()}: $command",
            )
            return Result.failure(originalError)
        }

        LogManager.d(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_AUTO_RECONNECT_RETRY.get()}: $command")
        return try {
            delay(100)
            val retryResponse = dadb.shell(command)
            logShellCommandResult(
                tag = LogTags.ADB_CONNECTION,
                command = command,
                exitCode = retryResponse.exitCode,
                output = retryResponse.output,
                errorOutput = retryResponse.errorOutput,
            )
            LogManager.d(LogTags.ADB_CONNECTION, AdbTexts.ADB_AUTO_RECONNECT_SUCCESS.get())
            Result.success(retryResponse.output)
        } catch (retryException: Exception) {
            logShellCommandFailure(LogTags.ADB_CONNECTION, command, retryException)
            LogManager.d(
                LogTags.ADB_CONNECTION,
                "${AdbTexts.ADB_AUTO_RECONNECT_STILL_FAILED.get()}: ${retryException.message}",
            )
            Result.failure(retryException)
        }
    }
}

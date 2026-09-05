package com.screen.remote.android.infrastructure.adb.connection

import android.content.Context
import com.screen.remote.android.core.common.AppConstants
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.i18n.AdbTexts
import dadb.AdbOperationFailedException
import dadb.Dadb
import dadb.InstallResult
import dadb.SyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.source
import java.io.File

/**
 * ADB 文件操作扩展
 * 提供文件传输、APK 安装等功能
 */
object AdbFileOperations {
    /**
     * 推送文件
     */
    suspend fun pushFile(
        dadb: Dadb,
        localPath: String,
        remotePath: String,
        onProgressBytes: (Long) -> Unit = {},
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(localPath)
                val source = file.source()
                val sourceWithProgress = object : Source {
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        val read = source.read(sink, byteCount)
                        if (read > 0L) {
                            onProgressBytes(read)
                        }
                        return read
                    }

                    override fun timeout(): Timeout = source.timeout()

                    override fun close() {
                        source.close()
                    }
                }
                when (val operation = dadb.push(sourceWithProgress, remotePath, 0b110100100, file.lastModified())) {
                    SyncResult.Success -> Unit
                    is SyncResult.Failure -> throw AdbOperationFailedException(operation.reason)
                }
                LogManager.d(
                    LogTags.ADB_CONNECTION,
                    "${AdbTexts.ADB_FILE_PUSH_SUCCESS.english}: $localPath -> $remotePath",
                )
                Result.success(true)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_FILE_PUSH_FAILED.english}: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * 从输入流直接推送文件（跳过本地缓存，提升上传速度）
     */
    suspend fun pushStream(
        dadb: Dadb,
        inputStream: java.io.InputStream,
        remotePath: String,
        fileSize: Long,
        lastModified: Long = System.currentTimeMillis(),
        onProgressBytes: (Long) -> Unit = {},
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val source = inputStream.source()
                val sourceWithProgress = object : Source {
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        val read = source.read(sink, byteCount)
                        if (read > 0L) {
                            onProgressBytes(read)
                        }
                        return read
                    }

                    override fun timeout(): Timeout = source.timeout()

                    override fun close() {
                        source.close()
                        inputStream.close()
                    }
                }
                when (val operation = dadb.push(sourceWithProgress, remotePath, 0b110100100, lastModified)) {
                    SyncResult.Success -> Unit
                    is SyncResult.Failure -> throw AdbOperationFailedException(operation.reason)
                }
                LogManager.d(
                    LogTags.ADB_CONNECTION,
                    "${AdbTexts.ADB_FILE_PUSH_SUCCESS.english}: stream -> $remotePath ($fileSize bytes)",
                )
                Result.success(true)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_FILE_PUSH_FAILED.english}: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * 拉取文件
     */
    suspend fun pullFile(
        dadb: Dadb,
        remotePath: String,
        localPath: String,
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(localPath)
                when (val operation = dadb.pull(file, remotePath)) {
                    SyncResult.Success -> Unit
                    is SyncResult.Failure -> throw AdbOperationFailedException(operation.reason)
                }
                LogManager.d(
                    LogTags.ADB_CONNECTION,
                    "${AdbTexts.ADB_FILE_PULL_SUCCESS.english}: $remotePath -> $localPath",
                )
                Result.success(true)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_FILE_PULL_FAILED.english}: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * 安装 APK
     */
    suspend fun installApk(
        dadb: Dadb,
        apkPath: String,
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(apkPath)
                when (val operation = dadb.install(file, "-r")) {
                    InstallResult.Success -> Unit
                    is InstallResult.Failure -> throw AdbOperationFailedException(operation.reason)
                }
                LogManager.d(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_APK_INSTALL_SUCCESS.english}: $apkPath")
                Result.success(true)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_APK_INSTALL_FAILED.english}: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * Install one base APK together with all of its split APKs.
     */
    suspend fun installApks(
        dadb: Dadb,
        apkPaths: List<String>,
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val apkFiles = apkPaths.distinct().map(::File)
                require(apkFiles.isNotEmpty()) { "No APK files provided" }
                val operation =
                    if (apkFiles.size == 1) {
                        dadb.install(apkFiles.single(), "-r")
                    } else {
                        dadb.installMultiple(apkFiles, "-r")
                    }
                when (operation) {
                    InstallResult.Success -> Unit
                    is InstallResult.Failure -> throw AdbOperationFailedException(operation.reason)
                }
                LogManager.d(
                    LogTags.ADB_CONNECTION,
                    "${AdbTexts.ADB_APK_INSTALL_SUCCESS.english}: ${apkFiles.joinToString()}"
                )
                Result.success(true)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_APK_INSTALL_FAILED.english}: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * 推送 scrcpy-server.jar 到设备
     * @param context Android Context，用于访问 assets
     * @param scrcpyServerPath 远程路径，默认 /data/local/tmp/scrcpy-server.jar
     */
    suspend fun pushScrcpyServer(
        dadb: Dadb,
        context: Context,
        scrcpyServerPath: String = AppConstants.SCRCPY_SERVER_PATH,
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                try {
                    context.assets.open("scrcpy-server.jar").use { input ->
                        val tempFile = context.cacheDir.resolve("scrcpy-server.jar")
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }

                        val pushResult = pushFile(dadb, tempFile.absolutePath, scrcpyServerPath)
                        if (pushResult.isFailure) {
                            return@withContext pushResult
                        }
                    }
                } catch (e: Exception) {
                    return@withContext Result.failure(
                        Exception(AdbTexts.ADB_SCRCPY_SERVER_NOT_IN_ASSETS.get() + ": ${e.message}"),
                    )
                }

                Result.success(true)
            } catch (e: Exception) {
                LogManager.e(
                    LogTags.ADB_CONNECTION,
                    "${AdbTexts.ADB_PUSH_SCRCPY_SERVER_FAILED.english}: ${e.message}",
                    e
                )
                Result.failure(e)
            }
        }
}

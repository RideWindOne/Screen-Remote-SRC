package com.mobile.scrcpy.android.infrastructure.adb.connection

import android.content.Context
import com.mobile.scrcpy.android.core.common.AppConstants
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(localPath)
                dadb.push(file, remotePath)
                LogManager.d(
                    LogTags.ADB_CONNECTION,
                    "${AdbTexts.ADB_FILE_PUSH_SUCCESS.get()}: $localPath -> $remotePath",
                )
                Result.success(true)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_FILE_PUSH_FAILED.get()}: ${e.message}", e)
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
                dadb.pull(file, remotePath)
                LogManager.d(
                    LogTags.ADB_CONNECTION,
                    "${AdbTexts.ADB_FILE_PULL_SUCCESS.get()}: $remotePath -> $localPath",
                )
                Result.success(true)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_FILE_PULL_FAILED.get()}: ${e.message}", e)
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
                dadb.install(file)
                LogManager.d(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_APK_INSTALL_SUCCESS.get()}: $apkPath")
                Result.success(true)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_APK_INSTALL_FAILED.get()}: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * 卸载应用
     */
    suspend fun uninstallPackage(
        dadb: Dadb,
        packageName: String,
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                dadb.uninstall(packageName)
                LogManager.d(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_APP_UNINSTALL_SUCCESS.get()}: $packageName")
                Result.success(true)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_APP_UNINSTALL_FAILED.get()}: ${e.message}", e)
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

                        // 设置执行权限
                        dadb.shell("chmod 755 $scrcpyServerPath")
                    }
                } catch (e: Exception) {
                    return@withContext Result.failure(
                        Exception(AdbTexts.ADB_SCRCPY_SERVER_NOT_IN_ASSETS.get() + ": ${e.message}"),
                    )
                }

                Result.success(true)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_PUSH_SCRCPY_SERVER_FAILED.get()}: ${e.message}", e)
                Result.failure(e)
            }
        }
}

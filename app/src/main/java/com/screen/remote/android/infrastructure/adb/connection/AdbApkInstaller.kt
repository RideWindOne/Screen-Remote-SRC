package com.screen.remote.android.infrastructure.adb.connection

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

suspend fun installApkFromUri(
    context: Context,
    connection: AdbConnection,
    uri: Uri,
): Result<Boolean> =
    stageAndInstallApk(
        cacheDir = context.cacheDir,
        openInputStream = { context.contentResolver.openInputStream(uri) },
        install = connection::installApk,
    )

internal suspend fun stageAndInstallApk(
    cacheDir: File,
    openInputStream: () -> InputStream?,
    install: suspend (String) -> Result<Boolean>,
): Result<Boolean> =
    withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            val tempDir = File(cacheDir, "adb-apk-install").apply {
                check(exists() || mkdirs()) { "Unable to create the temporary APK directory." }
            }
            tempFile = File.createTempFile("picked-", ".apk", tempDir)
            openInputStream()?.use { input ->
                tempFile.outputStream().use(input::copyTo)
            } ?: error("Unable to read the selected APK.")
            install(tempFile.absolutePath)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            tempFile?.delete()
        }
    }

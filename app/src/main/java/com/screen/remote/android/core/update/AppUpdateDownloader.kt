package com.screen.remote.android.core.update

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class DownloadedUpdate(
    val file: File,
    val contentUri: Uri,
)

class AppUpdateDownloader(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val downloadManager = appContext.getSystemService(DownloadManager::class.java)

    suspend fun download(
        asset: GitHubReleaseAsset,
        onProgress: (Int?) -> Unit,
    ): DownloadedUpdate =
        withContext(Dispatchers.IO) {
            val downloadRoot =
                appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: throw IOException("External download storage is unavailable")
            val updateDir = File(downloadRoot, "updates")
            if (!updateDir.exists() && !updateDir.mkdirs()) {
                throw IOException("Cannot create the update download directory")
            }
            val safeName =
                asset.name
                    .substringAfterLast('/')
                    .replace(Regex("[^A-Za-z0-9._-]"), "_")
                    .takeIf { it.isNotBlank() }
                    ?: "update.apk"
            val target = File(updateDir, safeName)
            if (target.exists() && !target.delete()) {
                throw IOException("Cannot replace the existing update file")
            }

            val request =
                DownloadManager.Request(asset.downloadUrl.toUri())
                    .setTitle(asset.name)
                    .setDescription("Screen Remote update")
                    .setMimeType(APK_MIME_TYPE)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(
                        appContext,
                        Environment.DIRECTORY_DOWNLOADS,
                        "updates/$safeName",
                    )
            val downloadId = downloadManager.enqueue(request)

            try {
                waitForDownload(downloadId, asset.sizeBytes, onProgress)
                verifyDownloadedFile(target, asset)
                validateApkPackage(target)
                DownloadedUpdate(
                    file = target,
                    contentUri =
                        FileProvider.getUriForFile(
                            appContext,
                            "${appContext.packageName}.fileprovider",
                            target,
                        ),
                )
            } catch (cancelled: CancellationException) {
                downloadManager.remove(downloadId)
                target.delete()
                throw cancelled
            } catch (failure: Throwable) {
                downloadManager.remove(downloadId)
                target.delete()
                throw failure
            }
        }

    private suspend fun waitForDownload(
        downloadId: Long,
        expectedSize: Long,
        onProgress: (Int?) -> Unit,
    ) {
        while (true) {
            coroutineContext.ensureActive()
            downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                if (!cursor.moveToFirst()) throw IOException("Update download disappeared")
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val reportedTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val total = reportedTotal.takeIf { it > 0 } ?: expectedSize.takeIf { it > 0 }
                val progress = total?.let { ((downloaded.coerceAtLeast(0) * 100) / it).toInt().coerceIn(0, 100) }
                withContext(Dispatchers.Main.immediate) { onProgress(progress) }
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> return
                    DownloadManager.STATUS_FAILED -> {
                        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        throw IOException("DownloadManager failed with reason $reason")
                    }
                }
            }
            delay(DOWNLOAD_POLL_INTERVAL_MS)
        }
    }

    private fun verifyDownloadedFile(
        file: File,
        asset: GitHubReleaseAsset,
    ) {
        if (!file.isFile || file.length() <= 0) throw IOException("Downloaded APK is empty")
        if (asset.sizeBytes > 0 && file.length() != asset.sizeBytes) {
            throw IOException("Downloaded APK size does not match the release asset")
        }
        val expectedDigest = asset.sha256 ?: return
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actualDigest = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actualDigest.equals(expectedDigest, ignoreCase = true)) {
            throw IOException("Downloaded APK SHA-256 does not match the GitHub release asset")
        }
    }

    @Suppress("DEPRECATION")
    private fun validateApkPackage(file: File) {
        val packageInfo =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageArchiveInfo(
                    file.absolutePath,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                appContext.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            }
        if (packageInfo?.packageName != appContext.packageName) {
            throw IOException("Downloaded APK package name is invalid")
        }
    }

    companion object {
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val DOWNLOAD_POLL_INTERVAL_MS = 350L

        fun canInstallPackages(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

        @SuppressLint("InlinedApi")
        fun unknownSourcesSettingsIntent(context: Context): Intent =
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri(),
            )

        fun install(context: Context, update: DownloadedUpdate) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(update.contentUri, APK_MIME_TYPE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

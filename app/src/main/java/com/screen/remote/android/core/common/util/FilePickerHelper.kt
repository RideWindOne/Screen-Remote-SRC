package com.screen.remote.android.core.common.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/**
 * 文件选择器辅助类
 * 统一管理导入导出文件选择器
 */
object FilePickerHelper {
    val DOWNLOADS_DIRECTORY_URI: Uri =
        DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Download",
        )

    /**
     * 创建单文件导出选择器
     * @param mimeType MIME 类型，如 "application/json"
     * @param onResult 选择结果回调
     */
    @Composable
    fun rememberExportFileLauncher(
        mimeType: String = "application/json",
        initialDirectoryUri: Uri? = null,
        onResult: (Uri?) -> Unit,
    ): ManagedActivityResultLauncher<String, Uri?> =
        rememberLauncherForActivityResult(
            contract = CreateDocumentAtLocation(mimeType, initialDirectoryUri),
            onResult = onResult,
        )

    /**
     * 创建单文件导入选择器
     * @param mimeTypes MIME 类型数组，如 arrayOf("application/json")
     * @param onResult 选择结果回调
     */
    @Composable
    fun rememberImportFileLauncher(
        mimeTypes: Array<String> = arrayOf("*/*"),
        initialDirectoryUri: Uri? = null,
        onResult: (Uri?) -> Unit,
    ): ManagedActivityResultLauncher<Array<String>, Uri?> =
        rememberLauncherForActivityResult(
            contract = OpenDocumentAtLocation(initialDirectoryUri),
            onResult = onResult,
        )

    @Composable
    fun rememberImportMultipleFilesLauncher(
        onResult: (List<Uri>) -> Unit,
    ): ManagedActivityResultLauncher<Array<String>, List<Uri>> =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments(),
            onResult = onResult,
        )


    private class CreateDocumentAtLocation(
        mimeType: String,
        private val initialDirectoryUri: Uri?,
    ) : ActivityResultContracts.CreateDocument(mimeType) {
        override fun createIntent(
            context: Context,
            input: String,
        ): Intent = super.createIntent(context, input).withInitialDirectory(initialDirectoryUri)
    }

    private class OpenDocumentAtLocation(
        private val initialDirectoryUri: Uri?,
    ) : ActivityResultContracts.OpenDocument() {
        override fun createIntent(
            context: Context,
            input: Array<String>,
        ): Intent = super.createIntent(context, input).withInitialDirectory(initialDirectoryUri)
    }

    private fun Intent.withInitialDirectory(uri: Uri?): Intent =
        apply {
            if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
            }
        }
}

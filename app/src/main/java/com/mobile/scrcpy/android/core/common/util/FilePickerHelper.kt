package com.mobile.scrcpy.android.core.common.util

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/**
 * 文件选择器辅助类
 * 统一管理导入导出文件选择器
 */
object FilePickerHelper {
    /**
     * 创建单文件导出选择器
     * @param mimeType MIME 类型，如 "application/json"
     * @param onResult 选择结果回调
     */
    @Composable
    fun rememberExportFileLauncher(
        mimeType: String = "application/json",
        onResult: (Uri?) -> Unit,
    ): ManagedActivityResultLauncher<String, Uri?> =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument(mimeType),
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
        onResult: (Uri?) -> Unit,
    ): ManagedActivityResultLauncher<Array<String>, Uri?> =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = onResult,
        )

    @Composable
    fun rememberImportMultipleFilesLauncher(
        mimeTypes: Array<String> = arrayOf("*/*"),
        onResult: (List<Uri>) -> Unit,
    ): ManagedActivityResultLauncher<Array<String>, List<Uri>> =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments(),
            onResult = onResult,
        )
}

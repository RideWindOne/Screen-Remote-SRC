package com.screen.remote.android.core.designsystem.component

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.content.FileProvider
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.i18n.LogTexts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun rememberLogViewerActions(
    context: Context,
    file: File,
    scope: CoroutineScope,
    state: LogViewerState,
    onDismiss: () -> Unit,
): LogViewerActions =
    remember(context, file, scope, state, onDismiss) {
        LogViewerActions(
            context = context,
            file = file,
            scope = scope,
            state = state,
            onDismiss = onDismiss,
        )
    }

internal class LogViewerActions(
    private val context: Context,
    private val file: File,
    private val scope: CoroutineScope,
    private val state: LogViewerState,
    private val onDismiss: () -> Unit,
) {
    fun loadLogContent() {
        scope.launch {
            if (file.length() > MAX_FILE_SIZE) {
                state.showFileTooLargeDialog()
                return@launch
            }

            val content =
                withContext(Dispatchers.IO) {
                    LogManager.readLogFile(file)
                }
            state.updateLogContent(content)
            state.updateAvailableTags(extractLogTags(content))
        }
    }

    fun shareLogFile() {
        try {
            val uri =
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )

            val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Scrcpy Log - ${file.name}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

            context.startActivity(Intent.createChooser(shareIntent, LogTexts.LOG_SHARE_BUTTON.get()))
        } catch (e: Exception) {
            LogManager.e("LogViewerDialog", "分享日志文件失败: ${e.message}")
        }
    }

    fun clearAndRetry() {
        state.dismissFileTooLargeDialog()
        scope.launch {
            withContext(Dispatchers.IO) {
                LogManager.clearAllLogs()
            }
            onDismiss()
        }
    }

    companion object {
        private const val MAX_FILE_SIZE = 1024 * 1024L
    }
}

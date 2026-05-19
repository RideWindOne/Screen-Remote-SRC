package com.mobile.scrcpy.android.feature.session.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.AppColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SessionManagementFileBrowser(
    modifier: Modifier = Modifier,
    refreshToken: Int,
    externalAddMenuRequestTick: Int,
    onSelectionModeChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var localRefreshTick by remember { mutableIntStateOf(0) }
    var currentPath by remember { mutableStateOf("/sdcard") }
    var selectedFile by remember { mutableStateOf<RemoteFileEntry?>(null) }
    var fileDetailEntry by remember { mutableStateOf<RemoteFileEntry?>(null) }
    var textEditorState by remember { mutableStateOf<RemoteTextEditorState?>(null) }
    var imagePreviewState by remember { mutableStateOf<RemotePreparedFileState?>(null) }
    var videoPreviewState by remember { mutableStateOf<RemotePreparedFileState?>(null) }
    var audioPreviewState by remember { mutableStateOf<RemotePreparedFileState?>(null) }
    var binaryPreviewState by remember { mutableStateOf<RemoteBinaryPreviewState?>(null) }
    var overwriteConfirmState by remember { mutableStateOf<RemoteOverwriteConfirmState?>(null) }
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    var addMenuOpen by remember { mutableStateOf(false) }
    var handledAddMenuRequestTick by remember { mutableIntStateOf(externalAddMenuRequestTick) }
    var createFolderDialogOpen by remember { mutableStateOf(false) }
    var createFileDialogOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<RemoteFileEntry?>(null) }
    var deleteTargets by remember { mutableStateOf<List<RemoteFileEntry>>(emptyList()) }
    var fileActionMessage by remember { mutableStateOf<String?>(null) }
    var fileActionProgress by remember { mutableStateOf<String?>(null) }
    val isSelectionMode = selectedPaths.isNotEmpty()
    val fileSnapshot by produceState(
        initialValue = FileBrowserSnapshot.loading(currentPath),
        key1 = currentPath,
        key2 = refreshToken,
        key3 = localRefreshTick,
    ) {
        value = loadFileBrowserSnapshot(currentPath)
    }
    val selectedEntries = fileSnapshot.entries.filter { it.fullPath in selectedPaths }

    LaunchedEffect(externalAddMenuRequestTick) {
        if (externalAddMenuRequestTick > handledAddMenuRequestTick) {
            addMenuOpen = true
            handledAddMenuRequestTick = externalAddMenuRequestTick
        }
    }

    LaunchedEffect(isSelectionMode) {
        onSelectionModeChanged(isSelectionMode)
    }

    BackHandler(enabled = isSelectionMode || currentPath != "/") {
        when {
            isSelectionMode -> {
                selectedPaths = emptySet()
            }

            else -> {
                currentPath = navigateFileBrowserUp(currentPath)
                selectedPaths = emptySet()
            }
        }
    }

    fun refreshCurrentDirectory() {
        localRefreshTick += 1
    }

    fun launchFileAction(
        progress: String,
        block: suspend () -> Result<String>,
    ) {
        fileActionProgress = progress
        scope.launch {
            val result = block()
            fileActionProgress = null
            fileActionMessage = result.getOrNull() ?: (result.exceptionOrNull()?.message ?: managementText("文件操作失败", "File action failed"))
            selectedPaths = emptySet()
            refreshCurrentDirectory()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = managementPanelColor(),
                tonalElevation = 0.5.dp,
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = fileSnapshot.currentPath.ifBlank { currentPath },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                    ) {
                        Text(
                            text = if (currentPath == "/") managementText("返回 sdcard", "Back to sdcard") else managementText("返回上一级", "Go up"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable {
                                        currentPath = navigateFileBrowserUp(currentPath)
                                        selectedPaths = emptySet()
                                    }.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                color = managementPanelColor(),
                tonalElevation = 1.dp,
            ) {
                when {
                    fileSnapshot.isLoading -> {
                        SessionManagementFileListSkeleton()
                    }

                    fileSnapshot.errorMessage != null -> {
                        SessionManagementNoteCard(
                            title = managementText("目录读取失败", "Couldn't load folder"),
                            text = fileSnapshot.errorMessage ?: managementText("目录读取失败。", "Couldn't load this folder."),
                        )
                    }

                    fileSnapshot.entries.isEmpty() -> {
                        SessionManagementNoteCard(
                            title = managementText("目录为空", "Folder is empty"),
                            text = managementText("当前目录没有可展示的文件或文件夹。", "No files or folders here."),
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = if (isSelectionMode) 82.dp else 76.dp),
                        ) {
                            items(fileSnapshot.entries.size) { index ->
                                val entry = fileSnapshot.entries[index]
                                SessionManagementFileRow(
                                    entry = entry,
                                    selected = entry.fullPath in selectedPaths,
                                    selectionMode = isSelectionMode,
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedPaths =
                                                selectedPaths.toMutableSet().apply {
                                                    if (!add(entry.fullPath)) {
                                                        remove(entry.fullPath)
                                                    }
                                                }
                                        } else if (entry.isDirectory) {
                                            currentPath = entry.fullPath
                                            selectedPaths = emptySet()
                                        } else {
                                            selectedFile = entry
                                        }
                                    },
                                    onLongPress = {
                                        selectedPaths = (selectedPaths + entry.fullPath).toSet()
                                    },
                                )
                                if (index != fileSnapshot.entries.lastIndex) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(start = 64.dp, end = 16.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                                ).height(1.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isSelectionMode) {
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                color = managementPanelColor(),
                tonalElevation = 0.5.dp,
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SessionManagementBottomIconAction(
                        icon = Icons.Default.Download,
                        label = managementText("下载", "Download"),
                        showLabel = false,
                        onClick = { fileActionMessage = managementText("暂不支持批量下载。", "Bulk download isn't available yet.") },
                    )
                    SessionManagementBottomIconAction(
                        icon = Icons.Default.ContentCopy,
                        label = managementText("复制", "Copy"),
                        showLabel = false,
                        onClick = { fileActionMessage = managementText("暂不支持复制。", "Copy isn't available yet.") },
                    )
                    SessionManagementBottomIconAction(
                        icon = Icons.Default.ContentCut,
                        label = managementText("剪切", "Cut"),
                        showLabel = false,
                        onClick = { fileActionMessage = managementText("暂不支持剪切。", "Cut isn't available yet.") },
                    )
                    SessionManagementBottomIconAction(
                        icon = Icons.Default.Edit,
                        label = managementText("重命名", "Rename"),
                        showLabel = false,
                        onClick = {
                            renameTarget = selectedEntries.singleOrNull()
                                ?: run {
                                    fileActionMessage = managementText("重命名仅支持单个文件或文件夹。", "Rename only supports a single file or folder.")
                                    null
                                }
                        },
                    )
                    SessionManagementBottomIconAction(
                        icon = androidx.compose.material.icons.Icons.Outlined.Info,
                        label = managementText("详情", "Details"),
                        showLabel = false,
                        onClick = {
                            fileDetailEntry = selectedEntries.singleOrNull()
                                ?: run {
                                    fileActionMessage = managementText("详情仅支持单个文件或文件夹。", "Details only supports a single file or folder.")
                                    null
                                }
                        },
                    )
                    SessionManagementBottomIconAction(
                        icon = Icons.Default.DeleteOutline,
                        label = managementText("删除", "Delete"),
                        showLabel = false,
                        onClick = {
                            if (selectedEntries.isNotEmpty()) {
                                deleteTargets = selectedEntries
                            }
                        },
                    )
                }
            }
        }
    }

    if (addMenuOpen) {
        SessionManagementFileAddDialog(
            onDismiss = { addMenuOpen = false },
            onCreateFolder = {
                addMenuOpen = false
                createFolderDialogOpen = true
            },
            onCreateFile = {
                addMenuOpen = false
                createFileDialogOpen = true
            },
            onUpload = {
                addMenuOpen = false
                fileActionMessage = managementText("本地上传暂不可用。", "Upload from device isn't available yet.")
            },
        )
    }

    if (createFolderDialogOpen) {
        SessionManagementTextInputDialog(
            title = managementText("新建文件夹", "New folder"),
            label = managementText("文件夹名称", "Folder name"),
            initialValue = "",
            confirmText = managementText("创建", "Create"),
            onDismiss = { createFolderDialogOpen = false },
            onConfirm = { folderName ->
                createFolderDialogOpen = false
                launchFileAction(progress = managementText("正在创建文件夹", "Creating folder")) {
                    runShellAction(
                        command = "mkdir -p ${quoteShellArg(joinRemotePath(currentPath, folderName))}",
                        successMessage = managementText("文件夹已创建。", "Folder created."),
                    )
                }
            },
        )
    }

    if (createFileDialogOpen) {
        SessionManagementTextInputDialog(
            title = managementText("新建文件", "New file"),
            label = managementText("文件名称", "File name"),
            initialValue = "",
            confirmText = managementText("创建", "Create"),
            onDismiss = { createFileDialogOpen = false },
            onConfirm = { fileName ->
                createFileDialogOpen = false
                launchFileAction(progress = managementText("正在创建文件", "Creating file")) {
                    runShellAction(
                        command = "touch ${quoteShellArg(joinRemotePath(currentPath, fileName))}",
                        successMessage = managementText("文件已创建。", "File created."),
                    )
                }
            },
        )
    }

    renameTarget?.let { entry ->
        SessionManagementTextInputDialog(
            title = managementText("重命名", "Rename"),
            label = managementText("新的名称", "New name"),
            initialValue = entry.name,
            confirmText = managementText("应用", "Apply"),
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                renameTarget = null
                launchFileAction(progress = managementText("正在重命名 ${entry.name}", "Renaming ${entry.name}")) {
                    runShellAction(
                        command =
                            "mv ${quoteShellArg(entry.fullPath)} " +
                                quoteShellArg(joinRemotePath(parentRemotePath(entry.fullPath), newName)),
                        successMessage = managementText("重命名已完成。", "Rename complete."),
                    )
                }
            },
        )
    }

    if (deleteTargets.isNotEmpty()) {
        SessionManagementFileDeleteDialog(
            targets = deleteTargets,
            onDismiss = { deleteTargets = emptyList() },
            onConfirm = {
                val targetPaths = deleteTargets.map { it.fullPath }
                deleteTargets = emptyList()
                launchFileAction(progress = managementText("正在删除 ${targetPaths.size} 项", "Deleting ${targetPaths.size} item(s)")) {
                    runShellAction(
                        command = "rm -rf ${targetPaths.joinToString(" ") { quoteShellArg(it) }}",
                        successMessage = managementText("删除已完成。", "Delete complete."),
                    )
                }
            },
        )
    }

    selectedFile?.let { entry ->
        SessionManagementFileActionDialog(
            entry = entry,
            fileKind = classifyRemoteFileKind(entry.name),
            canEdit =
                classifyRemoteFileKind(entry.name) !in
                    setOf(RemoteFileKind.Image, RemoteFileKind.Video, RemoteFileKind.Audio),
            canPushBack = getPreparedLocalFile(context, entry).exists(),
            onDismiss = { selectedFile = null },
            onPreview = {
                selectedFile = null
                fileActionProgress = managementText("正在准备预览文件", "Preparing preview")
                scope.launch {
                    val result = prepareRemoteFileForLocalOpen(context, entry)
                    fileActionProgress = null
                    result.fold(
                        onSuccess = { localFile ->
                            when (classifyRemoteFileKind(entry.name)) {
                                RemoteFileKind.Image -> {
                                    imagePreviewState = RemotePreparedFileState(entry, localFile)
                                }

                                RemoteFileKind.Video -> {
                                    videoPreviewState = RemotePreparedFileState(entry, localFile)
                                }

                                RemoteFileKind.Audio -> {
                                    audioPreviewState = RemotePreparedFileState(entry, localFile)
                                }

                                RemoteFileKind.Binary -> {
                                    binaryPreviewState =
                                        RemoteBinaryPreviewState(
                                            entry = entry,
                                            localFile = localFile,
                                            preview = readBinaryPreview(localFile),
                                        )
                                }

                                RemoteFileKind.Text -> {
                                    fileDetailEntry = entry
                                }
                            }
                        },
                        onFailure = { error ->
                            fileActionMessage = error.message ?: managementText("准备预览失败", "Couldn't prepare preview")
                        },
                    )
                }
            },
            onOpenExternal = {
                selectedFile = null
                fileActionProgress = managementText("正在准备本机临时文件", "Preparing local temp file")
                scope.launch {
                    val result =
                        prepareRemoteFileForLocalOpen(context, entry).mapCatching { file ->
                            openLocalFileExternal(context, file).getOrThrow()
                        }
                    fileActionProgress = null
                    result.exceptionOrNull()?.let { error ->
                        fileActionMessage = error.message ?: managementText("打开文件失败", "Couldn't open file")
                    }
                }
            },
            onPushBack = {
                selectedFile = null
                overwriteConfirmState = RemoteOverwriteConfirmState.PushBack(entry)
            },
            onEdit = {
                selectedFile = null
                fileActionProgress = managementText("正在加载文本文件", "Loading text file")
                scope.launch {
                    val result = loadRemoteTextEditorState(context, entry)
                    fileActionProgress = null
                    result.fold(
                        onSuccess = { editorState -> textEditorState = editorState },
                        onFailure = { error -> fileActionMessage = error.message ?: managementText("加载文本文件失败", "Couldn't load text file") },
                    )
                }
            },
            onDetails = {
                selectedFile = null
                fileDetailEntry = entry
            },
        )
    }

    fileDetailEntry?.let { entry ->
        SessionManagementFileDetailDialog(
            entry = entry,
            onDismiss = { fileDetailEntry = null },
        )
    }

    textEditorState?.let { editorState ->
        SessionManagementTextEditorDialog(
            state = editorState,
            onDismiss = { textEditorState = null },
            onSave = { content ->
                textEditorState = null
                launchFileAction(progress = managementText("正在保存并回写设备", "Saving and pushing back")) {
                    saveRemoteTextFile(editorState, content)
                }
            },
            onOpenExternal = {
                textEditorState = null
                fileActionProgress = managementText("正在准备本机临时文件", "Preparing local temp file")
                scope.launch {
                    val result =
                        prepareRemoteFileForLocalOpen(context, editorState.entry).mapCatching { file ->
                            openLocalFileExternal(context, file).getOrThrow()
                        }
                    fileActionProgress = null
                    result.exceptionOrNull()?.let { error ->
                        fileActionMessage = error.message ?: managementText("打开文件失败", "Couldn't open file")
                    }
                }
            },
        )
    }

    imagePreviewState?.let { state ->
        SessionManagementImagePreviewDialog(
            state = state,
            onDismiss = { imagePreviewState = null },
            onOpenExternal = {
                imagePreviewState = null
                scope.launch {
                    openLocalFileExternal(context, state.localFile)
                        .exceptionOrNull()
                        ?.let { error -> fileActionMessage = error.message ?: managementText("打开图片失败", "Couldn't open image") }
                }
            },
            onPushBack = {
                imagePreviewState = null
                overwriteConfirmState = RemoteOverwriteConfirmState.PushBack(state.entry)
            },
        )
    }

    videoPreviewState?.let { state ->
        SessionManagementVideoPreviewDialog(
            state = state,
            onDismiss = { videoPreviewState = null },
            onOpenExternal = {
                videoPreviewState = null
                scope.launch {
                    openLocalFileExternal(context, state.localFile)
                        .exceptionOrNull()
                        ?.let { error -> fileActionMessage = error.message ?: managementText("打开视频失败", "Couldn't open video") }
                }
            },
            onPushBack = {
                videoPreviewState = null
                overwriteConfirmState = RemoteOverwriteConfirmState.PushBack(state.entry)
            },
        )
    }

    audioPreviewState?.let { state ->
        SessionManagementAudioPreviewDialog(
            state = state,
            onDismiss = { audioPreviewState = null },
            onOpenExternal = {
                audioPreviewState = null
                scope.launch {
                    openLocalFileExternal(context, state.localFile)
                        .exceptionOrNull()
                        ?.let { error -> fileActionMessage = error.message ?: managementText("打开音频失败", "Couldn't open audio") }
                }
            },
            onPushBack = {
                audioPreviewState = null
                overwriteConfirmState = RemoteOverwriteConfirmState.PushBack(state.entry)
            },
        )
    }

    binaryPreviewState?.let { state ->
        SessionManagementBinaryPreviewDialog(
            state = state,
            onDismiss = { binaryPreviewState = null },
            onOpenExternal = {
                binaryPreviewState = null
                scope.launch {
                    openLocalFileExternal(context, state.localFile)
                        .exceptionOrNull()
                        ?.let { error -> fileActionMessage = error.message ?: managementText("打开文件失败", "Couldn't open file") }
                }
            },
            onPushBack = {
                binaryPreviewState = null
                overwriteConfirmState = RemoteOverwriteConfirmState.PushBack(state.entry)
            },
        )
    }

    overwriteConfirmState?.let { confirmState ->
        SessionManagementOverwriteConfirmDialog(
            state = confirmState,
            onDismiss = { overwriteConfirmState = null },
            onConfirm = {
                overwriteConfirmState = null
                when (confirmState) {
                    is RemoteOverwriteConfirmState.PushBack -> {
                        launchFileAction(progress = managementText("正在回写设备", "Pushing back to device")) {
                            pushPreparedLocalFileToDevice(context, confirmState.entry)
                        }
                    }
                }
            },
        )
    }

    fileActionProgress?.let { message ->
        SessionManagementProgressDialog(
            title = managementText("文件管理", "Files"),
            message = message,
        )
    }

    fileActionMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { fileActionMessage = null },
            title = { Text(managementText("文件管理", "Files")) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { fileActionMessage = null }) {
                    Text(managementText("确定", "OK"))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

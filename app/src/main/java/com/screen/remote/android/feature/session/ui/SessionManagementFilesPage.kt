package com.screen.remote.android.feature.session.ui

import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.net.Uri
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.common.AppColors
import com.screen.remote.android.core.common.util.FilePickerHelper
import com.screen.remote.android.core.i18n.ManagementTexts
import com.screen.remote.android.core.designsystem.component.ClickableBreadcrumb
import com.screen.remote.android.core.designsystem.component.ClickableBreadcrumbItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val BuiltInEditorUnsupportedFileKinds =
    setOf(RemoteFileKind.Image, RemoteFileKind.Video, RemoteFileKind.Audio)

private enum class RemoteFileClipboardOperation {
    Copy,
    Cut,
}

private data class RemoteFileClipboard(
    val operation: RemoteFileClipboardOperation,
    val entries: List<RemoteFileEntry>,
)

@SuppressLint("SdCardPath")
internal class SessionManagementFileBrowserState {
    val currentPathState = mutableStateOf("/sdcard")
    val selectedPathsState = mutableStateOf<Set<String>>(emptySet())
    val snapshotState = mutableStateOf(FileBrowserSnapshot.loading("/sdcard"))
    val snapshotPathState = mutableStateOf("/sdcard")
    val localRefreshTickState = mutableIntStateOf(0)
    var lastExternalRefreshToken: Int? = null
    var lastLocalRefreshTick: Int = 0
    private var prefetchedSnapshot: FileBrowserSnapshot? = null
    private val loadMutex = Mutex()

    suspend fun prefetchSnapshot(path: String): FileBrowserSnapshot =
        loadMutex.withLock {
            prefetchedSnapshot
                ?.takeIf { it.currentPath == path }
                ?.let { return@withLock it }
            loadFileBrowserSnapshot(path).also { snapshot ->
                if (!snapshot.isLoading && snapshot.errorMessage == null) {
                    prefetchedSnapshot = snapshot
                }
            }
        }

    suspend fun loadSnapshot(
        path: String,
        forceRefresh: Boolean = false,
    ): FileBrowserSnapshot =
        loadMutex.withLock {
            if (!forceRefresh) {
                prefetchedSnapshot
                    ?.takeIf { it.currentPath == path }
                    ?.let { snapshot ->
                        prefetchedSnapshot = null
                        return@withLock snapshot
                    }
            }
            loadFileBrowserSnapshot(path)
        }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SessionManagementFileBrowser(
    modifier: Modifier = Modifier,
    state: SessionManagementFileBrowserState,
    dataProvider: SessionManagementDataProvider,
    sessionId: String,
    refreshToken: Int,
    externalAddMenuRequestTick: Int,
    onSelectionModeChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var localRefreshTick by state.localRefreshTickState
    var currentPath by state.currentPathState
    var selectedFile by remember { mutableStateOf<RemoteFileEntry?>(null) }
    var fileDetailEntry by remember { mutableStateOf<RemoteFileEntry?>(null) }
    var textEditorState by remember { mutableStateOf<RemoteTextEditorState?>(null) }
    var imagePreviewState by remember { mutableStateOf<RemotePreparedFileState?>(null) }
    var videoPreviewState by remember { mutableStateOf<RemotePreparedFileState?>(null) }
    var binaryPreviewState by remember { mutableStateOf<RemoteBinaryPreviewState?>(null) }
    var overwriteConfirmState by remember { mutableStateOf<RemoteOverwriteConfirmState?>(null) }
    val audioPreviewPlayerState = remember { mutableStateOf<MediaPlayer?>(null) }
    var audioPreviewPlayer by audioPreviewPlayerState
    var fileClipboard by remember { mutableStateOf<RemoteFileClipboard?>(null) }
    var pendingDownloadEntries by remember { mutableStateOf<List<RemoteFileEntry>>(emptyList()) }
    var pendingUploadTargetDirectory by remember { mutableStateOf<String?>(null) }
    var selectedPaths by state.selectedPathsState
    var addMenuOpen by remember { mutableStateOf(false) }
    var handledAddMenuRequestTick by remember { mutableIntStateOf(externalAddMenuRequestTick) }
    var createFolderDialogOpen by remember { mutableStateOf(false) }
    var createFileDialogOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<RemoteFileEntry?>(null) }
    var deleteTargets by remember { mutableStateOf<List<RemoteFileEntry>>(emptyList()) }
    var fileActionMessage by remember { mutableStateOf<String?>(null) }
    var fileActionProgress by remember { mutableStateOf<String?>(null) }
    var fileSnapshot by state.snapshotState
    var fileSnapshotPath by state.snapshotPathState
    var fileRefreshing by remember { mutableStateOf(true) }
    val isSelectionMode by remember {
        derivedStateOf { selectedPaths.isNotEmpty() }
    }
    val selectedEntries by remember {
        derivedStateOf { fileSnapshot.entries.filter { it.fullPath in selectedPaths } }
    }
    val pathBreadcrumbItems by remember {
        derivedStateOf {
            buildRemotePathBreadcrumb(fileSnapshot.currentPath.ifBlank { currentPath })
                .map { ClickableBreadcrumbItem(label = it.label, value = it.path) }
        }
    }

    fun stopAudioPreview() {
        audioPreviewPlayer?.let { player ->
            runCatching { player.release() }
        }
        audioPreviewPlayer = null
    }

    DisposableEffect(Unit) {
        onDispose {
            audioPreviewPlayerState.value?.let { player ->
                runCatching { player.release() }
            }
            audioPreviewPlayerState.value = null
        }
    }

    LaunchedEffect(currentPath, refreshToken, localRefreshTick) {
        val refreshRequested =
            state.lastExternalRefreshToken?.let { it != refreshToken } == true ||
                state.lastLocalRefreshTick != localRefreshTick
        state.lastExternalRefreshToken = refreshToken
        state.lastLocalRefreshTick = localRefreshTick
        val pathChanged = fileSnapshotPath != currentPath
        val canKeepCurrentContent = !pathChanged && fileSnapshot.entries.isNotEmpty() && fileSnapshot.errorMessage == null
        if (pathChanged) {
            fileSnapshotPath = currentPath
            selectedPaths = emptySet()
            fileSnapshot = FileBrowserSnapshot.loading(currentPath)
        } else if (!refreshRequested && !fileSnapshot.isLoading && fileSnapshot.errorMessage == null) {
            fileRefreshing = false
            return@LaunchedEffect
        }
        fileRefreshing = true
        val nextSnapshot =
            dataProvider.loadFileInformation(
                sessionId = sessionId,
                path = currentPath,
                forceRefresh = refreshRequested,
            ) ?: return@LaunchedEffect
        fileRefreshing = false
        if (nextSnapshot.errorMessage != null && canKeepCurrentContent) {
            fileActionMessage = nextSnapshot.errorMessage
        } else {
            fileSnapshot = nextSnapshot
        }
    }

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
        onSuccess: () -> Unit = {},
        block: suspend () -> Result<String>,
    ) {
        fileActionProgress = progress
        scope.launch {
            val result = block()
            fileActionProgress = null
            fileActionMessage = result.getOrNull() ?: (result.exceptionOrNull()?.message ?: ManagementTexts.Files.FILE_ACTION_FAILED.get())
            if (result.isSuccess) {
                onSuccess()
            }
            selectedPaths = emptySet()
            refreshCurrentDirectory()
        }
    }

    fun savePendingDownload(destinationUri: Uri?) {
        val entriesToDownload = pendingDownloadEntries
        pendingDownloadEntries = emptyList()
        if (destinationUri != null && entriesToDownload.isNotEmpty()) {
            launchFileAction(
                progress = ManagementTexts.Files.DOWNLOADING_ITEM_S.format(entriesToDownload.size),
            ) {
                downloadRemoteEntriesToDocument(
                    context = context,
                    entries = entriesToDownload,
                    destinationUri = destinationUri,
                )
            }
        }
    }

    val singleFileDownloadLauncher =
        FilePickerHelper.rememberExportFileLauncher(
            mimeType = "*/*",
            initialDirectoryUri = FilePickerHelper.DOWNLOADS_DIRECTORY_URI,
            onResult = ::savePendingDownload,
        )
    val archiveDownloadLauncher =
        FilePickerHelper.rememberExportFileLauncher(
            mimeType = "application/zip",
            initialDirectoryUri = FilePickerHelper.DOWNLOADS_DIRECTORY_URI,
            onResult = ::savePendingDownload,
        )
    val uploadFileLauncher =
        FilePickerHelper.rememberImportMultipleFilesLauncher { sourceUris ->
            val targetDirectory = pendingUploadTargetDirectory
            pendingUploadTargetDirectory = null
            if (sourceUris.isNotEmpty() && targetDirectory != null) {
                launchFileAction(
                    progress = ManagementTexts.Files.UPLOADING_FILE_S.format(sourceUris.size),
                ) {
                    uploadLocalFilesToRemoteDirectory(
                        context = context,
                        sourceUris = sourceUris,
                        targetDirectory = targetDirectory,
                    )
                }
            }
        }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = SessionManagementCardShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ClickableBreadcrumb(
                        items = pathBreadcrumbItems,
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(end = 10.dp),
                        onItemClick = { item ->
                            currentPath = item.value
                            selectedPaths = emptySet()
                        },
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        tonalElevation = 0.dp,
                    ) {
                        Text(
                            text = if (currentPath == "/") ManagementTexts.Files.BACK_SDCARD.get() else ManagementTexts.Files.GO_UP.get(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
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
                shape = SessionManagementCardShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        fileSnapshot.isLoading -> {
                            SessionManagementFileListSkeleton()
                        }

                        fileSnapshot.errorMessage != null -> {
                            SessionManagementNoteCard(
                                title = ManagementTexts.Files.FOLDER_LOAD_FAILED_TITLE.get(),
                                text = fileSnapshot.errorMessage ?: ManagementTexts.Files.FOLDER_LOAD_FAILED_MESSAGE.get(),
                            )
                        }

                        fileSnapshot.entries.isEmpty() -> {
                            SessionManagementNoteCard(
                                title = ManagementTexts.Files.FOLDER_EMPTY.get(),
                                text = ManagementTexts.Files.NO_FILES_FOLDERS.get(),
                                modifier =
                                    Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 12.dp)
                                        .fillMaxWidth(),
                            )
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = if (isSelectionMode) 82.dp else 0.dp),
                            ) {
                                itemsIndexed(
                                    items = fileSnapshot.entries,
                                    key = { _, entry -> entry.fullPath },
                                ) { index, entry ->
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
                    if (fileRefreshing) {
                        SessionManagementLoadingBar(modifier = Modifier.align(Alignment.TopCenter))
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
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 1.dp,
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
                        label = ManagementTexts.Files.DOWNLOAD.get(),
                        iconTint = AppColors.info,
                        onClick = {
                            val entriesToDownload = selectedEntries.toList()
                            pendingDownloadEntries = entriesToDownload
                            if (entriesToDownload.size == 1 && !entriesToDownload.single().isDirectory) {
                                singleFileDownloadLauncher.launch(entriesToDownload.single().name)
                            } else {
                                archiveDownloadLauncher.launch(buildArchiveDownloadName(entriesToDownload))
                            }
                        },
                    )
                    SessionManagementBottomIconAction(
                        icon = Icons.Default.ContentCopy,
                        label = ManagementTexts.Files.COPY.get(),
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = {
                            fileClipboard =
                                RemoteFileClipboard(
                                    operation = RemoteFileClipboardOperation.Copy,
                                    entries = selectedEntries.toList(),
                                )
                            selectedPaths = emptySet()
                        },
                    )
                    SessionManagementBottomIconAction(
                        icon = Icons.Default.ContentCut,
                        label = ManagementTexts.Files.CUT.get(),
                        iconTint = AppColors.warning,
                        onClick = {
                            fileClipboard =
                                RemoteFileClipboard(
                                    operation = RemoteFileClipboardOperation.Cut,
                                    entries = selectedEntries.toList(),
                                )
                            selectedPaths = emptySet()
                        },
                    )
                    SessionManagementBottomIconAction(
                        icon = Icons.Default.Edit,
                        label = ManagementTexts.Files.RENAME.get(),
                        iconTint = AppColors.commandWindowAccent,
                        onClick = {
                            renameTarget = selectedEntries.singleOrNull()
                                ?: run {
                                    fileActionMessage = ManagementTexts.Files.RENAME_ONLY_SUPPORTS_SINGLE_FILE_FOLDER.get()
                                    null
                                }
                        },
                    )
                    SessionManagementBottomIconAction(
                        icon = Icons.Outlined.Info,
                        label = ManagementTexts.Files.DETAILS.get(),
                        iconTint = AppColors.info,
                        onClick = {
                            fileDetailEntry = selectedEntries.singleOrNull()
                                ?: run {
                                    fileActionMessage = ManagementTexts.Files.DETAILS_ONLY_SUPPORTS_SINGLE_FILE_FOLDER.get()
                                    null
                                }
                        },
                    )
                    SessionManagementBottomIconAction(
                        icon = Icons.Default.DeleteOutline,
                        label = ManagementTexts.Files.DELETE.get(),
                        iconTint = AppColors.destructive,
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
            canPaste = fileClipboard?.entries?.isNotEmpty() == true,
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
                pendingUploadTargetDirectory = currentPath
                uploadFileLauncher.launch(arrayOf("*/*"))
            },
            onPaste = {
                addMenuOpen = false
                val clipboard = fileClipboard ?: return@SessionManagementFileAddDialog
                val targetDirectory = currentPath
                launchFileAction(
                    progress =
                        when (clipboard.operation) {
                            RemoteFileClipboardOperation.Copy -> ManagementTexts.Files.COPYING_ITEM_S.format(clipboard.entries.size)
                            RemoteFileClipboardOperation.Cut -> ManagementTexts.Files.MOVING_ITEM_S.format(clipboard.entries.size)
                        },
                    onSuccess = {
                        if (clipboard.operation == RemoteFileClipboardOperation.Cut && fileClipboard == clipboard) {
                            fileClipboard = null
                        }
                    },
                ) {
                    when (clipboard.operation) {
                        RemoteFileClipboardOperation.Copy ->
                            copyRemoteEntries(
                                entries = clipboard.entries,
                                targetDirectory = targetDirectory,
                            )

                        RemoteFileClipboardOperation.Cut ->
                            moveRemoteEntries(
                                entries = clipboard.entries,
                                targetDirectory = targetDirectory,
                            )
                    }
                }
            },
        )
    }

    if (createFolderDialogOpen) {
        SessionManagementTextInputDialog(
            title = ManagementTexts.Files.NEW_FOLDER.get(),
            label = ManagementTexts.Files.FOLDER_NAME.get(),
            initialValue = "",
            confirmText = ManagementTexts.Files.CREATE.get(),
            onDismiss = { createFolderDialogOpen = false },
            onConfirm = { folderName ->
                createFolderDialogOpen = false
                launchFileAction(progress = ManagementTexts.Files.CREATING_FOLDER.get()) {
                    runShellAction(
                        command = "mkdir -p ${quoteShellArg(joinRemotePath(currentPath, folderName))}",
                        successMessage = ManagementTexts.Files.FOLDER_CREATED.get(),
                    )
                }
            },
        )
    }

    if (createFileDialogOpen) {
        SessionManagementTextInputDialog(
            title = ManagementTexts.Files.NEW_FILE.get(),
            label = ManagementTexts.Files.FILE_NAME.get(),
            initialValue = "",
            confirmText = ManagementTexts.Files.CREATE.get(),
            onDismiss = { createFileDialogOpen = false },
            onConfirm = { fileName ->
                createFileDialogOpen = false
                launchFileAction(progress = ManagementTexts.Files.CREATING_FILE.get()) {
                    runShellAction(
                        command = "touch ${quoteShellArg(joinRemotePath(currentPath, fileName))}",
                        successMessage = ManagementTexts.Files.FILE_CREATED.get(),
                    )
                }
            },
        )
    }

    renameTarget?.let { entry ->
        SessionManagementTextInputDialog(
            title = ManagementTexts.Files.RENAME.get(),
            label = ManagementTexts.Files.NEW_NAME.get(),
            initialValue = entry.name,
            confirmText = ManagementTexts.Files.APPLY.get(),
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                renameTarget = null
                launchFileAction(progress = ManagementTexts.Files.RENAMING.format(entry.name)) {
                    runShellAction(
                        command =
                            "mv ${quoteShellArg(entry.fullPath)} " +
                                quoteShellArg(joinRemotePath(parentRemotePath(entry.fullPath), newName)),
                        successMessage = ManagementTexts.Files.RENAME_COMPLETE.get(),
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
                launchFileAction(progress = ManagementTexts.Files.DELETING_ITEM_S.format(targetPaths.size)) {
                    runShellAction(
                        command = "rm -rf ${targetPaths.joinToString(" ") { quoteShellArg(it) }}",
                        successMessage = ManagementTexts.Files.DELETE_COMPLETE.get(),
                    )
                }
            },
        )
    }

    selectedFile?.let { entry ->
        val fileKind = classifyRemoteFileKind(entry.name)
        val canPushBack by produceState(
            initialValue = false,
            key1 = entry.fullPath,
        ) {
            value =
                withContext(Dispatchers.IO) {
                    getPreparedLocalFile(context, entry).exists()
                }
        }
        SessionManagementFileActionDialog(
            entry = entry,
            fileKind = fileKind,
            canEdit =
                fileKind !in BuiltInEditorUnsupportedFileKinds,
            canPushBack = canPushBack,
            onDismiss = {
                stopAudioPreview()
                selectedFile = null
            },
            onPreview = {
                if (fileKind != RemoteFileKind.Audio) {
                    selectedFile = null
                }
                fileActionProgress = ManagementTexts.Files.PREPARING_PREVIEW.get()
                scope.launch {
                    val result = prepareRemoteFileForLocalOpen(context, entry)
                    fileActionProgress = null
                    result.fold(
                        onSuccess = { localFile ->
                            when (fileKind) {
                                RemoteFileKind.Image -> {
                                    imagePreviewState = RemotePreparedFileState(entry, localFile)
                                }

                                RemoteFileKind.Video -> {
                                    videoPreviewState = RemotePreparedFileState(entry, localFile)
                                }

                                RemoteFileKind.Audio -> {
                                    stopAudioPreview()
                                    val player = MediaPlayer()
                                    audioPreviewPlayer = player
                                    player.setOnPreparedListener { preparedPlayer ->
                                        preparedPlayer.start()
                                    }
                                    player.setOnCompletionListener { completedPlayer ->
                                        if (audioPreviewPlayer === completedPlayer) {
                                            audioPreviewPlayer = null
                                        }
                                        completedPlayer.release()
                                    }
                                    player.setOnErrorListener { failedPlayer, _, _ ->
                                        if (audioPreviewPlayer === failedPlayer) {
                                            audioPreviewPlayer = null
                                        }
                                        failedPlayer.release()
                                        fileActionMessage = ManagementTexts.Files.COULDN_T_PLAY_AUDIO.get()
                                        true
                                    }
                                    runCatching {
                                        player.setDataSource(localFile.absolutePath)
                                        player.prepareAsync()
                                    }.onFailure { error ->
                                        if (audioPreviewPlayer === player) {
                                            audioPreviewPlayer = null
                                        }
                                        player.release()
                                        fileActionMessage = error.message ?: ManagementTexts.Files.COULDN_T_PLAY_AUDIO.get()
                                    }
                                }

                                RemoteFileKind.Binary -> {
                                    binaryPreviewState =
                                        RemoteBinaryPreviewState(
                                            entry = entry,
                                            localFile = localFile,
                                            preview =
                                                withContext(Dispatchers.IO) {
                                                    readBinaryPreview(localFile)
                                                },
                                        )
                                }

                                RemoteFileKind.Text -> {
                                    fileDetailEntry = entry
                                }
                            }
                        },
                        onFailure = { error ->
                            fileActionMessage = error.message ?: ManagementTexts.Files.COULDN_T_PREPARE_PREVIEW.get()
                        },
                    )
                }
            },
            onOpenExternal = {
                stopAudioPreview()
                selectedFile = null
                fileActionProgress = ManagementTexts.Files.PREPARING_LOCAL_TEMP_FILE.get()
                scope.launch {
                    val result =
                        prepareRemoteFileForLocalOpen(context, entry).mapCatching { file ->
                            openLocalFileExternal(context, file).getOrThrow()
                        }
                    fileActionProgress = null
                    result.exceptionOrNull()?.let { error ->
                        fileActionMessage = error.message ?: ManagementTexts.Files.COULDN_T_OPEN_FILE.get()
                    }
                }
            },
            onPushBack = {
                stopAudioPreview()
                selectedFile = null
                overwriteConfirmState = RemoteOverwriteConfirmState.PushBack(entry)
            },
            onEdit = {
                stopAudioPreview()
                selectedFile = null
                fileActionProgress = ManagementTexts.Files.LOADING_TEXT_FILE.get()
                scope.launch {
                    val result = loadRemoteTextEditorState(context, entry)
                    fileActionProgress = null
                    result.fold(
                        onSuccess = { editorState -> textEditorState = editorState },
                        onFailure = { error -> fileActionMessage = error.message ?: ManagementTexts.Files.COULDN_T_LOAD_TEXT_FILE.get() },
                    )
                }
            },
            onDetails = {
                stopAudioPreview()
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
                launchFileAction(progress = ManagementTexts.Files.SAVING_PUSHING_BACK.get()) {
                    saveRemoteTextFile(editorState, content)
                }
            },
            onOpenExternal = {
                textEditorState = null
                fileActionProgress = ManagementTexts.Files.PREPARING_LOCAL_TEMP_FILE.get()
                scope.launch {
                    val result =
                        prepareRemoteFileForLocalOpen(context, editorState.entry).mapCatching { file ->
                            openLocalFileExternal(context, file).getOrThrow()
                        }
                    fileActionProgress = null
                    result.exceptionOrNull()?.let { error ->
                        fileActionMessage = error.message ?: ManagementTexts.Files.COULDN_T_OPEN_FILE.get()
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
                        ?.let { error -> fileActionMessage = error.message ?: ManagementTexts.Files.COULDN_T_OPEN_IMAGE.get() }
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
                        ?.let { error -> fileActionMessage = error.message ?: ManagementTexts.Files.COULDN_T_OPEN_VIDEO.get() }
                }
            },
            onPushBack = {
                videoPreviewState = null
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
                        ?.let { error -> fileActionMessage = error.message ?: ManagementTexts.Files.COULDN_T_OPEN_FILE.get() }
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
                        launchFileAction(progress = ManagementTexts.Files.PUSHING_BACK_DEVICE.get()) {
                            pushPreparedLocalFileToDevice(context, confirmState.entry)
                        }
                    }
                }
            },
        )
    }

    fileActionProgress?.let { message ->
        SessionManagementProgressDialog(
            title = ManagementTexts.Files.FILES.get(),
            message = message,
        )
    }

    fileActionMessage?.let { message ->
        SessionManagementMessageDialog(
            title = ManagementTexts.Files.FILES.get(),
            message = message,
            onDismiss = { fileActionMessage = null },
        )
    }
}

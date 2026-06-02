package com.screen.remote.android.feature.session.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.screen.remote.android.core.common.AppColors
import com.screen.remote.android.core.designsystem.component.AppDivider
import com.screen.remote.android.core.i18n.ManagementTexts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val FilesDialogSpacing = 12.dp
private val FilesDialogButtonSpacing = 4.dp
private val FilesDetailRowHeight = 40.dp
private val FilesDetailContentHeight = 286.dp
private val FilesActionCardPadding = 8.dp
private val FilesActionCardSpacing = 6.dp
private val FilesMenuRowCornerRadius = 16.dp
private val FilesMenuRowHorizontalPadding = 12.dp
private val FilesMenuRowVerticalPadding = 12.dp
private val FilesMenuRowIconSize = 20.dp
private val FilesTextEditorHorizontalPadding = 16.dp
private val FilesTextEditorTopActionSpacing = 4.dp
private val FilesTextEditorMinHeight = 360.dp
private val FilesTextEditorMaxHeight = 520.dp
private val FilesImagePreviewHeight = 280.dp
private val FilesVideoPreviewHeight = 220.dp
private val FilesBinaryPreviewHeight = 260.dp
private val FilesBinaryPreviewHorizontalPadding = 12.dp
private val FilesBinaryPreviewVerticalPadding = 10.dp

private data class ImagePreviewDecodeState(
    val loading: Boolean = true,
    val bitmap: Bitmap? = null,
)

private data class RemoteFileIconPresentation(
    val icon: ImageVector,
    val tint: Color,
)

@Composable
internal fun SessionManagementFileRow(
    entry: RemoteFileEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val iconPresentation = remoteFileIconPresentation(entry)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        Color.Transparent
                    },
                ).combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongPress,
                ).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = iconPresentation.tint.copy(alpha = 0.1f),
        ) {
            Icon(
                imageVector = iconPresentation.icon,
                contentDescription = null,
                tint = iconPresentation.tint,
                modifier =
                    Modifier
                        .padding(10.dp)
                        .size(22.dp),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (entry.isDirectory) ManagementTexts.Files.FOLDER.get() else ManagementTexts.Files.FILE.get(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (!entry.isDirectory && entry.sizeBytes != null) {
                    Text(
                        text = formatFileSize(entry.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(
                    text = entry.detail,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (selectionMode) {
            Icon(
                imageVector = if (selected) Icons.Default.Check else Icons.Default.Add,
                contentDescription = if (selected) ManagementTexts.Files.SELECTED.get() else ManagementTexts.Files.NOT_SELECTED.get(),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun remoteFileIconPresentation(entry: RemoteFileEntry): RemoteFileIconPresentation {
    if (entry.isDirectory) {
        return RemoteFileIconPresentation(
            icon = Icons.Default.Folder,
            tint = MaterialTheme.colorScheme.primary,
        )
    }

    return when (entry.name.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
        "apk", "apks", "xapk" -> RemoteFileIconPresentation(Icons.Default.Android, AppColors.success)
        "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus" ->
            RemoteFileIconPresentation(Icons.Default.MusicNote, AppColors.commandMemoryAccent)
        "mov", "mp4", "m4v", "mkv", "avi", "webm", "3gp" ->
            RemoteFileIconPresentation(Icons.Default.Movie, AppColors.commandWindowAccent)
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif" ->
            RemoteFileIconPresentation(Icons.Default.Photo, AppColors.info)
        "pdf" -> RemoteFileIconPresentation(Icons.Default.PictureAsPdf, AppColors.destructive)
        "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz" ->
            RemoteFileIconPresentation(Icons.Default.Archive, AppColors.warning)
        "xml", "json", "kt", "java", "js", "ts", "html", "css", "sh", "py", "yaml", "yml" ->
            RemoteFileIconPresentation(Icons.Default.Code, AppColors.commandAppAccent)
        "txt", "log", "md", "csv", "ini", "conf" ->
            RemoteFileIconPresentation(Icons.Default.Description, AppColors.commandDeviceAccent)
        else ->
            RemoteFileIconPresentation(
                Icons.AutoMirrored.Filled.InsertDriveFile,
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}

@Composable
internal fun SessionManagementFileListSkeleton() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        repeat(6) { index ->
            SessionManagementFilePlaceholderRow(isDirectory = index % 3 != 1)
            if (index != 5) {
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

@Composable
private fun SessionManagementFilePlaceholderRow(isDirectory: Boolean) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color =
                if (isDirectory) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                } else {
                    MaterialTheme.colorScheme.background
                },
        ) {
            Box(
                modifier =
                    Modifier
                        .padding(10.dp)
                        .size(22.dp),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.56f)
                            .height(18.dp),
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(72.dp)
                            .height(14.dp),
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(44.dp)
                        .height(14.dp),
            )
        }
    }
}

@Composable
internal fun SessionManagementBottomIconAction(
    icon: ImageVector,
    label: String,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showLabel: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .clip(
                    RoundedCornerShape(12.dp),
                ).clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
        )
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SessionManagementFileAddDialog(
    canPaste: Boolean,
    onDismiss: () -> Unit,
    onCreateFolder: () -> Unit,
    onCreateFile: () -> Unit,
    onUpload: () -> Unit,
    onPaste: () -> Unit,
) {
    SessionManagementCenteredDialog(
        title = ManagementTexts.Files.ADD.get(),
        onDismiss = onDismiss,
        leftButtonText = ManagementTexts.Files.CANCEL.get(),
    ) {
        SessionManagementFileActionCard {
            SessionManagementActionRow(
                icon = Icons.Default.Folder,
                label = ManagementTexts.Files.NEW_FOLDER.get(),
                onClick = onCreateFolder,
            )
            SessionManagementActionRow(
                icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                label = ManagementTexts.Files.NEW_FILE.get(),
                onClick = onCreateFile,
            )
            SessionManagementActionRow(
                icon = Icons.Default.Download,
                label = ManagementTexts.Files.UPLOAD_FROM_DEVICE.get(),
                onClick = onUpload,
            )
            SessionManagementActionRow(
                icon = Icons.Default.ContentCopy,
                label = ManagementTexts.Files.PASTE.get(),
                enabled = canPaste,
                onClick = onPaste,
            )
        }
    }
}

@Composable
internal fun SessionManagementFileDeleteDialog(
    targets: List<RemoteFileEntry>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    SessionManagementCenteredDialog(
        title = ManagementTexts.Files.DELETE_ITEM_S.format(targets.size),
        onDismiss = onDismiss,
        leftButtonText = ManagementTexts.Files.CANCEL.get(),
        rightButtonText = ManagementTexts.Files.DELETE.get(),
        onRightButtonClick = onConfirm,
    ) {
        Text(ManagementTexts.Files.CAN_T_BE_UNDONE_PLEASE_CONFIRM.get())
        Text(
            text = targets.joinToString(separator = "\n") { it.name },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SessionManagementFileDetailDialog(
    entry: RemoteFileEntry,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val detail by produceState(
        initialValue = RemoteFileDetailSnapshot.loading(entry),
        key1 = entry.fullPath,
    ) {
        value = loadRemoteFileDetailSnapshot(entry)
    }

    SessionManagementCenteredDialog(
        title = detail.name,
        onDismiss = onDismiss,
    ) {
        val detailRows =
            listOf(
                ManagementTexts.Files.PATH.get() to detail.fullPath,
                ManagementTexts.Files.TYPE.get() to detail.typeLabel,
                ManagementTexts.Files.PERMISSIONS.get() to detail.permissions,
                ManagementTexts.Files.OWNER.get() to detail.owner,
                ManagementTexts.Files.GROUP.get() to detail.group,
                ManagementTexts.Files.SIZE.get() to detail.sizeLabel,
                ManagementTexts.Files.MODIFIED.get() to detail.modifiedTime,
            )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(FilesDetailContentHeight),
        ) {
            val errorMessage = detail.errorMessage
            if (errorMessage != null) {
                SessionManagementNoteCard(
                    title = ManagementTexts.Files.FILE_DETAILS_LOAD_FAILED_TITLE.get(),
                    text = errorMessage,
                )
            } else {
                SessionManagementDialogCard {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                    ) {
                        detailRows.forEachIndexed { index, (label, value) ->
                            if (detail.isLoading) {
                                SessionManagementInfoPlaceholderRow(
                                    label = label,
                                    rowMinHeight = FilesDetailRowHeight,
                                )
                            } else {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (index == 0) {
                                                    Modifier.combinedClickable(
                                                        onClick = {},
                                                        onLongClickLabel = ManagementTexts.Files.COPY_PATH.get(),
                                                        onLongClick = {
                                                            val clipboard =
                                                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                            clipboard.setPrimaryClip(
                                                                ClipData.newPlainText(
                                                                    ManagementTexts.Files.FILE_PATH.get(),
                                                                    detail.fullPath,
                                                                ),
                                                            )
                                                            Toast
                                                                .makeText(
                                                                    context,
                                                                    ManagementTexts.Files.PATH_COPIED.get(),
                                                                    Toast.LENGTH_SHORT,
                                                                ).show()
                                                        },
                                                    )
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                ) {
                                    SessionManagementInfoRow(
                                        label = label,
                                        value = value,
                                        rowMinHeight = FilesDetailRowHeight,
                                    )
                                }
                            }
                            if (index != detailRows.lastIndex) {
                                AppDivider(modifier = Modifier.padding(start = 104.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SessionManagementFileActionDialog(
    entry: RemoteFileEntry,
    fileKind: RemoteFileKind,
    canEdit: Boolean,
    canPushBack: Boolean,
    onDismiss: () -> Unit,
    onPreview: () -> Unit,
    onOpenExternal: () -> Unit,
    onPushBack: () -> Unit,
    onEdit: () -> Unit,
    onDetails: () -> Unit,
) {
    val previewLabel =
        when (fileKind) {
            RemoteFileKind.Image -> ManagementTexts.Files.IMAGE_PREVIEW.get()
            RemoteFileKind.Video -> ManagementTexts.Files.VIDEO_PREVIEW.get()
            RemoteFileKind.Audio -> ManagementTexts.Files.AUDIO_PREVIEW.get()
            RemoteFileKind.Binary -> ManagementTexts.Files.BINARY_PREVIEW.get()
            RemoteFileKind.Text -> null
        }
    SessionManagementCenteredDialog(
        title = entry.name,
        onDismiss = onDismiss,
        leftButtonText = ManagementTexts.Files.CANCEL.get(),
    ) {
        SessionManagementFileActionCard {
            previewLabel?.let {
                SessionManagementFileMenuRow(
                    icon = Icons.Default.Info,
                    label = it,
                    iconTint = AppColors.info,
                    onClick = onPreview,
                )
            }
            SessionManagementFileMenuRow(
                icon = Icons.Default.PlayArrow,
                label = ManagementTexts.Files.OPEN_EXTERNALLY.get(),
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = onOpenExternal,
            )
            if (fileKind != RemoteFileKind.Text) {
                SessionManagementFileMenuRow(
                    icon = Icons.Default.Upload,
                    label = ManagementTexts.Files.PUSH_BACK.get(),
                    iconTint = AppColors.success,
                    enabled = canPushBack,
                    onClick = onPushBack,
                )
            }
            SessionManagementFileMenuRow(
                icon = Icons.Default.Edit,
                label = ManagementTexts.Files.BUILT_IN_EDITOR.get(),
                iconTint = AppColors.warning,
                enabled = canEdit,
                onClick = onEdit,
            )
            SessionManagementFileMenuRow(
                icon = Icons.Outlined.Info,
                label = ManagementTexts.Files.DETAILS.get(),
                iconTint = AppColors.commandWindowAccent,
                onClick = onDetails,
            )
        }
    }
}

@Composable
internal fun SessionManagementTextEditorDialog(
    state: RemoteTextEditorState,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onOpenExternal: () -> Unit,
) {
    var content by remember(state.entry.fullPath, state.localFile.absolutePath) { mutableStateOf(state.content) }

    SessionManagementCenteredDialog(
        title = ManagementTexts.Files.EDIT.format(state.entry.name),
        onDismiss = onDismiss,
        widthRatio = 0.96f,
        maxHeightRatio = 0.9f,
        contentPadding = FilesTextEditorHorizontalPadding,
        rightButtonText = ManagementTexts.Files.SAVE.get(),
        onRightButtonClick = { onSave(content) },
    ) {
        Text(
            text = state.entry.fullPath,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(
                        min = FilesTextEditorMinHeight,
                        max = FilesTextEditorMaxHeight,
                    ),
            label = { Text(ManagementTexts.Files.FILE_CONTENT.get()) },
            singleLine = false,
            maxLines = Int.MAX_VALUE,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                FilesTextEditorTopActionSpacing,
                Alignment.End,
            ),
        ) {
            SessionManagementFileDialogButton(
                text = ManagementTexts.Files.OPEN_EXTERNALLY.get(),
                onClick = onOpenExternal,
            )
        }
    }
}

@Composable
internal fun SessionManagementImagePreviewDialog(
    state: RemotePreparedFileState,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit,
    onPushBack: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val targetWidthPx =
        with(density) {
            (configuration.screenWidthDp.dp * 0.9f).roundToPx()
        }
    val targetHeightPx = with(density) { FilesImagePreviewHeight.roundToPx() }
    val imageState by produceState(
        initialValue = ImagePreviewDecodeState(),
        key1 = state.localFile.absolutePath,
        key2 = targetWidthPx to targetHeightPx,
    ) {
        value =
            ImagePreviewDecodeState(
                loading = false,
                bitmap =
                    withContext(Dispatchers.IO) {
                        decodeSampledPreviewBitmap(
                            path = state.localFile.absolutePath,
                            targetWidthPx = targetWidthPx,
                            targetHeightPx = targetHeightPx,
                        )
                    },
            )
    }

    DisposableEffect(imageState.bitmap) {
        onDispose {
            imageState.bitmap?.recycle()
        }
    }

    SessionManagementFilePreviewDialog(
        title = ManagementTexts.Files.IMAGE_PREVIEW.get(),
        path = state.entry.fullPath,
        onDismiss = onDismiss,
        onOpenExternal = onOpenExternal,
        onPushBack = onPushBack,
    ) {
        val decodedBitmap = imageState.bitmap
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(FilesImagePreviewHeight),
            contentAlignment = Alignment.Center,
        ) {
            when {
                imageState.loading -> {
                    Text(ManagementTexts.Files.DECODING_IMAGE.get())
                }

                decodedBitmap != null -> {
                    Image(
                        bitmap = decodedBitmap.asImageBitmap(),
                        contentDescription = state.entry.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                else -> {
                    Text(ManagementTexts.Files.COULDN_T_DECODE_IMAGE.get())
                }
            }
        }
    }
}

private fun decodeSampledPreviewBitmap(
    path: String,
    targetWidthPx: Int,
    targetHeightPx: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        return null
    }

    var sampleSize = 1
    val safeTargetWidth = targetWidthPx.coerceAtLeast(1)
    val safeTargetHeight = targetHeightPx.coerceAtLeast(1)
    while (
        bounds.outWidth / (sampleSize * 2) >= safeTargetWidth &&
            bounds.outHeight / (sampleSize * 2) >= safeTargetHeight
    ) {
        sampleSize *= 2
    }

    return BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        },
    )
}

@Composable
internal fun SessionManagementVideoPreviewDialog(
    state: RemotePreparedFileState,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit,
    onPushBack: () -> Unit,
) {
    var videoView by remember(state.localFile.absolutePath) { mutableStateOf<VideoView?>(null) }

    DisposableEffect(state.localFile.absolutePath) {
        onDispose {
            videoView?.stopPlayback()
            videoView?.setOnPreparedListener(null)
            videoView?.setMediaController(null)
            videoView = null
        }
    }

    SessionManagementFilePreviewDialog(
        title = ManagementTexts.Files.VIDEO_PREVIEW.get(),
        path = state.entry.fullPath,
        onDismiss = onDismiss,
        onOpenExternal = onOpenExternal,
        onPushBack = onPushBack,
    ) {
        AndroidView(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(FilesVideoPreviewHeight),
            factory = { context ->
                VideoView(context).apply {
                    videoView = this
                    tag = state.localFile.absolutePath
                    setVideoPath(state.localFile.absolutePath)
                    setMediaController(MediaController(context).also { it.setAnchorView(this) })
                    setOnPreparedListener { start() }
                }
            },
            update = { view ->
                if (videoView !== view) {
                    videoView = view
                }
                if (view.tag != state.localFile.absolutePath) {
                    view.stopPlayback()
                    view.tag = state.localFile.absolutePath
                    view.setVideoPath(state.localFile.absolutePath)
                    view.setOnPreparedListener { it.start() }
                }
            },
        )
    }
}

@Composable
internal fun SessionManagementBinaryPreviewDialog(
    state: RemoteBinaryPreviewState,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit,
    onPushBack: () -> Unit,
) {
    val previewLines =
        remember(state.preview) {
            state.preview.lineSequence().toList()
        }

    SessionManagementFilePreviewDialog(
        title = ManagementTexts.Files.BINARY_PREVIEW.get(),
        path = state.entry.fullPath,
        onDismiss = onDismiss,
        onOpenExternal = onOpenExternal,
        onPushBack = onPushBack,
    ) {
        SessionManagementDialogCard {
            SelectionContainer {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(FilesBinaryPreviewHeight)
                            .padding(
                                horizontal = FilesBinaryPreviewHorizontalPadding,
                                vertical = FilesBinaryPreviewVerticalPadding,
                            ),
                ) {
                    itemsIndexed(
                        items = previewLines,
                        key = { index, _ -> index },
                    ) { _, line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SessionManagementOverwriteConfirmDialog(
    state: RemoteOverwriteConfirmState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    SessionManagementCenteredDialog(
        title = state.title,
        onDismiss = onDismiss,
        leftButtonText = ManagementTexts.Files.CANCEL.get(),
        rightButtonText = ManagementTexts.Files.OVERWRITE.get(),
        onRightButtonClick = onConfirm,
    ) {
        SessionManagementDialogMessage(state.message)
    }
}

@Composable
private fun SessionManagementFileActionCard(content: @Composable () -> Unit) {
    SessionManagementDialogCard {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = FilesActionCardPadding,
                        vertical = FilesActionCardPadding,
                    ),
            verticalArrangement = Arrangement.spacedBy(FilesActionCardSpacing),
        ) {
            content()
        }
    }
}

@Composable
private fun SessionManagementFilePreviewDialog(
    title: String,
    path: String,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit,
    onPushBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    SessionManagementCenteredDialog(
        title = title,
        onDismiss = onDismiss,
        widthRatio = 0.94f,
        maxHeightRatio = 0.84f,
    ) {
        Text(
            text = path,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        content()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                FilesDialogButtonSpacing,
                Alignment.End,
            ),
        ) {
            SessionManagementFileDialogButton(
                text = ManagementTexts.Files.OPEN_EXTERNALLY.get(),
                onClick = onOpenExternal,
            )
            SessionManagementFileDialogButton(
                text = ManagementTexts.Files.PUSH_BACK.get(),
                onClick = onPushBack,
            )
        }
    }
}

@Composable
private fun SessionManagementFileDialogButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        enabled = enabled,
        onClick = onClick,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SessionManagementFileMenuRow(
    icon: ImageVector,
    label: String,
    iconTint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(FilesMenuRowCornerRadius),
        color = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(FilesMenuRowCornerRadius))
                    .clickable(enabled = enabled, onClick = onClick)
                    .padding(
                        horizontal = FilesMenuRowHorizontalPadding,
                        vertical = FilesMenuRowVerticalPadding,
                    ),
            horizontalArrangement = Arrangement.spacedBy(FilesDialogSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint =
                    if (enabled) {
                        iconTint
                    } else {
                        iconTint.copy(alpha = 0.5f)
                    },
                modifier = Modifier.size(FilesMenuRowIconSize),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
            )
        }
    }
}

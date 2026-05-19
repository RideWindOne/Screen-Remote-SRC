package com.mobile.scrcpy.android.feature.session.ui

import android.media.MediaPlayer
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mobile.scrcpy.android.core.designsystem.component.AppDivider

private val FilesDialogSpacing = 12.dp
private val FilesDialogInfoSpacing = 10.dp
private val FilesDialogButtonSpacing = 4.dp
private val FilesDialogHeaderIconSize = 22.dp
private val FilesDialogLoadingIndicatorSize = 20.dp
private val FilesActionCardPadding = 8.dp
private val FilesActionCardSpacing = 6.dp
private val FilesMenuRowCornerRadius = 16.dp
private val FilesMenuRowHorizontalPadding = 12.dp
private val FilesMenuRowVerticalPadding = 12.dp
private val FilesMenuRowIconSize = 20.dp
private val FilesCardCornerRadius = 20.dp
private val FilesTextEditorHorizontalPadding = 16.dp
private val FilesTextEditorVerticalPadding = 12.dp
private val FilesTextEditorTopActionSpacing = 4.dp
private val FilesImagePreviewHeight = 280.dp
private val FilesVideoPreviewHeight = 220.dp
private val FilesBinaryPreviewHeight = 260.dp

@Composable
internal fun SessionManagementFileAddDialog(
    onDismiss: () -> Unit,
    onCreateFolder: () -> Unit,
    onCreateFile: () -> Unit,
    onUpload: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(managementText("新增", "Add")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SessionManagementActionRow(
                    icon = Icons.Default.Folder,
                    label = managementText("新建文件夹", "New folder"),
                    onClick = onCreateFolder,
                )
                SessionManagementActionRow(
                    icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                    label = managementText("新建文件", "New file"),
                    onClick = onCreateFile,
                )
                SessionManagementActionRow(
                    icon = Icons.Default.Download,
                    label = managementText("从本地上传", "Upload from device"),
                    onClick = onUpload,
                )
                SessionManagementActionRow(
                    icon = Icons.Default.ContentCopy,
                    label = managementText("粘贴", "Paste"),
                    enabled = false,
                    onClick = {},
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(managementText("取消", "Cancel"))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
internal fun SessionManagementFileDeleteDialog(
    targets: List<RemoteFileEntry>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(managementText("确认删除 ${targets.size} 项？", "Delete ${targets.size} item(s)?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(managementText("删除后不可恢复，请确认继续。", "This can't be undone. Please confirm."))
                Text(
                    text = targets.joinToString(separator = "\n") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(managementText("删除", "Delete"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(managementText("取消", "Cancel"))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
internal fun SessionManagementFileDetailDialog(
    entry: RemoteFileEntry,
    onDismiss: () -> Unit,
) {
    val detail by produceState(
        initialValue = RemoteFileDetailSnapshot.loading(entry),
        key1 = entry.fullPath,
    ) {
        value = loadRemoteFileDetailSnapshot(entry)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(FilesDialogSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector =
                        if (entry.isDirectory) {
                            Icons.Default.Folder
                        } else {
                            Icons.AutoMirrored.Filled.InsertDriveFile
                        },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(FilesDialogHeaderIconSize),
                )
                Text(detail.name)
            }
        },
        text = {
            when {
                detail.isLoading -> {
                    SessionManagementDialogCard {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(FilesDialogInfoSpacing),
                        ) {
                            listOf(
                                managementText("所在路径", "Path"),
                                managementText("类型", "Type"),
                                managementText("权限", "Permissions"),
                                managementText("所有者", "Owner"),
                                managementText("用户组", "Group"),
                                managementText("大小", "Size"),
                                managementText("最后修改时间", "Modified"),
                            ).forEachIndexed { index, label ->
                                SessionManagementInfoPlaceholderRow(label = label)
                                if (index != 6) {
                                    AppDivider(modifier = Modifier.padding(start = 104.dp))
                                }
                            }
                        }
                    }
                }

                detail.errorMessage != null -> {
                    detail.errorMessage?.let { Text(it) }
                }

                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(FilesDialogInfoSpacing)) {
                        SessionManagementInfoRow(label = managementText("所在路径", "Path"), value = detail.fullPath)
                        SessionManagementInfoRow(label = managementText("类型", "Type"), value = detail.typeLabel)
                        SessionManagementInfoRow(label = managementText("权限", "Permissions"), value = detail.permissions)
                        SessionManagementInfoRow(label = managementText("所有者", "Owner"), value = detail.owner)
                        SessionManagementInfoRow(label = managementText("用户组", "Group"), value = detail.group)
                        SessionManagementInfoRow(label = managementText("大小", "Size"), value = detail.sizeLabel)
                        SessionManagementInfoRow(label = managementText("最后修改时间", "Modified"), value = detail.modifiedTime)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(managementText("确定", "OK"))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
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
            RemoteFileKind.Image -> managementText("图片预览", "Image preview")
            RemoteFileKind.Video -> managementText("视频预览", "Video preview")
            RemoteFileKind.Audio -> managementText("音频预览", "Audio preview")
            RemoteFileKind.Binary -> managementText("二进制预览", "Binary preview")
            RemoteFileKind.Text -> null
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name) },
        text = {
            SessionManagementFileActionCard {
                previewLabel?.let {
                    SessionManagementFileMenuRow(
                        icon = Icons.Default.Info,
                        label = it,
                        onClick = onPreview,
                    )
                }
                SessionManagementFileMenuRow(
                    icon = Icons.Default.PlayArrow,
                    label = managementText("外部打开", "Open externally"),
                    onClick = onOpenExternal,
                )
                if (fileKind != RemoteFileKind.Text) {
                    SessionManagementFileMenuRow(
                        icon = Icons.Default.Upload,
                        label = managementText("回写设备", "Push back"),
                        enabled = canPushBack,
                        onClick = onPushBack,
                    )
                }
                SessionManagementFileMenuRow(
                    icon = Icons.Default.Edit,
                    label = managementText("内置编辑器", "Built-in editor"),
                    enabled = canEdit,
                    onClick = onEdit,
                )
                SessionManagementFileMenuRow(
                    icon = Icons.Outlined.Info,
                    label = managementText("详情", "Details"),
                    onClick = onDetails,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(managementText("取消", "Cancel"))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
internal fun SessionManagementTextEditorDialog(
    state: RemoteTextEditorState,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onOpenExternal: () -> Unit,
) {
    var content by remember(state.entry.fullPath, state.localFile.absolutePath) { mutableStateOf(state.content) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                            .padding(
                            horizontal = FilesTextEditorHorizontalPadding,
                            vertical = FilesTextEditorVerticalPadding,
                        ),
                verticalArrangement = Arrangement.spacedBy(FilesDialogSpacing),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = managementText("编辑 ${state.entry.name}", "Edit ${state.entry.name}"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(FilesTextEditorTopActionSpacing)) {
                        TextButton(onClick = onOpenExternal) {
                            Text(managementText("外部打开", "Open externally"))
                        }
                        TextButton(onClick = { onSave(content) }) {
                            Text(managementText("保存", "Save"))
                        }
                        TextButton(onClick = onDismiss) {
                            Text(managementText("关闭", "Close"))
                        }
                    }
                }

                Text(
                    text = state.entry.fullPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    label = { Text(managementText("文件内容", "File content")) },
                    singleLine = false,
                    maxLines = Int.MAX_VALUE,
                )
            }
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
    val bitmap =
        remember(state.localFile.absolutePath) {
            android.graphics.BitmapFactory.decodeFile(state.localFile.absolutePath)
        }

    SessionManagementFilePreviewDialog(
        title = managementText("图片预览", "Image preview"),
        path = state.entry.fullPath,
        onDismiss = onDismiss,
        onOpenExternal = onOpenExternal,
        onPushBack = onPushBack,
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = state.entry.name,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(FilesImagePreviewHeight),
            )
        } ?: Text(managementText("无法解码图片。", "Couldn't decode the image."))
    }
}

@Composable
internal fun SessionManagementVideoPreviewDialog(
    state: RemotePreparedFileState,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit,
    onPushBack: () -> Unit,
) {
    SessionManagementFilePreviewDialog(
        title = managementText("视频预览", "Video preview"),
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
                    setVideoPath(state.localFile.absolutePath)
                    setMediaController(MediaController(context).also { it.setAnchorView(this) })
                    setOnPreparedListener { start() }
                }
            },
        )
    }
}

@Composable
internal fun SessionManagementAudioPreviewDialog(
    state: RemotePreparedFileState,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit,
    onPushBack: () -> Unit,
) {
    val mediaPlayer =
        remember(state.localFile.absolutePath) {
            runCatching {
                MediaPlayer().apply {
                    setDataSource(state.localFile.absolutePath)
                    prepare()
                }
            }.getOrNull()
        }
    var isPlaying by remember(state.localFile.absolutePath) { mutableStateOf(false) }

    DisposableEffect(mediaPlayer) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    SessionManagementFilePreviewDialog(
        title = managementText("音频预览", "Audio preview"),
        path = state.entry.fullPath,
        onDismiss = onDismiss,
        onOpenExternal = onOpenExternal,
        onPushBack = onPushBack,
    ) {
        Text(
            text = managementText("本机副本：${state.localFile.name}", "Local copy: ${state.localFile.name}"),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    mediaPlayer?.let {
                        if (it.isPlaying) {
                            it.pause()
                            isPlaying = false
                        } else {
                            it.start()
                            isPlaying = true
                        }
                    }
                },
            ) {
                Text(if (isPlaying) managementText("暂停", "Pause") else managementText("播放", "Play"))
            }
            TextButton(
                onClick = {
                    mediaPlayer?.pause()
                    mediaPlayer?.seekTo(0)
                    isPlaying = false
                },
            ) {
                Text(managementText("停止", "Stop"))
            }
        }
    }
}

@Composable
internal fun SessionManagementBinaryPreviewDialog(
    state: RemoteBinaryPreviewState,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit,
    onPushBack: () -> Unit,
) {
    SessionManagementFilePreviewDialog(
        title = managementText("二进制预览", "Binary preview"),
        path = state.entry.fullPath,
        onDismiss = onDismiss,
        onOpenExternal = onOpenExternal,
        onPushBack = onPushBack,
    ) {
        OutlinedTextField(
            value = state.preview,
            onValueChange = {},
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(FilesBinaryPreviewHeight),
            readOnly = true,
            singleLine = false,
            maxLines = Int.MAX_VALUE,
            label = { Text(managementText("Hex 预览", "Hex preview")) },
        )
    }
}

@Composable
internal fun SessionManagementOverwriteConfirmDialog(
    state: RemoteOverwriteConfirmState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.title) },
        text = { Text(state.message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(managementText("覆盖", "Overwrite"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(managementText("取消", "Cancel"))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun SessionManagementFileActionCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(FilesCardCornerRadius),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.5.dp,
    ) {
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FilesDialogSpacing)) {
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                content()
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(FilesDialogButtonSpacing)) {
                TextButton(onClick = onOpenExternal) { Text(managementText("外部打开", "Open externally")) }
                TextButton(onClick = onPushBack) { Text(managementText("回写设备", "Push back")) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(managementText("关闭", "Close")) }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun SessionManagementFileMenuRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(FilesMenuRowCornerRadius),
        color =
            if (enabled) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
            },
        tonalElevation = 1.dp,
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
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.outline
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
                        MaterialTheme.colorScheme.outline
                    },
            )
        }
    }
}

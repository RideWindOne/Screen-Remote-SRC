package com.mobile.scrcpy.android.feature.session.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mobile.scrcpy.android.core.common.AppColors
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.common.util.formatHostPort
import com.mobile.scrcpy.android.core.data.repository.SessionData
import com.mobile.scrcpy.android.core.designsystem.component.AppDivider

private val ManagementFabInset = 20.dp
private val ManagementFabIconSize = 18.dp
private val ManagementFabAccent = AppColors.iOSBlue
private val ManagementTopBarHeight = 52.dp
private val ManagementTopBarSideWidth = 52.dp
private val ManagementTopBarActionSize = 40.dp
private val ManagementTopBarHorizontalInset = 4.dp
private val ManagementDrawerWidth = 248.dp
private val ManagementDrawerEdgeCornerRadius = 22.dp
private val ManagementCardCornerRadius = AppDimens.cardCornerRadius
private val ManagementDrawerPadding = 12.dp
private val ManagementDrawerHeaderPadding = 18.dp
private val ManagementDrawerSectionSpacing = 12.dp
private val ManagementDrawerItemSpacing = 6.dp
private val ManagementSurfaceElevation = 1.dp

@Composable
internal fun SessionManagementAddFab(
    modifier: Modifier = Modifier,
    contentDescription: String,
    onClick: () -> Unit,
) {
    SmallFloatingActionButton(
        onClick = onClick,
        modifier =
            modifier
                .navigationBarsPadding()
                .padding(end = ManagementFabInset, bottom = ManagementFabInset),
        containerColor = ManagementFabAccent,
        contentColor = Color.White,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = contentDescription,
            modifier = Modifier.size(ManagementFabIconSize),
        )
    }
}

@Composable
internal fun SessionManagementTopRow(
    title: String,
    onOpenMenu: () -> Unit,
    onRefresh: (() -> Unit)?,
    actionIcon: ImageVector = Icons.Default.Refresh,
    actionContentDescription: String = managementText("刷新", "Refresh"),
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .zIndex(1f),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
        tonalElevation = ManagementSurfaceElevation,
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(ManagementTopBarHeight)
                        .padding(
                            start = ManagementTopBarHorizontalInset,
                            end = ManagementTopBarHorizontalInset,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.width(ManagementTopBarSideWidth),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = onOpenMenu,
                        modifier = Modifier.size(ManagementTopBarActionSize),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = managementText("打开菜单", "Open menu"),
                        )
                    }
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Box(
                    modifier = Modifier.width(ManagementTopBarSideWidth),
                    contentAlignment = Alignment.Center,
                ) {
                    if (onRefresh != null) {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(ManagementTopBarActionSize),
                        ) {
                            Icon(
                                imageVector = actionIcon,
                                contentDescription = actionContentDescription,
                            )
                        }
                    }
                }
            }
            AppDivider()
        }
    }
}

@Composable
internal fun SessionManagementDrawer(
    sessionData: SessionData,
    selectedSection: SessionManagementSection,
    onDismiss: () -> Unit,
    onSectionSelected: (SessionManagementSection) -> Unit,
    onExit: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clickable(
                        onClick = onDismiss,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ),
        )

        Surface(
            modifier =
                Modifier
                    .width(ManagementDrawerWidth)
                    .fillMaxHeight(),
            shape =
                RoundedCornerShape(
                    topEnd = ManagementDrawerEdgeCornerRadius,
                    bottomEnd = ManagementDrawerEdgeCornerRadius,
                ),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(
                            horizontal = ManagementDrawerPadding,
                            vertical = ManagementDrawerPadding,
                        ),
            ) {
                Surface(
                    shape = RoundedCornerShape(ManagementCardCornerRadius),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.5.dp,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = ManagementDrawerHeaderPadding,
                                    vertical = ManagementDrawerHeaderPadding,
                                ),
                        verticalArrangement = Arrangement.spacedBy(ManagementDrawerSectionSpacing),
                    ) {
                        Text(
                            text = managementText("当前设备", "Current device"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = sessionData.name.ifBlank { sessionData.host },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text =
                                if (sessionData.isUsbConnection()) {
                                    sessionData.getUsbSerialNumber().orEmpty().ifBlank { sessionData.host }
                                } else {
                                    formatHostPort(sessionData.host, sessionData.port)
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(top = ManagementDrawerSectionSpacing, start = 2.dp, end = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(ManagementDrawerItemSpacing),
                ) {
                    SessionManagementSection.entries.forEach { section ->
                        SessionManagementDrawerItem(
                            section = section,
                            selected = selectedSection == section,
                            onClick = { onSectionSelected(section) },
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(ManagementDrawerItemSpacing),
                ) {
                    SessionManagementDrawerActionItem(
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        title = managementText("退出", "Exit"),
                        onClick = onExit,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SessionManagementUtilityList(
    sessionData: SessionData,
    snapshot: DeviceDashboardSnapshot,
    onAction: (UtilityAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        utilityItems(
            isTcpipMode = !sessionData.isUsbConnection(),
            snapshot = snapshot,
        ).forEach { item ->
            SessionManagementUtilityCard(
                item = item,
                onClick = { onAction(item.action) },
            )
        }
    }
}

@Composable
internal fun SessionManagementUtilityBadge(
    text: String,
    accent: Color = Color.Unspecified,
    available: Boolean = true,
) {
    val resolvedAccent =
        if (accent == Color.Unspecified) {
            MaterialTheme.colorScheme.outlineVariant
        } else {
            accent
        }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color =
                if (available) {
                    resolvedAccent
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SessionManagementUtilityCard(
    item: UtilityCardItem,
    onClick: () -> Unit,
) {
    val iconTintBackground =
        if (item.available) {
            item.accent.copy(alpha = 0.16f)
        } else {
            item.accent.copy(alpha = 0.08f)
        }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = managementPanelColor(),
        tonalElevation = 0.5.dp,
        shadowElevation = 0.5.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = item.available, onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = iconTintBackground,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = if (item.available) item.accent else item.accent.copy(alpha = 0.48f),
                    modifier =
                        Modifier
                            .padding(10.dp)
                            .size(22.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color =
                        if (item.available) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (item.available) 1f else 0.72f),
                )
            }

            when {
                item.statusChecked != null -> {
                    SessionManagementUtilityCheckBadge(
                        checked = item.statusChecked,
                        accent = item.accent,
                    )
                }

                item.statusText != null -> {
                    SessionManagementUtilityBadge(
                        text = item.statusText,
                        accent = item.accent,
                        available = item.available,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionManagementUtilityCheckBadge(
    checked: Boolean,
    accent: Color,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Icon(
            imageVector = if (checked) Icons.Default.Check else Icons.Default.Close,
            contentDescription = if (checked) managementText("已开启", "On") else managementText("未开启", "Off"),
            tint = if (checked) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .size(16.dp),
        )
    }
}

@Composable
private fun SessionManagementDrawerItem(
    section: SessionManagementSection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor =
        if (selected) {
            AppColors.iOSBlue.copy(alpha = 0.1f)
        } else {
            Color.Transparent
        }
    val contentColor =
        if (selected) {
            AppColors.iOSBlue
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = section.icon,
                contentDescription = null,
                tint = contentColor,
            )
            Text(
                text = section.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SessionManagementDrawerActionItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

internal enum class SessionManagementSection(
    val titleZh: String,
    val titleEn: String,
    val icon: ImageVector,
    val supportsRefresh: Boolean = false,
) {
    DeviceInfo("设备信息", "Device Info", Icons.Default.Info, supportsRefresh = true),
    Utility("实用工具", "Utilities", Icons.Default.Build, supportsRefresh = true),
    Files("文件管理", "Files", Icons.Default.Folder, supportsRefresh = true),
    Apps("应用管理", "Apps", Icons.Default.Apps, supportsRefresh = true),
    Process("进程管理", "Processes", Icons.Default.Usb, supportsRefresh = true),
    Command("运行命令", "Commands", Icons.Default.Code),
    ;

    val title: String
        get() = managementText(titleZh, titleEn)
}

internal enum class UtilityAction {
    FixedPort,
    Screenshot,
    AdvancedReboot,
    ActivateApp,
    ModifyDpi,
    ModifyResolution,
    AnimationScale,
    SleepStandby,
    TombstoneMode,
}

private data class UtilityCardItem(
    val action: UtilityAction,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val available: Boolean = true,
    val statusText: String? = null,
    val statusChecked: Boolean? = null,
)

private fun utilityItems(
    isTcpipMode: Boolean,
    snapshot: DeviceDashboardSnapshot,
): List<UtilityCardItem> =
    listOf(
        UtilityCardItem(
            action = UtilityAction.FixedPort,
            title = managementText("固定无线调试端口", "Fixed wireless debugging port"),
            subtitle = managementText("设置 ADB TCP 端口号，设备将重启 ADB 服务", "Set the ADB TCP port. The device will restart ADB."),
            icon = Icons.Default.Wifi,
            accent = Color(0xFF2A9D8F),
        ),
        UtilityCardItem(
            action = UtilityAction.Screenshot,
            title = managementText("屏幕截图", "Screenshot"),
            subtitle = managementText("截图到控制端本机缓存，可打开预览或保存到相册", "Save a screenshot to local cache, then preview or save it."),
            icon = Icons.Default.PhotoCamera,
            accent = AppColors.iOSBlue,
        ),
        UtilityCardItem(
            action = UtilityAction.AdvancedReboot,
            title = managementText("高级重启", "Advanced reboot"),
            subtitle = managementText("正常重启、Recovery、FastBoot", "Restart, Recovery, or Fastboot"),
            icon = Icons.Default.RestartAlt,
            accent = Color(0xFF34C759),
        ),
        UtilityCardItem(
            action = UtilityAction.ActivateApp,
            title = managementText("激活应用", "Activate app"),
            subtitle = managementText("点击后加载可激活应用列表", "Load the list of supported activation apps"),
            icon = Icons.Default.VerifiedUser,
            accent = Color(0xFF5AC8FA),
        ),
        UtilityCardItem(
            action = UtilityAction.ModifyDpi,
            title = managementText("修改DPI", "Change DPI"),
            subtitle =
                snapshot.currentDpiLabel?.let {
                    managementText("当前 $it，点击输入新数值", "Current $it. Tap to enter a new value")
                } ?: managementText("输入新的屏幕密度", "Enter a new screen density"),
            icon = Icons.Default.CropFree,
            accent = Color(0xFFFF9F0A),
        ),
        UtilityCardItem(
            action = UtilityAction.ModifyResolution,
            title = managementText("修改分辨率", "Change resolution"),
            subtitle = snapshot.resolution.ifBlank { managementText("调整屏幕分辨率大小", "Adjust the screen resolution") },
            icon = Icons.Default.CropFree,
            accent = Color(0xFF30B0C7),
        ),
        UtilityCardItem(
            action = UtilityAction.AnimationScale,
            title = managementText("动画调整", "Animation scale"),
            subtitle = managementText("统一设置窗口、过渡和时长动画倍率", "Set window, transition, and duration scales together"),
            icon = Icons.Default.Tune,
            accent = Color(0xFFFF9500),
        ),
        UtilityCardItem(
            action = UtilityAction.SleepStandby,
            title = managementText("熄屏待机", "Screen standby"),
            subtitle = managementText("提供息屏、亮屏两个操作", "Provide sleep and wake actions"),
            icon = Icons.Default.Lightbulb,
            accent = Color(0xFF8E8E93),
        ),
        UtilityCardItem(
            action = UtilityAction.TombstoneMode,
            title = managementText("墓碑模式", "Tombstone mode"),
            subtitle = managementText("暂未实现，当前保持禁用", "Not available yet"),
            icon = Icons.Default.Refresh,
            accent = Color(0xFF5856D6),
            available = false,
            statusText = managementText("禁用", "Disabled"),
        ),
    )

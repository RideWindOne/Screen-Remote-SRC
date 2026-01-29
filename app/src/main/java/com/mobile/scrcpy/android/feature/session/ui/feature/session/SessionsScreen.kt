package com.mobile.scrcpy.android.feature.session.ui.feature.session

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobile.scrcpy.android.core.common.manager.rememberText
import com.mobile.scrcpy.android.core.common.util.ApiCompatHelper
import com.mobile.scrcpy.android.core.designsystem.component.AddActionDialog
import com.mobile.scrcpy.android.core.designsystem.component.CompactGroupSelector
import com.mobile.scrcpy.android.core.designsystem.component.GroupManagementDialog
import com.mobile.scrcpy.android.core.designsystem.component.PathBreadcrumb
import com.mobile.scrcpy.android.core.domain.model.GroupType
import com.mobile.scrcpy.android.core.i18n.SessionTexts
import com.mobile.scrcpy.android.feature.codec.ui.CodecTestScreen
import com.mobile.scrcpy.android.feature.device.ui.component.AdbKeyManagementDialog
import com.mobile.scrcpy.android.feature.remote.ui.RemoteDisplayScreen
import com.mobile.scrcpy.android.feature.session.ui.SessionsScreen
import com.mobile.scrcpy.android.feature.session.ui.component.AddSessionDialog
import com.mobile.scrcpy.android.feature.session.viewmodel.MainViewModel
import com.mobile.scrcpy.android.feature.settings.ui.AboutScreen
import com.mobile.scrcpy.android.feature.settings.ui.ActionsScreen
import com.mobile.scrcpy.android.feature.settings.ui.AppearanceScreen
import com.mobile.scrcpy.android.feature.settings.ui.LanguageScreen
import com.mobile.scrcpy.android.feature.settings.ui.LogManagementScreen
import com.mobile.scrcpy.android.feature.settings.ui.SettingsScreen
import kotlin.collections.filter
import kotlin.collections.find
// import com.mobile.scrcpy.android.ui.components.FloatingMenuController  // 暂时隐藏

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var showLogManagement by remember { mutableStateOf(false) }
    var showCodecTest by remember { mutableStateOf(false) }
    var showGroupManagement by remember { mutableStateOf(false) }
    var showAdbKeyManagement by remember { mutableStateOf(false) }
    val showAddDialog by viewModel.showAddSessionDialog.collectAsState()
    val editingSessionId by viewModel.editingSessionId.collectAsState()
    val sessionDataList by viewModel.sessionDataList.collectAsState()
    val showAddActionDialog by viewModel.showAddActionDialog.collectAsState()
    val connectedSessionId by viewModel.connectedSessionId.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val selectedGroupPath by viewModel.selectedGroupPath.collectAsState()
    val selectedAutomationGroupPath by viewModel.selectedAutomationGroupPath.collectAsState()

    // 双语文本
    val txtTitle = rememberText(SessionTexts.MAIN_TITLE_SESSIONS)
    val txtTabSessions = rememberText(SessionTexts.MAIN_TAB_SESSIONS)
    val txtTabActions = rememberText(SessionTexts.MAIN_TAB_ACTIONS)
    val txtAddSession = rememberText(SessionTexts.MAIN_ADD_SESSION)
    val txtAddAction =
        rememberText(SessionTexts.MAIN_ADD_ACTION)

    // 申请通知权限（Android 13+）
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            // 权限结果处理（可选）
        }

    LaunchedEffect(Unit) {
        if (ApiCompatHelper.needsNotificationPermission()) {
            val hasPermission =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // 显示 Scrcpy 界面（只要有 connectedSessionId 就显示，不管连接状态）
    // 这样可以避免状态变化时组件被销毁重建
    if (connectedSessionId != null) {
        RemoteDisplayScreen(
            sessionId = connectedSessionId!!,
            mainViewModel = viewModel,
            onClose = {
                // 立即更新状态，使界面切换回主界面
                // 注意：disconnectFromDevice() 会更新状态，但为了立即响应，先清除状态
                viewModel.clearConnectStatus()
                // 异步断开设备连接（会再次更新状态，确保一致性）
                viewModel.disconnectFromDevice()
            },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = txtTitle,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = Color(0xFF007AFF),
                        )
                    }
                },
                actions = {
                    // 测试悬浮窗按钮（暂时隐藏）
                    // FloatingMenuController()

                    IconButton(onClick = {
                        if (selectedTab == 0) {
                            viewModel.showAddSessionDialog()
                        } else {
                            viewModel.showAddActionDialog()
                        }
                    }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = if (selectedTab == 0) txtAddSession else txtAddAction,
                            tint = Color(0xFF007AFF),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Tab 切换器 + 分组选择器（整体居中）
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Tab 切换器
                    Box(
                        modifier =
                            Modifier
                                .width(132.dp) // 70 + 60 = 130.dp
                                .height(38.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(2.dp),
                    ) {
                        // Sessions Tab (左边)
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.CenterStart)
                                    .width(70.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(15.dp))
                                    .background(
                                        if (selectedTab ==
                                            0
                                        ) {
                                            MaterialTheme.colorScheme.surface
                                        } else {
                                            Color.Transparent
                                        },
                                    ).then(
                                        if (selectedTab == 0) {
                                            Modifier.zIndex(1f)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            TextButton(
                                onClick = { selectedTab = 0 },
                                modifier = Modifier.fillMaxSize(),
                                colors =
                                    ButtonDefaults.textButtonColors(
                                        contentColor =
                                            if (selectedTab ==
                                                0
                                            ) {
                                                Color(0xFF007AFF)
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                    ),
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(
                                    text = txtTabSessions,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }

                        // Actions Tab (右边)
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(60.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(15.dp))
                                    .background(
                                        if (selectedTab ==
                                            1
                                        ) {
                                            MaterialTheme.colorScheme.surface
                                        } else {
                                            Color.Transparent
                                        },
                                    ).then(
                                        if (selectedTab == 1) {
                                            Modifier.zIndex(1f)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            TextButton(
                                onClick = { selectedTab = 1 },
                                modifier = Modifier.fillMaxSize(),
                                colors =
                                    ButtonDefaults.textButtonColors(
                                        contentColor =
                                            if (selectedTab ==
                                                1
                                            ) {
                                                Color(0xFF007AFF)
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                    ),
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(
                                    text = txtTabActions,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }

                    // 分组选择器（根据当前标签页显示对应类型的分组）
                    // 即使没有分组，也显示"主页"按钮
                    val filteredGroups =
                        if (selectedTab == 0) {
                            // Sessions 标签页：显示 SESSION 类型分组
                            groups.filter { it.type == GroupType.SESSION }
                        } else {
                            // Actions 标签页：显示 AUTOMATION 类型分组
                            groups.filter { it.type == GroupType.AUTOMATION }
                        }

                    // 始终显示分组选择器（即使没有分组，也显示"主页"）
                    CompactGroupSelector(
                        groups = filteredGroups,
                        selectedGroupPath = if (selectedTab == 0) selectedGroupPath else selectedAutomationGroupPath,
                        onGroupSelected = {
                            if (selectedTab == 0) {
                                viewModel.selectGroup(it)
                            } else {
                                viewModel.selectAutomationGroup(it)
                            }
                        },
                    )
                }
            }

            // 主内容区域
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            ) {
                when (selectedTab) {
                    0 -> SessionsScreen(viewModel)
                    1 -> ActionsScreen(viewModel)
                }
            }

            // 底部面包屑（根据当前标签页显示对应的路径）
            if (selectedTab == 0) {
                PathBreadcrumb(selectedGroupPath = selectedGroupPath)
            } else if (selectedTab == 1) {
                PathBreadcrumb(selectedGroupPath = selectedAutomationGroupPath)
            }
        }
    }

    if (showAddDialog) {
        val editingSession =
            editingSessionId?.let { id ->
                sessionDataList.find { it.id == id }
            }
        // 使用 editingSessionId 作为 key，确保切换编辑对象时强制重组
        androidx.compose.runtime.key(editingSessionId ?: "new") {
            AddSessionDialog(
                sessionData = editingSession,
                availableGroups = groups,
                onDismiss = { viewModel.hideAddSessionDialog() },
                onConfirm = { sessionData ->
                    viewModel.saveSessionData(sessionData)
                },
            )
        }
    }

    if (showAddActionDialog) {
        AddActionDialog(
            onDismiss = { viewModel.hideAddActionDialog() },
            onConfirm = { action ->
                viewModel.addAction(action)
            },
        )
    }

    if (showSettings) {
        SettingsScreen(
            viewModel = viewModel,
            onBack = { showSettings = false },
            onNavigateToAbout = {
                showAbout = true
            },
            onNavigateToAppearance = {
                showAppearance = true
            },
            onNavigateToLanguage = {
                showLanguage = true
            },
            onNavigateToAdbKeys = {
                showAdbKeyManagement = true
            },
            onNavigateToLogManagement = {
                showLogManagement = true
            },
            onNavigateToGroupManagement = {
                showGroupManagement = true
            },
        )
    }

    if (showAbout) {
        AboutScreen(
            onBack = { showAbout = false },
        )
    }

    if (showAppearance) {
        AppearanceScreen(
            viewModel = viewModel,
            onBack = { showAppearance = false },
        )
    }

    if (showLanguage) {
        LanguageScreen(
            viewModel = viewModel,
            onBack = { showLanguage = false },
        )
    }

    if (showCodecTest) {
        CodecTestScreen(
            onBack = { showCodecTest = false },
        )
    }

    if (showLogManagement) {
        LogManagementScreen(
            onDismiss = { showLogManagement = false },
        )
    }

    if (showGroupManagement) {
        GroupManagementDialog(
            groups = groups,
            sessionCounts = viewModel.getSessionCountByGroup(),
            onDismiss = { showGroupManagement = false },
            onAddGroup = { name, parentPath, type ->
                viewModel.addGroup(name, parentPath, type)
            },
            onUpdateGroup = { group ->
                viewModel.updateGroup(group)
            },
            onDeleteGroup = { groupId ->
                viewModel.removeGroup(groupId)
            },
        )
    }

    if (showAdbKeyManagement) {
        AdbKeyManagementDialog(
            onDismiss = { showAdbKeyManagement = false },
        )
    }
}

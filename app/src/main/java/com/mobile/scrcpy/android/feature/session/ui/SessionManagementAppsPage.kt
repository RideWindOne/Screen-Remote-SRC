package com.mobile.scrcpy.android.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.util.FilePickerHelper
import com.mobile.scrcpy.android.core.designsystem.component.AppDivider
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbBridge
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
internal fun SessionManagementAppsPage(
    modifier: Modifier = Modifier,
    refreshToken: Int,
    topActionTick: Int,
    addMenuRequestTick: Int,
) {
    val context = LocalContext.current
    val helperJar = remember(context) { ensureLocalAppIconHelperJar(context) }
    LaunchedEffect(Unit) {
        SessionManagementAppCache.prepareForProcess(context)
    }
    val scope = rememberCoroutineScope()
    var listRefreshTick by remember { mutableIntStateOf(0) }
    val appPresentationVersions = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
    var forceRefreshPending by remember { mutableStateOf(false) }
    val inventoryApps = remember { androidx.compose.runtime.mutableStateListOf<AppInventoryEntry>() }
    var inventoryLoading by remember { mutableStateOf(true) }
    var iconBatchLoadingCount by remember { mutableIntStateOf(0) }
    var inventoryError by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilters by remember { mutableStateOf(AppListFilter.defaultSelection) }
    var sort by remember { mutableStateOf(AppListSort.Title) }
    var packageNameOnlyMode by remember { mutableStateOf(false) }
    var optionsMenuExpanded by remember { mutableStateOf(false) }
    var selectedAppForActions by remember { mutableStateOf<AppInventoryEntry?>(null) }
    var selectedAppForDetails by remember { mutableStateOf<AppInventoryEntry?>(null) }
    var uninstallAppState by remember { mutableStateOf<AppInventoryEntry?>(null) }
    var appActionProgress by remember { mutableStateOf<String?>(null) }
    var appActionResult by remember { mutableStateOf<String?>(null) }
    var appLoadToken by remember { mutableIntStateOf(0) }
    var appAddDialogOpen by remember { mutableStateOf(false) }
    var helperReady by remember { mutableStateOf(false) }
    var helperPreparing by remember { mutableStateOf(false) }
    val appInventory =
        AppInventorySnapshot(
            isLoading = inventoryLoading,
            apps = inventoryApps.toList(),
            shizukuInstalled = inventoryApps.any { it.packageName == "moe.shizuku.privileged.api" },
            errorMessage = inventoryError,
        )

    LaunchedEffect(Unit) {
        helperPreparing = true
        val connection = AdbBridge.getConnection()
        if (connection == null) {
            helperPreparing = false
            return@LaunchedEffect
        }
        val result = connection.prepareAppIconHelper(helperJar)
        helperPreparing = false
        helperReady = result.isSuccess
    }

    LaunchedEffect(refreshToken, listRefreshTick) {
        appLoadToken += 1
        val currentLoadToken = appLoadToken
        val manualRefresh = forceRefreshPending
        forceRefreshPending = false
        inventoryError = null
        iconBatchLoadingCount = 0

        if (manualRefresh) {
            SessionManagementAppCache.clearSnapshot()
            inventoryApps.clear()
            appPresentationVersions.clear()
        } else {
            SessionManagementAppCache.snapshot()?.let { cached ->
                inventoryApps.clear()
                inventoryApps.addAll(cached.apps)
            }
        }

        inventoryLoading = inventoryApps.isEmpty()

        if (!manualRefresh && inventoryApps.isNotEmpty()) {
            return@LaunchedEffect
        }

        val result = loadAppInventorySnapshot(context, includeSystemApps = false, forceRefresh = manualRefresh)
        inventoryLoading = false
        if (result.errorMessage != null) {
            if (inventoryApps.isEmpty()) {
                inventoryError = result.errorMessage
            }
            return@LaunchedEffect
        }

        inventoryApps.clear()
        inventoryApps.addAll(result.apps)
        SessionManagementAppCache.updateSnapshot(result.copy(isLoading = false))

        launch {
            runCatching {
                loadAppInventorySnapshot(context, includeSystemApps = true, forceRefresh = true)
            }.onSuccess { fullSnapshot ->
                if (currentLoadToken == appLoadToken) {
                    inventoryApps.clear()
                    inventoryApps.addAll(fullSnapshot.apps)
                    SessionManagementAppCache.updateSnapshot(fullSnapshot.copy(isLoading = false))
                }
            }.onFailure { error ->
                runCatching {
                    com.mobile.scrcpy.android.core.common.manager.LogManager.w(
                        com.mobile.scrcpy.android.core.common.LogTags.ADB_CONNECTION,
                        "后台补全全量应用列表失败: ${error.message}",
                    )
                }
            }
        }
    }

    LaunchedEffect(topActionTick) {
        if (topActionTick > 0) {
            optionsMenuExpanded = true
        }
    }

    LaunchedEffect(addMenuRequestTick) {
        if (addMenuRequestTick > 0) {
            appAddDialogOpen = true
        }
    }

    LaunchedEffect(helperReady, packageNameOnlyMode, appLoadToken, inventoryApps.map { it.packageName }) {
        if (!helperReady || packageNameOnlyMode || inventoryApps.isEmpty()) {
            return@LaunchedEffect
        }
        iconBatchLoadingCount += 1
        try {
            prefetchAppIconsWithHelper(context, inventoryApps.toList(), helperJar) {
                it.forEach { packageName ->
                    appPresentationVersions[packageName] = (appPresentationVersions[packageName] ?: 0) + 1
                }
            }
        } catch (error: Throwable) {
            runCatching {
                com.mobile.scrcpy.android.core.common.manager.LogManager.w(
                    com.mobile.scrcpy.android.core.common.LogTags.ADB_CONNECTION,
                    "helper 就绪后预取图标失败: ${error.message}",
                )
            }
        } finally {
            iconBatchLoadingCount = (iconBatchLoadingCount - 1).coerceAtLeast(0)
        }
    }

    val appLoadInProgress = inventoryLoading || helperPreparing || iconBatchLoadingCount > 0
    val visibleApps =
        appInventory.apps
            .map { entry ->
                entry.copy(appTitle = resolveAppListTitle(entry, packageNameOnlyMode))
            }.filter { entry ->
                val showSystem = AppListFilter.ShowSystemApps in selectedFilters
                val showUser = AppListFilter.ShowUserApps in selectedFilters
                (entry.isSystemApp && showSystem) || (!entry.isSystemApp && showUser)
            }.filter { entry ->
                val showEnabled = AppListFilter.ShowEnabledApps in selectedFilters
                val showDisabled = AppListFilter.ShowDisabledApps in selectedFilters
                (entry.isEnabled && showEnabled) || (!entry.isEnabled && showDisabled)
            }.filter { entry ->
                if (searchQuery.isBlank()) {
                    true
                } else {
                    val keyword = searchQuery.trim().lowercase(Locale.getDefault())
                    if (packageNameOnlyMode) {
                        entry.packageName.lowercase(Locale.getDefault()).contains(keyword)
                    } else {
                        entry.appTitle.lowercase(Locale.getDefault()).contains(keyword) ||
                            entry.packageName.lowercase(Locale.getDefault()).contains(keyword)
                    }
                }
            }.sortedWith(
                when (sort) {
                    AppListSort.Title -> {
                        compareBy<AppInventoryEntry> { it.appTitle.lowercase(Locale.getDefault()) }
                            .thenBy { it.packageName.lowercase(Locale.getDefault()) }
                    }

                    AppListSort.Package -> {
                        compareBy<AppInventoryEntry> { it.packageName.lowercase(Locale.getDefault()) }
                            .thenBy { it.appTitle.lowercase(Locale.getDefault()) }
                    }

                    AppListSort.EnabledState -> {
                        compareByDescending<AppInventoryEntry> { it.isEnabled }
                            .thenBy { it.appTitle.lowercase(Locale.getDefault()) }
                    }
                },
            )

    fun refreshInventory(manual: Boolean) {
        if (manual) {
            forceRefreshPending = true
        }
        listRefreshTick += 1
    }

    val apkImportLauncher =
        FilePickerHelper.rememberImportFileLauncher(
            mimeTypes = arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*"),
        ) { uri ->
            uri ?: return@rememberImportFileLauncher
            scope.launch {
                appActionProgress = "正在准备安装包"
                val result =
                    runCatching {
                        val tempFile = copyUriToTempApk(context, uri)
                        val connection = AdbBridge.getConnection() ?: error("当前没有可用的 ADB 连接。")
                        connection.installApk(tempFile.absolutePath).getOrThrow()
                        "安装请求已发送。"
                    }
                appActionProgress = null
                appActionResult = result.getOrNull() ?: (result.exceptionOrNull()?.message ?: "安装失败")
                refreshInventory(manual = true)
            }
        }

    fun launchAppAction(
        progress: String,
        block: suspend () -> Result<String>,
    ) {
        appActionProgress = progress
        scope.launch {
            val result = block()
            appActionProgress = null
            appActionResult = result.getOrNull() ?: (result.exceptionOrNull()?.message ?: "操作失败")
            refreshInventory(manual = true)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (appLoadInProgress) {
            LinearProgressIndicator(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .zIndex(2f),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "共 ${visibleApps.size} 项",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                singleLine = true,
                                shape = RoundedCornerShape(18.dp),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "清空",
                                            )
                                        }
                                    }
                                },
                                label = { Text("搜索应用或包名") },
                            )
                        }
                    }

                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 4.dp)
                                .size(1.dp),
                    ) {
                        SessionManagementAppOptionsMenu(
                            expanded = optionsMenuExpanded,
                            selectedFilters = selectedFilters,
                            selectedSort = sort,
                            packageNameOnlyMode = packageNameOnlyMode,
                            onDismiss = { optionsMenuExpanded = false },
                            onRefreshList = {
                                optionsMenuExpanded = false
                                refreshInventory(manual = true)
                            },
                            onSortSelected = { sort = it },
                            onPackageNameOnlyModeChanged = { packageNameOnlyMode = it },
                            onToggleFilter = { filter ->
                                selectedFilters =
                                    if (filter in selectedFilters) {
                                        selectedFilters - filter
                                    } else {
                                        selectedFilters + filter
                                    }
                            },
                        )
                    }
                }
            }

            when {
                appInventory.isLoading && visibleApps.isEmpty() -> {
                    item {
                        SessionManagementNoteCard(
                            title = "正在读取应用列表",
                            text = "应用列表按页加载；每页到手后会并行预取该页图标并写入本地缓存。",
                        )
                    }
                }

                appInventory.errorMessage != null -> {
                    item {
                        SessionManagementNoteCard(
                            title = "应用列表读取失败",
                            text = appInventory.errorMessage,
                        )
                    }
                }

                visibleApps.isEmpty() -> {
                    item {
                        SessionManagementNoteCard(
                            title = "没有匹配结果",
                            text = "调整搜索条件或在右上角下拉菜单里修改筛选。",
                        )
                    }
                }

                else -> {
                    itemsIndexed(visibleApps) { index, entry ->
                        Surface(
                            shape =
                                RoundedCornerShape(
                                    topStart = if (index == 0) 20.dp else 0.dp,
                                    topEnd = if (index == 0) 20.dp else 0.dp,
                                    bottomStart = if (index == visibleApps.lastIndex) 20.dp else 0.dp,
                                    bottomEnd = if (index == visibleApps.lastIndex) 20.dp else 0.dp,
                                ),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                SessionManagementAppRow(
                                    entry = entry,
                                    packageNameOnlyMode = packageNameOnlyMode,
                                    presentationVersion = appPresentationVersions[entry.packageName] ?: 0,
                                    onClick = { selectedAppForActions = entry },
                                )
                                if (index != visibleApps.lastIndex) {
                                    AppDivider(modifier = Modifier.padding(start = 58.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (appAddDialogOpen) {
        SessionManagementAppAddDialog(
            onDismiss = { appAddDialogOpen = false },
            onPickApk = {
                appAddDialogOpen = false
                apkImportLauncher.launch(
                    arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*"),
                )
            },
            onPickInstalledApp = {
                appAddDialogOpen = false
                appActionResult = "选择已安装应用稍后补接 install-existing 选择器。"
            },
        )
    }

    selectedAppForActions?.let { entry ->
        SessionManagementAppActionDialog(
            entry = entry,
            onDismiss = { selectedAppForActions = null },
            onDetails = {
                selectedAppForActions = null
                selectedAppForDetails = entry
            },
            onLaunch = {
                selectedAppForActions = null
                launchAppAction(progress = "正在启动 ${entry.appTitle}") {
                    runShellAction(
                        command = "monkey -p ${entry.packageName} -c android.intent.category.LAUNCHER 1",
                        successMessage = "已尝试在设备上启动 ${entry.packageName}。",
                    )
                }
            },
            onToggleEnabled = {
                selectedAppForActions = null
                launchAppAction(
                    progress = if (entry.isEnabled) "正在停用 ${entry.appTitle}" else "正在启用 ${entry.appTitle}",
                ) {
                    runShellAction(
                        command =
                            if (entry.isEnabled) {
                                "pm disable-user --user 0 ${entry.packageName}"
                            } else {
                                "pm enable ${entry.packageName}"
                            },
                        successMessage =
                            if (entry.isEnabled) {
                                "已尝试停用 ${entry.packageName}。"
                            } else {
                                "已尝试启用 ${entry.packageName}。"
                            },
                    )
                }
            },
            onUninstall = {
                selectedAppForActions = null
                uninstallAppState = entry
            },
            onClearData = {
                selectedAppForActions = null
                launchAppAction(progress = "正在清除 ${entry.appTitle} 数据") {
                    runShellAction(
                        command = "pm clear ${entry.packageName}",
                        successMessage = "已清除 ${entry.packageName} 数据。",
                    )
                }
            },
            onDownloadApk = {
                selectedAppForActions = null
                launchAppAction(progress = "正在导出 ${entry.appTitle} 安装包") {
                    exportPackageApk(context, entry.packageName)
                }
            },
        )
    }

    selectedAppForDetails?.let { entry ->
        SessionManagementAppDetailDialog(
            entry = entry,
            onDismiss = { selectedAppForDetails = null },
        )
    }

    uninstallAppState?.let { entry ->
        SessionManagementAppUninstallDialog(
            packageName = entry.packageName,
            onDismiss = { uninstallAppState = null },
            onConfirm = { keepData ->
                uninstallAppState = null
                launchAppAction(progress = "正在卸载 ${entry.appTitle}") {
                    runShellAction(
                        command = "pm uninstall ${if (keepData) "-k " else ""}${entry.packageName}",
                        successMessage = "已尝试卸载 ${entry.packageName}。",
                    )
                }
            },
        )
    }

    appActionProgress?.let { message ->
        SessionManagementProgressDialog(
            title = "应用管理",
            message = message,
        )
    }

    appActionResult?.let { message ->
        AlertDialog(
            onDismissRequest = { appActionResult = null },
            title = { Text("应用管理") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { appActionResult = null }) {
                    Text("确定")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

package com.screen.remote.android.feature.session.ui

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.screen.remote.android.core.i18n.ManagementTexts
import com.screen.remote.android.core.common.util.FilePickerHelper
import com.screen.remote.android.core.designsystem.component.AppDivider
import com.screen.remote.android.infrastructure.adb.connection.AdbBridge
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
                    com.screen.remote.android.core.common.manager.LogManager.w(
                        com.screen.remote.android.core.common.LogTags.ADB_CONNECTION,
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
                com.screen.remote.android.core.common.manager.LogManager.w(
                    com.screen.remote.android.core.common.LogTags.ADB_CONNECTION,
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
                appActionProgress = ManagementTexts.text("正在准备安装包", "Preparing APK")
                val result =
                    runCatching {
                        val tempFile = copyUriToTempApk(context, uri)
                        val connection = AdbBridge.getConnection() ?: error(ManagementTexts.text("当前没有可用的 ADB 连接。", "No ADB connection is available."))
                        connection.installApk(tempFile.absolutePath).getOrThrow()
                        ManagementTexts.text("安装请求已发送。", "Install request sent.")
                    }
                appActionProgress = null
                appActionResult = result.getOrNull() ?: (result.exceptionOrNull()?.message ?: ManagementTexts.text("安装失败", "Install failed"))
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
            appActionResult = result.getOrNull() ?: (result.exceptionOrNull()?.message ?: ManagementTexts.text("操作失败", "Action failed"))
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
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = managementPanelColor(),
                        tonalElevation = 1.dp,
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = ManagementTexts.countLabel(visibleApps.size),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
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
                                                contentDescription = ManagementTexts.text("清空", "Clear"),
                                            )
                                        }
                                    }
                                },
                                placeholder = {
                                    Text(
                                        text = ManagementTexts.text("搜索应用或包名", "Search apps or packages"),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
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

            if (!appInventory.isLoading && appInventory.errorMessage == null && visibleApps.isNotEmpty()) {
                item {
                    Box(modifier = Modifier.height(12.dp))
                }
            }

            when {
                appInventory.isLoading && visibleApps.isEmpty() -> {
                    item {
                        SessionManagementNoteCard(
                            title = ManagementTexts.text("正在读取应用列表", "Loading apps"),
                            text = ManagementTexts.text("正在同步设备上的应用。", "Syncing apps from the device."),
                        )
                    }
                }

                appInventory.errorMessage != null -> {
                    item {
                        SessionManagementNoteCard(
                            title = ManagementTexts.text("应用列表读取失败", "Couldn't load apps"),
                            text = appInventory.errorMessage,
                        )
                    }
                }

                visibleApps.isEmpty() -> {
                    item {
                        SessionManagementNoteCard(
                            title = ManagementTexts.text("没有匹配结果", "No results"),
                            text = ManagementTexts.text("试试别的关键词或筛选。", "Try a different keyword or filter."),
                        )
                    }
                }

                else -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = managementPanelColor(),
                            tonalElevation = 1.dp,
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                            ) {
                                visibleApps.forEachIndexed { index, entry ->
                                    SessionManagementAppRow(
                                        entry = entry,
                                        packageNameOnlyMode = packageNameOnlyMode,
                                        presentationVersion = appPresentationVersions[entry.packageName] ?: 0,
                                        onClick = { selectedAppForActions = entry },
                                    )
                                    if (index != visibleApps.lastIndex) {
                                        AppDivider(modifier = Modifier.padding(start = 50.dp))
                                    }
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
                appActionResult = ManagementTexts.text("选择已安装应用功能稍后补上。", "Pick installed app will be added later.")
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
                launchAppAction(progress = ManagementTexts.text("正在启动 ${entry.appTitle}", "Launching ${entry.appTitle}")) {
                    runShellAction(
                        command = "monkey -p ${entry.packageName} -c android.intent.category.LAUNCHER 1",
                        successMessage = ManagementTexts.text("已尝试在设备上启动 ${entry.packageName}。", "Tried to launch ${entry.packageName} on the device."),
                    )
                }
            },
            onToggleEnabled = {
                selectedAppForActions = null
                launchAppAction(
                    progress =
                        if (entry.isEnabled) {
                            ManagementTexts.text("正在停用 ${entry.appTitle}", "Disabling ${entry.appTitle}")
                        } else {
                            ManagementTexts.text("正在启用 ${entry.appTitle}", "Enabling ${entry.appTitle}")
                        },
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
                                ManagementTexts.text("已尝试停用 ${entry.packageName}。", "Tried to disable ${entry.packageName}.")
                            } else {
                                ManagementTexts.text("已尝试启用 ${entry.packageName}。", "Tried to enable ${entry.packageName}.")
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
                launchAppAction(progress = ManagementTexts.text("正在清除 ${entry.appTitle} 数据", "Clearing ${entry.appTitle} data")) {
                    runShellAction(
                        command = "pm clear ${entry.packageName}",
                        successMessage = ManagementTexts.text("已清除 ${entry.packageName} 数据。", "Cleared data for ${entry.packageName}."),
                    )
                }
            },
            onDownloadApk = {
                selectedAppForActions = null
                launchAppAction(progress = ManagementTexts.text("正在导出 ${entry.appTitle} 安装包", "Exporting APK for ${entry.appTitle}")) {
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
                launchAppAction(progress = ManagementTexts.text("正在卸载 ${entry.appTitle}", "Uninstalling ${entry.appTitle}")) {
                    runShellAction(
                        command = "pm uninstall ${if (keepData) "-k " else ""}${entry.packageName}",
                        successMessage = ManagementTexts.text("已尝试卸载 ${entry.packageName}。", "Tried to uninstall ${entry.packageName}."),
                    )
                }
            },
        )
    }

    appActionProgress?.let { message ->
        SessionManagementProgressDialog(
            title = ManagementTexts.text("应用管理", "Apps"),
            message = message,
        )
    }

    appActionResult?.let { message ->
        AlertDialog(
            onDismissRequest = { appActionResult = null },
            title = { Text(ManagementTexts.text("应用管理", "Apps")) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { appActionResult = null }) {
                    Text(ManagementTexts.text("确定", "OK"))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

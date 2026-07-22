package com.screen.remote.android.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.i18n.ManagementTexts
import com.screen.remote.android.core.common.util.FilePickerHelper
import com.screen.remote.android.infrastructure.adb.connection.installApkFromUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private data class AppListProjectionRequest(
    val apps: List<AppInventoryEntry>,
    val selectedFilters: Set<AppListFilter>,
    val sort: AppListSort,
    val sortAscending: Boolean,
    val packageNameOnlyMode: Boolean,
    val normalizedSearchQuery: String,
    val presentationGeneration: Int,
)

private data class AppListProjection(
    val request: AppListProjectionRequest? = null,
    val apps: List<AppInventoryEntry> = emptyList(),
)

@Stable
internal class SessionManagementAppOptionsState {
    var expanded by mutableStateOf(false)
        private set

    fun show() {
        expanded = true
    }

    fun dismiss() {
        expanded = false
    }
}

@Composable
internal fun SessionManagementAppsPage(
    modifier: Modifier = Modifier,
    refreshToken: Int,
    optionsState: SessionManagementAppOptionsState,
    addMenuRequestTick: Int,
    cacheScopeKey: String,
    dataProvider: SessionManagementDataProvider,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var listRefreshTick by remember { mutableIntStateOf(0) }
    var forceRefreshPending by remember { mutableStateOf(false) }
    var inventoryApps by remember { mutableStateOf<List<AppInventoryEntry>>(emptyList()) }
    var inventoryLoading by remember { mutableStateOf(true) }
    var inventoryRefreshing by remember { mutableStateOf(true) }
    var inventoryError by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var submittedSearchQuery by remember { mutableStateOf("") }
    var selectedFilters by remember { mutableStateOf(AppListFilter.defaultSelection) }
    var sortSelection by remember { mutableStateOf(AppListSortSelection()) }
    var packageNameOnlyMode by remember { mutableStateOf(false) }
    var selectedAppForActions by remember { mutableStateOf<AppInventoryEntry?>(null) }
    var selectedAppForDetails by remember { mutableStateOf<AppInventoryEntry?>(null) }
    var pendingApkExport by remember { mutableStateOf<AppInventoryEntry?>(null) }
    var uninstallAppState by remember { mutableStateOf<AppInventoryEntry?>(null) }
    var appActionProgress by remember { mutableStateOf<String?>(null) }
    var appActionResult by remember { mutableStateOf<String?>(null) }
    var appAddDialogOpen by remember { mutableStateOf(false) }
    var localAppPickerOpen by remember { mutableStateOf(false) }
    var localAppsLoading by remember { mutableStateOf(false) }
    var localApps by remember { mutableStateOf<List<LocalInstalledApp>>(emptyList()) }
    var localAppsError by remember { mutableStateOf<String?>(null) }
    val appCacheRevision = SessionManagementAppCache.revision()
    val inventoryAppList = inventoryApps
    val shizukuInstalled by remember {
        derivedStateOf { inventoryApps.any { it.packageName == "moe.shizuku.privileged.api" } }
    }
    val appInventory =
        AppInventorySnapshot(
            isLoading = inventoryLoading,
            apps = inventoryAppList,
            shizukuInstalled = shizukuInstalled,
            errorMessage = inventoryError,
        )

    LaunchedEffect(cacheScopeKey, refreshToken, listRefreshTick, selectedFilters) {
        inventoryRefreshing = true
        val manualRefresh = forceRefreshPending
        forceRefreshPending = false
        inventoryError = null
        dataProvider.loadApplicationInformation(
            context = context,
            sessionId = cacheScopeKey,
            selectedFilters = selectedFilters,
            forceRefresh = manualRefresh,
        )
        val result = SessionManagementAppCache.snapshot()
        inventoryApps = result?.apps.orEmpty()
        inventoryLoading = result == null
        inventoryRefreshing = false
        inventoryError = result?.errorMessage
    }

    LaunchedEffect(appCacheRevision) {
        SessionManagementAppCache.snapshot()?.let { cached ->
            inventoryApps = cached.apps
            inventoryLoading = cached.isLoading
            inventoryError = cached.errorMessage
        }
        inventoryRefreshing = !SessionManagementAppCache.isPipelineComplete(cacheScopeKey)
    }

    LaunchedEffect(addMenuRequestTick) {
        if (addMenuRequestTick > 0) {
            appAddDialogOpen = true
        }
    }

    val normalizedSearchQuery by remember {
        derivedStateOf { submittedSearchQuery.trim().lowercase(Locale.getDefault()) }
    }

    val projectionRequest =
        remember(inventoryAppList, selectedFilters, sortSelection, packageNameOnlyMode, normalizedSearchQuery, appCacheRevision) {
            AppListProjectionRequest(
                apps = inventoryAppList,
                selectedFilters = selectedFilters,
                sort = sortSelection.sort,
                sortAscending = sortSelection.ascending,
                packageNameOnlyMode = packageNameOnlyMode,
                normalizedSearchQuery = normalizedSearchQuery,
                presentationGeneration = appCacheRevision,
            )
        }
    val projection by produceState(
        initialValue = AppListProjection(),
        key1 = projectionRequest,
    ) {
        value =
            withContext(Dispatchers.Default) {
                AppListProjection(
                    request = projectionRequest,
                    apps = projectVisibleApps(projectionRequest),
                )
            }
    }
    val visibleApps = projection.apps
    val visibleAppsReady = projection.request == projectionRequest
    val initialProjectionLoading = appInventory.isLoading || (!visibleAppsReady && visibleApps.isEmpty())

    fun refreshInventory(manual: Boolean) {
        if (manual) {
            forceRefreshPending = true
        }
        listRefreshTick += 1
    }

    val apkImportLauncher =
        FilePickerHelper.rememberImportFileLauncher { uri ->
            uri ?: return@rememberImportFileLauncher
            scope.launch {
                appActionProgress = ManagementTexts.Apps.PREPARING_APK.get()
                val result =
                    runCatching {
                        val connection =
                            SessionManagementAdbConnection.current()
                                ?: error(ManagementTexts.Apps.NO_ADB_CONNECTION_AVAILABLE.get())
                        installApkFromUri(context, connection, uri).getOrThrow()
                        ManagementTexts.Apps.INSTALL_SUCCEEDED.get()
                    }
                appActionProgress = null
                appActionResult = result.getOrNull() ?: (result.exceptionOrNull()?.message ?: ManagementTexts.Apps.INSTALL_FAILED.get())
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
            appActionResult = result.getOrNull() ?: (result.exceptionOrNull()?.message ?: ManagementTexts.Apps.ACTION_FAILED.get())
            refreshInventory(manual = true)
        }
    }

    val apkExportLauncher =
        FilePickerHelper.rememberExportFileLauncher(
            mimeType = "application/vnd.android.package-archive",
            initialDirectoryUri = FilePickerHelper.DOWNLOADS_DIRECTORY_URI,
        ) { destinationUri ->
            val entry = pendingApkExport
            pendingApkExport = null
            if (destinationUri != null && entry != null) {
                launchAppAction(progress = ManagementTexts.Apps.EXPORTING_APK.format(entry.appTitle)) {
                    exportPackageApk(
                        context = context,
                        packageName = entry.packageName,
                        destinationUri = destinationUri,
                    )
                }
            }
        }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = SessionManagementPageInnerTopPadding,
                    bottom = 0.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Surface(
                    shape = SessionManagementCardShape,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text =
                                if (visibleAppsReady || visibleApps.isNotEmpty()) {
                                    ManagementTexts.General.ITEM_COUNT.format(visibleApps.size)
                                } else {
                                    "--"
                                },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )

                        SessionManagementSearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(SessionManagementControlHeight),
                            onSearch = { submittedSearchQuery = searchQuery.trim() },
                            placeholder = ManagementTexts.Apps.SEARCH_APPS_PACKAGES.get(),
                            contentDescription = ManagementTexts.Apps.SEARCH.get(),
                        )
                    }
                }
            }

            if (
                initialProjectionLoading ||
                    (appInventory.errorMessage == null && visibleApps.isNotEmpty())
            ) {
                item {
                    Box(modifier = Modifier.height(12.dp))
                }
            }

            when {
                initialProjectionLoading -> {
                    repeat(6) { index ->
                        item(key = "app-placeholder-$index") {
                            SessionManagementVirtualizedPanelRow(
                                index = index,
                                totalCount = 6,
                                dividerInsetStart = 50.dp,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                ) {
                                    SessionManagementAppPlaceholderRow()
                                }
                            }
                        }
                    }
                }

                appInventory.errorMessage != null -> {
                    item {
                        SessionManagementNoteCard(
                            title = ManagementTexts.Apps.COULDN_T_LOAD_APPS.get(),
                            text = appInventory.errorMessage,
                        )
                    }
                }

                visibleAppsReady && visibleApps.isEmpty() -> {
                    item {
                        SessionManagementNoteCard(
                            title = ManagementTexts.Apps.NO_RESULTS.get(),
                            text = ManagementTexts.Apps.TRY_DIFFERENT_KEYWORD_FILTER.get(),
                        )
                    }
                }

                else -> {
                    itemsIndexed(
                        items = visibleApps,
                        key = { _, entry -> entry.packageName },
                    ) { index, entry ->
                        SessionManagementVirtualizedPanelRow(
                            index = index,
                            totalCount = visibleApps.size,
                            dividerInsetStart = 50.dp,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                            ) {
                                SessionManagementAppRow(
                                    entry = entry,
                                    packageNameOnlyMode = packageNameOnlyMode,
                                    presentationVersion = appCacheRevision,
                                    onClick = { selectedAppForActions = entry },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (inventoryRefreshing || !visibleAppsReady) {
            SessionManagementLoadingBar(modifier = Modifier.align(Alignment.TopCenter))
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .size(1.dp),
        ) {
            SessionManagementAppOptionsMenu(
                expanded = optionsState.expanded,
                selectedFilters = selectedFilters,
                selectedSort = sortSelection.sort,
                sortAscending = sortSelection.ascending,
                packageNameOnlyMode = packageNameOnlyMode,
                onDismiss = optionsState::dismiss,
                onRefreshList = {
                    optionsState.dismiss()
                    refreshInventory(manual = true)
                },
                onSortSelected = { selectedSort -> sortSelection = sortSelection.select(selectedSort) },
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
                localAppPickerOpen = true
                localAppsLoading = true
                localAppsError = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { loadLocalInstalledApps(context) } }
                    localApps = result.getOrDefault(emptyList())
                    localAppsError =
                        result.exceptionOrNull()?.message
                            ?: if (result.isFailure) ManagementTexts.Apps.LOCAL_APPS_LOAD_FAILED.get() else null
                    localAppsLoading = false
                }
            },
        )
    }

    if (localAppPickerOpen) {
        SessionManagementLocalAppPickerDialog(
            apps = localApps,
            isLoading = localAppsLoading,
            errorMessage = localAppsError,
            onDismiss = { localAppPickerOpen = false },
            onSelect = { app ->
                localAppPickerOpen = false
                launchAppAction(progress = ManagementTexts.Apps.INSTALLING_LOCAL_APP.format(app.label)) {
                    runCatching {
                        val connection =
                            SessionManagementAdbConnection.current()
                                ?: error(ManagementTexts.Apps.NO_ADB_CONNECTION_AVAILABLE.get())
                        connection.installApks(app.apkPaths).getOrThrow()
                        ManagementTexts.Apps.LOCAL_APP_INSTALLED.format(app.label)
                    }
                }
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
                launchAppAction(progress = ManagementTexts.Apps.LAUNCHING.format(entry.appTitle)) {
                    runShellAction(
                        command = "monkey -p ${entry.packageName} -c android.intent.category.LAUNCHER 1",
                        successMessage = ManagementTexts.Apps.TRIED_LAUNCH_DEVICE.format(entry.packageName),
                    )
                }
            },
            onToggleEnabled = {
                selectedAppForActions = null
                launchAppAction(
                    progress =
                        if (entry.isEnabled) {
                            ManagementTexts.Apps.DISABLING.format(entry.appTitle)
                        } else {
                            ManagementTexts.Apps.ENABLING.format(entry.appTitle)
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
                                ManagementTexts.Apps.TRIED_DISABLE.format(entry.packageName)
                            } else {
                                ManagementTexts.Apps.TRIED_ENABLE.format(entry.packageName)
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
                launchAppAction(progress = ManagementTexts.Apps.CLEARING_DATA.format(entry.appTitle)) {
                    runShellAction(
                        command = "pm clear ${entry.packageName}",
                        successMessage = ManagementTexts.Apps.CLEARED_DATA.format(entry.packageName),
                    )
                }
            },
            onDownloadApk = {
                selectedAppForActions = null
                pendingApkExport = entry
                apkExportLauncher.launch("${entry.packageName}.apk")
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
                launchAppAction(progress = ManagementTexts.Apps.UNINSTALLING.format(entry.appTitle)) {
                    runShellAction(
                        command = "pm uninstall ${if (keepData) "-k " else ""}${entry.packageName}",
                        successMessage = ManagementTexts.Apps.TRIED_UNINSTALL.format(entry.packageName),
                    )
                }
            },
        )
    }

    appActionProgress?.let { message ->
        SessionManagementProgressDialog(
            title = ManagementTexts.Apps.APPS.get(),
            message = message,
        )
    }

    appActionResult?.let { message ->
        SessionManagementMessageDialog(
            title = ManagementTexts.Apps.APPS.get(),
            message = message,
            onDismiss = { appActionResult = null },
        )
    }
}

private fun projectVisibleApps(request: AppListProjectionRequest): List<AppInventoryEntry> {
    val query = request.normalizedSearchQuery

    return request.apps
        .asSequence()
        .map { entry ->
            entry.copy(appTitle = resolveAppListTitle(entry, request.packageNameOnlyMode))
        }.filter { entry -> matchesSelectedAppFilters(entry, request.selectedFilters) }
        .filter { entry -> matchesAppSearch(entry, request.packageNameOnlyMode, query) }
        .sortedWith(
            appListComparator(request.sort, request.sortAscending),
        ).toList()
}

internal fun appListComparator(
    sort: AppListSort,
    ascending: Boolean,
): Comparator<AppInventoryEntry> {
    val comparator =
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
                    compareBy<AppInventoryEntry> { it.isEnabled }
                        .thenBy { it.appTitle.lowercase(Locale.getDefault()) }
                }

                AppListSort.Size -> {
                    compareBy<AppInventoryEntry> { it.apkSizeBytes ?: -1L }
                        .thenBy { it.appTitle.lowercase(Locale.getDefault()) }
                }
            }
    return if (ascending) comparator else Comparator { left, right -> comparator.compare(right, left) }
}

private fun matchesSelectedAppFilters(
    entry: AppInventoryEntry,
    selectedFilters: Set<AppListFilter>,
): Boolean {
    val matchesSource =
        if (entry.isSystemApp) {
            AppListFilter.ShowSystemApps in selectedFilters
        } else {
            AppListFilter.ShowUserApps in selectedFilters
        }
    val matchesEnabledState =
        if (entry.isEnabled) {
            AppListFilter.ShowEnabledApps in selectedFilters
        } else {
            AppListFilter.ShowDisabledApps in selectedFilters
        }
    return matchesSource && matchesEnabledState
}

private fun matchesAppSearch(
    entry: AppInventoryEntry,
    packageNameOnlyMode: Boolean,
    normalizedQuery: String,
): Boolean {
    if (normalizedQuery.isBlank()) return true
    if (entry.packageName.lowercase(Locale.getDefault()).contains(normalizedQuery)) return true
    return !packageNameOnlyMode &&
        resolveAppListTitle(entry, packageNameOnlyMode = false)
            .lowercase(Locale.getDefault())
            .contains(normalizedQuery)
}

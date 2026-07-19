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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.i18n.ManagementTexts
import com.screen.remote.android.core.common.util.FilePickerHelper
import com.screen.remote.android.infrastructure.adb.connection.installApkFromUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private data class AppListProjectionRequest(
    val apps: List<AppInventoryEntry>,
    val selectedFilters: Set<AppListFilter>,
    val sort: AppListSort,
    val packageNameOnlyMode: Boolean,
    val normalizedSearchQuery: String,
    val presentationGeneration: Int,
)

private data class AppListProjection(
    val request: AppListProjectionRequest? = null,
    val apps: List<AppInventoryEntry> = emptyList(),
)

private data class AppSizeLoadRequest(
    val enabled: Boolean,
    val candidatePackageNames: Set<String>,
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
) {
    val context = LocalContext.current
    var helperJar by remember(context) { mutableStateOf<java.io.File?>(null) }
    var cacheReady by remember(cacheScopeKey) { mutableStateOf(false) }
    LaunchedEffect(context, cacheScopeKey) {
        SessionManagementAppCache.prepareForSession(context, cacheScopeKey)
        cacheReady = true
        helperJar = withContext(Dispatchers.IO) { ensureLocalDadbHelperJar(context) }
    }
    val scope = rememberCoroutineScope()
    val appSizeLoadRequests = remember(cacheScopeKey) { Channel<AppSizeLoadRequest>(Channel.CONFLATED) }
    var listRefreshTick by remember { mutableIntStateOf(0) }
    val appPresentationVersions = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
    var appPresentationGeneration by remember { mutableIntStateOf(0) }
    var forceRefreshPending by remember { mutableStateOf(false) }
    var inventoryApps by remember { mutableStateOf<List<AppInventoryEntry>>(emptyList()) }
    var inventoryLoading by remember { mutableStateOf(true) }
    var inventoryRefreshing by remember { mutableStateOf(true) }
    var appSizesLoading by remember { mutableStateOf(false) }
    var inventoryError by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var submittedSearchQuery by remember { mutableStateOf("") }
    var selectedFilters by remember { mutableStateOf(AppListFilter.defaultSelection) }
    var sort by remember { mutableStateOf(AppListSort.Title) }
    var packageNameOnlyMode by remember { mutableStateOf(false) }
    var selectedAppForActions by remember { mutableStateOf<AppInventoryEntry?>(null) }
    var selectedAppForDetails by remember { mutableStateOf<AppInventoryEntry?>(null) }
    var pendingApkExport by remember { mutableStateOf<AppInventoryEntry?>(null) }
    var uninstallAppState by remember { mutableStateOf<AppInventoryEntry?>(null) }
    var appActionProgress by remember { mutableStateOf<String?>(null) }
    var appActionResult by remember { mutableStateOf<String?>(null) }
    var appLoadToken by remember { mutableIntStateOf(0) }
    var appAddDialogOpen by remember { mutableStateOf(false) }
    var localAppPickerOpen by remember { mutableStateOf(false) }
    var localAppsLoading by remember { mutableStateOf(false) }
    var localApps by remember { mutableStateOf<List<LocalInstalledApp>>(emptyList()) }
    var localAppsError by remember { mutableStateOf<String?>(null) }
    var helperReady by remember { mutableStateOf(false) }
    val inventoryAppList = inventoryApps
    val inventoryPackageNames by remember {
        derivedStateOf { inventoryApps.map { it.packageName } }
    }
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

    LaunchedEffect(helperJar) {
        val readyHelperJar = helperJar ?: return@LaunchedEffect
        val connection = SessionManagementAdbConnection.current()
        if (connection == null) {
            return@LaunchedEffect
        }
        val result = connection.prepareAppIconHelper(readyHelperJar)
        helperReady = result.isSuccess
    }

    LaunchedEffect(cacheReady, refreshToken, listRefreshTick) {
        if (!cacheReady) return@LaunchedEffect
        inventoryRefreshing = true
        appLoadToken += 1
        val currentLoadToken = appLoadToken
        val manualRefresh = forceRefreshPending
        forceRefreshPending = false
        inventoryError = null

        if (manualRefresh) {
            SessionManagementAppCache.clearSnapshot()
        } else {
            SessionManagementAppCache.snapshot()?.let { cached ->
                inventoryApps = cached.apps
            }
        }

        inventoryLoading = inventoryApps.isEmpty()

        if (!manualRefresh && inventoryApps.isNotEmpty()) {
            inventoryRefreshing = false
            return@LaunchedEffect
        }

        val result = loadAppInventorySnapshot(context, includeSystemApps = false, forceRefresh = manualRefresh)
        inventoryLoading = false
        inventoryRefreshing = false
        if (result.errorMessage != null) {
            if (inventoryApps.isEmpty()) {
                inventoryError = result.errorMessage
            }
            return@LaunchedEffect
        }

        inventoryApps = result.apps
        SessionManagementAppCache.updateSnapshot(result.copy(isLoading = false))

        launch {
            runCatching {
                loadAppInventorySnapshot(context, includeSystemApps = true, forceRefresh = true)
            }.onSuccess { fullSnapshot ->
                if (currentLoadToken == appLoadToken) {
                    inventoryApps = fullSnapshot.apps
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

    LaunchedEffect(addMenuRequestTick) {
        if (addMenuRequestTick > 0) {
            appAddDialogOpen = true
        }
    }

    val normalizedSearchQuery by remember {
        derivedStateOf { submittedSearchQuery.trim().lowercase(Locale.getDefault()) }
    }

    LaunchedEffect(
        sort,
        appLoadToken,
        inventoryPackageNames,
        selectedFilters,
        packageNameOnlyMode,
        normalizedSearchQuery,
    ) {
        val candidatePackageNames =
            if (sort == AppListSort.Size) {
                inventoryApps
                    .asSequence()
                    .filter { entry -> matchesSelectedAppFilters(entry, selectedFilters) }
                    .filter { entry -> matchesAppSearch(entry, packageNameOnlyMode, normalizedSearchQuery) }
                    .map { entry -> entry.packageName }
                    .toSet()
            } else {
                emptySet()
            }
        appSizeLoadRequests.trySend(
            AppSizeLoadRequest(
                enabled = sort == AppListSort.Size,
                candidatePackageNames = candidatePackageNames,
            ),
        )
    }

    LaunchedEffect(appSizeLoadRequests) {
        for (request in appSizeLoadRequests) {
            if (!request.enabled) continue
            val missingSizes =
                inventoryApps.filter { entry ->
                    entry.packageName in request.candidatePackageNames && entry.apkSizeBytes == null
                }
            if (missingSizes.isEmpty()) continue

            appSizesLoading = true
            try {
                val sizes = loadAppApkSizes(missingSizes)
                if (sizes.isNotEmpty()) {
                    inventoryApps =
                        inventoryApps.map { entry ->
                            sizes[entry.packageName]?.let { size -> entry.copy(apkSizeBytes = size) } ?: entry
                        }
                    SessionManagementAppCache.updateSnapshot(
                        AppInventorySnapshot(
                            isLoading = false,
                            apps = inventoryApps,
                            shizukuInstalled = inventoryApps.any { it.packageName == "moe.shizuku.privileged.api" },
                            errorMessage = inventoryError,
                        ),
                    )
                }
            } finally {
                appSizesLoading = false
            }
        }
    }

    LaunchedEffect(helperReady, packageNameOnlyMode, appLoadToken, inventoryPackageNames) {
        if (packageNameOnlyMode || inventoryApps.isEmpty()) {
            return@LaunchedEffect
        }
        try {
            val warmedPackages = warmCachedAppPresentations(context, inventoryAppList, packageNameOnlyMode)
            if (warmedPackages.isNotEmpty()) {
                Snapshot.withMutableSnapshot {
                    warmedPackages.forEach { packageName ->
                        appPresentationVersions[packageName] = (appPresentationVersions[packageName] ?: 0) + 1
                    }
                }
            }
            val readyHelperJar = helperJar
            if (!helperReady || readyHelperJar == null) {
                return@LaunchedEffect
            }
            var presentationUpdated = false
            prefetchAppIconsWithHelper(context, inventoryAppList, readyHelperJar) { updatedPackages ->
                presentationUpdated = presentationUpdated || updatedPackages.isNotEmpty()
                Snapshot.withMutableSnapshot {
                    updatedPackages.forEach { packageName ->
                        appPresentationVersions[packageName] = (appPresentationVersions[packageName] ?: 0) + 1
                    }
                }
            }
            if (presentationUpdated) {
                appPresentationGeneration += 1
            }
        } catch (error: Throwable) {
            runCatching {
                com.screen.remote.android.core.common.manager.LogManager.w(
                    com.screen.remote.android.core.common.LogTags.ADB_CONNECTION,
                    "helper 就绪后预取图标失败: ${error.message}",
                )
            }
        }
    }

    val projectionRequest =
        remember(inventoryAppList, selectedFilters, sort, packageNameOnlyMode, normalizedSearchQuery, appPresentationGeneration) {
            AppListProjectionRequest(
                apps = inventoryAppList,
                selectedFilters = selectedFilters,
                sort = sort,
                packageNameOnlyMode = packageNameOnlyMode,
                normalizedSearchQuery = normalizedSearchQuery,
                presentationGeneration = appPresentationGeneration,
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
        FilePickerHelper.rememberImportFileLauncher(
            mimeTypes = arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*"),
        ) { uri ->
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
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 1.dp,
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

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            trailingIcon = {
                                IconButton(onClick = { submittedSearchQuery = searchQuery.trim() }) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = ManagementTexts.Apps.SEARCH.get(),
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions =
                                KeyboardActions(
                                    onSearch = { submittedSearchQuery = searchQuery.trim() },
                                ),
                            placeholder = {
                                Text(
                                    text = ManagementTexts.Apps.SEARCH_APPS_PACKAGES.get(),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }

            if (
                initialProjectionLoading ||
                    (!appInventory.isLoading && appInventory.errorMessage == null && visibleApps.isNotEmpty())
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
                                    presentationVersion = appPresentationVersions[entry.packageName] ?: 0,
                                    onClick = { selectedAppForActions = entry },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (inventoryRefreshing || appSizesLoading || !visibleAppsReady) {
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
                selectedSort = sort,
                packageNameOnlyMode = packageNameOnlyMode,
                onDismiss = optionsState::dismiss,
                onRefreshList = {
                    optionsState.dismiss()
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
            when (request.sort) {
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

                AppListSort.Size -> {
                    compareByDescending<AppInventoryEntry> { it.apkSizeBytes ?: -1L }
                        .thenBy { it.appTitle.lowercase(Locale.getDefault()) }
                }
            },
        ).toList()
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

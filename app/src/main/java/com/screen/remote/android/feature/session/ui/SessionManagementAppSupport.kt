package com.screen.remote.android.feature.session.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.screen.remote.android.core.i18n.ManagementTexts
import com.screen.remote.android.core.common.util.compat.putIfAbsentCompat
import com.screen.remote.android.infrastructure.adb.connection.AdbConnection
import dadb.helper.RemoteAppField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class AppInventoryEntry(
    val packageName: String,
    val appTitle: String,
    val isSystemApp: Boolean,
    val apkPath: String,
    val isEnabled: Boolean,
    val versionCode: Long = 0L,
    val versionName: String = "",
    val lastUpdateTime: Long = 0L,
    val apkSizeBytes: Long? = null,
)

internal data class LocalInstalledApp(
    val label: String,
    val packageName: String,
    val apkPaths: List<String>,
    val isSystemApp: Boolean,
)

internal data class AppInventorySnapshot(
    val isLoading: Boolean,
    val apps: List<AppInventoryEntry>,
    val shizukuInstalled: Boolean,
    val errorMessage: String? = null,
) {
    val packages: List<String>
        get() = apps.map { it.packageName }

    companion object {
        fun loading(): AppInventorySnapshot =
            AppInventorySnapshot(
                isLoading = true,
                apps = emptyList(),
                shizukuInstalled = false,
            )
    }
}

internal enum class AppListFilter(
    private val text: com.screen.remote.android.core.i18n.TextPair,
) {
    ShowSystemApps(ManagementTexts.Apps.SHOW_SYSTEM_APPS),
    ShowUserApps(ManagementTexts.Apps.SHOW_USER_APPS),
    ShowEnabledApps(ManagementTexts.Apps.SHOW_ENABLED_APPS),
    ShowDisabledApps(ManagementTexts.Apps.SHOW_DISABLED_APPS),
    ;

    val label: String
        get() = text.get()

    companion object {
        val defaultSelection: Set<AppListFilter> =
            setOf(
                ShowUserApps,
                ShowEnabledApps,
            )
    }
}

internal enum class AppListSort(
    val text: com.screen.remote.android.core.i18n.TextPair,
) {
    Title(ManagementTexts.Apps.SORT_BY_APP_NAME),
    Package(ManagementTexts.Apps.SORT_BY_PACKAGE),
    EnabledState(ManagementTexts.Apps.SORT_BY_ENABLED_STATE),
    Size(ManagementTexts.Apps.SORT_BY_SIZE),
}

internal val AppListSort.label: String
    get() = text.get()

internal data class AppListSortSelection(
    val sort: AppListSort = AppListSort.Title,
    val ascending: Boolean = true,
) {
    fun select(selectedSort: AppListSort): AppListSortSelection =
        if (sort == selectedSort) {
            copy(ascending = !ascending)
        } else {
            AppListSortSelection(sort = selectedSort, ascending = true)
        }
}

@Serializable
internal data class AppIconCacheMetadata(
    val versionCode: Long,
    val versionName: String,
    val lastUpdateTime: Long,
)

internal fun AppIconCacheMetadata.matches(entry: AppInventoryEntry): Boolean =
    entry.lastUpdateTime > 0L &&
        versionCode == entry.versionCode &&
        versionName == entry.versionName &&
        lastUpdateTime == entry.lastUpdateTime

@Serializable
private data class AppIconIndexSnapshot(
    val entries: Map<String, AppIconCacheMetadata> = emptyMap(),
)

internal fun resolveAppListTitle(
    entry: AppInventoryEntry,
    packageNameOnlyMode: Boolean,
): String {
    if (packageNameOnlyMode) {
        return entry.packageName
    }
    return SessionManagementAppCache.appTitle(entry.packageName, entry.appTitle)
}

private fun sanitizeAppTitle(
    rawTitle: String?,
    packageName: String,
): String {
    val title = rawTitle?.trim().orEmpty()
    if (title.isBlank()) return ""
    if (title == packageName) return ""
    val isLikelyPathLike =
        title.startsWith("package:") ||
            title.endsWith(".apk") ||
            title.contains("/") &&
                (title.contains("base.apk=") || title.contains(".apk"))
    if (isLikelyPathLike) {
        if (title.contains("=")) {
            val suffixAfterEquals = title.substringAfterLast("=").trim()
            return suffixAfterEquals.takeIf { it.isNotBlank() && it != packageName } ?: ""
        }
        return ""
    }
    return title
}

internal object SessionManagementAppCache {
    private var activeScopeKey: String? = null
    private var iconIndexPrepared = false
    private var snapshot: AppInventorySnapshot? = null
    private val filteredSnapshots = mutableMapOf<Set<AppListFilter>, AppInventorySnapshot>()
    private val detailCache = mutableMapOf<String, AppDetailSnapshot>()
    private val detailLoadMutexes = mutableMapOf<String, Mutex>()
    private val iconCache = mutableMapOf<String, Bitmap?>()
    private val iconMetadataCache = mutableMapOf<String, AppIconCacheMetadata>()
    private val titleCache = mutableMapOf<String, String>()
    private var iconHelperUnavailableReason: String? = null
    private var iconHelperDiagnosticsCaptured = false
    private var pipelineComplete = false
    private val revisionState = mutableIntStateOf(0)
    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = false
        }

    fun selectScope(scopeKey: String) {
        synchronized(this) {
            if (activeScopeKey == scopeKey) return
            activeScopeKey = scopeKey
            snapshot = null
            filteredSnapshots.clear()
            detailCache.clear()
            detailLoadMutexes.clear()
            titleCache.clear()
            iconHelperUnavailableReason = null
            iconHelperDiagnosticsCaptured = false
            pipelineComplete = false
            bumpRevision()
        }
    }

    fun releaseScope(scopeKey: String) {
        synchronized(this) {
            if (activeScopeKey != scopeKey) return
            activeScopeKey = null
            snapshot = null
            filteredSnapshots.clear()
            detailCache.clear()
            detailLoadMutexes.clear()
            titleCache.clear()
            iconHelperUnavailableReason = null
            iconHelperDiagnosticsCaptured = false
            pipelineComplete = false
            bumpRevision()
        }
    }

    suspend fun prepareForSession(
        context: Context,
        scopeKey: String,
    ) {
        selectScope(scopeKey)
        val shouldLoad =
            synchronized(this) {
                !iconIndexPrepared
            }
        if (!shouldLoad) return

        val indexSnapshot = withContext(Dispatchers.IO) { readIconIndex(context) }
        synchronized(this) {
            if (!iconIndexPrepared) {
                indexSnapshot?.let { snapshot ->
                    iconMetadataCache.putAll(snapshot.entries)
                }
                iconIndexPrepared = true
            }
        }
    }

    fun snapshot(): AppInventorySnapshot? =
        synchronized(this) {
            snapshot
        }

    fun filteredSnapshot(filters: Set<AppListFilter>): AppInventorySnapshot? =
        synchronized(this) {
            filteredSnapshots[filters]
        }

    fun revision(): Int = revisionState.intValue

    fun isPipelineComplete(scopeKey: String): Boolean =
        synchronized(this) {
            activeScopeKey == scopeKey && pipelineComplete
        }

    fun isActiveScope(scopeKey: String): Boolean =
        synchronized(this) {
            activeScopeKey == scopeKey
        }

    fun markPipelineComplete(scopeKey: String) {
        synchronized(this) {
            if (activeScopeKey == scopeKey) {
                pipelineComplete = true
                bumpRevision()
            }
        }
    }

    fun markPipelineLoading(scopeKey: String) {
        synchronized(this) {
            if (activeScopeKey == scopeKey) {
                pipelineComplete = false
                bumpRevision()
            }
        }
    }

    fun updateSnapshot(value: AppInventorySnapshot) {
        synchronized(this) {
            snapshot = value
            value.apps.forEach { entry ->
                titleCache.putIfAbsentCompat(entry.packageName, entry.appTitle)
            }
            bumpRevision()
        }
    }

    fun updateFilteredSnapshot(
        filters: Set<AppListFilter>,
        value: AppInventorySnapshot,
    ) {
        synchronized(this) {
            value.apps.forEach { entry ->
                if (!iconMetadataMatchesLocked(entry)) {
                    iconCache.remove(entry.packageName)
                }
            }
            filteredSnapshots[filters.toSet()] = value
            snapshot = value
            value.apps.forEach { entry ->
                titleCache.putIfAbsentCompat(entry.packageName, entry.appTitle)
            }
            bumpRevision()
        }
    }

    fun clearSnapshot() {
        synchronized(this) {
            snapshot = null
            filteredSnapshots.clear()
            detailCache.clear()
            pipelineComplete = false
            bumpRevision()
        }
    }

    fun cachedAppDetail(packageName: String): AppDetailSnapshot? =
        synchronized(this) {
            detailCache[packageName]
        }

    fun detailLoadMutex(packageName: String): Mutex =
        synchronized(this) {
            detailLoadMutexes.getOrPut(packageName) { Mutex() }
        }

    fun updateAppDetail(snapshot: AppDetailSnapshot) {
        synchronized(this) {
            detailCache[snapshot.packageName] = snapshot
            if (snapshot.appTitle.isNotBlank()) {
                titleCache[snapshot.packageName] = snapshot.appTitle
            }
            bumpRevision()
        }
    }

    fun updateAppDetails(snapshots: List<AppDetailSnapshot>) {
        synchronized(this) {
            snapshots.forEach { snapshot ->
                detailCache[snapshot.packageName] = snapshot
                if (snapshot.appTitle.isNotBlank()) {
                    titleCache[snapshot.packageName] = snapshot.appTitle
                }
            }
            bumpRevision()
        }
    }

    fun appTitle(
        packageName: String,
        fallback: String,
    ): String =
        synchronized(this) {
            sanitizeAppTitle(titleCache[packageName], packageName)
                .ifBlank { sanitizeAppTitle(fallback, packageName).ifBlank { guessAppTitle(packageName) } }
        }

    fun cachedAppTitle(packageName: String): String? =
        synchronized(this) {
            titleCache[packageName]
        }

    fun updateAppTitle(
        packageName: String,
        title: String,
    ) {
        synchronized(this) {
            if (title.isNotBlank() && titleCache[packageName] != title) {
                titleCache[packageName] = title
                bumpRevision()
            }
        }
    }

    private fun bumpRevision() {
        Snapshot.withMutableSnapshot {
            revisionState.intValue += 1
        }
    }

    fun cachedIcon(packageName: String): Bitmap? =
        synchronized(this) {
            iconCache[packageName]
        }

    fun hasValidIcon(entry: AppInventoryEntry): Boolean =
        synchronized(this) {
            iconCache[entry.packageName] != null && iconMetadataMatchesLocked(entry)
        }

    fun updateIcon(
        packageName: String,
        icon: Bitmap?,
    ) {
        synchronized(this) {
            iconCache[packageName] = icon
            bumpRevision()
        }
    }

    fun markIconHelperUnavailable(reason: String) {
        synchronized(this) {
            if (iconHelperUnavailableReason == null) {
                iconHelperUnavailableReason = reason
            }
        }
    }

    fun iconHelperUnavailableReason(): String? =
        synchronized(this) {
            iconHelperUnavailableReason
        }

    fun shouldCaptureIconHelperDiagnostics(): Boolean =
        synchronized(this) {
            !iconHelperDiagnosticsCaptured
        }

    fun markIconHelperDiagnosticsCaptured() {
        synchronized(this) {
            iconHelperDiagnosticsCaptured = true
        }
    }

    fun iconMetadataMatches(entry: AppInventoryEntry): Boolean =
        synchronized(this) {
            iconMetadataMatchesLocked(entry)
        }

    private fun iconMetadataMatchesLocked(entry: AppInventoryEntry): Boolean =
        iconMetadataCache[entry.packageName]?.matches(entry) == true

    private fun readIconIndex(context: Context): AppIconIndexSnapshot? {
        val file = getIconIndexFile(context)
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString<AppIconIndexSnapshot>(file.readText())
        }.getOrNull()
    }

    private fun writeIconIndex(context: Context) {
        val file = getIconIndexFile(context)
        file.parentFile?.mkdirs()
        file.writeText(
            json.encodeToString(
                AppIconIndexSnapshot(
                    entries = iconMetadataCache.toSortedMap(),
                ),
            ),
        )
    }

    fun updateIconMetadataBatch(
        entries: Map<String, AppIconCacheMetadata>,
    ) {
        synchronized(this) {
            iconMetadataCache.putAll(entries)
        }
    }

    fun persistIconMetadata(context: Context) {
        synchronized(this) {
            writeIconIndex(context)
        }
    }
}

internal data class AppDetailSnapshot(
    val isLoading: Boolean,
    val appTitle: String,
    val packageName: String,
    val apkSize: String,
    val versionName: String,
    val isSystemApp: Boolean,
    val minSdk: String,
    val targetSdk: String,
    val firstInstallTime: String,
    val lastUpdateTime: String,
    val errorMessage: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
) {
    companion object {
        fun loading(packageName: String): AppDetailSnapshot =
            AppDetailSnapshot(
                isLoading = true,
                appTitle = guessAppTitle(packageName),
                packageName = packageName,
                apkSize = "",
                versionName = "",
                isSystemApp = false,
                minSdk = "",
                targetSdk = "",
                firstInstallTime = "",
                lastUpdateTime = "",
            )

        fun loading(entry: AppInventoryEntry): AppDetailSnapshot =
            AppDetailSnapshot(
                isLoading = true,
                appTitle = resolveAppListTitle(entry, packageNameOnlyMode = false),
                packageName = entry.packageName,
                apkSize = "",
                versionName = "",
                isSystemApp = entry.isSystemApp,
                minSdk = "",
                targetSdk = "",
                firstInstallTime = "",
                lastUpdateTime = "",
            )
    }
}

internal suspend fun loadAppInventorySnapshot(
    context: Context,
    includeSystemApps: Boolean,
): AppInventorySnapshot {
    return runCatching {
        val remoteApps =
            queryAppDataWithHelper(
                context = context,
                includeUser = true,
                includeSystem = includeSystemApps,
                includeEnabled = true,
                includeDisabled = true,
                fields = setOf("list"),
            )
        val apps = remoteApps.map(::remoteAppDataToInventoryEntry)
        AppInventorySnapshot(
            isLoading = false,
            apps = apps,
            shizukuInstalled = apps.any { it.packageName == "moe.shizuku.privileged.api" },
        )
    }.getOrElse { error ->
        if (error is CancellationException) throw error
        AppInventorySnapshot.loading().copy(
            isLoading = false,
            errorMessage = error.message ?: ManagementTexts.Apps.APP_LIST_LOAD_FAILED.get(),
        )
    }
}

private suspend fun queryAppDataWithHelper(
    context: Context,
    includeUser: Boolean,
    includeSystem: Boolean,
    includeEnabled: Boolean,
    includeDisabled: Boolean,
    fields: Set<String>,
    packageNames: Set<String> = emptySet(),
): List<dadb.helper.RemoteAppData> {
    val connection =
        SessionManagementAdbConnection.current()
            ?: error(ManagementTexts.Apps.NO_CONNECTION_FOR_APP_LIST.get())
    val helperJar = withContext(Dispatchers.IO) { ensureLocalDadbHelperJar(context) }
    val requestFields = fields.toSet()
    val primaryResult =
        connection
            .loadAppsWithHelper(
                includeUser = includeUser,
                includeSystem = includeSystem,
                includeEnabled = includeEnabled,
                includeDisabled = includeDisabled,
                fields = requestFields,
                packageNames = packageNames,
                localHelperJar = helperJar,
            )
    return primaryResult.getOrElse { error ->
        if (error is CancellationException) throw error
        throw error
    }
        .also { apps ->
            val partialFailures = apps.count { it.errors.isNotEmpty() }
            if (partialFailures > 0) {
                com.screen.remote.android.core.common.manager.LogManager.w(
                    com.screen.remote.android.core.common.LogTags.ADB_CONNECTION,
                    "App data helper completed with partial failures for $partialFailures of ${apps.size} apps",
                )
            }
        }
}

private fun remoteAppDataToInventoryEntry(
    app: dadb.helper.RemoteAppData,
    fallback: AppInventoryEntry? = null,
): AppInventoryEntry =
    AppInventoryEntry(
        packageName = app.packageName,
        appTitle =
            app.valueOrFallback(
                field = RemoteAppField.Label,
                value = sanitizeAppTitle(app.label, app.packageName),
                missingValue = "",
                fallbackValue = fallback?.appTitle,
            ).ifBlank { guessAppTitle(app.packageName) },
        isSystemApp =
            app.valueOrFallback(RemoteAppField.SystemApp, app.systemApp, false, fallback?.isSystemApp),
        apkPath =
            app.valueOrFallback(RemoteAppField.SourceDir, app.sourceDir, "", fallback?.apkPath),
        isEnabled =
            app.valueOrFallback(RemoteAppField.Enabled, app.enabled, false, fallback?.isEnabled),
        versionCode =
            app.valueOrFallback(RemoteAppField.VersionCode, app.versionCode, 0L, fallback?.versionCode),
        versionName =
            app.valueOrFallback(RemoteAppField.VersionName, app.versionName, "", fallback?.versionName),
        lastUpdateTime =
            app.valueOrFallback(RemoteAppField.LastUpdateTime, app.lastUpdateTime, 0L, fallback?.lastUpdateTime),
        apkSizeBytes =
            app.valueOrFallback(
                field = RemoteAppField.ApkSizeBytes,
                value = app.apkSizeBytes.takeIf { it > 0L },
                missingValue = null,
                fallbackValue = fallback?.apkSizeBytes,
            ),
    )

private fun remoteAppDataToDetailSnapshot(
    app: dadb.helper.RemoteAppData,
    fallback: AppDetailSnapshot? = null,
): AppDetailSnapshot =
    AppDetailSnapshot(
        isLoading = false,
        appTitle =
            app.valueOrFallback(
                field = RemoteAppField.Label,
                value = sanitizeAppTitle(app.label, app.packageName),
                missingValue = "",
                fallbackValue = fallback?.appTitle,
            ).ifBlank { guessAppTitle(app.packageName) },
        packageName = app.packageName,
        apkSize =
            app.valueOrFallback(
                RemoteAppField.ApkSizeBytes,
                app.apkSizeBytes.takeIf { it > 0L }?.let(::formatAppSize).orEmpty(),
                "",
                fallback?.apkSize,
            ),
        versionName =
            app.valueOrFallback(RemoteAppField.VersionName, app.versionName, "", fallback?.versionName),
        isSystemApp =
            app.valueOrFallback(RemoteAppField.SystemApp, app.systemApp, false, fallback?.isSystemApp),
        minSdk =
            app.valueOrFallback(
                RemoteAppField.MinSdk,
                app.minSdk.takeIf { it > 0 }?.toString().orEmpty(),
                "",
                fallback?.minSdk,
            ),
        targetSdk =
            app.valueOrFallback(
                RemoteAppField.TargetSdk,
                app.targetSdk.takeIf { it > 0 }?.toString().orEmpty(),
                "",
                fallback?.targetSdk,
            ),
        firstInstallTime =
            app.valueOrFallback(
                RemoteAppField.FirstInstallTime,
                formatAppTimestamp(app.firstInstallTime),
                "",
                fallback?.firstInstallTime,
            ),
        lastUpdateTime =
            app.valueOrFallback(
                RemoteAppField.LastUpdateTime,
                formatAppTimestamp(app.lastUpdateTime),
                "",
                fallback?.lastUpdateTime,
            ),
        fieldErrors =
            app.fieldResults.mapNotNull { (field, result) ->
                result.errorReason?.let { reason -> field.wireName to reason }
            }.toMap(),
    )

private fun <T> dadb.helper.RemoteAppData.valueOrFallback(
    field: RemoteAppField,
    value: T,
    missingValue: T,
    fallbackValue: T?,
): T =
    when {
        hasValue(field) -> value
        isMissing(field) -> missingValue
        else -> fallbackValue ?: missingValue
    }

private suspend fun loadAppDetailsWithHelper(
    context: Context,
    apps: List<AppInventoryEntry>,
    includeUser: Boolean,
    includeSystem: Boolean,
    includeEnabled: Boolean,
    includeDisabled: Boolean,
): List<dadb.helper.RemoteAppData> =
    coroutineScope {
        apps
            .chunked(APP_HELPER_BATCH_SIZE)
            .chunked(APP_HELPER_CONCURRENCY)
            .flatMap { wave ->
                val waveResults =
                    wave
                        .map { chunk ->
                            async {
                                runCatching {
                                    queryAppDataWithHelper(
                                        context = context,
                                        includeUser = includeUser,
                                        includeSystem = includeSystem,
                                        includeEnabled = includeEnabled,
                                        includeDisabled = includeDisabled,
                                        fields = setOf("details"),
                                        packageNames = chunk.mapTo(linkedSetOf(), AppInventoryEntry::packageName),
                                    )
                                }.onFailure { error ->
                                    if (error is CancellationException) throw error
                                    com.screen.remote.android.core.common.manager.LogManager.w(
                                        com.screen.remote.android.core.common.LogTags.ADB_CONNECTION,
                                        "App detail helper batch failed for ${chunk.size} apps. reason=${error.message}",
                                    )
                                }.getOrDefault(emptyList())
                            }
                        }.awaitAll()
                        .flatten()
                if (waveResults.isNotEmpty()) {
                    SessionManagementAppCache.updateAppDetails(
                        waveResults.map { app ->
                            remoteAppDataToDetailSnapshot(
                                app = app,
                                fallback = SessionManagementAppCache.cachedAppDetail(app.packageName),
                            )
                        },
                    )
                }
                waveResults
            }
    }

private val sessionManagementAppPipelineMutex = Mutex()

/**
 * The single entry point for application data. Results are deliberately published in three
 * stages: publish the basic package inventory first, then load icons and full details concurrently.
 */
internal suspend fun loadSessionManagementAppData(
    context: Context,
    scopeKey: String,
    selectedFilters: Set<AppListFilter>,
    forceRefresh: Boolean = false,
) {
    sessionManagementAppPipelineMutex.withLock {
        SessionManagementAppCache.prepareForSession(context, scopeKey)
        if (forceRefresh) SessionManagementAppCache.clearSnapshot()
        if (!forceRefresh) {
            SessionManagementAppCache.filteredSnapshot(selectedFilters)?.let { cached ->
                SessionManagementAppCache.updateSnapshot(cached)
                SessionManagementAppCache.markPipelineComplete(scopeKey)
                return@withLock
            }
        }
        SessionManagementAppCache.markPipelineLoading(scopeKey)
        val includeUser = AppListFilter.ShowUserApps in selectedFilters
        val includeSystem = AppListFilter.ShowSystemApps in selectedFilters
        val includeEnabled = AppListFilter.ShowEnabledApps in selectedFilters
        val includeDisabled = AppListFilter.ShowDisabledApps in selectedFilters
        if ((!includeUser && !includeSystem) || (!includeEnabled && !includeDisabled)) {
            SessionManagementAppCache.updateFilteredSnapshot(
                selectedFilters,
                AppInventorySnapshot(false, emptyList(), shizukuInstalled = false),
            )
            SessionManagementAppCache.markPipelineComplete(scopeKey)
            return@withLock
        }

        val inventoryResult =
            runCatching {
                queryAppDataWithHelper(
                    context = context,
                    includeUser = includeUser,
                    includeSystem = includeSystem,
                    includeEnabled = includeEnabled,
                    includeDisabled = includeDisabled,
                    fields = setOf("list"),
                )
            }
        val inventoryApps = inventoryResult.getOrElse { error ->
            if (error is CancellationException) throw error
            SessionManagementAppCache.updateSnapshot(
                AppInventorySnapshot.loading().copy(
                    isLoading = false,
                    errorMessage = error.message ?: ManagementTexts.Apps.APP_LIST_LOAD_FAILED.get(),
                ),
            )
            return@withLock
        }
        if (!SessionManagementAppCache.isActiveScope(scopeKey)) return@withLock

        val apps = inventoryApps.map(::remoteAppDataToInventoryEntry)
        SessionManagementAppCache.updateFilteredSnapshot(
            selectedFilters,
            AppInventorySnapshot(
                isLoading = false,
                apps = apps,
                shizukuInstalled = apps.any { it.packageName == "moe.shizuku.privileged.api" },
            ),
        )

        val detailApps =
            coroutineScope {
                val iconJob =
                    async {
                        runCatching { warmAndPrefetchAppPresentations(context, apps) }
                            .onFailure { error -> if (error is CancellationException) throw error }
                    }
                val detailJob =
                    async {
                        loadAppDetailsWithHelper(
                            context = context,
                            apps = apps,
                            includeUser = includeUser,
                            includeSystem = includeSystem,
                            includeEnabled = includeEnabled,
                            includeDisabled = includeDisabled,
                        )
                    }
                iconJob.await()
                detailJob.await()
            }
        if (!SessionManagementAppCache.isActiveScope(scopeKey)) return@withLock

        if (detailApps.isNotEmpty()) {
            val detailsByPackage = detailApps.associateBy(dadb.helper.RemoteAppData::packageName)
            val detailedApps =
                apps.map { entry ->
                    detailsByPackage[entry.packageName]
                        ?.let { app -> remoteAppDataToInventoryEntry(app, fallback = entry) }
                        ?: entry
                }
            SessionManagementAppCache.updateFilteredSnapshot(
                selectedFilters,
                AppInventorySnapshot(
                    isLoading = false,
                    apps = detailedApps,
                    shizukuInstalled = detailedApps.any { it.packageName == "moe.shizuku.privileged.api" },
                ),
            )
        }
        SessionManagementAppCache.markPipelineComplete(scopeKey)
    }
}

private fun formatAppTimestamp(timestamp: Long): String =
    timestamp.takeIf { it > 0L }?.let {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(it))
    }.orEmpty()

internal fun guessAppTitle(packageName: String): String {
    val normalized = packageName.substringBefore(':')
    val exactPredefined =
        mapOf(
            "moe.shizuku.privileged.api" to "Shizuku",
            "me.piebridge.brevent" to "Brevent",
            "com.catchingnow.icebox" to "Ice Box",
        )
    exactPredefined[normalized]?.let { return it }

    val key = normalized.substringAfterLast('.').lowercase(Locale.getDefault())
    val predefined =
        mapOf(
            "android" to ManagementTexts.Apps.ANDROID_SYSTEM.get(),
            "settings" to ManagementTexts.Apps.SETTINGS_APP.get(),
            "systemui" to ManagementTexts.Apps.SYSTEM_UI.get(),
            "vending" to "Google Play Store",
            "documentsui" to ManagementTexts.Apps.DOCUMENTS.get(),
            "packageinstaller" to ManagementTexts.Apps.PACKAGE_INSTALLER.get(),
            "launcher" to ManagementTexts.Apps.LAUNCHER.get(),
            "oneuihome" to ManagementTexts.Apps.ONE_UI_HOME.get(),
            "permissioncontroller" to ManagementTexts.Apps.PERMISSION_CONTROLLER.get(),
            "bluetooth" to ManagementTexts.Apps.BLUETOOTH.get(),
            "phone" to ManagementTexts.Apps.PHONE.get(),
            "contacts" to ManagementTexts.Apps.CONTACTS.get(),
            "camera" to ManagementTexts.Apps.CAMERA.get(),
            "gallery" to ManagementTexts.Apps.GALLERY.get(),
            "music" to ManagementTexts.Apps.MUSIC.get(),
            "video" to ManagementTexts.Apps.VIDEO.get(),
        )

    predefined[key]?.let { return it }

    val genericSuffixes =
        setOf(
            "app",
            "apps",
            "api",
            "android",
            "cn",
            "com",
            "client",
            "core",
            "debug",
            "helper",
            "impl",
            "io",
            "main",
            "me",
            "mobile",
            "net",
            "org",
            "privileged",
            "release",
            "service",
            "services",
            "tv",
            "ui",
        )
    val meaningfulToken =
        normalized
            .split(Regex("[._-]+"))
            .asReversed()
            .firstOrNull { token ->
                val lower = token.lowercase(Locale.getDefault())
                token.isNotBlank() && token.length > 2 && lower !in genericSuffixes
            }

    val lastToken = normalized.substringAfterLast('.').ifBlank { normalized }
    val fallback =
        meaningfulToken
            ?: lastToken.takeIf { token ->
                token.lowercase(Locale.getDefault()) !in genericSuffixes && token.length > 2
            }
            ?: normalized
    return fallback.replaceFirstChar { char ->
        if (char.isLowerCase()) {
            char.titlecase(Locale.getDefault())
        } else {
            char.toString()
        }
    }
}

internal data class RemoteAppPresentation(
    val title: String,
    val icon: Bitmap?,
)

private const val DADB_HELPER_ASSET_NAME = "dadb-helper.jar"
private const val APP_HELPER_BATCH_SIZE = 50
private const val APP_HELPER_CONCURRENCY = 3

private data class AppIconChunkResult(
    val fetchedCount: Int,
    val updatedMetadata: Map<String, AppIconCacheMetadata>,
    val updatedPackages: List<String>,
)

internal suspend fun loadCachedAppPresentation(
    context: Context,
    entry: AppInventoryEntry,
    packageNameOnlyMode: Boolean,
): RemoteAppPresentation =
    withContext(Dispatchers.IO) {
        val resolvedTitle =
            if (packageNameOnlyMode) {
                entry.packageName
            } else {
                SessionManagementAppCache.appTitle(entry.packageName, entry.appTitle)
            }

        if (packageNameOnlyMode) {
            return@withContext RemoteAppPresentation(
                title = resolvedTitle,
                icon = SessionManagementAppCache.cachedIcon(entry.packageName),
            )
        }

        val cachedIcon = SessionManagementAppCache.cachedIcon(entry.packageName)
        if (cachedIcon != null && SessionManagementAppCache.iconMetadataMatches(entry)) {
            return@withContext RemoteAppPresentation(
                title = resolvedTitle,
                icon = cachedIcon,
            )
        }

        val iconFile = getAppIconFile(context, entry.packageName)
        val bitmap =
            if (SessionManagementAppCache.iconMetadataMatches(entry) && iconFile.exists()) {
                runCatching { BitmapFactory.decodeFile(iconFile.absolutePath) }.getOrNull()
            } else {
                null
            }
        if (bitmap != null) {
            SessionManagementAppCache.updateIcon(entry.packageName, bitmap)
        }

        RemoteAppPresentation(
            title = resolvedTitle,
            icon = bitmap,
        )
    }

internal suspend fun warmCachedAppPresentations(
    context: Context,
    entries: List<AppInventoryEntry>,
    packageNameOnlyMode: Boolean,
): List<String> =
    withContext(Dispatchers.IO) {
        if (packageNameOnlyMode) {
            return@withContext emptyList()
        }

        entries
            .filterNot(SessionManagementAppCache::hasValidIcon)
            .mapNotNull { entry ->
                if (!SessionManagementAppCache.iconMetadataMatches(entry)) {
                    return@mapNotNull null
                }
                val iconFile = getAppIconFile(context, entry.packageName)
                if (!iconFile.exists()) {
                    return@mapNotNull null
                }
                val bitmap = runCatching { BitmapFactory.decodeFile(iconFile.absolutePath) }.getOrNull() ?: return@mapNotNull null
                SessionManagementAppCache.updateIcon(entry.packageName, bitmap)
                entry.packageName
            }
    }

private suspend fun warmAndPrefetchAppPresentations(
    context: Context,
    entries: List<AppInventoryEntry>,
) {
    warmCachedAppPresentations(context, entries, packageNameOnlyMode = false)
    val missingIconApps = entries.filterNot(SessionManagementAppCache::hasValidIcon)
    if (missingIconApps.isEmpty()) return

    val helperJar = withContext(Dispatchers.IO) { ensureLocalDadbHelperJar(context) }
    prefetchAppIconsWithHelper(context, missingIconApps, helperJar)
}

internal suspend fun prefetchAppIconsWithHelper(
    context: Context,
    entries: List<AppInventoryEntry>,
    helperJar: File,
    onChunkApplied: suspend (List<String>) -> Unit = {},
): Int =
    withContext(Dispatchers.IO) {
        val helperGateway = AppIconHelperGateway.current(helperJar) ?: return@withContext 0
        val allUpdatedMetadata = linkedMapOf<String, AppIconCacheMetadata>()
        entries
            .chunked(APP_HELPER_BATCH_SIZE)
            .chunked(APP_HELPER_CONCURRENCY)
            .sumOf { wave ->
                coroutineScope {
                    wave
                        .map { chunk ->
                            async {
                                runCatching {
                                    prefetchAppIconChunkWithHelper(
                                        context = context,
                                        helperGateway = helperGateway,
                                        chunk = chunk,
                                    )
                                }.onFailure { error ->
                                    if (error is CancellationException) throw error
                                    com.screen.remote.android.core.common.manager.LogManager.w(
                                        com.screen.remote.android.core.common.LogTags.ADB_CONNECTION,
                                        "App icon helper batch failed for ${chunk.size} apps. reason=${error.message}",
                                    )
                                }.getOrNull()
                            }
                        }.awaitAll()
                        .filterNotNull()
                        .sumOf { result ->
                            if (result.updatedMetadata.isNotEmpty()) {
                                SessionManagementAppCache.updateIconMetadataBatch(
                                    entries = result.updatedMetadata,
                                )
                                allUpdatedMetadata.putAll(result.updatedMetadata)
                                withContext(Dispatchers.Main) {
                                    onChunkApplied(result.updatedPackages)
                                }
                            }
                            result.fetchedCount
                        }
                }
            }.also {
                if (allUpdatedMetadata.isNotEmpty()) {
                    SessionManagementAppCache.persistIconMetadata(context)
                }
            }
    }

private suspend fun prefetchAppIconChunkWithHelper(
    context: Context,
    helperGateway: AppIconHelperGateway,
    chunk: List<AppInventoryEntry>,
): AppIconChunkResult {
    val result = helperGateway.loadIconBatch(chunk.map(AppInventoryEntry::packageName))

    val updatedMetadata = linkedMapOf<String, AppIconCacheMetadata>()
    val updatedPackages = mutableListOf<String>()

    result.entries.forEach { remoteIcon ->
        updatedMetadata[remoteIcon.packageName] =
            AppIconCacheMetadata(
                versionCode = remoteIcon.versionCode,
                versionName = remoteIcon.versionName,
                lastUpdateTime = remoteIcon.lastUpdateTime,
            )
        sanitizeAppTitle(remoteIcon.label, remoteIcon.packageName)
            .takeIf(String::isNotBlank)
            ?.let { title -> SessionManagementAppCache.updateAppTitle(remoteIcon.packageName, title) }
        updatedPackages += remoteIcon.packageName

        val imageBytes = remoteIcon.imageBytes
        val iconFile = getAppIconFile(context, remoteIcon.packageName)
        iconFile.parentFile?.mkdirs()
        iconFile.writeBytes(imageBytes)
        val bitmap =
            BitmapFactory.decodeByteArray(
                imageBytes,
                0,
                imageBytes.size,
            )
        SessionManagementAppCache.updateIcon(remoteIcon.packageName, bitmap)
    }

    return AppIconChunkResult(
        fetchedCount = updatedPackages.size,
        updatedMetadata = updatedMetadata,
        updatedPackages = updatedPackages,
    )
}

private fun getAppIconFile(
    context: Context,
    packageName: String,
): File {
    val iconDir =
        File(
            context.filesDir,
            com.screen.remote.android.core.common.constants.FilePathConstants.APP_ICONS_DIR,
        )
    if (!iconDir.exists()) {
        iconDir.mkdirs()
    }
    return File(iconDir, "${sanitizeAppIconFileName(packageName)}.webp")
}

private fun getIconIndexFile(context: Context): File =
    File(
        File(context.filesDir, com.screen.remote.android.core.common.constants.FilePathConstants.APP_ICONS_DIR),
        "index.json",
    )

internal fun loadLocalInstalledApps(context: Context): List<LocalInstalledApp> {
    val packageManager = context.packageManager
    return packageManager
        .getInstalledApplications(0)
        .asSequence()
        .mapNotNull { applicationInfo ->
            val apkPaths =
                collectInstalledApkPaths(
                    sourceDir = applicationInfo.sourceDir,
                    splitSourceDirs = applicationInfo.splitSourceDirs,
                )
            if (apkPaths.isEmpty()) {
                return@mapNotNull null
            }
            LocalInstalledApp(
                label = applicationInfo.loadLabel(packageManager).toString().ifBlank { applicationInfo.packageName },
                packageName = applicationInfo.packageName,
                apkPaths = apkPaths,
                isSystemApp = applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            )
        }.sortedWith(
            compareBy<LocalInstalledApp> { it.label.lowercase(Locale.getDefault()) }
                .thenBy { it.packageName },
        ).toList()
}

internal fun collectInstalledApkPaths(
    sourceDir: String?,
    splitSourceDirs: Array<String>?,
    isFile: (String) -> Boolean = { path -> File(path).isFile },
): List<String> =
    (listOfNotNull(sourceDir) + splitSourceDirs.orEmpty())
        .distinct()
        .filter(isFile)

private fun sanitizeAppIconFileName(packageName: String): String = packageName.replace(':', '_')

internal fun ensureLocalDadbHelperJar(context: Context): File {
    val helperDir = File(context.filesDir, "dadb-helpers").apply { mkdirs() }
    val helperFile = File(helperDir, DADB_HELPER_ASSET_NAME)
    context.assets.open(DADB_HELPER_ASSET_NAME).use { input ->
        helperFile.outputStream().use { output -> input.copyTo(output) }
    }
    return helperFile
}

private class AppIconHelperGateway(
    private val connection: AdbConnection,
    private val helperJar: File,
) {
    suspend fun loadIconBatch(packageNames: List<String>) =
        connection
            .loadAppIconBatchWithHelper(
                packageNames = packageNames,
                localHelperJar = helperJar,
            ).getOrThrow()

    suspend fun runProbe(
        command: String,
        args: List<String>,
    ) = connection.runAppHelperProbe(command, args, helperJar)

    companion object {
        fun current(helperJar: File): AppIconHelperGateway? =
            SessionManagementAdbConnection
                .current()
                ?.let { connection -> AppIconHelperGateway(connection, helperJar) }

        fun current(context: Context): AppIconHelperGateway? =
            current(ensureLocalDadbHelperJar(context))
    }
}

private suspend fun captureAppHelperDiagnostics(
    context: Context,
    packageName: String,
) {
    val helperGateway = AppIconHelperGateway.current(context) ?: return
    val probes =
        listOf(
            "ping" to emptyList(),
            "runtime" to emptyList(),
            "context" to emptyList(),
            "pm" to emptyList(),
            "apps" to emptyList(),
            "iconprobe" to listOf(packageName),
        )

    probes.forEach { (command, args) ->
        val result = helperGateway.runProbe(command, args)
        result.fold(
            onSuccess = { probe ->
                com.screen.remote.android.core.common.manager.LogManager.e(
                    com.screen.remote.android.core.common.LogTags.ADB_CONNECTION,
                    "helper probe ${probe.command} exit=${probe.exitCode} stdout=${
                        probe.stdout.ifBlank {
                            "<empty>"
                        }
                    } stderr=${probe.stderr.ifBlank { "<empty>" }}",
                )
            },
            onFailure = { error ->
                com.screen.remote.android.core.common.manager.LogManager.e(
                    com.screen.remote.android.core.common.LogTags.ADB_CONNECTION,
                    "Helper probe $command failed to execute: ${error.message}",
                    error,
                )
            },
        )
    }
}

internal fun sha256(bytes: ByteArray): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

internal suspend fun loadAppDetailSnapshot(
    context: Context,
    entry: AppInventoryEntry,
): AppDetailSnapshot {
    val loadMutex = SessionManagementAppCache.detailLoadMutex(entry.packageName)
    return loadMutex.withLock {
        val cachedDetail = SessionManagementAppCache.cachedAppDetail(entry.packageName)
        cachedDetail?.takeIf { it.fieldErrors.isEmpty() }?.let { cached ->
            return@withLock cached
        }

        runCatching {
            val app =
                queryAppDataWithHelper(
                    context = context,
                    includeUser = true,
                    includeSystem = true,
                    includeEnabled = true,
                    includeDisabled = true,
                    fields = setOf("list", "details"),
                    packageNames = setOf(entry.packageName),
                ).firstOrNull()
                    ?: error("Helper returned no app data for ${entry.packageName}")
            remoteAppDataToDetailSnapshot(
                app = app,
                fallback = cachedDetail ?: AppDetailSnapshot.loading(entry).copy(isLoading = false),
            ).also(SessionManagementAppCache::updateAppDetail)
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            AppDetailSnapshot.loading(entry).copy(
                isLoading = false,
                errorMessage = error.message ?: ManagementTexts.Apps.APP_DETAILS_LOAD_FAILED.get(),
            )
        }
    }
}

internal fun parseAppDetailFields(text: String): Map<String, String> {
    val supportedKeys = setOf("versionName", "minSdk", "targetSdk", "firstInstallTime", "lastUpdateTime")
    return Regex("""(?:^|\s)([A-Za-z]+)=([^\s]+)""", setOf(RegexOption.MULTILINE))
        .findAll(text)
        .map { match -> match.groupValues[1] to match.groupValues[2] }
        .filter { (key, _) -> key in supportedKeys }
        .toMap()
}

internal fun formatAppSize(bytes: Long): String {
    val mb = bytes / 1024.0 / 1024.0
    if (mb < 1024.0) {
        return String.format(Locale.US, "%.2f M", mb)
    }
    return String.format(Locale.US, "%.2f G", mb / 1024.0)
}

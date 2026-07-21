package com.screen.remote.android.feature.session.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.screen.remote.android.core.i18n.ManagementTexts
import com.screen.remote.android.core.common.util.ApiCompatHelper
import com.screen.remote.android.core.common.util.compat.putIfAbsentCompat
import com.screen.remote.android.infrastructure.adb.connection.AdbConnection
import dadb.helper.RemoteAppIconBatchRequest
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
import java.util.Locale

internal data class AppInventoryEntry(
    val packageName: String,
    val appTitle: String,
    val isSystemApp: Boolean,
    val apkPath: String,
    val isEnabled: Boolean,
    val versionCode: Long = 0L,
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

@Serializable
private data class AppIconIndexSnapshot(
    val hashes: Map<String, String> = emptyMap(),
    val titles: Map<String, String> = emptyMap(),
)

internal fun resolveAppListTitle(
    entry: AppInventoryEntry,
    packageNameOnlyMode: Boolean,
): String {
    if (packageNameOnlyMode) {
        return entry.packageName
    }
    val cachedTitle = SessionManagementAppCache.cachedAppTitle(entry.packageName)
    return when {
        !cachedTitle.isNullOrBlank() -> cachedTitle
        else -> entry.appTitle
    }
}

internal object SessionManagementAppCache {
    private var activeScopeKey: String? = null
    private var activeStorageScopeName: String? = null
    private var scopePrepared = false
    private var snapshot: AppInventorySnapshot? = null
    private val detailCache = mutableMapOf<String, AppDetailSnapshot>()
    private val detailLoadMutexes = mutableMapOf<String, Mutex>()
    private val iconCache = mutableMapOf<String, Bitmap?>()
    private val iconGenerationCache = mutableMapOf<String, Int>()
    private val iconHashCache = mutableMapOf<String, String>()
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
            activeStorageScopeName = "session-${sha256(scopeKey.toByteArray(Charsets.UTF_8)).take(16)}"
            scopePrepared = false
            snapshot = null
            detailCache.clear()
            detailLoadMutexes.clear()
            iconCache.clear()
            iconGenerationCache.clear()
            iconHashCache.clear()
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
            activeStorageScopeName = null
            scopePrepared = false
            snapshot = null
            detailCache.clear()
            detailLoadMutexes.clear()
            iconCache.clear()
            iconGenerationCache.clear()
            iconHashCache.clear()
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
                activeScopeKey == scopeKey && !scopePrepared
            }
        if (!shouldLoad) return

        val indexSnapshot = withContext(Dispatchers.IO) { readIconIndex(context) }
        synchronized(this) {
            if (activeScopeKey == scopeKey && !scopePrepared) {
                indexSnapshot?.let { snapshot ->
                    iconHashCache.putAll(snapshot.hashes)
                    titleCache.putAll(snapshot.titles)
                }
                scopePrepared = true
            }
        }
    }

    fun storageScopeName(): String =
        synchronized(this) {
            checkNotNull(activeStorageScopeName) { "Session management cache scope is not selected" }
        }

    fun snapshot(): AppInventorySnapshot? =
        synchronized(this) {
            snapshot
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

    fun updateSnapshot(value: AppInventorySnapshot) {
        synchronized(this) {
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

    fun appTitle(
        packageName: String,
        fallback: String,
    ): String =
        synchronized(this) {
            titleCache[packageName] ?: fallback
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

    fun hasIcon(packageName: String): Boolean =
        synchronized(this) {
            iconCache.containsKey(packageName)
        }

    fun updateIcon(
        packageName: String,
        icon: Bitmap?,
        generation: Int = 0,
    ) {
        synchronized(this) {
            iconCache[packageName] = icon
            iconGenerationCache[packageName] = generation
            bumpRevision()
        }
    }

    fun iconGeneration(packageName: String): Int? =
        synchronized(this) {
            iconGenerationCache[packageName]
        }

    fun clearIcons() {
        synchronized(this) {
            iconCache.clear()
            iconGenerationCache.clear()
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

    fun cachedIconHash(packageName: String): String? =
        synchronized(this) {
            iconHashCache[packageName]
        }

    fun updateIconHash(
        context: Context,
        packageName: String,
        hash: String,
    ) {
        synchronized(this) {
            iconHashCache[packageName] = hash
            writeIconIndex(context)
        }
    }

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
                    hashes = iconHashCache.toSortedMap(),
                    titles = titleCache.toSortedMap(),
                ),
            ),
        )
    }

    fun updateIconMetadataBatch(
        hashes: Map<String, String>,
        titles: Map<String, String> = emptyMap(),
        persist: Boolean = true,
        context: Context? = null,
    ) {
        synchronized(this) {
            var changed = false
            hashes.forEach { (packageName, hash) ->
                if (iconHashCache[packageName] != hash) {
                    iconHashCache[packageName] = hash
                    changed = true
                }
            }
            titles.forEach { (packageName, title) ->
                if (title.isNotBlank() && titleCache[packageName] != title) {
                    titleCache[packageName] = title
                    changed = true
                }
            }
            if (changed && persist) {
                requireNotNull(context) { "context is required when persist=true" }
                writeIconIndex(context)
            }
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
    forceRefresh: Boolean = false,
): AppInventorySnapshot {
    if (!forceRefresh) {
        SessionManagementAppCache.snapshot()?.takeIf { it.errorMessage == null }?.let { cached ->
            return if (includeSystemApps) {
                cached
            } else {
                cached.copy(
                    apps = cached.apps.filterNot { it.isSystemApp },
                    shizukuInstalled = cached.apps.any { it.packageName == "moe.shizuku.privileged.api" },
                )
            }
        }
    }

    val connection = SessionManagementAdbConnection.current()
    if (connection == null) {
        return AppInventorySnapshot.loading().copy(
            isLoading = false,
            errorMessage = ManagementTexts.Apps.NO_CONNECTION_FOR_APP_LIST.get(),
        )
    }

    return runCatching {
        loadAppInventorySnapshotWithShell(
            connection = connection,
            includeSystemApps = includeSystemApps,
        )
    }.getOrElse { error ->
        if (error is CancellationException) throw error
        AppInventorySnapshot.loading().copy(
            isLoading = false,
            errorMessage = error.message ?: ManagementTexts.Apps.APP_LIST_LOAD_FAILED.get(),
        )
    }
}

internal suspend fun loadAppApkSizes(entries: List<AppInventoryEntry>): Map<String, Long> {
    val connection = SessionManagementAdbConnection.current() ?: return emptyMap()
    val chunks =
        entries
            .asSequence()
            .filter { it.apkPath.isNotBlank() }
            .distinctBy { it.packageName }
            .toList()
            .chunked(80)
    val outputs = mutableListOf<String>()
    for (chunk in chunks) {
        val command =
            chunk.joinToString(separator = "; ") { entry ->
                "size=\$(stat -c %s ${quoteShellArg(entry.apkPath)} 2>/dev/null); " +
                    "printf '%s\\t%s\\n' ${quoteShellArg(entry.packageName)} \"\${size:-0}\""
            }
        connection.executeShell(command, retryOnFailure = false).getOrNull()?.let(outputs::add)
    }
    val output = outputs.joinToString(separator = "\n")

    return withContext(Dispatchers.Default) {
        output
            .lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('\t')
                if (separator <= 0) return@mapNotNull null
                val packageName = line.substring(0, separator).trim()
                val size = line.substring(separator + 1).trim().toLongOrNull()
                if (packageName.isBlank() || size == null || size < 0L) null else packageName to size
            }.toMap()
    }
}

private val sessionManagementAppPipelineMutex = Mutex()

/**
 * The single entry point for remote application data. It deliberately publishes useful data in
 * stages: user applications first, then the complete inventory and sizes, followed by details and
 * presentations. Callers only observe [SessionManagementAppCache]; filtering never performs ADB IO.
 */
internal suspend fun loadSessionManagementAppData(
    context: Context,
    scopeKey: String,
    forceRefresh: Boolean = false,
) {
    sessionManagementAppPipelineMutex.withLock {
        SessionManagementAppCache.prepareForSession(context, scopeKey)
        if (!forceRefresh && SessionManagementAppCache.isPipelineComplete(scopeKey)) return@withLock
        if (forceRefresh) SessionManagementAppCache.clearSnapshot()

        val userSnapshot =
            loadAppInventorySnapshot(
                context = context,
                includeSystemApps = false,
                forceRefresh = forceRefresh,
            )
        if (userSnapshot.errorMessage != null) {
            SessionManagementAppCache.updateSnapshot(userSnapshot.copy(isLoading = false))
            return@withLock
        }
        SessionManagementAppCache.updateSnapshot(userSnapshot.copy(isLoading = false))
        if (!SessionManagementAppCache.isActiveScope(scopeKey)) return@withLock

        val fullResult =
            runCatching {
                loadAppInventorySnapshot(
                    context = context,
                    includeSystemApps = true,
                    forceRefresh = true,
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
            }
        val fullSnapshot = fullResult.getOrNull()?.takeIf { it.errorMessage == null }
        var apps = fullSnapshot?.apps ?: userSnapshot.apps
        if (!SessionManagementAppCache.isActiveScope(scopeKey)) return@withLock

        val sizes =
            runCatching { loadAppApkSizes(apps.filter { it.apkSizeBytes == null }) }
                .onFailure { error -> if (error is CancellationException) throw error }
                .getOrDefault(emptyMap())
        if (sizes.isNotEmpty()) {
            apps =
                apps.map { entry ->
                    sizes[entry.packageName]?.let { entry.copy(apkSizeBytes = it) } ?: entry
                }
        }
        SessionManagementAppCache.updateSnapshot(
            AppInventorySnapshot(
                isLoading = false,
                apps = apps,
                shizukuInstalled = apps.any { it.packageName == "moe.shizuku.privileged.api" },
                errorMessage = if (apps.isEmpty()) fullResult.exceptionOrNull()?.message else null,
            ),
        )

        coroutineScope {
            val detailsJob =
                async {
                    apps.chunked(6).forEach { chunk ->
                        chunk.map { entry -> async { loadAppDetailSnapshot(entry) } }.awaitAll()
                    }
                }
            val presentationsJob =
                async {
                    runCatching { warmCachedAppPresentations(context, apps, packageNameOnlyMode = false) }
                        .onFailure { error -> if (error is CancellationException) throw error }
                    val helperJar = withContext(Dispatchers.IO) { ensureLocalDadbHelperJar(context) }
                    val connection = SessionManagementAdbConnection.current() ?: return@async
                    if (connection.prepareAppIconHelper(helperJar).isSuccess) {
                        runCatching { prefetchAppIconsWithHelper(context, apps, helperJar) }
                            .onFailure { error -> if (error is CancellationException) throw error }
                    }
                }
            detailsJob.await()
            presentationsJob.await()
        }
        if (fullSnapshot != null) {
            SessionManagementAppCache.markPipelineComplete(scopeKey)
        }
    }
}

private suspend fun loadAppInventorySnapshotWithShell(
    connection: com.screen.remote.android.infrastructure.adb.connection.AdbConnection,
    includeSystemApps: Boolean,
): AppInventorySnapshot =
    runCatching {
        if (!includeSystemApps) {
            val output =
                connection
                    .executeShell("pm list packages -3 -e -f", retryOnFailure = false)
                    .getOrThrow()

            val apps =
                withContext(Dispatchers.Default) {
                    output
                        .lineSequence()
                        .mapNotNull { parseAppInventoryLine(it, emptySet(), emptySet()) }
                        .sortedWith(
                            compareBy<AppInventoryEntry> { it.packageName.lowercase(Locale.getDefault()) },
                        ).toList()
                }

            val snapshot =
                AppInventorySnapshot(
                    isLoading = false,
                    apps = apps,
                    shizukuInstalled = apps.any { it.packageName == "moe.shizuku.privileged.api" },
                )
            return@runCatching snapshot
        }

        val output =
            connection
                .executeShell("pm list packages -f", retryOnFailure = false)
                .getOrThrow()
        val userPackagesOutput =
            connection
                .executeShell("pm list packages -3 -f", retryOnFailure = false)
                .getOrNull()
                .orEmpty()
        val userPackages =
            withContext(Dispatchers.Default) {
                userPackagesOutput
                    .lineSequence()
                    .mapNotNull { parseAppInventoryLine(it, emptySet(), emptySet()) }
                    .map { it.packageName }
                    .toSet()
            }

        val disabledOutput =
            connection
                .executeShell("pm list packages -d", retryOnFailure = false)
                .getOrNull()
                .orEmpty()
        val disabledPackages =
            withContext(Dispatchers.Default) {
                disabledOutput
                    .lineSequence()
                    .map { it.trim().removePrefix("package:").trim() }
                    .filter { it.isNotBlank() }
                    .toSet()
            }

        val apps =
            withContext(Dispatchers.Default) {
                output
                    .lineSequence()
                    .mapNotNull { parseAppInventoryLine(it, disabledPackages, userPackages) }
                    .sortedWith(
                        compareBy<AppInventoryEntry> { it.packageName.lowercase(Locale.getDefault()) },
                    ).toList()
            }

        val fullSnapshot =
            AppInventorySnapshot(
                isLoading = false,
                apps = apps,
                shizukuInstalled = apps.any { it.packageName == "moe.shizuku.privileged.api" },
            )
        if (includeSystemApps) {
            fullSnapshot
        } else {
            fullSnapshot.copy(
                apps = fullSnapshot.apps.filterNot { it.isSystemApp },
                shizukuInstalled = fullSnapshot.shizukuInstalled,
            )
        }
    }.getOrThrow()

private fun parseAppInventoryLine(
    line: String,
    disabledPackages: Set<String> = emptySet(),
    userPackages: Set<String> = emptySet(),
): AppInventoryEntry? {
    val trimmed = line.trim()
    if (!trimmed.startsWith("package:")) return null

    val body = trimmed.removePrefix("package:")
    val separatorIndex = body.lastIndexOf('=')
    if (separatorIndex <= 0 || separatorIndex >= body.lastIndex) {
        return null
    }

    val apkPath = body.substring(0, separatorIndex).trim()
    val packageName = body.substring(separatorIndex + 1).trim()
    if (packageName.isBlank()) return null

    return AppInventoryEntry(
        packageName = packageName,
        appTitle = guessAppTitle(packageName),
        isSystemApp =
            if (userPackages.isNotEmpty()) {
                packageName !in userPackages
            } else {
                isSystemApkPath(apkPath)
            },
        apkPath = apkPath,
        isEnabled = packageName !in disabledPackages,
    )
}

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

private fun isSystemApkPath(apkPath: String): Boolean =
    apkPath.startsWith("/system/") ||
        apkPath.startsWith("/product/") ||
        apkPath.startsWith("/vendor/") ||
        apkPath.startsWith("/apex/")

internal data class RemoteAppPresentation(
    val title: String,
    val icon: Bitmap?,
)

private const val DADB_HELPER_ASSET_NAME = "dadb-device-helper.jar"
private const val APP_ICON_HELPER_BATCH_SIZE = 50
private const val APP_ICON_HELPER_CONCURRENCY = 3

private data class AppIconChunkResult(
    val changedCount: Int,
    val updatedHashes: Map<String, String>,
    val updatedTitles: Map<String, String>,
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
        if (cachedIcon != null) {
            return@withContext RemoteAppPresentation(
                title = resolvedTitle,
                icon = cachedIcon,
            )
        }

        val iconFile = getAppIconFile(context, entry.packageName)
        val bitmap =
            if (iconFile.exists()) {
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
            .filterNot { SessionManagementAppCache.hasIcon(it.packageName) }
            .mapNotNull { entry ->
                val iconFile = getAppIconFile(context, entry.packageName)
                if (!iconFile.exists()) {
                    return@mapNotNull null
                }
                val bitmap = runCatching { BitmapFactory.decodeFile(iconFile.absolutePath) }.getOrNull() ?: return@mapNotNull null
                SessionManagementAppCache.updateIcon(entry.packageName, bitmap)
                entry.packageName
            }
    }

internal suspend fun prefetchAppIconsWithHelper(
    context: Context,
    entries: List<AppInventoryEntry>,
    helperJar: File,
    onChunkApplied: suspend (List<String>) -> Unit = {},
): Int =
    withContext(Dispatchers.IO) {
        val helperGateway = AppIconHelperGateway.current(helperJar) ?: return@withContext 0
        val allUpdatedHashes = linkedMapOf<String, String>()
        val allUpdatedTitles = linkedMapOf<String, String>()
        entries
            .chunked(APP_ICON_HELPER_BATCH_SIZE)
            .chunked(APP_ICON_HELPER_CONCURRENCY)
            .sumOf { wave ->
                coroutineScope {
                    wave
                        .map { chunk ->
                            async {
                                prefetchAppIconChunkWithHelper(
                                    context = context,
                                    helperGateway = helperGateway,
                                    chunk = chunk,
                                )
                            }
                        }.awaitAll()
                        .sumOf { result ->
                            if (result.updatedHashes.isNotEmpty() || result.updatedTitles.isNotEmpty()) {
                                SessionManagementAppCache.updateIconMetadataBatch(
                                    hashes = result.updatedHashes,
                                    titles = result.updatedTitles,
                                    persist = false,
                                )
                                allUpdatedHashes.putAll(result.updatedHashes)
                                allUpdatedTitles.putAll(result.updatedTitles)
                                withContext(Dispatchers.Main) {
                                    onChunkApplied(result.updatedPackages)
                                }
                            }
                            result.changedCount
                        }
                }
            }.also {
                if (allUpdatedHashes.isNotEmpty() || allUpdatedTitles.isNotEmpty()) {
                    SessionManagementAppCache.persistIconMetadata(context)
                }
            }
    }

private suspend fun prefetchAppIconChunkWithHelper(
    context: Context,
    helperGateway: AppIconHelperGateway,
    chunk: List<AppInventoryEntry>,
): AppIconChunkResult {
    val requests =
        chunk.map { entry ->
            val iconFile = getAppIconFile(context, entry.packageName)
            RemoteAppIconBatchRequest(
                packageName = entry.packageName,
                localHash =
                    SessionManagementAppCache
                        .cachedIconHash(entry.packageName)
                        ?.takeIf { iconFile.exists() },
            )
        }

    val result = helperGateway.loadIconBatch(requests)

    val updatedHashes = linkedMapOf<String, String>()
    val updatedTitles = linkedMapOf<String, String>()
    val updatedPackages = mutableListOf<String>()
    var changedCount = 0

    result.entries.forEach { changedIcon ->
        updatedHashes[changedIcon.packageName] = changedIcon.iconHash
        updatedTitles[changedIcon.packageName] = changedIcon.label
        updatedPackages += changedIcon.packageName

        val imageBytes = changedIcon.imageBytes
        if (imageBytes != null) {
            val iconFile = getAppIconFile(context, changedIcon.packageName)
            iconFile.parentFile?.mkdirs()
            iconFile.writeBytes(imageBytes)
            val bitmap =
                BitmapFactory.decodeByteArray(
                    imageBytes,
                    0,
                    imageBytes.size,
                )
            SessionManagementAppCache.updateIcon(changedIcon.packageName, bitmap)
            changedCount += 1
        } else {
            SessionManagementAppCache.updateIcon(
                changedIcon.packageName,
                SessionManagementAppCache.cachedIcon(changedIcon.packageName),
            )
        }
    }

    return AppIconChunkResult(
        changedCount = changedCount,
        updatedHashes = updatedHashes,
        updatedTitles = updatedTitles,
        updatedPackages = updatedPackages,
    )
}

private fun getAppIconFile(
    context: Context,
    packageName: String,
): File {
    val iconDir =
        File(
            File(
                context.filesDir,
                com.screen.remote.android.core.common.constants.FilePathConstants.APP_ICONS_DIR,
            ),
            SessionManagementAppCache.storageScopeName(),
        )
    if (!iconDir.exists()) {
        iconDir.mkdirs()
    }
    return File(iconDir, "${sanitizeAppIconFileName(packageName)}.webp")
}

private fun getIconIndexFile(context: Context): File =
    File(
        File(
            File(context.filesDir, com.screen.remote.android.core.common.constants.FilePathConstants.APP_ICONS_DIR),
            SessionManagementAppCache.storageScopeName(),
        ),
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
    suspend fun loadIconBatch(requests: List<RemoteAppIconBatchRequest>) =
        connection
            .loadAppIconBatchWithHelper(
                requests = requests,
                localHelperJar = helperJar,
            ).getOrThrow()

    suspend fun loadIcon(
        packageName: String,
        localHash: String?,
    ) = connection
        .loadAppIconWithHelper(
            packageName = packageName,
            localHash = localHash,
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

private suspend fun fetchAndSaveAppPresentationWithHelper(
    context: Context,
    entry: AppInventoryEntry,
    localHash: String?,
): RemoteAppPresentation? {
    return runCatching {
        val helperGateway = AppIconHelperGateway.current(context) ?: return null
        val helperResult = helperGateway.loadIcon(entry.packageName, localHash)

        val resolvedTitle =
            helperResult.label
                .takeIf { it.isNotBlank() }
                ?: SessionManagementAppCache.appTitle(entry.packageName, entry.appTitle)

        val iconFile = getAppIconFile(context, entry.packageName)
        val bitmap =
            if (helperResult.changed) {
                val bytes = helperResult.imageBytes ?: error("Helper returned changed icon without bytes")
                iconFile.parentFile?.mkdirs()
                iconFile.writeBytes(bytes)
                SessionManagementAppCache.updateIconHash(context, entry.packageName, helperResult.iconHash)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else {
                if (!iconFile.exists()) {
                    null
                } else {
                    SessionManagementAppCache.updateIconHash(context, entry.packageName, helperResult.iconHash)
                    BitmapFactory.decodeFile(iconFile.absolutePath)
                }
            }

        RemoteAppPresentation(
            title = resolvedTitle,
            icon = bitmap,
        )
    }.getOrElse { error ->
        if (SessionManagementAppCache.shouldCaptureIconHelperDiagnostics()) {
            runCatching {
                captureAppHelperDiagnostics(
                    context = context,
                    packageName = entry.packageName,
                )
            }
            SessionManagementAppCache.markIconHelperDiagnosticsCaptured()
        }
        if (error.message?.contains("RuntimeInit", ignoreCase = true) == true ||
            error.message?.contains("Killed", ignoreCase = true) == true
        ) {
            SessionManagementAppCache.markIconHelperUnavailable(error.message ?: "icon helper unavailable")
        }
        runCatching {
            com.screen.remote.android.core.common.manager.LogManager.w(
                com.screen.remote.android.core.common.LogTags.ADB_CONNECTION,
                "Helper failed to obtain application icon ${entry.packageName}: ${error.message}",
            )
        }
        null
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

internal suspend fun loadAppDetailSnapshot(entry: AppInventoryEntry): AppDetailSnapshot {
    val loadMutex = SessionManagementAppCache.detailLoadMutex(entry.packageName)
    return loadMutex.withLock {
        SessionManagementAppCache.cachedAppDetail(entry.packageName)?.let { cached ->
            return@withLock cached
        }

        val connection =
            SessionManagementAdbConnection.current()
                ?: return@withLock AppDetailSnapshot.loading(entry).copy(
                    isLoading = false,
                    errorMessage = ManagementTexts.Apps.NO_CONNECTION_FOR_APP_DETAILS.get(),
                )

        suspend fun shell(command: String): String =
            connection
                .executeShell(command, retryOnFailure = false)
                .getOrNull()
                ?.trim()
                .orEmpty()

        runCatching {
            coroutineScope {
                val pathDeferred = async { shell("pm path ${entry.packageName} | head -n 1") }
                val detailDeferred =
                    async {
                        shell(
                            "dumpsys package ${entry.packageName} | grep -E 'versionName=|minSdk=|targetSdk=|firstInstallTime=|lastUpdateTime='",
                        )
                    }

                val apkPath = pathDeferred.await().removePrefix("package:").trim()
                val detailMap = parseAppDetailFields(detailDeferred.await())
                val apkSize =
                    if (apkPath.isNotBlank()) {
                        val sizeBytes =
                            entry.apkSizeBytes
                                ?: shell("stat -c %s \"$apkPath\" 2>/dev/null").toLongOrNull()
                        sizeBytes?.let(::formatAppSize).orEmpty()
                    } else {
                        ""
                    }

                AppDetailSnapshot(
                    isLoading = false,
                    appTitle = SessionManagementAppCache.appTitle(entry.packageName, entry.appTitle),
                    packageName = entry.packageName,
                    apkSize = apkSize,
                    versionName = detailMap["versionName"].orEmpty(),
                    isSystemApp = entry.isSystemApp,
                    minSdk = detailMap["minSdk"].orEmpty(),
                    targetSdk = detailMap["targetSdk"].orEmpty(),
                    firstInstallTime = detailMap["firstInstallTime"].orEmpty(),
                    lastUpdateTime = detailMap["lastUpdateTime"].orEmpty(),
                ).also(SessionManagementAppCache::updateAppDetail)
            }
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

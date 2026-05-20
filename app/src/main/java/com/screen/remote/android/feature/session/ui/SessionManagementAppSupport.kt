package com.screen.remote.android.feature.session.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.screen.remote.android.core.i18n.ManagementTexts
import com.screen.remote.android.core.common.util.ApiCompatHelper
import com.screen.remote.android.core.common.util.compat.putIfAbsentCompat
import com.screen.remote.android.infrastructure.adb.connection.AdbBridge
import dadb.helper.RemoteAppIconBatchRequest
import dadb.helper.RemoteAppListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
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
    val labelZh: String,
    val labelEn: String,
) {
    ShowSystemApps("显示系统应用", "Show system apps"),
    ShowUserApps("显示第三方应用", "Show user apps"),
    ShowEnabledApps("显示启用应用", "Show enabled apps"),
    ShowDisabledApps("显示禁用应用", "Show disabled apps"),
    ;

    val label: String
        get() = ManagementTexts.text(labelZh, labelEn)

    companion object {
        val defaultSelection: Set<AppListFilter> =
            setOf(
                ShowUserApps,
                ShowEnabledApps,
            )
    }
}

internal enum class AppListSort(
    val labelZh: String,
    val labelEn: String,
) {
    Title("按应用名排序", "Sort by app name"),
    Package("按包名排序", "Sort by package"),
    EnabledState("按启用状态排序", "Sort by enabled state"),
}

internal val AppListSort.label: String
    get() = ManagementTexts.text(labelZh, labelEn)

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
    private var processPrepared = false
    private var snapshot: AppInventorySnapshot? = null
    private val detailCache = mutableMapOf<String, AppDetailSnapshot>()
    private val iconCache = mutableMapOf<String, Bitmap?>()
    private val iconGenerationCache = mutableMapOf<String, Int>()
    private val iconHashCache = mutableMapOf<String, String>()
    private val titleCache = mutableMapOf<String, String>()
    private var iconHelperUnavailableReason: String? = null
    private var iconHelperDiagnosticsCaptured = false
    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = false
        }

    fun prepareForProcess(context: Context) {
        synchronized(this) {
            if (processPrepared) return
            processPrepared = true
            clearSnapshot()
            detailCache.clear()
            titleCache.clear()
            iconHelperUnavailableReason = null
            iconHelperDiagnosticsCaptured = false
            loadIconIndex(context)
        }
    }

    fun snapshot(): AppInventorySnapshot? =
        synchronized(this) {
            snapshot
        }

    fun updateSnapshot(value: AppInventorySnapshot) {
        synchronized(this) {
            snapshot = value
            value.apps.forEach { entry ->
                titleCache.putIfAbsentCompat(entry.packageName, entry.appTitle)
            }
        }
    }

    fun clearSnapshot() {
        synchronized(this) {
            snapshot = null
        }
    }

    fun cachedAppDetail(packageName: String): AppDetailSnapshot? =
        synchronized(this) {
            detailCache[packageName]
        }

    fun updateAppDetail(snapshot: AppDetailSnapshot) {
        synchronized(this) {
            detailCache[snapshot.packageName] = snapshot
            if (snapshot.appTitle.isNotBlank()) {
                titleCache[snapshot.packageName] = snapshot.appTitle
            }
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
            }
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

    private fun loadIconIndex(context: Context) {
        val file = getIconIndexFile(context)
        if (!file.exists()) return
        val snapshot =
            runCatching {
                json.decodeFromString<AppIconIndexSnapshot>(file.readText())
            }.getOrNull() ?: return
        iconHashCache.clear()
        iconHashCache.putAll(snapshot.hashes)
        titleCache.putAll(snapshot.titles)
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
        SessionManagementAppCache.snapshot()?.let { cached ->
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

    val connection = AdbBridge.getConnection()
    if (connection == null) {
        return AppInventorySnapshot.loading().copy(
            isLoading = false,
            errorMessage = "当前没有可用的 ADB 连接，无法读取应用列表。",
        )
    }

    return runCatching {
        loadAppInventorySnapshotWithShell(
            connection = connection,
            includeSystemApps = includeSystemApps,
        )
    }.getOrElse { error ->
        AppInventorySnapshot.loading().copy(
            isLoading = false,
            errorMessage = error.message ?: "读取应用列表失败。",
        )
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
                output
                    .lineSequence()
                    .mapNotNull { parseAppInventoryLine(it, emptySet(), emptySet()) }
                    .sortedWith(
                        compareBy<AppInventoryEntry> { it.packageName.lowercase(Locale.getDefault()) },
                    ).toList()

            val snapshot =
                AppInventorySnapshot(
                    isLoading = false,
                    apps = apps,
                    shizukuInstalled = apps.any { it.packageName == "moe.shizuku.privileged.api" },
                )
            SessionManagementAppCache.updateSnapshot(snapshot)
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
            userPackagesOutput
                .lineSequence()
                .mapNotNull { parseAppInventoryLine(it, emptySet(), emptySet()) }
                .map { it.packageName }
                .toSet()

        val disabledOutput =
            connection
                .executeShell("pm list packages -d", retryOnFailure = false)
                .getOrNull()
                .orEmpty()
        val disabledPackages =
            disabledOutput
                .lineSequence()
                .map { it.trim().removePrefix("package:").trim() }
                .filter { it.isNotBlank() }
                .toSet()

        val apps =
            output
                .lineSequence()
                .mapNotNull { parseAppInventoryLine(it, disabledPackages, userPackages) }
                .sortedWith(
                    compareBy<AppInventoryEntry> { it.packageName.lowercase(Locale.getDefault()) },
                ).toList()

        val fullSnapshot =
            AppInventorySnapshot(
                isLoading = false,
                apps = apps,
                shizukuInstalled = apps.any { it.packageName == "moe.shizuku.privileged.api" },
            )
        SessionManagementAppCache.updateSnapshot(fullSnapshot)

        if (includeSystemApps) {
            fullSnapshot
        } else {
            fullSnapshot.copy(
                apps = fullSnapshot.apps.filterNot { it.isSystemApp },
                shizukuInstalled = fullSnapshot.shizukuInstalled,
            )
        }
    }.getOrThrow()

private fun remoteAppListItemToInventoryEntry(item: RemoteAppListItem): AppInventoryEntry =
    AppInventoryEntry(
        packageName = item.packageName,
        appTitle = item.label.ifBlank { guessAppTitle(item.packageName) },
        isSystemApp = item.systemApp,
        apkPath = item.sourceDir,
        isEnabled = item.enabled,
        versionCode = item.versionCode,
        lastUpdateTime = item.lastUpdateTime,
    )

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
            "android" to "Android 系统",
            "settings" to "设置",
            "systemui" to "系统 UI",
            "vending" to "Google Play Store",
            "documentsui" to "文档",
            "packageinstaller" to "安装程序",
            "launcher" to "桌面",
            "oneuihome" to "One UI 主屏幕",
            "permissioncontroller" to "权限控制器",
            "bluetooth" to "蓝牙",
            "phone" to "电话",
            "contacts" to "联系人",
            "camera" to "相机",
            "gallery" to "相册",
            "music" to "音乐",
            "video" to "视频",
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

private const val APP_ICON_HELPER_ASSET_NAME = "dadb-icon-helper.jar"
private const val APP_LIST_HELPER_PAGE_SIZE = 200
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
                runCatching { android.graphics.BitmapFactory.decodeFile(iconFile.absolutePath) }.getOrNull()
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

internal suspend fun prefetchAppIconsWithHelper(
    context: Context,
    entries: List<AppInventoryEntry>,
    helperJar: File,
    onChunkApplied: suspend (List<String>) -> Unit = {},
): Int =
    withContext(Dispatchers.IO) {
        val connection = AdbBridge.getConnection() ?: return@withContext 0
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
                                    connection = connection,
                                    chunk = chunk,
                                    helperJar = helperJar,
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
    connection: com.screen.remote.android.infrastructure.adb.connection.AdbConnection,
    chunk: List<AppInventoryEntry>,
    helperJar: File,
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

    val result =
        connection
            .loadAppIconBatchWithHelper(
                requests = requests,
                localHelperJar = helperJar,
            ).getOrThrow()

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
                android.graphics.BitmapFactory.decodeByteArray(
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

private suspend fun loadRemoteAppPresentation(
    context: Context,
    entry: AppInventoryEntry,
    iconRefreshGeneration: Int,
): RemoteAppPresentation {
    val cachedTitle = SessionManagementAppCache.appTitle(entry.packageName, entry.appTitle)
    val cachedGeneration = SessionManagementAppCache.iconGeneration(entry.packageName)
    if (SessionManagementAppCache.hasIcon(entry.packageName) && cachedGeneration == iconRefreshGeneration) {
        return RemoteAppPresentation(
            title = cachedTitle,
            icon = SessionManagementAppCache.cachedIcon(entry.packageName),
        )
    }

    return withContext(Dispatchers.IO) {
        val iconFile = getAppIconFile(context, entry.packageName)
        val shouldRefreshFromDevice =
            iconRefreshGeneration > 0 && cachedGeneration != iconRefreshGeneration
        val localHash =
            SessionManagementAppCache
                .cachedIconHash(entry.packageName)
                ?.takeIf { iconFile.exists() }
        val helperUnavailableReason = SessionManagementAppCache.iconHelperUnavailableReason()
        val presentation =
            if ((!shouldRefreshFromDevice || helperUnavailableReason != null) && iconFile.exists()) {
                RemoteAppPresentation(
                    title = cachedTitle,
                    icon = runCatching { android.graphics.BitmapFactory.decodeFile(iconFile.absolutePath) }.getOrNull(),
                )
            } else if (helperUnavailableReason != null) {
                RemoteAppPresentation(
                    title = cachedTitle,
                    icon = null,
                )
            } else {
                fetchAndSaveAppPresentationWithHelper(context, entry, localHash)
                    ?: RemoteAppPresentation(title = cachedTitle, icon = null)
            }

        SessionManagementAppCache.updateIcon(
            packageName = entry.packageName,
            icon = presentation.icon,
            generation = iconRefreshGeneration,
        )
        SessionManagementAppCache.updateAppTitle(entry.packageName, presentation.title)

        presentation
    }
}

private fun getAppIconFile(
    context: Context,
    packageName: String,
): java.io.File {
    val iconDir =
        java.io.File(
            context.filesDir,
            com.screen.remote.android.core.common.constants.FilePathConstants.APP_ICONS_DIR,
        )
    if (!iconDir.exists()) {
        iconDir.mkdirs()
    }
    return java.io.File(iconDir, "${sanitizeAppIconFileName(packageName)}.webp")
}

private fun getIconIndexFile(context: Context): File =
    File(
        File(context.filesDir, com.screen.remote.android.core.common.constants.FilePathConstants.APP_ICONS_DIR),
        "index.json",
    )

internal suspend fun copyUriToTempApk(
    context: Context,
    uri: android.net.Uri,
): File =
    withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "session-management/install").apply { mkdirs() }
        val tempFile = File(tempDir, "picked-${System.currentTimeMillis()}.apk")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("无法读取选择的 APK 文件。")
        tempFile
    }

private fun sanitizeAppIconFileName(packageName: String): String = packageName.replace(':', '_')

internal fun ensureLocalAppIconHelperJar(context: Context): File {
    val helperDir = File(context.filesDir, "dadb-helpers").apply { mkdirs() }
    val helperFile = File(helperDir, APP_ICON_HELPER_ASSET_NAME)
    context.assets.open(APP_ICON_HELPER_ASSET_NAME).use { input ->
        helperFile.outputStream().use { output -> input.copyTo(output) }
    }
    return helperFile
}

private suspend fun fetchAndSaveAppIcon(
    context: Context,
    packageName: String,
    apkPath: String,
): Bitmap? =
    fetchAndSaveAppPresentationWithHelper(
        context = context,
        entry =
            AppInventoryEntry(
                packageName = packageName,
                appTitle = SessionManagementAppCache.appTitle(packageName, guessAppTitle(packageName)),
                isSystemApp = false,
                apkPath = apkPath,
                isEnabled = true,
            ),
        localHash = SessionManagementAppCache.cachedIconHash(packageName),
    )?.icon

private suspend fun fetchAndSaveAppPresentationWithHelper(
    context: Context,
    entry: AppInventoryEntry,
    localHash: String?,
): RemoteAppPresentation? {
    val connection = AdbBridge.getConnection() ?: return null

    return runCatching {
        val helperJar = ensureLocalAppIconHelperJar(context)
        val helperResult =
            connection
                .loadAppIconWithHelper(
                    packageName = entry.packageName,
                    localHash = localHash,
                    localHelperJar = helperJar,
                ).getOrThrow()

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
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else {
                if (!iconFile.exists()) {
                    null
                } else {
                    SessionManagementAppCache.updateIconHash(context, entry.packageName, helperResult.iconHash)
                    android.graphics.BitmapFactory.decodeFile(iconFile.absolutePath)
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
                "helper 获取应用图标失败 ${entry.packageName}: ${error.message}",
            )
        }
        null
    }
}

private suspend fun captureAppHelperDiagnostics(
    context: Context,
    packageName: String,
) {
    val connection = AdbBridge.getConnection() ?: return
    val helperJar = ensureLocalAppIconHelperJar(context)
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
        val result = connection.runAppHelperProbe(command, args, helperJar)
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
                    "helper probe $command 执行失败: ${error.message}",
                    error,
                )
            },
        )
    }
}

private fun overwriteBitmapFileIfChanged(
    iconFile: File,
    bitmap: Bitmap,
) {
    iconFile.parentFile?.mkdirs()

    val newBytes =
        ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }

    if (iconFile.exists()) {
        val oldBytes = runCatching { iconFile.readBytes() }.getOrNull()
        if (oldBytes != null && sha256(oldBytes) == sha256(newBytes)) {
            return
        }
    }

    iconFile.writeBytes(newBytes)
}

internal fun sha256(bytes: ByteArray): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

internal suspend fun loadAppDetailSnapshot(entry: AppInventoryEntry): AppDetailSnapshot {
    SessionManagementAppCache.cachedAppDetail(entry.packageName)?.let { cached ->
        return cached
    }

    val connection =
        AdbBridge.getConnection()
            ?: return AppDetailSnapshot.loading(entry).copy(
                isLoading = false,
                errorMessage = "当前没有可用的 ADB 连接，无法读取应用详情。",
            )

    suspend fun shell(command: String): String =
        connection
            .executeShell(command, retryOnFailure = false)
            .getOrNull()
            ?.trim()
            .orEmpty()

    return runCatching {
        coroutineScope {
            val pathDeferred = async { shell("pm path ${entry.packageName} | head -n 1") }
            val detailDeferred =
                async {
                    shell(
                        "dumpsys package ${entry.packageName} | grep -E 'versionName=|minSdk=|targetSdk=|firstInstallTime=|lastUpdateTime='",
                    )
                }

            val apkPath = pathDeferred.await().removePrefix("package:").trim()
            val detailMap = parseKeyValueEqualsBlock(detailDeferred.await())
            val apkSize =
                if (apkPath.isNotBlank()) {
                    shell("ls -l \"$apkPath\" | awk '{print \$5}'").toLongOrNull()?.let(::formatBytes).orEmpty()
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
        AppDetailSnapshot.loading(entry).copy(
            isLoading = false,
            errorMessage = error.message ?: "应用详情读取失败。",
        )
    }
}

private fun parseKeyValueEqualsBlock(text: String): Map<String, String> =
    text
        .lineSequence()
        .map { it.trim() }
        .mapNotNull { line ->
            val index = line.indexOf('=')
            if (index <= 0) null else line.substring(0, index).trim() to line.substring(index + 1).trim()
        }.toMap()

private fun formatBytes(bytes: Long): String {
    val gb = bytes / 1024.0 / 1024.0 / 1024.0
    return String.format(Locale.US, "%.2f G", gb)
}

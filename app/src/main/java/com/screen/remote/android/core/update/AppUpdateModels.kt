package com.screen.remote.android.core.update

import java.util.Calendar
import java.util.TimeZone

enum class UpdateChannel {
    STABLE,
    PRERELEASE,
}

data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val revision: Int = 0,
    val prerelease: String = "",
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch, AppVersion::revision)
            .takeIf { it != 0 }
            ?.let { return it }

        return when {
            prerelease.isBlank() && other.prerelease.isNotBlank() -> 1
            prerelease.isNotBlank() && other.prerelease.isBlank() -> -1
            else -> prerelease.compareTo(other.prerelease)
        }
    }
}

data class GitHubReleaseInfo(
    val tagName: String,
    val name: String,
    val htmlUrl: String,
    val prerelease: Boolean,
    val draft: Boolean,
    val assets: List<GitHubReleaseAsset> = emptyList(),
)

data class GitHubReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String?,
)

data class UpdateCheckCache(
    val checkedAtEpochMillis: Long = 0,
    val latestVersion: String? = null,
    val releaseUrl: String? = null,
    val skippedVersion: String? = null,
)

fun shouldShowAutomaticUpdate(
    release: GitHubReleaseInfo?,
    cache: UpdateCheckCache,
): Boolean = release != null && release.tagName != cache.skippedVersion

fun isAutomaticUpdateCheckDue(
    cache: UpdateCheckCache,
    nowEpochMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): Boolean {
    if (cache.checkedAtEpochMillis <= 0) return true
    val lastCheck = Calendar.getInstance(timeZone).apply { timeInMillis = cache.checkedAtEpochMillis }
    val now = Calendar.getInstance(timeZone).apply { timeInMillis = nowEpochMillis }
    return lastCheck.get(Calendar.ERA) != now.get(Calendar.ERA) ||
        lastCheck.get(Calendar.YEAR) != now.get(Calendar.YEAR) ||
        lastCheck.get(Calendar.DAY_OF_YEAR) != now.get(Calendar.DAY_OF_YEAR)
}

fun parseAppVersion(raw: String): AppVersion? {
    // 去掉开头的 v/V 前缀（GitHub tag 通常是 v1.2.3 格式）
    val normalized = raw.trim().removePrefix("v").removePrefix("V")
    val core = normalized.substringBefore('-')
    val prerelease = normalized.substringAfter('-', "")
    val parts = core.split('.')
    if (parts.isEmpty()) return null
    return AppVersion(
        major = parts.getOrNull(0)?.toIntOrNull() ?: return null,
        minor = parts.getOrNull(1)?.toIntOrNull() ?: 0,
        patch = parts.getOrNull(2)?.toIntOrNull() ?: 0,
        revision = parts.getOrNull(3)?.toIntOrNull() ?: 0,
        prerelease = prerelease,
    )
}

fun selectLatestRelease(
    releases: List<GitHubReleaseInfo>,
    currentVersion: String,
    channel: UpdateChannel,
): GitHubReleaseInfo? {
    val current = parseAppVersion(currentVersion) ?: return null
    return releases
        .asSequence()
        .filterNot { it.draft }
        .filter { channel == UpdateChannel.PRERELEASE || !it.prerelease }
        .mapNotNull { release ->
            val version = parseAppVersion(release.tagName) ?: return@mapNotNull null
            if (version > current) release to version else null
        }
        .maxWithOrNull(compareBy { it.second })
        ?.first
}

fun selectApkAsset(
    release: GitHubReleaseInfo,
    supportedAbis: List<String>,
): GitHubReleaseAsset? {
    val apkAssets = release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
    supportedAbis.forEach { abi ->
        // 支持两种格式：-$abi-（ABI在中间）和 -$abi.apk（ABI在末尾）
        apkAssets.firstOrNull {
            it.name.contains("-$abi-", ignoreCase = true) ||
                it.name.endsWith("-$abi.apk", ignoreCase = true)
        }?.let { return it }
    }
    return apkAssets.firstOrNull {
        it.name.contains("-universal-", ignoreCase = true) ||
            it.name.endsWith("-universal.apk", ignoreCase = true)
    }
}

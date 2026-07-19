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
)

data class UpdateCheckCache(
    val checkedAtEpochMillis: Long = 0,
    val latestVersion: String? = null,
    val releaseUrl: String? = null,
)

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
    val normalized = raw.trim().removePrefix("v")
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
        .maxWithOrNull(compareBy<Pair<GitHubReleaseInfo, AppVersion>> { it.second })
        ?.first
}

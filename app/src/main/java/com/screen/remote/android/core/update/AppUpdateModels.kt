package com.screen.remote.android.core.update

enum class UpdateChannel {
    STABLE,
    PRERELEASE,
}

data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: String = "",
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)
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

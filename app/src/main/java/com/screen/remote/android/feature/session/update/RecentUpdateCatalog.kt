package com.screen.remote.android.feature.session.update

import com.screen.remote.android.core.update.parseAppVersion

enum class RecentUpdateContent(
    val version: String,
) {
    RELEASE_4_4_3_8("4.4.3.8"),
}

fun latestRecentUpdateContent(currentVersion: String): RecentUpdateContent? {
    val current = parseAppVersion(currentVersion) ?: return null
    return RecentUpdateContent.entries
        .mapNotNull { content ->
            val contentVersion = parseAppVersion(content.version) ?: return@mapNotNull null
            if (contentVersion <= current) content to contentVersion else null
        }.maxByOrNull { it.second }
        ?.first
}

fun hasUnseenRecentUpdateContent(
    lastSeenVersion: String,
    currentVersion: String,
): Boolean {
    val latestContent = latestRecentUpdateContent(currentVersion) ?: return false
    val lastSeen = parseAppVersion(lastSeenVersion) ?: return true
    val latestContentVersion = parseAppVersion(latestContent.version) ?: return false
    return lastSeen < latestContentVersion
}

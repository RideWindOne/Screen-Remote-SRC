package com.screen.remote.android.core.designsystem.component

import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.core.i18n.LogTexts

private val LogLineTagRegex = Regex("""^\d{2}:\d{2}:\d{2}\s+(?:[A-Z]+/)?([A-Za-z0-9_.-]+):""")

internal fun extractLogTags(content: String): List<String> {
    return content
        .lineSequence()
        .mapNotNull(::extractTagFromLogLine)
        .distinct()
        .sorted()
        .toList()
}

internal fun filterLogContent(
    content: String,
    query: String,
    tags: Set<String>,
): String {
    var lines = content.lines()

    if (tags.isNotEmpty()) {
        lines =
            lines.filter { line ->
                extractTagFromLogLine(line) in tags
            }
    }

    if (query.isNotBlank()) {
        lines = lines.filter { line -> line.contains(query, ignoreCase = true) }
    }

    return lines.joinToString("\n")
}

internal fun buildLogDisplayContent(
    logContent: String,
    searchQuery: String,
    selectedTags: Set<String>,
): String {
    if (logContent.isEmpty()) {
        return CommonTexts.STATUS_CONNECTING.get()
    }

    val filtered = filterLogContent(logContent, searchQuery, selectedTags)
    return if (filtered.isEmpty() && (searchQuery.isNotBlank() || selectedTags.isNotEmpty())) {
        LogTexts.LOG_NO_RESULTS.get()
    } else {
        filtered
    }
}

private fun extractTagFromLogLine(line: String): String? =
    LogLineTagRegex.find(line)?.groupValues?.getOrNull(1)

package com.screen.remote.android.infrastructure.adb.connection

import com.screen.remote.android.core.common.AppConstants

enum class AdbConnectionPurpose {
    SCRCPY_SESSION,
    MANAGEMENT,
    CODEC_TEST,
    LATENCY_TEST,
}

data class AdbCandidatePreflight(
    val buildFingerprint: String,
    val displayInfo: AdbDisplayInfo?,
    val hasCompatibleScrcpyServer: Boolean?,
)

internal fun buildAdbCandidatePreflightCommand(purpose: AdbConnectionPurpose): String =
    "echo '$PREFLIGHT_FINGERPRINT_BEGIN'; " +
        if (purpose == AdbConnectionPurpose.SCRCPY_SESSION || purpose == AdbConnectionPurpose.MANAGEMENT) {
            "getprop ro.build.fingerprint; "
        } else {
            "echo; "
        } +
        "echo '$PREFLIGHT_DISPLAY_BEGIN'; " +
        if (purpose == AdbConnectionPurpose.SCRCPY_SESSION || purpose == AdbConnectionPurpose.MANAGEMENT) {
            "$READ_ADB_DISPLAY_INFO_COMMAND; "
        } else {
            "echo; "
        } +
        "echo '$PREFLIGHT_SERVER_BEGIN'; " +
        if (purpose == AdbConnectionPurpose.SCRCPY_SESSION || purpose == AdbConnectionPurpose.CODEC_TEST) {
            "if [ -s '${AppConstants.SCRCPY_SERVER_PATH}' ] && " +
                "[ \"\$(sha256sum '${AppConstants.SCRCPY_SERVER_PATH}' 2>/dev/null | cut -d' ' -f1)\" = " +
                "'${AppConstants.SCRCPY_SERVER_SHA256}' ]; then echo 1; else echo 0; fi"
        } else {
            "echo"
        }

internal fun parseAdbCandidatePreflight(output: String): AdbCandidatePreflight {
    val fingerprintSection = output.sectionBetween(PREFLIGHT_FINGERPRINT_BEGIN, PREFLIGHT_DISPLAY_BEGIN)
    val displaySection = output.sectionBetween(PREFLIGHT_DISPLAY_BEGIN, PREFLIGHT_SERVER_BEGIN)
    val serverSection = output.substringAfter(PREFLIGHT_SERVER_BEGIN, missingDelimiterValue = "").trim()
    return AdbCandidatePreflight(
        buildFingerprint = fingerprintSection.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty(),
        displayInfo = runCatching { parseAdbDisplayInfo(displaySection) }.getOrNull(),
        hasCompatibleScrcpyServer =
            serverSection.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.let { it == "1" },
    )
}

internal fun AdbCandidatePreflight.merge(newer: AdbCandidatePreflight): AdbCandidatePreflight =
    AdbCandidatePreflight(
        buildFingerprint = newer.buildFingerprint.ifBlank { buildFingerprint },
        displayInfo = newer.displayInfo ?: displayInfo,
        hasCompatibleScrcpyServer = newer.hasCompatibleScrcpyServer ?: hasCompatibleScrcpyServer,
    )

private fun String.sectionBetween(
    startMarker: String,
    endMarker: String,
): String {
    val section = substringAfter(startMarker, missingDelimiterValue = "")
    require(section.isNotEmpty()) { "ADB 预检缺少标记: $startMarker" }
    val value = section.substringBefore(endMarker, missingDelimiterValue = "")
    require(value.isNotEmpty()) { "ADB 预检缺少标记: $endMarker" }
    return value.trim()
}

private const val PREFLIGHT_FINGERPRINT_BEGIN = "__SCREEN_REMOTE_FINGERPRINT__"
private const val PREFLIGHT_DISPLAY_BEGIN = "__SCREEN_REMOTE_DISPLAY__"
private const val PREFLIGHT_SERVER_BEGIN = "__SCREEN_REMOTE_SERVER__"

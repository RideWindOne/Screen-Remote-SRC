package com.screen.remote.android.infrastructure.adb.connection

data class AdbDisplayInfo(
    val currentWidth: Int,
    val currentHeight: Int,
    val currentDensityDpi: Int?,
)

internal fun parseAdbDisplayInfo(output: String): AdbDisplayInfo {
    val currentSize = DISPLAY_SIZE_REGEX.findAll(output).lastOrNull() ?: error("Unable to read the current device resolution")
    val currentDensity = DISPLAY_DENSITY_REGEX.findAll(output).lastOrNull()
    return AdbDisplayInfo(
        currentWidth = currentSize.groupValues[1].toInt(),
        currentHeight = currentSize.groupValues[2].toInt(),
        currentDensityDpi = currentDensity?.groupValues?.get(1)?.toInt(),
    )
}

private val DISPLAY_SIZE_REGEX = Regex("""(?:Physical|Override) size:\s*(\d+)x(\d+)""", RegexOption.IGNORE_CASE)
private val DISPLAY_DENSITY_REGEX = Regex("""(?:Physical|Override) density:\s*(\d+)""", RegexOption.IGNORE_CASE)

internal const val READ_ADB_DISPLAY_INFO_COMMAND =
    "wm size | grep -E 'Physical|Override' | tail -n 1; " +
        "wm density | grep -E 'Physical|Override' | tail -n 1"

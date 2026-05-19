package com.mobile.scrcpy.android.feature.session.ui

import com.mobile.scrcpy.android.core.data.repository.SessionData
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbBridge
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.Locale

internal data class DeviceDashboardSnapshot(
    val isLoading: Boolean,
    val model: String,
    val manufacturer: String,
    val socModel: String,
    val androidVersion: String,
    val uptime: String,
    val basebandVersion: String,
    val productCodeName: String,
    val securityPatch: String,
    val serialNumber: String,
    val connectionTypeLabel: String,
    val resolution: String,
    val dpi: String,
    val ppi: String,
    val screenSize: String,
    val refreshRate: String,
    val storageSummary: String,
    val memoryTotal: String,
    val memoryAvailable: String,
    val batteryLevel: String,
    val batteryStatus: String,
    val batteryHealth: String,
    val voltage: String,
    val currentNow: String,
    val temperature: String,
    val cpuSummary: String,
    val abi: String,
    val board: String,
    val fingerprint: String,
    val mobileNetworkType: String,
    val carrierNames: String,
    val mobileBand: String,
    val mobilePci: String,
    val mobileEarfcn: String,
    val rsrp: String,
    val rsrq: String,
    val sinr: String,
    val wifiSsid: String,
    val wifiBssid: String,
    val wifiIpAddress: String,
    val wifiFrequency: String,
    val wifiLinkSpeed: String,
    val supportedRefreshRates: String,
    val batteryCycleCount: String,
    val wirelessDebugPort: Int? = null,
    val errorMessage: String? = null,
) {
    val brandModelLabel: String
        get() =
            listOf(manufacturer, model)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { model.ifBlank { "未知设备" } }

    val androidVersionLabel: String
        get() = androidVersion.ifBlank { "Android" }

    val heroSummary: String
        get() =
            when {
                errorMessage != null -> "设备已连接，但设备信息读取失败。"
                isLoading -> "设备已连接，正在同步首页参数信息。"
                else -> "当前设备为 $model，系统 $androidVersion，屏幕 $resolution，数据存储 $storageSummary。"
            }

    val currentDpiValue: String?
        get() = dpi.substringAfterLast(": ", dpi).trim().takeIf { it.isNotBlank() }

    val currentDpiLabel: String?
        get() = currentDpiValue?.let { "$it DPI" }

    val currentResolutionWidth: String?
        get() =
            resolution
                .substringAfterLast(": ", resolution)
                .substringBefore("x")
                .trim()
                .takeIf { it.isNotBlank() }

    val currentResolutionHeight: String?
        get() = resolution.substringAfterLast("x", "").trim().takeIf { it.isNotBlank() }

    companion object {
        fun loading(sessionData: SessionData): DeviceDashboardSnapshot =
            DeviceDashboardSnapshot(
                isLoading = true,
                model = sessionData.name.ifBlank { "设备" },
                manufacturer = "",
                socModel = "",
                androidVersion = "Android",
                uptime = "",
                basebandVersion = "",
                productCodeName = "",
                securityPatch = "",
                serialNumber = sessionData.getUsbSerialNumber().orEmpty(),
                connectionTypeLabel = if (sessionData.isUsbConnection()) "USB / OTG" else "WiFi / TCP",
                resolution = "",
                dpi = "",
                ppi = "",
                screenSize = "",
                refreshRate = "",
                storageSummary = "",
                memoryTotal = "",
                memoryAvailable = "",
                batteryLevel = "",
                batteryStatus = "",
                batteryHealth = "",
                voltage = "",
                currentNow = "",
                temperature = "",
                cpuSummary = "",
                abi = "",
                board = "",
                fingerprint = "",
                mobileNetworkType = "",
                carrierNames = "",
                mobileBand = "",
                mobilePci = "",
                mobileEarfcn = "",
                rsrp = "",
                rsrq = "",
                sinr = "",
                wifiSsid = "",
                wifiBssid = "",
                wifiIpAddress = "",
                wifiFrequency = "",
                wifiLinkSpeed = "",
                supportedRefreshRates = "",
                batteryCycleCount = "",
                wirelessDebugPort = null,
            )
    }
}

private data class SignalMetrics(
    val rsrp: String,
    val rsrq: String,
    val sinr: String,
)

private data class CellularIdentityMetrics(
    val band: String,
    val pci: String,
    val earfcn: String,
)

private data class WifiMetrics(
    val ssid: String,
    val bssid: String,
    val frequency: String,
    val linkSpeed: String,
)

internal suspend fun loadDeviceDashboardSnapshot(sessionData: SessionData): DeviceDashboardSnapshot {
    val connection = AdbBridge.getConnection()
    if (connection == null) {
        return DeviceDashboardSnapshot.loading(sessionData).copy(
            isLoading = false,
            errorMessage = "当前没有可用的 ADB 连接，无法读取首页参数。",
        )
    }

    suspend fun shell(command: String): String =
        connection
            .executeShell(command, retryOnFailure = false)
            .getOrNull()
            ?.trim()
            .orEmpty()

    return runCatching {
        coroutineScope {
            val modelDeferred = async { shell("getprop ro.product.model") }
            val manufacturerDeferred = async { shell("getprop ro.product.manufacturer") }
            val socModelDeferred = async { shell("getprop ro.soc.model") }
            val androidVersionDeferred = async { shell("getprop ro.build.version.release") }
            val uptimeDeferred = async { shell("cat /proc/uptime | awk '{print \$1}'") }
            val basebandDeferred = async { shell("getprop gsm.version.baseband") }
            val productCodeNameDeferred = async { shell("getprop ro.product.device") }
            val securityPatchDeferred = async { shell("getprop ro.build.version.security_patch") }
            val serialDeferred = async { shell("getprop ro.serialno") }
            val resolutionDeferred = async { shell("wm size | grep -E 'Physical|Override' | head -n 1") }
            val dpiDeferred = async { shell("wm density | grep -E 'Physical|Override' | head -n 1") }
            val displayMetricsDeferred =
                async { shell("dumpsys display | grep -E 'xDpi=|yDpi=|density .* dpi' | head -n 4") }
            val displayRefreshDeferred = async { shell("dumpsys display | grep -m 1 'DisplayDeviceInfo{'") }
            val wifiIpDeferred = async { shell("ip addr show wlan0 | grep -m 1 'inet '") }
            val mobileNetworkTypeDeferred = async { shell("getprop gsm.network.type") }
            val carrierNamesDeferred = async { shell("getprop gsm.operator.alpha") }
            val signalStrengthDeferred = async { shell("dumpsys telephony.registry | grep -m 1 'mSignalStrength='") }
            val cellIdentityDeferred = async { shell("dumpsys telephony.registry | grep -m 1 'mCellIdentity='") }
            val wifiInfoDeferred = async { shell("dumpsys wifi | grep -m 1 'mWifiInfo SSID:'") }
            val memoryDeferred = async { shell("cat /proc/meminfo | grep -E 'MemTotal|MemAvailable'") }
            val dataDfDeferred = async { shell("df /data | tail -n 1") }
            val batteryCycleDeferred =
                async {
                    shell(
                        """
                        for path in /sys/class/power_supply/battery/cycle_count /sys/class/power_supply/bq_bms/cycle_count
                        do
                            if [ -r "${'$'}path" ]; then
                                value=${'$'}(cat "${'$'}path" 2>/dev/null)
                                if [ -n "${'$'}value" ]; then
                                    echo "${'$'}value"
                                    break
                                fi
                            fi
                        done
                        """.trimIndent(),
                    )
                }
            val batteryDeferred =
                async {
                    shell(
                        "dumpsys battery | grep -E 'level:|status:|health:|voltage:|temperature:|current now:|current average:'",
                    )
                }
            val voltageNowDeferred = async { shell("cat /sys/class/power_supply/battery/voltage_now 2>/dev/null") }
            val batteryCurrentNowDeferred = async { shell("cmd battery get -f current_now 2>/dev/null") }
            val batteryCurrentAverageDeferred = async { shell("cmd battery get -f current_average 2>/dev/null") }
            val currentNowDeferred =
                async {
                    shell(
                        """
                        for path in \
                            /sys/class/power_supply/battery/current_now \
                            /sys/class/power_supply/battery/current_avg \
                            /sys/class/power_supply/bms/current_now \
                            /sys/class/power_supply/main/current_now \
                            /sys/class/power_supply/battery/constant_charge_current \
                            /sys/class/power_supply/usb/current_max
                        do
                            if [ -r "${'$'}path" ]; then
                                value=${'$'}(cat "${'$'}path" 2>/dev/null)
                                if [ -n "${'$'}value" ]; then
                                    echo "${'$'}value"
                                    break
                                fi
                            fi
                        done
                        """.trimIndent(),
                    )
                }
            val abiDeferred = async { shell("getprop ro.product.cpu.abilist") }
            val boardDeferred = async { shell("getprop ro.product.board") }
            val fingerprintDeferred = async { shell("getprop ro.build.fingerprint") }
            val wirelessPortDeferred = async { shell("getprop service.adb.tcp.port") }
            val cpuCountDeferred = async { shell("cat /proc/cpuinfo | grep -c processor") }
            val cpuFreqDeferred =
                async { shell("cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq 2>/dev/null") }

            val memoryMap = parseKeyValueBlock(memoryDeferred.await())
            val batteryMap = parseKeyValueBlock(batteryDeferred.await())
            val cpuCount = cpuCountDeferred.await()
            val cpuFreqKhz = cpuFreqDeferred.await()
            val resolutionValue = formatWmValue(resolutionDeferred.await(), "Physical size:")
            val dpiValue = formatWmValue(dpiDeferred.await(), "Physical density:")
            val densityDpi = parseDensityDpi(dpiValue)
            val physicalDpiPair =
                parsePhysicalDpiPair(displayMetricsDeferred.await())
                    ?: densityDpi?.let { it.toDouble() to it.toDouble() }
            val signalMetrics = parseSignalMetrics(signalStrengthDeferred.await())
            val cellIdentityMetrics = parseCellularIdentityMetrics(cellIdentityDeferred.await())
            val wifiMetrics = parseWifiMetrics(wifiInfoDeferred.await())

            DeviceDashboardSnapshot(
                isLoading = false,
                model = modelDeferred.await().ifBlank { sessionData.name.ifBlank { "未知设备" } },
                manufacturer = manufacturerDeferred.await(),
                socModel = formatSocModel(socModelDeferred.await(), boardDeferred.await()),
                androidVersion = androidVersionDeferred.await().ifBlank { "Android" },
                uptime = formatUptime(uptimeDeferred.await()),
                basebandVersion = formatBasebandVersion(basebandDeferred.await()),
                productCodeName = productCodeNameDeferred.await(),
                securityPatch = securityPatchDeferred.await(),
                serialNumber = serialDeferred.await(),
                connectionTypeLabel = if (sessionData.isUsbConnection()) "USB / OTG" else "WiFi / TCP",
                resolution = resolutionValue,
                dpi = dpiValue,
                ppi = formatPpi(physicalDpiPair),
                screenSize = formatScreenSize(resolutionValue, physicalDpiPair),
                refreshRate = formatRefreshRate(displayRefreshDeferred.await()),
                storageSummary = formatStorageSummary(dataDfDeferred.await()),
                memoryTotal = formatMemValue(memoryMap["MemTotal"]),
                memoryAvailable = formatMemValue(memoryMap["MemAvailable"]),
                batteryLevel = batteryMap["level"]?.let { "$it %" }.orEmpty(),
                batteryStatus = mapBatteryStatus(batteryMap["status"]),
                batteryHealth = mapBatteryHealth(batteryMap["health"]),
                voltage = formatBatteryVoltage(batteryMap["voltage"], voltageNowDeferred.await()),
                currentNow =
                    formatBatteryCurrent(
                        dumpsysCurrentNow = batteryMap["current now"],
                        dumpsysCurrentAverage = batteryMap["current average"],
                        batteryCurrentNow = batteryCurrentNowDeferred.await(),
                        batteryCurrentAverage = batteryCurrentAverageDeferred.await(),
                        sysfsCurrent = currentNowDeferred.await(),
                    ),
                temperature = batteryMap["temperature"]?.let { formatBatteryTemperature(it) }.orEmpty(),
                cpuSummary = formatCpuSummary(cpuCount, cpuFreqKhz),
                abi = abiDeferred.await(),
                board = boardDeferred.await(),
                fingerprint = fingerprintDeferred.await(),
                mobileNetworkType = formatNetworkPropertyList(mobileNetworkTypeDeferred.await()),
                carrierNames = formatCarrierNames(carrierNamesDeferred.await()),
                mobileBand = cellIdentityMetrics.band,
                mobilePci = cellIdentityMetrics.pci,
                mobileEarfcn = cellIdentityMetrics.earfcn,
                rsrp = signalMetrics.rsrp,
                rsrq = signalMetrics.rsrq,
                sinr = signalMetrics.sinr,
                wifiSsid = wifiMetrics.ssid,
                wifiBssid = wifiMetrics.bssid,
                wifiIpAddress = formatWifiIpAddress(wifiIpDeferred.await()),
                wifiFrequency = wifiMetrics.frequency,
                wifiLinkSpeed = wifiMetrics.linkSpeed,
                supportedRefreshRates = formatSupportedRefreshRates(displayRefreshDeferred.await()),
                batteryCycleCount = formatBatteryCycleCount(batteryCycleDeferred.await()),
                wirelessDebugPort = parseAdbTcpPort(wirelessPortDeferred.await()),
            )
        }
    }.getOrElse { error ->
        DeviceDashboardSnapshot.loading(sessionData).copy(
            isLoading = false,
            errorMessage = error.message ?: "读取设备首页参数失败。",
        )
    }
}

private fun parseKeyValueBlock(text: String): Map<String, String> =
    text
        .lineSequence()
        .mapNotNull { line ->
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                parts[0].trim() to parts[1].trim()
            } else {
                null
            }
        }.toMap()

private fun parseKeyValueEqualsBlock(text: String): Map<String, String> =
    Regex("""([A-Za-z][A-Za-z0-9_]+)=([^=\n]+?)(?=\s+[A-Za-z][A-Za-z0-9_]+=|$)""")
        .findAll(text)
        .associate { match ->
            match.groupValues[1].trim() to match.groupValues[2].trim()
        }

private fun formatWmValue(
    raw: String,
    prefix: String,
): String =
    raw
        .removePrefix(prefix)
        .substringAfter(": ", missingDelimiterValue = raw.removePrefix(prefix))
        .trim()

private fun parseResolution(resolution: String): Pair<Int, Int>? {
    val cleaned = resolution.substringAfterLast(": ", resolution).trim()
    val width = cleaned.substringBefore("x").trim().toIntOrNull() ?: return null
    val height = cleaned.substringAfter("x", "").trim().toIntOrNull() ?: return null
    return width to height
}

private fun parseDensityDpi(dpi: String): Int? = dpi.substringAfterLast(": ", dpi).trim().toIntOrNull()

private fun parsePhysicalDpiPair(raw: String): Pair<Double, Double>? {
    val equalsPattern = Regex("""xDpi\s*=\s*([0-9]+(?:\.[0-9]+)?)\D+yDpi\s*=\s*([0-9]+(?:\.[0-9]+)?)""")
    equalsPattern.find(raw)?.let { match ->
        val x = match.groupValues[1].toDoubleOrNull()
        val y = match.groupValues[2].toDoubleOrNull()
        if (x != null && y != null) return x to y
    }

    val tuplePattern = Regex("""\(([0-9]+(?:\.[0-9]+)?) x ([0-9]+(?:\.[0-9]+)?)\)\s*dpi""")
    tuplePattern.find(raw)?.let { match ->
        val x = match.groupValues[1].toDoubleOrNull()
        val y = match.groupValues[2].toDoubleOrNull()
        if (x != null && y != null) return x to y
    }

    return null
}

private fun formatPpi(physicalDpiPair: Pair<Double, Double>?): String {
    val (xDpi, yDpi) = physicalDpiPair ?: return ""
    val average = (xDpi + yDpi) / 2.0
    return String.format(Locale.US, "%.0f PPI", average)
}

private fun formatScreenSize(
    resolution: String,
    physicalDpiPair: Pair<Double, Double>?,
): String {
    val (width, height) = parseResolution(resolution) ?: return ""
    val (xDpi, yDpi) = physicalDpiPair ?: return ""
    if (xDpi <= 0.0 || yDpi <= 0.0) return ""
    val widthInches = width / xDpi
    val heightInches = height / yDpi
    val diagonalInches = kotlin.math.sqrt((widthInches * widthInches) + (heightInches * heightInches))
    return String.format(Locale.US, "%.2f 英寸", diagonalInches)
}

private fun formatNetworkPropertyList(raw: String): String =
    raw
        .split(",")
        .map { it.trim() }
        .filter { value ->
            value.isNotBlank() &&
                value != "Unknown" &&
                value != "unknown" &&
                value != "N/A"
        }.distinct()
        .joinToString(separator = ", ")

private fun parseSignalMetrics(raw: String): SignalMetrics {
    if (raw.isBlank()) {
        return SignalMetrics("", "", "")
    }

    fun extract(pattern: String): String =
        Regex(pattern, RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeUnless { it == "2147483647" }
            .orEmpty()

    val nrRsrp = extract("""ssRsrp\s*=\s*(-?\d+)""")
    val nrRsrq = extract("""ssRsrq\s*=\s*(-?\d+)""")
    val nrSinr = extract("""ssSinr\s*=\s*(-?\d+)""")
    if (nrRsrp.isNotBlank() || nrRsrq.isNotBlank() || nrSinr.isNotBlank()) {
        return SignalMetrics(
            rsrp = formatSignalMetric(nrRsrp, "dBm"),
            rsrq = formatSignalMetric(nrRsrq, "dB"),
            sinr = formatSinrMetric(nrSinr),
        )
    }

    return SignalMetrics(
        rsrp = formatSignalMetric(extract("""rsrp\s*=\s*(-?\d+)"""), "dBm"),
        rsrq = formatSignalMetric(extract("""rsrq\s*=\s*(-?\d+)"""), "dB"),
        sinr = formatSinrMetric(extract("""rssnr\s*=\s*(-?\d+)""")),
    )
}

private fun parseCellularIdentityMetrics(raw: String): CellularIdentityMetrics {
    if (raw.isBlank()) {
        return CellularIdentityMetrics("", "", "")
    }

    fun extract(pattern: String): String =
        Regex(pattern, RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()

    return CellularIdentityMetrics(
        band =
            extract("""mBands\s*=\s*\[([^\]]+)\]""")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(", "),
        pci = extract("""mPci\s*=\s*([*\d]+)"""),
        earfcn =
            extract("""mNrArfcn\s*=\s*([*\d]+)""")
                .ifBlank {
                    extract("""mEarfcn\s*=\s*([*\d]+)""")
                },
    )
}

private fun parseWifiMetrics(raw: String): WifiMetrics {
    if (raw.isBlank()) {
        return WifiMetrics("", "", "", "")
    }

    fun extract(pattern: String): String =
        Regex(pattern, RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()

    val ssid =
        Regex("""SSID:\s*"?(.*?)(?:"?\s*,|\s+BSSID:|$)""", RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()

    return WifiMetrics(
        ssid =
            when {
                ssid.equals("<unknown ssid>", ignoreCase = true) -> ""
                ssid.equals("<none>", ignoreCase = true) -> ""
                ssid.equals("null", ignoreCase = true) -> ""
                else -> ssid
            },
        bssid = extract("""BSSID:\s*([^,]+)"""),
        frequency = extract("""Frequency:\s*([0-9]+)\s*MHz""").let { if (it.isNotBlank()) "$it MHz" else "" },
        linkSpeed = extract("""Link speed:\s*([0-9]+)\s*Mbps""").let { if (it.isNotBlank()) "$it Mbps" else "" },
    )
}

private fun formatCarrierNames(raw: String): String =
    raw
        .split(",")
        .map { it.trim() }
        .filter { value ->
            value.isNotBlank() &&
                value != "unknown" &&
                value != "Unknown" &&
                value != "(unknown)"
        }.distinct()
        .joinToString(separator = ", ")

internal fun formatMobileBandSummary(
    networkType: String,
    band: String,
): String =
    when {
        band.isBlank() -> {
            ""
        }

        networkType.contains("NR", ignoreCase = true) -> {
            val normalized =
                band
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .joinToString(", ") { "n$it" }
            "$networkType $normalized"
        }

        band.startsWith("B", ignoreCase = true) -> {
            "$networkType $band".trim()
        }

        networkType.isBlank() -> {
            band
        }

        else -> {
            val normalized =
                band
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .joinToString(", ") { "B$it" }
            "$networkType $normalized"
        }
    }

internal fun formatWifiSummary(
    frequency: String,
    linkSpeed: String,
): String =
    listOf(frequency, linkSpeed)
        .filter { it.isNotBlank() }
        .joinToString(" / ")

private fun formatWifiSsid(raw: String): String {
    val value =
        Regex("""SSID:\s*"?(.*?)(?:"?\s*,|\s+BSSID:|$)""", RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()
    return when {
        value.isBlank() -> ""
        value.equals("<unknown ssid>", ignoreCase = true) -> ""
        value.equals("<none>", ignoreCase = true) -> ""
        value.equals("null", ignoreCase = true) -> ""
        else -> value
    }
}

private fun formatRefreshRate(raw: String): String {
    val rate =
        Regex("""renderFrameRate\s*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
            ?: return ""

    return String.format(Locale.US, "%.0f Hz", rate)
}

private fun formatSupportedRefreshRates(raw: String): String {
    val directRates =
        Regex("""supportedRefreshRates\s*\[([^\]]+)\]""", RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.split(",")
            ?.mapNotNull { it.trim().toDoubleOrNull() }
            ?.map { String.format(Locale.US, "%.0f", it) }
            ?.distinct()
            .orEmpty()

    if (directRates.isNotEmpty()) {
        return directRates.joinToString("/") + " Hz"
    }

    val modeRates =
        Regex("""fps=([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)
            .findAll(raw)
            .mapNotNull { it.groupValues.getOrNull(1)?.toDoubleOrNull() }
            .map { String.format(Locale.US, "%.0f", it) }
            .distinct()
            .toList()

    return if (modeRates.isEmpty()) "" else modeRates.joinToString("/") + " Hz"
}

internal fun formatDisplaySummary(
    resolution: String,
    refreshRate: String,
): String =
    listOf(resolution, refreshRate)
        .filter { it.isNotBlank() }
        .joinToString(" / ")

internal fun formatScreenMetricsSummary(
    dpi: String,
    ppi: String,
    screenSize: String,
): String =
    listOf(formatDpiLabel(dpi), ppi, screenSize)
        .filter { it.isNotBlank() }
        .joinToString("/")

private fun formatDpiLabel(raw: String): String =
    when {
        raw.isBlank() -> ""
        raw.contains("dpi", ignoreCase = true) -> raw
        else -> "$raw DPI"
    }

internal fun formatBatterySummary(
    health: String,
    voltage: String,
    current: String,
): String =
    listOf(voltage, current, health)
        .filter { it.isNotBlank() }
        .joinToString(" / ")

internal fun formatBatteryStatusSummary(
    status: String,
    level: String,
    temperature: String,
): String =
    listOf(level, status, temperature)
        .filter { it.isNotBlank() }
        .joinToString(" / ")

private fun formatSocModel(
    socModel: String,
    board: String,
): String =
    socModel
        .ifBlank { board }
        .trim()

private fun formatBasebandVersion(raw: String): String =
    raw
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" / ")

private fun formatWifiIpAddress(raw: String): String =
    Regex("""inet\s+([0-9.]+)\/""", RegexOption.IGNORE_CASE)
        .find(raw)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()

private fun formatBatteryCycleCount(raw: String): String = raw.trim().takeIf { it.isNotBlank() && it != "0" } ?: ""

private fun formatStorageSummary(dfLine: String): String {
    val parts = dfLine.trim().split(Regex("\\s+"))
    if (parts.size < 4) return ""
    val totalKb = parts.getOrNull(1)?.toLongOrNull() ?: return ""
    val availableKb = parts.getOrNull(3)?.toLongOrNull() ?: return ""
    val availableBytes = availableKb * 1024
    val totalBytes = totalKb * 1024
    return "${
        formatBytes(
            availableBytes,
        )
    } / ${formatBytes(totalBytes)}${formatAvailablePercent(availableBytes, totalBytes)}"
}

internal fun formatMemorySummary(
    available: String,
    total: String,
): String =
    when {
        available.isNotBlank() && total.isNotBlank() -> {
            val availableBytes = parseDisplayBytes(available)
            val totalBytes = parseDisplayBytes(total)
            "$available / $total${formatAvailablePercent(availableBytes, totalBytes)}"
        }

        available.isNotBlank() -> {
            available
        }

        else -> {
            total
        }
    }

private fun formatAvailablePercent(
    availableBytes: Long?,
    totalBytes: Long?,
): String {
    if (availableBytes == null || totalBytes == null || totalBytes <= 0L) {
        return ""
    }

    val percent = (availableBytes.toDouble() / totalBytes.toDouble()) * 100.0
    return String.format(Locale.US, " (%.0f%%)", percent)
}

private fun formatSignalMetric(
    value: String,
    unit: String,
): String = value.takeIf { it.isNotBlank() }?.let { "$it $unit" }.orEmpty()

private fun formatSinrMetric(value: String): String {
    val raw = value.toIntOrNull() ?: return ""
    val display =
        if (kotlin.math.abs(raw) >= 100) {
            String.format(Locale.US, "%.1f", raw / 10.0)
        } else {
            raw.toString()
        }
    return "$display dB"
}

private fun formatUptime(raw: String): String {
    val seconds =
        raw
            .substringBefore(" ")
            .trim()
            .toDoubleOrNull()
            ?.toLong() ?: return ""
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60

    return when {
        days > 0 -> "${days}天 ${hours}小时"
        hours > 0 -> "${hours}小时 ${minutes}分钟"
        else -> "${minutes}分钟"
    }
}

private fun formatMemValue(raw: String?): String {
    val kb = raw?.split(Regex("\\s+"))?.firstOrNull()?.toLongOrNull() ?: return ""
    return formatBytes(kb * 1024)
}

internal fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.2f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f G", gb)
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / 1024.0 / 1024.0 / 1024.0
    return String.format("%.2f G", gb)
}

private fun parseDisplayBytes(text: String): Long? {
    val match = Regex("""([0-9]+(?:\.[0-9]+)?)\s*([kmgt]?b?|[kmgt])""", RegexOption.IGNORE_CASE).find(text)
        ?: return null
    val value = match.groupValues[1].toDoubleOrNull() ?: return null
    val unit = match.groupValues[2].lowercase(Locale.US)
    val multiplier =
        when (unit) {
            "t", "tb" -> 1024.0 * 1024 * 1024 * 1024
            "g", "gb" -> 1024.0 * 1024 * 1024
            "m", "mb" -> 1024.0 * 1024
            "k", "kb" -> 1024.0
            "b", "" -> 1.0
            else -> return null
        }
    return (value * multiplier).toLong()
}

private fun mapBatteryStatus(raw: String?): String =
    when (raw) {
        "2" -> "充电中"
        "3" -> "放电中"
        "4" -> "未充电"
        "5" -> "已充满"
        else -> raw.orEmpty()
    }

private fun mapBatteryHealth(raw: String?): String =
    when (raw) {
        "2" -> "良好"
        "3" -> "过热"
        "4" -> "损坏"
        "5" -> "过压"
        "7" -> "过冷"
        else -> raw.orEmpty()
    }

private fun formatBatteryTemperature(raw: String): String {
    val temp = raw.toFloatOrNull() ?: return raw
    return String.format("%.1f °C", temp / 10f)
}

private fun formatCurrentNow(raw: String): String {
    val value = raw.toLongOrNull() ?: return raw
    val absolute = kotlin.math.abs(value).toDouble()
    val ma =
        when {
            absolute >= 1_000_000_000.0 -> absolute / 1_000_000.0
            absolute >= 10_000.0 -> absolute / 1000.0
            else -> absolute
        }
    return String.format("%.0f mA", ma)
}

private fun formatBatteryCurrent(
    dumpsysCurrentNow: String?,
    dumpsysCurrentAverage: String?,
    batteryCurrentNow: String,
    batteryCurrentAverage: String,
    sysfsCurrent: String,
): String {
    val dumpsysNowValue = dumpsysCurrentNow?.trim()?.toLongOrNull()
    if (dumpsysNowValue != null && dumpsysNowValue != 0L) {
        return formatCurrentNow(dumpsysNowValue.toString())
    }

    val dumpsysAverageValue = dumpsysCurrentAverage?.trim()?.toLongOrNull()
    if (dumpsysAverageValue != null && dumpsysAverageValue != 0L) {
        return formatCurrentNow(dumpsysAverageValue.toString())
    }

    val nowValue = batteryCurrentNow.trim().toLongOrNull()
    if (nowValue != null && nowValue != 0L) {
        return formatCurrentNow(nowValue.toString())
    }

    val averageValue = batteryCurrentAverage.trim().toLongOrNull()
    if (averageValue != null && averageValue != 0L) {
        return formatCurrentNow(averageValue.toString())
    }

    return formatCurrentNow(sysfsCurrent)
}

private fun formatBatteryVoltage(
    dumpsysVoltage: String?,
    sysfsVoltageNow: String,
): String {
    val dumpsysValue = dumpsysVoltage?.toLongOrNull()
    if (dumpsysValue != null && dumpsysValue > 0) {
        return "$dumpsysValue mV"
    }

    val sysfsValue = sysfsVoltageNow.trim().toLongOrNull() ?: return dumpsysVoltage.orEmpty()
    if (sysfsValue <= 0) return dumpsysVoltage.orEmpty()

    val mv =
        if (sysfsValue >= 100_000) {
            sysfsValue / 1000.0
        } else {
            sysfsValue.toDouble()
        }
    return String.format("%.0f mV", mv)
}

private fun parseAdbTcpPort(raw: String): Int? {
    val port = raw.trim().toIntOrNull() ?: return null
    return port.takeIf { it > 0 }
}

private fun formatCpuSummary(
    cpuCountRaw: String,
    cpuFreqRaw: String,
): String {
    val count = cpuCountRaw.toIntOrNull()
    val freqKhz = cpuFreqRaw.toLongOrNull()
    val freqText =
        if (freqKhz != null && freqKhz > 0) {
            String.format("%.2f GHz", freqKhz / 1_000_000.0)
        } else {
            ""
        }
    return when {
        count != null && freqText.isNotBlank() -> "$count 核 / $freqText"
        count != null -> "$count 核"
        else -> freqText
    }
}

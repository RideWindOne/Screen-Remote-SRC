package com.screen.remote.android.feature.session.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.designsystem.component.AppDivider
import com.screen.remote.android.core.designsystem.component.SectionCard
import com.screen.remote.android.core.i18n.ManagementTexts
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import dadb.helper.RemoteDeviceField
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.Locale

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

internal suspend fun loadDeviceDashboardSnapshot(
    context: Context,
    sessionData: SessionData,
    preferCachedConnectionInfo: Boolean = false,
): DeviceDashboardSnapshot {
    val connection =
        SessionManagementAdbConnection.current() ?: return DeviceDashboardSnapshot.loading(sessionData).copy(
            isLoading = false,
            errorMessage = ManagementTexts.DeviceInfo.NO_CONNECTION_FOR_OVERVIEW.get(),
        )

    val helperResult =
        runCatching {
            val helperJar = ensureLocalDadbHelperJar(context)
            connection
                .loadDeviceSnapshotWithHelper(localHelperJar = helperJar)
                .getOrThrow()
        }.getOrElse { error ->
            return DeviceDashboardSnapshot.loading(sessionData).copy(
                isLoading = false,
                errorMessage = error.message ?: ManagementTexts.DeviceInfo.OVERVIEW_LOAD_FAILED.get(),
            )
        }
    val failedFields =
        buildMap {
            helperResult.fields.forEach { (field, result) ->
                if (result.error.isNotBlank()) put(field.wireName, result.error)
            }
            (RemoteDeviceField.entries - helperResult.fields.keys).forEach { field ->
                put(field.wireName, "Missing helper response")
            }
        }
    if (failedFields.isNotEmpty()) {
        LogManager.w(
            LogTags.ADB_CONNECTION,
            "Dashboard helper completed with ${failedFields.size} failed fields: ${
                failedFields.toSortedMap().entries.joinToString("; ") { (key, error) -> "$key=$error" }
            }",
        )
    }

    fun field(field: RemoteDeviceField): String = helperResult.fields[field]?.value?.trim().orEmpty()

    val cachedDeviceInfo = connection.deviceInfo
    val cachedPreflight = connection.getCachedCandidatePreflight().takeIf { preferCachedConnectionInfo }
    val cachedDisplayInfo = cachedPreflight?.displayInfo

    fun cachedValue(value: String): String? =
        value.takeIf { preferCachedConnectionInfo && it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }

    return runCatching {
        coroutineScope {
            val modelDeferred = async { cachedValue(cachedDeviceInfo.model) ?: field(RemoteDeviceField.Model) }
            val manufacturerDeferred =
                async { cachedValue(cachedDeviceInfo.manufacturer) ?: field(RemoteDeviceField.Manufacturer) }
            val socModelDeferred = async { field(RemoteDeviceField.SocModel) }
            val androidVersionDeferred =
                async { cachedValue(cachedDeviceInfo.androidVersion) ?: field(RemoteDeviceField.AndroidVersion) }
            val uptimeDeferred = async { field(RemoteDeviceField.Uptime) }
            val basebandDeferred = async { field(RemoteDeviceField.Baseband) }
            val productCodeNameDeferred = async { field(RemoteDeviceField.ProductCodeName) }
            val securityPatchDeferred = async { field(RemoteDeviceField.SecurityPatch) }
            val serialDeferred =
                async { cachedValue(cachedDeviceInfo.serialNumber) ?: field(RemoteDeviceField.Serial) }
            val resolutionDeferred =
                async {
                    cachedDisplayInfo?.let { "${it.currentWidth}x${it.currentHeight}" }
                        ?: field(RemoteDeviceField.Resolution)
                }
            val dpiDeferred =
                async {
                    cachedDisplayInfo?.currentDensityDpi?.toString()
                        ?: field(RemoteDeviceField.Density)
                }
            val displayMetricsDeferred = async { field(RemoteDeviceField.DisplayMetrics) }
            val displayRefreshDeferred = async { field(RemoteDeviceField.DisplayInfo) }
            val networkInterfacesDeferred = async { field(RemoteDeviceField.NetworkInterfaces) }
            val defaultRouteDeferred = async { field(RemoteDeviceField.DefaultRoute) }
            val mobileNetworkTypeDeferred = async { field(RemoteDeviceField.MobileNetworkType) }
            val carrierNamesDeferred = async { field(RemoteDeviceField.CarrierNames) }
            val signalStrengthDeferred = async { field(RemoteDeviceField.SignalStrength) }
            val cellIdentityDeferred = async { field(RemoteDeviceField.CellIdentity) }
            val wifiInfoDeferred = async { field(RemoteDeviceField.WifiInfo) }
            val memoryDeferred = async { field(RemoteDeviceField.Memory) }
            val dataDfDeferred = async { field(RemoteDeviceField.DataFilesystem) }
            val batteryCycleDeferred = async { field(RemoteDeviceField.BatteryCycle) }
            val batteryDeferred = async { field(RemoteDeviceField.Battery) }
            val voltageNowDeferred = async { field(RemoteDeviceField.VoltageNow) }
            val batteryCurrentNowDeferred = async { field(RemoteDeviceField.BatteryCurrentNow) }
            val batteryCurrentAverageDeferred = async { field(RemoteDeviceField.BatteryCurrentAverage) }
            val currentNowDeferred = async { field(RemoteDeviceField.SysfsCurrent) }
            val abiDeferred = async { field(RemoteDeviceField.Abi) }
            val boardDeferred = async { field(RemoteDeviceField.Board) }
            val fingerprintDeferred =
                async {
                    cachedPreflight?.buildFingerprint?.takeIf { it.isNotBlank() }
                        ?: field(RemoteDeviceField.Fingerprint)
                }
            val wirelessPortDeferred = async { field(RemoteDeviceField.WirelessPort) }
            val cpuCountDeferred = async { field(RemoteDeviceField.CpuCount) }
            val cpuFreqDeferred = async { field(RemoteDeviceField.CpuMaxFrequency) }

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
                model = modelDeferred.await()
                    .ifBlank { sessionData.name.ifBlank { ManagementTexts.General.UNKNOWN_DEVICE.get() } },
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
                ipv4Interfaces = parseIpv4Interfaces(networkInterfacesDeferred.await()),
                defaultGateway = parseDefaultGateway(defaultRouteDeferred.await()),
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
            errorMessage = error.message ?: ManagementTexts.DeviceInfo.OVERVIEW_LOAD_FAILED.get(),
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
    return ManagementTexts.DeviceInfo.SCREEN_INCHES.format(diagonalInches)
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

internal fun parseIpv4Interfaces(raw: String): List<DeviceIpv4Interface> {
    val addresses = linkedSetOf<DeviceIpv4Interface>()
    val ipLinePattern =
        Regex("""^\d+:\s+([^:@\s]+)(?:@[^:\s]+)?\s+.*\binet\s+([0-9.]+)(?:\/\d+)?\b""")
    val ifconfigIpv4Pattern =
        Regex("""\binet(?:\s+addr:|\s+)([0-9.]+)\b""", RegexOption.IGNORE_CASE)
    var ifconfigInterface = ""

    raw.lineSequence().forEach { sourceLine ->
        val line = sourceLine.trimEnd()
        ipLinePattern.find(line.trim())?.let { match ->
            addresses += DeviceIpv4Interface(match.groupValues[1], match.groupValues[2])
            return@forEach
        }

        if (line.isNotBlank() && !sourceLine.first().isWhitespace()) {
            ifconfigInterface = line.substringBefore(':').substringBefore(' ').trim()
        }
        val address = ifconfigIpv4Pattern.find(line)?.groupValues?.getOrNull(1)
        if (ifconfigInterface.isNotBlank() && address != null) {
            addresses += DeviceIpv4Interface(ifconfigInterface, address)
        }
    }

    return addresses
        .asSequence()
        .filterNot { it.name == "lo" || it.address.startsWith("127.") }
        .sortedWith(
            compareBy<DeviceIpv4Interface> { networkInterfaceOrder(it.name) }
                .thenBy(DeviceIpv4Interface::name)
                .thenBy(DeviceIpv4Interface::address),
        ).toList()
}

internal fun parseDefaultGateway(raw: String): String {
    val route = raw.lineSequence().firstOrNull { it.trimStart().startsWith("default") }.orEmpty()
    val gateway = Regex("""\bvia\s+(\S+)""").find(route)?.groupValues?.getOrNull(1).orEmpty()
    val interfaceName = Regex("""\bdev\s+(\S+)""").find(route)?.groupValues?.getOrNull(1).orEmpty()
    val ipRouteValue =
        when {
            gateway.isNotBlank() && interfaceName.isNotBlank() -> "$gateway ($interfaceName)"
            gateway.isNotBlank() -> gateway
            interfaceName.isNotBlank() -> interfaceName
            else -> ""
        }
    if (ipRouteValue.isNotBlank()) return ipRouteValue

    val procRoute =
        raw.lineSequence()
            .map { it.trim().split(Regex("""\s+""")) }
            .firstOrNull { columns ->
                columns.size >= 4 &&
                    columns[1] == "00000000" &&
                    (columns[3].toIntOrNull(16)?.and(0x2) ?: 0) != 0
            } ?: return ""
    val procGateway = decodeLittleEndianIpv4(procRoute[2])
    return if (procGateway.isBlank()) procRoute[0] else "$procGateway (${procRoute[0]})"
}

private fun decodeLittleEndianIpv4(hex: String): String {
    if (hex.length != 8) return ""
    val bytes = hex.chunked(2).map { it.toIntOrNull(16) ?: return "" }
    return bytes.asReversed().joinToString(".")
}

private fun networkInterfaceOrder(name: String): Int =
    when {
        name.matches(Regex("""(?:wlan|wifi)\d+""", RegexOption.IGNORE_CASE)) -> 0
        name.matches(Regex("""(?:ap|softap|swlan)\d*""", RegexOption.IGNORE_CASE)) -> 1
        name.matches(Regex("""(?:rndis|usb)\d*""", RegexOption.IGNORE_CASE)) -> 2
        else -> 3
    }

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
    val seconds = parseProcUptimeSeconds(raw) ?: return ""
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60

    return when {
        days > 0 -> ManagementTexts.DeviceInfo.UPTIME_DAYS_HOURS.format(days, hours)
        hours > 0 -> ManagementTexts.DeviceInfo.UPTIME_HOURS_MINUTES.format(hours, minutes)
        else -> ManagementTexts.DeviceInfo.UPTIME_MINUTES.format(minutes)
    }
}

internal fun parseProcUptimeSeconds(raw: String): Long? =
    raw
        .substringBefore(" ")
        .trim()
        .toDoubleOrNull()
        ?.toLong()

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
    return String.format(Locale.US, "%.2f G", gb)
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
        "2" -> ManagementTexts.DeviceInfo.BATTERY_CHARGING.get()
        "3" -> ManagementTexts.DeviceInfo.BATTERY_DISCHARGING.get()
        "4" -> ManagementTexts.DeviceInfo.BATTERY_NOT_CHARGING.get()
        "5" -> ManagementTexts.DeviceInfo.BATTERY_FULL.get()
        else -> raw.orEmpty()
    }

private fun mapBatteryHealth(raw: String?): String =
    when (raw) {
        "2" -> ManagementTexts.DeviceInfo.BATTERY_GOOD.get()
        "3" -> ManagementTexts.DeviceInfo.BATTERY_OVERHEAT.get()
        "4" -> ManagementTexts.DeviceInfo.BATTERY_DEAD.get()
        "5" -> ManagementTexts.DeviceInfo.BATTERY_OVER_VOLTAGE.get()
        "7" -> ManagementTexts.DeviceInfo.BATTERY_COLD.get()
        else -> raw.orEmpty()
    }

private fun formatBatteryTemperature(raw: String): String {
    val temp = raw.toFloatOrNull() ?: return raw
    return String.format(Locale.US, "%.1f °C", temp / 10f)
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
    return String.format(Locale.US, "%.0f mA", ma)
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
    return String.format(Locale.US, "%.0f mV", mv)
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
            String.format(Locale.US, "%.2f GHz", freqKhz / 1_000_000.0)
        } else {
            ""
        }
    return when {
        count != null && freqText.isNotBlank() -> ManagementTexts.DeviceInfo.CPU_CORES_WITH_FREQUENCY.format(
            count,
            freqText
        )

        count != null -> ManagementTexts.DeviceInfo.CPU_CORES.format(count)
        else -> freqText
    }
}

@Composable
internal fun SessionManagementHomeSnapshot(snapshot: DeviceDashboardSnapshot) {
    if (snapshot.errorMessage != null) {
        SessionManagementNoteCard(
            title = ManagementTexts.DeviceInfo.COULDN_T_LOAD_DEVICE_INFO.get(),
            text = snapshot.errorMessage,
        )
        return
    }

    if (snapshot.isLoading) {
        SessionManagementHomeLoadingSkeleton()
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SessionManagementInfoGroup(
            title = ManagementTexts.DeviceInfo.SYSTEM_HARDWARE.get(),
            items =
                listOf(
                    ManagementTexts.DeviceInfo.BRAND_MODEL.get() to snapshot.brandModelLabel,
                    "SOC" to snapshot.socModel,
                    ManagementTexts.DeviceInfo.ANDROID_VERSION.get() to snapshot.androidVersionLabel,
                    ManagementTexts.DeviceInfo.UPTIME.get() to snapshot.uptime,
                    ManagementTexts.DeviceInfo.BASEBAND.get() to snapshot.basebandVersion,
                    ManagementTexts.DeviceInfo.PRODUCT_CODENAME.get() to snapshot.productCodeName,
                    ManagementTexts.DeviceInfo.SECURITY_PATCH.get() to snapshot.securityPatch,
                    ManagementTexts.DeviceInfo.SERIAL_NUMBER.get() to snapshot.serialNumber,
                    ManagementTexts.DeviceInfo.CPU.get() to snapshot.cpuSummary,
                    "ABI" to snapshot.abi,
                    ManagementTexts.DeviceInfo.BOARD.get() to snapshot.board,
                ),
        )

        SessionManagementInfoGroup(
            title = ManagementTexts.DeviceInfo.DISPLAY_POWER.get(),
            items =
                listOf(
                    ManagementTexts.DeviceInfo.DISPLAY.get() to formatDisplaySummary(
                        snapshot.resolution,
                        snapshot.refreshRate
                    ),
                    ManagementTexts.DeviceInfo.SCREEN.get() to formatScreenMetricsSummary(
                        snapshot.dpi,
                        snapshot.ppi,
                        snapshot.screenSize
                    ),
                    ManagementTexts.DeviceInfo.REFRESH_RATES.get() to snapshot.supportedRefreshRates,
                    ManagementTexts.DeviceInfo.BATTERY.get() to formatBatterySummary(
                        snapshot.batteryHealth,
                        snapshot.voltage,
                        snapshot.currentNow
                    ),
                    ManagementTexts.DeviceInfo.STATUS.get() to
                        formatBatteryStatusSummary(snapshot.batteryStatus, snapshot.batteryLevel, snapshot.temperature),
                    ManagementTexts.DeviceInfo.CYCLE_COUNT.get() to snapshot.batteryCycleCount,
                ),
        )

        SessionManagementInfoGroup(
            title = ManagementTexts.DeviceInfo.STORAGE_MEMORY.get(),
            items =
                listOf(
                    ManagementTexts.DeviceInfo.STORAGE.get() to snapshot.storageSummary,
                    ManagementTexts.DeviceInfo.MEMORY.get() to formatMemorySummary(
                        snapshot.memoryAvailable,
                        snapshot.memoryTotal
                    ),
                ),
        )

        SessionManagementInfoGroup(
            title = ManagementTexts.DeviceInfo.NETWORK.get(),
            items =
                listOf(
                    ManagementTexts.DeviceInfo.CELLULAR.get() to formatMobileBandSummary(
                        snapshot.mobileNetworkType,
                        snapshot.mobileBand
                    ),
                    ManagementTexts.DeviceInfo.CARRIER.get() to snapshot.carrierNames,
                    "PCI" to snapshot.mobilePci,
                    "EARFCN" to snapshot.mobileEarfcn,
                    "RSRP" to snapshot.rsrp,
                    "RSRQ" to snapshot.rsrq,
                    "SINR" to snapshot.sinr,
                    ManagementTexts.DeviceInfo.WI_FI_SSID.get() to snapshot.wifiSsid,
                    ManagementTexts.DeviceInfo.WI_FI_INFO.get() to formatWifiSummary(
                        snapshot.wifiFrequency,
                        snapshot.wifiLinkSpeed
                    ),
                    "BSSID" to snapshot.wifiBssid,
                ) +
                    snapshot.ipv4Interfaces.map { networkInterfaceLabel(it.name) to it.address } +
                    listOf(ManagementTexts.DeviceInfo.DEFAULT_GATEWAY.get() to snapshot.defaultGateway),
        )

        if (snapshot.fingerprint.isNotBlank()) {
            SessionManagementInfoGroup(
                title = ManagementTexts.DeviceInfo.SYSTEM_IDENTITY.get(),
                items = listOf("Fingerprint" to snapshot.fingerprint),
            )
        }
    }
}

private fun networkInterfaceLabel(name: String): String =
    when {
        name.matches(Regex("""(?:wlan|wifi)\d+""", RegexOption.IGNORE_CASE)) ->
            ManagementTexts.DeviceInfo.WI_FI_IP.get()

        name.matches(Regex("""(?:ap|softap|swlan)\d*""", RegexOption.IGNORE_CASE)) ->
            ManagementTexts.DeviceInfo.HOTSPOT_IP.get()

        name.matches(Regex("""(?:rndis|usb)\d*""", RegexOption.IGNORE_CASE)) ->
            ManagementTexts.DeviceInfo.USB_TETHERING_IP.get()

        else -> ManagementTexts.DeviceInfo.INTERFACE_IP.get()
    }

@Composable
private fun SessionManagementHomeLoadingSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SessionManagementInfoLoadingGroup(
            title = ManagementTexts.DeviceInfo.SYSTEM_HARDWARE.get(),
            labels =
                listOf(
                    ManagementTexts.DeviceInfo.BRAND_MODEL.get(),
                    "SOC",
                    ManagementTexts.DeviceInfo.ANDROID_VERSION.get(),
                    ManagementTexts.DeviceInfo.UPTIME.get(),
                    ManagementTexts.DeviceInfo.BASEBAND.get(),
                    ManagementTexts.DeviceInfo.PRODUCT_CODENAME.get(),
                    ManagementTexts.DeviceInfo.SECURITY_PATCH.get(),
                    ManagementTexts.DeviceInfo.SERIAL_NUMBER.get(),
                    ManagementTexts.DeviceInfo.CPU.get(),
                    "ABI",
                    ManagementTexts.DeviceInfo.BOARD.get(),
                ),
        )
        SessionManagementInfoLoadingGroup(
            title = ManagementTexts.DeviceInfo.DISPLAY_POWER.get(),
            labels = listOf(
                ManagementTexts.DeviceInfo.DISPLAY.get(),
                ManagementTexts.DeviceInfo.SCREEN.get(),
                ManagementTexts.DeviceInfo.REFRESH_RATES.get(),
                ManagementTexts.DeviceInfo.BATTERY.get(),
                ManagementTexts.DeviceInfo.STATUS.get(),
                ManagementTexts.DeviceInfo.CYCLE_COUNT.get(),
            ),
        )
        SessionManagementInfoLoadingGroup(
            title = ManagementTexts.DeviceInfo.STORAGE_MEMORY.get(),
            labels = listOf(ManagementTexts.DeviceInfo.STORAGE.get(), ManagementTexts.DeviceInfo.MEMORY.get()),
        )
        SessionManagementInfoLoadingGroup(
            title = ManagementTexts.DeviceInfo.NETWORK.get(),
            labels =
                listOf(
                    ManagementTexts.DeviceInfo.CELLULAR.get(),
                    ManagementTexts.DeviceInfo.CARRIER.get(),
                    "PCI",
                    "EARFCN",
                    "RSRP",
                    "RSRQ",
                    "SINR",
                    ManagementTexts.DeviceInfo.WI_FI_SSID.get(),
                    ManagementTexts.DeviceInfo.WIFI_INFO_SECTION.get(),
                    "BSSID",
                    ManagementTexts.DeviceInfo.NETWORK_INTERFACE_IP.get(),
                    ManagementTexts.DeviceInfo.DEFAULT_GATEWAY.get(),
                ),
        )
    }
}

@Composable
private fun SessionManagementInfoLoadingGroup(
    title: String,
    labels: List<String>,
) {
    SectionCard(
        title = title,
        elevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            labels.forEachIndexed { index, label ->
                SessionManagementInfoPlaceholderRow(label = label)
                if (index != labels.lastIndex) {
                    AppDivider(modifier = Modifier.padding(start = 104.dp))
                }
            }
        }
    }
}

@Composable
private fun SessionManagementInfoGroup(
    title: String,
    items: List<Pair<String, String>>,
) {
    val visibleItems = items.filter { it.second.isNotBlank() }
    if (visibleItems.isEmpty()) {
        return
    }

    SectionCard(
        title = title,
        elevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            visibleItems.forEachIndexed { index, (label, value) ->
                SessionManagementInfoRow(
                    label = label,
                    value = value,
                    valueMaxLines = 1,
                )
                if (index != visibleItems.lastIndex) {
                    AppDivider(modifier = Modifier.padding(start = 104.dp))
                }
            }
        }
    }
}

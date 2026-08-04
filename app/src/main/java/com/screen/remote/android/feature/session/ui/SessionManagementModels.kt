package com.screen.remote.android.feature.session.ui

import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.i18n.ManagementTexts
import com.screen.remote.android.core.i18n.TextPair
import java.io.File

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
    val ipv4Interfaces: List<DeviceIpv4Interface>,
    val defaultGateway: String,
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
                .ifBlank { model.ifBlank { ManagementTexts.General.UNKNOWN_DEVICE.get() } }

    val androidVersionLabel: String
        get() = androidVersion.ifBlank { "Android" }

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
                model = sessionData.name.ifBlank { ManagementTexts.General.DEVICE.get() },
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
                ipv4Interfaces = emptyList(),
                defaultGateway = "",
                wifiFrequency = "",
                wifiLinkSpeed = "",
                supportedRefreshRates = "",
                batteryCycleCount = "",
                wirelessDebugPort = null,
            )
    }
}

internal fun managementConnectionEndpoint(
    sessionData: SessionData,
    activeDeviceId: String?,
): String =
    activeDeviceId
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: sessionData.primaryConnectionEndpointForDisplay()

internal data class DeviceIpv4Interface(
    val name: String,
    val address: String,
)

internal data class ManagementProgressDialogState(
    val title: String,
    val message: String,
)

internal data class ManagementResultDialogState(
    val title: String,
    val message: String,
    val isSuccess: Boolean,
)

internal data class ManagementValueInputDialogState(
    val title: String,
    val label: String,
    val initialValue: String,
    val confirmText: String,
    val placeholder: String? = null,
)

internal data class ResolutionDialogState(
    val width: String,
    val height: String,
)

internal data class AnimationScaleDialogState(
    val windowScale: String,
    val transitionScale: String,
    val durationScale: String,
)

internal data class ScreenshotPreviewState(
    val file: File,
)

internal data class ActivationTarget(
    val labelText: TextPair,
    val packageName: String,
    val command: String,
) {
    val label: String
        get() = labelText.get()
}

internal enum class StandbyAction(
    private val labelText: TextPair,
    val command: String,
) {
    Sleep(ManagementTexts.General.SLEEP, "input keyevent 223"),
    Wake(ManagementTexts.General.WAKE, "input keyevent 224");

    val label: String
        get() = labelText.get()
}

internal enum class RebootMode(
    private val labelText: TextPair,
    val command: String,
) {
    Normal(ManagementTexts.General.RESTART, "reboot"),
    PowerOff(ManagementTexts.General.POWER_OFF, "reboot -p"),
    Recovery(ManagementTexts.General.RECOVERY, "reboot recovery"),
    Fastboot(ManagementTexts.General.FASTBOOT, "reboot bootloader");

    val label: String
        get() = labelText.get()
}

internal data class RemoteFileEntry(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long?,
    val detail: String,
)

internal data class RemoteTextEditorState(
    val entry: RemoteFileEntry,
    val localFile: File,
    val content: String,
)

internal data class RemotePreparedFileState(
    val entry: RemoteFileEntry,
    val localFile: File,
)

internal data class RemoteBinaryPreviewState(
    val entry: RemoteFileEntry,
    val localFile: File,
    val preview: String,
)

internal data class RemoteFileDetailSnapshot(
    val isLoading: Boolean,
    val name: String,
    val fullPath: String,
    val typeLabel: String,
    val permissions: String,
    val owner: String,
    val group: String,
    val sizeLabel: String,
    val modifiedTime: String,
    val errorMessage: String? = null,
) {
    companion object {
        fun loading(entry: RemoteFileEntry): RemoteFileDetailSnapshot =
            RemoteFileDetailSnapshot(
                isLoading = true,
                name = entry.name,
                fullPath = entry.fullPath,
                typeLabel = remoteFileTypeLabel(entry),
                permissions = "--",
                owner = "--",
                group = "--",
                sizeLabel = "--",
                modifiedTime = "--",
            )
    }
}

internal sealed interface RemoteOverwriteConfirmState {
    val title: String
    val message: String

    data class PushBack(
        val entry: RemoteFileEntry,
    ) : RemoteOverwriteConfirmState {
        override val title: String = ManagementTexts.General.PUSH_LOCAL_COPY_BACK.get()
        override val message: String = ManagementTexts.General.WILL_OVERWRITE_DEVICE.format(entry.fullPath)
    }
}

internal data class FileBrowserSnapshot(
    val isLoading: Boolean,
    val currentPath: String,
    val entries: List<RemoteFileEntry>,
    val errorMessage: String? = null,
) {
    companion object {
        fun loading(path: String): FileBrowserSnapshot =
            FileBrowserSnapshot(
                isLoading = true,
                currentPath = path,
                entries = emptyList(),
            )
    }
}

internal fun supportedActivationTargets(installedPackages: Set<String>): List<ActivationTarget> =
    listOfNotNull(
        activationTargetIfInstalled(
            labelText = ManagementTexts.General.SHIZUKU,
            packageName = "moe.shizuku.privileged.api",
            command = "$(pm path moe.shizuku.privileged.api | sed \"s#package:##;s#/base.apk#/lib/$(getprop ro.product.cpu.abi | sed \"s/arm64-v8a/arm64/\")/libshizuku.so#\")",
            installedPackages = installedPackages,
        ),
        activationTargetIfInstalled(
            labelText = ManagementTexts.General.BREVENT,
            packageName = "me.piebridge.brevent",
            command = "sh /data/data/me.piebridge.brevent/brevent.sh",
            installedPackages = installedPackages,
        ),
        activationTargetIfInstalled(
            labelText = ManagementTexts.General.ICE_BOX,
            packageName = "com.catchingnow.icebox",
            command = "sh /storage/emulated/0/Android/data/com.catchingnow.icebox/files/start.sh",
            installedPackages = installedPackages,
        ),
    ).ifEmpty {
        listOf(
            ActivationTarget(
                labelText = ManagementTexts.General.SHIZUKU,
                packageName = "moe.shizuku.privileged.api",
                command = "",
            ),
            ActivationTarget(
                labelText = ManagementTexts.General.BREVENT,
                packageName = "me.piebridge.brevent",
                command = "",
            ),
            ActivationTarget(
                labelText = ManagementTexts.General.ICE_BOX,
                packageName = "com.catchingnow.icebox",
                command = "",
            ),
        )
    }

private fun activationTargetIfInstalled(
    labelText: TextPair,
    packageName: String,
    command: String,
    installedPackages: Set<String>,
): ActivationTarget =
    ActivationTarget(
        labelText = labelText,
        packageName = packageName,
        command = if (packageName in installedPackages) command else "",
    )

package com.mobile.scrcpy.android.debug

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.mobile.scrcpy.android.app.ScreenRemoteApp
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.domain.model.ScrcpyOptions
import com.mobile.scrcpy.android.infrastructure.adb.usb.UsbDadb
import com.mobile.scrcpy.android.infrastructure.scrcpy.client.ScrcpyClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID

internal object DebugUsbAdbCommands {
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val scrcpyClient by lazy {
        ScrcpyClient(ScreenRemoteApp.instance, ScreenRemoteApp.instance.adbConnectionManager)
    }

    suspend fun handleIntent(intent: Intent) {
        val command = intent.getStringExtra(DebugUsbAdbReceiver.EXTRA_COMMAND)?.ifBlank { null } ?: DebugUsbAdbReceiver.COMMAND_DIAG
        val manager = ScreenRemoteApp.instance.adbConnectionManager
        log("command start: $command")

        when (command) {
            DebugUsbAdbReceiver.COMMAND_SCAN -> {
                val devices = manager.scanUsbDevices().getOrThrow()
                if (devices.isEmpty()) {
                    log("scan: no adb usb devices detected")
                } else {
                    devices.forEach { device ->
                        log(
                            "scan: device=${device.device.deviceName} product=${device.productName} " +
                                "manufacturer=${device.manufacturerName} serial=${device.serialNumber} permission=${device.hasPermission}",
                        )
                    }
                }
            }

            DebugUsbAdbReceiver.COMMAND_PERMISSION -> {
                val device = resolveUsbDevice(intent) ?: return
                val granted = manager.requestUsbPermission(device.device).getOrThrow()
                log("permission: device=${device.device.deviceName} granted=$granted")
            }

            DebugUsbAdbReceiver.COMMAND_CONNECT -> {
                val (_, deviceId) = ensureConnected(manager, intent)
                log("connect: deviceId=$deviceId")
            }

            DebugUsbAdbReceiver.COMMAND_SHELL -> {
                val (device, deviceId) = ensureConnected(manager, intent)
                val shellCommand =
                    intent.getStringExtra(DebugUsbAdbReceiver.EXTRA_SHELL_COMMAND)?.ifBlank { null }
                        ?: "getprop ro.serialno"
                val connection =
                    manager.getConnection(deviceId)
                        ?: error("connection missing after connect: $deviceId")
                val output = connection.executeShell(shellCommand).getOrThrow().trimEnd()
                log(
                    "shell: device=${device.device.deviceName} deviceId=$deviceId command=$shellCommand output=${output.ifBlank { "<empty>" }}",
                )
            }

            DebugUsbAdbReceiver.COMMAND_TCP_FORWARD -> {
                val (device, deviceId) = ensureConnected(manager, intent)
                val localPort = intent.getIntExtra(DebugUsbAdbReceiver.EXTRA_LOCAL_PORT, -1)
                val remotePort = intent.getIntExtra(DebugUsbAdbReceiver.EXTRA_REMOTE_PORT, -1)
                require(localPort > 0) { "local_port must be > 0" }
                require(remotePort > 0) { "remote_port must be > 0" }

                val connection =
                    manager.getConnection(deviceId)
                        ?: error("connection missing after connect: $deviceId")
                connection.setupPortForward(localPort, remotePort).getOrThrow()
                log(
                    "tcp_forward: device=${device.device.deviceName} deviceId=$deviceId localPort=$localPort remotePort=$remotePort success=true",
                )
            }

            DebugUsbAdbReceiver.COMMAND_SHELL_ASYNC -> {
                val (device, deviceId) = ensureConnected(manager, intent)
                val shellCommand =
                    intent.getStringExtra(DebugUsbAdbReceiver.EXTRA_SHELL_COMMAND)?.ifBlank { null }
                        ?: error("shell_command is required")
                val connection =
                    manager.getConnection(deviceId)
                        ?: error("connection missing after connect: $deviceId")
                connection.executeShellAsync(shellCommand)
                log(
                    "shell_async: device=${device.device.deviceName} deviceId=$deviceId command=$shellCommand dispatched=true",
                )
            }

            DebugUsbAdbReceiver.COMMAND_START_TEST_TCP_LISTENER -> {
                val (device, deviceId) = ensureConnected(manager, intent)
                val remotePort = intent.getIntExtra(DebugUsbAdbReceiver.EXTRA_REMOTE_PORT, -1)
                require(remotePort > 0) { "remote_port must be > 0" }
                val connection =
                    manager.getConnection(deviceId)
                        ?: error("connection missing after connect: $deviceId")
                val shellCommand =
                    "sh -c 'echo FORWARD_OK | toybox nc -l -p $remotePort'"
                connection.executeShellAsync(shellCommand)
                log(
                    "start_test_tcp_listener: device=${device.device.deviceName} deviceId=$deviceId remotePort=$remotePort dispatched=true",
                )
            }

            DebugUsbAdbReceiver.COMMAND_ADB_FORWARD -> {
                val (device, deviceId) = ensureConnected(manager, intent)
                val localPort = intent.getIntExtra(DebugUsbAdbReceiver.EXTRA_LOCAL_PORT, -1)
                val socketName = intent.getStringExtra(DebugUsbAdbReceiver.EXTRA_SOCKET_NAME)?.ifBlank { null }
                require(localPort > 0) { "local_port must be > 0" }
                require(!socketName.isNullOrBlank()) { "socket_name is required" }

                val connection =
                    manager.getConnection(deviceId)
                        ?: error("connection missing after connect: $deviceId")
                connection.setupAdbForward(localPort, socketName).getOrThrow()
                val forwardReachable = connection.checkAdbForward(localPort)
                log(
                    "adb_forward: device=${device.device.deviceName} deviceId=$deviceId localPort=$localPort socketName=$socketName reachable=$forwardReachable",
                )
            }

            DebugUsbAdbReceiver.COMMAND_DISCONNECT -> {
                val selected = resolveUsbDevice(intent) ?: return
                val deviceId = buildUsbDeviceId(selected)
                val result = manager.disconnectDevice(deviceId).getOrThrow()
                log("disconnect: device=${selected.device.deviceName} deviceId=$deviceId result=$result")
            }

            DebugUsbAdbReceiver.COMMAND_DIAG -> {
                val (device, deviceId) = ensureConnected(manager, intent)
                val connection =
                    manager.getConnection(deviceId)
                        ?: error("connection missing after connect: $deviceId")
                val shellCommand =
                    intent.getStringExtra(DebugUsbAdbReceiver.EXTRA_SHELL_COMMAND)?.ifBlank { null }
                        ?: "getprop ro.serialno; getprop ro.product.model"
                val output = connection.executeShell(shellCommand).getOrThrow().trimEnd()
                log(
                    "diag: device=${device.device.deviceName} deviceId=$deviceId shellOutput=${output.ifBlank { "<empty>" }}",
                )
            }

            DebugUsbAdbReceiver.COMMAND_DIAG_LEGACY -> {
                val device = resolveUsbDevice(intent) ?: error("no matching usb adb device found")
                if (!device.hasPermission) {
                    val granted = manager.requestUsbPermission(device.device).getOrThrow()
                    require(granted) { "usb permission denied for ${device.device.deviceName}" }
                }

                val keyPair =
                    manager.getKeyPair()
                        ?: error("adb key pair not initialized")
                val usbManager = ScreenRemoteApp.instance.getSystemService(Context.USB_SERVICE) as UsbManager
                val deviceId = buildUsbDeviceId(device)

                UsbDadb(
                    usbManager = usbManager,
                    usbDevice = device.device,
                    keyPair = keyPair,
                    deviceId = deviceId,
                ).use { dadb ->
                    val shellCommand =
                        intent.getStringExtra(DebugUsbAdbReceiver.EXTRA_SHELL_COMMAND)?.ifBlank { null }
                            ?: "getprop ro.serialno; getprop ro.product.model"
                    val output = dadb.shell(shellCommand).allOutput.trimEnd()
                    log(
                        "diag_legacy: device=${device.device.deviceName} deviceId=$deviceId shellOutput=${output.ifBlank { "<empty>" }}",
                    )
                }
            }

            DebugUsbAdbReceiver.COMMAND_START_SCRCPY -> {
                val commandIntent = Intent(intent)
                backgroundScope.launch {
                    try {
                        val (device, deviceId) = ensureConnected(manager, commandIntent)
                        runCatching { scrcpyClient.disconnect() }

                        val options =
                            ScrcpyOptions(
                                sessionId = "debug-usb-${UUID.randomUUID()}",
                                host = deviceId,
                                port = 0,
                                maxSize = 1920,
                                videoBitRate = 8_000_000,
                                maxFps = 60,
                                preferredVideoCodec = "h264",
                                enableAudio = false,
                                stayAwake = false,
                                turnScreenOff = false,
                            )

                        val result =
                            scrcpyClient.connect(
                                sessionId = options.sessionId,
                                options = options,
                            )
                        val error = result.exceptionOrNull()?.message
                        log(
                            "start_scrcpy: device=${device.device.deviceName} deviceId=$deviceId success=${result.isSuccess} error=${error ?: "<none>"}",
                        )
                    } catch (t: Throwable) {
                        log("start_scrcpy failed: ${t.message}", t)
                    }
                }
                log("start_scrcpy: dispatched=true")
            }

            else -> {
                log("unknown command: $command")
            }
        }

        log("command finish: $command")
    }

    @JvmStatic
    fun handleActivityLaunch(activity: ComponentActivity) {
        val command =
            activity.intent.getStringExtra(EXTRA_STARTUP_COMMAND)
                ?.ifBlank { null }
                ?: return

        if (activity.intent.getBooleanExtra(EXTRA_STARTUP_CONSUMED, false)) {
            return
        }
        activity.intent.putExtra(EXTRA_STARTUP_CONSUMED, true)
        activity.intent.putExtra(DebugUsbAdbReceiver.EXTRA_COMMAND, command)

        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                handleIntent(activity.intent)
            } catch (t: Throwable) {
                log("startup command failed: ${t.message}", t)
            }
        }
    }

    private suspend fun ensureConnected(
        manager: com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnectionManager,
        intent: Intent,
    ): Pair<com.mobile.scrcpy.android.infrastructure.adb.usb.UsbDeviceInfo, String> {
        val device = resolveUsbDevice(intent) ?: error("no matching usb adb device found")
        log(
            "ensureConnected: device=${device.device.deviceName} product=${device.productName} permission=${device.hasPermission}",
        )

        if (!device.hasPermission) {
            log("ensureConnected: requesting usb permission for ${device.device.deviceName}")
            val granted = manager.requestUsbPermission(device.device).getOrThrow()
            require(granted) { "usb permission denied for ${device.device.deviceName}" }
            log("ensureConnected: usb permission granted for ${device.device.deviceName}")
        }

        log("ensureConnected: connecting usb adb for ${device.device.deviceName}")
        val deviceId =
            withTimeout(DebugUsbAdbReceiver.CONNECT_TIMEOUT_MS) {
                manager.connectUsbDevice(device.device, device.getDisplayName()).getOrThrow()
            }
        log("ensureConnected: connected deviceId=$deviceId")
        return device to deviceId
    }

    private suspend fun resolveUsbDevice(intent: Intent): com.mobile.scrcpy.android.infrastructure.adb.usb.UsbDeviceInfo? {
        val selector = intent.getStringExtra(DebugUsbAdbReceiver.EXTRA_DEVICE_MATCH)?.trim().orEmpty()
        val devices =
            withContext(Dispatchers.IO) {
                ScreenRemoteApp.instance.adbConnectionManager.scanUsbDevices().getOrThrow()
            }
        if (devices.isEmpty()) {
            log("resolve: no adb usb devices detected")
            return null
        }
        if (selector.isBlank()) {
            return devices.first()
        }
        return devices.firstOrNull { device ->
            device.device.deviceName.contains(selector, ignoreCase = true) ||
                device.productName.contains(selector, ignoreCase = true) ||
                device.manufacturerName.contains(selector, ignoreCase = true) ||
                device.serialNumber.contains(selector, ignoreCase = true)
        } ?: run {
            log("resolve: no usb device matches selector=$selector")
            null
        }
    }

    private fun buildUsbDeviceId(device: com.mobile.scrcpy.android.infrastructure.adb.usb.UsbDeviceInfo): String {
        val serial = device.serialNumber.ifBlank { device.device.deviceName }
        return "usb:$serial"
    }

    private fun log(
        message: String,
        throwable: Throwable? = null,
    ) {
        if (throwable == null) {
            LogManager.d(DebugUsbAdbReceiver.TAG, message)
        } else {
            LogManager.e(DebugUsbAdbReceiver.TAG, message, throwable)
        }
    }

    private const val EXTRA_STARTUP_COMMAND = "debug_usb_command"
    private const val EXTRA_STARTUP_CONSUMED = "debug_usb_command_consumed"
}

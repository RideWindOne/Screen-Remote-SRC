package com.screen.remote.android.feature.session.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.screen.remote.android.core.common.constants.IosDesignTokens
import com.screen.remote.android.core.common.util.DeviceTransportSerial
import com.screen.remote.android.core.common.util.formatHostPort
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.designsystem.component.CompactSegmentedActionButton
import com.screen.remote.android.core.designsystem.component.DialogPage
import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.core.i18n.SessionTexts
import com.screen.remote.android.infrastructure.adb.discovery.NearbyAdbProtocol
import com.screen.remote.android.infrastructure.adb.discovery.NearbyAdbScanEvent
import com.screen.remote.android.infrastructure.adb.discovery.NearbyAdbScanProgress
import com.screen.remote.android.infrastructure.adb.discovery.NearbyAdbScanStage
import com.screen.remote.android.infrastructure.adb.discovery.NearbyAdbScanner
import com.screen.remote.android.infrastructure.adb.discovery.NearbyTcpAdbDevice
import com.screen.remote.android.infrastructure.adb.mdns.MdnsDiscoveredConnectService
import com.screen.remote.android.infrastructure.adb.mdns.MdnsDiscoveredTcpService
import com.screen.remote.android.infrastructure.adb.mdns.MdnsSessionDiscoveryManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
internal class NearbyAdbScanController(
    context: Context,
    private val scope: CoroutineScope,
    private val scanner: NearbyAdbScanner,
    internal val mdnsManager: MdnsSessionDiscoveryManager,
) {
    var scanSessionActive by mutableStateOf(false)
        private set
    var scanStarted by mutableStateOf(false)
        private set
    var tcpDevices by mutableStateOf<List<NearbyTcpAdbDevice>>(emptyList())
        private set
    var progress by mutableStateOf(NearbyAdbScanProgress(NearbyAdbScanStage.DISCOVERING_HOSTS))
        private set
    var scanError by mutableStateOf<String?>(null)
        private set
    var scanNotice by mutableStateOf<String?>(null)
        private set

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var sessions: List<SessionData> = emptyList()
    private var scanJob: Job? = null
    private var scanNetworkSnapshot: ScanNetworkSnapshot? = null
    private var networkMonitoring = false
    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = checkNetworkChange()

            override fun onLost(network: Network) {
                scope.launch {
                    if (scanNetworkSnapshot?.network == network) {
                        interruptForNetworkChange()
                    }
                }
            }

            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: LinkProperties,
            ) = checkNetworkChange()
        }

    fun startNetworkMonitoring() {
        if (networkMonitoring) return
        networkMonitoring =
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    connectivityManager.registerDefaultNetworkCallback(networkCallback)
                } else {
                    val request =
                        NetworkRequest
                            .Builder()
                            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            .build()
                    connectivityManager.registerNetworkCallback(request, networkCallback)
                }
                true
            }.getOrDefault(false)
    }

    fun updateSessions(value: List<SessionData>) {
        sessions = value
    }

    fun ensureStarted() {
        if (!scanStarted) refresh()
    }

    fun refresh() {
        scanSessionActive = true
        scanStarted = true
        scanJob?.cancel()
        tcpDevices = emptyList()
        scanError = null
        scanNotice = null
        progress = NearbyAdbScanProgress(NearbyAdbScanStage.DISCOVERING_HOSTS)
        scanNetworkSnapshot = connectivityManager.currentScanNetworkSnapshot()
        val sessionSnapshot = sessions
        scanJob =
            scope.launch {
                scanner
                    .scan(sessions = sessionSnapshot)
                    .collect { event ->
                        when (event) {
                            is NearbyAdbScanEvent.DeviceFound -> {
                                tcpDevices =
                                    (tcpDevices + event.device)
                                        .distinctBy { formatHostPort(it.host, it.port) }
                                        .sortedWith(compareBy(NearbyTcpAdbDevice::host, NearbyTcpAdbDevice::port))
                            }

                            is NearbyAdbScanEvent.Progress -> progress = event.value
                            is NearbyAdbScanEvent.Failed -> scanError = event.message
                        }
                    }
            }
    }

    fun dispose() {
        scanJob?.cancel()
        scanJob = null
        scanNetworkSnapshot = null
        if (networkMonitoring) {
            runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
            networkMonitoring = false
        }
    }

    private fun checkNetworkChange() {
        scope.launch {
            val baseline = scanNetworkSnapshot ?: return@launch
            if (scanJob?.isActive == true && connectivityManager.currentScanNetworkSnapshot() != baseline) {
                interruptForNetworkChange()
            }
        }
    }

    private fun interruptForNetworkChange() {
        if (scanJob?.isActive != true) return
        scanJob?.cancel()
        scanJob = null
        scanNetworkSnapshot = null
        progress = NearbyAdbScanProgress(NearbyAdbScanStage.COMPLETE)
        scanNotice = SessionTexts.MAIN_SCAN_NETWORK_CHANGED.get()
    }
}

private data class ScanNetworkSnapshot(
    val network: Network?,
    val localAddresses: Set<String>,
)

private fun ConnectivityManager.currentScanNetworkSnapshot(): ScanNetworkSnapshot {
    val network = activeNetwork
    val addresses =
        network
            ?.let(::getLinkProperties)
            ?.linkAddresses
            ?.mapTo(linkedSetOf()) { it.address.hostAddress.orEmpty() }
            .orEmpty()
    return ScanNetworkSnapshot(network = network, localAddresses = addresses)
}

@Composable
internal fun rememberNearbyAdbScanController(sessions: List<SessionData>): NearbyAdbScanController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mdnsManager = remember { MdnsSessionDiscoveryManager.get() }
    val controller =
        remember(context, scope, mdnsManager) {
            NearbyAdbScanController(
                context = context,
                scope = scope,
                scanner = NearbyAdbScanner(context),
                mdnsManager = mdnsManager,
            )
        }
    SideEffect(controller::startNetworkMonitoring)

    SideEffect {
        controller.updateSessions(sessions)
    }

    DisposableEffect(controller, controller.scanSessionActive) {
        val discoveryLease =
            if (controller.scanSessionActive) {
                controller.mdnsManager.acquireInteractiveDiscovery()
            } else {
                null
            }
        onDispose { discoveryLease?.close() }
    }

    DisposableEffect(controller) {
        onDispose(controller::dispose)
    }
    return controller
}

@Composable
internal fun NearbyAdbDevicesButton(
    controller: NearbyAdbScanController,
    onPairingRequired: (host: String, port: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mdnsState by controller.mdnsManager.state.collectAsState()
    var showDevices by remember { mutableStateOf(false) }
    val openScanner = {
        showDevices = true
        controller.ensureStarted()
    }
    val localNetworkPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                openScanner()
            } else {
                Toast.makeText(
                    context,
                    SessionTexts.MAIN_LOCAL_NETWORK_PERMISSION_REQUIRED.get(),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    val requestScanner = {
        if (Build.VERSION.SDK_INT < 37 ||
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_LOCAL_NETWORK,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            openScanner()
        } else {
            localNetworkPermissionLauncher.launch(android.Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }

    CompactSegmentedActionButton(
        imageVector = Icons.Default.Search,
        contentDescription = SessionTexts.MAIN_SCAN_NEARBY_DEVICES.get(),
        onClick = requestScanner,
        modifier = modifier,
    )

    if (showDevices) {
        NearbyAdbDevicesDialog(
            mdnsServices = mdnsState.connectServices,
            mdnsTcpServices = mdnsState.tcpServices,
            tcpDevices = controller.tcpDevices,
            progress = controller.progress,
            scanError = controller.scanError,
            scanNotice = controller.scanNotice,
            onDismiss = { showDevices = false },
            onRefresh = controller::refresh,
            onPairingRequired = {
                showDevices = false
                onPairingRequired(it.host, it.port)
            },
        )
    }
}

@Composable
private fun NearbyAdbDevicesDialog(
    mdnsServices: List<MdnsDiscoveredConnectService>,
    mdnsTcpServices: List<MdnsDiscoveredTcpService>,
    tcpDevices: List<NearbyTcpAdbDevice>,
    progress: NearbyAdbScanProgress,
    scanError: String?,
    scanNotice: String?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onPairingRequired: (MdnsDiscoveredConnectService) -> Unit,
) {
    val context = LocalContext.current
    val mdnsTlsEndpoints =
        mdnsServices
            .asSequence()
            .filterNot(MdnsDiscoveredConnectService::requiresPairing)
            .map { service -> formatHostPort(service.host, service.port).lowercase() }
            .toSet()
    val allTcpDevices =
        (mdnsTcpServices.map { service ->
            NearbyTcpAdbDevice(
                host = service.host,
                port = service.port,
                protocol = NearbyAdbProtocol.TCP,
            )
        } + tcpDevices)
            .filterNot { device ->
                formatHostPort(device.host, device.port).lowercase() in mdnsTlsEndpoints
            }.distinctBy { formatHostPort(it.host, it.port).lowercase() }
    val hasDevices = mdnsServices.isNotEmpty() || allTcpDevices.isNotEmpty()

    DialogPage(
        title = SessionTexts.MAIN_NEARBY_ADB_DEVICES.get(),
        onDismiss = onDismiss,
        showBackButton = false,
        leftButtonText = CommonTexts.BUTTON_CLOSE.get(),
        trailingContent = {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = SessionTexts.MAIN_REFRESH_SCAN.get(),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        enableScroll = true,
        verticalSpacing = IosDesignTokens.compactInlineSpacing,
    ) {
        if (progress.stage != NearbyAdbScanStage.COMPLETE) {
            ScanProgressRow(progress)
        }

        mdnsServices.forEach { service ->
            MdnsAdbDeviceRow(
                service = service,
                onClick = {
                    if (service.requiresPairing) {
                        onPairingRequired(service)
                    } else {
                        copyAddress(
                            context = context,
                            label = "Screen Remote mDNS address",
                            address = DeviceTransportSerial.mdns(service.deviceSerial),
                            confirmation = SessionTexts.MAIN_MDNS_ADDRESS_COPIED.get(),
                        )
                    }
                },
            )
        }

        allTcpDevices.forEach { device ->
            TcpAdbDeviceRow(
                device = device,
                onClick = {
                    copyAddress(
                        context = context,
                        label = "Screen Remote TCP address",
                        address = DeviceTransportSerial.tcp(device.host, device.port),
                        confirmation = SessionTexts.MAIN_TCP_ADDRESS_COPIED.get(),
                    )
                },
            )
        }

        scanError?.let { error ->
            Text(
                text = "${SessionTexts.MAIN_SCAN_FAILED.get()}: $error",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = IosDesignTokens.compactSpacing),
            )
        }

        scanNotice?.let { notice ->
            Text(
                text = notice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = IosDesignTokens.compactSpacing),
            )
        }

        if (progress.stage == NearbyAdbScanStage.COMPLETE && !hasDevices && scanError == null && scanNotice == null) {
            Text(
                text = SessionTexts.MDNS_CONNECT_EMPTY.get(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = IosDesignTokens.standardSpacing),
            )
        }
    }
}

@Composable
private fun ScanProgressRow(progress: NearbyAdbScanProgress) {
    val stageText =
        when (progress.stage) {
            NearbyAdbScanStage.DISCOVERING_HOSTS -> SessionTexts.MAIN_SCAN_DISCOVERING_HOSTS.get()
            NearbyAdbScanStage.CHECKING_HISTORY -> SessionTexts.MAIN_SCAN_CHECKING_HISTORY.get()
            NearbyAdbScanStage.CHECKING_COMMON_PORTS -> SessionTexts.MAIN_SCAN_CHECKING_COMMON_PORTS.get()
            NearbyAdbScanStage.CHECKING_DYNAMIC_PORTS -> SessionTexts.MAIN_SCAN_CHECKING_DYNAMIC_PORTS.get()
            NearbyAdbScanStage.COMPLETE -> SessionTexts.MAIN_SCAN_COMPLETE.get()
        }
    val progressText =
        if (progress.total > 0) {
            SessionTexts.MAIN_SCAN_PROGRESS.format(progress.completed, progress.total)
        } else {
            null
        }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = IosDesignTokens.compactSpacing),
        horizontalArrangement = Arrangement.spacedBy(IosDesignTokens.compactInlineSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(IosDesignTokens.externalIconSize),
            strokeWidth = 2.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stageText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            progressText?.let { value ->
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MdnsAdbDeviceRow(
    service: MdnsDiscoveredConnectService,
    onClick: () -> Unit,
) {
    val status =
        when {
            service.confirming -> SessionTexts.MDNS_DEVICE_CONFIRMING.get()
            service.requiresPairing -> SessionTexts.MAIN_MDNS_PAIRABLE.get()
            else -> SessionTexts.MAIN_MDNS_CONNECTABLE.get()
        }
    NearbyAdbDeviceRow(
        address = DeviceTransportSerial.mdns(service.deviceSerial),
        status = status,
        statusIsError = service.requiresPairing && !service.confirming,
        enabled = !service.confirming,
        onClick = onClick,
    )
}

@Composable
private fun TcpAdbDeviceRow(
    device: NearbyTcpAdbDevice,
    onClick: () -> Unit,
) {
    NearbyAdbDeviceRow(
        address = DeviceTransportSerial.tcp(device.host, device.port),
        status =
            when (device.protocol) {
                NearbyAdbProtocol.TCP -> SessionTexts.MAIN_TCP_ADB.get()
                NearbyAdbProtocol.TLS -> SessionTexts.MAIN_TLS_ADB.get()
            },
        statusIsError = false,
        enabled = true,
        onClick = onClick,
    )
}

@Composable
private fun NearbyAdbDeviceRow(
    address: String,
    status: String,
    statusIsError: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(IosDesignTokens.cardCornerRadius),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.5.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = IosDesignTokens.compactHorizontalPadding,
                        vertical = IosDesignTokens.compactSpacing,
                    ),
            horizontalArrangement = Arrangement.spacedBy(IosDesignTokens.compactInlineSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = address,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (statusIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

private fun copyAddress(
    context: Context,
    label: String,
    address: String,
    confirmation: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, address))
    Toast.makeText(context, confirmation, Toast.LENGTH_SHORT).show()
}

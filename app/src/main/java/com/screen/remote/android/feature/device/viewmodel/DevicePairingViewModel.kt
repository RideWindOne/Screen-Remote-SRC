package com.screen.remote.android.feature.device.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.util.parseHostPort
import com.screen.remote.android.feature.device.data.DeviceInfo
import com.screen.remote.android.feature.device.data.PairingEndpointMetadataManager
import com.screen.remote.android.feature.device.data.PairingHistoryItem
import com.screen.remote.android.feature.device.data.PairingResult
import com.screen.remote.android.feature.device.data.PairingStatus
import com.screen.remote.android.infrastructure.adb.pairing.AdbPairingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设备配对 ViewModel
 *
 * 负责处理设备配对的业务逻辑。
 */
class DevicePairingViewModel : ViewModel() {
    private val _pairingStatus = MutableStateFlow(PairingStatus.IDLE)
    val pairingStatus: StateFlow<PairingStatus> = _pairingStatus.asStateFlow()

    private val _pairingResult = MutableStateFlow<PairingResult?>(null)
    val pairingResult: StateFlow<PairingResult?> = _pairingResult.asStateFlow()

    private val _pairingHistory = MutableStateFlow<List<PairingHistoryItem>>(emptyList())
    val pairingHistory: StateFlow<List<PairingHistoryItem>> = _pairingHistory.asStateFlow()

    /**
     * 加载配对历史
     */
    fun loadPairingHistory(context: Context) {
        viewModelScope.launch {
            try {
                refreshPairingMetadata(context)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_PAIRING, "Failed to load pairing history: ${e.message}", e)
            }
        }
    }

    /**
     * 删除单条配对历史
     */
    fun deletePairingHistoryItem(
        context: Context,
        hostPort: String,
    ) {
        viewModelScope.launch {
            try {
                val endpoint = parseHostPort(hostPort, allowUnbracketedIpv6 = true)?.host ?: hostPort
                withContext(Dispatchers.IO) {
                    PairingEndpointMetadataManager(context).removeEndpoint(endpoint)
                }
                refreshPairingMetadata(context)
                LogManager.d(LogTags.ADB_PAIRING, "Pairing history item deleted: $hostPort")
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_PAIRING, "Failed to delete pairing history item: ${e.message}", e)
            }
        }
    }

    /**
     * 清除配对历史
     */
    fun clearPairingHistory(context: Context) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    PairingEndpointMetadataManager(context).clear()
                }
                _pairingHistory.value = emptyList()
                LogManager.d(LogTags.ADB_PAIRING, "Pairing history cleared")
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_PAIRING, "Failed to clear pairing history: ${e.message}", e)
            }
        }
    }

    /**
     * 使用配对码配对
     */
    fun pairWithCode(
        context: Context,
        ipAddress: String,
        port: String,
        pairingCode: String,
        mdnsDeviceSerial: String? = null,
    ) =
        startPairing(
            context = context,
            ipAddress = ipAddress,
            port = port,
            pairingSecret = pairingCode,
            pairingMethod = "pairing code",
            mdnsDeviceSerial = mdnsDeviceSerial,
        )

    /**
     * 使用二维码中生成的临时密码配对。
     */
    fun pairWithQrCode(
        context: Context,
        ipAddress: String,
        port: String,
        pairingPassword: String,
    ) =
        startPairing(
            context = context,
            ipAddress = ipAddress,
            port = port,
            pairingSecret = pairingPassword,
            pairingMethod = "QR code",
        )

    private fun startPairing(
        context: Context,
        ipAddress: String,
        port: String,
        pairingSecret: String,
        pairingMethod: String,
        mdnsDeviceSerial: String? = null,
    ) {
        viewModelScope.launch {
            try {
                _pairingStatus.value = PairingStatus.CONNECTING
                LogManager.d(
                    LogTags.ADB_PAIRING,
                    "Starting Wireless Debugging pairing: method=$pairingMethod endpoint=$ipAddress:$port",
                )

                // 创建配对管理器
                val pairingManager = AdbPairingManager(context)

                _pairingStatus.value = PairingStatus.PAIRING

                // 执行配对
                val result = pairingManager.pairWithCode(ipAddress, port.toInt(), pairingSecret)

                if (result.isSuccess) {
                    // 配对成功
                    _pairingStatus.value = PairingStatus.SUCCESS
                    _pairingResult.value =
                        PairingResult(
                            success = true,
                            deviceInfo =
                                DeviceInfo(
                                    name = "Android Device",
                                    ipAddress = ipAddress,
                                    adbPort = port.toInt(), // 仅记录本次输入，不再假设 Wireless Debugging 固定为 5555
                                ),
                        )

                    withContext(Dispatchers.IO) {
                        if (mdnsDeviceSerial == null) {
                            PairingEndpointMetadataManager(context).saveSuccessfulPairing(
                                endpoint = ipAddress,
                                port = port,
                                deviceName = "Android Device",
                            )
                        } else {
                            PairingEndpointMetadataManager(context).saveSuccessfulMdnsPairing(
                                deviceSerial = mdnsDeviceSerial,
                                endpoint = ipAddress,
                            )
                        }
                    }

                    refreshPairingMetadata(context)

                    LogManager.d(LogTags.ADB_PAIRING, "Pairing successful")
                } else {
                    // 配对失败
                    throw result.exceptionOrNull() ?: Exception("Unknown error")
                }
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_PAIRING, "Pairing failed", e)
                _pairingStatus.value = PairingStatus.FAILED
                _pairingResult.value =
                    PairingResult(
                        success = false,
                        errorMessage = e.message ?: "Unknown error",
                    )
            }
        }
    }

    /**
     * 重置配对状态
     */
    fun resetPairingStatus() {
        _pairingStatus.value = PairingStatus.IDLE
        _pairingResult.value = null
    }

    private suspend fun refreshPairingMetadata(context: Context) {
        withContext(Dispatchers.IO) {
            val manager = PairingEndpointMetadataManager(context)
            _pairingHistory.value = manager.listRecentSuccessfulPairings()
        }
    }
}

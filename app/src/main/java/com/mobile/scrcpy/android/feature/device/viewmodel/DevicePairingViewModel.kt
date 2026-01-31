package com.mobile.scrcpy.android.feature.device.viewmodel

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.feature.device.data.DeviceInfo
import com.mobile.scrcpy.android.feature.device.data.PairingHistoryItem
import com.mobile.scrcpy.android.feature.device.data.PairingResult
import com.mobile.scrcpy.android.feature.device.data.PairingStatus
import com.mobile.scrcpy.android.infrastructure.adb.pairing.AdbPairingManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 设备配对 ViewModel
 *
 * 负责处理设备配对的业务逻辑（配对码方式）
 */
class DevicePairingViewModel : ViewModel() {
    private val _pairingStatus = MutableStateFlow(PairingStatus.IDLE)
    val pairingStatus: StateFlow<PairingStatus> = _pairingStatus.asStateFlow()

    private val _pairingResult = MutableStateFlow<PairingResult?>(null)
    val pairingResult: StateFlow<PairingResult?> = _pairingResult.asStateFlow()

    private val _pairingHistory = MutableStateFlow<List<PairingHistoryItem>>(emptyList())
    val pairingHistory: StateFlow<List<PairingHistoryItem>> = _pairingHistory.asStateFlow()

    companion object {
        private const val PREFS_NAME = "adb_pairing_prefs"
        private const val KEY_HISTORY = "pairing_history"
        private const val MAX_HISTORY_SIZE = 10
    }

    /**
     * 加载配对历史
     */
    fun loadPairingHistory(context: Context) {
        viewModelScope.launch {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val historyJson = prefs.getString(KEY_HISTORY, null)

                if (historyJson != null) {
                    // 解析 JSON（简单实现，实际项目可使用 Gson/Moshi）
                    val items = parseHistoryJson(historyJson)
                    _pairingHistory.value = items
                }
            } catch (e: Exception) {
                Log.e(LogTags.ADB_PAIRING, "Failed to load pairing history", e)
            }
        }
    }

    /**
     * 保存配对历史
     */
    private fun savePairingHistory(
        context: Context,
        hostPort: String,
    ) {
        viewModelScope.launch {
            try {
                val newItem = PairingHistoryItem(hostPort)
                val currentHistory = _pairingHistory.value.toMutableList()

                // 移除重复项
                currentHistory.removeAll { it.hostPort == hostPort }

                // 添加到开头
                currentHistory.add(0, newItem)

                // 限制历史记录数量
                if (currentHistory.size > MAX_HISTORY_SIZE) {
                    currentHistory.subList(MAX_HISTORY_SIZE, currentHistory.size).clear()
                }

                _pairingHistory.value = currentHistory

                // 保存到 SharedPreferences
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val historyJson = toHistoryJson(currentHistory)
                prefs.edit { putString(KEY_HISTORY, historyJson) }
            } catch (e: Exception) {
                Log.e(LogTags.ADB_PAIRING, "Failed to save pairing history", e)
            }
        }
    }

    /**
     * 清除配对历史
     */
    fun clearPairingHistory(context: Context) {
        viewModelScope.launch {
            try {
                _pairingHistory.value = emptyList()

                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit { remove(KEY_HISTORY) }

                Log.d(LogTags.ADB_PAIRING, "Pairing history cleared")
            } catch (e: Exception) {
                Log.e(LogTags.ADB_PAIRING, "Failed to clear pairing history", e)
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
    ) {
        viewModelScope.launch {
            try {
                _pairingStatus.value = PairingStatus.CONNECTING
                Log.d(LogTags.ADB_PAIRING, "Starting pairing with code: IP=$ipAddress, Port=$port")

                // 创建配对管理器
                val pairingManager = AdbPairingManager(context)

                _pairingStatus.value = PairingStatus.PAIRING

                // 执行配对
                val result = pairingManager.pairWithCode(ipAddress, port.toInt(), pairingCode)

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
                                    adbPort = 5555, // 配对成功后通常使用 5555 端口连接
                                ),
                        )

                    // 保存到历史记录
                    val hostPort = "$ipAddress:$port"
                    savePairingHistory(context, hostPort)

                    Log.d(LogTags.ADB_PAIRING, "Pairing successful")
                } else {
                    // 配对失败
                    throw result.exceptionOrNull() ?: Exception("Unknown error")
                }
            } catch (e: Exception) {
                Log.e(LogTags.ADB_PAIRING, "Pairing failed", e)
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

    /**
     * 解析历史记录 JSON（简单实现）
     */
    private fun parseHistoryJson(json: String): List<PairingHistoryItem> =
        try {
            val items = mutableListOf<PairingHistoryItem>()
            // 格式：hostPort1|timestamp1;hostPort2|timestamp2;...
            json.split(";").forEach { entry ->
                if (entry.isNotEmpty()) {
                    val parts = entry.split("|")
                    if (parts.size == 2) {
                        items.add(PairingHistoryItem(parts[0], parts[1].toLong()))
                    }
                }
            }
            items
        } catch (e: Exception) {
            emptyList()
        }

    /**
     * 转换为历史记录 JSON（简单实现）
     */
    private fun toHistoryJson(items: List<PairingHistoryItem>): String =
        items.joinToString(";") { "${it.hostPort}|${it.timestamp}" }
}

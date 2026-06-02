package com.screen.remote.android.feature.session.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.domain.model.ConnectionCandidate
import com.screen.remote.android.core.domain.model.ConnectionTransport
import com.screen.remote.android.infrastructure.adb.connection.AdbLatencyBenchmark
import com.screen.remote.android.infrastructure.adb.connection.AdbLatencyRoundResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

data class ConnectionLatencyEndpointState(
    val candidate: ConnectionCandidate,
    val label: String,
    val activeRound: Int? = null,
    val completedRounds: Int = 0,
    val connectSamples: List<Double> = emptyList(),
    val shellSamples: List<Double> = emptyList(),
    val failures: Int = 0,
    val resolvedEndpoint: String = "",
    val roundLogs: List<String> = emptyList(),
    val finished: Boolean = false,
)

data class ConnectionLatencyTestState(
    val running: Boolean = false,
    val endpoints: Map<String, ConnectionLatencyEndpointState> = emptyMap(),
    val message: String = "",
)

class ConnectionLatencyTestViewModel(
    private val benchmark: AdbLatencyBenchmark = AdbLatencyBenchmark(),
) : ViewModel() {
    private val _state = MutableStateFlow(ConnectionLatencyTestState())
    val state: StateFlow<ConnectionLatencyTestState> = _state.asStateFlow()

    private var testJob: Job? = null

    fun start(session: SessionData) {
        testJob?.cancel()
        val candidates =
            session
                .toConnectionCandidates()
                .distinctBy(::endpointKey)

        if (candidates.isEmpty()) {
            _state.value =
                ConnectionLatencyTestState(
                    message = "会话没有可测试的 USB、mDNS 或 TCP 地址",
                )
            return
        }

        _state.value =
            ConnectionLatencyTestState(
                running = true,
                endpoints =
                    candidates.associate { candidate ->
                        endpointKey(candidate) to
                            ConnectionLatencyEndpointState(
                                candidate = candidate,
                                label = endpointLabel(candidate),
                            )
                    },
                message = "正在检查 USB 设备和权限…",
            )

        testJob =
            viewModelScope.launch {
                val usbPreparation = benchmark.prepareUsbCandidates(candidates)
                usbPreparation.forEach { (candidate, result) ->
                    result.exceptionOrNull()?.let { error ->
                        markPreparationFailed(
                            candidate,
                            error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName,
                        )
                    }
                }
                val runnableCandidates =
                    candidates.filter { candidate ->
                        candidate.transport != ConnectionTransport.USB || usbPreparation[candidate]?.isSuccess == true
                    }
                _state.update {
                    it.copy(message = "所有地址并行测试；每个地址 10 次重连，每次连接后测试 10 次 shell RTT")
                }
                supervisorScope {
                    runnableCandidates
                        .map { candidate ->
                            launch {
                                repeat(AdbLatencyBenchmark.DEFAULT_CONNECT_ROUNDS) { index ->
                                    markRoundStarting(candidate, index + 1)
                                    val result =
                                        benchmark.runRound(
                                            candidate = candidate,
                                            round = index + 1,
                                            usbDevice = usbPreparation[candidate]?.getOrNull(),
                                        )
                                    recordRound(candidate, result)
                                    delay(150)
                                }
                                markFinished(candidate)
                            }
                        }.joinAll()
                }
                _state.update { it.copy(running = false, message = "测试完成") }
            }
    }

    private fun markPreparationFailed(
        candidate: ConnectionCandidate,
        error: String,
    ) {
        val key = endpointKey(candidate)
        _state.update { current ->
            val endpoint = current.endpoints[key] ?: return@update current
            current.copy(
                endpoints =
                    current.endpoints +
                        (key to
                            endpoint.copy(
                                failures = 1,
                                roundLogs = listOf("准备失败｜$error"),
                                finished = true,
                            )),
            )
        }
    }

    fun stop() {
        testJob?.cancel()
        testJob = null
        _state.update { current ->
            current.copy(
                running = false,
                endpoints = current.endpoints.mapValues { (_, endpoint) -> endpoint.copy(activeRound = null) },
                message = "测试已停止",
            )
        }
    }

    private fun recordRound(
        candidate: ConnectionCandidate,
        result: AdbLatencyRoundResult,
    ) {
        val key = endpointKey(candidate)
        _state.update { current ->
            val endpoint = current.endpoints[key] ?: return@update current
            val log =
                if (result.successful) {
                    val rttMedian = median(result.shellRoundTripMillis)
                    "#${result.round} 连接 ${result.connectMillis!!.oneDecimal()} ms｜RTT ${rttMedian.oneDecimal()} ms " +
                        "[${result.shellRoundTripMillis.minOrNull()!!.oneDecimal()}–${result.shellRoundTripMillis.maxOrNull()!!.oneDecimal()}]｜" +
                        result.resolvedEndpoint.orEmpty()
                } else {
                    "#${result.round} 失败 ${result.failureMillis?.oneDecimal() ?: "-"} ms｜${result.error}" +
                        result.resolvedEndpoint?.let { "｜$it" }.orEmpty()
                }
            current.copy(
                endpoints =
                    current.endpoints +
                        (key to
                            endpoint.copy(
                                completedRounds = endpoint.completedRounds + 1,
                                activeRound = null,
                                connectSamples = endpoint.connectSamples + listOfNotNull(result.connectMillis),
                                shellSamples = endpoint.shellSamples + result.shellRoundTripMillis,
                                failures = endpoint.failures + if (result.successful) 0 else 1,
                                resolvedEndpoint = result.resolvedEndpoint ?: endpoint.resolvedEndpoint,
                                roundLogs = endpoint.roundLogs + log,
                            )),
            )
        }
    }

    private fun markFinished(candidate: ConnectionCandidate) {
        val key = endpointKey(candidate)
        _state.update { current ->
            val endpoint = current.endpoints[key] ?: return@update current
            current.copy(endpoints = current.endpoints + (key to endpoint.copy(activeRound = null, finished = true)))
        }
    }

    private fun markRoundStarting(
        candidate: ConnectionCandidate,
        round: Int,
    ) {
        val key = endpointKey(candidate)
        _state.update { current ->
            val endpoint = current.endpoints[key] ?: return@update current
            current.copy(endpoints = current.endpoints + (key to endpoint.copy(activeRound = round)))
        }
    }
}

internal fun endpointKey(candidate: ConnectionCandidate): String =
    "${candidate.transport}:${candidate.host}:${candidate.port}"

internal fun endpointLabel(candidate: ConnectionCandidate): String =
    when (candidate.transport) {
        ConnectionTransport.MDNS -> "mDNS  ${candidate.host}"
        ConnectionTransport.TCP -> "TCP  ${candidate.host}:${candidate.port}"
        ConnectionTransport.USB -> "USB  ${candidate.host}"
    }

internal fun median(values: List<Double>): Double {
    if (values.isEmpty()) return 0.0
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) {
        (sorted[middle - 1] + sorted[middle]) / 2.0
    } else {
        sorted[middle]
    }
}

internal fun Double.oneDecimal(): String = String.format(java.util.Locale.US, "%.1f", this)

fun ConnectionLatencyTestState.allTestsCompleted(): Boolean =
    !running && endpoints.isNotEmpty() && endpoints.values.all(ConnectionLatencyEndpointState::finished)

fun ConnectionLatencyTestState.copyText(session: SessionData): String =
    buildString {
        appendLine("Screen Remote 连接延迟测试")
        appendLine("会话：${session.name}")
        appendLine(
            "测试配置：${AdbLatencyBenchmark.DEFAULT_CONNECT_ROUNDS} 次独立连接；" +
                "每次连接后 ${AdbLatencyBenchmark.DEFAULT_SHELL_ROUNDS} 次 shell RTT",
        )
        appendLine("说明：USB 权限在测速前统一检查；每轮连接耗时只统计 ADB 建链，mDNS 另含服务解析。")

        endpoints.values.forEach { endpoint ->
            appendLine()
            appendLine("[${endpoint.label}]")
            if (endpoint.resolvedEndpoint.isNotBlank()) {
                appendLine("解析地址：${endpoint.resolvedEndpoint}")
            }
            appendLine("成功 ${endpoint.connectSamples.size}；失败 ${endpoint.failures}")
            if (endpoint.connectSamples.isNotEmpty()) {
                appendLine(
                    "连接：中位 ${median(endpoint.connectSamples).oneDecimal()} ms；" +
                        "平均 ${endpoint.connectSamples.average().oneDecimal()} ms；" +
                        "范围 ${endpoint.connectSamples.minOrNull()!!.oneDecimal()}–${endpoint.connectSamples.maxOrNull()!!.oneDecimal()} ms",
                )
            }
            if (endpoint.shellSamples.isNotEmpty()) {
                appendLine(
                    "连接后 RTT：中位 ${median(endpoint.shellSamples).oneDecimal()} ms；" +
                        "平均 ${endpoint.shellSamples.average().oneDecimal()} ms；" +
                        "范围 ${endpoint.shellSamples.minOrNull()!!.oneDecimal()}–${endpoint.shellSamples.maxOrNull()!!.oneDecimal()} ms；" +
                        "样本 ${endpoint.shellSamples.size}",
                )
            }
            endpoint.roundLogs.forEach(::appendLine)
        }
    }.trimEnd()

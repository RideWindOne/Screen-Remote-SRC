package com.screen.remote.android.infrastructure.adb.connection

import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.domain.model.ConnectionCandidate
import com.screen.remote.android.core.domain.model.ConnectionTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

internal data class AdbConnectionRaceOutcome(
    val candidate: ConnectionCandidate,
    val result: Result<AdbConnection>,
    val completedAtNanos: Long,
) {
    fun elapsedMillis(startedAtNanos: Long): Long =
        (completedAtNanos - startedAtNanos) / 1_000_000L
}

internal suspend fun raceAdbConnections(
    candidates: List<ConnectionCandidate>,
    connectionManager: AdbConnectionManager,
    attemptScope: CoroutineScope,
    cleanupScope: CoroutineScope,
    logTag: String,
    logLabel: String,
    isCurrentRace: () -> Boolean = { true },
    connectCandidate: suspend (ConnectionCandidate) -> AdbConnection,
): AdbConnectionRaceOutcome {
    val distinctCandidates = candidates.distinctBy(ConnectionCandidate::deviceIdentifier)
    require(distinctCandidates.isNotEmpty()) { "会话没有可用的 connectionCandidates" }

    val startedAtNanos = System.nanoTime()
    val outcomes = Channel<AdbConnectionRaceOutcome>(Channel.UNLIMITED)
    val completedConnections = ConcurrentHashMap<String, AdbConnection>()
    val pending = distinctCandidates.toMutableSet()
    val preExistingConnections =
        distinctCandidates
            .map(ConnectionCandidate::deviceIdentifier)
            .mapNotNull { deviceId ->
                connectionManager.getConnection(deviceId)?.let { it.deviceId to it }
            }.toMap()
    val successfulOutcomes = mutableListOf<AdbConnectionRaceOutcome>()
    var decisionDeadlineNanos: Long? = null
    var winner: AdbConnectionRaceOutcome? = null
    var lastError: Throwable? = null

    LogManager.i(
        logTag,
        "启动 $logLabel 多线路竞速 (${distinctCandidates.size}): " +
            distinctCandidates.joinToString { formatAdbRaceCandidate(it) },
    )

    val jobs =
        distinctCandidates.map { candidate ->
            attemptScope.launch {
                LogManager.d(logTag, "$logLabel candidate started: ${formatAdbRaceCandidate(candidate)}")
                val result =
                    try {
                        Result.success(connectCandidate(candidate))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        Result.failure(error)
                    }
                result.getOrNull()?.let { connection ->
                    completedConnections[connection.deviceId] = connection
                }
                outcomes.send(
                    AdbConnectionRaceOutcome(
                        candidate = candidate,
                        result = result,
                        completedAtNanos = System.nanoTime(),
                    ),
                )
            }
        }

    try {
        while (winner == null && pending.isNotEmpty()) {
            val outcome =
                if (decisionDeadlineNanos == null) {
                    outcomes.receive()
                } else {
                    val remainingNanos = decisionDeadlineNanos - System.nanoTime()
                    withTimeoutOrNull((remainingNanos / 1_000_000L).coerceAtLeast(1L)) {
                        outcomes.receive()
                    }
                }

            if (outcome == null) {
                winner =
                    chooseAdbConnectionRaceWinner(
                        successfulOutcomes = successfulOutcomes,
                        pending = pending,
                        usbWaitExpired = true,
                    )
                break
            }

            pending.remove(outcome.candidate)
            outcome.result.onSuccess { connection ->
                successfulOutcomes += outcome
                if (outcome.candidate.transport == ConnectionTransport.USB) {
                    winner = outcome
                } else if (
                    decisionDeadlineNanos == null &&
                    pending.any { it.transport == ConnectionTransport.USB }
                ) {
                    decisionDeadlineNanos = outcome.completedAtNanos + ADB_CONNECTION_RACE_DECISION_WINDOW_NANOS
                    LogManager.d(
                        logTag,
                        "$logLabel 网络候选已连接，最多等待 USB ${ADB_CONNECTION_RACE_DECISION_WINDOW_MILLIS}ms",
                    )
                }
                LogManager.i(
                    logTag,
                    "$logLabel candidate connected: ${formatAdbRaceCandidate(outcome.candidate)} " +
                        "deviceId=${connection.deviceId} ${outcome.elapsedMillis(startedAtNanos)}ms",
                )
            }.onFailure { error ->
                lastError = error
                LogManager.w(
                    logTag,
                    "$logLabel candidate failed: ${formatAdbRaceCandidate(outcome.candidate)} " +
                        "${outcome.elapsedMillis(startedAtNanos)}ms ${error.message}",
                )
            }

            winner =
                winner ?: chooseAdbConnectionRaceWinner(
                    successfulOutcomes = successfulOutcomes,
                    pending = pending,
                    usbWaitExpired = false,
                )
        }

        val isCurrentOutcome: (AdbConnectionRaceOutcome) -> Boolean = { outcome ->
            val connection = outcome.result.getOrNull()
            connection != null && connectionManager.getConnection(connection.deviceId) === connection
        }
        winner = winner?.takeIf(isCurrentOutcome) ?: choosePreferredAdbConnection(successfulOutcomes, isCurrentOutcome)
    } finally {
        jobs.forEach { it.cancel() }
        cleanupScope.launch {
            jobs.joinAll()
            val winnerDeviceId = winner?.result?.getOrNull()?.deviceId
            completedConnections.forEach { (deviceId, connection) ->
                if (deviceId == winnerDeviceId || connection === preExistingConnections[deviceId]) {
                    return@forEach
                }
                if (!isCurrentRace()) {
                    LogManager.d(logTag, "跳过过期 $logLabel 竞速清理")
                    return@launch
                }
                if (connectionManager.disconnectDeviceIfCurrent(deviceId, connection).getOrDefault(false)) {
                    LogManager.d(logTag, "已关闭 $logLabel 竞速落选连接: $deviceId")
                }
            }
            outcomes.close()
        }
    }

    val selected = winner ?: throw lastError ?: IllegalStateException("ADB 连接失败")
    val selectedConnection = selected.result.getOrThrow()
    LogManager.i(
        logTag,
        "$logLabel 竞速胜出: ${formatAdbRaceCandidate(selected.candidate)} " +
            "deviceId=${selectedConnection.deviceId} ${selected.elapsedMillis(startedAtNanos)}ms",
    )
    return selected
}

internal fun chooseAdbConnectionRaceWinner(
    successfulOutcomes: Collection<AdbConnectionRaceOutcome>,
    pending: Collection<ConnectionCandidate>,
    usbWaitExpired: Boolean,
): AdbConnectionRaceOutcome? {
    chooseFastestAdbConnection(successfulOutcomes.filter { it.candidate.transport == ConnectionTransport.USB })
        ?.let { return it }

    val networkWinner = choosePreferredNetworkAdbConnection(successfulOutcomes) ?: return null
    val usbStillPending = pending.any { it.transport == ConnectionTransport.USB }
    return networkWinner.takeIf { usbWaitExpired || !usbStillPending }
}

internal fun choosePreferredAdbConnection(
    successfulOutcomes: Collection<AdbConnectionRaceOutcome>,
    isCurrent: (AdbConnectionRaceOutcome) -> Boolean = { true },
): AdbConnectionRaceOutcome? =
    chooseFastestAdbConnection(
        successfulOutcomes.filter { it.candidate.transport == ConnectionTransport.USB },
        isCurrent,
    ) ?: chooseFastestAdbConnection(
        successfulOutcomes.filter { it.candidate.transport == ConnectionTransport.MDNS },
        isCurrent,
    ) ?: chooseFastestAdbConnection(
        successfulOutcomes.filter { it.candidate.transport == ConnectionTransport.TCP },
        isCurrent,
    )

internal fun choosePreferredNetworkAdbConnection(
    successfulOutcomes: Collection<AdbConnectionRaceOutcome>,
): AdbConnectionRaceOutcome? =
    chooseFastestAdbConnection(successfulOutcomes.filter { it.candidate.transport == ConnectionTransport.MDNS })
        ?: chooseFastestAdbConnection(successfulOutcomes.filter { it.candidate.transport == ConnectionTransport.TCP })

internal fun chooseFastestAdbConnection(
    successfulOutcomes: Collection<AdbConnectionRaceOutcome>,
    isCurrent: (AdbConnectionRaceOutcome) -> Boolean = { true },
): AdbConnectionRaceOutcome? =
    successfulOutcomes
        .asSequence()
        .filter(isCurrent)
        .minByOrNull(AdbConnectionRaceOutcome::completedAtNanos)

internal fun formatAdbRaceCandidate(candidate: ConnectionCandidate): String =
    "${candidate.transport}:${candidate.host}${if (candidate.port > 0) ":${candidate.port}" else ""}"

internal const val ADB_CONNECTION_RACE_DECISION_WINDOW_MILLIS = 200L
private const val ADB_CONNECTION_RACE_DECISION_WINDOW_NANOS =
    ADB_CONNECTION_RACE_DECISION_WINDOW_MILLIS * 1_000_000L

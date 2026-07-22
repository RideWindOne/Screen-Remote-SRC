package com.screen.remote.android.feature.session.ui

import android.content.Context
import com.screen.remote.android.core.data.repository.TcpPortForwardRule
import com.screen.remote.android.core.i18n.ManagementTexts
import com.screen.remote.android.infrastructure.adb.connection.AdbConnection
import dadb.helper.DEFAULT_REMOTE_HELPER_PATH

internal data class PortForwardConfig(
    val rules: List<TcpPortForwardRule>,
) {
    val localPorts: List<Int>
        get() = rules.map(TcpPortForwardRule::localPort)

    val remoteConfigLine: String
        get() =
            rules.joinToString(separator = " ", prefix = "relay ") { rule ->
                "-L=tcp://127.0.0.1:${rule.localPort}/${rule.targetHost}:${rule.targetPort}"
            }
}

internal data class PortForwardStatus(
    val remoteRunning: Boolean,
    val localForwardRunning: Boolean,
    val pid: Int? = null,
    val config: PortForwardConfig? = null,
    val detail: String? = null,
) {
    val running: Boolean
        get() = remoteRunning && localForwardRunning
}

internal object SessionManagementPortForwardManager {
    private const val REMOTE_MAIN_CLASS = "dadb.helper.TcpRelayMain"
    private const val REMOTE_PID = "/data/local/tmp/screen-remote-tcp-relay.pid"
    private const val REMOTE_CONFIG = "/data/local/tmp/screen-remote-tcp-relay.config"
    private const val REMOTE_LOG = "/data/local/tmp/screen-remote-tcp-relay.log"

    suspend fun status(): Result<PortForwardStatus> {
        val connection = currentConnection()
        return status(connection)
    }

    suspend fun start(
        context: Context,
        config: PortForwardConfig,
    ): Result<PortForwardStatus> =
        runCatching {
            validate(config)
            val connection = currentConnection()
            val previous = status(connection).getOrThrow()

            previous.config?.localPorts?.forEach { oldPort ->
                connection.removeAdbForward(oldPort).getOrThrow()
            }
            stopRemoteProcess(connection).getOrThrow()
            prepareHelper(context, connection).getOrThrow()

            val arguments =
                config.rules.joinToString(" ") { rule ->
                    quoteShellArg("tcp://127.0.0.1:${rule.localPort}/${rule.targetHost}:${rule.targetPort}")
                }
            val script =
                buildString {
                    append("rm -f ").append(REMOTE_PID).append("; ")
                    append("printf '%s\\n' ").append(quoteShellArg(config.remoteConfigLine))
                        .append(" > ").append(REMOTE_CONFIG).append("; ")
                    append(": > ").append(REMOTE_LOG).append("; ")
                    append("CLASSPATH=").append(quoteShellArg(DEFAULT_REMOTE_HELPER_PATH))
                        .append(" nohup app_process / ").append(REMOTE_MAIN_CLASS)
                        .append(" ").append(arguments)
                        .append(" >> ").append(REMOTE_LOG).append(" 2>&1 </dev/null & ")
                    append($$"pid=$!; printf '%s\\n' \"$pid\" > ").append(REMOTE_PID).append("; ")
                    append($$"printf 'PID=%s\\n' \"$pid\"")
                }

            val launchOutput = connection.executeShell("sh -c ${quoteShellArg(script)}", retryOnFailure = false).getOrThrow()
            val pid = launchOutput.lineSequence().firstOrNull { it.startsWith("PID=") }?.substringAfter('=')?.toIntOrNull()
            val activeLocalPorts = mutableListOf<Int>()
            config.localPorts.forEach { port ->
                connection.setupPortForward(port, port).getOrElse { error ->
                    activeLocalPorts.forEach { activePort -> connection.removeAdbForward(activePort) }
                    stopRemoteProcess(connection)
                    throw error
                }
                activeLocalPorts += port
            }

            PortForwardStatus(
                remoteRunning = true,
                localForwardRunning = true,
                pid = pid,
                config = config,
            )
        }

    suspend fun stop(): Result<PortForwardStatus> =
        runCatching {
            val connection = currentConnection()
            val previous = status(connection).getOrThrow()
            previous.config?.localPorts?.forEach { localPort ->
                connection.removeAdbForward(localPort).getOrThrow()
            }
            stopRemoteProcess(connection).getOrThrow()
            status(connection).getOrThrow()
        }

    private suspend fun status(connection: AdbConnection): Result<PortForwardStatus> =
        runCatching {
            val script =
                $$"pid=$(cat $$REMOTE_PID 2>/dev/null); " +
                    $$"if [ -n \"$pid\" ] && [ -r /proc/$pid/cmdline ] && " +
                    $$"tr '\\000' ' ' < /proc/$pid/cmdline | grep -F -q $${quoteShellArg(REMOTE_MAIN_CLASS)}; " +
                    $$"then printf 'STATE=RUNNING\\nPID=%s\\n' \"$pid\"; " +
                    "else rm -f $REMOTE_PID; printf 'STATE=STOPPED\\n'; fi; " +
                    "if [ -f $REMOTE_CONFIG ]; then printf 'CONFIG='; cat $REMOTE_CONFIG; fi"
            val output =
                connection
                    .executeShell("sh -c ${quoteShellArg(script)}", retryOnFailure = false)
                    .getOrThrow()
            val remoteRunning = output.lineSequence().any { it.trim() == "STATE=RUNNING" }
            val pid = output.lineSequence().firstOrNull { it.startsWith("PID=") }?.substringAfter('=')?.toIntOrNull()
            val config = output.lineSequence().firstOrNull { it.startsWith("CONFIG=") }?.let { parseRemoteConfig(it.substringAfter("CONFIG=")) }
            val localForwardRunning = config?.localPorts?.all { connection.isAdbForwardRunning(it) } == true
            PortForwardStatus(
                remoteRunning = remoteRunning,
                localForwardRunning = localForwardRunning,
                pid = pid,
                config = config,
                detail = if (remoteRunning && !localForwardRunning) readRemoteLogTail(connection) else null,
            )
        }

    private suspend fun prepareHelper(
        context: Context,
        connection: AdbConnection,
    ): Result<Unit> =
        runCatching {
            val helperJar = ensureLocalDadbHelperJar(context)
            connection.prepareAppIconHelper(helperJar).getOrThrow()
        }

    private suspend fun stopRemoteProcess(connection: AdbConnection): Result<Unit> =
        runCatching {
            val script =
                $$"pid=$(cat $$REMOTE_PID 2>/dev/null); " +
                    $$"if [ -n \"$pid\" ] && [ -r /proc/$pid/cmdline ] && " +
                    $$"tr '\\000' ' ' < /proc/$pid/cmdline | grep -F -q $${quoteShellArg(REMOTE_MAIN_CLASS)}; then " +
                    $$"kill \"$pid\" 2>/dev/null || true; " +
                    $$"i=0; while kill -0 \"$pid\" 2>/dev/null && [ \"$i\" -lt 20 ]; do sleep 0.1; i=$((i + 1)); done; " +
                    $$"if kill -0 \"$pid\" 2>/dev/null; then kill -9 \"$pid\" 2>/dev/null || true; fi; fi; " +
                    "rm -f $REMOTE_PID"
            connection.executeShell("sh -c ${quoteShellArg(script)}", retryOnFailure = false).getOrThrow()
        }

    private suspend fun readRemoteLogTail(connection: AdbConnection): String? =
        connection.executeShell("tail -n 8 $REMOTE_LOG 2>/dev/null", retryOnFailure = false)
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    suspend fun logs(): Result<String> =
        runCatching {
            currentConnection()
                .executeShell("tail -n 120 $REMOTE_LOG 2>/dev/null", retryOnFailure = false)
                .getOrThrow()
                .trimEnd()
        }

    private fun currentConnection(): AdbConnection =
        SessionManagementAdbConnection.current()
            ?: error(ManagementTexts.PortForward.NO_ADB_CONNECTION_AVAILABLE.get())

    internal fun configFor(rules: List<TcpPortForwardRule>): PortForwardConfig? =
        runCatching {
            PortForwardConfig(rules.map { it.copy(targetHost = it.targetHost.trim()) }).also(::validate)
        }.getOrNull()

    internal fun validate(config: PortForwardConfig) {
        require(config.rules.isNotEmpty()) {
            ManagementTexts.PortForward.AT_LEAST_ONE_PORT_FORWARD_REQUIRED.get()
        }
        config.rules.forEach { rule ->
            require(TARGET_HOST.matches(rule.targetHost)) {
                ManagementTexts.PortForward.TARGET_MUST_BE_IPV4_ADDRESS_HOSTNAME.get()
            }
            require(rule.localPort in 1..65535 && rule.targetPort in 1..65535) {
                ManagementTexts.PortForward.PORTS_MUST_BE_BETWEEN_1_65535.get()
            }
        }
        require(config.localPorts.distinct().size == config.localPorts.size) {
            ManagementTexts.PortForward.LOCAL_PORT_CANNOT_BE_LISTENED_MORE_THAN_ONCE.get()
        }
    }

    private fun parseRemoteConfig(command: String): PortForwardConfig? =
        runCatching {
            val tokens = command.trim().split(Regex("\\s+")).filter(String::isNotBlank)
            val arguments = if (tokens.firstOrNull() == "relay") tokens.drop(1) else tokens
            val rules =
                arguments.map { argument ->
                    val match = FORWARD_ARGUMENT.matchEntire(argument) ?: error("Invalid remote relay config")
                    TcpPortForwardRule(
                        targetHost = match.groupValues[2],
                        targetPort = match.groupValues[3].toInt(),
                        localPort = match.groupValues[1].toInt(),
                    )
                }
            PortForwardConfig(rules).also(::validate)
        }.getOrNull()

    private val TARGET_HOST = Regex("^[A-Za-z0-9.-]+$")
    private val FORWARD_ARGUMENT =
        Regex("^-L=tcp://127\\.0\\.0\\.1:(\\d{1,5})/([A-Za-z0-9.-]+):(\\d{1,5})$")
}

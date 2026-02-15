package com.mobile.scrcpy.android.infrastructure.adb.connection

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.event.ForwardRemoved
import com.mobile.scrcpy.android.core.common.event.ForwardSetup
import com.mobile.scrcpy.android.core.common.event.ScrcpyEventBus.pushEvent
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ForwardRemovalContext
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ForwardRemovalTrigger
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ForwardIssue
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ForwardIssueKind
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ForwardSetupContext
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SessionEvent
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.runtime.SessionContext
import dadb.Dadb
import dadb.PortForwarder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

internal class AdbConnectionForwardRegistry(
    private val dadb: Dadb,
    private val deviceId: String,
    private val sessionContextProvider: () -> SessionContext?,
) {
    private val forwarders = ConcurrentHashMap<Int, PortForwarder>()
    private val forwardTargets = ConcurrentHashMap<Int, String>()

    suspend fun setupPortForward(
        localPort: Int,
        remotePort: Int,
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val targetSocket = "tcp:$remotePort"
                forwarders[localPort]?.close()
                forwardTargets.remove(localPort)
                val forwarder = dadb.forward(localPort, targetSocket)
                forwarders[localPort] = forwarder
                forwardTargets[localPort] = targetSocket

                LogManager.d(
                    LogTags.ADB_CONNECTION,
                    "${AdbTexts.ADB_PORT_FORWARD_SUCCESS.get()}: $localPort -> $remotePort",
                )
                Result.success(true)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_PORT_FORWARD_FAILED.get()}: ${e.message}", e)
                Result.failure(e)
            }
        }

    suspend fun setupAdbForward(
        localPort: Int,
        socketName: String,
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val targetSocket = "localabstract:$socketName"

            try {
                forwarders[localPort]?.close()
                forwardTargets.remove(localPort)
                val forwarder = dadb.forward(localPort, targetSocket)
                forwarders[localPort] = forwarder
                forwardTargets[localPort] = targetSocket

                val duration = System.currentTimeMillis() - startTime
                pushEvent(
                    ForwardSetup(
                        deviceId = deviceId,
                        localPort = localPort,
                        remoteSocket = targetSocket,
                        durationMs = duration,
                        success = true,
                    ),
                )
                sessionContextProvider()?.emit(
                    SessionEvent.ForwardSetup(
                        localPort = localPort,
                        remoteSocket = targetSocket,
                        context = ForwardSetupContext(durationMs = duration),
                    ),
                )
                Result.success(true)
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime

                LogManager.e(
                    LogTags.ADB_CONNECTION,
                    "${AdbTexts.ADB_SOCKET_FORWARDER_FAILED.get()}: ${e.message}",
                    e,
                )

                pushEvent(
                    ForwardSetup(
                        deviceId = deviceId,
                        localPort = localPort,
                        remoteSocket = targetSocket,
                        durationMs = duration,
                        success = false,
                        error = e.message,
                    ),
                )
                sessionContextProvider()?.emit(
                    SessionEvent.ForwardFailed(
                        ForwardIssue(
                            kind = ForwardIssueKind.SetupFailed,
                            localPort = localPort,
                            remoteSocket = targetSocket,
                            detail = e.message ?: "Unknown error",
                        ),
                    ),
                )
                Result.failure(e)
            }
        }

    suspend fun checkAdbForward(localPort: Int): Boolean =
        withContext(Dispatchers.IO) {
            val forwarder = forwarders[localPort]
            if (forwarder?.isRunning() != true) {
                LogManager.d(LogTags.ADB_CONNECTION, "forwarder not Running")
                return@withContext false
            }

            try {
                val testSocket = Socket()
                testSocket.connect(InetSocketAddress("127.0.0.1", localPort), 500)
                testSocket.close()
                LogManager.d(LogTags.ADB_CONNECTION, "forwarder can connect")
                true
            } catch (_: Exception) {
                LogManager.d(LogTags.ADB_CONNECTION, "forwarder can't connect")
                false
            }
        }

    suspend fun removeAdbForward(
        localPort: Int,
        trigger: ForwardRemovalTrigger = ForwardRemovalTrigger.Unknown,
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                forwarders[localPort]?.close()
                forwarders.remove(localPort)
                val remoteSocket = forwardTargets.remove(localPort)
                pushEvent(
                    ForwardRemoved(
                        deviceId = deviceId,
                        localPort = localPort,
                    ),
                )
                sessionContextProvider()?.emit(
                    SessionEvent.ForwardRemoved(
                        localPort = localPort,
                        context =
                            ForwardRemovalContext(
                                remoteSocket = remoteSocket,
                                trigger = trigger,
                            ),
                    ),
                )
                Result.success(true)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_FORWARD_REMOVE_EXCEPTION.get()}: ${e.message}", e)
                Result.failure(e)
            }
        }

    fun closeAll() {
        forwarders.values.forEach { it.close() }
        forwarders.clear()
        forwardTargets.clear()
    }
}

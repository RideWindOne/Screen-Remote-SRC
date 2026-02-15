package com.mobile.scrcpy.android.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mobile.scrcpy.android.core.common.manager.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DebugUsbAdbReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        log("receiver: action=${intent.action} extras=${intent.extras?.keySet()?.joinToString().orEmpty()}")
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        scope.launch {
            try {
                DebugUsbAdbCommands.handleIntent(intent)
            } catch (t: Throwable) {
                log("command failed: ${t.message}", t)
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    private fun log(
        message: String,
        throwable: Throwable? = null,
    ) {
        if (throwable == null) {
            LogManager.d(TAG, message)
        } else {
            LogManager.e(TAG, message, throwable)
        }
    }

    companion object {
        internal const val TAG = "USBDBG"

        const val EXTRA_COMMAND = "command"
        const val EXTRA_DEVICE_MATCH = "device_match"
        const val EXTRA_SHELL_COMMAND = "shell_command"
        const val EXTRA_LOCAL_PORT = "local_port"
        const val EXTRA_REMOTE_PORT = "remote_port"
        const val EXTRA_SOCKET_NAME = "socket_name"

        const val COMMAND_SCAN = "scan"
        const val COMMAND_PERMISSION = "permission"
        const val COMMAND_CONNECT = "connect"
        const val COMMAND_SHELL = "shell"
        const val COMMAND_TCP_FORWARD = "tcp_forward"
        const val COMMAND_ADB_FORWARD = "adb_forward"
        const val COMMAND_SHELL_ASYNC = "shell_async"
        const val COMMAND_START_TEST_TCP_LISTENER = "start_test_tcp_listener"
        const val COMMAND_DISCONNECT = "disconnect"
        const val COMMAND_DIAG = "diag"
        const val COMMAND_DIAG_LEGACY = "diag_legacy"
        const val COMMAND_START_SCRCPY = "start_scrcpy"

        internal const val CONNECT_TIMEOUT_MS = 20_000L
    }
}

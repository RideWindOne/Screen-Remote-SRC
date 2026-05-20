package com.screen.remote.android.infrastructure.adb.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.infrastructure.adb.AdbEndpointTlsDataStore

class AdbTlsIdentityChangeReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val endpoint = intent.getStringExtra(EXTRA_ENDPOINT).orEmpty()
        if (endpoint.isBlank()) {
            return
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager

        when (intent.action) {
            ACTION_UPDATE_TRUST -> {
                val observedPin = intent.getStringExtra(EXTRA_OBSERVED_PIN).orEmpty()
                if (observedPin.isBlank()) {
                    return
                }

                runCatching {
                    AdbEndpointTlsDataStore(context).rememberObservedConnectTlsPublicKey(
                        endpoint = endpoint,
                        observedConnectTlsPublicKeySha256Base64 = observedPin,
                    )
                }.onSuccess {
                    LogManager.i(LogTags.ADB_CONNECTION, "Updated stored ADB TLS identity in DataStore for $endpoint")
                }.onFailure { error ->
                    LogManager.e(LogTags.ADB_CONNECTION, "Failed to update stored ADB TLS identity for $endpoint: ${error.message}", error)
                }
            }

            ACTION_KEEP_OLD -> {
                LogManager.i(LogTags.ADB_CONNECTION, "Kept previous ADB TLS identity for $endpoint")
            }
        }

        notificationManager?.cancel(AdbTlsIdentityChangeNotificationController.notificationIdFor(endpoint))
    }

    companion object {
        private const val ACTION_PREFIX = "com.screen.remote.android.adb.tls"
        const val ACTION_UPDATE_TRUST = "$ACTION_PREFIX.UPDATE_TRUST"
        const val ACTION_KEEP_OLD = "$ACTION_PREFIX.KEEP_OLD"

        private const val EXTRA_ENDPOINT = "endpoint"
        private const val EXTRA_OBSERVED_PIN = "observed_pin"

        fun createUpdateIntent(
            context: Context,
            endpoint: String,
            observedPinSha256Base64: String,
        ): Intent =
            Intent(context, AdbTlsIdentityChangeReceiver::class.java).apply {
                action = ACTION_UPDATE_TRUST
                putExtra(EXTRA_ENDPOINT, endpoint)
                putExtra(EXTRA_OBSERVED_PIN, observedPinSha256Base64)
            }

        fun createKeepIntent(
            context: Context,
            endpoint: String,
        ): Intent =
            Intent(context, AdbTlsIdentityChangeReceiver::class.java).apply {
                action = ACTION_KEEP_OLD
                putExtra(EXTRA_ENDPOINT, endpoint)
            }
    }
}

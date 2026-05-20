package com.screen.remote.android.infrastructure.adb.security

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.screen.remote.android.app.MainActivity
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.infrastructure.adb.AdbEndpointTlsDataStore
import com.screen.remote.android.core.common.util.compat.NotificationApiCompat
import com.screen.remote.android.core.common.util.compat.PendingIntentApiCompat
import com.screen.remote.android.core.common.manager.LogManager
import dadb.android.runtime.ExperimentalDadbAndroidApi
import dadb.android.runtime.AdbTlsPeerIdentity
import kotlin.math.absoluteValue

@OptIn(ExperimentalDadbAndroidApi::class)
internal class AdbTlsIdentityChangeNotificationController(
    private val context: Context,
) {
    private val tlsStateStore = AdbEndpointTlsDataStore(context)

    fun createNotificationChannel() {
        NotificationApiCompat.createNotificationChannelCompat(
            context = context,
            channelId = CHANNEL_ID,
            channelName = CHANNEL_NAME,
            importance = NotificationManager.IMPORTANCE_HIGH,
            description = "ADB Wireless Debugging target identity change alerts",
            showBadge = true,
        )
    }

    fun handleObservedIdentity(identity: AdbTlsPeerIdentity) {
        val endpointKey = identity.target.host
        val previousPin = tlsStateStore.findObservedConnectTlsPublicKey(endpointKey)
        when {
            previousPin.isNullOrBlank() -> {
                tlsStateStore.rememberObservedConnectTlsPublicKey(
                    endpoint = endpointKey,
                    observedConnectTlsPublicKeySha256Base64 = identity.observedPinSha256Base64,
                )
                LogManager.i(
                    LogTags.ADB_CONNECTION,
                    "Recorded initial ADB TLS identity for ${identity.target.authority}: ${identity.observedPinSha256Base64.take(16)}",
                )
            }

            previousPin == identity.observedPinSha256Base64 -> {
                tlsStateStore.rememberObservedConnectTlsPublicKey(
                    endpoint = endpointKey,
                    observedConnectTlsPublicKeySha256Base64 = identity.observedPinSha256Base64,
                )
            }

            else -> {
                showIdentityChangedNotification(
                    change =
                        AdbTlsIdentityChangePresentation(
                            endpoint = endpointKey,
                            targetAuthority = identity.target.authority,
                            previousPinSha256Base64 = previousPin,
                            observedPinSha256Base64 = identity.observedPinSha256Base64,
                        ),
                )
            }
        }
    }

    private fun showIdentityChangedNotification(change: AdbTlsIdentityChangePresentation) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

        val contentIntent =
            PendingIntent.getActivity(
                context,
                notificationIdFor(change.endpoint),
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntentApiCompat.getPendingIntentFlags(mutable = false),
            )

        val updateIntent =
            PendingIntent.getBroadcast(
                context,
                notificationIdFor("${change.endpoint}:update"),
                AdbTlsIdentityChangeReceiver.createUpdateIntent(
                    context = context,
                    endpoint = change.endpoint,
                    observedPinSha256Base64 = change.observedPinSha256Base64,
                ),
                PendingIntentApiCompat.getPendingIntentFlags(mutable = false),
            )

        val keepIntent =
            PendingIntent.getBroadcast(
                context,
                notificationIdFor("${change.endpoint}:keep"),
                AdbTlsIdentityChangeReceiver.createKeepIntent(
                    context = context,
                    endpoint = change.endpoint,
                ),
                PendingIntentApiCompat.getPendingIntentFlags(mutable = false),
            )

        val notification =
            NotificationApiCompat
                .createNotificationBuilder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("目标设备身份已变化")
                .setContentText("${change.targetAuthority} 的 Wireless Debugging TLS 身份发生变化")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "${change.targetAuthority} 的 Wireless Debugging TLS 身份发生变化。你可以更新本地信任记录，或保持旧记录以便后续继续提醒。",
                    ),
                ).setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .addAction(0, "更新信任", updateIntent)
                .addAction(0, "保持旧记录", keepIntent)
                .build()

        notificationManager.notify(notificationIdFor(change.endpoint), notification)
        LogManager.w(
            LogTags.ADB_CONNECTION,
            "ADB TLS identity changed: target=${change.targetAuthority} key=${change.endpoint} previous=${change.previousPinSha256Base64.take(16)} observed=${change.observedPinSha256Base64.take(16)}",
        )
    }

    companion object {
        const val CHANNEL_ID = "adb_tls_identity_change"
        private const val CHANNEL_NAME = "ADB 身份变更提醒"

        fun notificationIdFor(endpoint: String): Int = 30_000 + endpoint.hashCode().absoluteValue % 10_000
    }
}

private data class AdbTlsIdentityChangePresentation(
    val endpoint: String,
    val targetAuthority: String,
    val previousPinSha256Base64: String,
    val observedPinSha256Base64: String,
)

package com.screen.remote.android.infrastructure.adb.pairing

import com.screen.remote.android.core.common.util.DeviceTransportSerial
import com.screen.remote.android.infrastructure.adb.mdns.MdnsDiscoveredConnectService
import java.security.SecureRandom

/** Ephemeral credentials encoded into an Android Wireless Debugging pairing QR code. */
class AdbQrPairingCredentials internal constructor(
    val serviceName: String,
    val password: String,
) {
    val qrPayload: String = "WIFI:T:ADB;S:$serviceName;P:$password;;"

    override fun toString(): String = "AdbQrPairingCredentials(serviceName=$serviceName)"
}

object AdbQrPairingCredentialsGenerator {
    private const val SERVICE_PREFIX = "studio-"
    internal const val SERVICE_SUFFIX_LENGTH = 10
    internal const val PASSWORD_LENGTH = 12
    private const val TOKEN_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val secureRandom = SecureRandom()

    fun create(): AdbQrPairingCredentials = create { bound -> secureRandom.nextInt(bound) }

    internal fun create(nextIndex: (bound: Int) -> Int): AdbQrPairingCredentials =
        AdbQrPairingCredentials(
            serviceName = SERVICE_PREFIX + randomToken(SERVICE_SUFFIX_LENGTH, nextIndex),
            password = randomToken(PASSWORD_LENGTH, nextIndex),
        )

    private fun randomToken(
        length: Int,
        nextIndex: (bound: Int) -> Int,
    ): String =
        buildString(length) {
            repeat(length) {
                append(TOKEN_ALPHABET[nextIndex(TOKEN_ALPHABET.length)])
            }
        }
}

fun List<MdnsDiscoveredConnectService>.findQrPairingService(
    credentials: AdbQrPairingCredentials,
): MdnsDiscoveredConnectService? =
    firstOrNull { service ->
        service.requiresPairing &&
            !service.confirming &&
            service.host.isNotBlank() &&
            service.port in 1..65535 &&
            DeviceTransportSerial
                .mdnsInstanceName(service.name)
                .equals(credentials.serviceName, ignoreCase = true)
    }

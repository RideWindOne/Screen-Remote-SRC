package com.screen.remote.android.core.common.util

object DeviceTransportSerial {
    private const val USB_PREFIX = "usb:"
    private const val TCP_PREFIX = "tcp:"
    private const val MDNS_PREFIX = "mdns:"
    private const val ADB_TLS_PREFIX = "adb-tls:"
    private val MDNS_SERVICE_SUFFIXES =
        listOf(
            "._adb-tls-connect._tcp",
            "._adb-tls-pairing._tcp",
            "._adb._tcp",
        )

    fun usb(serial: String): String =
        USB_PREFIX + stripUsbPrefix(serial)

    fun tcp(host: String, port: Int): String =
        TCP_PREFIX + formatHostPort(normalizeEndpointHost(host), port)

    fun tcp(host: String, port: String): String =
        TCP_PREFIX + formatHostPort(normalizeEndpointHost(host), port)

    fun mdns(serviceName: String): String =
        MDNS_PREFIX + stripMdnPrefix(serviceName)

    fun adbTls(host: String, port: Int): String =
        ADB_TLS_PREFIX + formatHostPort(normalizeEndpointHost(host), port)

    fun stripUsbPrefix(value: String): String = stripPrefixIgnoreCase(value, USB_PREFIX)

    fun stripMdnPrefix(value: String): String = stripPrefixIgnoreCase(value, MDNS_PREFIX)

    fun mdnsInstanceName(value: String): String {
        val normalized =
            stripMdnPrefix(value)
                .trimEnd('.')
                .removeLocalSuffixIgnoreCase()
                .trimEnd('.')
        val suffix = MDNS_SERVICE_SUFFIXES.firstOrNull { normalized.endsWith(it, ignoreCase = true) }
        return if (suffix == null) normalized else normalized.dropLast(suffix.length)
    }

    fun mdnsDeviceSerial(value: String): String {
        val instanceName = mdnsInstanceName(value)
        if (!instanceName.startsWith("adb-", ignoreCase = true)) {
            return instanceName
        }
        val serialAndNonce = instanceName.substring(4)
        val nonceSeparator = serialAndNonce.lastIndexOf('-')
        return if (nonceSeparator > 0 && nonceSeparator < serialAndNonce.lastIndex) {
            serialAndNonce.substring(0, nonceSeparator)
        } else {
            serialAndNonce
        }
    }

    fun mdnsDisplayName(value: String): String {
        val instanceName = mdnsInstanceName(value)
        return if (instanceName.startsWith("adb-", ignoreCase = true)) {
            instanceName.substring(4)
        } else {
            instanceName
        }
    }

    fun mdnsDeviceKey(value: String): String = mdns(mdnsDeviceSerial(value)).lowercase()

    fun stripTcpPrefix(value: String): String = stripPrefixIgnoreCase(value, TCP_PREFIX)

    fun normalizeEndpoint(value: String): String = stripAnyTransportPrefix(value)

    fun stripAnyTransportPrefix(value: String): String =
        when {
            value.trim().startsWith(USB_PREFIX, ignoreCase = true) -> stripUsbPrefix(value)
            value.trim().startsWith(MDNS_PREFIX, ignoreCase = true) -> stripMdnPrefix(value)
            value.trim().startsWith(TCP_PREFIX, ignoreCase = true) -> stripTcpPrefix(value)
            else -> value.trim()
        }

    private fun stripPrefixIgnoreCase(
        value: String,
        prefix: String,
    ): String {
        val trimmed = value.trim()
        return if (trimmed.startsWith(prefix, ignoreCase = true)) trimmed.substring(prefix.length).trim() else trimmed
    }

    private fun String.removeLocalSuffixIgnoreCase(): String =
        if (endsWith(".local", ignoreCase = true)) dropLast(".local".length) else this
}

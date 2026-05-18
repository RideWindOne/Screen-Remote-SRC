package com.mobile.scrcpy.android.core.common.util

data class HostPort(
    val host: String,
    val port: Int,
)

fun normalizeEndpointHost(host: String): String {
    val trimmed = host.trim()
    return if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
        trimmed.substring(1, trimmed.length - 1)
    } else {
        trimmed
    }
}

fun formatHostForAuthority(host: String): String {
    val normalized = normalizeEndpointHost(host)
    return if (normalized.contains(':') && !normalized.startsWith("[") && !normalized.endsWith("]")) {
        "[$normalized]"
    } else {
        normalized
    }
}

fun formatHostPort(
    host: String,
    port: Int,
): String = "${formatHostForAuthority(host)}:$port"

fun formatHostPort(
    host: String,
    port: String,
): String = "${formatHostForAuthority(host)}:$port"

fun parseHostPort(
    value: String,
    allowUnbracketedIpv6: Boolean = false,
): HostPort? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null

    if (trimmed.startsWith("[")) {
        val hostEnd = trimmed.indexOf(']')
        if (hostEnd <= 1 || hostEnd + 1 >= trimmed.length || trimmed[hostEnd + 1] != ':') return null

        val host = trimmed.substring(1, hostEnd)
        val port = trimmed.substring(hostEnd + 2).toIntOrNull() ?: return null
        return HostPort(host, port)
    }

    val separator = trimmed.lastIndexOf(':')
    if (separator <= 0 || separator == trimmed.lastIndex) return null

    val host = trimmed.substring(0, separator)
    val port = trimmed.substring(separator + 1).toIntOrNull() ?: return null
    if (host.contains(':') && !allowUnbracketedIpv6) return null

    return HostPort(host, port)
}

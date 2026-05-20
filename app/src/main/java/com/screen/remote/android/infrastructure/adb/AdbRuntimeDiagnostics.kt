package com.screen.remote.android.infrastructure.adb

import android.content.Context
import dadb.AdbKeyPair
import dadb.android.runtime.ExperimentalDadbAndroidApi
import dadb.android.runtime.AdbRuntime
import dadb.android.tls.AdbTlsCertificatePins
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec

@OptIn(ExperimentalDadbAndroidApi::class)
internal object AdbRuntimeDiagnostics {
    fun identitySummary(adbRuntime: AdbRuntime): String =
        runCatching {
            val keyPair = adbRuntime.loadOrCreateKeyPair()
            val publicKeyPin = shortValue(AdbTlsCertificatePins.sha256Base64(keyPair.normalizedAdbPublicKey()))
            val tlsPublicKeyPin = shortValue(keyPair.tlsPublicKeyPin())
            val fileMatchesPrivate = keyPair.adbPublicKeyMatchesPrivateKey()
            "root=${AdbRuntimeProvider.rootDir().absolutePath} adbPubSha256=$publicKeyPin tlsPubSha256=$tlsPublicKeyPin adbFileMatchesPrivate=$fileMatchesPrivate"
        }.getOrElse { error ->
            "identity-unavailable(${error.message})"
        }

    fun endpointSummary(
        context: Context,
        host: String,
        port: Int,
    ): String =
        runCatching {
            val authority = "$host:$port"
            val storedPin =
                AdbEndpointTlsDataStore(context).findObservedConnectTlsPublicKey(host)
            if (storedPin.isNullOrBlank()) {
                return@runCatching "target=$authority key=$host state=missing"
            }

            "target=$authority key=$host hasStoredObservedTlsPin=true"
        }.getOrElse { error ->
            "target=$host:$port key=$host state-error(${error.message})"
        }

    private fun AdbKeyPair.normalizedAdbPublicKey(): ByteArray {
        val raw = adbPublicKey()
        var size = raw.size
        while (size > 0 && raw[size - 1] == 0.toByte()) {
            size--
        }
        return raw.copyOf(size)
    }

    private fun AdbKeyPair.tlsPublicKeyPin(): String {
        val privateKey = privateKey() as? RSAPrivateKey
            ?: return "<non-rsa>"
        val publicKey =
            KeyFactory.getInstance("RSA")
                .generatePublic(
                    RSAPublicKeySpec(privateKey.modulus, BigInteger.valueOf(65537L)),
                ) as RSAPublicKey
        return AdbTlsCertificatePins.sha256Base64(publicKey.encoded)
    }

    private fun AdbKeyPair.adbPublicKeyMatchesPrivateKey(): Boolean {
        val privateKey = privateKey() as? RSAPrivateKey ?: return false
        val alias = adbPublicKey().decodeAdbKeyAlias()
        val publicKey =
            KeyFactory.getInstance("RSA")
                .generatePublic(
                    RSAPublicKeySpec(privateKey.modulus, BigInteger.valueOf(65537L)),
                ) as RSAPublicKey
        return adbPublicKey().contentEquals(publicKey.toAdbEncoded(alias))
    }

    private fun ByteArray.decodeAdbKeyAlias(): String {
        val text = toString(Charsets.UTF_8).trimEnd('\u0000').trim()
        val separator = text.indexOf(' ')
        if (separator < 0 || separator == text.lastIndex) {
            return "unknown@unknown"
        }
        return text.substring(separator + 1).trim().ifBlank { "unknown@unknown" }
    }

    private fun BigInteger.toAdbEncodedWords(): IntArray {
        val encoded = IntArray(64)
        val r32 = BigInteger.ZERO.setBit(32)
        var remaining = this
        for (index in encoded.indices) {
            val parts = remaining.divideAndRemainder(r32)
            remaining = parts[0]
            encoded[index] = parts[1].toInt()
        }
        return encoded
    }

    private fun RSAPublicKey.toAdbEncoded(alias: String): ByteArray {
        val r32 = BigInteger.ZERO.setBit(32)
        val n0inv = modulus.remainder(r32).modInverse(r32).negate()
        val r = BigInteger.ZERO.setBit(2048)
        val rr = r.modPow(BigInteger.valueOf(2), modulus)

        val buffer = java.nio.ByteBuffer.allocate(524).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(64)
        buffer.putInt(n0inv.toInt())
        modulus.toAdbEncodedWords().forEach { buffer.putInt(it) }
        rr.toAdbEncodedWords().forEach { buffer.putInt(it) }
        buffer.putInt(publicExponent.toInt())

        val base64 = android.util.Base64.encode(buffer.array(), android.util.Base64.NO_WRAP)
        val suffix = " $alias\u0000".toByteArray(Charsets.UTF_8)
        return ByteArray(base64.size + suffix.size).also {
            base64.copyInto(it)
            suffix.copyInto(it, base64.size)
        }
    }

    private fun shortValue(value: String?): String =
        if (value.isNullOrBlank()) {
            "<none>"
        } else if (value.length <= 16) {
            value
        } else {
            value.take(16)
        }
}

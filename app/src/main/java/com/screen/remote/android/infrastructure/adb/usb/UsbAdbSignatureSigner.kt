package com.screen.remote.android.infrastructure.adb.usb

import dadb.AdbKeyPair
import java.io.IOException
import java.security.interfaces.RSAPrivateKey
import javax.crypto.Cipher

internal object UsbAdbSignatureSigner {
    fun signToken(
        token: ByteArray,
        keyPair: AdbKeyPair,
    ): ByteArray =
        try {
            val privateKey = keyPair.privateKey()
            val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, privateKey)
            cipher.update(UsbAdbSignaturePadding.build(privateKey as RSAPrivateKey, token))
            cipher.doFinal(token)
        } catch (e: Exception) {
            throw IOException("Failed to sign token: ${e.message}", e)
        }
}

internal object UsbAdbSignaturePadding {
    private val sha1DigestInfoPrefix =
        byteArrayOf(
            0x00,
            0x30,
            0x21,
            0x30,
            0x09,
            0x06,
            0x05,
            0x2b,
            0x0e,
            0x03,
            0x02,
            0x1a,
            0x05,
            0x00,
            0x04,
            0x14,
        )

    fun build(
        privateKey: RSAPrivateKey,
        token: ByteArray,
    ): ByteArray {
        val blockSize = (privateKey.modulus.bitLength() + 7) / 8
        val ffCount = blockSize - token.size - sha1DigestInfoPrefix.size - 2

        require(ffCount > 0) {
            "RSA block is too small for token signing"
        }

        return ByteArray(2 + ffCount + sha1DigestInfoPrefix.size).apply {
            this[0] = 0x00
            this[1] = 0x01
            for (index in 0 until ffCount) {
                this[2 + index] = 0xFF.toByte()
            }
            sha1DigestInfoPrefix.copyInto(
                destination = this,
                destinationOffset = 2 + ffCount,
            )
        }
    }
}

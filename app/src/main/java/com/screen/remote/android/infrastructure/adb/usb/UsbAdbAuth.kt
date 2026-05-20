package com.screen.remote.android.infrastructure.adb.usb

import dadb.AdbKeyPair

/**
 * USB ADB authentication facade.
 */
internal object UsbAdbAuth {
    fun signToken(
        token: ByteArray,
        keyPair: AdbKeyPair,
    ): ByteArray = UsbAdbSignatureSigner.signToken(token = token, keyPair = keyPair)

    fun getPublicKeyBytes(keyPair: AdbKeyPair): ByteArray =
        keyPair.adbPublicKey()
}

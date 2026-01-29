package com.mobile.scrcpy.android.infrastructure.adb.key.core.adb

import android.content.Context
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import dadb.AdbKeyPair
import java.io.File

/**
 * ADB 密钥对管理器
 * 负责生成、加载和管理 ADB 密钥对
 */
internal class AdbKeyManager(
    private val context: Context,
) {
    private var keyPair: AdbKeyPair? = null

    init {
        initKeyPair()
    }

    /**
     * 初始化 ADB 密钥对
     */
    private fun initKeyPair() {
        try {
            val keysDir = File(context.filesDir, "adb_keys")
            if (!keysDir.exists()) {
                keysDir.mkdirs()
            }

            val privateKeyFile = File(keysDir, "adbkey")
            val publicKeyFile = File(keysDir, "adbkey.pub")

            if (!privateKeyFile.exists() || !publicKeyFile.exists()) {
                LogManager.d(LogTags.ADB_CONNECTION, AdbTexts.ADB_GENERATE_NEW_KEYPAIR.get())
                AdbKeyPair.generate(privateKeyFile, publicKeyFile)
            }

            keyPair = AdbKeyPair.read(privateKeyFile, publicKeyFile)
            LogManager.d(LogTags.ADB_CONNECTION, AdbTexts.ADB_KEYPAIR_LOADED.get())
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_KEYPAIR_INIT_FAILED.get()}: ${e.message}", e)
        }
    }

    /**
     * 获取 ADB 密钥对
     */
    fun getKeyPair(): AdbKeyPair? = keyPair

    /**
     * 重新加载密钥对（在生成新密钥或导入密钥后调用）
     */
    fun reloadKeyPair() {
        initKeyPair()
    }

    /**
     * 获取公钥（用于手动授权）
     */
    fun getPublicKey(): String? =
        try {
            val keysDir = File(context.filesDir, "adb_keys")
            val publicKeyFile = File(keysDir, "adbkey.pub")
            if (publicKeyFile.exists()) {
                publicKeyFile.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_GET_PUBLIC_KEY_FAILED.get()}: ${e.message}", e)
            null
        }
}

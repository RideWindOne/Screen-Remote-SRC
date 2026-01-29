package com.mobile.scrcpy.android.infrastructure.adb.pairing

import android.content.Context
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.infrastructure.adb.key.core.adb.AdbKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * ADB 配对管理器
 *
 * 实现 ADB 无线调试配对协议
 *
 * 协议说明：
 * 1. 建立 TLS 连接（使用自签名证书，不验证）
 * 2. 使用 SPAKE2+ 进行密钥交换
 * 3. 交换 PEER_INFO（包含公钥）
 * 4. 发送配对请求（包含配对码）
 * 5. 接收配对响应
 *
 * 技术限制：
 * - SPAKE2+ 算法需要专门的加密库（如 BoringSSL）
 * - 当前实现仅提供框架，完整实现需要 C++ 加密库支持
 * - iOS 版本通过移植完整 ADB 客户端实现（C++）
 * - Android 应用层无法直接调用系统 adb 命令
 *
 * 参考：
 * - https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/docs/dev/adb_wifi.md
 * - https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/pairing_auth/
 * - external/adb-mobile-ios (iOS 版本的 ADB 移植)
 *
 * TODO: 引入 SPAKE2+ 实现以完成配对功能
 */
class AdbPairingManager(
    private val context: Context,
) {
    private val keyManager = AdbKeyManager(context)

    companion object {
        private const val PAIRING_TIMEOUT_MS = 30000L
        private const val CONNECT_TIMEOUT_MS = 5000

        // ADB 配对协议常量（基于 AOSP pairing_auth 实现）
        private const val PAIRING_PACKET_HEADER_SIZE = 6
        private const val PAIRING_PACKET_MAX_PAYLOAD_SIZE = 2048

        // 消息类型
        private const val MSG_PEER_INFO = 0
        private const val MSG_PAIRING_REQUEST = 1
        private const val MSG_PAIRING_RESPONSE = 2

        // 状态码
        private const val STATUS_SUCCESS = 0
        private const val STATUS_FAIL = 1

        // SPAKE2 协议常量（ADB 使用 SPAKE2+ 进行密钥交换）
        private const val SPAKE2_KEY_SIZE = 32
    }

    /**
     * 使用配对码配对设备
     *
     * @param ipAddress 设备 IP 地址
     * @param port 配对端口
     * @param pairingCode 6位配对码
     * @return 配对结果
     */
    suspend fun pairWithCode(
        ipAddress: String,
        port: Int,
        pairingCode: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            var socket: Socket? = null
            var sslSocket: SSLSocket? = null

            try {
                LogManager.d(LogTags.ADB_PAIRING, "Starting pairing: $ipAddress:$port")

                // 1. 建立 TCP 连接
                socket = Socket()
                withTimeout(CONNECT_TIMEOUT_MS.toLong()) {
                    socket.connect(InetSocketAddress(ipAddress, port), CONNECT_TIMEOUT_MS)
                }
                LogManager.d(LogTags.ADB_PAIRING, "TCP connection established")

                // 2. 升级到 TLS 连接
                sslSocket = createTLSSocket(socket)
                LogManager.d(LogTags.ADB_PAIRING, "TLS handshake completed")

                val input = DataInputStream(sslSocket.inputStream)
                val output = DataOutputStream(sslSocket.outputStream)

                // 3. 发送 PEER_INFO
                sendPeerInfo(output)
                LogManager.d(LogTags.ADB_PAIRING, "Sent PEER_INFO")

                // 4. 接收 PEER_INFO
                val peerInfo = receivePeerInfo(input)
                LogManager.d(LogTags.ADB_PAIRING, "Received PEER_INFO: $peerInfo")

                // 5. 发送配对请求
                sendPairingRequest(output, pairingCode)
                LogManager.d(LogTags.ADB_PAIRING, "Sent PAIRING_REQUEST")

                // 6. 接收配对响应
                val response = receivePairingResponse(input)
                if (response.status != STATUS_SUCCESS) {
                    return@withContext Result.failure(Exception("Pairing failed: status=${response.status}"))
                }
                LogManager.d(LogTags.ADB_PAIRING, "Received PAIRING_RESPONSE: success")

                // 7. 保存公钥（如果需要）
                // ADB 配对成功后，设备会记住我们的公钥
                // 后续连接使用 5555 端口时会自动信任

                LogManager.d(LogTags.ADB_PAIRING, "Pairing completed successfully")
                Result.success(Unit)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_PAIRING, "Pairing failed: ${e.message}", e)
                Result.failure(e)
            } finally {
                sslSocket?.close()
                socket?.close()
            }
        }

    /**
     * 创建 TLS Socket
     */
    private fun createTLSSocket(socket: Socket): SSLSocket {
        // 创建信任所有证书的 TrustManager（配对过程中不验证证书）
        val trustAllCerts =
            arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(
                        chain: Array<java.security.cert.X509Certificate>,
                        authType: String,
                    ) {}

                    override fun checkServerTrusted(
                        chain: Array<java.security.cert.X509Certificate>,
                        authType: String,
                    ) {}

                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                },
            )

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())

        val sslSocketFactory = sslContext.socketFactory
        val sslSocket =
            sslSocketFactory.createSocket(
                socket,
                socket.inetAddress.hostAddress,
                socket.port,
                true,
            ) as SSLSocket

        // 启动 TLS 握手
        sslSocket.startHandshake()

        return sslSocket
    }

    /**
     * 发送 PEER_INFO 消息
     */
    private fun sendPeerInfo(output: DataOutputStream) {
        val keyPair =
            keyManager.getKeyPair()
                ?: throw Exception("ADB key pair not initialized")

        // 获取公钥
        val publicKeyField = keyPair.javaClass.getDeclaredField("publicKeyBytes")
        publicKeyField.isAccessible = true
        val publicKey = publicKeyField.get(keyPair) as ByteArray

        // 构造 PEER_INFO payload
        val payload =
            ByteBuffer
                .allocate(4 + publicKey.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(publicKey.size)
                .put(publicKey)
                .array()

        sendPacket(output, MSG_PEER_INFO, payload)
    }

    /**
     * 接收 PEER_INFO 消息
     */
    private fun receivePeerInfo(input: DataInputStream): PeerInfo {
        val packet = receivePacket(input)
        if (packet.type != MSG_PEER_INFO) {
            throw Exception("Expected PEER_INFO, got ${packet.type}")
        }

        val buffer = ByteBuffer.wrap(packet.payload).order(ByteOrder.LITTLE_ENDIAN)
        val publicKeySize = buffer.int
        val publicKey = ByteArray(publicKeySize)
        buffer.get(publicKey)

        return PeerInfo(publicKey)
    }

    /**
     * 发送配对请求
     */
    private fun sendPairingRequest(
        output: DataOutputStream,
        pairingCode: String,
    ) {
        // 配对码转为字节数组
        val codeBytes = pairingCode.toByteArray(Charsets.UTF_8)

        val payload =
            ByteBuffer
                .allocate(4 + codeBytes.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(codeBytes.size)
                .put(codeBytes)
                .array()

        sendPacket(output, MSG_PAIRING_REQUEST, payload)
    }

    /**
     * 接收配对响应
     */
    private fun receivePairingResponse(input: DataInputStream): PairingResponse {
        val packet = receivePacket(input)
        if (packet.type != MSG_PAIRING_RESPONSE) {
            throw Exception("Expected PAIRING_RESPONSE, got ${packet.type}")
        }

        val buffer = ByteBuffer.wrap(packet.payload).order(ByteOrder.LITTLE_ENDIAN)
        val status = buffer.int

        return PairingResponse(status)
    }

    /**
     * 发送数据包
     */
    private fun sendPacket(
        output: DataOutputStream,
        type: Int,
        payload: ByteArray,
    ) {
        val header =
            ByteBuffer
                .allocate(PAIRING_PACKET_HEADER_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(type.toByte())
                .put(0.toByte()) // reserved
                .putInt(payload.size)
                .array()

        output.write(header)
        output.write(payload)
        output.flush()
    }

    /**
     * 接收数据包
     */
    private fun receivePacket(input: DataInputStream): Packet {
        // 读取头部
        val header = ByteArray(PAIRING_PACKET_HEADER_SIZE)
        input.readFully(header)

        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val type = buffer.get().toInt()
        buffer.get() // reserved
        val payloadSize = buffer.int

        if (payloadSize < 0 || payloadSize > PAIRING_PACKET_MAX_PAYLOAD_SIZE) {
            throw Exception("Invalid payload size: $payloadSize")
        }

        // 读取 payload
        val payload = ByteArray(payloadSize)
        if (payloadSize > 0) {
            input.readFully(payload)
        }

        return Packet(type, payload)
    }

    /**
     * 数据包
     */
    private data class Packet(
        val type: Int,
        val payload: ByteArray,
    )

    /**
     * 对端信息
     */
    private data class PeerInfo(
        val publicKey: ByteArray,
    )

    /**
     * 配对响应
     */
    private data class PairingResponse(
        val status: Int,
    )
}

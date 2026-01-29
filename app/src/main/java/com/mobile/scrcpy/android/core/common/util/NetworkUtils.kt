package com.mobile.scrcpy.android.core.common.util

import java.net.InetAddress
import java.net.NetworkInterface

/**
 * 网络工具类
 */
object NetworkUtils {
    
    /**
     * 获取本机局域网 IP 地址
     * 
     * @return IP 地址，失败返回 null
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                
                // 跳过未启用或虚拟接口
                if (!networkInterface.isUp || networkInterface.isLoopback || networkInterface.isVirtual) {
                    continue
                }
                
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    
                    // 只处理 IPv4 地址
                    if (!address.isLoopbackAddress && address is InetAddress && address.hostAddress?.contains(':') == false) {
                        val ip = address.hostAddress ?: continue
                        
                        // 优先返回局域网 IP
                        if (ip.startsWith("192.168.") || 
                            ip.startsWith("10.") || 
                            ip.matches(Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*"))) {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
    
    /**
     * 获取所有网络接口的 IP 地址
     * 
     * @return IP 地址列表
     */
    fun getAllIpAddresses(): List<String> {
        val ipList = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is InetAddress && address.hostAddress?.contains(':') == false) {
                        address.hostAddress?.let { ipList.add(it) }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ipList
    }
    
    /**
     * 验证 IP 地址格式
     * 
     * @param ip IP 地址字符串
     * @return 是否为有效的 IPv4 地址
     */
    fun isValidIpAddress(ip: String): Boolean {
        return ip.matches(Regex("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$"))
    }
    
    /**
     * 验证端口号
     * 
     * @param port 端口号
     * @return 是否为有效端口
     */
    fun isValidPort(port: Int): Boolean {
        return port in 1..65535
    }
}

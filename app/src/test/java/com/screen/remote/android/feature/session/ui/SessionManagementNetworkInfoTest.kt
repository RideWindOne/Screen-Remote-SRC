package com.screen.remote.android.feature.session.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionManagementNetworkInfoTest {
    @Test
    fun `parses wifi hotspot and usb tether addresses from ip output`() {
        val output =
            """
            12: wlan0    inet 192.168.1.20/24 brd 192.168.1.255 scope global wlan0
            15: ap0    inet 192.168.43.1/24 brd 192.168.43.255 scope global ap0
            18: rndis0    inet 192.168.42.129/24 brd 192.168.42.255 scope global rndis0
            1: lo    inet 127.0.0.1/8 scope host lo
            """.trimIndent()

        assertEquals(
            listOf(
                DeviceIpv4Interface("wlan0", "192.168.1.20"),
                DeviceIpv4Interface("ap0", "192.168.43.1"),
                DeviceIpv4Interface("rndis0", "192.168.42.129"),
            ),
            parseIpv4Interfaces(output),
        )
    }

    @Test
    fun `falls back to ifconfig format and removes duplicates`() {
        val output =
            """
            wlan0: flags=4163<UP,BROADCAST,RUNNING,MULTICAST>  mtu 1500
                    inet 10.0.0.8  netmask 255.255.255.0  broadcast 10.0.0.255
            rndis0 Link encap:Ethernet
                    inet addr:192.168.42.129  Bcast:192.168.42.255  Mask:255.255.255.0
            wlan0: flags=4163<UP,BROADCAST,RUNNING,MULTICAST>  mtu 1500
                    inet 10.0.0.8  netmask 255.255.255.0  broadcast 10.0.0.255
            """.trimIndent()

        assertEquals(
            listOf(
                DeviceIpv4Interface("wlan0", "10.0.0.8"),
                DeviceIpv4Interface("rndis0", "192.168.42.129"),
            ),
            parseIpv4Interfaces(output),
        )
    }

    @Test
    fun `parses default gateway with interface`() {
        assertEquals(
            "192.168.1.1 (wlan0)",
            parseDefaultGateway("default via 192.168.1.1 dev wlan0 proto dhcp src 192.168.1.20"),
        )
    }

    @Test
    fun `falls back to proc net route gateway`() {
        val output =
            """
            Iface Destination Gateway Flags RefCnt Use Metric Mask MTU Window IRTT
            wlan0 00000000 0101A8C0 0003 0 0 303 00000000 0 0 0
            """.trimIndent()

        assertEquals("192.168.1.1 (wlan0)", parseDefaultGateway(output))
    }
}

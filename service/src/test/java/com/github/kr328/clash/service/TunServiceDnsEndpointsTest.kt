package com.github.kr328.clash.service

import kotlin.test.Test
import kotlin.test.assertEquals

class TunServiceDnsEndpointsTest {
    @Test
    fun buildsDnsEndpointsForIpv4AndIpv6Policies() {
        assertEquals("0.0.0.0", TunService.buildTunDnsEndpoints(dnsHijacking = true, allowIpv6 = false))
        assertEquals("0.0.0.0,::", TunService.buildTunDnsEndpoints(dnsHijacking = true, allowIpv6 = true))
        assertEquals("172.19.0.2", TunService.buildTunDnsEndpoints(dnsHijacking = false, allowIpv6 = false))
        assertEquals(
            "172.19.0.2,fdfe:dcba:9876::2",
            TunService.buildTunDnsEndpoints(dnsHijacking = false, allowIpv6 = true),
        )
    }
}

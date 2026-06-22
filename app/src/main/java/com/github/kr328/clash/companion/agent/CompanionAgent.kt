package com.github.kr328.clash.companion.agent

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Process-wide snapshot of the running gateway, so the pairing UI can build a QR (it needs the
 * live LAN address, bound port and TLS fingerprint) without binding to the service.
 */
object CompanionAgent {
    @Volatile
    var running: Boolean = false
        private set

    @Volatile
    var port: Int = 0
        private set

    @Volatile
    var fingerprint: String? = null
        private set

    fun onStarted(port: Int, fingerprint: String) {
        this.port = port
        this.fingerprint = fingerprint
        this.running = true
    }

    fun onStopped() {
        running = false
        port = 0
        fingerprint = null
    }

    fun isReady(): Boolean = running && port > 0 && fingerprint != null

    /**
     * Best-effort current LAN IPv4 for the pairing payload. Skips VPN/virtual interfaces (tun/ppp/
     * rmnet/p2p) — critical because the agent often has the ClashFest VPN running, and `tun0` would
     * otherwise be picked, encoding an unreachable internal address into the QR. Prefers wlan/eth.
     */
    fun lanAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .filterNot { iface ->
                    val n = iface.name.lowercase()
                    n.startsWith("tun") || n.startsWith("ppp") || n.startsWith("rmnet") ||
                        n.startsWith("p2p") || n.startsWith("dummy") || iface.isVirtual
                }
                .sortedBy { iface ->
                    val n = iface.name.lowercase()
                    if (n.startsWith("wlan") || n.startsWith("eth")) 0 else 1
                }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        } catch (_: Exception) {
            null
        }
    }
}

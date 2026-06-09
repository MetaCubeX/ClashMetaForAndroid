package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.ProfileSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Editable model for a profile's `tunnels:` block — static `address`→`target`
 * forwards routed through a chosen `proxy`. Read from [ProfileSnapshot] (engine-
 * normalized into the map form), written back as the explicit map form.
 */
@Serializable
data class TunnelEntry(
    var network: List<String> = listOf("tcp", "udp"),
    var address: String = "",
    var target: String = "",
    var proxy: String = "",
) {
    /** Ordered map for the YAML block, or null if the entry is incomplete. */
    fun toMap(): Map<String, Any?>? {
        val net = network.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val addr = address.trim()
        val tgt = target.trim()
        val px = proxy.trim()
        if (net.isEmpty() || addr.isEmpty() || tgt.isEmpty() || px.isEmpty()) return null
        val m = LinkedHashMap<String, Any?>()
        m["network"] = net
        m["address"] = addr
        m["target"] = tgt
        m["proxy"] = px
        return m
    }
}

@Serializable
data class TunnelsConfig(
    var entries: List<TunnelEntry> = emptyList(),
) {
    /** The `tunnels:` block value (list of ordered maps), or null when empty. */
    fun toTunnelsBlock(): List<Map<String, Any?>>? =
        entries.mapNotNull { it.toMap() }.takeIf { it.isNotEmpty() }

    /** True when the model would write nothing (used by the master-toggle teardown). */
    fun isEmpty(): Boolean = toTunnelsBlock() == null

    companion object {
        fun fromSnapshot(snapshot: ProfileSnapshot): TunnelsConfig = from(snapshot.tunnels)

        fun from(tunnels: JsonArray?): TunnelsConfig {
            val list = tunnels?.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                TunnelEntry(
                    network = obj.strList("network").ifEmpty { listOf("tcp", "udp") },
                    address = obj.str("address") ?: "",
                    target = obj.str("target") ?: "",
                    proxy = obj.str("proxy") ?: "",
                )
            } ?: emptyList()
            return TunnelsConfig(list)
        }

        private fun JsonObject.str(key: String): String? =
            this[key]?.jsonPrimitive?.contentOrNull

        private fun JsonObject.strList(key: String): List<String> =
            (this[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
    }
}

/** Pure validators used by the UI (inline errors) and unit tests. */
object TunnelsValidator {
    enum class Error {
        ADDRESS_INVALID, // not a loopback `host:port` — a tunnel must never bind the LAN
        TARGET_EMPTY,
        PROXY_EMPTY,
        NETWORK_INVALID, // empty or not a subset of {tcp,udp}
    }

    private val loopbackHosts = setOf("127.0.0.1", "::1", "[::1]", "localhost")
    private val validNetworks = setOf("tcp", "udp")

    fun validate(entry: TunnelEntry): Error? {
        val net = entry.network.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (net.isEmpty() || net.any { it !in validNetworks }) return Error.NETWORK_INVALID
        addressError(entry.address)?.let { return it }
        if (entry.target.trim().isEmpty()) return Error.TARGET_EMPTY
        if (entry.proxy.trim().isEmpty()) return Error.PROXY_EMPTY
        return null
    }

    /**
     * `address` must be an explicit loopback `host:port`. Unlike DNS listen, a
     * tunnel forwards traffic, so a bare `:port` (binds all interfaces) or any
     * routable host is rejected — the forward must stay on loopback.
     */
    fun addressError(address: String): Error? {
        val v = address.trim()
        if (v.isEmpty()) return Error.ADDRESS_INVALID
        val colon = v.lastIndexOf(':')
        if (colon <= 0 || colon == v.length - 1) return Error.ADDRESS_INVALID // need host:port
        val host = hostOf(v)
        return if (host in loopbackHosts) null else Error.ADDRESS_INVALID
    }

    /** Host portion of `host:port` / `[ipv6]:port`. */
    private fun hostOf(addr: String): String {
        val s = addr.trim()
        if (s.startsWith("[")) {
            val end = s.indexOf(']')
            if (end > 0) return s.substring(0, end + 1)
        }
        val colon = s.lastIndexOf(':')
        return if (colon >= 0) s.substring(0, colon) else s
    }
}

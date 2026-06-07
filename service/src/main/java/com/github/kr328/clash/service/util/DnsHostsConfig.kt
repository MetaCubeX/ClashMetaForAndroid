package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.ProfileSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Editable model for a profile's name-resolution config (the `dns:` block and
 * the top-level `hosts:` map). Read from [ProfileSnapshot] (engine-parsed,
 * exactly as the user wrote it), serialized back into ordered YAML blocks for
 * the WRITE pipeline. Absent / empty fields are omitted so we never write
 * engine defaults or empty blocks.
 *
 * `enhancedMode` carries the raw mihomo value: `normal` (UI "Off"), `redir-host`,
 * or `fake-ip`.
 */
@Serializable
data class DnsHostsConfig(
    var enable: Boolean? = null,
    var enhancedMode: String? = null,
    var listen: String? = null,
    var cacheAlgorithm: String? = null,
    var nameserver: List<String> = emptyList(),
    var directNameserver: List<String> = emptyList(),
    var proxyServerNameserver: List<String> = emptyList(),
    var defaultNameserver: List<String> = emptyList(),
    var hosts: Map<String, String> = emptyMap(),
) {
    /** The `dns:` block value as an ordered map, or null when nothing is set. */
    fun toDnsBlock(): Map<String, Any?>? {
        val m = LinkedHashMap<String, Any?>()
        enable?.let { m["enable"] = it }
        enhancedMode?.takeIf { it.isNotBlank() }?.let { m["enhanced-mode"] = it }
        listen?.takeIf { it.isNotBlank() }?.let { m["listen"] = it }
        cacheAlgorithm?.takeIf { it.isNotBlank() }?.let { m["cache-algorithm"] = it }
        nameserver.cleaned().ifNotEmpty { m["nameserver"] = it }
        directNameserver.cleaned().ifNotEmpty { m["direct-nameserver"] = it }
        proxyServerNameserver.cleaned().ifNotEmpty { m["proxy-server-nameserver"] = it }
        defaultNameserver.cleaned().ifNotEmpty { m["default-nameserver"] = it }
        return m.takeIf { it.isNotEmpty() }
    }

    /** The `hosts:` block value as an ordered map, or null when empty. */
    fun toHostsBlock(): Map<String, String>? {
        val out = LinkedHashMap<String, String>()
        for ((k, v) in hosts) {
            val key = k.trim()
            val value = v.trim()
            if (key.isNotEmpty() && value.isNotEmpty()) out[key] = value
        }
        return out.takeIf { it.isNotEmpty() }
    }

    /** True when the model would write nothing (used by the master-toggle teardown). */
    fun isEmpty(): Boolean = toDnsBlock() == null && toHostsBlock() == null

    /**
     * Overlays the managed DNS fields onto an EXISTING `dns:` map, preserving
     * any keys this editor doesn't model (respect-rules, nameserver-policy,
     * fake-ip-filter, ipv6, prefer-h3, use-hosts, …). A managed field that is
     * empty/cleared is removed from the map (so the user can drop it) without
     * touching unknown keys. This prevents Save from wiping the rest of a rich
     * provider `dns:` block.
     */
    fun mergeIntoDns(existing: MutableMap<String, Any?>) {
        fun set(key: String, value: Any?) {
            if (value != null) existing[key] = value else existing.remove(key)
        }
        set("enable", enable)
        set("enhanced-mode", enhancedMode?.takeIf { it.isNotBlank() })
        set("listen", listen?.takeIf { it.isNotBlank() })
        set("cache-algorithm", cacheAlgorithm?.takeIf { it.isNotBlank() })
        set("nameserver", nameserver.cleaned().takeIf { it.isNotEmpty() })
        set("direct-nameserver", directNameserver.cleaned().takeIf { it.isNotEmpty() })
        set("proxy-server-nameserver", proxyServerNameserver.cleaned().takeIf { it.isNotEmpty() })
        set("default-nameserver", defaultNameserver.cleaned().takeIf { it.isNotEmpty() })
    }

    private fun List<String>.cleaned(): List<String> =
        map { it.trim() }.filter { it.isNotEmpty() }

    private inline fun List<String>.ifNotEmpty(block: (List<String>) -> Unit) {
        if (isNotEmpty()) block(this)
    }

    companion object {
        fun fromSnapshot(snapshot: ProfileSnapshot): DnsHostsConfig =
            from(snapshot.dns, snapshot.hosts)

        fun from(dns: JsonObject?, hosts: JsonObject?): DnsHostsConfig {
            val c = DnsHostsConfig()
            if (dns != null) {
                c.enable = dns["enable"]?.jsonPrimitive?.booleanOrNull
                c.enhancedMode = dns.str("enhanced-mode")
                c.listen = dns.str("listen")
                c.cacheAlgorithm = dns.str("cache-algorithm")
                c.nameserver = dns.strList("nameserver")
                c.directNameserver = dns.strList("direct-nameserver")
                c.proxyServerNameserver = dns.strList("proxy-server-nameserver")
                c.defaultNameserver = dns.strList("default-nameserver")
            }
            if (hosts != null) {
                val map = LinkedHashMap<String, String>()
                for ((k, v) in hosts) {
                    val value = v.jsonPrimitive.contentOrNull ?: continue
                    map[k] = value
                }
                c.hosts = map
            }
            return c
        }

        private fun JsonObject.str(key: String): String? =
            this[key]?.jsonPrimitive?.contentOrNull

        private fun JsonObject.strList(key: String): List<String> =
            (this[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
    }
}

/** Pure validators used both by the UI (inline errors) and unit tests. */
object DnsHostsValidator {
    enum class Error {
        LISTEN_NOT_LOOPBACK,
        RESPECT_RULES_NEEDS_PROXY_NAMESERVER,
    }

    private val loopbackHosts = setOf("127.0.0.1", "::1", "[::1]", "localhost")

    /**
     * `dns.listen` must be empty, loopback, or the idiomatic empty-host `:port`
     * form (mihomo's default listen notation, e.g. `:1053`). An explicit
     * non-loopback IP (e.g. `192.168.1.5:53`) is rejected as a likely mistake.
     */
    fun listenError(listen: String?): Error? {
        val v = listen?.trim().orEmpty()
        if (v.isEmpty()) return null
        val host = hostOf(v)
        return if (host.isEmpty() || host in loopbackHosts) null else Error.LISTEN_NOT_LOOPBACK
    }

    /** Engine: `respect-rules: true` requires a non-empty `proxy-server-nameserver`. */
    fun proxyServerNameserverError(respectRules: Boolean, proxyServerNameserver: List<String>): Error? {
        if (!respectRules) return null
        val any = proxyServerNameserver.any { it.trim().isNotEmpty() }
        return if (any) null else Error.RESPECT_RULES_NEEDS_PROXY_NAMESERVER
    }

    /** Host portion of `host:port` / `[ipv6]:port` / bare host. */
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

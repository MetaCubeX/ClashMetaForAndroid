package com.github.kr328.clash.service.util

import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.model.ProxyHardeningMode
import java.io.File

/**
 * Sanitises an imported / refreshed `config.yaml` at the filesystem level
 * before mihomo loads it. Closes the leak window left by [ProxyHardener]:
 * that one only rewrites global ports via [ConfigurationOverride], so a
 * subscription can still expose an unauthenticated SOCKS5/HTTP listener
 * to the LAN through the YAML `listeners:` block — bypassing Strict mode
 * entirely. This file-level pass covers the same surface the engine
 * actually reads.
 *
 * Targets (per [ProxyHardeningMode.Strict]):
 *  - `listeners:` — dropped wholesale. Custom listener configs are an
 *    advanced power-user feature; ClashFest's contract is TUN-only.
 *  - `allow-lan: true` — forced to `false`. LAN exposure is never the
 *    default for a security-first client.
 *  - `bind-address: <non-loopback>` — rewritten to `127.0.0.1`.
 *  - `external-controller: <non-loopback>:port` — host rewritten to
 *    `127.0.0.1`, port preserved.
 *  - `dns.listen` — wildcard / non-loopback hosts are rebound to
 *    `127.0.0.1`; malformed non-empty values are removed fail-closed.
 *
 * [ProxyHardeningMode.Compat] keeps `listeners:` but force-binds each
 * entry to `127.0.0.1` so it never advertises to the network even if the
 * subscription set `listen: 0.0.0.0`. allow-lan and external-controller
 * follow the same loopback rules as Strict.
 *
 * [ProxyHardeningMode.Off] is a true no-op — used only by users who
 * explicitly opt out via the UI.
 *
 * AGENTS.md requires that hardening changes preserve the user's original
 * YAML at `config.original.yaml`; this writer creates that copy on the
 * first transformation only (subsequent runs find it already there and
 * leave it untouched). Re-running the hardener on already-clean YAML is
 * a no-op, both on disk and in the returned text.
 */
object YamlHardener {
    private const val ORIGINAL_FILENAME = "config.original.yaml"

    /** Global proxy listener ports; stripped at file level in Strict (SEC-1). */
    private val GLOBAL_PORT_KEYS =
        listOf("port", "socks-port", "mixed-port", "redir-port", "tproxy-port")

    /**
     * Apply the requested [mode] to `config.yaml` inside [profileDir].
     *
     * @return true if the file was rewritten (and `config.original.yaml`
     *         was created if it didn't exist yet).
     */
    fun hardenProfile(profileDir: File, mode: ProxyHardeningMode): Boolean {
        if (mode == ProxyHardeningMode.Off) return false
        val configFile = File(profileDir, "config.yaml")
        if (!configFile.isFile) return false

        val original = try {
            configFile.readText()
        } catch (e: Exception) {
            Log.w("YamlHardener: failed to read ${configFile.absolutePath}: ${e.message}", e)
            return false
        }
        val hardened = hardenYaml(original, mode) ?: return false
        if (hardened == original) return false

        // Preserve the user's YAML on the first hardening run only — later
        // runs (e.g. subscription refresh) must not overwrite the snapshot
        // we made of the unmodified subscription.
        val originalFile = File(profileDir, ORIGINAL_FILENAME)
        if (!originalFile.isFile) {
            try {
                originalFile.writeText(original)
            } catch (e: Exception) {
                Log.w("YamlHardener: failed to save ${originalFile.absolutePath}: ${e.message}", e)
                // Continue — the original is recoverable via the subscription
                // URL even if we couldn't snapshot it locally.
            }
        }

        return try {
            configFile.writeText(hardened)
            Log.i("YamlHardener: applied $mode hardening to ${configFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.w("YamlHardener: failed to write ${configFile.absolutePath}: ${e.message}", e)
            false
        }
    }

    /**
     * Returns hardened YAML for [text]. `null` if [text] doesn't parse as
     * a top-level map; identical-to-input string if the document was
     * already clean.
     *
     * Exposed for unit tests and for callers that operate on in-memory
     * YAML (e.g. subscription merge before write).
     */
    fun hardenYaml(text: String, mode: ProxyHardeningMode): String? {
        if (mode == ProxyHardeningMode.Off) return text
        val document = MihomoConfigDocument.parse(text) ?: return null
        val root = document.root
        var changed = false
        // Removals in Strict (listeners block, global port keys) do
        // root.remove(...), which renderReplacing intentionally skips. Collect
        // such keys so the renderer can strip them from the source text via
        // renderRemoving.
        val removeFromSource = mutableListOf<String>()

        when (hardenListeners(root, mode)) {
            ListenersChange.None -> Unit
            ListenersChange.Rewritten -> changed = true
            ListenersChange.Dropped -> {
                changed = true
                removeFromSource += "listeners"
            }
        }
        // SEC-1: in Strict the global proxy ports must not survive into the
        // config the engine loads. The runtime override (ports=0) is applied
        // after Clash.load, never reloaded, and cleared on each runtime start,
        // so a YAML-declared mixed-port/socks-port stays open (cross-UID
        // reachable on loopback) on every connect. File-level removal is the
        // only reliable close. Compat keeps them (loopback bind via
        // bind-address + RuntimeSocksAuth credential).
        if (mode == ProxyHardeningMode.Strict) {
            for (key in GLOBAL_PORT_KEYS) {
                if (root.containsKey(key)) {
                    root.remove(key)
                    changed = true
                    removeFromSource += key
                }
            }
        }
        changed = forceFalse(root, "allow-lan") || changed
        changed = forceLoopbackHost(root, "bind-address") || changed
        changed = forceLoopbackHostPort(root, "external-controller") || changed
        changed = hardenDnsListen(root) || changed

        if (!changed) return text

        var rendered = document.renderReplacing(
            "listeners",
            "allow-lan",
            "bind-address",
            "external-controller",
            "dns",
        )
        if (removeFromSource.isNotEmpty()) {
            // Re-render through the cleaner so this pass produces a document
            // with the removed blocks/keys actually stripped from the source.
            rendered = MihomoConfigDocument.parseOrEmpty(rendered)
                .renderRemoving(*removeFromSource.toTypedArray())
        }
        return rendered
    }

    private enum class ListenersChange { None, Rewritten, Dropped }

    /**
     * Strict drops the whole `listeners:` block. Compat rewrites every
     * entry's `listen:` to a loopback bind. Off never runs.
     */
    private fun hardenListeners(root: MutableMap<String, Any?>, mode: ProxyHardeningMode): ListenersChange {
        val listeners = root["listeners"] as? List<*> ?: return ListenersChange.None
        if (listeners.isEmpty()) {
            // Empty `listeners: []` block is harmless; leave it untouched
            // rather than churning the YAML signature.
            return ListenersChange.None
        }
        return when (mode) {
            ProxyHardeningMode.Off -> ListenersChange.None
            ProxyHardeningMode.Strict -> {
                root.remove("listeners")
                Log.i("YamlHardener: dropped ${listeners.size} listener entries (Strict)")
                ListenersChange.Dropped
            }
            ProxyHardeningMode.Compat -> {
                val rewritten = listeners.map { sanitizeListenerEntry(it) }
                if (rewritten == listeners) return ListenersChange.None
                root["listeners"] = rewritten
                Log.i("YamlHardener: rebound ${listeners.size} listener entries to loopback (Compat)")
                ListenersChange.Rewritten
            }
        }
    }

    /** Force a listener entry's `listen:` to a loopback bind. Other keys untouched. */
    private fun sanitizeListenerEntry(entry: Any?): Any? {
        val map = entry as? Map<*, *> ?: return entry
        val current = map["listen"]?.toString()
        if (current != null && isLoopbackBind(current)) return entry
        val mutable = LinkedHashMap<String, Any?>(map.size)
        for ((k, v) in map) mutable[k.toString()] = v
        mutable["listen"] = rewriteListenToLoopback(current)
        return mutable
    }

    private fun forceFalse(root: MutableMap<String, Any?>, key: String): Boolean {
        val current = root[key]
        if (current == false) return false
        // Absent allow-lan defaults to false in mihomo, so don't write it
        // unless the file explicitly opted in.
        if (current == null) return false
        root[key] = false
        return true
    }

    private fun forceLoopbackHost(root: MutableMap<String, Any?>, key: String): Boolean {
        val raw = root[key]?.toString()?.trim() ?: return false
        if (raw.isEmpty()) return false
        if (isLoopbackHost(raw)) return false
        root[key] = "127.0.0.1"
        return true
    }

    /**
     * Rewrites a `host:port` entry so the host portion is loopback. Used
     * for `external-controller` which would otherwise expose the RESTful
     * API + dashboard to the LAN.
     */
    private fun forceLoopbackHostPort(root: MutableMap<String, Any?>, key: String): Boolean {
        val raw = root[key]?.toString()?.trim() ?: return false
        if (raw.isEmpty()) return false
        val rewritten = rewriteListenToLoopback(raw)
        if (rewritten == raw) return false
        root[key] = rewritten
        return true
    }

    private fun hardenDnsListen(root: MutableMap<String, Any?>): Boolean {
        val dns = root["dns"] as? Map<*, *> ?: return false
        if (!dns.containsKey("listen")) return false
        val raw = dns["listen"]?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) return false

        val hardened = DnsHostsValidator.hardenListen(raw)
        if (hardened == raw) return false

        val mutable = LinkedHashMap<String, Any?>(dns.size)
        for ((key, value) in dns) mutable[key.toString()] = value
        if (hardened == null) {
            mutable.remove("listen")
            Log.i("YamlHardener: removed malformed dns.listen")
        } else {
            mutable["listen"] = hardened
            Log.i("YamlHardener: rebound dns.listen to loopback")
        }
        root["dns"] = mutable
        return true
    }

    /**
     * Accepts both `host:port` and bare `:port`. Returns input untouched
     * when host is already loopback; otherwise replaces the host token.
     */
    private fun rewriteListenToLoopback(value: String?): String {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return "127.0.0.1"
        if (raw.startsWith(":")) return "127.0.0.1$raw"
        val colon = raw.lastIndexOf(':')
        val (host, port) = if (colon < 0) raw to "" else raw.substring(0, colon) to raw.substring(colon)
        if (isLoopbackHost(host)) return raw
        return "127.0.0.1$port"
    }

    private fun isLoopbackBind(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.startsWith(":")) return false
        val colon = trimmed.lastIndexOf(':')
        val host = if (colon < 0) trimmed else trimmed.substring(0, colon)
        return isLoopbackHost(host)
    }

    private fun isLoopbackHost(host: String): Boolean {
        val h = host.trim().trim('[', ']').lowercase()
        return h == "127.0.0.1" || h == "::1" || h == "localhost"
    }
}

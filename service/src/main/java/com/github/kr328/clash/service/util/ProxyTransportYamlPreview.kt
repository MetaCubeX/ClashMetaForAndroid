package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.ProfileSnapshot
import com.github.kr328.clash.service.model.ProxyTransportInfo
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Pulls per-proxy transport metadata (network / tls / reality) from a
 * mihomo-parsed snapshot plus any proxy-provider files on disk.
 *
 * Cheap structural read: just walks `proxies:` entries and reads a handful of
 * fields. Anything we don't recognise (DNS, raw TCP, custom adapters) gets an
 * empty [ProxyTransportInfo] which UI can treat as "nothing to badge".
 */
object ProxyTransportYamlPreview {

    /** name -> transport info. */
    fun parse(snapshot: ProfileSnapshot, profileDir: File? = null): Map<String, ProxyTransportInfo> {
        val out = linkedMapOf<String, ProxyTransportInfo>()
        for (entry in snapshot.proxies) {
            val name = entry.stringField("name")?.takeIf { it.isNotEmpty() } ?: continue
            out[name] = extractTransportInfo(entry)
        }
        collectProviderProxies(snapshot, profileDir, out)
        return out
    }

    /**
     * Provider YAML files on disk are still walked through SnakeYAML — they
     * are standalone provider documents (just a `proxies:` list), not full
     * mihomo configs, so the engine snapshot does not load them.
     */
    private fun collectProviderProxies(
        snapshot: ProfileSnapshot,
        profileDir: File?,
        out: MutableMap<String, ProxyTransportInfo>,
    ) {
        val dir = profileDir?.takeIf { it.isDirectory } ?: return
        for ((_, prov) in snapshot.proxyProviders) {
            val pathStr = prov.stringField("path")
            val providerUrl = prov.stringField("url")
            val providerFile = if (!pathStr.isNullOrBlank()) {
                ProxyDialerYamlEdit.resolveProviderPath(dir, pathStr)
            } else {
                resolveDefaultProviderFile(dir, providerUrl) ?: continue
            }
            if (!providerFile.isFile) continue
            val pRoot = runCatching { YamlFormatting.parseRootMap(providerFile.readText()) }
                .getOrNull() ?: continue
            collectFromRawMap(pRoot, out)
        }
    }

    private fun collectFromRawMap(
        root: Map<String, Any?>,
        out: MutableMap<String, ProxyTransportInfo>,
    ) {
        val proxies = root["proxies"] as? List<*> ?: return
        for (raw in proxies) {
            val p = raw as? Map<*, *> ?: continue
            val name = p["name"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            out[name] = extractTransportInfoFromRaw(p)
        }
    }

    /**
     * Replicates ClashFest's `<profileDir>/providers/proxies/<md5(url)>` rewrite
     * (see core/src/main/golang/native/config/process.go:patchProviders).
     */
    private fun resolveDefaultProviderFile(profileDir: File, providerUrl: String?): File? {
        if (providerUrl.isNullOrBlank()) return null
        return File(profileDir, "providers/proxies/${md5Hex(providerUrl)}")
    }

    private fun md5Hex(input: String): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return buildString(bytes.size * 2) {
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                append(HEX_CHARS[v ushr 4])
                append(HEX_CHARS[v and 0x0F])
            }
        }
    }

    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    private fun extractTransportInfo(p: JsonObject): ProxyTransportInfo {
        val network = p.stringField("network")?.trim()?.lowercase().orEmpty()
        val tls = p.booleanField("tls")
        // Reality is signalled by a non-empty reality-opts map, with or without
        // explicit tls: true. Mihomo accepts both forms.
        val realityOpts = p["reality-opts"] as? JsonObject
        val reality = realityOpts != null && realityOpts.isNotEmpty()
        val type = p.stringField("type")?.trim()?.lowercase().orEmpty()
        return ProxyTransportInfo(
            network = network,
            tls = tls,
            reality = reality,
            type = type,
        )
    }

    private fun extractTransportInfoFromRaw(p: Map<*, *>): ProxyTransportInfo {
        val network = (p["network"] as? String)?.trim()?.lowercase().orEmpty()
        val tls = truthy(p["tls"])
        val realityOpts = p["reality-opts"] as? Map<*, *>
        val reality = realityOpts != null && realityOpts.isNotEmpty()
        val type = (p["type"] as? String)?.trim()?.lowercase().orEmpty()
        return ProxyTransportInfo(
            network = network,
            tls = tls,
            reality = reality,
            type = type,
        )
    }

    private fun JsonObject.stringField(key: String): String? = runCatching {
        this[key]?.jsonPrimitive?.content
    }.getOrNull()

    private fun JsonObject.booleanField(key: String): Boolean {
        val element = this[key] ?: return false
        return runCatching {
            val prim = element.jsonPrimitive
            when {
                prim.content.equals("true", ignoreCase = true) -> true
                prim.content == "1" -> true
                prim.content.equals("yes", ignoreCase = true) -> true
                prim.content.equals("on", ignoreCase = true) -> true
                else -> false
            }
        }.getOrDefault(false)
    }

    private fun truthy(value: Any?): Boolean = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> {
            val t = value.trim()
            t.equals("true", ignoreCase = true) ||
                t == "1" ||
                t.equals("yes", ignoreCase = true) ||
                t.equals("on", ignoreCase = true)
        }
        else -> false
    }
}

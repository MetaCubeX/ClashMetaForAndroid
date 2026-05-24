package com.github.kr328.clash.service.util

import com.github.kr328.clash.service.model.ProxyTransportInfo
import java.io.File

/**
 * Pulls per-proxy transport metadata (network / tls / reality) from a Clash
 * config.yaml and any proxy-provider files referenced by it.
 *
 * Cheap structural parse: just walks `proxies:` blocks and reads a handful of
 * fields. Anything we don't recognise (DNS, raw TCP, custom adapters) gets an
 * empty [ProxyTransportInfo] which UI can treat as "nothing to badge".
 */
object ProxyTransportYamlPreview {

    /** name -> transport info. */
    fun parse(text: String, profileDir: File? = null): Map<String, ProxyTransportInfo> {
        val root = MihomoConfigDocument.parse(text)?.root ?: return emptyMap()
        val out = linkedMapOf<String, ProxyTransportInfo>()
        collectInlineProxies(root, out)
        collectProviderProxies(root, profileDir, out)
        return out
    }

    private fun collectInlineProxies(
        root: Map<String, Any?>,
        out: MutableMap<String, ProxyTransportInfo>,
    ) {
        val proxies = root["proxies"] as? List<*> ?: return
        for (raw in proxies) {
            val p = raw as? Map<*, *> ?: continue
            val name = p["name"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            out[name] = extractTransportInfo(p)
        }
    }

    private fun collectProviderProxies(
        root: Map<String, Any?>,
        profileDir: File?,
        out: MutableMap<String, ProxyTransportInfo>,
    ) {
        val dir = profileDir?.takeIf { it.isDirectory } ?: return
        val providers = root["proxy-providers"] as? Map<*, *> ?: return
        for ((_, v) in providers) {
            val prov = v as? Map<*, *> ?: continue
            val pathStr = prov["path"] as? String
            val providerUrl = prov["url"] as? String
            val providerFile = if (!pathStr.isNullOrBlank()) {
                ProxyDialerYamlEdit.resolveProviderPath(dir, pathStr)
            } else {
                resolveDefaultProviderFile(dir, providerUrl) ?: continue
            }
            if (!providerFile.isFile) continue
            val pRoot = runCatching { YamlFormatting.parseRootMap(providerFile.readText()) }
                .getOrNull() ?: continue
            collectInlineProxies(pRoot, out)
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

    private fun extractTransportInfo(p: Map<*, *>): ProxyTransportInfo {
        val network = (p["network"] as? String)?.trim()?.lowercase().orEmpty()
        val tls = truthy(p["tls"])
        // Reality is signalled by a non-empty reality-opts map, with or without
        // explicit tls: true. Mihomo accepts both forms.
        val realityOpts = p["reality-opts"] as? Map<*, *>
        val reality = realityOpts != null && realityOpts.isNotEmpty()
        return ProxyTransportInfo(
            network = network,
            tls = tls,
            reality = reality,
        )
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

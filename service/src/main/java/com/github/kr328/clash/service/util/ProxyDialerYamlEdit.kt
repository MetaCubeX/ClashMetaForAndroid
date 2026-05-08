package com.github.kr328.clash.service.util

import java.io.File

/**
 * Sets or clears [dialer-proxy] on a proxy entry (Mihomo / Meta).
 * Searches [config.yaml] `proxies:` first, then each [proxy-providers] path on disk.
 *
 * **Names:** the UI passes engine names (e.g. `[sub1] Spain` after `override.additional-prefix`).
 * On-disk provider YAML still uses the original `name:` from the subscription. Matching uses
 * exact equality first, then compares after stripping a leading `[subN] ` prefix from the UI name.
 */
object ProxyDialerYamlEdit {
    private val dumpYaml = YamlFormatting.blockYaml()

    /** One row from `proxies:` on disk that has `dialer-proxy` set. */
    data class DialerChainRow(
        /** `name:` in YAML (no UI prefix). */
        val targetName: String,
        /** Value of `dialer-proxy` (may include `[subN]` prefix from the app). */
        val dialerName: String,
        /** Relative path under profile dir, e.g. `config.yaml` or `proxies/sub1.yaml`. */
        val relativePath: String,
    )

    /**
     * Lists every proxy entry that has a non-blank `dialer-proxy` in [config.yaml] and in each
     * proxy-provider file. Does not require the engine to be running.
     */
    fun listDialerChains(profileDir: File): List<DialerChainRow> {
        val out = ArrayList<DialerChainRow>()
        val configFile = File(profileDir, "config.yaml")
        if (!configFile.isFile) return out
        val root = YamlFormatting.parseRootMap(configFile.readText()) ?: return out
        collectDialerChainsFromRoot(root, "config.yaml", out)
        val pp = root["proxy-providers"] as? Map<*, *> ?: return out
        for ((_, v) in pp) {
            val prov = v as? Map<*, *> ?: continue
            val pathStr = prov["path"] as? String ?: continue
            val f = resolveProviderPath(profileDir, pathStr)
            if (!f.isFile) continue
            val pRoot = YamlFormatting.parseRootMap(f.readText()) ?: continue
            val rel = pathStr.trim().removePrefix("./")
            collectDialerChainsFromRoot(pRoot, rel, out)
        }
        return out
    }

    /**
     * Removes every `dialer-proxy` field from all `proxies:` lists in [config.yaml] and provider files.
     * Use when stale dialer names break the core (wrong subscription / renamed nodes).
     */
    fun clearAllDialerProxies(profileDir: File): Boolean {
        val configFile = File(profileDir, "config.yaml")
        if (!configFile.isFile) return false
        var any = false
        val root = YamlFormatting.parseRootMap(configFile.readText()) ?: return false
        if (stripDialerFromProxiesInRoot(root)) {
            configFile.writeText(dumpYaml.dump(root))
            any = true
        }
        val pp = root["proxy-providers"] as? Map<*, *> ?: return any
        for ((_, v) in pp) {
            val prov = v as? Map<*, *> ?: continue
            val pathStr = prov["path"] as? String ?: continue
            val f = resolveProviderPath(profileDir, pathStr)
            if (!f.isFile) continue
            val pRoot = YamlFormatting.parseRootMap(f.readText()) ?: continue
            if (stripDialerFromProxiesInRoot(pRoot)) {
                f.writeText(dumpYaml.dump(pRoot))
                any = true
            }
        }
        return any
    }

    private fun collectDialerChainsFromRoot(root: Map<*, *>, relativePath: String, out: MutableList<DialerChainRow>) {
        val proxies = root["proxies"] as? List<*> ?: return
        for (raw in proxies) {
            val m = raw as? Map<*, *> ?: continue
            val name = m["name"]?.toString() ?: continue
            val dialer = m["dialer-proxy"]?.toString()?.trim().orEmpty()
            if (dialer.isEmpty()) continue
            out.add(DialerChainRow(name, dialer, relativePath))
        }
    }

    private fun stripDialerFromProxiesInRoot(root: MutableMap<String, Any?>): Boolean {
        val proxiesRaw = root["proxies"] ?: return false
        val proxies = proxiesRaw as? List<*> ?: return false
        var changed = false
        val out = ArrayList<Any?>()
        for (raw in proxies) {
            val m = raw as? Map<*, *>
            if (m == null) {
                out.add(raw)
                continue
            }
            val dialer = m["dialer-proxy"]?.toString()?.trim().orEmpty()
            if (dialer.isEmpty()) {
                out.add(raw)
                continue
            }
            changed = true
            val newMap = LinkedHashMap<String, Any?>()
            for ((k, v) in m) {
                if (k.toString() == "dialer-proxy") continue
                newMap[k.toString()] = v
            }
            out.add(newMap)
        }
        if (!changed) return false
        root["proxies"] = out
        return true
    }

    /**
     * @param dialerProxyName upstream proxy name, or **null** to remove dialer-proxy
     * @return true if a file was updated
     */
    fun applyDialerProxy(profileDir: File, targetProxyName: String, dialerProxyName: String?): Boolean {
        val trimmedTarget = targetProxyName.trim()
        if (trimmedTarget.isEmpty()) return false
        val configFile = File(profileDir, "config.yaml")
        if (!configFile.isFile) return false
        val configText = try {
            configFile.readText()
        } catch (_: Exception) {
            return false
        }
        val root = YamlFormatting.parseRootMap(configText) ?: return false
        if (patchProxiesList(root, trimmedTarget, dialerProxyName)) {
            configFile.writeText(dumpYaml.dump(root))
            return true
        }

        val pp = root["proxy-providers"] as? Map<*, *> ?: return false
        for ((_, v) in pp) {
            val prov = v as? Map<*, *> ?: continue
            val pathStr = prov["path"] as? String ?: continue
            val f = resolveProviderPath(profileDir, pathStr)
            if (!f.isFile) continue
            val pText = try {
                f.readText()
            } catch (_: Exception) {
                continue
            }
            val pRoot = YamlFormatting.parseRootMap(pText) ?: continue
            if (patchProxiesList(pRoot, trimmedTarget, dialerProxyName)) {
                f.writeText(dumpYaml.dump(pRoot))
                return true
            }
        }
        return false
    }

    /** [proxy-providers] entry `path` relative to [profileDir]. */
    internal fun resolveProviderPath(profileDir: File, path: String): File {
        val n = path.trim().removePrefix("./")
        return File(profileDir, n)
    }

    private fun proxyNameMatches(yamlName: String, uiName: String): Boolean {
        if (yamlName == uiName) return true
        val uiStripped = stripSubPrefix(uiName)
        if (uiStripped.isNotEmpty() && yamlName == uiStripped) return true
        return false
    }

    /** Strips `[sub1] ` style prefix that the app adds via proxy-provider override (on-disk names stay raw). */
    private fun stripSubPrefix(name: String): String =
        name.replaceFirst(Regex("^\\[sub\\d+\\]\\s*"), "").trim()

    private fun patchProxiesList(root: MutableMap<String, Any?>, targetName: String, dialer: String?): Boolean {
        val proxiesRaw = root["proxies"] ?: return false
        val proxies = proxiesRaw as? List<*> ?: return false
        val out = ArrayList<Any?>()
        var found = false
        for (raw in proxies) {
            val m = raw as? Map<*, *>
            if (m == null) {
                out.add(raw)
                continue
            }
            val name = m["name"]?.toString()
            if (name == null) {
                out.add(raw)
                continue
            }
            if (!proxyNameMatches(name, targetName)) {
                out.add(raw)
                continue
            }
            found = true
            val newMap = LinkedHashMap<String, Any?>()
            for ((k, v) in m) {
                newMap[k.toString()] = v
            }
            if (dialer.isNullOrBlank()) {
                newMap.remove("dialer-proxy")
            } else {
                newMap["dialer-proxy"] = dialer.trim()
            }
            out.add(newMap)
        }
        if (!found) return false
        root["proxies"] = out
        return true
    }
}

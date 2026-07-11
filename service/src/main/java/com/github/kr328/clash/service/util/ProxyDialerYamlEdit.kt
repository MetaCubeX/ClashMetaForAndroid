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

    data class FilePatch(
        val relativePath: String,
        val currentYaml: String,
        val proposedYaml: String,
        val providerFile: Boolean = false,
    )

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
        val root = MihomoConfigDocument.parse(configFile.readText())?.root ?: return out
        collectDialerChainsFromRoot(root, "config.yaml", out)
        val pp = root["proxy-providers"] as? Map<*, *> ?: return out
        for ((_, v) in pp) {
            val prov = v as? Map<*, *> ?: continue
            val pathStr = prov["path"] as? String ?: continue
            val f = resolveProviderPath(profileDir, pathStr) ?: continue
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
        val patches = previewClearAllDialerProxies(profileDir)
        for (patch in patches) {
            resolvePatchPath(profileDir, patch)?.writeText(patch.proposedYaml)
        }
        return patches.isNotEmpty()
    }

    fun previewClearAllDialerProxies(profileDir: File): List<FilePatch> {
        val configFile = File(profileDir, "config.yaml")
        if (!configFile.isFile) return emptyList()
        val patches = ArrayList<FilePatch>()
        val configText = configFile.readText()
        val document = MihomoConfigDocument.parse(configText) ?: return emptyList()
        val root = document.root
        if (stripDialerFromProxiesInRoot(root)) {
            patches.add(FilePatch("config.yaml", configText, document.renderReplacing("proxies")))
        }
        val pp = root["proxy-providers"] as? Map<*, *> ?: return patches
        for ((_, v) in pp) {
            val prov = v as? Map<*, *> ?: continue
            val pathStr = prov["path"] as? String ?: continue
            val f = resolveProviderPath(profileDir, pathStr) ?: continue
            if (!f.isFile) continue
            val rel = pathStr.trim().removePrefix("./")
            val pText = f.readText()
            val pRoot = YamlFormatting.parseRootMap(pText) ?: continue
            if (stripDialerFromProxiesInRoot(pRoot)) {
                patches.add(FilePatch(rel, pText, dumpYaml.dump(pRoot), providerFile = true))
            }
        }
        return patches
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
     * Text-in/text-out: applies the proxy-chain intent (`target proxy name → dialer proxy name`)
     * to the `proxies:` list of the given config YAML. Used by [ConfigComposer] to replay the user
     * layer's proxy-chain onto the freshly fetched subscription at apply time.
     *
     * Targets that live in proxy-provider *files* are not reachable here (no file access); the apply
     * path replays those separately via [applyDialerProxy] after providers are fetched. A target not
     * found in `proxies:` is simply skipped (no error) — the engine gate is the final judge.
     */
    fun applyChainToConfigText(configText: String, chain: Map<String, String>): String {
        if (chain.isEmpty()) return configText
        val document = MihomoConfigDocument.parse(configText) ?: return configText
        val root = document.root
        var changed = false
        for ((target, dialer) in chain) {
            val t = target.trim()
            if (t.isEmpty()) continue
            if (patchProxiesList(root, t, dialer)) changed = true
        }
        return if (changed) document.renderReplacing("proxies") else configText
    }

    /**
     * @param dialerProxyName upstream proxy name, or **null** to remove dialer-proxy
     * @return true if a file was updated
     */
    fun applyDialerProxy(profileDir: File, targetProxyName: String, dialerProxyName: String?): Boolean {
        val patch = previewDialerProxy(profileDir, targetProxyName, dialerProxyName) ?: return false
        val target = resolvePatchPath(profileDir, patch) ?: return false
        target.writeText(patch.proposedYaml)
        return true
    }

    fun previewDialerProxy(profileDir: File, targetProxyName: String, dialerProxyName: String?): FilePatch? {
        val trimmedTarget = targetProxyName.trim()
        if (trimmedTarget.isEmpty()) return null
        val configFile = File(profileDir, "config.yaml")
        if (!configFile.isFile) return null
        val configText = try {
            configFile.readText()
        } catch (_: Exception) {
            return null
        }
        val document = MihomoConfigDocument.parse(configText) ?: return null
        val root = document.root
        if (patchProxiesList(root, trimmedTarget, dialerProxyName)) {
            return FilePatch("config.yaml", configText, document.renderReplacing("proxies"))
        }

        val pp = root["proxy-providers"] as? Map<*, *> ?: return null
        for ((_, v) in pp) {
            val prov = v as? Map<*, *> ?: continue
            val pathStr = prov["path"] as? String ?: continue
            val f = resolveProviderPath(profileDir, pathStr) ?: continue
            if (!f.isFile) continue
            val pText = try {
                f.readText()
            } catch (_: Exception) {
                continue
            }
            val pRoot = YamlFormatting.parseRootMap(pText) ?: continue
            if (patchProxiesList(pRoot, trimmedTarget, dialerProxyName)) {
                return FilePatch(
                    pathStr.trim().removePrefix("./"),
                    pText,
                    dumpYaml.dump(pRoot),
                    providerFile = true,
                )
            }
        }
        return null
    }

    /** Resolve a provider path exactly inside this profile's native `providers/` sandbox. */
    internal fun resolveProviderPath(profileDir: File, path: String): File? {
        val raw = path.trim()
        if (raw.isEmpty()) return null
        return runCatching {
            val profileRoot = profileDir.canonicalFile
            val providerRoot = File(profileRoot, "providers").canonicalFile
            if (providerRoot.parentFile != profileRoot) return@runCatching null
            val candidate = File(providerRoot, resolveAsRoot(raw))
            val resolved = candidate.canonicalFile
            if (resolved.toPath().startsWith(providerRoot.toPath())) resolved else null
        }.getOrNull()
    }

    /** Mirrors native common.ResolveAsRoot before mihomo prefixes `<profile>/providers/`. */
    internal fun resolveAsRoot(path: String): String {
        val result = ArrayDeque<String>()
        for (directory in path.split('/')) {
            when (directory) {
                "", "." -> Unit
                ".." -> if (result.isNotEmpty()) result.removeLast()
                else -> result.addLast(directory)
            }
        }
        return result.joinToString("/")
    }

    private fun resolvePatchPath(profileDir: File, patch: FilePatch): File? =
        if (patch.providerFile) resolveProviderPath(profileDir, patch.relativePath)
        else resolveConfigPath(profileDir)

    private fun resolveConfigPath(profileDir: File): File? = runCatching {
        val profileRoot = profileDir.canonicalFile
        File(profileRoot, "config.yaml").canonicalFile.takeIf { it.parentFile == profileRoot }
    }.getOrNull()

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

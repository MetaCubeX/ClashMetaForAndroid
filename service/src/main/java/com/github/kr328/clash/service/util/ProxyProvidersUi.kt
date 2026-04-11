package com.github.kr328.clash.service.util

import org.json.JSONObject
import org.yaml.snakeyaml.Yaml

/**
 * UI-friendly model for multiple HTTP proxy-providers with stable keys [sub1]..[subN]
 * and paths `./providers/subN.yaml`.
 */
data class ProxyProviderUiRow(
    val title: String,
    val url: String,
    val intervalSeconds: Long,
)

object ProxyProvidersUi {
    private val yaml = Yaml()
    private val dumpYaml = YamlFormatting.blockYaml()

    /** Parse labels JSON: `{"sub1":"My name",...}` */
    fun parseLabelsJson(jsonText: String?): Map<String, String> {
        if (jsonText.isNullOrBlank()) return emptyMap()
        return try {
            val o = JSONObject(jsonText)
            buildMap {
                val keys = o.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    put(k, o.optString(k))
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun labelsToJson(labels: Map<String, String>): String {
        val o = JSONObject()
        labels.forEach { (k, v) -> o.put(k, v) }
        return o.toString()
    }

    /**
     * Reads [yamlBlock] (output of [ProxyProvidersYamlEdit.extractBlock]) and optional labels.
     * Preserves declaration order from YAML.
     */
    fun parseRows(yamlBlock: String?, labels: Map<String, String>): List<ProxyProviderUiRow> {
        if (yamlBlock.isNullOrBlank()) return emptyList()
        val root = try {
            yaml.load<MutableMap<String, Any?>>(yamlBlock) ?: return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
        @Suppress("UNCHECKED_CAST")
        val pp = root["proxy-providers"] as? Map<String, Any?> ?: return emptyList()
        val out = ArrayList<ProxyProviderUiRow>(pp.size)
        for ((key, value) in pp) {
            @Suppress("UNCHECKED_CAST")
            val m = value as? Map<String, Any?> ?: continue
            if (m["type"]?.toString()?.lowercase() != "http") continue
            val url = m["url"]?.toString()?.trim().orEmpty()
            if (url.isEmpty()) continue
            val interval = (m["interval"] as? Number)?.toLong()
                ?: 3600L
            val title = labels[key]?.trim().orEmpty().ifEmpty { key }
            out.add(ProxyProviderUiRow(title = title, url = url, intervalSeconds = interval))
        }
        return out
    }

    /**
     * Builds full YAML document containing only `proxy-providers:` with keys sub1..subN.
     *
     * With **more than one** row, each provider gets `override.additional-prefix: "[subN] "` so node
     * names are unique across subscriptions. Otherwise Mihomo merges `use: [sub1, sub2]` into one
     * list and [Selector] matches the **first** proxy with a given name — traffic always follows
     * the first provider when names collide.
     *
     * Each HTTP provider also gets `health-check` so leaf delays populate (select groups do not run
     * url-test-style checks on their own).
     */
    fun buildProxyProvidersDocument(rows: List<ProxyProviderUiRow>): String {
        if (rows.isEmpty()) {
            return dumpYaml.dump(mapOf("proxy-providers" to emptyMap<String, Any>())).trimEnd()
        }
        val sorted = LinkedHashMap<String, Any>()
        val multi = rows.size > 1
        rows.forEachIndexed { index, row ->
            val n = index + 1
            val key = "sub$n"
            val provider = LinkedHashMap<String, Any>()
            provider["type"] = "http"
            provider["url"] = row.url.trim()
            provider["interval"] = row.intervalSeconds.coerceAtLeast(60L)
            provider["path"] = "./providers/sub$n.yaml"
            if (multi) {
                provider["override"] = mapOf("additional-prefix" to "[$key] ")
            }
            provider["health-check"] = linkedMapOf(
                "enable" to true,
                "url" to "http://www.gstatic.com/generate_204",
                "interval" to 300,
                "timeout" to 3000,
                "lazy" to true,
            )
            sorted[key] = provider
        }
        return dumpYaml.dump(mapOf("proxy-providers" to sorted)).trimEnd()
    }

    /** Build labels map sub1..title for non-empty titles. */
    fun buildLabels(rows: List<ProxyProviderUiRow>): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        rows.forEachIndexed { index, row ->
            val key = "sub${index + 1}"
            val t = row.title.trim()
            if (t.isNotEmpty()) m[key] = t
        }
        return m
    }
}

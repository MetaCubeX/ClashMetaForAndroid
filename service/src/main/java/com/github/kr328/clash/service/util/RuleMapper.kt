package com.github.kr328.clash.service.util

import com.github.kr328.clash.service.model.RuleItem
import com.github.kr328.clash.service.model.RuleProviderItem
import com.github.kr328.clash.service.model.RuleSource
import com.github.kr328.clash.service.model.RuleState
import org.yaml.snakeyaml.Yaml
import java.util.UUID

object RuleMapper {
    private val yaml = Yaml()

    fun parseStateFromConfig(configText: String): RuleState {
        val root = YamlFormatting.parseRootMap(configText) ?: return RuleState()
        val providers = parseProviders(root["rule-providers"])
        val rules = parseRules(root["rules"])
        return RuleState(providers = providers, rules = rules)
    }

    fun mergeStateIntoConfig(configText: String, state: RuleState, geoDataUrls: GeoDataUrls): String {
        val root = YamlFormatting.parseRootMap(configText) ?: mutableMapOf()
        root["rule-providers"] = state.providers
            .filter { it.enabled }
            .associate { p ->
                p.name to linkedMapOf(
                    "type" to p.type,
                    "behavior" to p.behavior,
                    "url" to p.url,
                    "path" to p.path.ifBlank { "./ruleset/${p.name}.yaml" },
                    "interval" to p.interval,
                )
            }

        root["rules"] = state.rules
            .filter { it.enabled && !it.deleted }
            .sortedBy { it.order }
            .map { toRuleLine(it) }
            .distinct()
        ensureGeositeConnectivity(root, state.rules, geoDataUrls)

        return YamlFormatting.blockYaml().dump(root)
    }

    private fun ensureGeositeConnectivity(
        root: MutableMap<String, Any?>,
        rules: List<RuleItem>,
        geoDataUrls: GeoDataUrls,
    ) {
        val usesGeosite = rules.any {
            it.enabled && !it.deleted && it.type.equals("GEOSITE", true)
        }
        if (!usesGeosite) return

        root["geodata-mode"] = true

        val existing = (root["geox-url"] as? Map<*, *>)?.entries
            ?.associate { (k, v) -> k.toString() to v }
            ?.toMutableMap()
            ?: mutableMapOf()
        if (existing["geoip"]?.toString().isNullOrBlank()) {
            existing["geoip"] = geoDataUrls.geoIp
        }
        if (existing["geosite"]?.toString().isNullOrBlank()) {
            existing["geosite"] = geoDataUrls.geoSite
        }
        if (existing["mmdb"]?.toString().isNullOrBlank()) {
            existing["mmdb"] = geoDataUrls.mmdb
        }
        if (existing["asn"]?.toString().isNullOrBlank()) {
            existing["asn"] = geoDataUrls.asn
        }
        root["geox-url"] = existing
    }

    fun parseProvidersYaml(yamlText: String): List<RuleProviderItem> {
        val parsed = yaml.load<Any?>(yamlText) ?: return emptyList()
        val rp = when (parsed) {
            is Map<*, *> -> (parsed["rule-providers"] as? Map<*, *>) ?: parsed
            else -> emptyMap<Any?, Any?>()
        }
        return rp.entries.mapIndexedNotNull { _, entry ->
            val key = entry.key?.toString()?.trim().orEmpty()
            val body = entry.value as? Map<*, *> ?: return@mapIndexedNotNull null
            if (key.isEmpty()) return@mapIndexedNotNull null
            RuleProviderItem(
                id = UUID.randomUUID().toString(),
                name = key,
                type = body["type"]?.toString().orEmpty().ifBlank { "http" },
                behavior = body["behavior"]?.toString().orEmpty().ifBlank { "classical" },
                url = body["url"]?.toString().orEmpty(),
                path = body["path"]?.toString().orEmpty(),
                interval = body["interval"]?.toString()?.toIntOrNull() ?: 86400,
                enabled = true,
                source = RuleSource.MANUAL,
            )
        }
    }

    private fun parseProviders(node: Any?): List<RuleProviderItem> {
        val map = node as? Map<*, *> ?: return emptyList()
        return map.entries.mapIndexedNotNull { _, entry ->
            val name = entry.key?.toString()?.trim().orEmpty()
            val body = entry.value as? Map<*, *> ?: return@mapIndexedNotNull null
            if (name.isBlank()) return@mapIndexedNotNull null
            RuleProviderItem(
                id = UUID.randomUUID().toString(),
                name = name,
                type = body["type"]?.toString().orEmpty().ifBlank { "http" },
                behavior = body["behavior"]?.toString().orEmpty().ifBlank { "classical" },
                url = body["url"]?.toString().orEmpty(),
                path = body["path"]?.toString().orEmpty(),
                interval = body["interval"]?.toString()?.toIntOrNull() ?: 86400,
                enabled = true,
                source = RuleSource.PROVIDER,
            )
        }
    }

    private fun parseRules(node: Any?): List<RuleItem> {
        val list = node as? List<*> ?: return emptyList()
        return list.mapIndexedNotNull { index, raw ->
            parseRuleLine(raw?.toString().orEmpty(), index)
        }
    }

    private fun parseRuleLine(line: String, order: Int): RuleItem? {
        val t = line.trim().removePrefix("-").trim()
        if (t.isBlank()) return null
        val parts = t.split(",").map { it.trim() }
        if (parts.isEmpty()) return null
        val type = parts[0].uppercase()
        return if (type == "MATCH") {
            RuleItem(
                id = UUID.randomUUID().toString(),
                raw = t,
                type = type,
                value = "",
                policy = parts.getOrElse(1) { "DIRECT" },
                enabled = true,
                deleted = false,
                source = RuleSource.PROVIDER,
                providerName = null,
                isRestorable = true,
                order = order,
            )
        } else {
            val providerName = if (type == "RULE-SET") parts.getOrElse(1) { "" }.ifBlank { null } else null
            RuleItem(
                id = UUID.randomUUID().toString(),
                raw = t,
                type = type,
                value = parts.getOrElse(1) { "" },
                policy = parts.getOrElse(2) { "DIRECT" },
                enabled = true,
                deleted = false,
                source = if (providerName != null) RuleSource.PROVIDER else RuleSource.MANUAL,
                providerName = providerName,
                isRestorable = providerName != null,
                order = order,
            )
        }
    }

    private fun toRuleLine(rule: RuleItem): String {
        if (rule.raw.isNotBlank() && rule.type.equals("CUSTOM", true)) return rule.raw.trim()
        return if (rule.type.equals("MATCH", true)) {
            "MATCH,${rule.policy}"
        } else {
            "${rule.type},${rule.value},${rule.policy}"
        }
    }
}

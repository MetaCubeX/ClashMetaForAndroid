package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.ProfileSnapshot
import com.github.kr328.clash.service.model.RuleItem
import com.github.kr328.clash.service.model.RuleProviderItem
import com.github.kr328.clash.service.model.RuleSource
import com.github.kr328.clash.service.model.RuleState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.yaml.snakeyaml.Yaml
import java.util.UUID

object RuleMapper {
    private val yaml = Yaml()
    private val DEFAULT_GEOIP_URL = GeoMirrors.primaryGeoIpDat()
    private val DEFAULT_GEOSITE_URL = GeoMirrors.primaryGeoSiteDat()

    // Rule types whose body is structural (nested parens, lists, scripts) and
    // would be silently corrupted by a naive split-by-comma. For these we keep
    // the raw line as-is and never re-synthesise it from type/value/policy.
    private val OPAQUE_RULE_TYPES = setOf("AND", "OR", "NOT", "SUB-RULE", "SCRIPT")

    /**
     * Opaque/logical rule types carry their whole payload in [RuleItem.raw];
     * their `value` and `policy` fields are legitimately empty (see
     * [parseStateFromSnapshot]). Callers that validate value/policy must skip
     * these (e.g. [RuleValidator]) — otherwise a single AND/OR/SUB-RULE rule
     * from the subscription fails the whole save with "value is empty".
     */
    fun isOpaqueType(type: String): Boolean = type.trim().uppercase() in OPAQUE_RULE_TYPES

    // Rule types that only exist via subscription content - the UI does not
    // offer a form to add these manually. When we read them out of config.yaml
    // they are by definition PROVIDER-sourced. Marking them MANUAL caused
    // syncProviderRules to retain stale entries after a subscription refresh
    // that no longer contained them (e.g. GEOSITE,category-ads-all,REJECT
    // surviving across an update that deleted it).
    //
    // These are the rule types the overlay composition treats as subscription-owned: the
    // user's MANUAL rules are list-prepended and win, while these PROVIDER-sourced types come
    // fresh from subscription.yaml on every update and are never frozen into the user layer.
    private val SUBSCRIPTION_OWNED_RULE_TYPES = setOf(
        "RULE-SET", "GEOSITE", "GEOIP", "MATCH",
        "SUB-RULE", "AND", "OR", "NOT",
    )

    /**
     * Build the editor-facing state from an engine-parsed snapshot. This is
     * the entry point for the Path B read pipeline — rule strings arrive
     * from mihomo as whole tokens (logical rules included), no Kotlin-side
     * YAML parsing happens here.
     */
    fun parseStateFromSnapshot(snapshot: ProfileSnapshot): RuleState {
        val providers = snapshot.ruleProviders.map { (name, body) ->
            providerFromJson(name, body, source = RuleSource.PROVIDER)
        }
        val rules = snapshot.rules.mapIndexedNotNull { index, raw ->
            // Rules read out of config.yaml are PROVIDER-sourced by DEFAULT: they came
            // from the (effective) subscription config, regardless of rule TYPE. The old
            // heuristic assumed plain types (DOMAIN, IP-CIDR, ...) were user-MANUAL — but
            // subscriptions routinely author plain DOMAIN/IP-CIDR rules, so that mislabelled
            // subscription rules as MANUAL. Genuine MANUAL rules are only ever set by the
            // editor (Add rule) and preserved via the stored state + the raw-fetch reconcile
            // (reconcileWithStoredState reclassifies by what's actually in the fetched sub).
            parseRuleLine(raw, index, defaultSource = RuleSource.PROVIDER)
        }
        return RuleState(providers = providers, rules = rules)
    }

    fun mergeStateIntoConfig(configText: String, state: RuleState, geoDataUrls: GeoDataUrls): String {
        val document = MihomoConfigDocument.parseOrEmpty(configText)
        val root = document.root
        root["rule-providers"] = state.providers
            .filter { it.enabled }
            .associate { p ->
                p.name to linkedMapOf<String, Any?>(
                    "type" to p.type,
                    "behavior" to p.behavior,
                    "url" to p.url,
                    "path" to p.path.ifBlank { "./ruleset/${p.name}.yaml" },
                    "interval" to p.interval,
                ).apply {
                    // Preserve `format:` for non-yaml providers (mrs/text).
                    // Dropping it silently turned mrs providers into broken
                    // classical-yaml parses with "file must have a payload
                    // field" on every refresh after a UI edit.
                    if (p.format.isNotBlank()) put("format", p.format)
                }
            }

        root["rules"] = state.rules
            .filter { it.enabled && !it.deleted }
            .sortedBy { it.order }
            .map { toRuleLine(it) }
            .distinct()
        ensureGeositeConnectivity(root, state.rules, geoDataUrls)

        return document.renderReplacing("rule-providers", "rules", "geodata-mode", "geox-url")
    }

    /**
     * Additive composition of a USER rule layer onto a fetched subscription
     * (config-overlay-architecture, Group 3). Unlike [mergeStateIntoConfig] — which renders a
     * COMPLETE captured state and *replaces* both blocks — this keeps everything the subscription
     * declares and only layers the user's edits on top, Clash-Verge-Rev style:
     *  - the user's enabled rule-providers are **unioned** into the fetched ones (user wins on a key
     *    clash); nothing is dropped, so a provider referenced only by `dns.nameserver-policy` survives;
     *  - the user's enabled rules are **prepended** to the subscription's rules (overrides evaluated
     *    first).
     */
    fun composeUserRulesOnto(configText: String, userRules: RuleState, geoDataUrls: GeoDataUrls): String {
        val document = MihomoConfigDocument.parseOrEmpty(configText)
        val root = document.root

        val mergedProviders = LinkedHashMap<String, Any?>()
        (root["rule-providers"] as? Map<*, *>)?.forEach { (k, v) ->
            if (k != null) mergedProviders[k.toString()] = v
        }
        userRules.providers.filter { it.enabled && it.source == RuleSource.MANUAL }.forEach { p ->
            mergedProviders[p.name] = linkedMapOf<String, Any?>(
                "type" to p.type,
                "behavior" to p.behavior,
                "url" to p.url,
                "path" to p.path.ifBlank { "./ruleset/${p.name}.yaml" },
                "interval" to p.interval,
            ).apply { if (p.format.isNotBlank()) put("format", p.format) }
        }
        if (mergedProviders.isNotEmpty()) root["rule-providers"] = mergedProviders

        val userLines = userRules.rules
            .filter { it.enabled && !it.deleted && it.source == RuleSource.MANUAL }
            .sortedBy { it.order }
            .map { toRuleLine(it) }
        val existingLines = (root["rules"] as? List<*>).orEmpty().mapNotNull { it?.toString() }
        root["rules"] = (userLines + existingLines).distinct()
        ensureGeositeConnectivity(root, userRules.rules, geoDataUrls)

        return document.renderReplacing("rule-providers", "rules", "geodata-mode", "geox-url")
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

    /**
     * Parses a fragment of YAML that contains only a `rule-providers:` block
     * (or just the inner provider map), used when the UI hands us a textarea
     * paste. Not on the snapshot path because the input is not a full config.
     */
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
            val rawPath = body["path"]?.toString().orEmpty()
            val rawUrl = body["url"]?.toString().orEmpty()
            RuleProviderItem(
                id = UUID.randomUUID().toString(),
                name = key,
                type = body["type"]?.toString().orEmpty().ifBlank { "http" },
                behavior = body["behavior"]?.toString().orEmpty().ifBlank { "classical" },
                url = rawUrl,
                path = rawPath,
                interval = body["interval"]?.toString()?.toIntOrNull() ?: 86400,
                format = inferFormat(
                    declared = body["format"]?.toString().orEmpty(),
                    path = rawPath,
                    url = rawUrl,
                ),
                enabled = true,
                source = RuleSource.MANUAL,
            )
        }
    }

    /**
     * Parses a single raw rule line into a RuleItem. Logical / opaque rule
     * types (AND, OR, NOT, SUB-RULE, SCRIPT) keep the raw line intact and
     * expose empty value/policy — re-synthesising them from substrings would
     * corrupt the nested-paren payload, see toRuleLine.
     *
     * Exposed as a top-level utility so RuleApplyService can build RuleItems
     * for lines that come from the UI directly (paste, snippets) without
     * going through a full snapshot.
     */
    fun parseRuleLine(line: String, order: Int, defaultSource: RuleSource = RuleSource.MANUAL): RuleItem? {
        val trimmed = line.trim().removePrefix("-").trim()
        if (trimmed.isBlank()) return null

        val typeRaw = trimmed.substringBefore(',', missingDelimiterValue = trimmed).trim()
        if (typeRaw.isBlank()) return null
        val type = typeRaw.uppercase()

        if (type == "MATCH") {
            // MATCH,<target> — only the target after the comma is meaningful.
            val target = trimmed.substringAfter(',', missingDelimiterValue = "").trim()
            return RuleItem(
                id = UUID.randomUUID().toString(),
                raw = trimmed,
                type = "MATCH",
                value = "",
                policy = target.ifBlank { "DIRECT" },
                enabled = true,
                deleted = false,
                source = defaultSource,
                providerName = null,
                isRestorable = true,
                order = order,
            )
        }

        if (type in OPAQUE_RULE_TYPES) {
            // Body is structural (nested parens, comma-bearing payloads). Do
            // not split — store the raw line, leave value/policy empty so the
            // UI knows not to offer structural edits, and rely on toRuleLine
            // returning raw verbatim.
            return RuleItem(
                id = UUID.randomUUID().toString(),
                raw = trimmed,
                type = type,
                value = "",
                policy = "",
                enabled = true,
                deleted = false,
                source = defaultSource,
                providerName = null,
                isRestorable = false,
                order = order,
            )
        }

        // Regular rule: TYPE,VALUE,POLICY[,no-resolve|src]. Split-by-comma is
        // safe here because the value of a non-opaque rule never contains a
        // literal comma (per mihomo grammar).
        val parts = trimmed.split(",").map { it.trim() }
        val providerName = if (type == "RULE-SET") parts.getOrElse(1) { "" }.ifBlank { null } else null
        return RuleItem(
            id = UUID.randomUUID().toString(),
            raw = trimmed,
            type = type,
            value = parts.getOrElse(1) { "" },
            policy = parts.getOrElse(2) { "DIRECT" },
            enabled = true,
            deleted = false,
            source = if (providerName != null) RuleSource.PROVIDER else defaultSource,
            providerName = providerName,
            isRestorable = providerName != null,
            order = order,
        )
    }

    fun toRuleLine(rule: RuleItem): String {
        // For opaque types (AND/OR/NOT/SUB-RULE/SCRIPT) and any explicit
        // CUSTOM marker, trust the raw line. Re-synthesising would lose the
        // nested-paren body and produce "AND,((NETWORK,UDP)" — the exact bug
        // that broke logical rules before Path B.
        if (rule.raw.isNotBlank() &&
            (rule.type.uppercase() in OPAQUE_RULE_TYPES || rule.type.equals("CUSTOM", true))
        ) {
            return rule.raw.trim()
        }
        return if (rule.type.equals("MATCH", true)) {
            "MATCH,${rule.policy}"
        } else {
            "${rule.type},${rule.value},${rule.policy}"
        }
    }

    private fun providerFromJson(
        name: String,
        body: JsonObject,
        source: RuleSource,
    ): RuleProviderItem {
        fun str(key: String): String =
            body[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }.orEmpty()

        fun int(key: String, default: Int): Int =
            body[key]?.let { runCatching { it.jsonPrimitive.content.toIntOrNull() }.getOrNull() }
                ?: default

        val rawPath = str("path")
        val rawUrl = str("url")
        return RuleProviderItem(
            id = UUID.randomUUID().toString(),
            name = name,
            type = str("type").ifBlank { "http" },
            behavior = str("behavior").ifBlank { "classical" },
            url = rawUrl,
            path = rawPath,
            interval = int("interval", 86400),
            // Optional in mihomo (defaults to "yaml"). Critical for .mrs
            // providers: without "mrs" here, mergeStateIntoConfig will drop
            // the field and mihomo will try to parse the binary as YAML.
            format = inferFormat(declared = str("format"), path = rawPath, url = rawUrl),
            enabled = true,
            source = source,
        )
    }

    /**
     * Recovers a provider's `format:` field when it is missing from the
     * config but the path or url extension makes the binary format obvious.
     *
     * This is a one-way heuristic — we never *override* an explicitly set
     * value, we only *fill in* a blank. Triggered specifically by a class of
     * pre-Path-B configs where mergeStateIntoConfig used to silently strip
     * `format:` and turn .mrs providers into broken classical-yaml parses.
     *
     * Conservative: only the `.mrs` suffix is recovered. `.txt`/`.list` could
     * imply `format: text`, but those are also valid as classical-yaml when
     * the file actually has a `payload:` block, so we don't guess there.
     */
    internal fun inferFormat(declared: String, path: String, url: String): String {
        if (declared.isNotBlank()) return declared
        val pathLower = path.trim().lowercase()
        val urlLower = url.trim().lowercase()
        if (pathLower.endsWith(".mrs") || urlLower.substringBefore('?').endsWith(".mrs")) {
            return "mrs"
        }
        return ""
    }
}

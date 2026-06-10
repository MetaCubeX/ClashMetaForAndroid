package com.github.kr328.clash.design.util

import com.github.kr328.clash.design.R

/**
 * Friendly catalog of mihomo rule matcher types for the rule editor's "intent"
 * form. Maps each engine type to a human label, an example hint, the right
 * keyboard, and a lightweight value validator — so the add-rule sheet can adapt
 * its value field per type and show a live preview, instead of dumping raw
 * `TYPE / VALUE / POLICY` fields.
 *
 * `titleRes == 0` → show [mihomoType] verbatim (advanced types power users know).
 * `hintRes == 0`  → use the generic value hint.
 * Icons are intentionally left to the UI layer (assign per [mihomoType]).
 */
data class RuleTypeMeta(
    val mihomoType: String,
    val group: Group,
    val titleRes: Int = 0,
    val hintRes: Int = 0,
    val requiresValue: Boolean = true,
    val keyboard: Keyboard = Keyboard.TEXT,
    val validate: (String) -> RuleValueError? = { v -> if (v.isBlank()) RuleValueError.EMPTY else null },
) {
    enum class Group { COMMON, ADVANCED }
    enum class Keyboard { TEXT, NUMBER }
}

/** Per-field validation outcomes; the UI maps these to localized messages. */
enum class RuleValueError {
    EMPTY,
    BAD_PORT,
    BAD_CIDR,
    BAD_NETWORK,
    HAS_SEPARATOR, // domains/values must not contain a comma/whitespace (breaks the rule line)
}

object RuleTypeCatalog {
    // ---- validators (pure) ----
    private fun nonEmptyNoSep(v: String): RuleValueError? {
        val t = v.trim()
        if (t.isEmpty()) return RuleValueError.EMPTY
        if (t.contains(',') || t.any { it.isWhitespace() }) return RuleValueError.HAS_SEPARATOR
        return null
    }

    private fun port(v: String): RuleValueError? {
        val t = v.trim()
        if (t.isEmpty()) return RuleValueError.EMPTY
        // single, range (a-b) or comma list — each part a port in 1..65535
        val parts = t.split(',').flatMap { it.split('/') }
        for (p in parts) {
            val bounds = p.split('-')
            if (bounds.isEmpty() || bounds.size > 2) return RuleValueError.BAD_PORT
            for (b in bounds) {
                val n = b.trim().toIntOrNull() ?: return RuleValueError.BAD_PORT
                if (n !in 1..65535) return RuleValueError.BAD_PORT
            }
        }
        return null
    }

    private fun cidr(v: String): RuleValueError? {
        val t = v.trim()
        if (t.isEmpty()) return RuleValueError.EMPTY
        val slash = t.indexOf('/')
        if (slash <= 0 || slash == t.length - 1) return RuleValueError.BAD_CIDR
        val prefix = t.substring(slash + 1).toIntOrNull() ?: return RuleValueError.BAD_CIDR
        if (prefix !in 0..128) return RuleValueError.BAD_CIDR
        return null
    }

    private fun network(v: String): RuleValueError? =
        if (v.trim().lowercase() in setOf("tcp", "udp")) null else RuleValueError.BAD_NETWORK

    // ---- common (surfaced first, friendly) ----
    val common: List<RuleTypeMeta> = listOf(
        RuleTypeMeta("DOMAIN-SUFFIX", RuleTypeMeta.Group.COMMON, R.string.rule_type_domain, R.string.rule_type_domain_hint, validate = ::nonEmptyNoSep),
        RuleTypeMeta("DOMAIN-KEYWORD", RuleTypeMeta.Group.COMMON, R.string.rule_type_domain_keyword, R.string.rule_type_domain_keyword_hint, validate = ::nonEmptyNoSep),
        RuleTypeMeta("DOMAIN", RuleTypeMeta.Group.COMMON, R.string.rule_type_domain_exact, R.string.rule_type_domain_exact_hint, validate = ::nonEmptyNoSep),
        RuleTypeMeta("IP-CIDR", RuleTypeMeta.Group.COMMON, R.string.rule_type_ip, R.string.rule_type_ip_hint, validate = ::cidr),
        RuleTypeMeta("GEOIP", RuleTypeMeta.Group.COMMON, R.string.rule_type_geoip, R.string.rule_type_geoip_hint, validate = ::nonEmptyNoSep),
        RuleTypeMeta("GEOSITE", RuleTypeMeta.Group.COMMON, R.string.rule_type_geosite, R.string.rule_type_geosite_hint, validate = ::nonEmptyNoSep),
        RuleTypeMeta("DST-PORT", RuleTypeMeta.Group.COMMON, R.string.rule_type_port, R.string.rule_type_port_hint, keyboard = RuleTypeMeta.Keyboard.NUMBER, validate = ::port),
        RuleTypeMeta("PROCESS-NAME", RuleTypeMeta.Group.COMMON, R.string.rule_type_process, R.string.rule_type_process_hint, validate = ::nonEmptyNoSep),
        RuleTypeMeta("NETWORK", RuleTypeMeta.Group.COMMON, R.string.rule_type_network, R.string.rule_type_network_hint, validate = ::network),
        RuleTypeMeta("MATCH", RuleTypeMeta.Group.COMMON, R.string.rule_type_match, requiresValue = false, validate = { null }),
    )

    // ---- advanced (raw type label, generic hint) ----
    val advanced: List<RuleTypeMeta> = listOf(
        "DOMAIN-REGEX", "IP-CIDR6", "IP-SUFFIX", "IP-ASN", "SRC-IP-CIDR", "SRC-PORT",
        "IN-PORT", "IN-TYPE", "IN-NAME", "PROCESS-PATH", "PROCESS-NAME-REGEX",
        "UID", "DSCP", "RULE-SET",
    ).map { type ->
        val numeric = type in setOf("SRC-PORT", "IN-PORT", "UID", "DSCP")
        RuleTypeMeta(
            mihomoType = type,
            group = RuleTypeMeta.Group.ADVANCED,
            keyboard = if (numeric) RuleTypeMeta.Keyboard.NUMBER else RuleTypeMeta.Keyboard.TEXT,
            validate = when (type) {
                "SRC-PORT", "IN-PORT" -> ::port
                "SRC-IP-CIDR" -> ::cidr
                else -> { v -> if (v.isBlank()) RuleValueError.EMPTY else null }
            },
        )
    }

    fun all(): List<RuleTypeMeta> = common + advanced

    fun byMihomoType(type: String): RuleTypeMeta? =
        all().firstOrNull { it.mihomoType.equals(type.trim(), ignoreCase = true) }
}

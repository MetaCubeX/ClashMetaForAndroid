package com.github.kr328.clash.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Structural view of a mihomo profile, produced by the engine via
 * `Bridge.nativeParseProfileSnapshot`. This is the single source of truth for
 * UI READ-operations (rules, proxy-groups, providers) — never parse the YAML
 * yourself in Kotlin.
 *
 * Free-form maps (`proxies`, `proxyGroups`, providers) are kept as `JsonObject`
 * so callers can read whichever fields they need (`name`, `type`, `use`, ...)
 * without forcing a hard data class for every mihomo protocol variant.
 *
 * `rules` are returned as `List<String>` exactly as mihomo parsed them from
 * YAML — including logical rules like `AND,((NETWORK,UDP),(DST-PORT,53)),P1`
 * which would be silently corrupted by a naive comma-split.
 */
@Serializable
data class ProfileSnapshot(
    val rules: List<String> = emptyList(),
    @SerialName("sub-rules") val subRules: Map<String, List<String>> = emptyMap(),
    val proxies: List<JsonObject> = emptyList(),
    @SerialName("proxy-groups") val proxyGroups: List<JsonObject> = emptyList(),
    @SerialName("proxy-providers") val proxyProviders: Map<String, JsonObject> = emptyMap(),
    @SerialName("rule-providers") val ruleProviders: Map<String, JsonObject> = emptyMap(),
    val listeners: List<JsonObject> = emptyList(),
)

/**
 * Wire envelope returned by the native `parseProfileSnapshot` call. On success
 * `snapshot` is populated and `error` is null; on failure `error` carries
 * mihomo's verbatim message and `snapshot` is null.
 */
@Serializable
data class ProfileSnapshotEnvelope(
    val ok: Boolean,
    val snapshot: ProfileSnapshot? = null,
    val error: String? = null,
)

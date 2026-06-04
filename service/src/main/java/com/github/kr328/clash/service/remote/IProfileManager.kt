package com.github.kr328.clash.service.remote

import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.model.ProxyGroupPreviewRow
import com.github.kr328.clash.service.model.ProxyTransportInfo
import com.github.kr328.kaidl.BinderInterface
import java.util.UUID

@BinderInterface
interface IProfileManager {
    suspend fun create(type: Profile.Type, name: String, source: String = ""): UUID
    suspend fun clone(uuid: UUID): UUID
    suspend fun commit(uuid: UUID, callback: IFetchObserver? = null)
    suspend fun release(uuid: UUID)
    suspend fun delete(uuid: UUID)
    suspend fun patch(uuid: UUID, name: String, source: String, interval: Long)

    /**
     * Direct rename of an **already imported** profile — only changes `name`
     * in [ImportedDao], does not enter the pending/draft state that [patch]
     * uses for the full "Edit profile" flow. Used by optimistic-import to
     * upgrade the placeholder host-based name to the real Profile-Title once
     * the background guess finishes, without making the profile look unsaved
     * to the user. No-op if the profile is not in [ImportedDao] (pending /
     * deleted).
     */
    suspend fun renameImported(uuid: UUID, name: String)

    /**
     * Persists subscription auto-update interval from operator headers (e.g. `Profile-Update-Interval`)
     * without re-fetching the subscription body. Coerces to at least 15 minutes (same as [ProfileReceiver]).
     * No-op if the profile is missing or not a URL subscription.
     */
    suspend fun applySubscriptionUpdateInterval(uuid: UUID, intervalMillis: Long)
    /**
     * Force-refresh the subscription identified by [uuid]. Suspends until the
     * fetch + verify pipeline finishes (or fails). [callback] receives the
     * usual `FetchStatus` updates so the UI can drive a progress dialog.
     *
     * Calls are deduplicated by UUID — if an `update()` is already in flight
     * for the same profile, the second invocation returns immediately without
     * starting a second concurrent fetch. This prevents the "io read/write on
     * closed pipe" race that happened when the user rage-tapped the update
     * button while a previous request was still running.
     */
    suspend fun update(uuid: UUID, callback: IFetchObserver? = null)
    suspend fun queryByUUID(uuid: UUID): Profile?
    suspend fun queryAll(): List<Profile>
    suspend fun reorder(uuids: List<String>)
    suspend fun queryActive(): Profile?
    suspend fun setActive(profile: Profile)

    /**
     * Merges [ruleProvidersYaml] into [uuid]'s config.yaml and prepends [prependRuleLine] after `rules:`.
     * Returns false if profile missing, file missing, or config already has `rule-providers:`.
     */
    suspend fun mergeRuleProviderYaml(
        uuid: UUID,
        ruleProvidersYaml: String,
        prependRuleLine: String,
    ): Boolean

    suspend fun previewMergeRuleProviderYaml(
        uuid: UUID,
        ruleProvidersYaml: String,
        prependRuleLine: String,
    ): String?

    /** Proxy group name → type + member names from [uuid]'s config.yaml (no engine). Hidden groups omitted. */
    suspend fun readProxyGroupsPreview(uuid: UUID): Map<String, ProxyGroupPreviewRow>

    /** Per-proxy transport metadata (network/tls/reality) from config + provider files. */
    suspend fun readProxyTransports(uuid: UUID): Map<String, ProxyTransportInfo>

    /** `rule-providers` YAML block or null. */
    suspend fun readRuleProvidersYaml(uuid: UUID): String?

    /** Full processed `config.yaml` text for [uuid], or null if missing. Read-only. */
    suspend fun readConfigYaml(uuid: UUID): String?

    suspend fun replaceRuleProvidersYaml(uuid: UUID, yaml: String): Boolean

    suspend fun previewReplaceRuleProvidersYaml(uuid: UUID, yaml: String): String?

    /** `proxy-providers` YAML block or null. */
    suspend fun readProxyProvidersYaml(uuid: UUID): String?

    suspend fun replaceProxyProvidersYaml(uuid: UUID, yaml: String): Boolean

    suspend fun previewReplaceProxyProvidersYaml(uuid: UUID, yaml: String): String?

    /**
     * Appends a **url-test** [proxy-groups] entry with `use: [providerKeys]` (proxy-provider keys
     * such as sub1, sub2), health-check URL, interval, etc. Fails if a group with the same name exists.
     */
    suspend fun appendRelayProxyGroup(uuid: UUID, groupName: String, providerKeys: List<String>): Boolean

    suspend fun previewAppendRelayProxyGroup(uuid: UUID, groupName: String, providerKeys: List<String>): String?

    /** Removes one [proxy-groups] entry by [groupName]. Returns false if missing or not found. */
    suspend fun removeProxyGroup(uuid: UUID, groupName: String): Boolean

    suspend fun previewRemoveProxyGroup(uuid: UUID, groupName: String): String?

    /**
     * Sets **dialer-proxy** on a proxy entry in config or provider YAML.
     * [dialerProxyName] null removes the field. Returns false if the proxy was not found.
     */
    suspend fun setProxyDialerProxy(uuid: UUID, targetProxyName: String, dialerProxyName: String?): Boolean

    suspend fun previewSetProxyDialerProxy(uuid: UUID, targetProxyName: String, dialerProxyName: String?): String?

    /**
     * Lists saved **dialer-proxy** entries by scanning YAML on disk (no running engine required).
     * Each string is `targetName\u001FdialerName\u001FrelativePath` ([U+001F] unit separator).
     */
    suspend fun listProxyDialerChains(uuid: UUID): List<String>

    /** Removes every **dialer-proxy** from config and provider `proxies:` lists. */
    suspend fun clearAllProxyDialerChains(uuid: UUID): Boolean

    suspend fun previewClearAllProxyDialerChains(uuid: UUID): String?

    /** Optional JSON map `{"sub1":"Display name",...}` for proxy-provider UI labels. */
    suspend fun readProxyProviderLabelsJson(uuid: UUID): String?

    suspend fun writeProxyProviderLabelsJson(uuid: UUID, json: String): Boolean

    /** Structured rules model json (source of truth for Rules UI). */
    suspend fun readRuleState(uuid: UUID): String?

    /** Validates, stores structured state, regenerates YAML, and applies profile change. */
    suspend fun applyRuleState(uuid: UUID, stateJson: String): Boolean

    suspend fun applyYamlPreview(previewId: String): Boolean

    /** Single entry from `proxies:` as YAML, for display. */
    suspend fun readProxyEntryYaml(uuid: UUID, proxyName: String): String?

    /** Remember selector choice before VPN/engine applies [Clash.patchSelector]. */
    suspend fun rememberProxySelection(uuid: UUID, group: String, name: String)

    /** Saved [Selection] rows for profile (group → selected proxy). */
    suspend fun queryProxySelections(uuid: UUID): Map<String, String>

    /** Raw `config.yaml` for an imported profile, or null. */
    suspend fun readImportedConfigYaml(uuid: UUID): String?

    /**
     * JSON-encoded BrandManifest stored for [uuid], or null when no brand
     * was ever applied for that subscription. Decode with `BrandManifest.fromJson(...)`.
     */
    suspend fun readBrandJsonFor(uuid: UUID): String?

    /**
     * Absolute path of the cached brand logo bitmap for [uuid] in the
     * requested theme. `darkTheme=true` returns the dark logo (or fallback
     * to the primary); `darkTheme=false` returns the light variant
     * (or fallback to the primary).
     */
    suspend fun brandLogoPathFor(uuid: UUID, darkTheme: Boolean): String?
}

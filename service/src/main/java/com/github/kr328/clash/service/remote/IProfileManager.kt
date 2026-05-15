package com.github.kr328.clash.service.remote

import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.model.ProxyGroupPreviewRow
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
     * Persists subscription auto-update interval from operator headers (e.g. `Profile-Update-Interval`)
     * without re-fetching the subscription body. Coerces to at least 15 minutes (same as [ProfileReceiver]).
     * No-op if the profile is missing or not a URL subscription.
     */
    suspend fun applySubscriptionUpdateInterval(uuid: UUID, intervalMillis: Long)
    suspend fun update(uuid: UUID)
    suspend fun queryByUUID(uuid: UUID): Profile?
    suspend fun queryAll(): List<Profile>
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

    /** `rule-providers` YAML block or null. */
    suspend fun readRuleProvidersYaml(uuid: UUID): String?

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

    suspend fun previewApplyRuleState(uuid: UUID, stateJson: String): String?

    /** Add raw rules lines with insertion mode: append/prepend/index:<n>. */
    suspend fun addRules(
        uuid: UUID,
        rawRules: List<String>,
        addMode: Boolean,
        insertMode: String = "append",
    ): Boolean

    suspend fun previewAddRules(
        uuid: UUID,
        rawRules: List<String>,
        addMode: Boolean,
        insertMode: String = "append",
    ): String?

    /** mutate rule by id: toggle/delete/restore. */
    suspend fun mutateRule(uuid: UUID, ruleId: String, action: String, enabled: Boolean): Boolean

    suspend fun previewMutateRule(uuid: UUID, ruleId: String, action: String, enabled: Boolean): String?

    suspend fun applyYamlPreview(previewId: String): Boolean

    /** Single entry from `proxies:` as YAML, for display. */
    suspend fun readProxyEntryYaml(uuid: UUID, proxyName: String): String?

    /** Remember selector choice before VPN/engine applies [Clash.patchSelector]. */
    suspend fun rememberProxySelection(uuid: UUID, group: String, name: String)

    /** Saved [Selection] rows for profile (group → selected proxy). */
    suspend fun queryProxySelections(uuid: UUID): Map<String, String>

    /** Raw `config.yaml` for an imported profile, or null. */
    suspend fun readImportedConfigYaml(uuid: UUID): String?
}

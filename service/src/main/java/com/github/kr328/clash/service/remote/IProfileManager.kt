package com.github.kr328.clash.service.remote

import com.github.kr328.clash.service.model.Profile
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

    /** Proxy group name → member names from [uuid]'s config.yaml (no engine). */
    suspend fun readProxyGroupsPreview(uuid: UUID): Map<String, List<String>>

    /** `rule-providers` YAML block or null. */
    suspend fun readRuleProvidersYaml(uuid: UUID): String?

    suspend fun replaceRuleProvidersYaml(uuid: UUID, yaml: String): Boolean

    /** Single entry from `proxies:` as YAML, for display. */
    suspend fun readProxyEntryYaml(uuid: UUID, proxyName: String): String?

    /** Remember selector choice before VPN/engine applies [Clash.patchSelector]. */
    suspend fun rememberProxySelection(uuid: UUID, group: String, name: String)

    /** Saved [Selection] rows for profile (group → selected proxy). */
    suspend fun queryProxySelections(uuid: UUID): Map<String, String>

    /** Raw `config.yaml` for an imported profile, or null. */
    suspend fun readImportedConfigYaml(uuid: UUID): String?
}
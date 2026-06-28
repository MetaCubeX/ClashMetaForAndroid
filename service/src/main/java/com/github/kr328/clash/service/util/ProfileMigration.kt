package com.github.kr328.clash.service.util

import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.model.ProfileSnapshot
import com.github.kr328.clash.service.model.RuleState
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * One-time, per-profile migration from the legacy "edits baked into config.yaml" model to the
 * overlay model (config-overlay-architecture, Group 5). Designed so that **updating the app just
 * works**: on first contact a profile gets a `subscription.yaml` base + a `user_layer.json` of its
 * extracted edits, while `config.yaml` is left exactly as-is (zero behavior change on first launch).
 *
 * Safety rests on two facts:
 *  - the base `subscription.yaml` is the current `config.yaml` (which already contains the baked
 *    edits), and every composition op is idempotent (distinct rules, whole-block replace, map
 *    union, dialer set) — so re-materializing does not double-apply;
 *  - on the next subscription update the freshly fetched config replaces `subscription.yaml` with a
 *    clean base, and the extracted layer is applied cleanly from then on.
 *
 * The DNS/hosts/tunnels extraction needs the native parser, injected as [parseSnapshot] so this is
 * unit-testable without JNI; a null/failed parse simply skips those sections (they remain in the
 * base until the next fetch).
 */
object ProfileMigration {
    private val json = Json { ignoreUnknownKeys = true }

    /** True once a profile has a `subscription.yaml` — migration is idempotent on that marker. */
    fun isMigrated(profileDir: File): Boolean =
        File(profileDir, ProfileComposer.SUBSCRIPTION_FILE).isFile

    /**
     * Migrates [profileDir] if it has not been migrated yet. Returns true when migration ran.
     *
     * @param rulesStateJson  contents of `rules_state.json`, or null if the user never edited rules
     * @param dnsHostsManaged whether the user owns this profile's DNS/hosts (managed flag)
     * @param tunnelsManaged  whether the user owns this profile's tunnels (managed flag)
     * @param parseSnapshot   native snapshot parser (real at runtime; a fake in tests)
     */
    fun migrateIfNeeded(
        profileDir: File,
        uuid: UUID,
        store: UserLayerStore,
        rulesStateJson: String?,
        dnsHostsManaged: Boolean,
        tunnelsManaged: Boolean,
        parseSnapshot: (File) -> ProfileSnapshot?,
    ): Boolean {
        val config = File(profileDir, ProfileComposer.CONFIG_FILE)
        if (!config.isFile) return false
        if (isMigrated(profileDir)) return false

        // 1. The current effective config becomes the subscription base (untouched going forward).
        File(profileDir, ProfileComposer.SUBSCRIPTION_FILE).writeText(config.readText())

        // 2. Extract the user's edits as intent.
        val rules = rulesStateJson
            ?.let { runCatching { json.decodeFromString(RuleState.serializer(), it) }.getOrNull() }
            ?: RuleState()

        val chain = runCatching { ProxyDialerYamlEdit.listDialerChains(profileDir) }
            .getOrDefault(emptyList())
            .associate { it.targetName to it.dialerName }

        val snapshot = if (dnsHostsManaged || tunnelsManaged) {
            runCatching { parseSnapshot(profileDir) }.getOrNull()
        } else {
            null
        }
        val dnsHosts = if (dnsHostsManaged && snapshot != null) {
            runCatching { DnsHostsConfig.fromSnapshot(snapshot) }.getOrNull()
        } else {
            null
        }
        val tunnels = if (tunnelsManaged && snapshot != null) {
            runCatching { TunnelsConfig.fromSnapshot(snapshot) }.getOrNull()
        } else {
            null
        }

        val layer = UserLayer(
            rules = rules,
            dnsHosts = dnsHosts,
            tunnels = tunnels,
            proxyChain = chain,
        )
        store.save(uuid, layer)
        Log.d(
            "ProfileMigration: migrated ${profileDir.name} " +
                "(rules=${rules.rules.size} chain=${chain.size} dns=${dnsHosts != null} tunnels=${tunnels != null})",
        )
        return true
    }
}

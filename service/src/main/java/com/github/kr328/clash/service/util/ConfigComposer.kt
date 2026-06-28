package com.github.kr328.clash.service.util

import com.github.kr328.clash.service.model.ProxyHardeningMode

/**
 * Builds the config the engine actually receives, the Clash-Verge-Rev way: take the fetched
 * subscription **as-is**, apply the user's edit layer on top with non-reconciling merge operations,
 * then harden the composed result. config-overlay-architecture, Group 3.
 *
 * The subscription YAML is never rewritten on disk — composition happens at apply time and is a
 * pure text-in/text-out function, so it can be validated by the engine oracle (gate, Group 1)
 * before anything reaches the running tunnel.
 *
 * Each step reuses an already-netted renderer; there is deliberately **no identity-reconciliation,
 * no orphan-detection and no garbage-collection** here — only:
 *  - replay-intent: user rules + rule-providers ([RuleMapper.mergeStateIntoConfig])
 *  - whole-block replace: `dns`/`hosts` ([DnsHostsYamlEdit]) and `tunnels` ([TunnelsYamlEdit])
 *  - map union: user proxy-providers ([ProxyProvidersYamlEdit])
 *
 * Hardening runs **last**, on the fully composed config, so user-added listeners/DNS cannot slip
 * past the loopback/TUN safety filter (P0).
 *
 * Order within the merge does not matter for correctness (each renderer targets a distinct top-level
 * key); hardening last is the only ordering invariant.
 */
object ConfigComposer {
    /**
     * @param fetchedYaml   the subscription config exactly as fetched (untouched base)
     * @param layer         the user's edits, as intent
     * @param geoDataUrls   resolved geo-data source URLs (rule rendering needs them); caller pulls
     *                      these from settings so this function stays pure/testable
     * @param hardeningMode strict/compat/off — applied to the composed result
     */
    fun compose(
        fetchedYaml: String,
        layer: UserLayer,
        geoDataUrls: GeoDataUrls,
        hardeningMode: ProxyHardeningMode,
    ): String {
        var doc = fetchedYaml

        if (layer.rules.rules.isNotEmpty() || layer.rules.providers.isNotEmpty()) {
            // Additive (prepend rules + union providers) — NOT mergeStateIntoConfig, which replaces
            // both blocks and would wipe the subscription's rules/providers.
            doc = RuleMapper.composeUserRulesOnto(doc, layer.rules, geoDataUrls)
        }
        layer.dnsHosts?.let { doc = DnsHostsYamlEdit.render(doc, it) }
        layer.tunnels?.let { doc = TunnelsYamlEdit.render(doc, it) }
        layer.proxyProviders?.takeIf { it.isNotBlank() }?.let {
            doc = ProxyProvidersYamlEdit.mergeIntoConfig(doc, it)
        }
        layer.ruleProviders?.takeIf { it.isNotBlank() }?.let {
            doc = RuleProvidersYamlEdit.mergeIntoConfig(doc, it)
        }
        for (group in layer.relayGroups) {
            doc = ProxyGroupsYamlEdit.appendSelectGroupUsingProviders(doc, group.name, group.providerKeys) ?: doc
        }
        if (layer.proxyChain.isNotEmpty()) {
            // dialer-proxy on `proxies:` in config.yaml; targets inside provider files are replayed
            // file-side on the apply path (no file access here).
            doc = ProxyDialerYamlEdit.applyChainToConfigText(doc, layer.proxyChain)
        }

        // Hardening LAST — on everything that will reach the engine.
        return YamlHardener.hardenYaml(doc, hardeningMode) ?: doc
    }
}

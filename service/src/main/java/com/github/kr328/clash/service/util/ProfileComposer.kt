package com.github.kr328.clash.service.util

import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.model.ProxyHardeningMode
import java.io.File

/**
 * File-level wrapper around [ConfigComposer] (config-overlay-architecture, Group 3 wiring).
 *
 * Profile layout under `importedDir/<uuid>/`:
 *  - `subscription.yaml` — the fetched subscription, **untouched** (the compose base);
 *  - `config.yaml`       — the composed, effective config the engine loads and previews read;
 *  - `user_layer.json`   — the user's edits (intent), see [UserLayerStore].
 *
 * [materialize] regenerates `config.yaml` from `subscription.yaml` + the user layer. It is
 * idempotent (always composes from the canonical subscription, never from a previously composed
 * `config.yaml`), so it is safe to call after every update/import, after every edit, and at VPN
 * start before `Clash.load`.
 */
object ProfileComposer {
    const val SUBSCRIPTION_FILE = "subscription.yaml"
    const val CONFIG_FILE = "config.yaml"

    /** The canonical fetched subscription file, falling back to `config.yaml` before migration. */
    fun subscriptionFile(profileDir: File): File {
        val sub = File(profileDir, SUBSCRIPTION_FILE)
        return if (sub.isFile) sub else File(profileDir, CONFIG_FILE)
    }

    /**
     * Composes `subscription.yaml` + [layer] and writes the result to `config.yaml`.
     *
     * @return true when `config.yaml` was written; false when there is no subscription to compose.
     */
    fun materialize(
        profileDir: File,
        layer: UserLayer,
        geoDataUrls: GeoDataUrls,
        hardeningMode: ProxyHardeningMode,
    ): Boolean {
        val base = subscriptionFile(profileDir)
        if (!base.isFile) {
            Log.w("ProfileComposer: no subscription to compose in ${profileDir.name}")
            return false
        }
        val fetched = base.readText()
        val composed = ConfigComposer.compose(fetched, layer, geoDataUrls, hardeningMode)
        File(profileDir, CONFIG_FILE).writeText(composed)

        // proxy-chain targets that live in proxy-provider files (not in config.yaml `proxies:`) are
        // replayed file-side here — ConfigComposer only reaches the config.yaml proxies list.
        for ((target, dialer) in layer.proxyChain) {
            runCatching { ProxyDialerYamlEdit.applyDialerProxy(profileDir, target, dialer) }
                .onFailure { Log.w("ProfileComposer: dialer replay failed for $target", it) }
        }
        return true
    }
}

package com.github.kr328.clash.service.util

import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.model.RuleState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * The user's in-app edits for a profile, stored as **intent** separate from the subscription
 * `config.yaml` (config-overlay-architecture, Group 2). This is ClashFest's equivalent of a
 * Clash-Verge-Rev "Merge profile": the subscription stays exactly as fetched, and this layer is
 * composed on top at apply time using only non-reconciling operations (Group 3).
 *
 * Each slot reuses an existing serializable edit model where one exists, so the editors can move
 * onto this store without inventing new shapes:
 *  - [rules]          rule editor state (also carries user rule-providers via [RuleState.providers])
 *  - [dnsHosts]       the user's `dns:` + `hosts:` override (whole-block replace)
 *  - [tunnels]        the user's `tunnels:` override (whole-block replace)
 *  - [proxyProviders] user-added proxy-providers as the inner-map YAML under `proxy-providers:`
 *                     (exactly what `ProxyProvidersYamlEdit.mergeIntoConfig` consumes; union)
 *  - [proxyChain]     proxy-chain (dialer-proxy) intent: target proxy name → dialer proxy name
 *
 * Population of the slots (re-pointing the editors) is Group 2.3; composition is Group 3. Until
 * then this type is inert.
 */
@Serializable
data class UserLayer(
    /** Schema version, for forward-compatible migration of the on-disk file. */
    val version: Int = CURRENT_VERSION,
    val rules: RuleState = RuleState(),
    val dnsHosts: DnsHostsConfig? = null,
    val tunnels: TunnelsConfig? = null,
    val proxyProviders: String? = null,
    val proxyChain: Map<String, String> = emptyMap(),
) {
    /** True when the user has no edits — nothing to compose on top of the subscription. */
    fun isEmpty(): Boolean =
        rules.rules.isEmpty() &&
            rules.providers.isEmpty() &&
            dnsHosts == null &&
            tunnels == null &&
            proxyProviders.isNullOrBlank() &&
            proxyChain.isEmpty()

    companion object {
        const val CURRENT_VERSION = 1
    }
}

/**
 * Reads/writes the per-profile [UserLayer] at `importedDir/<uuid>/user_layer.json`, mirroring how
 * the rule state and proxy-provider labels are already persisted next to `config.yaml`.
 */
class UserLayerStore(private val importedDir: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private fun fileOf(uuid: UUID): File = File(importedDir, "$uuid/$FILE_NAME")

    /** The stored layer, or an empty layer when there is no file / it cannot be parsed. */
    fun load(uuid: UUID): UserLayer {
        val f = fileOf(uuid)
        if (!f.isFile) return UserLayer()
        return runCatching { json.decodeFromString(UserLayer.serializer(), f.readText()) }
            .getOrElse {
                Log.w("UserLayerStore: failed to parse ${f.name} for $uuid, treating as empty", it)
                UserLayer()
            }
    }

    /** Persists the layer; removes the file when the layer is empty (no edits to keep). */
    fun save(uuid: UUID, layer: UserLayer) {
        val f = fileOf(uuid)
        if (layer.isEmpty()) {
            f.delete()
            return
        }
        f.parentFile?.mkdirs()
        f.writeText(json.encodeToString(UserLayer.serializer(), layer))
    }

    /** Mutate-and-save helper for per-section editors. */
    fun update(uuid: UUID, block: (UserLayer) -> UserLayer) {
        save(uuid, block(load(uuid)))
    }

    fun clear(uuid: UUID) {
        fileOf(uuid).delete()
    }

    fun exists(uuid: UUID): Boolean = fileOf(uuid).isFile

    companion object {
        const val FILE_NAME = "user_layer.json"
    }
}

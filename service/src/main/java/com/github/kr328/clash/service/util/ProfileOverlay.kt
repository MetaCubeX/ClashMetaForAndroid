package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ProfileSnapshot
import com.github.kr328.clash.service.model.ProxyHardeningMode
import com.github.kr328.clash.service.store.ServiceStore
import java.io.File
import java.util.UUID

/**
 * Single entry point for keeping a profile's effective `config.yaml` in sync with the overlay model
 * (config-overlay-architecture): lazily migrate a legacy profile, then re-derive `config.yaml` from
 * `subscription.yaml` + the user layer. Shared by every live call site (VPN start, subscription
 * import/update, in-app edits) so the migrate-then-compose sequence is defined in exactly one place.
 *
 * All inputs are passed explicitly (settings + native parser resolved by the caller) so this stays
 * unit-testable without JNI / Android.
 */
object ProfileOverlay {
    /**
     * @return true when `config.yaml` was (re)written.
     */
    fun refresh(
        profileDir: File,
        uuid: UUID,
        userLayerStore: UserLayerStore,
        rulesStateJson: String?,
        dnsHostsManaged: Boolean,
        tunnelsManaged: Boolean,
        geoDataUrls: GeoDataUrls,
        hardeningMode: ProxyHardeningMode,
        parseSnapshot: (File) -> ProfileSnapshot?,
    ): Boolean {
        ProfileMigration.migrateIfNeeded(
            profileDir = profileDir,
            uuid = uuid,
            store = userLayerStore,
            rulesStateJson = rulesStateJson,
            dnsHostsManaged = dnsHostsManaged,
            tunnelsManaged = tunnelsManaged,
            parseSnapshot = parseSnapshot,
        )
        return ProfileComposer.materialize(
            profileDir = profileDir,
            layer = userLayerStore.load(uuid),
            geoDataUrls = geoDataUrls,
            hardeningMode = hardeningMode,
        )
    }

    /**
     * Production convenience over [refresh] that resolves every input from [store] + the native
     * engine. Used by the live call sites (VPN start, subscription import/update). Kept separate
     * from the pure [refresh] so the latter stays unit-testable without JNI/Android.
     */
    fun refreshFromStore(
        profileDir: File,
        uuid: UUID,
        importedDir: File,
        store: ServiceStore,
    ): Boolean {
        val rulesStateJson = File(profileDir, "rules_state.json")
            .takeIf { it.isFile }
            ?.let { runCatching { it.readText() }.getOrNull() }
        return refresh(
            profileDir = profileDir,
            uuid = uuid,
            userLayerStore = UserLayerStore(importedDir),
            rulesStateJson = rulesStateJson,
            dnsHostsManaged = store.isDnsHostsManaged(uuid),
            tunnelsManaged = store.isTunnelsManaged(uuid),
            geoDataUrls = GeoDataSources.resolve(
                preset = store.geoDataSourcePreset,
                customGeoIp = store.geoDataCustomGeoIp,
                customGeoSite = store.geoDataCustomGeoSite,
                customMmdb = store.geoDataCustomMmdb,
                customAsn = store.geoDataCustomAsn,
            ),
            hardeningMode = store.proxyHardeningMode,
            parseSnapshot = { d -> runCatching { Clash.parseProfileSnapshot(d) }.getOrNull() },
        )
    }
}

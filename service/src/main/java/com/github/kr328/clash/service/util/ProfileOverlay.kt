package com.github.kr328.clash.service.util

import com.github.kr328.clash.core.model.ProfileSnapshot
import com.github.kr328.clash.service.model.ProxyHardeningMode
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
}

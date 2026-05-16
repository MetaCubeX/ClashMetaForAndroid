package com.github.kr328.clash.service.branding

import android.content.Context
import com.github.kr328.clash.common.branding.BrandManifest
import com.github.kr328.clash.common.log.Log
import java.io.File
import java.util.UUID

/**
 * Coordinates "operator brand has changed" — applies a freshly-parsed
 * [BrandManifest] from a subscription fetch to the persistent [BrandStore],
 * downloading logo bytes when needed and pruning stale caches.
 *
 * Single entry point for the app layer; the store-layer details
 * (paths, SharedPreferences keys, atomic writes) stay encapsulated.
 */
object BrandRefresh {

    /**
     * Apply [manifest] coming from a fetch against [sourceProfile]'s subscription
     * URL. If the user has explicitly reset branding for this app instance, the
     * operator's headers are still parsed but no UI state is changed until the
     * user opts back in (we keep flag, not value).
     *
     * @return true when [BrandStore] state changed.
     */
    suspend fun apply(context: Context, sourceProfile: UUID, manifest: BrandManifest): Boolean {
        val store = BrandStore(context)

        // Empty manifest from the same profile = operator turned branding off → clear.
        if (manifest.isEmpty()) {
            if (store.sourceProfile == sourceProfile && store.isActive()) {
                pruneLogos(context, retain = null)
                store.clear()
                Log.d("BrandRefresh: operator cleared branding for $sourceProfile")
                return true
            }
            return false
        }

        val previousManifest = store.manifest
        val previousProfile = store.sourceProfile

        val logoUrl = manifest.logoUrl?.takeIf { it.isNotBlank() }
        val logoLightUrl = manifest.logoLightUrl?.takeIf { it.isNotBlank() }
        val primaryPath = logoUrl?.let { BrandLogoFetcher.fetch(context, it) }
        val lightPath = logoLightUrl?.let { BrandLogoFetcher.fetch(context, it) }

        store.sourceProfile = sourceProfile
        store.manifest = manifest
        store.cachedLogoPath = primaryPath
        store.cachedLightLogoPath = lightPath

        pruneLogos(context, retain = listOfNotNull(primaryPath, lightPath))

        val changed =
            previousManifest != manifest ||
                previousProfile != sourceProfile

        if (changed) Log.d("BrandRefresh: applied brand from $sourceProfile (name=${manifest.name})")
        return changed
    }

    /**
     * Clear the active brand because [sourceProfile] no longer exists.
     * No-op when the store wasn't pointing at that profile.
     */
    fun onProfileDeleted(context: Context, sourceProfile: UUID) {
        val store = BrandStore(context)
        if (store.sourceProfile != sourceProfile) return
        pruneLogos(context, retain = null)
        store.clear()
        Log.d("BrandRefresh: cleared brand because $sourceProfile was deleted")
    }

    /**
     * One-shot wipe of cached brand state. The next subscription fetch will
     * re-apply whatever the operator is currently sending — this is not a
     * "lock branding off" toggle.
     */
    fun resetByUser(context: Context) {
        val store = BrandStore(context)
        pruneLogos(context, retain = null)
        store.clear()
    }

    private fun pruneLogos(context: Context, retain: List<String>?) {
        val dir = File(context.filesDir, "brand")
        if (!dir.isDirectory) return
        val keep = retain?.mapNotNull {
            runCatching { File(it).canonicalPath }.getOrNull()
        }?.toSet().orEmpty()
        dir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val canonical = runCatching { file.canonicalPath }.getOrNull()
            if (canonical != null && canonical in keep) return@forEach
            file.delete()
        }
    }
}

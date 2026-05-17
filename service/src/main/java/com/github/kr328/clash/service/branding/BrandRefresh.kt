package com.github.kr328.clash.service.branding

import android.content.Context
import com.github.kr328.clash.common.branding.BrandManifest
import com.github.kr328.clash.common.log.Log
import java.io.File
import java.util.UUID

/**
 * Coordinates per-profile operator-brand state — applies a freshly-parsed
 * [BrandManifest] from a subscription fetch to [BrandStore] for that
 * specific profile UUID, downloads logo bytes when needed, and prunes
 * stale caches that no longer belong to any profile.
 */
object BrandRefresh {

    /**
     * Apply [manifest] for [sourceProfile]'s subscription. Empty manifest
     * means "operator stopped sending brand headers" — clear the entry.
     *
     * @return true when [BrandStore] state changed for this profile.
     */
    suspend fun apply(context: Context, sourceProfile: UUID, manifest: BrandManifest): Boolean {
        val store = BrandStore(context)
        val previous = store.manifestFor(sourceProfile)

        if (manifest.isEmpty()) {
            if (store.isActiveFor(sourceProfile)) {
                store.clearFor(sourceProfile)
                pruneOrphanLogos(context)
                Log.d("BrandRefresh: operator cleared branding for $sourceProfile")
                return true
            }
            return false
        }

        val logoUrl = manifest.logoUrl?.takeIf { it.isNotBlank() }
        val logoLightUrl = manifest.logoLightUrl?.takeIf { it.isNotBlank() }

        // Keep previously cached paths around so a transient fetch failure
        // (VPN routing quirk, server hiccup, SSRF guard tripping during a
        // resolve through the tunnel) doesn't visually strip the logo.
        // Logo URL change AND successful new fetch is what advances the path;
        // failures retain the last working file.
        val prevPrimary = store.logoPathFor(sourceProfile, darkTheme = true)
        val prevLight = run {
            // logoPathFor(uuid, false) falls back to primary when light is missing,
            // so read the raw light-only slot here.
            val both = store.logoPathFor(sourceProfile, darkTheme = false)
            if (both == prevPrimary) null else both
        }
        val primaryPath = if (logoUrl != null) {
            BrandLogoFetcher.fetch(context, logoUrl) ?: prevPrimary
        } else null
        val lightPath = if (logoLightUrl != null) {
            BrandLogoFetcher.fetch(context, logoLightUrl) ?: prevLight
        } else null

        store.setManifest(sourceProfile, manifest)
        store.setLogoPaths(sourceProfile, primaryPath, lightPath)
        pruneOrphanLogos(context)

        val changed = previous != manifest
        if (changed) Log.d("BrandRefresh: applied brand from $sourceProfile (name=${manifest.name})")
        return changed
    }

    /** Clear brand state for a deleted profile. */
    fun onProfileDeleted(context: Context, sourceProfile: UUID) {
        val store = BrandStore(context)
        if (!store.isActiveFor(sourceProfile)) return
        store.clearFor(sourceProfile)
        pruneOrphanLogos(context)
        Log.d("BrandRefresh: cleared brand because $sourceProfile was deleted")
    }

    /**
     * Delete every cached logo file under `<filesDir>/brand/` that no live
     * profile references. Called after any apply/clear so the cache directory
     * doesn't grow unbounded across operator-side logo URL changes.
     *
     * Currently uses string-contains across all profile entries — cheap and
     * good enough for a handful of profiles.
     */
    private fun pruneOrphanLogos(context: Context) {
        val dir = File(context.filesDir, "brand")
        if (!dir.isDirectory) return
        // Collect every path currently referenced by some profile.
        val keep = HashSet<String>()
        val prefs = com.github.kr328.clash.service.PreferenceProvider
            .createSharedPreferencesFromContext(context)
        prefs.all.forEach { (key, value) ->
            if (value !is String) return@forEach
            if (key.startsWith("brand_logo")) {
                runCatching { File(value).canonicalPath }.getOrNull()?.let(keep::add)
            }
        }
        dir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val canonical = runCatching { file.canonicalPath }.getOrNull()
            if (canonical != null && canonical in keep) return@forEach
            file.delete()
        }
    }
}

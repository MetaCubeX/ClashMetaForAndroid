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

    /** Consecutive confirmed-empty responses before a previously-applied brand is cleared. */
    const val EMPTY_STREAK_TO_CLEAR = 3

    internal data class EmptyAction(val clear: Boolean, val newStreak: Int, val streakChanged: Boolean)

    /**
     * Decide what to do with a previously-applied brand when the fresh manifest is empty.
     * Pure (no I/O) so it is unit-testable.
     *
     * - [confirmed] is true only for an empty parsed from a *successful* (HTTP 2xx) subscription
     *   response — i.e. the operator served the subscription but sent no `X-Brand-*` headers.
     *   An unconfirmed empty (opportunistic fetch that may have just failed) never counts.
     * - Clearing is debounced: only after [threshold] consecutive confirmed-empty responses, so a
     *   one-off deploy that drops the headers doesn't strip a brand the user already saw.
     */
    internal fun decideOnEmpty(confirmed: Boolean, isActive: Boolean, streak: Int, threshold: Int): EmptyAction {
        if (!confirmed) return EmptyAction(clear = false, newStreak = streak, streakChanged = false)
        if (!isActive) return EmptyAction(clear = false, newStreak = 0, streakChanged = streak != 0)
        val next = streak + 1
        return if (next >= threshold) {
            EmptyAction(clear = true, newStreak = 0, streakChanged = true)
        } else {
            EmptyAction(clear = false, newStreak = next, streakChanged = true)
        }
    }

    /**
     * Apply [manifest] for [sourceProfile]'s subscription.
     *
     * @param confirmedResponse true when [manifest] was parsed from a confirmed successful (HTTP
     *   2xx) subscription response, so an empty manifest genuinely means "operator served the sub
     *   without brand headers" (not a transient fetch failure). Drives debounced clearing.
     * @return true when [BrandStore] state changed for this profile.
     */
    suspend fun apply(
        context: Context,
        sourceProfile: UUID,
        manifest: BrandManifest,
        confirmedResponse: Boolean = false,
    ): Boolean {
        val store = BrandStore(context)
        val previous = store.manifestFor(sourceProfile)

        // Explicit kill-switch from the operator → wipe.
        if (manifest.enabled == false) {
            if (store.isActiveFor(sourceProfile)) {
                store.clearFor(sourceProfile)
                pruneOrphanLogos(context)
                Log.d("BrandRefresh: operator disabled branding via X-Branding-Enabled=false for $sourceProfile")
                return true
            }
            return false
        }

        // Empty response. An UNCONFIRMED empty (opportunistic fetch that may have just failed, or
        // a flaky deploy) never drops a brand the user already saw. A CONFIRMED empty (operator
        // served the subscription over HTTP 2xx with no X-Brand-* headers) is debounced: after
        // EMPTY_STREAK_TO_CLEAR consecutive confirmed-empties the brand is cleared, so a panel that
        // permanently stops branding eventually resets instead of sticking forever.
        if (manifest.isEmpty()) {
            val action = decideOnEmpty(
                confirmed = confirmedResponse,
                isActive = store.isActiveFor(sourceProfile),
                streak = store.emptyStreakFor(sourceProfile),
                threshold = EMPTY_STREAK_TO_CLEAR,
            )
            if (action.clear) {
                store.clearFor(sourceProfile)
                pruneOrphanLogos(context)
                Log.d("BrandRefresh: cleared brand after $EMPTY_STREAK_TO_CLEAR confirmed empty responses for $sourceProfile")
                return true
            }
            if (action.streakChanged) store.setEmptyStreak(sourceProfile, action.newStreak)
            Log.d("BrandRefresh: empty manifest from $sourceProfile (confirmed=$confirmedResponse, streak=${action.newStreak}), keeping cached state")
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
        // Headers are back (non-empty) → reset the confirmed-empty debounce counter.
        store.setEmptyStreak(sourceProfile, 0)
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

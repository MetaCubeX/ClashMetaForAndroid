package com.github.kr328.clash.service.branding

import android.content.Context
import com.github.kr328.clash.common.branding.BrandManifest
import com.github.kr328.clash.service.PreferenceProvider
import java.util.UUID

/**
 * Per-subscription operator-brand persistence.
 *
 * Each imported profile keeps its own brand snapshot — switching the active
 * profile flips the visible brand immediately, without any subscription
 * refetch. When a profile is deleted, its entry is purged on the spot.
 *
 * Backed by the same SharedPreferences as ServiceStore. All keys are scoped
 * by the source profile UUID so unrelated profiles never collide.
 */
class BrandStore(context: Context) {
    private val prefs = PreferenceProvider.createSharedPreferencesFromContext(context)

    /**
     * Manifest stored for [uuid]. Returns [BrandManifest.EMPTY] when no brand
     * was ever applied for that subscription.
     */
    fun manifestFor(uuid: UUID): BrandManifest =
        BrandManifest.fromJson(prefs.getString(manifestKey(uuid), null))

    fun setManifest(uuid: UUID, manifest: BrandManifest) {
        prefs.edit().also { e ->
            if (manifest.isEmpty()) e.remove(manifestKey(uuid))
            else e.putString(manifestKey(uuid), manifest.toJson())
        }.apply()
    }

    fun logoPathFor(uuid: UUID, darkTheme: Boolean): String? {
        val primary = prefs.getString(logoKey(uuid), null)
        val light = prefs.getString(logoLightKey(uuid), null)
        return if (darkTheme) primary else (light ?: primary)
    }

    fun setLogoPaths(uuid: UUID, primary: String?, light: String?) {
        prefs.edit().also { e ->
            if (primary.isNullOrBlank()) e.remove(logoKey(uuid)) else e.putString(logoKey(uuid), primary)
            if (light.isNullOrBlank()) e.remove(logoLightKey(uuid)) else e.putString(logoLightKey(uuid), light)
        }.apply()
    }

    /**
     * Whether [uuid]'s subscription carries a real brand identity (name /
     * logo / accent). Operator-info URLs alone do NOT count — having only a
     * support URL must not cause "Operator" UI to appear.
     */
    fun isActiveFor(uuid: UUID): Boolean = manifestFor(uuid).hasBrandIdentity()

    fun clearFor(uuid: UUID) {
        prefs.edit()
            .remove(manifestKey(uuid))
            .remove(logoKey(uuid))
            .remove(logoLightKey(uuid))
            .remove(emptyStreakKey(uuid))
            .apply()
    }

    /**
     * Count of consecutive *confirmed* (HTTP 200) subscription responses that carried no brand
     * headers. Used to debounce clearing a brand: a one-off deploy that drops the headers must
     * not strip a brand, but a panel that permanently stops sending them eventually should.
     */
    fun emptyStreakFor(uuid: UUID): Int = prefs.getInt(emptyStreakKey(uuid), 0)

    fun setEmptyStreak(uuid: UUID, value: Int) {
        prefs.edit().also { e ->
            if (value <= 0) e.remove(emptyStreakKey(uuid)) else e.putInt(emptyStreakKey(uuid), value)
        }.apply()
    }

    /**
     * Accent hex (`#RRGGBB`) of the theme overlay actually applied to the
     * current Activity. Tracked separately from manifests so the design
     * layer can detect when an Activity needs to be recreated.
     */
    var lastAppliedAccent: String
        get() = prefs.getString(KEY_LAST_APPLIED_ACCENT, "").orEmpty()
        set(value) {
            prefs.edit().also { e ->
                if (value.isBlank()) e.remove(KEY_LAST_APPLIED_ACCENT)
                else e.putString(KEY_LAST_APPLIED_ACCENT, value)
            }.apply()
        }

    private fun manifestKey(uuid: UUID) = "${KEY_PREFIX_MANIFEST}_$uuid"
    private fun logoKey(uuid: UUID) = "${KEY_PREFIX_LOGO}_$uuid"
    private fun logoLightKey(uuid: UUID) = "${KEY_PREFIX_LOGO_LIGHT}_$uuid"
    private fun emptyStreakKey(uuid: UUID) = "${KEY_PREFIX_EMPTY_STREAK}_$uuid"

    companion object {
        private const val KEY_PREFIX_MANIFEST = "brand_manifest"
        private const val KEY_PREFIX_LOGO = "brand_logo"
        private const val KEY_PREFIX_LOGO_LIGHT = "brand_logo_light"
        private const val KEY_PREFIX_EMPTY_STREAK = "brand_empty_streak"
        private const val KEY_LAST_APPLIED_ACCENT = "brand_last_applied_accent"
    }
}

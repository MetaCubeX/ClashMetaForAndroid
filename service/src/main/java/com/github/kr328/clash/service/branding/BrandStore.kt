package com.github.kr328.clash.service.branding

import android.content.Context
import com.github.kr328.clash.common.branding.BrandManifest
import com.github.kr328.clash.service.PreferenceProvider
import java.util.UUID

/**
 * Persistence for the currently-active operator brand manifest.
 *
 * State model is intentionally simple: at any moment there is **one active
 * brand**, attached to **one source profile UUID**. When that profile is
 * deleted (or branding is reset by the user, or the operator stops sending
 * brand headers on the next subscription fetch), the store clears itself.
 *
 * Backed by the same SharedPreferences as ServiceStore — values survive
 * app restarts.
 */
class BrandStore(context: Context) {
    private val prefs = PreferenceProvider.createSharedPreferencesFromContext(context)

    var sourceProfile: UUID?
        get() = prefs.getString(KEY_SOURCE_PROFILE, null)?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }
        set(value) {
            prefs.edit().also { e ->
                if (value == null) e.remove(KEY_SOURCE_PROFILE)
                else e.putString(KEY_SOURCE_PROFILE, value.toString())
            }.apply()
        }

    var manifest: BrandManifest
        get() = BrandManifest.fromJson(prefs.getString(KEY_MANIFEST, null))
        set(value) {
            prefs.edit().also { e ->
                if (value.isEmpty()) e.remove(KEY_MANIFEST)
                else e.putString(KEY_MANIFEST, value.toJson())
            }.apply()
        }

    /** Local cache path of the most recently downloaded primary logo, if any. */
    var cachedLogoPath: String?
        get() = prefs.getString(KEY_CACHED_LOGO_PATH, null)
        set(value) {
            prefs.edit().also { e ->
                if (value.isNullOrBlank()) e.remove(KEY_CACHED_LOGO_PATH)
                else e.putString(KEY_CACHED_LOGO_PATH, value)
            }.apply()
        }

    /** Local cache path of the light-theme logo variant, if any. */
    var cachedLightLogoPath: String?
        get() = prefs.getString(KEY_CACHED_LIGHT_LOGO_PATH, null)
        set(value) {
            prefs.edit().also { e ->
                if (value.isNullOrBlank()) e.remove(KEY_CACHED_LIGHT_LOGO_PATH)
                else e.putString(KEY_CACHED_LIGHT_LOGO_PATH, value)
            }.apply()
        }

    /**
     * Accent hex (`#RRGGBB`) of the theme overlay actually applied to the
     * current Activity. Tracked separately from [manifest] so the design
     * layer can detect when an Activity needs to be recreated to refresh its
     * Material colour roles.
     */
    var lastAppliedAccent: String
        get() = prefs.getString(KEY_LAST_APPLIED_ACCENT, "").orEmpty()
        set(value) {
            prefs.edit().also { e ->
                if (value.isBlank()) e.remove(KEY_LAST_APPLIED_ACCENT)
                else e.putString(KEY_LAST_APPLIED_ACCENT, value)
            }.apply()
        }

    fun isActive(): Boolean = !manifest.isEmpty() && sourceProfile != null

    fun clear() {
        prefs.edit()
            .remove(KEY_SOURCE_PROFILE)
            .remove(KEY_MANIFEST)
            .remove(KEY_CACHED_LOGO_PATH)
            .remove(KEY_CACHED_LIGHT_LOGO_PATH)
            // lastAppliedAccent is intentionally NOT cleared — keeps the
            // recreate cycle from oscillating across resets.
            .apply()
    }

    companion object {
        private const val KEY_SOURCE_PROFILE = "brand_source_profile"
        private const val KEY_MANIFEST = "brand_manifest_json"
        private const val KEY_CACHED_LOGO_PATH = "brand_cached_logo_path"
        private const val KEY_CACHED_LIGHT_LOGO_PATH = "brand_cached_light_logo_path"
        private const val KEY_LAST_APPLIED_ACCENT = "brand_last_applied_accent"
    }
}

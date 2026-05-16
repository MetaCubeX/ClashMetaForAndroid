package com.github.kr328.clash.design.branding

import android.app.Activity
import android.graphics.Color
import com.github.kr328.clash.common.branding.BrandManifest
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions

/**
 * Applies the operator-supplied accent as the Material 3 colorPrimary seed for
 * an entire Activity. Generates a harmonised tonal palette from one hex value
 * via Material's content-based dynamic colors, so every widget that reaches
 * for `colorPrimary` / `colorPrimaryContainer` / `colorOnPrimary` / etc.
 * automatically follows.
 *
 * **Must be called before `setContentView` in the Activity's onCreate.**
 *
 * Reads the persisted brand directly from SharedPreferences (sync) — we can't
 * wait for the AIDL profile manager because that happens after the Activity
 * has already inflated its theme.
 */
object BrandThemeApplier {

    /**
     * Apply the operator accent as the **final** theme overlay on [activity].
     *
     * Must run AFTER any user-picked palette / system dynamic-color overlays
     * inside the host theme chain so the operator has the last word — calling
     * this before, say, `paletteOverlay` from UiStore will silently lose to
     * the later palette applyStyle.
     */
    fun applyToActivity(activity: Activity) {
        val store = com.github.kr328.clash.service.branding.BrandStore(activity)
        if (!store.isActive()) return
        val accent = parseHexColor(store.manifest.accentColor) ?: return

        // `applyToActivityIfAvailable`'s default precondition refuses to apply
        // on pre-Android-12 because system "wallpaper dynamic color" isn't
        // there. We want a content-based palette derived from the operator
        // accent, which works on every Android version — override the
        // precondition to always-true.
        DynamicColors.applyToActivityIfAvailable(
            activity,
            DynamicColorsOptions.Builder()
                .setContentBasedSource(accent)
                .setPrecondition { _, _ -> true }
                .build(),
        )
        com.github.kr328.clash.common.log.Log.d(
            "BrandThemeApplier: applied accent=${store.manifest.accentColor} to ${activity::class.java.simpleName}",
        )
    }

    /**
     * Recreate the activity when the active brand's accent has changed since
     * it was inflated. The persisted `lastAppliedAccent` is tracked separately
     * from the manifest so we don't recreate on unrelated brand updates
     * (logo refresh, link change, etc.).
     */
    fun maybeRecreateOnAccentChange(activity: Activity) {
        val store = com.github.kr328.clash.service.branding.BrandStore(activity)
        val current = store.manifest.accentColor.orEmpty()
        val last = store.lastAppliedAccent
        if (current == last) return
        // Persist BEFORE recreate so we don't loop.
        store.lastAppliedAccent = current
        com.github.kr328.clash.common.log.Log.d(
            "BrandThemeApplier: accent changed ('$last' -> '$current'), recreating ${activity::class.java.simpleName}",
        )
        activity.recreate()
    }

    private fun parseHexColor(hex: String?): Int? {
        if (hex.isNullOrBlank()) return null
        return runCatching { Color.parseColor(hex) }.getOrNull()
    }
}

@Suppress("unused")
private fun BrandManifest.accentColorOrNull(): String? = accentColor

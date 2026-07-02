package com.github.kr328.clash.design.branding

import android.app.Activity
import android.graphics.Color
import com.github.kr328.clash.design.R
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions

/**
 * Applies the operator accent as a Material 3 dynamic-color theme overlay —
 * exactly the same path used by the built-in "themes & palette" feature, just
 * sourced from a runtime hex instead of a pre-built XML palette. The
 * harmoniser builds the whole M3 tonal palette from the accent seed so every
 * widget that reads `?attr/colorPrimary` etc. picks up the brand
 * automatically — filled buttons, switches in their checked state, sliders,
 * progress indicators, tab indicators, on-primary text and icons, etc.
 *
 * Right after the harmoniser, we apply [R.style.ThemeOverlay_App_BrandNeutralSurfaces]
 * to pin the **off-state / non-primary** attrs (colorSurfaceVariant,
 * colorOutline, colorSurfaceContainer*, etc.) back to the default M3
 * neutrals. Without that second overlay the harmoniser would re-tint every
 * surface tone from the brand seed by design, and disconnected widgets
 * would visually read as the accent — Material 3's "harmonious palette" idea
 * conflicts with our "off must look off" requirement, so we forcibly split
 * the two.
 *
 * **Must be called before setContentView** of the host Activity. We invoke it
 * from BaseActivity.applyDayNight, after the user's day/night and palette
 * overlays so the brand always wins.
 */
object BrandThemeApplier {

    fun applyToActivity(activity: Activity) {
        val store = com.github.kr328.clash.service.branding.BrandStore(activity)
        val activeUuid = com.github.kr328.clash.service.store.ServiceStore(activity).activeProfile
        if (activeUuid == null || !store.isActiveFor(activeUuid)) {
            // No brand for the active profile (or no active profile at all) →
            // record that the theme is *unbranded* so [maybeRecreateOnAccentChange]
            // can compare against the actual state next tick. Crucially, we mark
            // this AFTER the theme reflects it; if recreate ever silently fails
            // we want the next tick to still observe last != current and retry.
            store.lastAppliedAccent = ""
            return
        }
        // Derive the applicable accent through the SAME helper maybeRecreateOnAccentChange uses,
        // so "what is applied" and "what is desired" can never disagree on parseability.
        val hex = applicableAccent(store.manifestFor(activeUuid).accentColor)
        val accent = if (hex.isNotEmpty()) runCatching { Color.parseColor(hex) }.getOrNull() else null
        if (accent == null) {
            store.lastAppliedAccent = ""
            return
        }

        // 1) Brand harmoniser → palette from seed (works on every Android version
        //    when we override the precondition; the default precondition checks
        //    system dynamic-color support which is Android 12+).
        DynamicColors.applyToActivityIfAvailable(
            activity,
            DynamicColorsOptions.Builder()
                .setContentBasedSource(accent)
                .setPrecondition { _, _ -> true }
                .build(),
        )
        // 2) Pin off-state surface attrs back to default M3 neutrals so
        //    disconnected / unchecked surfaces don't read as the accent.
        activity.theme.applyStyle(R.style.ThemeOverlay_App_BrandNeutralSurfaces, true)

        // 3) Pure-black (OLED) must win over the neutral pin — re-apply TrueBlack surfaces here in
        //    the SAME theme pass (right after the neutral pin) so grey cards don't survive on the
        //    black canvas. Only touches surfaces/background, so the brand accent above still wins.
        val nightMode = (activity.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        if (nightMode && com.github.kr328.clash.design.store.UiStore(activity).trueBlack) {
            activity.theme.applyStyle(R.style.ThemeOverlay_ClashFest_TrueBlack, true)
        }

        // Mark the accent that's now baked into this Activity's theme.
        // [maybeRecreateOnAccentChange] reads this on the next dashboard tick
        // and compares to the desired-for-active-profile accent.
        store.lastAppliedAccent = hex

        com.github.kr328.clash.common.log.Log.d(
            "BrandThemeApplier: harmonised palette from accent=$hex + neutral surfaces overlay applied to ${activity::class.java.simpleName}",
        )
    }

    /**
     * Recreate the Activity when the brand accent (or active brand state)
     * has changed since this Activity was inflated. The harmoniser overlay
     * is captured at inflate time — there's no way to update an already-
     * inflated theme.
     *
     * **Do not** update [com.github.kr328.clash.service.branding.BrandStore.lastAppliedAccent]
     * here — that field is owned by [applyToActivity] and represents what is
     * actually applied to the live theme. Writing it pre-recreate created a
     * race: if [Activity.recreate] silently no-op'd (Activity in STOPPED
     * state, lifecycle race, OEM quirk), the stored value would advance to
     * the new accent while the theme stayed on the old one, and every
     * follow-up tick would see `current == last` and bail without retrying.
     * Result: brand accent permanently stuck on the home screen until the
     * process was killed.
     */
    fun maybeRecreateOnAccentChange(activity: Activity) {
        val activeUuid = com.github.kr328.clash.service.store.ServiceStore(activity).activeProfile
        val store = com.github.kr328.clash.service.branding.BrandStore(activity)
        val desired = if (activeUuid != null && store.isActiveFor(activeUuid)) {
            applicableAccent(store.manifestFor(activeUuid).accentColor)
        } else {
            ""
        }
        val applied = store.lastAppliedAccent
        if (desired == applied) return
        com.github.kr328.clash.common.log.Log.d(
            "BrandThemeApplier: accent mismatch (applied='$applied', desired='$desired'), recreating ${activity::class.java.simpleName}",
        )
        activity.recreate()
    }

    private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")

    /**
     * The accent that can actually be baked into the theme: a `#RRGGBB` hex, or "" otherwise.
     * BOTH [applyToActivity] (what is applied) and [maybeRecreateOnAccentChange] (what is desired)
     * MUST derive their accent through this. Otherwise a non-blank-but-unapplicable accent would
     * ping-pong applied="" vs desired=raw and recreate the Activity on every dashboard tick
     * (a recreate storm). Pure + matches BrandValidation.cleanHexColor's accepted form, so it is
     * unit-testable without android.graphics.Color (stubbed in JVM tests).
     */
    internal fun applicableAccent(hex: String?): String =
        if (hex != null && HEX_COLOR.matches(hex)) hex else ""
}

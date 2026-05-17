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
        val activeUuid = com.github.kr328.clash.service.store.ServiceStore(activity).activeProfile
            ?: return
        val store = com.github.kr328.clash.service.branding.BrandStore(activity)
        if (!store.isActiveFor(activeUuid)) return
        val hex = store.manifestFor(activeUuid).accentColor
        val accent = parseHexColor(hex) ?: return

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

        com.github.kr328.clash.common.log.Log.d(
            "BrandThemeApplier: harmonised palette from accent=$hex + neutral surfaces overlay applied to ${activity::class.java.simpleName}",
        )
    }

    /**
     * Recreate the Activity when the brand accent (or active brand state)
     * has changed since this Activity was inflated. The harmoniser overlay
     * is captured at inflate time — there's no way to update an already-
     * inflated theme.
     */
    fun maybeRecreateOnAccentChange(activity: Activity) {
        val activeUuid = com.github.kr328.clash.service.store.ServiceStore(activity).activeProfile
        val store = com.github.kr328.clash.service.branding.BrandStore(activity)
        val current = if (activeUuid != null && store.isActiveFor(activeUuid)) {
            store.manifestFor(activeUuid).accentColor.orEmpty()
        } else {
            ""
        }
        val last = store.lastAppliedAccent
        if (current == last) return
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

package com.github.kr328.clash.design.branding

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.ContextThemeWrapper
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.HomeBackgroundStyle
import com.github.kr328.clash.design.model.ThemePalette
import com.github.kr328.clash.design.store.UiStore
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

    /**
     * Apply an accent [seed] color as a Material 3 harmonised palette + neutral off-state surface pin
     * (+ TrueBlack re-pin in night). Shared by the operator brand and the user's custom accent so both
     * produce the same premium result. Works on every Android version (precondition overridden — the
     * default only enables dynamic color on Android 12+).
     */
    fun applySeed(activity: Activity, seed: Int) {
        DynamicColors.applyToActivityIfAvailable(
            activity,
            DynamicColorsOptions.Builder()
                .setContentBasedSource(seed)
                .setPrecondition { _, _ -> true }
                .build(),
        )
        // Pin off-state surface attrs back to default M3 neutrals so disconnected / unchecked
        // surfaces don't read as the accent.
        activity.theme.applyStyle(R.style.ThemeOverlay_App_BrandNeutralSurfaces, true)
        // Pure-black (OLED) must win over the neutral pin — re-apply TrueBlack surfaces right after.
        val nightMode = (activity.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        if (nightMode && com.github.kr328.clash.design.store.UiStore(activity).trueBlack) {
            activity.theme.applyStyle(R.style.ThemeOverlay_ClashFest_TrueBlack, true)
        }
    }

    fun applyToActivity(activity: Activity) {
        val store = com.github.kr328.clash.service.branding.BrandStore(activity)
        val activeUuid = com.github.kr328.clash.service.store.ServiceStore(activity).activeProfile
        if (activeUuid == null || !store.isActiveFor(activeUuid)) {
            // No brand for the active profile (or no active profile at all) →
            // record that the theme is *unbranded* so [accentStale]
            // can compare against the actual state next tick. Crucially, we mark
            // this AFTER the theme reflects it; if recreate ever silently fails
            // we want the next tick to still observe last != current and retry.
            store.lastAppliedAccent = ""
            return
        }
        // Derive the applicable accent through the SAME helper accentStale uses,
        // so "what is applied" and "what is desired" can never disagree on parseability.
        val hex = applicableAccent(store.manifestFor(activeUuid).accentColor)
        val accent = if (hex.isNotEmpty()) runCatching { Color.parseColor(hex) }.getOrNull() else null
        if (accent == null) {
            store.lastAppliedAccent = ""
            return
        }

        // Harmonise the brand seed into a full M3 palette + neutral-surface pin (+ TrueBlack re-pin).
        applySeed(activity, accent)

        // Mark the accent that's now baked into this Activity's theme.
        // [accentStale] reads this on the next dashboard tick
        // and compares to the desired-for-active-profile accent.
        store.lastAppliedAccent = hex

        com.github.kr328.clash.common.log.Log.d(
            "BrandThemeApplier: harmonised palette from accent=$hex + neutral surfaces overlay applied to ${activity::class.java.simpleName}",
        )
    }

    /**
     * True when the brand accent (or active brand state) has changed since the current main-screen
     * design was inflated. The harmoniser overlay is captured at inflate time — there's no way to
     * update an already-inflated theme — so MainActivity resolves `true` by re-inflating its design
     * from a fresh [themedContextFor] wrapper (soft recreate; the Activity itself is never
     * destroyed — Android 16 ContentCapture SIGABRT on Activity destroy).
     *
     * **Do not** update [com.github.kr328.clash.service.branding.BrandStore.lastAppliedAccent]
     * here — that field is owned by [applyToActivity]/[themedContextFor] and represents what the
     * newest inflated surface actually carries. Advancing it from the check would make every
     * follow-up tick see `current == last` and bail without converging.
     */
    fun accentStale(context: Context): Boolean {
        val activeUuid = com.github.kr328.clash.service.store.ServiceStore(context).activeProfile
        val store = com.github.kr328.clash.service.branding.BrandStore(context)
        val desired = if (activeUuid != null && store.isActiveFor(activeUuid)) {
            applicableAccent(store.manifestFor(activeUuid).accentColor)
        } else {
            ""
        }
        val applied = store.lastAppliedAccent
        if (desired == applied) return false
        com.github.kr328.clash.common.log.Log.d(
            "BrandThemeApplier: accent stale (applied='$applied', desired='$desired')",
        )
        return true
    }

    /**
     * Build the themed [Context] the main screen's design inflates from — a [ContextThemeWrapper]
     * chain over [activity] carrying the FULL optional-overlay stack that [applyToActivity] +
     * BaseActivity.applyDayNight would otherwise bake into the Activity theme: Sloth skin /
     * preset palette / Material You / user custom accent / TrueBlack, with the operator brand
     * accent layered last so it always wins (same order as the activity path).
     *
     * MainActivity's own theme deliberately stays a virgin config-driven base theme: overlays can
     * never be *un*-applied from a live theme, so a brand baked into the Activity theme would make
     * "switch away from a branded profile" impossible without destroying the Activity, and a
     * day/night flip would leave stale `_Light`/`_Dark` palette + TrueBlack attrs behind. The
     * wrapper dies with the design; each soft recreate derives a fresh one from current state.
     *
     * Also records what accent the wrapper carries in
     * [com.github.kr328.clash.service.branding.BrandStore.lastAppliedAccent] (the same contract
     * [applyToActivity] follows for regular activities), so [accentStale] compares against the
     * surface the user actually sees.
     */
    fun themedContextFor(activity: Activity): Context {
        val uiStore = UiStore(activity)
        val night = isNight(activity)
        val sloth = uiStore.homeBackgroundStyle == HomeBackgroundStyle.Sloth

        // Operator brand accent wins over the user's custom accent; the custom accent (like the
        // preset palette) is only read outside the Sloth skin — same precedence as applyDayNight.
        val store = com.github.kr328.clash.service.branding.BrandStore(activity)
        val activeUuid = com.github.kr328.clash.service.store.ServiceStore(activity).activeProfile
        val brandHex = if (activeUuid != null && store.isActiveFor(activeUuid)) {
            applicableAccent(store.manifestFor(activeUuid).accentColor)
        } else {
            ""
        }
        val brandSeed = if (brandHex.isNotEmpty()) runCatching { Color.parseColor(brandHex) }.getOrNull() else null
        val customSeed = if (!sloth) uiStore.customAccent else null
        val seed = brandSeed ?: customSeed

        // Overlays that sit UNDER the harmonised seed palette (or stand alone without one).
        // A custom accent replaces the preset palette in the activity path too, so it
        // contributes no underlay.
        val underlay: Int? = when {
            sloth ->
                if (night) R.style.ThemeOverlay_ClashFest_Sloth_Dark
                else R.style.ThemeOverlay_ClashFest_Sloth_Light
            customSeed == null -> paletteOverlay(uiStore.themePalette, night)
            else -> null
        }

        val themed: Context = if (seed != null) {
            seededContext(activity, seed, underlay, night, uiStore.trueBlack)
        } else {
            var ctx: Context = activity
            underlay?.let { ctx = ContextThemeWrapper(activity, it) }
            if (!sloth && uiStore.dynamicColors) {
                // No content seed → material returns a plain (loader-free) wrapper; safe to nest.
                ctx = DynamicColors.wrapContextIfAvailable(ctx)
            }
            if (!sloth && night && uiStore.trueBlack) {
                ctx = if (ctx === activity) {
                    ContextThemeWrapper(activity, R.style.ThemeOverlay_ClashFest_TrueBlack)
                } else {
                    ctx.apply { theme.applyStyle(R.style.ThemeOverlay_ClashFest_TrueBlack, true) }
                }
            }
            ctx
        }

        // What the wrapper actually carries — owned contract shared with applyToActivity.
        store.lastAppliedAccent = if (brandSeed != null) brandHex else ""
        if (brandSeed != null) {
            com.github.kr328.clash.common.log.Log.d(
                "BrandThemeApplier: themed wrapper carries brand accent=$brandHex",
            )
        }
        return themed
    }

    /**
     * Context-wrapper twin of [applySeed]: harmonised M3 palette from [seed] + neutral off-state
     * surface pin (+ TrueBlack re-pin in night). Material's content-based dynamic color needs
     * API 30+ (ResourcesLoader) — exactly the same gate [applySeed] is behind, so the two paths
     * light up on the same devices.
     *
     * ⚠ Material's loader-wrapper MUST wrap the Activity directly. Nesting it over another
     * ContextThemeWrapper silently drops theme attr entries when the base theme is copied into
     * the forked Resources — device-verified crash on inflation
     * (`Failed to resolve attribute … selectableItemBackgroundBorderless` with
     * PaletteRose_Dark chained under PersonalizedColors). So [underlay] (Sloth / preset palette)
     * is applied onto the SAME wrapper's theme, with the seed palette re-asserted on top to keep
     * the activity path's overlay order (brand always wins).
     */
    private fun seededContext(
        activity: Activity,
        seed: Int,
        underlay: Int?,
        night: Boolean,
        trueBlack: Boolean,
    ): Context {
        val wrapped = DynamicColors.wrapContextIfAvailable(
            activity,
            DynamicColorsOptions.Builder()
                .setContentBasedSource(seed)
                .build(),
        )
        if (wrapped === activity) {
            // Harmoniser unavailable (pre-API-30): match applySeed — the underlay + neutral pin
            // (and TrueBlack re-pin) still apply.
            val ctx = ContextThemeWrapper(
                activity,
                underlay ?: R.style.ThemeOverlay_App_BrandNeutralSurfaces,
            )
            if (underlay != null) {
                ctx.theme.applyStyle(R.style.ThemeOverlay_App_BrandNeutralSurfaces, true)
            }
            if (night && trueBlack) {
                ctx.theme.applyStyle(R.style.ThemeOverlay_ClashFest_TrueBlack, true)
            }
            return ctx
        }
        // Android 16 (device-verified): Theme.setTo() into the wrapper's forked Resources loses
        // the copied base-theme content — every attr the activity theme provided resolves as
        // missing and inflation crashes on the first `?attr/` lookup. Re-apply the activity's
        // manifest theme with force=false: it only fills the holes, so everything the
        // personalized (seed) overlay defined stays on top.
        val baseTheme = runCatching {
            activity.packageManager.getActivityInfo(activity.componentName, 0).themeResource
        }.getOrDefault(0)
        if (baseTheme != 0) {
            wrapped.theme.applyStyle(baseTheme, false)
        }
        if (underlay != null) {
            wrapped.theme.applyStyle(underlay, true)
            // Re-assert the harmonised palette over the underlay — same end state as the
            // activity path, where the seed is applied after Sloth / preset palette.
            wrapped.theme.applyStyle(
                com.google.android.material.R.style.ThemeOverlay_Material3_PersonalizedColors,
                true,
            )
        }
        wrapped.theme.applyStyle(R.style.ThemeOverlay_App_BrandNeutralSurfaces, true)
        if (night && trueBlack) {
            wrapped.theme.applyStyle(R.style.ThemeOverlay_ClashFest_TrueBlack, true)
        }
        return wrapped
    }

    private fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    /**
     * Preset palette → theme-overlay style, per day/night. Single source of truth shared by
     * BaseActivity.applyDayNight (activity-theme path, all other screens) and [themedContextFor]
     * (wrapper path, main screen).
     */
    fun paletteOverlay(palette: ThemePalette, night: Boolean): Int? = when (palette) {
        ThemePalette.Clash -> null
        ThemePalette.Blue -> if (night) R.style.ThemeOverlay_ClashFest_PaletteBlue_Dark else R.style.ThemeOverlay_ClashFest_PaletteBlue_Light
        ThemePalette.Violet -> if (night) R.style.ThemeOverlay_ClashFest_PaletteViolet_Dark else R.style.ThemeOverlay_ClashFest_PaletteViolet_Light
        ThemePalette.Rose -> if (night) R.style.ThemeOverlay_ClashFest_PaletteRose_Dark else R.style.ThemeOverlay_ClashFest_PaletteRose_Light
        ThemePalette.Amber -> if (night) R.style.ThemeOverlay_ClashFest_PaletteAmber_Dark else R.style.ThemeOverlay_ClashFest_PaletteAmber_Light
        ThemePalette.Mint -> if (night) R.style.ThemeOverlay_ClashFest_PaletteMint_Dark else R.style.ThemeOverlay_ClashFest_PaletteMint_Light
        ThemePalette.Graphite -> if (night) R.style.ThemeOverlay_ClashFest_PaletteGraphite_Dark else R.style.ThemeOverlay_ClashFest_PaletteGraphite_Light
    }

    private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")

    /**
     * The accent that can actually be baked into the theme: a `#RRGGBB` hex, or "" otherwise.
     * BOTH [applyToActivity]/[themedContextFor] (what is applied) and [accentStale] (what is
     * desired) MUST derive their accent through this. Otherwise a non-blank-but-unapplicable
     * accent would ping-pong applied="" vs desired=raw and re-inflate the design on every
     * dashboard tick (a soft-recreate storm). Pure + matches BrandValidation.cleanHexColor's accepted form, so it is
     * unit-testable without android.graphics.Color (stubbed in JVM tests).
     */
    internal fun applicableAccent(hex: String?): String =
        if (hex != null && HEX_COLOR.matches(hex)) hex else ""
}

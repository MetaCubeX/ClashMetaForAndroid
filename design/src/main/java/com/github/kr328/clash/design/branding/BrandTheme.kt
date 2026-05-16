package com.github.kr328.clash.design.branding

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import androidx.core.view.children
import com.github.kr328.clash.common.branding.BrandValidation
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * Applies an operator-supplied accent color to a curated set of UI surfaces.
 *
 * Material 3 theme attributes (`colorPrimary` etc.) are baked at theme-attach
 * time and can't be cleanly mutated at runtime, so instead of trying to
 * rewrite the global theme we tint the specific surfaces operators reasonably
 * expect to see in their brand color:
 *
 *  - the main VPN power button
 *  - the brand logo tile background ring
 *  - "Active" filled chips on the home card
 *  - progress indicators on subscription quota
 *
 * Anything else (toggles in settings, dialog accents, etc.) keeps the
 * platform-native Material colors so a hostile / clashing operator value
 * can't make the deep UI unreadable.
 *
 * Contract: callers pass the accent hex string from [BrandValidation.cleanHexColor]
 * — already validated — together with the current surface color and the
 * binder skips the override entirely when the contrast ratio is too low to
 * be readable.
 */
object BrandTheme {

    /**
     * Parse a validated hex like `#5E35B1` to a Color int, ensuring contrast
     * against [surfaceColor] is at least 3.0:1 (WCAG AA large text). Returns
     * null when the value is unsafe to apply.
     */
    fun resolveAccent(hex: String?, surfaceColor: Int): Int? {
        if (hex.isNullOrBlank()) return null
        return runCatching {
            val parsed = Color.parseColor(hex)
            if (BrandValidation.hasMinContrast(parsed, surfaceColor)) parsed else null
        }.getOrNull()
    }

    /**
     * Branding accent helpers follow a strict rule: **no-op when color is null**.
     * Brand is an additive layer — when the operator hasn't supplied an accent
     * (or contrast filter rejected it), the existing theme stays untouched.
     * Resetting tints to null here would strip Material state-list backgrounds
     * (power button, switches, etc.) and break the default look.
     *
     * To "reset branding" we don't call these with null — we recreate the
     * view binding so theme defaults reattach themselves.
     */
    fun applyBackgroundTint(view: View?, color: Int?) {
        if (view == null || color == null) return
        view.backgroundTintList = ColorStateList.valueOf(color)
    }

    fun applyMaterialButtonAccent(button: MaterialButton?, color: Int?) {
        if (button == null || color == null) return
        button.backgroundTintList = ColorStateList.valueOf(color)
        val onColor = MaterialColors.getColor(
            button,
            com.google.android.material.R.attr.colorOnPrimary,
        )
        button.setTextColor(onColor)
        button.iconTint = ColorStateList.valueOf(onColor)
    }

    fun applyChipAccent(chip: Chip?, color: Int?) {
        if (chip == null || color == null) return
        chip.chipBackgroundColor = ColorStateList.valueOf(color)
    }

    fun applyProgressAccent(bar: LinearProgressIndicator?, color: Int?) {
        if (bar == null || color == null) return
        bar.setIndicatorColor(color)
    }

    /**
     * Walks [root] and applies the operator [accent] to every Material widget
     * inside that would normally use `colorPrimary`. No-op when [accent] is null.
     *
     * Touched widget classes:
     *  - MaterialButton (filled / outlined / tonal styles get the accent on
     *    background or icon depending on what's drawn)
     *  - MaterialSwitch / SwitchMaterial — thumb + track when checked
     *  - Slider — active track + thumb
     *  - LinearProgressIndicator / CircularProgressIndicator
     *  - Chip — checked-state background (only when checkable)
     *  - TabLayout — selected indicator
     *
     * Restoring defaults requires Activity recreation (we don't keep original
     * tint lists). Callers should rebuild bindings after a brand reset.
     */
    fun tintMaterialTree(root: View?, accent: Int?) {
        if (root == null || accent == null) return
        val accentList = ColorStateList.valueOf(accent)
        walk(root) { v ->
            when (v) {
                is com.google.android.material.button.MaterialButton -> tintMaterialButton(v, accent, accentList)
                is com.google.android.material.materialswitch.MaterialSwitch -> tintSwitch(v, accent, accentList)
                is com.google.android.material.switchmaterial.SwitchMaterial -> tintLegacySwitch(v, accent, accentList)
                is com.google.android.material.slider.Slider -> tintSlider(v, accentList)
                is LinearProgressIndicator -> v.setIndicatorColor(accent)
                is com.google.android.material.progressindicator.CircularProgressIndicator -> v.setIndicatorColor(accent)
                is Chip -> tintChip(v, accent, accentList)
                is com.google.android.material.tabs.TabLayout -> v.setSelectedTabIndicatorColor(accent)
                is android.widget.ProgressBar -> {
                    v.progressTintList = accentList
                    v.indeterminateTintList = accentList
                }
            }
        }
    }

    private fun walk(view: View, action: (View) -> Unit) {
        action(view)
        if (view is android.view.ViewGroup) {
            view.children.forEach { walk(it, action) }
        }
    }

    private fun tintMaterialButton(
        button: com.google.android.material.button.MaterialButton,
        accent: Int,
        accentList: ColorStateList,
    ) {
        val onColor = MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnPrimary)
        // Filled / tonal buttons: tint background. Outlined / text: tint stroke+icon+text.
        if (button.backgroundTintList != null) {
            button.backgroundTintList = accentList
            button.setTextColor(onColor)
            button.iconTint = ColorStateList.valueOf(onColor)
        } else {
            // text / outlined style — let the accent breathe through stroke / icon / text
            button.setTextColor(accent)
            button.iconTint = accentList
            if (button.strokeWidth > 0) button.strokeColor = accentList
        }
    }

    private fun tintSwitch(
        switch: com.google.android.material.materialswitch.MaterialSwitch,
        accent: Int,
        accentList: ColorStateList,
    ) {
        val unchecked = MaterialColors.getColor(switch, com.google.android.material.R.attr.colorOutline)
        val track = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
            intArrayOf(accent, unchecked),
        )
        switch.thumbTintList = accentList
        switch.trackTintList = track
    }

    private fun tintLegacySwitch(
        switch: com.google.android.material.switchmaterial.SwitchMaterial,
        accent: Int,
        accentList: ColorStateList,
    ) {
        val unchecked = MaterialColors.getColor(switch, com.google.android.material.R.attr.colorOutline)
        val track = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
            intArrayOf(accent, unchecked),
        )
        switch.thumbTintList = accentList
        switch.trackTintList = track
    }

    private fun tintSlider(slider: com.google.android.material.slider.Slider, accentList: ColorStateList) {
        slider.trackActiveTintList = accentList
        slider.thumbTintList = accentList
        slider.tickActiveTintList = accentList
    }

    private fun tintChip(chip: Chip, accent: Int, accentList: ColorStateList) {
        if (!chip.isCheckable) {
            // Non-checkable chip is a static badge — don't repaint it.
            return
        }
        val container = MaterialColors.getColor(chip, com.google.android.material.R.attr.colorSurfaceContainerHigh)
        val state = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
            intArrayOf(accent, container),
        )
        chip.chipBackgroundColor = state
    }
}

package com.github.kr328.clash.design

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import androidx.appcompat.app.AppCompatActivity
import com.github.kr328.clash.design.databinding.DesignThemeSettingsBinding
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.model.HomeBackgroundStyle
import com.github.kr328.clash.design.model.ThemePalette
import com.github.kr328.clash.design.model.ThemeTextScale
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.design.view.ThemePaletteView
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.cancel

class ThemeSettingsDesign(
    context: Context,
    private val uiStore: UiStore,
    private val syncNightMode: () -> Unit,
) : Design<ThemeSettingsDesign.Request>(context) {
    enum class Request {
        /** Reinflate Theme screen + stagger-recreate other activities (no Activity.recreate for Theme). */
        ReCreateOtherActivities,
        /** Full staggered recreate including Theme (font scale / reset need attachBaseContext). */
        ReCreateAllActivities,
    }

    private val binding = DesignThemeSettingsBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    private val paletteCards = mutableMapOf<ThemePalette, MaterialCardView>()
    private var customAccentCard: MaterialCardView? = null
    private var customAccentLabel: android.widget.TextView? = null
    private val recreateHandler = Handler(Looper.getMainLooper())
    private var pendingRecreateIncludesHost = false
    private val recreateRunnable = Runnable {
        val includeHost = pendingRecreateIncludesHost
        pendingRecreateIncludesHost = false
        requests.trySend(
            if (includeHost) Request.ReCreateAllActivities
            else Request.ReCreateOtherActivities,
        )
    }

    init {
        binding.surface = surface
        binding.header.screenTitle.text = context.getString(R.string.theme_settings)

        setupThemeMode()
        setupDynamicColors()
        setupPalettes()
        setupTrueBlack()
        setupTextScale()
        setupHomeBackground()
        setupReset()
    }

    fun disposeForReplace() {
        recreateHandler.removeCallbacks(recreateRunnable)
        cancel()
    }

    /**
     * A non-day/night theme change (palette, Material You, true-black, text scale, home background):
     * recreate EVERY activity including this one, so the whole app re-themes from a clean inflate.
     * We no longer applyStyle onto the already-drawn tree — that live merge left a mixed
     * half-dark/half-light state. Day/night is handled by [syncNightMode] (AppCompat recreates).
     */
    private fun recreateAll() {
        pendingRecreateIncludesHost = true
        recreateHandler.removeCallbacks(recreateRunnable)
        recreateHandler.postDelayed(recreateRunnable, 240L)
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    private fun setupThemeMode() {
        binding.themeModeGroup.check(
            when (uiStore.darkMode) {
                DarkMode.Auto -> R.id.theme_mode_auto
                DarkMode.ForceLight -> R.id.theme_mode_light
                DarkMode.ForceDark -> R.id.theme_mode_dark
            }
        )
        binding.themeModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            uiStore.darkMode = when (checkedId) {
                R.id.theme_mode_light -> DarkMode.ForceLight
                R.id.theme_mode_dark -> DarkMode.ForceDark
                else -> DarkMode.Auto
            }
            // Day/night change: drive AppCompat night mode -> the real Configuration flips ->
            // AppCompat recreates every activity cleanly with values-night resolved correctly.
            syncNightMode()
        }
    }

    private fun setupDynamicColors() {
        // Material You pulls colors from the wallpaper — only meaningful on Android 12+ where dynamic
        // color exists. On older devices the toggle is a no-op, so hide it entirely rather than offer
        // a control that does nothing.
        if (!com.google.android.material.color.DynamicColors.isDynamicColorAvailable()) {
            binding.dynamicColorSwitch.visibility = android.view.View.GONE
            return
        }
        binding.dynamicColorSwitch.isChecked = uiStore.dynamicColors
        binding.dynamicColorSwitch.setOnCheckedChangeListener { _, checked ->
            uiStore.dynamicColors = checked
            updatePaletteEnabled()
            recreateAll()
        }
    }

    private fun setupPalettes() {
        ThemePalette.values().forEach { palette ->
            val card = createPaletteCard(palette)
            paletteCards[palette] = card
            binding.themePaletteGrid.addView(card)
        }
        // Append a "+" custom-accent tile as the last cell of the palette grid.
        customAccentCard = createCustomAccentCard().also { binding.themePaletteGrid.addView(it) }
        updatePaletteSelection()
        updatePaletteEnabled()
    }

    private fun createPaletteCard(palette: ThemePalette): MaterialCardView {
        val margin = dp(6)
        val size = dp(76)
        val params = GridLayout.LayoutParams().apply {
            width = 0
            height = size
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(margin, margin, margin, margin)
        }

        return MaterialCardView(context).apply {
            layoutParams = params
            radius = dp(18).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            setCardBackgroundColor(context.resolvePaletteBackground(palette))
            strokeColor = context.resolvePaletteStroke(palette, palette == uiStore.themePalette)
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(palette.labelRes)

            addView(FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
                addView(ThemePaletteView(context).apply {
                    colors = palette.previewColors
                    checked = palette == uiStore.themePalette
                    layoutParams = FrameLayout.LayoutParams(
                        dp(54),
                        dp(54),
                        Gravity.CENTER,
                    )
                })
            })

            setOnClickListener {
                if (uiStore.dynamicColors) {
                    uiStore.dynamicColors = false
                    binding.dynamicColorSwitch.isChecked = false
                }
                // Picking a palette exits the Sloth skin so the palette applies.
                if (slothActive) {
                    uiStore.homeBackgroundStyle = HomeBackgroundStyle.Preview
                    binding.homeBackgroundGroup.check(R.id.home_background_preview)
                }
                // Picking a preset clears any custom accent (they're mutually exclusive).
                uiStore.customAccent = null
                uiStore.themePalette = palette
                updatePaletteEnabled()
                updatePaletteSelection()
                recreateAll()
            }
        }
    }

    private fun updatePaletteSelection() {
        // A custom accent overrides presets, so no preset reads as selected while one is set.
        val presetActive = uiStore.customAccent == null
        paletteCards.forEach { (palette, card) ->
            val checked = presetActive && palette == uiStore.themePalette
            card.strokeWidth = dp(if (checked) 2 else 1)
            card.strokeColor = context.resolvePaletteStroke(palette, checked)
            ((card.getChildAt(0) as? FrameLayout)?.getChildAt(0) as? ThemePaletteView)?.checked = checked
        }
        updateCustomAccentCard()
    }

    private fun updatePaletteEnabled() {
        // Sloth skin and dynamic colors both own the palette, so dim the grid
        // to signal it has no effect while either is active.
        val overridden = uiStore.dynamicColors || slothActive
        paletteCards.values.forEach {
            it.isEnabled = true
            it.alpha = if (overridden) 0.64f else 1.0f
        }
    }

    // --- Custom accent: a "+" tile appended to the palette grid. Tapping it lets the user pick any
    //     color, which seeds the full M3 palette via the same harmoniser as the operator brand.
    //     Overrides preset palettes / Material You; the operator brand still wins. ---
    private fun createCustomAccentCard(): MaterialCardView {
        val margin = dp(6)
        val size = dp(76)
        return MaterialCardView(context).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = size
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(margin, margin, margin, margin)
            }
            radius = dp(18).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.theme_custom_accent_pick)
            addView(
                android.widget.TextView(context).apply {
                    customAccentLabel = this
                    text = "+"
                    textSize = 26f
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    )
                },
            )
            setOnClickListener { showCustomAccentDialog() }
        }
    }

    private fun updateCustomAccentCard() {
        val card = customAccentCard ?: return
        val accent = uiStore.customAccent
        if (accent != null) {
            // Filled with the chosen color + a selection ring; a check reads on top for contrast.
            card.setCardBackgroundColor(accent)
            card.strokeWidth = dp(2)
            card.strokeColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurface)
            customAccentLabel?.apply {
                text = "✓"
                setTextColor(onColorFor(accent))
            }
        } else {
            card.setCardBackgroundColor(context.resolveThemedColor(com.google.android.material.R.attr.colorSurfaceContainerHighest))
            card.strokeWidth = dp(1)
            card.strokeColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOutline)
            customAccentLabel?.apply {
                text = "+"
                setTextColor(context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            }
        }
    }

    /** Black or white, whichever contrasts better on [bg]. */
    private fun onColorFor(bg: Int): Int {
        val luminance = (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)) / 255.0
        return if (luminance > 0.6) Color.BLACK else Color.WHITE
    }

    private fun selectAccent(color: Int) {
        // A custom accent owns the palette, so leave Material You / Sloth (they'd override it).
        if (uiStore.dynamicColors) {
            uiStore.dynamicColors = false
            binding.dynamicColorSwitch.isChecked = false
        }
        if (slothActive) {
            uiStore.homeBackgroundStyle = HomeBackgroundStyle.Preview
            binding.homeBackgroundGroup.check(R.id.home_background_preview)
        }
        uiStore.customAccent = color
        updatePaletteSelection()
        updatePaletteEnabled()
        recreateAll()
    }

    private fun showCustomAccentDialog() {
        val hsv = FloatArray(3)
        Color.colorToHSV(uiStore.customAccent ?: 0xFF4CAF50.toInt(), hsv)

        val svView = com.github.kr328.clash.design.view.SaturationValueView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, dp(200))
            setColor(hsv[0], hsv[1], hsv[2])
        }
        val hueView = com.github.kr328.clash.design.view.HueBarView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, dp(26)).apply { topMargin = dp(16) }
            hue = hsv[0]
        }
        val previewBg = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(10).toFloat() }
        val preview = View(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(dp(44), dp(44))
            background = previewBg
        }
        // Tap-to-edit hex field: dragging the square/bar rewrites it; typing a valid #RRGGBB drives the
        // square/bar back. A guard flag breaks the drag<->text feedback loop.
        val hexEdit = android.widget.EditText(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(14) }
            textSize = 18f
            setTextColor(context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurface))
            setSingleLine()
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(android.text.InputFilter.LengthFilter(7))
        }

        var syncingFromViews = false
        fun currentColor(): Int =
            Color.HSVToColor(floatArrayOf(hueView.hue, svView.saturation, svView.value)) or (0xFF shl 24)
        fun refresh() {
            val c = currentColor()
            previewBg.setColor(c)
            syncingFromViews = true
            hexEdit.setText(String.format("#%06X", 0xFFFFFF and c))
            syncingFromViews = false
        }
        svView.onChanged = { _, _ -> refresh() }
        hueView.onChanged = { h -> svView.hue = h; refresh() }
        hexEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (syncingFromViews) return
                val raw = s?.toString()?.trim().orEmpty()
                val normalized = if (raw.startsWith("#")) raw else "#$raw"
                if (normalized.length != 7) return
                val parsed = runCatching { Color.parseColor(normalized) }.getOrNull() ?: return
                val hsvOut = FloatArray(3)
                Color.colorToHSV(parsed, hsvOut)
                hueView.hue = hsvOut[0]
                svView.setColor(hsvOut[0], hsvOut[1], hsvOut[2])
                previewBg.setColor(parsed or (0xFF shl 24))
            }
        })
        refresh()

        val previewRow = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(18) }
            addView(preview)
            addView(hexEdit)
        }
        val content = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), 0)
            addView(svView)
            addView(hueView)
            addView(previewRow)
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle(R.string.theme_custom_accent_dialog_title)
            .setView(content)
            .setPositiveButton(android.R.string.ok) { _, _ -> selectAccent(currentColor()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private companion object {
        const val MATCH = android.widget.LinearLayout.LayoutParams.MATCH_PARENT
        const val WRAP = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
    }

    private fun setupTrueBlack() {
        binding.trueBlackSwitch.isChecked = uiStore.trueBlack
        binding.trueBlackSwitch.setOnCheckedChangeListener { _, checked ->
            uiStore.trueBlack = checked
            recreateAll()
        }
    }

    private fun setupTextScale() {
        binding.textScaleGroup.check(
            when (uiStore.themeTextScale) {
                ThemeTextScale.Small -> R.id.text_scale_small
                ThemeTextScale.Default -> R.id.text_scale_default
                ThemeTextScale.Large -> R.id.text_scale_large
                ThemeTextScale.ExtraLarge -> R.id.text_scale_extra
            }
        )
        binding.textScaleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            uiStore.themeTextScale = when (checkedId) {
                R.id.text_scale_small -> ThemeTextScale.Small
                R.id.text_scale_large -> ThemeTextScale.Large
                R.id.text_scale_extra -> ThemeTextScale.ExtraLarge
                else -> ThemeTextScale.Default
            }
            recreateAll()
        }
    }

    private fun setupHomeBackground() {
        binding.homeBackgroundGroup.check(
            when (uiStore.homeBackgroundStyle) {
                HomeBackgroundStyle.MaterialYou -> R.id.home_background_material
                HomeBackgroundStyle.Preview -> R.id.home_background_preview
                HomeBackgroundStyle.Sloth -> R.id.home_background_sloth
            }
        )
        binding.homeBackgroundGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            uiStore.homeBackgroundStyle = when (checkedId) {
                R.id.home_background_material -> HomeBackgroundStyle.MaterialYou
                R.id.home_background_sloth -> HomeBackgroundStyle.Sloth
                else -> HomeBackgroundStyle.Preview
            }
            updatePaletteEnabled()
            recreateAll()
        }
    }

    private val slothActive: Boolean
        get() = uiStore.homeBackgroundStyle == HomeBackgroundStyle.Sloth

    private fun setupReset() {
        binding.resetThemeButton.setOnClickListener {
            uiStore.darkMode = DarkMode.Auto
            uiStore.dynamicColors = true
            uiStore.themePalette = ThemePalette.Clash
            uiStore.customAccent = null
            uiStore.trueBlack = false
            uiStore.themeTextScale = ThemeTextScale.Default
            uiStore.homeBackgroundStyle = HomeBackgroundStyle.Preview
            // Reset also changes day/night (-> Auto), so sync night mode; recreateAll re-themes the
            // rest and re-runs attachBaseContext for the text-scale change.
            syncNightMode()
            recreateAll()
        }
    }

    private val ThemePalette.labelRes: Int
        get() = when (this) {
            ThemePalette.Clash -> R.string.theme_palette_clash
            ThemePalette.Blue -> R.string.theme_palette_blue
            ThemePalette.Violet -> R.string.theme_palette_violet
            ThemePalette.Rose -> R.string.theme_palette_rose
            ThemePalette.Amber -> R.string.theme_palette_amber
            ThemePalette.Mint -> R.string.theme_palette_mint
            ThemePalette.Graphite -> R.string.theme_palette_graphite
        }

    private val ThemePalette.previewColors: IntArray
        get() = when (this) {
            ThemePalette.Clash -> intArrayOf(0xFF2FA36B.toInt(), 0xFFB6F6D3.toInt(), 0xFFF8FAFD.toInt(), 0xFF4E6357.toInt())
            ThemePalette.Blue -> intArrayOf(0xFF2563EB.toInt(), 0xFFD9E2FF.toInt(), 0xFFF0F4FF.toInt(), 0xFF2F6878.toInt())
            ThemePalette.Violet -> intArrayOf(0xFF7C3AED.toInt(), 0xFFEADDFF.toInt(), 0xFFF8F1FF.toInt(), 0xFF85536D.toInt())
            ThemePalette.Rose -> intArrayOf(0xFFC0265A.toInt(), 0xFFFFD9E2.toInt(), 0xFFFFF0F4.toInt(), 0xFF7D5735.toInt())
            ThemePalette.Amber -> intArrayOf(0xFF8A5A00.toInt(), 0xFFFFDFA3.toInt(), 0xFFFBF2DF.toInt(), 0xFF52643B.toInt())
            ThemePalette.Mint -> intArrayOf(0xFF00856F.toInt(), 0xFFA8F2DF.toInt(), 0xFFEDF8F4.toInt(), 0xFF3E6373.toInt())
            ThemePalette.Graphite -> intArrayOf(0xFF54616F.toInt(), 0xFFD8E5F5.toInt(), 0xFFF0F3F7.toInt(), 0xFF705C73.toInt())
        }

    private fun Context.resolvePaletteBackground(palette: ThemePalette): Int {
        return palette.previewColors[2]
    }

    private fun Context.resolvePaletteStroke(palette: ThemePalette, checked: Boolean): Int {
        return if (checked) palette.previewColors[0] else Color.argb(70, 255, 255, 255)
    }
}

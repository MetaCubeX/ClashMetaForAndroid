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
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.design.view.ThemePaletteView
import com.google.android.material.card.MaterialCardView

class ThemeSettingsDesign(
    context: Context,
    private val uiStore: UiStore,
) : Design<ThemeSettingsDesign.Request>(context) {
    enum class Request {
        ReCreateAllActivities,
    }

    private val binding = DesignThemeSettingsBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    private val paletteCards = mutableMapOf<ThemePalette, MaterialCardView>()
    private val recreateHandler = Handler(Looper.getMainLooper())
    private val recreateRunnable = Runnable {
        requests.trySend(Request.ReCreateAllActivities)
    }

    init {
        binding.surface = surface
        binding.toolbar.title = context.getString(R.string.theme_settings)
        binding.toolbar.setNavigationOnClickListener {
            (context as? AppCompatActivity)?.onBackPressedDispatcher?.onBackPressed()
        }

        setupThemeMode()
        setupDynamicColors()
        setupPalettes()
        setupTrueBlack()
        setupTextScale()
        setupHomeBackground()
        setupReset()
    }

    private fun recreateAll() {
        recreateHandler.removeCallbacks(recreateRunnable)
        // Batch several quick toggles (palette + dynamic + true black) into one recreate.
        recreateHandler.postDelayed(recreateRunnable, 120L)
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
            recreateAll()
        }
    }

    private fun setupDynamicColors() {
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
                uiStore.themePalette = palette
                updatePaletteEnabled()
                updatePaletteSelection()
                recreateAll()
            }
        }
    }

    private fun updatePaletteSelection() {
        paletteCards.forEach { (palette, card) ->
            val checked = palette == uiStore.themePalette
            card.strokeWidth = dp(if (checked) 2 else 1)
            card.strokeColor = context.resolvePaletteStroke(palette, checked)
            ((card.getChildAt(0) as? FrameLayout)?.getChildAt(0) as? ThemePaletteView)?.checked = checked
        }
    }

    private fun updatePaletteEnabled() {
        val dynamic = uiStore.dynamicColors
        paletteCards.values.forEach {
            it.isEnabled = true
            it.alpha = if (dynamic) 0.64f else 1.0f
        }
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
                HomeBackgroundStyle.Plain -> R.id.home_background_plain
                HomeBackgroundStyle.Preview -> R.id.home_background_preview
            }
        )
        binding.homeBackgroundGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            uiStore.homeBackgroundStyle = when (checkedId) {
                R.id.home_background_material -> HomeBackgroundStyle.MaterialYou
                R.id.home_background_plain -> HomeBackgroundStyle.Plain
                else -> HomeBackgroundStyle.Preview
            }
            recreateAll()
        }
    }

    private fun setupReset() {
        binding.resetThemeButton.setOnClickListener {
            uiStore.darkMode = DarkMode.Auto
            uiStore.dynamicColors = true
            uiStore.themePalette = ThemePalette.Clash
            uiStore.trueBlack = false
            uiStore.themeTextScale = ThemeTextScale.Default
            uiStore.homeBackgroundStyle = HomeBackgroundStyle.Preview
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
            ThemePalette.Mono -> R.string.theme_palette_mono
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
            ThemePalette.Mono -> intArrayOf(0xFF5F5E62.toInt(), 0xFFE6E1E7.toInt(), 0xFFF4EFF4.toInt(), 0xFF7D5260.toInt())
        }

    private fun Context.resolvePaletteBackground(palette: ThemePalette): Int {
        return palette.previewColors[2]
    }

    private fun Context.resolvePaletteStroke(palette: ThemePalette, checked: Boolean): Int {
        return if (checked) palette.previewColors[0] else Color.argb(70, 255, 255, 255)
    }
}

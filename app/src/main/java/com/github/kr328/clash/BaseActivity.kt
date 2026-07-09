package com.github.kr328.clash

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.compat.isAllowForceDarkCompat
import com.github.kr328.clash.common.compat.isLightNavigationBarCompat
import com.github.kr328.clash.common.compat.isLightStatusBarsCompat
import com.github.kr328.clash.common.compat.isSystemBarsTranslucentCompat
import com.github.kr328.clash.core.bridge.ClashException
import com.github.kr328.clash.design.Design
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.model.HomeBackgroundStyle
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.ui.DayNight
import com.github.kr328.clash.design.util.resolveThemedBoolean
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.remote.Broadcasts
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.util.ActivityResultLifecycle
import com.github.kr328.clash.util.ApplicationObserver
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import com.github.kr328.clash.design.R

/** Maps the app's darkMode choice to an AppCompat night mode (the single source of truth for the
 *  Configuration night bit, so config-qualified resources resolve to match the chosen theme). */
fun nightModeFor(darkMode: DarkMode): Int = when (darkMode) {
    DarkMode.ForceLight -> AppCompatDelegate.MODE_NIGHT_NO
    DarkMode.ForceDark -> AppCompatDelegate.MODE_NIGHT_YES
    DarkMode.Auto -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
}

abstract class BaseActivity<D : Design<*>> : AppCompatActivity(),
    CoroutineScope by MainScope(),
    Broadcasts.Observer {
    
    protected val uiStore by lazy { UiStore(this) }

    /**
     * When true (default), applyDayNight bakes the optional theme overlays (Sloth / palette /
     * dynamic-color / custom accent / brand / true-black) into the Activity theme at onCreate.
     * MainActivity overrides to false: its design inflates from a per-design
     * [com.github.kr328.clash.design.branding.BrandThemeApplier.themedContextFor] wrapper instead,
     * so the accent can change without destroying the Activity (soft recreate) — an overlay baked
     * into the Activity theme could never be un-applied.
     */
    protected open val themeOverlaysOnActivityTheme: Boolean = true

    protected val events = Channel<Event>(Channel.UNLIMITED)
    protected var activityStarted: Boolean = false
    protected val clashRunning: Boolean
        get() = Remote.broadcasts.clashRunning
    protected var design: D? = null
        set(value) {
            field = value
            if (value != null) {
                setContentView(value.root)
            } else {
                setContentView(View(this))
            }
        }

    private var defer: suspend () -> Unit = {}
    private var deferRunning = false
    private val nextRequestKey = AtomicInteger(0)
    private var dayNight: DayNight = DayNight.Day

    protected abstract suspend fun main()

    override fun attachBaseContext(newBase: Context) {
        val store = runCatching { UiStore(newBase) }.getOrNull()
        val scale = store?.themeTextScale?.factor ?: 1.0f
        if (scale == 1.0f) {
            super.attachBaseContext(newBase)
        } else {
            val config = Configuration(newBase.resources.configuration).apply {
                fontScale *= scale
            }
            super.attachBaseContext(newBase.createConfigurationContext(config))
        }
    }

    fun defer(operation: suspend () -> Unit) {
        this.defer = operation
    }

    suspend fun <I, O> startActivityForResult(
        contracts: ActivityResultContract<I, O>,
        input: I,
    ): O = withContext(Dispatchers.Main) {
        val requestKey = nextRequestKey.getAndIncrement().toString()

        ActivityResultLifecycle().use { lifecycle, start ->
            suspendCoroutine { c ->
                activityResultRegistry.register(requestKey, lifecycle, contracts) {
                    c.resume(it)
                }.apply { start() }.launch(input)
            }
        }
    }

    suspend fun setContentDesign(design: D) {
        suspendCoroutine<Unit> {
            window.decorView.post {
                this.design = design
                it.resume(Unit)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDayNight()

        // Apply excludeFromRecents setting to all app tasks.
        checkNotNull(getSystemService<ActivityManager>()).appTasks.forEach { task ->
            task.setExcludeFromRecents(uiStore.hideFromRecents)
        }

        launch {
            main()
        }
    }

    override fun onStart() {
        super.onStart()
        activityStarted = true
        Remote.broadcasts.addObserver(this)
        events.trySend(Event.ActivityStart)
    }

    override fun onStop() {
        super.onStop()
        activityStarted = false
        Remote.broadcasts.removeObserver(this)
        events.trySend(Event.ActivityStop)
    }

    override fun onDestroy() {
        design?.cancel()
        cancel()
        super.onDestroy()
    }

    override fun finish() {
        if (deferRunning) return
        deferRunning = true

        launch {
            try {
                defer()
            } finally {
                withContext(NonCancellable) {
                    super.finish()
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val newDayNight = queryDayNight(newConfig)
        if (newDayNight != dayNight) {
            // Track the new mode BEFORE dispatching so a soft-recreating subclass (MainActivity)
            // still detects the NEXT flip — recreate() re-derives it in onCreate anyway.
            dayNight = newDayNight
            onDayNightChanged()
        }
    }

    /**
     * Day/night flipped while this activity is alive. Base behavior: recreate only this activity
     * (mass-recreating every tracked activity caused recreate storms on some Android 15 OEM
     * builds — Honor). MainActivity overrides this to re-inflate its content instead — destroying
     * it risks the Android 16 ContentCapture SIGABRT (see soft-recreate change).
     */
    protected open fun onDayNightChanged() {
        recreate()
    }

    open fun shouldDisplayHomeAsUpEnabled(): Boolean {
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        this.onBackPressed()
        return true
    }

    override fun onProfileChanged() {
        events.trySend(Event.ProfileChanged)
    }

    override fun onProfileUpdateCompleted(uuid: UUID?) {
        events.trySend(Event.ProfileUpdateCompleted)
    }

    override fun onProfileUpdateFailed(uuid: UUID?, reason: String?) {
        events.trySend(Event.ProfileUpdateFailed)
    }

    open override fun onProfileLoaded() {
        events.trySend(Event.ProfileLoaded)
    }

    override fun onConnectionsChanged() {
        events.trySend(Event.ConnectionsChanged)
    }

    override fun onServiceRecreated() {
        events.trySend(Event.ServiceRecreated)
    }

    override fun onStarted() {
        events.trySend(Event.ClashStart)
    }

    override fun onStopped(cause: String?) {
        events.trySend(Event.ClashStop)

        if (cause != null && activityStarted) {
            launch {
                design?.showExceptionToast(ClashException(cause))
            }
        }
    }

    private fun queryDayNight(config: Configuration = resources.configuration): DayNight {
        return when (uiStore.darkMode) {
            DarkMode.Auto -> if (config.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) DayNight.Night else DayNight.Day
            DarkMode.ForceLight -> DayNight.Day
            DarkMode.ForceDark -> DayNight.Night
        }
    }

    /**
     * Keep AppCompat night mode in sync with the user's darkMode. When it changes, AppCompat
     * recreates every activity with the new Configuration — a single clean, full re-theme (this is
     * what makes in-app dark/light switching actually take effect and never show a mixed transient).
     * Palette / accent / true-black changes don't touch night mode; ThemeSettings recreates for those.
     */
    fun syncNightModeFromUiStore() {
        val mode = nightModeFor(uiStore.darkMode)
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    private fun applyDayNight(config: Configuration = resources.configuration) {
        val dayNight = queryDayNight(config)
        val night = dayNight == DayNight.Night
        // Day/night is NOT faked here anymore: AppCompatDelegate night mode (set from darkMode in
        // MainApplication + on change) drives the real Configuration, so the config-qualified base
        // theme (BootstrapTheme -> AppThemeLight in values/, AppThemeDark in values-night/) and the
        // values-night/ colors already resolve to the correct mode. We only layer the OPTIONAL
        // overlays (Sloth / palette / dynamic-color / user-accent / brand / true-black) on top —
        // unless the subclass delivers them through a per-design themed wrapper instead
        // (MainActivity; see themeOverlaysOnActivityTheme + BrandThemeApplier.themedContextFor,
        // which mirrors this exact overlay order).
        if (themeOverlaysOnActivityTheme) {
            // SlothClash skin is an exclusive look (warm surfaces + gold accent),
            // selected via the Home background picker — it owns colors, so it
            // bypasses palette / dynamic-color / true-black.
            if (uiStore.homeBackgroundStyle == HomeBackgroundStyle.Sloth) {
                theme.applyStyle(
                    if (night) R.style.ThemeOverlay_ClashFest_Sloth_Dark
                    else R.style.ThemeOverlay_ClashFest_Sloth_Light,
                    true,
                )
            } else {
                val accent = uiStore.customAccent
                if (accent != null) {
                    // User custom accent: harmonise a full M3 palette from the seed (same path as the
                    // operator brand). Takes precedence over preset palette / Material You; the operator
                    // brand (applied below) still overrides it. applySeed also re-pins TrueBlack in night.
                    com.github.kr328.clash.design.branding.BrandThemeApplier.applySeed(this, accent)
                } else {
                    com.github.kr328.clash.design.branding.BrandThemeApplier
                        .paletteOverlay(uiStore.themePalette, night)?.let {
                            theme.applyStyle(it, true)
                        }
                    if (uiStore.dynamicColors) {
                        DynamicColors.applyToActivityIfAvailable(this)
                    }
                    if (night && uiStore.trueBlack) {
                        theme.applyStyle(R.style.ThemeOverlay_ClashFest_TrueBlack, true)
                    }
                }
            }

            // Operator brand accent — applied as the FINAL theme overlay so the
            // brand always wins over user palette / system dynamic-color choices.
            // applyToActivity also pins neutral surface attrs after the harmoniser, keeping off-state
            // widgets unambiguously neutral — and re-applies TrueBlack surfaces when pure-black is on
            // (otherwise the neutral pin leaves grey cards on the black canvas).
            com.github.kr328.clash.design.branding.BrandThemeApplier.applyToActivity(this)
        }

        applyWindowAppearance(this)

        this.dayNight = dayNight
    }

    /**
     * Resolve window-level appearance (system-bar colors + light/dark icon hints) from [themed]'s
     * theme. For regular activities that's the activity itself (from applyDayNight); MainActivity
     * re-runs this against the design's themed wrapper after every soft recreate so the bars
     * follow the wrapper theme, not the deliberately-virgin Activity theme.
     */
    protected fun applyWindowAppearance(themed: Context) {
        window.isAllowForceDarkCompat = false
        window.isSystemBarsTranslucentCompat = true

        window.statusBarColor = themed.resolveThemedColor(android.R.attr.statusBarColor)
        window.navigationBarColor = themed.resolveThemedColor(android.R.attr.navigationBarColor)

        if (Build.VERSION.SDK_INT >= 23) {
            window.isLightStatusBarsCompat = themed.resolveThemedBoolean(android.R.attr.windowLightStatusBar)
        }

        if (Build.VERSION.SDK_INT >= 27) {
            window.isLightNavigationBarCompat = themed.resolveThemedBoolean(android.R.attr.windowLightNavigationBar)
        }
    }

    enum class Event {
        ServiceRecreated,
        ActivityStart,
        ActivityStop,
        ClashStop,
        ClashStart,
        ProfileLoaded,
        ProfileChanged,
        ProfileUpdateCompleted,
        ProfileUpdateFailed,
        ConnectionsChanged,
    }
}

package com.github.kr328.clash

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.compat.isAllowForceDarkCompat
import com.github.kr328.clash.common.compat.isLightNavigationBarCompat
import com.github.kr328.clash.common.compat.isLightStatusBarsCompat
import com.github.kr328.clash.common.compat.isSystemBarsTranslucentCompat
import com.github.kr328.clash.core.bridge.ClashException
import com.github.kr328.clash.design.Design
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.model.ThemePalette
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

abstract class BaseActivity<D : Design<*>> : AppCompatActivity(),
    CoroutineScope by MainScope(),
    Broadcasts.Observer {
    
    protected val uiStore by lazy { UiStore(this) }
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

        if (queryDayNight(newConfig) != dayNight) {
            // Recreate only this activity. Mass-recreating every tracked activity caused
            // recreate storms on some Android 15 OEM builds (Honor).
            recreate()
        }
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

    fun applyThemeFromUiStore(config: Configuration = resources.configuration) {
        applyDayNight(config)
    }

    private fun applyDayNight(config: Configuration = resources.configuration) {
        val dayNight = queryDayNight(config)
        when (dayNight) {
            DayNight.Night -> theme.applyStyle(R.style.AppThemeDark, true)
            DayNight.Day -> theme.applyStyle(R.style.AppThemeLight, true)
        }
        paletteOverlay(uiStore.themePalette, dayNight)?.let {
            theme.applyStyle(it, true)
        }
        if (uiStore.dynamicColors) {
            DynamicColors.applyToActivityIfAvailable(this)
        }
        if (dayNight == DayNight.Night && uiStore.trueBlack) {
            theme.applyStyle(R.style.ThemeOverlay_ClashFest_TrueBlack, true)
        }

        window.isAllowForceDarkCompat = false
        window.isSystemBarsTranslucentCompat = true
        
        window.statusBarColor = resolveThemedColor(android.R.attr.statusBarColor)
        window.navigationBarColor = resolveThemedColor(android.R.attr.navigationBarColor)

        if (Build.VERSION.SDK_INT >= 23) {
            window.isLightStatusBarsCompat = resolveThemedBoolean(android.R.attr.windowLightStatusBar)
        }

        if (Build.VERSION.SDK_INT >= 27) {
            window.isLightNavigationBarCompat = resolveThemedBoolean(android.R.attr.windowLightNavigationBar)
        }

        this.dayNight = dayNight
    }

    private fun paletteOverlay(palette: ThemePalette, dayNight: DayNight): Int? {
        val night = dayNight == DayNight.Night
        return when (palette) {
            ThemePalette.Clash -> null
            ThemePalette.Blue -> if (night) R.style.ThemeOverlay_ClashFest_PaletteBlue_Dark else R.style.ThemeOverlay_ClashFest_PaletteBlue_Light
            ThemePalette.Violet -> if (night) R.style.ThemeOverlay_ClashFest_PaletteViolet_Dark else R.style.ThemeOverlay_ClashFest_PaletteViolet_Light
            ThemePalette.Rose -> if (night) R.style.ThemeOverlay_ClashFest_PaletteRose_Dark else R.style.ThemeOverlay_ClashFest_PaletteRose_Light
            ThemePalette.Amber -> if (night) R.style.ThemeOverlay_ClashFest_PaletteAmber_Dark else R.style.ThemeOverlay_ClashFest_PaletteAmber_Light
            ThemePalette.Mint -> if (night) R.style.ThemeOverlay_ClashFest_PaletteMint_Dark else R.style.ThemeOverlay_ClashFest_PaletteMint_Light
            ThemePalette.Graphite -> if (night) R.style.ThemeOverlay_ClashFest_PaletteGraphite_Dark else R.style.ThemeOverlay_ClashFest_PaletteGraphite_Light
            ThemePalette.Mono -> if (night) R.style.ThemeOverlay_ClashFest_PaletteMono_Dark else R.style.ThemeOverlay_ClashFest_PaletteMono_Light
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

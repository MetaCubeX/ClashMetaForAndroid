package com.github.kr328.clash

import android.app.ActivityManager
import android.content.Intent
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
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.core.bridge.ClashException
import com.github.kr328.clash.core.bridge.Bridge
import com.github.kr328.clash.design.Design
import com.github.kr328.clash.design.databinding.DesignAboutBinding
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.ui.DayNight
import com.github.kr328.clash.design.util.resolveThemedBoolean
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.design.view.TvActivityShell
import com.github.kr328.clash.design.view.TvNavigationBar
import com.github.kr328.clash.remote.Broadcasts
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.util.ActivityResultLifecycle
import com.github.kr328.clash.util.ApplicationObserver
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
    private var tvShell: TvActivityShell? = null
    protected val clashRunning: Boolean
        get() = Remote.broadcasts.clashRunning
    protected var design: D? = null
        set(value) {
            field = value
            if (value != null) {
                setContentView(wrapTvContent(value.root))
            } else {
                tvShell = null
                setContentView(View(this))
            }
        }

    private var defer: suspend () -> Unit = {}
    private var deferRunning = false
    private val nextRequestKey = AtomicInteger(0)
    private var dayNight: DayNight = DayNight.Day

    protected abstract suspend fun main()

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

    protected fun openTvTab(intent: Intent) {
        if (isTelevision) {
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        startActivity(intent)
        if (isTelevision) overridePendingTransition(0, 0)
    }

    private val isTelevision: Boolean
        get() = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_TELEVISION

    private fun wrapTvContent(content: View): View {
        val activeTab = currentTvTab() ?: return content.also { tvShell = null }
        if (!isTelevision || this is MainActivity) return content.also { tvShell = null }

        return TvActivityShell(this).also { shell ->
            tvShell = shell
            shell.setContent(content)
            shell.navigationBar.apply {
                setActiveTab(activeTab)
                proxyEnabled = clashRunning
                onTabSelected = ::openTvTab
            }

            if (isTopLevelTvTab()) {
                content.findViewById<View>(R.id.activity_bar_close_view)?.visibility = View.GONE
            }
            shell.focusFirstContent()
        }
    }

    private fun currentTvTab(): TvNavigationBar.Tab? = when (this) {
        is MainActivity -> TvNavigationBar.Tab.Home
        is ProxyActivity -> TvNavigationBar.Tab.Proxy
        is ProfilesActivity,
        is NewProfileActivity,
        is PropertiesActivity,
        is FilesActivity,
        is ProvidersActivity -> TvNavigationBar.Tab.Profiles
        is LogsActivity,
        is LogcatActivity -> TvNavigationBar.Tab.Logs
        is SettingsActivity,
        is AppSettingsActivity,
        is NetworkSettingsActivity,
        is OverrideSettingsActivity,
        is MetaFeatureSettingsActivity,
        is AccessControlActivity -> TvNavigationBar.Tab.Settings
        is HelpActivity -> TvNavigationBar.Tab.Help
        else -> null
    }

    private fun isTopLevelTvTab(): Boolean = when (this) {
        is ProxyActivity,
        is ProfilesActivity,
        is LogsActivity,
        is SettingsActivity,
        is HelpActivity -> true
        else -> false
    }

    private fun openTvTab(tab: TvNavigationBar.Tab) {
        if (tab == currentTvTab() && isTopLevelTvTab()) {
            tvShell?.focusFirstContent()
            return
        }

        when (tab) {
            TvNavigationBar.Tab.Home -> openTvTab(
                MainActivity::class.intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            TvNavigationBar.Tab.Proxy -> if (clashRunning) openTvTab(ProxyActivity::class.intent)
            TvNavigationBar.Tab.Profiles -> openTvTab(ProfilesActivity::class.intent)
            TvNavigationBar.Tab.Logs -> openTvTab(
                if (LogcatService.running) LogcatActivity::class.intent else LogsActivity::class.intent,
            )
            TvNavigationBar.Tab.Settings -> openTvTab(SettingsActivity::class.intent)
            TvNavigationBar.Tab.Help -> openTvTab(HelpActivity::class.intent)
            TvNavigationBar.Tab.About -> launch { showTvAbout() }
        }
    }

    private suspend fun showTvAbout() {
        val versionName = withContext(Dispatchers.IO) {
            packageManager.getPackageInfo(packageName, 0).versionName + "\n" +
                Bridge.nativeCoreVersion().replace("_", "-")
        }
        withContext(Dispatchers.Main) {
            val about = DesignAboutBinding.inflate(layoutInflater).apply {
                this.versionName = versionName
            }
            androidx.appcompat.app.AlertDialog.Builder(this@BaseActivity)
                .setView(about.root)
                .show()
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
            ApplicationObserver.createdActivities.forEach {
                it.recreate()
            }
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

    override fun onProfileLoaded() {
        events.trySend(Event.ProfileLoaded)
    }

    override fun onServiceRecreated() {
        events.trySend(Event.ServiceRecreated)
    }

    override fun onStarted() {
        tvShell?.navigationBar?.proxyEnabled = true
        events.trySend(Event.ClashStart)
    }

    override fun onStopped(cause: String?) {
        tvShell?.navigationBar?.proxyEnabled = false
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

    private fun applyDayNight(config: Configuration = resources.configuration) {
        val dayNight = queryDayNight(config)
        when (dayNight) {
            DayNight.Night -> theme.applyStyle(R.style.AppThemeDark, true)
            DayNight.Day -> theme.applyStyle(R.style.AppThemeLight, true)
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
    }
}

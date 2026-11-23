package com.github.kr328.clash

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.design.MainDesign
import universe.agentic.clash.QuickNodeUi
import universe.agentic.clash.ShellTab
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.model.resolveAccessControl
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.TunnelClock
import com.github.kr328.clash.util.clashDir
import com.github.kr328.clash.util.requestClashReload
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.bridge.*
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.TunnelState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import com.github.kr328.clash.design.R as DesignR

class MainActivity : BaseActivity<MainDesign>() {
    private var observedSince: Long? = null
    private var proxyShell: ProxyShell? = null
    private var profilesShell: ProfilesShell? = null
    private var appsShell: AppsStrategyShell? = null
    private var settingsShell: SettingsShell? = null

    override suspend fun main() {
        val design = MainDesign(this)
        val proxy = ProxyShell(
            this,
            embedded = true,
            onMounted = { design.proxyRoot = it.root },
        )
        val profiles = ProfilesShell(this, embedded = true) { design.profilesRoot = it.root }
        val apps = AppsStrategyShell(this, embedded = true) { design.appsRoot = it.root }
        val settings = SettingsShell(this, embedded = true) { design.settingsRoot = it.root }
        proxyShell = proxy
        profilesShell = profiles
        appsShell = apps
        settingsShell = settings

        setContentDesign(design)

        coroutineScope {
            launch { proxy.run() }
            launch { profiles.run() }
            launch { apps.run() }
            launch { settings.run() }

            design.fetch()

            val ticker = ticker(TimeUnit.SECONDS.toMillis(1))

            while (isActive) {
                select<Unit> {
                    events.onReceive {
                        when (it) {
                            Event.ActivityStart,
                            Event.ProfileChanged -> {
                                design.fetch()
                                profiles.poke()
                                apps.poke()
                                settingsShell?.poke()
                            }
                            Event.ServiceRecreated,
                            Event.ClashStart,
                            Event.ProfileLoaded -> {
                                design.fetch()
                                proxy.onProfileLoaded()
                                proxy.refreshProviders()
                            }
                            Event.ClashStop -> {
                                design.fetch()
                                proxy.refreshProviders()
                            }
                            else -> Unit
                        }
                    }
                    design.requests.onReceive {
                        when (it) {
                            MainDesign.Request.ToggleStatus -> {
                                if (clashRunning)
                                    stopClashService()
                                else
                                    design.startClash()
                            }
                            is MainDesign.Request.SelectNode -> {
                                withClash { patchSelector(it.group, it.name) }
                                proxy.reloadGroup(it.group)
                                design.fetch()
                            }
                            is MainDesign.Request.OpenProxy -> {
                                design.showTab(ShellTab.Proxies)
                                val group = it.group
                                if (!group.isNullOrEmpty()) proxy.showGroup(group)
                            }
                            MainDesign.Request.OpenProfiles ->
                                design.showTab(ShellTab.Profiles)
                            MainDesign.Request.OpenProviders -> {
                                if (clashRunning) {
                                    design.showTab(ShellTab.Proxies)
                                    proxy.showSection(1)
                                } else {
                                    design.showToast(DesignR.string.proxy_after_connect, ToastDuration.Long)
                                }
                            }
                            MainDesign.Request.OpenAppsStrategy ->
                                design.showTab(ShellTab.Apps)
                            MainDesign.Request.OpenLogs -> {
                                if (LogcatService.running) {
                                    startActivity(LogcatActivity::class.intent)
                                } else {
                                    startActivity(LogsActivity::class.intent)
                                }
                            }
                            MainDesign.Request.ReloadKernel -> {
                                if (clashRunning) {
                                    applicationContext.requestClashReload()
                                } else {
                                    design.showToast(DesignR.string.kernel_reload_stopped, ToastDuration.Long)
                                }
                            }
                            MainDesign.Request.OpenWebdav ->
                                startActivity(WebdavSettingsActivity::class.intent)
                            MainDesign.Request.OpenFiles ->
                                openFiles()
                            is MainDesign.Request.SetMode -> {
                                design.showToast(DesignR.string.mode_switch_tips, ToastDuration.Long)
                                withClash {
                                    val override = queryOverride(Clash.OverrideSlot.Session)
                                    override.mode = it.mode
                                    patchOverride(Clash.OverrideSlot.Session, override)
                                }
                                design.fetch()
                            }
                        }
                    }
                    if (clashRunning) {
                        ticker.onReceive {
                            design.fetchTraffic()
                            design.setUptime(currentUptime())
                        }
                    }
                }
            }
        }
    }

    private suspend fun MainDesign.fetch() {
        setClashRunning(clashRunning)

        val mode = withClash {
            queryOverride(Clash.OverrideSlot.Session).mode ?: queryTunnelState().mode
        }
        val providers = if (clashRunning) {
            try {
                withClash { queryProviders() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        setMode(mode)
        val outbound = if (clashRunning) {
            queryOutbound(mode)
        } else {
            OutboundSnapshot(null, null, -1, false, emptyList())
        }
        setOutbound(outbound.node, outbound.group, outbound.delay, outbound.selectable, outbound.nodes)

        if (clashRunning) {
            fetchTraffic()
            setUptime(currentUptime())
        }

        val profile = withProfile { queryActive() }
        setProfileName(profile?.name)
        val aside = queryAside(
            profile = profile,
            providerCount = if (clashRunning) providers.size else -1,
        )
        setAside(aside.strategy, aside.webdav, aside.files, aside.providers)
    }

    private suspend fun openFiles() {
        val active = withProfile { queryActive() }
        if (active != null && active.imported) {
            startActivity(FilesActivity::class.intent.setUUID(active.uuid))
        } else {
            startActivity(MetaFeatureSettingsActivity::class.intent)
        }
    }

    private fun currentUptime(): String {
        if (!clashRunning) {
            observedSince = null
            return ""
        }
        val stamped = TunnelClock.startedAt(this)
        val since = stamped ?: observedSince ?: System.currentTimeMillis().also { observedSince = it }
        if (stamped != null) observedSince = null
        val elapsed = (System.currentTimeMillis() - since).coerceAtLeast(0L) / 1000L
        val hours = elapsed / 3600
        val minutes = (elapsed % 3600) / 60
        val seconds = elapsed % 60
        return "%d:%02d:%02d".format(hours, minutes, seconds)
    }

    private data class Aside(
        val strategy: String,
        val webdav: String,
        val files: String,
        val providers: Int,
    )

    private suspend fun queryAside(profile: Profile?, providerCount: Int): Aside {
        return withContext(Dispatchers.IO) {
            val service = ServiceStore(this@MainActivity)
            val config = service.activeAppsStrategyConfigUuid?.let { id ->
                service.appsStrategyConfigs.find { it.uuid == id }
            }
            val effective = resolveAccessControl(
                activeStrategy = config,
            )
            val mode = effective.mode
            val count = effective.packages.size
            val name = config?.name?.takeIf { it.isNotBlank() }
                ?: getString(DesignR.string.not_selected)
            val modeLabel = getString(
                when (mode) {
                    AccessControlMode.AcceptSelected -> DesignR.string.strategy_mode_allow_selected
                    AccessControlMode.DenySelected -> DesignR.string.strategy_mode_deny_selected
                    AccessControlMode.AcceptAll -> DesignR.string.strategy_mode_allow_all
                },
            )
            val countLabel = if (mode == AccessControlMode.AcceptAll) {
                getString(DesignR.string.allow_all_apps)
            } else {
                resources.getQuantityString(DesignR.plurals.selected_apps_count, count, count)
            }
            val webdav = this@MainActivity.webdavHomeLabel()
            val present = listOf("geoip.metadb", "geosite.dat", "ASN.mmdb")
                .filter { clashDir.resolve(it).isFile }
            val ageLabel = getString(
                if (profile?.ageSecretKey.isNullOrBlank()) {
                    DesignR.string.webdav_unset
                } else {
                    DesignR.string.webdav_password_set
                },
            )
            val files = buildList {
                profile?.name?.takeIf { it.isNotBlank() }?.let { add(it) }
                addAll(present)
                if (profile != null) {
                    add("${getString(DesignR.string.age_secret_key)} $ageLabel")
                }
            }.joinToString(" · ").ifEmpty { getString(DesignR.string.not_selected) }
            Aside("$name · $modeLabel · $countLabel", webdav, files, providerCount)
        }
    }

    private suspend fun MainDesign.fetchTraffic() {
        withClash {
            setTraffic(queryTrafficNow(), queryTrafficTotal())
        }
    }

    private data class OutboundSnapshot(
        val node: String?,
        val group: String?,
        val delay: Int,
        val selectable: Boolean,
        val nodes: List<QuickNodeUi>,
    )

    private suspend fun queryOutbound(mode: TunnelState.Mode): OutboundSnapshot {
        if (mode == TunnelState.Mode.Direct) {
            return OutboundSnapshot(null, null, -1, false, emptyList())
        }
        return withClash {
            val names = queryProxyGroupNames(true)
            val group = when (mode) {
                TunnelState.Mode.Global ->
                    names.firstOrNull { it.equals("GLOBAL", ignoreCase = true) } ?: names.firstOrNull()
                else ->
                    names.firstOrNull { !it.equals("GLOBAL", ignoreCase = true) } ?: names.firstOrNull()
            } ?: return@withClash OutboundSnapshot(null, null, -1, false, emptyList())
            val state = queryProxyGroup(group, ProxySort.Default)
            val now = state.now.takeIf { it.isNotBlank() }
            val delay = state.proxies.firstOrNull { it.name == now }?.delay ?: -1
            val nodes = state.proxies.map { proxy ->
                QuickNodeUi(
                    name = proxy.name,
                    type = proxy.type.ifBlank { proxy.subtitle },
                    delay = proxy.delay,
                )
            }
            OutboundSnapshot(now, group, delay, state.type == "Selector", nodes)
        }
    }

    private suspend fun MainDesign.startClash() {
        val active = withProfile { queryActive() }

        if (active == null || !active.imported) {
            showToast(DesignR.string.no_profile_selected, ToastDuration.Long) {
                setAction(DesignR.string.profiles) {
                    this@startClash.showTab(ShellTab.Profiles)
                }
            }

            return
        }

        val vpnRequest = startClashService()

        try {
            if (vpnRequest != null) {
                val result = startActivityForResult(
                    ActivityResultContracts.StartActivityForResult(),
                    vpnRequest
                )

                if (result.resultCode == RESULT_OK)
                    startClashService()
            }
        } catch (e: Exception) {
            design?.showToast(DesignR.string.unable_to_start_vpn, ToastDuration.Long)
        }
    }

    override fun onProfileUpdateCompleted(uuid: UUID?) {
        super.onProfileUpdateCompleted(uuid)
        if (uuid == null) return
        launch {
            profilesShell?.finishUpdate(uuid)
            profilesShell?.poke()
            var name: String? = null
            withProfile { name = queryByUUID(uuid)?.name }
            design?.showToast(
                getString(DesignR.string.toast_profile_updated_complete, name),
                ToastDuration.Long,
            )
        }
    }

    override fun onProfileUpdateFailed(uuid: UUID?, reason: String?) {
        super.onProfileUpdateFailed(uuid, reason)
        if (uuid == null) return
        launch {
            profilesShell?.finishUpdate(uuid)
            profilesShell?.poke()
            var name: String? = null
            withProfile { name = queryByUUID(uuid)?.name }
            design?.showToast(
                getString(DesignR.string.toast_profile_updated_failed, name, reason),
                ToastDuration.Long,
            ) {
                setAction(DesignR.string.edit) {
                    startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val current = design?.tab
                if (current != null && current != ShellTab.Dashboard) {
                    design?.showTab(ShellTab.Dashboard)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher =
                registerForActivityResult(RequestPermission()
                ) { isGranted: Boolean ->
                }
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        setupShortcuts()
    }

    private fun setupShortcuts() {
        // Skip dynamic shortcut setup when the app icon is hidden.
        if (uiStore.hideAppIcon) return

        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
            Intent.FLAG_ACTIVITY_NO_ANIMATION

        val toggle = ShortcutInfoCompat.Builder(this, "toggle_clash")
            .setShortLabel(getString(DesignR.string.shortcut_toggle_short))
            .setLongLabel(getString(DesignR.string.shortcut_toggle_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_toggle_all))
            .setIntent(
                Intent(Intents.ACTION_TOGGLE_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags)
            )
            .setRank(0)
            .build()

        val start = ShortcutInfoCompat.Builder(this, "start_clash")
            .setShortLabel(getString(DesignR.string.shortcut_start_short))
            .setLongLabel(getString(DesignR.string.shortcut_start_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_toggle_on))
            .setIntent(
                Intent(Intents.ACTION_START_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags)
            )
            .setRank(1)
            .build()

        val stop = ShortcutInfoCompat.Builder(this, "stop_clash")
            .setShortLabel(getString(DesignR.string.shortcut_stop_short))
            .setLongLabel(getString(DesignR.string.shortcut_stop_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_toggle_off))
            .setIntent(
                Intent(Intents.ACTION_STOP_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags)
            )
            .setRank(2)
            .build()

        ShortcutManagerCompat.setDynamicShortcuts(this, listOf(toggle, start, stop))
    }
}

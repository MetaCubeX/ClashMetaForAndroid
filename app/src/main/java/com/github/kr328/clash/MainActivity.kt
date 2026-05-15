package com.github.kr328.clash

import android.content.ClipboardManager
import android.content.Intent
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import android.os.PowerManager
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import com.github.kr328.clash.design.dialog.AppBottomSheetDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.util.ShareImportSupport
import com.github.kr328.clash.util.SubscriptionMetaCache
import com.github.kr328.clash.util.createEmptyUrlProfileAndOpenEditor
import com.github.kr328.clash.common.util.StandalonePing
import com.github.kr328.clash.common.util.SubscriptionNameGuesser
import com.github.kr328.clash.common.util.SubscriptionOverrides
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.bridge.*
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.MainDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.dialog.withModelProgressBar
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.applyFetchStatus
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.remote.StatusClient
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.model.ProxyGroupPreviewRow
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.util.RussianBypassDefaults
import com.github.kr328.clash.util.GitHubReleaseUpdate
import com.github.kr328.clash.util.AppUpdateChecker
import com.github.kr328.clash.util.showProfileQuickEditSheet
import com.github.kr328.clash.util.closeConnectionsAfterUserProxySwitchIfEnabled
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.QRResult.QRError
import io.github.g00fy2.quickie.QRResult.QRMissingPermission
import io.github.g00fy2.quickie.QRResult.QRSuccess
import io.github.g00fy2.quickie.QRResult.QRUserCanceled
import io.github.g00fy2.quickie.ScanQRCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : BaseActivity<MainDesign>() {
    private data class HomeRuntimeSnapshot(
        val running: Boolean,
        val activeProfileUuid: UUID?,
        val proxyNames: List<String>,
    )

    private val scanLauncher = registerForActivityResult(ScanQRCode(), ::onScanResult)
    private var lastForwardedTrafficTotal: Long = Long.MIN_VALUE
    private var isCheckingUpdates: Boolean = false
    private var isToggleStatusInFlight: Boolean = false
    private var pendingTunnelMode: TunnelState.Mode? = null
    private var pendingApkDownloadId: Long = -1L
    private var downloadReceiverRegistered: Boolean = false
    private val updatePrefs by lazy { getSharedPreferences("app_update", MODE_PRIVATE) }
    /** Incremented on each [onProfileLoaded] — used to await post-load selector patches. */
    private val profileLoadEpoch = AtomicInteger(0)

    private fun clearPendingDownloadState() {
        pendingApkDownloadId = -1L
        updatePrefs.edit().remove("pending_download_id").apply()
    }

    private val apkDownloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id <= 0L || id != pendingApkDownloadId) return
            clearPendingDownloadState()

            checkDownloadedApkAndInstall(id)
        }
    }

    private fun normalizeTunnelMode(mode: TunnelState.Mode): TunnelState.Mode =
        if (mode == TunnelState.Mode.Direct) TunnelState.Mode.Rule else mode

    private fun parseTunnelMode(name: String): TunnelState.Mode? =
        when (name) {
            TunnelState.Mode.Rule.name -> TunnelState.Mode.Rule
            TunnelState.Mode.Global.name -> TunnelState.Mode.Global
            TunnelState.Mode.Direct.name -> TunnelState.Mode.Rule
            else -> null
        }

    private suspend fun MainDesign.patchTunnelMode(mode: TunnelState.Mode, scheduleRefresh: () -> Unit) {
        try {
            val normalized = normalizeTunnelMode(mode)
            uiStore.tunnelModePreference = normalized.name
            pendingTunnelMode = normalized
            setMode(normalized)

            val running = clashRunning || withContext(Dispatchers.IO) { resolveStatusSnapshot().serviceRunning }
            if (running) {
                val error = runCatching {
                    withClash {
                        val o = queryOverride(Clash.OverrideSlot.Session)
                        o.mode = normalized
                        patchOverride(Clash.OverrideSlot.Session, o)
                    }
                    if (normalized == TunnelState.Mode.Global) {
                        ensureGlobalSelectionSafe()
                    }
                    delay(120L)
                    val applied = runCatching {
                        withClash { queryTunnelState().mode }
                    }.getOrNull()
                    if (applied != null && normalizeTunnelMode(applied) != normalized) {
                        withClash {
                            val o = queryOverride(Clash.OverrideSlot.Session)
                            o.mode = normalized
                            patchOverride(Clash.OverrideSlot.Session, o)
                        }
                    } else if (applied != null) {
                        pendingTunnelMode = null
                    }
                }.exceptionOrNull()

                if (error != null && error !is CancellationException) {
                    pendingTunnelMode = null
                    showExceptionToast(error as? Exception ?: Exception(error))
                }
            }
        } catch (e: Exception) {
            pendingTunnelMode = null
            if (e !is CancellationException) showExceptionToast(e)
        } finally {
            // Defer heavy dashboard work so mode UI updates immediately (Rule / Global switch).
            launch {
                delay(48)
                scheduleRefresh()
            }
        }
    }

    private fun resolveStatusSnapshot(): StatusClient.StatusSnapshot =
        runCatching { StatusClient(this).statusSnapshot() }
            .getOrDefault(StatusClient.StatusSnapshot(clashRunning, null))

    /** Wait until the core has proxy groups (profile loaded), up to ~7s. */
    private suspend fun waitForProxyEngineReady() {
        repeat(35) {
            val ok = runCatching {
                withClash { queryProxyGroupNames(false).isNotEmpty() }
            }.getOrDefault(false)
            if (ok) return
            delay(200L)
        }
    }

    /**
     * [com.github.kr328.clash.service.clash.module.ConfigurationModule] runs
     * [Clash.patchSelector] in applyPostLoad then sends PROFILE_LOADED, which increments
     * [profileLoadEpoch]. The JNI snapshot can still show the YAML-default `now` right after
     * [waitForProxyEngineReady]; we wait for that post-load hop before the first dashboard fetch.
     */
    private suspend fun waitForProfileLoadEpochAfter(snapshot: Int, timeoutMs: Long = 3000L) {
        if (profileLoadEpoch.get() > snapshot) return
        withTimeoutOrNull(timeoutMs) {
            while (profileLoadEpoch.get() <= snapshot) {
                delay(15L)
            }
        }
    }

    private fun isAutoProxyGroup(name: String, group: ProxyGroup): Boolean {
        if (group.type == Proxy.Type.URLTest ||
            group.type == Proxy.Type.Fallback ||
            group.type == Proxy.Type.LoadBalance
        ) {
            return true
        }

        val normalized = name.lowercase(Locale.ROOT)
        return normalized.contains("auto") ||
            normalized.contains("url-test") ||
            normalized.contains("best") ||
            normalized.contains("fast") ||
            normalized.contains("авто") ||
            normalized.contains("лучш") ||
            normalized.contains("быстр")
    }

    private fun isBlockedGlobalChoice(name: String): Boolean =
        name.equals("DIRECT", ignoreCase = true) || name.equals("REJECT", ignoreCase = true)

    private fun isBlockedGlobalChoice(proxy: Proxy): Boolean =
        proxy.type == Proxy.Type.Direct ||
            proxy.type == Proxy.Type.Reject ||
            isBlockedGlobalChoice(proxy.name)

    private suspend fun ensureGlobalSelectionSafe() {
        runCatching {
            val global = withClash {
                queryProxyGroup("GLOBAL", com.github.kr328.clash.core.model.ProxySort.Default)
            }
            if (!isBlockedGlobalChoice(global.now)) return@runCatching
            val firstUsable = global.proxies.firstOrNull { !isBlockedGlobalChoice(it) }?.name ?: return@runCatching
            withClash { patchSelector("GLOBAL", firstUsable) }
        }
    }

    private suspend fun ensureGlobalSelectionSafeWithRetry(
        attempts: Int = 10,
        delayMs: Long = 180L,
    ) {
        repeat(attempts) { index ->
            val done = runCatching {
                val running = resolveStatusSnapshot().serviceRunning
                if (!running) return@runCatching true

                val mode = withClash { queryTunnelState().mode }
                if (normalizeTunnelMode(mode) != TunnelState.Mode.Global) return@runCatching true

                val hasGlobal = withClash { queryProxyGroupNames(false).any { it.equals("GLOBAL", ignoreCase = true) } }
                if (!hasGlobal) return@runCatching false

                ensureGlobalSelectionSafe()
                true
            }.getOrDefault(false)

            if (done) return
            if (index < attempts - 1) delay(delayMs)
        }
    }

    /**
     * Runs [healthCheck] on auto proxy groups. Only used in **Global** mode on connect: in **Rule**
     * mode it would fight [ConfigurationModule] applying [SelectionDao] and flashes the first
     * url-test/fallback hop before the user’s pick appears.
     */
    private suspend fun warmUpAutoProxyGroupsForGlobalOnly() {
        val mode = runCatching {
            withClash { normalizeTunnelMode(queryTunnelState().mode) }
        }.getOrNull() ?: return
        if (mode != TunnelState.Mode.Global) return
        warmUpAutoProxyGroups()
    }

    private suspend fun warmUpAutoProxyGroups() = coroutineScope {
        waitForProxyEngineReady()

        val groupNames = runCatching {
            withClash { queryProxyGroupNames(false) }
        }.getOrDefault(emptyList())
        if (groupNames.isEmpty()) return@coroutineScope

        val groupNameSet = groupNames.toSet()
        val autoGroups = linkedSetOf<String>()
        for (groupName in groupNames) {
            val group = runCatching {
                withClash { queryProxyGroup(groupName, com.github.kr328.clash.core.model.ProxySort.Default) }
            }.getOrNull() ?: continue
            if (isAutoProxyGroup(groupName, group)) {
                autoGroups += groupName
            }
            for (proxy in group.proxies) {
                if (proxy.name in groupNameSet &&
                    (proxy.type == Proxy.Type.URLTest ||
                        proxy.type == Proxy.Type.Fallback ||
                        proxy.type == Proxy.Type.LoadBalance)
                ) {
                    autoGroups += proxy.name
                }
            }
        }
        if (autoGroups.isEmpty()) return@coroutineScope

        autoGroups.map { group ->
            launch {
                runCatching {
                    withClash { healthCheck(group) }
                }
            }
        }.joinAll()
    }

    private fun showHomeImportSheet(design: MainDesign) {
        val dialog = AppBottomSheetDialog(this, fitContentHeight = false)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_home_import, null)
        dialog.setContentView(view)
        view.findViewById<View>(R.id.opt_clipboard).setOnClickListener {
            dialog.dismiss()
            launch { importFromClipboard(design) }
        }
        view.findViewById<View>(R.id.opt_url).setOnClickListener {
            dialog.dismiss()
            launch { createEmptyUrlProfileAndOpenEditor() }
        }
        view.findViewById<View>(R.id.opt_qr).setOnClickListener {
            dialog.dismiss()
            scanLauncher.launch(null)
        }
        dialog.show()
    }

    private fun onScanResult(result: QRResult) {
        launch {
            val d = design ?: return@launch
            when (result) {
                is QRSuccess -> {
                    val url = result.content.rawValue
                        ?: result.content.rawBytes?.let { String(it) }.orEmpty()
                    if (url.isNotBlank()) {
                        importSubscriptionFromUrl(d, url)
                    }
                }

                QRUserCanceled -> Unit
                QRMissingPermission ->
                    d.showExceptionToast(getString(R.string.import_from_qr_no_permission))

                is QRError ->
                    d.showExceptionToast(getString(R.string.import_from_qr_exception))
            }
        }
    }

    override suspend fun main() {
        val design = MainDesign(this)
        design.applyInitialModeFromPreference(uiStore.tunnelModePreference)

        setContentDesign(design)
        design.fetch()
        refreshAnnouncement(design)

        val tickerInteractive = ticker(TimeUnit.SECONDS.toMillis(2))
        // Bumped from 8s -> 30s. While the screen is off (or activity is in background),
        // the dashboard is invisible; we only need infrequent updates so totals stay
        // roughly accurate when the user comes back.
        val tickerIdle = ticker(TimeUnit.SECONDS.toMillis(30))
        val profileTicker = ticker(TimeUnit.MINUTES.toMillis(2))
        val refreshRequests = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
        val proxyDetailRequests =
            kotlinx.coroutines.channels.Channel<Pair<Profile, String>>(kotlinx.coroutines.channels.Channel.CONFLATED)
        var announcementRefreshPending = false
        var proxyDetailJob: Job? = null
        var homeProxyPatchJob: Job? = null
        var lastDashboardRefreshRequestAt = 0L

        fun scheduleDashboardRefresh(includeAnnouncement: Boolean = false, force: Boolean = false) {
            val now = SystemClock.elapsedRealtime()
            if (!force && !includeAnnouncement && now - lastDashboardRefreshRequestAt < 1200L) {
                return
            }
            lastDashboardRefreshRequestAt = now
            if (includeAnnouncement) {
                announcementRefreshPending = true
            }
            refreshRequests.trySend(Unit)
        }

        fun scheduleProxyDetailsRefresh(profile: Profile, group: String) {
            if (group.isNotBlank()) {
                proxyDetailRequests.trySend(profile to group)
            }
        }

        launch {
            while (isActive) {
                refreshRequests.receive()

                do {
                    val refreshAnnouncementNow = announcementRefreshPending
                    announcementRefreshPending = false

                    try {
                        val runtime = design.fetch()
                        if (!runtime.running ||
                            runtime.activeProfileUuid == null ||
                            runtime.proxyNames.isEmpty()
                        ) {
                            proxyDetailJob?.cancel()
                            proxyDetailJob = null
                        }
                        if (refreshAnnouncementNow) {
                            refreshAnnouncement(design)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        design.showExceptionToast(e)
                    }
                } while (refreshRequests.tryReceive().isSuccess)
            }
        }

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart,
                        Event.ServiceRecreated,
                        Event.ClashStop,
                        Event.ClashStart,
                        Event.ProfileLoaded,
                        Event.ProfileChanged -> {
                            // Coalesce expensive dashboard refreshes so a burst of
                            // service/profile broadcasts does not pile up parallel
                            // proxy-group queries and stall follow-up taps.
                            val serviceStateEvent = it == Event.ClashStop ||
                                it == Event.ClashStart ||
                                it == Event.ServiceRecreated
                            scheduleDashboardRefresh(
                                includeAnnouncement = it == Event.ActivityStart ||
                                    it == Event.ProfileChanged,
                                force = serviceStateEvent,
                            )
                            if (it == Event.ProfileLoaded || it == Event.ProfileChanged) {
                                launch {
                                    delay(260L)
                                    ensureGlobalSelectionSafeWithRetry()
                                }
                            }
                        }

                        else -> Unit
                    }
                }

                design.requests.onReceive {
                    when (it) {
                        MainDesign.Request.ToggleStatus -> {
                            launch {
                                if (isToggleStatusInFlight) return@launch
                                isToggleStatusInFlight = true
                                try {
                                    val runningNow = withContext(Dispatchers.IO) {
                                        resolveStatusSnapshot().serviceRunning
                                    }
                                    Remote.broadcasts.clashRunning = runningNow
                                    if (runningNow) {
                                        stopClashService()
                                        design.setClashRunning(false)
                                        design.setTunnelStarting(false)
                                    } else {
                                        if (!maybePromptRuBypass()) {
                                            return@launch
                                        }
                                        design.setTunnelStarting(true)
                                        try {
                                            design.startClash()
                                        } finally {
                                            scheduleDashboardRefresh()
                                        }
                                    }
                                } finally {
                                    // Keep lock short so rapid taps don't enqueue duplicate start/stop sequences.
                                    launch {
                                        delay(350L)
                                        isToggleStatusInFlight = false
                                    }
                                    scheduleDashboardRefresh(force = true)
                                }
                            }
                        }

                        MainDesign.Request.OpenNewProfile ->
                            showHomeImportSheet(design)

                        MainDesign.Request.OpenConnections ->
                            startActivity(ConnectionsActivity::class.intent)

                        MainDesign.Request.OpenLogs ->
                            startActivity(LogsActivity::class.intent)

                        MainDesign.Request.OpenRouting ->
                            startActivity(EffectiveRulesActivity::class.intent)

                        MainDesign.Request.OpenRules ->
                            startActivity(RuleSnippetActivity::class.intent)

                        MainDesign.Request.OpenProxyChain ->
                            startActivity(ProxyChainActivity::class.intent)

                        MainDesign.Request.OpenPerAppRouting ->
                            startActivity(AccessControlActivity::class.intent)

                        MainDesign.Request.OpenProfiles ->
                            startActivity(ProfilesActivity::class.intent)

                        MainDesign.Request.OpenSettings ->
                            startActivity(SettingsActivity::class.intent)

                        MainDesign.Request.OpenThemeSettings ->
                            startActivity(ThemeSettingsActivity::class.intent)

                        MainDesign.Request.OpenAppSettings ->
                            startActivity(SubscriptionIdentityActivity::class.intent)

                        MainDesign.Request.OpenAbout ->
                            design.showAbout(
                                versionName = queryAppVersionName(),
                                coreVersion = queryCoreVersionName(),
                            ) { setLoading, setStatus ->
                                if (isCheckingUpdates) return@showAbout
                                launch {
                                    isCheckingUpdates = true
                                    setStatus(null)
                                    setLoading(true)
                                    try {
                                        checkForUpdates(design, setStatus)
                                    } finally {
                                        isCheckingUpdates = false
                                        setLoading(false)
                                    }
                                }
                            }

                        MainDesign.Request.OpenImportClipboard ->
                            importFromClipboard(design)

                        MainDesign.Request.OpenImportQr ->
                            scanLauncher.launch(null)

                        MainDesign.Request.PatchModeDirect ->
                            launch { design.patchTunnelMode(TunnelState.Mode.Rule) { scheduleDashboardRefresh() } }

                        MainDesign.Request.PatchModeGlobal ->
                            launch { design.patchTunnelMode(TunnelState.Mode.Global) { scheduleDashboardRefresh() } }

                        MainDesign.Request.PatchModeRule ->
                            launch { design.patchTunnelMode(TunnelState.Mode.Rule) { scheduleDashboardRefresh() } }

                        MainDesign.Request.CycleTheme -> {
                            uiStore.darkMode = when (uiStore.darkMode) {
                                DarkMode.ForceLight -> DarkMode.ForceDark
                                else -> DarkMode.ForceLight
                            }
                            recreate()
                        }
                    }
                }

                design.patchHomeProxyRequests.onReceive { (profile, group, name) ->
                    homeProxyPatchJob?.cancel()
                    homeProxyPatchJob = launch {
                        try {
                            val runningNow = withContext(Dispatchers.IO) {
                                resolveStatusSnapshot().serviceRunning
                            }
                            Remote.broadcasts.clashRunning = runningNow
                            if (!runningNow) {
                                if (isProxyProviderKeyName(name)) {
                                    scheduleDashboardRefresh()
                                    return@launch
                                }
                                design.markProxySelectionPending(profile, group, name)
                                withProfile {
                                    rememberProxySelection(profile.uuid, group, name)
                                }
                                uiStore.proxyLastGroup = group
                                scheduleDashboardRefresh()
                                return@launch
                            }
                            design.markProxySelectionPending(profile, group, name)
                            val patched = withClash {
                                patchSelector(group, name)
                            }
                            if (patched) {
                                uiStore.proxyLastGroup = group
                                withProfile {
                                    rememberProxySelection(profile.uuid, group, name)
                                }
                                closeConnectionsAfterUserProxySwitchIfEnabled { message, duration ->
                                    design.showToast(message, duration)
                                }
                            } else {
                                design.showToast(R.string.proxy_switch_selector_failed, ToastDuration.Long)
                            }
                            scheduleProxyDetailsRefresh(profile, group)
                        } catch (e: CancellationException) {
                            throw e
                        }
                    }
                }

                design.profilePingAllRequests.onReceive { (profile, group, nodeNames) ->
                    launch {
                        try {
                            design.setPingingProfile(profile.uuid)
                            design.clearStandalonePingForProfile(profile.uuid)
                            val engineReady = runCatching {
                                resolveStatusSnapshot().serviceRunning &&
                                    withClash { queryProxyGroupNames(false).isNotEmpty() }
                            }.getOrDefault(false)
                            if (engineReady) {
                                val activeUuid = withProfile { queryActive()?.uuid }
                                if (activeUuid != profile.uuid && profile.imported) {
                                    withProfile { setActive(profile) }
                                }
                                waitForProxyEngineReady()
                                val groupsToRefresh = collectRuntimeGroupTree(group)
                                    .ifEmpty { linkedSetOf(group).filter { it.isNotBlank() }.toSet() }
                                val jobs = groupsToRefresh.map { groupName ->
                                    launch {
                                        runCatching {
                                            withClash { healthCheck(groupName) }
                                        }
                                    }
                                }
                                while (isActive && jobs.any { !it.isCompleted }) {
                                    delay(350L)
                                    val partial = refreshRuntimeGroupDetails(groupsToRefresh)
                                    if (partial.isNotEmpty()) {
                                        design.patchProxyDetails(partial)
                                    }
                                }
                                jobs.forEach { it.join() }
                                val refreshed = refreshRuntimeGroupDetails(groupsToRefresh)
                                if (refreshed.isNotEmpty()) {
                                    design.patchProxyDetails(refreshed)
                                }
                                scheduleDashboardRefresh()
                            } else {
                                val offlineGroups = withProfile { readProxyGroupsPreview(profile.uuid) }
                                val pingTargets = collectOfflineLeafProxyNames(group, nodeNames, offlineGroups)
                                val jobs = pingTargets.map { name -> launch(Dispatchers.IO) {
                                    if (StandalonePing.isBuiltinProxyName(name)) return@launch
                                    val yaml = withProfile { readProxyEntryYaml(profile.uuid, name) }
                                        ?: return@launch
                                    val hp = StandalonePing.parseServerPortFromProxyYaml(yaml) ?: return@launch
                                    val ms = StandalonePing.tcpConnectMs(hp.first, hp.second)
                                        .getOrNull()?.toInt() ?: return@launch
                                    design.patchStandalonePingResults(profile.uuid, mapOf(name to ms))
                                } }
                                jobs.forEach { it.join() }
                            }
                        } catch (e: Exception) {
                            design.showExceptionToast(e)
                        } finally {
                            design.setPingingProfile(null)
                        }
                    }
                }

                design.profileForceUpdateRequests.onReceive { profile ->
                    launch {
                        withProfile { update(profile.uuid) }
                        // Also re-pull operator headers (announcement, support, userinfo) right away,
                        // so the inline announcement text updates with the same tap.
                        launch(Dispatchers.IO) {
                            uiStore.subscriptionMetadataLastFetch = 0L
                            runCatching { syncSubscriptionMetadata() }
                            withContext(Dispatchers.Main) { refreshAnnouncement(design) }
                        }
                        design.showToast(R.string.profile_update_scheduled, ToastDuration.Long)
                        scheduleDashboardRefresh()
                    }
                }

                design.profileProxyYamlRequests.onReceive { (profile, _, proxyName) ->
                    launch {
                        val yaml = withProfile { readProxyEntryYaml(profile.uuid, proxyName) }
                        showProxyYamlDialog(proxyName, yaml ?: getString(R.string.proxy_yaml_missing))
                    }
                }

                design.profileExpandChanged.onReceive {
                    scheduleDashboardRefresh()
                }

                design.profileVisibleGroupChanged.onReceive { (profile, group) ->
                    uiStore.proxyLastGroup = group
                    scheduleProxyDetailsRefresh(profile, group)
                }

                proxyDetailRequests.onReceive { (profile, group) ->
                    proxyDetailJob?.cancel()
                    proxyDetailJob = launch {
                        try {
                            design.fetchVisibleProxyGroup(profile, group)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // Runtime detail queries can race profile reload/startup; the next UI event retries.
                        }
                    }
                }

                design.profileActivateRequests.onReceive { profile ->
                    launch {
                        withProfile {
                            if (profile.imported) {
                                setActive(profile)
                                ensureGlobalSelectionSafeWithRetry()
                            } else {
                                design.requestSave(profile)
                            }
                        }
                        scheduleDashboardRefresh()
                    }
                }

                design.profileMenuRequests.onReceive { (profile, anchor) ->
                    launch(Dispatchers.Main) {
                        showProfileOverflowMenu(design, profile, anchor)
                    }
                }

                design.profileEditRequests.onReceive { profile ->
                    showProfileQuickEditSheet(design, profile) { design.fetch() }
                }

                if (clashRunning && activityStarted) {
                    val interactive = getSystemService<PowerManager>()?.isInteractive ?: true
                    // Pick the ticker BEFORE registering the select branch so that the idle
                    // ticker (and only it) wakes us when the screen is off, instead of both
                    // tickers competing.
                    val trafficTicker = if (interactive) tickerInteractive else tickerIdle
                    trafficTicker.onReceive {
                        launch { design.fetchTraffic() }
                    }
                }

                if (activityStarted) {
                    profileTicker.onReceive {
                        design.updateElapsed()
                    }
                }
            }
        }
    }

    private fun showProfileOverflowMenu(design: MainDesign, profile: Profile, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_profile_home, popup.menu)
        val m = popup.menu
        m.findItem(R.id.profile_menu_set_active).isVisible =
            profile.imported && !profile.active
        m.findItem(R.id.profile_menu_update).isVisible =
            profile.imported && profile.type != Profile.Type.File
        m.findItem(R.id.profile_menu_subscription_sources).isVisible = profile.imported
        m.findItem(R.id.profile_menu_duplicate).isVisible = profile.imported
        val shareLocked = ServiceStore(this).subscriptionShareLinksLocked
        m.findItem(R.id.profile_menu_share).isVisible =
            profile.imported && profile.type == Profile.Type.Url && !shareLocked
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.profile_menu_set_active -> {
                    launch {
                        withProfile {
                            if (profile.imported) {
                                setActive(profile)
                                ensureGlobalSelectionSafeWithRetry()
                            } else {
                                design.requestSave(profile)
                            }
                        }
                        design.fetch()
                    }
                    true
                }
                R.id.profile_menu_update -> {
                    launch {
                        withProfile { update(profile.uuid) }
                        design.showToast(R.string.profile_update_scheduled, ToastDuration.Long)
                        design.fetch()
                    }
                    true
                }
                R.id.profile_menu_share -> {
                    val text = profile.source.takeIf { it.isNotBlank() } ?: return@setOnMenuItemClickListener false
                    startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            },
                            getString(R.string.profile_menu_share),
                        ),
                    )
                    true
                }
                R.id.profile_menu_edit -> {
                    showProfileQuickEditSheet(design, profile) { design.fetch() }
                    true
                }
                R.id.profile_menu_subscription_sources -> {
                    if (profile.imported) {
                        startActivity(ProxyProvidersEditorActivity::class.intent.setUUID(profile.uuid))
                    }
                    true
                }
                R.id.profile_menu_duplicate -> {
                    launch {
                        val uuid = withProfile { clone(profile.uuid) }
                        startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                    }
                    true
                }
                R.id.profile_menu_delete -> {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.delete)
                        .setMessage(R.string.profile_delete_confirm)
                        .setPositiveButton(R.string.delete) { _, _ ->
                            launch {
                                withProfile { delete(profile.uuid) }
                                design.fetch()
                            }
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private suspend fun importFromClipboard(design: MainDesign) {
        val text = withContext(Dispatchers.Main) {
            getSystemService<ClipboardManager>()?.primaryClip
                ?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
        }
        if (text.isEmpty()) {
            design.showToast(R.string.clipboard_empty, ToastDuration.Short)
            return
        }
        if (!ShareImportSupport.isAllowedUrlProfileSource(text)) {
            design.showToast(R.string.clipboard_no_url, ToastDuration.Long)
            return
        }
        importSubscriptionFromUrl(design, text)
    }

    /**
     * Resolves a friendly name from the subscription HTTP response, creates the profile,
     * [commit]s the fetch, activates it — no Properties screen on success.
     */
    private suspend fun importSubscriptionFromUrl(design: MainDesign, url: String) {
        design.showToast(R.string.import_resolving, ToastDuration.Short)
        val name = withTimeoutOrNull(4500L) {
            SubscriptionNameGuesser.guess(this@MainActivity, url)
        } ?: SubscriptionNameGuesser.guessFast(url)
        val uuid = withProfile {
            create(Profile.Type.Url, name, url)
        }
        try {
            commitProfileWithProgress(uuid)
        } catch (e: Exception) {
            showImportCommitFailureDialog(uuid, e)
            return
        }
        val profile = withProfile { queryByUUID(uuid) }
        if (profile?.imported == true) {
            withProfile { setActive(profile) }
            ensureGlobalSelectionSafeWithRetry()
            design.showToast(getString(R.string.import_done_named, name), ToastDuration.Long)
        } else {
            launchProperties(uuid)
        }
        design.fetch()
    }

    private suspend fun commitProfileWithProgress(uuid: UUID) {
        withModelProgressBar {
            configure {
                isIndeterminate = true
                text = getString(R.string.initializing)
            }

            coroutineScope {
                withProfile {
                    commit(uuid) { status ->
                        launch {
                            configure {
                                applyFetchStatus(this@MainActivity, status)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun launchProperties(uuid: UUID) {
        startActivityForResult(
            ActivityResultContracts.StartActivityForResult(),
            PropertiesActivity::class.intent.setUUID(uuid)
        )
    }

    private suspend fun showImportCommitFailureDialog(uuid: UUID, e: Throwable) {
        withContext(Dispatchers.Main) {
            val raw = e.message?.trim().orEmpty().ifBlank { e.javaClass.simpleName }
            val msg = if (raw.length > 6000) raw.take(6000) + "…" else raw
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(getString(R.string.import_failed_title))
                .setMessage(msg)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(getString(R.string.import_failed_open_editor)) { _, _ ->
                    startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                }
                .show()
        }
    }

    private suspend fun showProxyYamlDialog(title: String, body: String) {
        withContext(Dispatchers.Main) {
            val pad = (16 * resources.displayMetrics.density).toInt()
            val scroll = ScrollView(this@MainActivity)
            val tv = TextView(this@MainActivity).apply {
                text = body
                setTextIsSelectable(true)
                setPadding(pad, pad, pad, pad)
                textSize = 12f
                typeface = Typeface.MONOSPACE
            }
            scroll.addView(tv)
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private suspend fun MainDesign.fetch(): HomeRuntimeSnapshot {
        val status = withContext(Dispatchers.IO) { resolveStatusSnapshot() }
        val running = status.serviceRunning
        Remote.broadcasts.clashRunning = running
        setClashRunning(running)

        var state = if (running) {
            runCatching {
                withClash { queryTunnelState() }
            }.getOrElse {
                TunnelState(parseTunnelMode(uiStore.tunnelModePreference) ?: TunnelState.Mode.Rule)
            }
        } else {
            TunnelState(parseTunnelMode(uiStore.tunnelModePreference) ?: TunnelState.Mode.Rule)
        }
        val rawMode = state.mode
        state = TunnelState(normalizeTunnelMode(state.mode))
        if (running && rawMode != state.mode) {
            runCatching {
                withClash {
                    val o = queryOverride(Clash.OverrideSlot.Session)
                    o.mode = state.mode
                    patchOverride(Clash.OverrideSlot.Session, o)
                }
            }
        }

        if (running) {
            val pref = uiStore.tunnelModePreference
            if (pref.isNotEmpty()) {
                val desired = parseTunnelMode(pref)
                if (desired != null && state.mode != desired) {
                    withClash {
                        val o = queryOverride(Clash.OverrideSlot.Session)
                        o.mode = desired
                        patchOverride(Clash.OverrideSlot.Session, o)
                    }
                    state = withClash { queryTunnelState() }
                    state = TunnelState(normalizeTunnelMode(state.mode))
                }
            } else {
                uiStore.tunnelModePreference = normalizeTunnelMode(state.mode).name
            }

            val preferredMode = parseTunnelMode(uiStore.tunnelModePreference)
            if (preferredMode == TunnelState.Mode.Global || state.mode == TunnelState.Mode.Global) {
                ensureGlobalSelectionSafe()
            }
        }

        val pendingMode = pendingTunnelMode
        if (pendingMode != null) {
            setMode(pendingMode)
            if (state.mode == pendingMode) {
                pendingTunnelMode = null
            }
        } else {
            val preferredMode = if (running) parseTunnelMode(uiStore.tunnelModePreference) else null
            setMode(preferredMode ?: state.mode)
        }

        val active = withProfile { queryActive() }
        val profiles = withProfile { queryAll() }
        setProfileName(active?.name ?: status.currentProfile)
        patchProfiles(profiles)

        val proxyNames = if (running) {
            runCatching {
                withClash { queryProxyGroupNames(uiStore.proxyExcludeNotSelectable) }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val activeUuid = active?.uuid
        val expandedSet = getExpandedProfileUuids()
        val summaryPreviewUuid = activeUuid ?: profiles.firstOrNull { it.imported }?.uuid
        val previewUuids = (expandedSet + listOfNotNull(summaryPreviewUuid)).toSet()
        val offlinePreviewByProfile = previewUuids.associateWith { uuid ->
            withProfile { readProxyGroupsPreview(uuid) }
        }
        val offlineSelectionsByProfile = previewUuids.associateWith { uuid ->
            withProfile { queryProxySelections(uuid) }
        }

        patchProxyGroups(
            proxyNames,
            running,
            state.mode,
            uiStore.proxyLastGroup,
            offlinePreviewByProfile,
            activeUuid,
            offlineSelectionsByProfile,
        )
        if (!running || activeUuid == null || proxyNames.isEmpty()) {
            clearProxyDetails()
        }
        syncThemeToggleIcon(uiStore.darkMode)

        return HomeRuntimeSnapshot(
            running = running,
            activeProfileUuid = activeUuid,
            proxyNames = proxyNames,
        )
    }

    private suspend fun MainDesign.fetchVisibleProxyGroup(profile: Profile, group: String) {
        val status = withContext(Dispatchers.IO) { resolveStatusSnapshot() }
        Remote.broadcasts.clashRunning = status.serviceRunning
        if (!status.serviceRunning || group.isBlank()) {
            clearProxyDetails()
            return
        }

        val activeUuid = withProfile { queryActive()?.uuid }
        if (activeUuid != profile.uuid || !profile.imported) {
            return
        }

        val groupsToFetch = collectRuntimeGroupTree(group)
            .ifEmpty { linkedSetOf(group).filter { it.isNotBlank() }.toSet() }
        repeat(12) { attempt ->
            val details = refreshRuntimeGroupDetails(groupsToFetch)
            val rootDetail = details[group]
                ?: details.entries.firstOrNull { it.key.equals(group, ignoreCase = true) }?.value
            val emptyDynamic = rootDetail != null &&
                rootDetail.proxies.isEmpty() &&
                rootDetail.type.group &&
                rootDetail.type != Proxy.Type.Relay
            val retry = (details.isEmpty() || emptyDynamic) && attempt < 11
            if (!retry) {
                if (details.isNotEmpty() && withProfile { queryActive()?.uuid } == profile.uuid) {
                    patchProxyDetails(details)
                }
                return
            }
            delay(200L)
        }
    }

    private suspend fun MainDesign.fetchTraffic() {
        withClash {
            val total = queryTrafficTotal()
            if (total != lastForwardedTrafficTotal) {
                lastForwardedTrafficTotal = total
                setForwarded(total)
            }
        }
    }

    private suspend fun MainDesign.startClash() {
        val active = prepareActiveProfileForStart()

        if (active == null) return

        val loadEpochSnapshot = profileLoadEpoch.get()
        try {
            val vpnRequest = startClashService()
            if (vpnRequest != null) {
                val result = startActivityForResult(
                    ActivityResultContracts.StartActivityForResult(),
                    vpnRequest
                )

                if (result.resultCode == RESULT_OK) {
                    startClashService()
                } else {
                    setTunnelStarting(false)
                    showToast(R.string.vpn_permission_denied, ToastDuration.Long)
                    return
                }
            }
            launch {
                val d = design
                try {
                    d?.setPingingProfile(active.uuid)
                    waitForProxyEngineReady()
                    waitForProfileLoadEpochAfter(loadEpochSnapshot)
                    d?.fetch()
                    warmUpAutoProxyGroupsForGlobalOnly()
                    d?.fetch()
                } finally {
                    d?.setPingingProfile(null)
                }
            }
        } catch (e: Exception) {
            setTunnelStarting(false)
            showToast(R.string.vpn_start_failed_plain, ToastDuration.Indefinite) {
                setAction(R.string.logs) {
                    startActivity(LogsActivity::class.intent)
                }
            }
        }
    }

    private suspend fun MainDesign.prepareActiveProfileForStart(): Profile? {
        val service = ServiceStore(this@MainActivity)
        service.seedDefaultGeoMirrors = true

        var active = withProfile { queryActive() }
        if (active == null) {
            val importedProfiles = withProfile { queryAll() }
                .filter { it.imported && !it.pending }
            if (importedProfiles.size == 1) {
                val only = importedProfiles.first()
                withProfile { setActive(only) }
                active = withProfile { queryActive() }
            }
        }

        if (active == null) {
            setTunnelStarting(false)
            showToast(R.string.start_no_subscription_plain, ToastDuration.Long) {
                setAction(R.string.main_add_subscription) {
                    showHomeImportSheet(this@prepareActiveProfileForStart)
                }
            }
            return null
        }

        if (!active.imported || active.pending) {
            setTunnelStarting(false)
            showToast(R.string.start_profile_not_ready_plain, ToastDuration.Long) {
                setAction(R.string.profiles) {
                    startActivity(ProfilesActivity::class.intent)
                }
            }
            return null
        }

        if (shouldAutoRefreshBeforeStart(active)) {
            showToast(R.string.profile_auto_refreshing, ToastDuration.Short)
            val refreshed = runCatching {
                withProfile { update(active.uuid) }
            }.isSuccess
            if (!refreshed) {
                showToast(R.string.profile_auto_refresh_failed_starting_saved, ToastDuration.Long)
            }
        }

        return withProfile { queryActive() } ?: active
    }

    private fun shouldAutoRefreshBeforeStart(profile: Profile): Boolean {
        if (profile.type != Profile.Type.Url || profile.source.isBlank()) return false
        val now = System.currentTimeMillis()
        val last = profile.updatedAt.takeIf { it > 0L } ?: return true
        val userInterval = profile.interval.takeIf { it >= TimeUnit.MINUTES.toMillis(15) }
        val interval = userInterval ?: TimeUnit.HOURS.toMillis(12)
        return now - last >= interval
    }

    private suspend fun queryAppVersionName(): String {
        return withContext(Dispatchers.IO) {
            val raw = packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
            val semver = Regex("""(\d+\.\d+\.\d+)""").find(raw)?.groupValues?.getOrNull(1) ?: raw
            val channel = if (BuildConfig.DEBUG) "Debug" else "Release"
            "$semver.$channel"
        }
    }

    private suspend fun queryCoreVersionName(): String {
        return withContext(Dispatchers.IO) {
            val raw = Bridge.nativeCoreVersion().replace("_", "-")
            val semver = Regex("""v?(\d+\.\d+\.\d+)""").find(raw)?.groupValues?.getOrNull(1)
            val normalized = semver ?: raw
            "Mihomo $normalized"
        }
    }

    private suspend fun checkForUpdates(design: MainDesign, setStatus: (String?) -> Unit) {
        val latest = GitHubReleaseUpdate.fetchLatest()
        if (latest == null) {
            setStatus(getString(R.string.about_update_check_failed))
            return
        }
        val current = withContext(Dispatchers.IO) {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
        }
        val hasUpdate = GitHubReleaseUpdate.compareVersions(latest.tagName, current) > 0
        withContext(Dispatchers.Main) {
            if (!hasUpdate) {
                setStatus(getString(R.string.about_update_latest))
                return@withContext
            }
            setStatus(getString(R.string.about_update_available, latest.tagName))
            AppUpdateChecker.showUpdateNotification(this@MainActivity, latest)
        }
    }

    private fun checkDownloadedApkAndInstall(downloadId: Long) {
        val dm = getSystemService<DownloadManager>() ?: return
        val query = DownloadManager.Query().setFilterById(downloadId)
        dm.query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) return
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> installDownloadedApk(dm, downloadId)
                DownloadManager.STATUS_FAILED -> {
                    clearPendingDownloadState()
                    Toast.makeText(this, R.string.about_download_failed, Toast.LENGTH_SHORT).show()
                }
                else -> Unit
            }
        }
    }

    private fun installDownloadedApk(dm: DownloadManager, downloadId: Long) {
        val uri = dm.getUriForDownloadedFile(downloadId)
        if (uri == null) {
            Toast.makeText(this, R.string.about_download_failed, Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
            Toast.makeText(this, R.string.about_enable_unknown_apps, Toast.LENGTH_LONG).show()
            updatePrefs.edit().putLong("pending_download_id", downloadId).apply()
            return
        }

        // Clear pending marker before opening installer to avoid install loop
        // if user cancels installation and returns to the app.
        clearPendingDownloadState()
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        }.onFailure {
            Toast.makeText(this, R.string.about_download_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher =
                registerForActivityResult(RequestPermission()) { _: Boolean -> }

            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (!downloadReceiverRegistered) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    apkDownloadReceiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    RECEIVER_EXPORTED
                )
            } else {
                registerReceiver(apkDownloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            }
            downloadReceiverRegistered = true
        }

        // If the installed version is already at/ahead of whatever we last notified about,
        // clear the stale "update available" banner so users don't see it after upgrading.
        runCatching {
            val current = packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
            AppUpdateChecker.resetIfUpdated(this, current)
        }

        handleUpdateIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUpdateIntent(intent)
    }

    /** Triggered when user taps "Download & install" on the update notification.
     *  Enqueue the APK from inside the Activity so our runtime receiver owns the download id
     *  end-to-end — manifest receivers for DOWNLOAD_COMPLETE are blocked on Android 8+. */
    private fun handleUpdateIntent(intent: Intent?) {
        if (intent?.action != ACTION_DOWNLOAD_AND_INSTALL_UPDATE) return
        val tag = intent.getStringExtra(EXTRA_UPDATE_TAG).orEmpty()
        val url = intent.getStringExtra(EXTRA_UPDATE_APK_URL).orEmpty()
        val name = intent.getStringExtra(EXTRA_UPDATE_APK_NAME)
        // Consume so a configuration change doesn't re-enqueue.
        intent.action = null
        if (url.isBlank()) return
        AppUpdateChecker.dismissUpdateNotification(this)
        runCatching {
            val id = GitHubReleaseUpdate.enqueueApkDownload(
                context = this,
                tagName = tag.ifBlank { "update" },
                apkUrl = url,
                apkName = name,
            )
            if (id > 0L) {
                pendingApkDownloadId = id
                updatePrefs.edit().putLong("pending_download_id", id).apply()
                Toast.makeText(this, R.string.about_download_started, Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            Toast.makeText(this, R.string.about_download_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        events.trySend(Event.ActivityStart)
        val pending = updatePrefs.getLong("pending_download_id", -1L)
        if (pending > 0L) {
            pendingApkDownloadId = pending
            checkDownloadedApkAndInstall(pending)
        }
    }

    override fun onDestroy() {
        if (downloadReceiverRegistered) {
            runCatching { unregisterReceiver(apkDownloadReceiver) }
            downloadReceiverRegistered = false
        }
        super.onDestroy()
    }

    /** Proxy-provider YAML keys (sub1, sub2, …) are not leaf proxy names; never persist as selection. */
    private fun isProxyProviderKeyName(name: String): Boolean =
        name.matches(Regex("^sub\\d+$", RegexOption.IGNORE_CASE))

    private suspend fun collectRuntimeGroupTree(rootGroup: String): Set<String> {
        if (rootGroup.isBlank()) return emptySet()
        val knownGroups = runCatching { withClash { queryProxyGroupNames(false).toSet() } }
            .getOrDefault(emptySet())
        val seen = linkedSetOf<String>()

        suspend fun visit(groupName: String) {
            if (groupName.isBlank() || groupName in seen) return
            val group = runCatching {
                withClash { queryProxyGroup(groupName, uiStore.proxySort) }
            }.getOrNull() ?: return
            seen += groupName
            for (proxy in group.proxies) {
                if (proxy.type.group || proxy.name in knownGroups) {
                    visit(proxy.name)
                }
            }
        }

        visit(rootGroup)
        return seen
    }

    private suspend fun refreshRuntimeGroupDetails(groups: Set<String>): Map<String, ProxyGroup> {
        if (groups.isEmpty()) return emptyMap()
        val refreshed = LinkedHashMap<String, ProxyGroup>()
        for (group in groups) {
            val detail = runCatching {
                withClash { queryProxyGroup(group, uiStore.proxySort) }
            }.getOrNull() ?: continue
            refreshed[group] = detail
        }
        return refreshed
    }

    private fun collectOfflineLeafProxyNames(
        rootGroup: String,
        directNames: List<String>,
        groups: Map<String, ProxyGroupPreviewRow>,
    ): List<String> {
        val leaves = linkedSetOf<String>()
        val seenGroups = linkedSetOf<String>()

        fun visitName(name: String) {
            if (name.isBlank()) return
            val nested = groups[name]?.members
            if (nested == null) {
                leaves += name
                return
            }
            if (!seenGroups.add(name)) return
            nested.forEach(::visitName)
        }

        if (rootGroup.isNotBlank() && rootGroup in groups) {
            visitName(rootGroup)
        } else {
            directNames.forEach(::visitName)
        }
        return leaves.toList()
    }

    /**
     * Reads announcement settings from [uiStore] and forwards them to the design.
     * Resets the dismissed-flag automatically when the announcement text changes.
     */
    private suspend fun refreshAnnouncement(design: com.github.kr328.clash.design.MainDesign) {
        // Render current cache first for snappy UI.
        renderAnnouncementFromStore(design)

        // Then refresh metadata and re-render so support/announce updates appear immediately.
        launch(Dispatchers.IO) {
            val updated = runCatching { syncSubscriptionMetadata() }.isSuccess
            if (updated) {
                withContext(Dispatchers.Main) {
                    renderAnnouncementFromStore(design)
                }
            }
        }
    }

    private suspend fun renderAnnouncementFromStore(design: com.github.kr328.clash.design.MainDesign) {
        val active = runCatching { withProfile { queryActive() } }.getOrNull()
        com.github.kr328.clash.util.SubscriptionMetaCache.hydrateUiStoreForProfile(uiStore, active)

        val text = uiStore.announcement
        val url = uiStore.announcementUrl
        val supportUrl = uiStore.supportUrl
        val hash = text.hashCode().toString()
        if (uiStore.announcementSeenHash != hash) {
            uiStore.announcementSeenHash = hash
            uiStore.announcementDismissed = false
            uiStore.announcementCollapsed = false
        }
        val textVisible = if (uiStore.announcementCardEnabled) {
            text.isNotBlank()
        } else {
            text.isNotBlank() && !uiStore.announcementDismissed
        }
        val usage = com.github.kr328.clash.common.util.SubscriptionUsage.parse(
            uiStore.subscriptionUserinfo.takeIf { it.isNotBlank() }
        )

        design.setAnnouncement(
            text = if (textVisible) text else null,
            url = url.takeIf { it.isNotBlank() },
            usage = usage,
            supportUrl = supportUrl.takeIf { it.isNotBlank() },
            onOpenUrl = { openExternalUrl(it) },
            onRefresh = {
                launch(Dispatchers.IO) {
                    uiStore.subscriptionMetadataLastFetch = 0L
                    runCatching { syncSubscriptionMetadata() }
                    withContext(Dispatchers.Main) { refreshAnnouncement(design) }
                }
            },
            onSupport = {
                supportUrl.takeIf { it.isNotBlank() }?.let { openExternalUrl(it) }
            },
            announcementCollapsed = uiStore.announcementCollapsed,
            onToggleCollapsed = if (textVisible && uiStore.announcementCardEnabled) {
                {
                    uiStore.announcementCollapsed = !uiStore.announcementCollapsed
                    launch { renderAnnouncementFromStore(design) }
                }
            } else {
                null
            },
        )
    }

    /**
     * When the per-app exclusion list is empty, offer to seed it with installed
     * Russian apps before starting the tunnel. Skip still starts VPN; cancel does not.
     */
    private suspend fun maybePromptRuBypass(): Boolean {
        val service = ServiceStore(this)
        if (uiStore.ruBypassPromptHandled) return true
        val packages = withContext(Dispatchers.IO) { service.accessControlPackages }
        if (packages.isNotEmpty()) return true

        return suspendCancellableCoroutine { cont ->
            val message = buildString {
                append(getString(R.string.ru_bypass_prompt_message))
                append("\n\n")
                append(getString(R.string.ru_bypass_prompt_tile_note))
            }
            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ru_bypass_prompt_title)
                .setMessage(message)
                .setPositiveButton(R.string.ru_bypass_prompt_apply) { d, _ ->
                    d.dismiss()
                    launch {
                        val count = withContext(Dispatchers.IO) {
                            val seed = RussianBypassDefaults.installed(packageManager)
                            if (seed.isNotEmpty()) {
                                service.accessControlPackages = seed
                                service.accessControlMode = AccessControlMode.DenySelected
                                service.russianBypassSeeded = true
                            }
                            seed.size
                        }
                        uiStore.ruBypassPromptHandled = true
                        if (count > 0) {
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.ru_bypass_prompt_seeded, count),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        if (cont.isActive) cont.resumeWith(Result.success(true))
                    }
                }
                .setNegativeButton(R.string.ru_bypass_prompt_skip) { d, _ ->
                    d.dismiss()
                    uiStore.ruBypassPromptHandled = true
                    if (cont.isActive) cont.resumeWith(Result.success(true))
                }
                .setOnCancelListener {
                    if (cont.isActive) cont.resumeWith(Result.success(false))
                }
                .show()
            cont.invokeOnCancellation { runCatching { dialog.dismiss() } }
        }
    }

    override fun onProfileLoaded() {
        profileLoadEpoch.incrementAndGet()
        super.onProfileLoaded()
    }

    override fun onProfileUpdateCompleted(uuid: UUID?) {
        super.onProfileUpdateCompleted(uuid)
        if (uuid == null) return

        launch {
            val name = withProfile { queryByUUID(uuid)?.name } ?: return@launch
            design?.showToast(
                getString(R.string.toast_profile_updated_complete, name),
                ToastDuration.Long
            )
        }
    }

    override fun onProfileUpdateFailed(uuid: UUID?, reason: String?) {
        super.onProfileUpdateFailed(uuid, reason)
        if (uuid == null) return

        launch {
            val name = withProfile { queryByUUID(uuid)?.name } ?: return@launch
            design?.showToast(
                getString(R.string.toast_profile_updated_failed, name, reason ?: "Unknown"),
                ToastDuration.Long
            ) {
                setAction(R.string.edit) {
                    startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                }
            }
        }
    }

    /**
     * Periodically pulls panel-style headers (support-url, announce, profile-title,
     * profile-update-interval, subscription-userinfo, …) from the *active* URL profile.
     * Throttled to once per ~6 hours; user-edited fields stay user-edited.
     */
    private suspend fun syncSubscriptionMetadata() {
        val active = runCatching { withProfile { queryActive() } }.getOrNull() ?: return
        if (active.type != Profile.Type.Url) return
        val url = active.source.takeIf { it.isNotBlank() } ?: return

        val now = System.currentTimeMillis() / 1000L
        val cooldown = 6L * 3600L
        val activeId = active.uuid.toString()
        val sameProfileThrottle =
            uiStore.subscriptionUserinfo.isNotBlank() &&
                activeId == uiStore.subscriptionMetadataLastFetchProfileId &&
                now - uiStore.subscriptionMetadataLastFetch < cooldown
        if (sameProfileThrottle) {
            return
        }

        val meta = com.github.kr328.clash.common.util.SubscriptionMetadataFetcher.fetch(
            this,
            url,
            SubscriptionOverrides.getUserAgent(this, active.uuid),
            uiStore.subscriptionMetadataAllowInsecureHttp,
        )
        meta.shareLinksDisable?.let { ServiceStore(this).subscriptionShareLinksLocked = it }
        uiStore.subscriptionHwidActive = meta.hwidActive?.toString().orEmpty()
        uiStore.subscriptionHwidNotSupported = meta.hwidNotSupported?.toString().orEmpty()
        uiStore.subscriptionHwidMaxDevicesReached = meta.hwidMaxDevicesReached?.toString().orEmpty()
        uiStore.subscriptionHwidLimit = meta.hwidLimit?.toString().orEmpty()
        if (meta.isEmpty()) {
            // still mark the attempt to avoid hammering the server every screen-on
            uiStore.subscriptionMetadataLastFetch = now
            uiStore.subscriptionMetadataLastFetchProfileId = activeId
            return
        }
        uiStore.subscriptionMetadataLastFetch = now
        uiStore.subscriptionMetadataLastFetchProfileId = activeId

        // Always-mirrored fields (cache only):
        meta.subscriptionUserinfo?.let { uiStore.subscriptionUserinfo = it }
        meta.profileWebPageUrl?.let { uiStore.profileWebPageUrl = it }
        meta.profileUpdateIntervalHours?.let { hours ->
            uiStore.profileUpdateIntervalHours = hours
            if (!uiStore.subscriptionMetadataLockUser) {
                val ms = java.util.concurrent.TimeUnit.HOURS.toMillis(hours.toLong())
                runCatching {
                    withProfile { applySubscriptionUpdateInterval(active.uuid, ms) }
                }
            }
        }

        // User-overridable fields — skip if user opted out of operator overrides.
        if (!uiStore.subscriptionMetadataLockUser) {
            meta.supportUrl?.let { uiStore.supportUrl = it }
            meta.announcement?.let { uiStore.announcement = it }
            meta.announcementUrl?.let { uiStore.announcementUrl = it }
        }
        SubscriptionMetaCache.put(
            uiStore,
            active.uuid,
            SubscriptionMetaCache.Entry(
                announcement = meta.announcement.orEmpty(),
                announcementUrl = meta.announcementUrl.orEmpty(),
                supportUrl = meta.supportUrl.orEmpty(),
                userinfo = meta.subscriptionUserinfo.orEmpty(),
            ),
        )
    }

    private fun openExternalUrl(url: String) {
        val target = url.trim().let {
            if (it.startsWith("t.me/", ignoreCase = true)) "https://$it" else it
        }
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(target))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    companion object {
        const val ACTION_DOWNLOAD_AND_INSTALL_UPDATE =
            "com.github.kr328.clash.action.MAIN_DOWNLOAD_INSTALL_UPDATE"
        const val EXTRA_UPDATE_TAG = "extra_update_tag"
        const val EXTRA_UPDATE_APK_URL = "extra_update_apk_url"
        const val EXTRA_UPDATE_APK_NAME = "extra_update_apk_name"
    }
}

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
import com.github.kr328.clash.remote.FilesClient
import com.github.kr328.clash.util.commitProfileWithProgress
import com.github.kr328.clash.util.fileName
import com.github.kr328.clash.util.createEmptyUrlProfileAndOpenEditor
import com.github.kr328.clash.util.updateProfileWithProgress
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
import com.github.kr328.clash.service.remote.IProxyDelayObserver
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
     * Runs [healthCheck] on every auto proxy group after connect, regardless
     * of tunnel mode.
     *
     * Earlier this only ran in Global mode because the original concern was
     * that in Rule mode it would fight [ConfigurationModule] applying
     * [SelectionDao] and flash the active card. That guard turned out to be
     * over-conservative for two reasons:
     *  1. [SelectionDao] only stores picks for Selector groups; auto types
     *     (URLTest / Fallback / LoadBalance) have no user-stored selection,
     *     so a health check on them can never overwrite a user's pick.
     *  2. Subscriptions that wrap an entire url-test/fallback subtree behind
     *     a single select root with `hidden: true` on the children (a very
     *     common kaso.fyi-style layout) leave every node ping-less in Rule
     *     mode without a warmup.
     *
     * A brief UI flash on connect is the acceptable trade-off for pings
     * actually working on those subscriptions.
     */
    private suspend fun warmUpAutoProxyGroupsForGlobalOnly(): Set<String> {
        return warmUpAutoProxyGroups()
    }

    /**
     * Run a per-proxy health check on [group] and stream each proxy's delay
     * into the active card as soon as URLTest resolves. Suspends until every
     * proxy has reported (or the call early-outed because the group is
     * missing / wrong type).
     *
     * UI patches are dispatched on the activity's MainScope and target the
     * design property — if the activity has already torn down the design by
     * the time a result lands, the patch is silently dropped. Callers that
     * need `now` / `alive` state should still issue a closing
     * refreshRuntimeGroupDetails — per-proxy push only carries the delay
     * measurement itself.
     */
    private suspend fun runPerProxyHealthCheck(group: String) {
        runCatching {
            withClash {
                healthCheckPerProxy(
                    group,
                    object : IProxyDelayObserver {
                        override fun onDelay(
                            grp: String,
                            proxy: String,
                            delayMs: Int,
                            errMsg: String,
                        ) {
                            // Failed URLTest carries errMsg + delayMs == 0. Showing 0 would
                            // render as "0ms" — engine convention is Int.MAX_VALUE for
                            // unreachable, so coerce before the patch.
                            val effective = if (errMsg.isNotEmpty()) Int.MAX_VALUE else delayMs
                            this@MainActivity.launch {
                                this@MainActivity.design?.patchSingleProxyDelay(grp, proxy, effective)
                            }
                        }

                        override fun onComplete(error: String?) {
                            // No-op: the outer suspend returns via await() in
                            // ClashManager.healthCheckPerProxy.
                        }
                    },
                )
            }
        }
    }

    private suspend fun warmUpAutoProxyGroups(): Set<String> = coroutineScope {
        waitForProxyEngineReady()

        // Walk every group (including hidden) so subscriptions whose entire
        // url-test/fallback subtree is `hidden: true` (visible root is a
        // plain select) still get warmed up. Filtering by visibility was the
        // bug: hidden auto-groups were skipped entirely, so pings never ran.
        val allGroupNames = runCatching {
            withClash { queryAllProxyGroupNamesIncludingHidden() }
        }.getOrDefault(emptyList())
        val visibleGroupNames = runCatching {
            withClash { queryProxyGroupNames(false) }
        }.getOrDefault(emptyList())
        if (allGroupNames.isEmpty() && visibleGroupNames.isEmpty()) return@coroutineScope emptySet()

        val allGroupSet = allGroupNames.toSet()
        val autoGroups = linkedSetOf<String>()
        // Iterate over the union so we can both flag visible auto roots and
        // discover hidden auto subgroups via their members.
        for (groupName in (allGroupNames + visibleGroupNames).distinct()) {
            val group = runCatching {
                withClash { queryProxyGroup(groupName, com.github.kr328.clash.core.model.ProxySort.Default) }
            }.getOrNull() ?: continue
            if (isAutoProxyGroup(groupName, group)) {
                autoGroups += groupName
            }
            for (proxy in group.proxies) {
                if (proxy.name in allGroupSet &&
                    (proxy.type == Proxy.Type.URLTest ||
                        proxy.type == Proxy.Type.Fallback ||
                        proxy.type == Proxy.Type.LoadBalance)
                ) {
                    autoGroups += proxy.name
                }
            }
        }
        if (autoGroups.isEmpty()) return@coroutineScope emptySet()

        // Prime engine state so per-proxy push has rows to land on. Without
        // this, the first warmup cycle on cold start would have nowhere to
        // write — proxyDetails is empty until the user scrolls into a group.
        val primed = refreshRuntimeGroupDetails(autoGroups)
        if (primed.isNotEmpty()) {
            design?.patchProxyDetails(primed)
        }

        autoGroups.map { group ->
            launch { runPerProxyHealthCheck(group) }
        }.joinAll()
        autoGroups.toSet()
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
        view.findViewById<View>(R.id.opt_file).setOnClickListener {
            dialog.dismiss()
            launch { importFromFile(design) }
        }
        dialog.show()
    }

    /**
     * Pick a local YAML, create a [Profile.Type.File] profile, write the picked
     * content straight into its `config.yaml` and commit — then activate it,
     * same as a URL/clipboard import. Falls back to Properties if the engine
     * rejects the file (invalid YAML), so the user can fix it.
     */
    private suspend fun importFromFile(design: MainDesign) {
        val uri: Uri = startActivityForResult(
            ActivityResultContracts.GetContent(),
            "*/*",
        ) ?: return

        val name = uri.fileName?.substringBeforeLast('.')?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.new_profile)

        val uuid = withProfile { create(Profile.Type.File, name) }

        try {
            FilesClient(this).copyDocument("$uuid/config.yaml", uri)
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

        design.onUpdateBadgeTap = { showUpdateAvailableDialog() }
        refreshUpdateBadge(design)

        design.onOpenBrandUrl = { url -> openExternalUrl(url) }

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
                            if (it == Event.ActivityStart) {
                                refreshUpdateBadge(design)
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
                            design.openProfilesTab()

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
                                initialUpdateStatus = AppUpdateChecker.peekCachedRelease(this@MainActivity)
                                    ?.let { getString(R.string.about_update_available, it.tagName) },
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
                                // Prime live engine state so per-proxy push has rows to land on.
                                // healthCheckPerProxy only updates delays for proxies already
                                // present in proxyDetails — without an initial query the very
                                // first ping cycle would be a silent no-op until the closing
                                // refresh below.
                                val primed = refreshRuntimeGroupDetails(groupsToRefresh)
                                if (primed.isNotEmpty()) {
                                    design.patchProxyDetails(primed)
                                }
                                val jobs = groupsToRefresh.map { groupName ->
                                    launch { runPerProxyHealthCheck(groupName) }
                                }
                                jobs.forEach { it.join() }
                                // Closing refresh captures `now` / `alive` fields the per-proxy
                                // push doesn't carry, plus any auto-group selection that the
                                // engine's url-test logic flipped after the ping cycle.
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
                        // Success / failure toast comes from the broadcast
                        // observer (onProfileUpdateCompleted / Failed). Don't
                        // toast "started" here — the user sees that *after*
                        // the modal closes, by which point the work is done.
                        runCatching { updateProfileWithProgress(profile.uuid) }
                        // Run metadata sync in the background so the UI tap returns
                        // immediately. The earlier race (announcement-card hiding the
                        // active-card support button for a tick) was structural — we
                        // removed that coverage logic, so there's nothing to race now
                        // and we don't need to block the user on a slow HTTP roundtrip.
                        launch(Dispatchers.IO) {
                            uiStore.subscriptionMetadataLastFetch = 0L
                            runCatching { syncSubscriptionMetadata() }
                            withContext(Dispatchers.Main) { refreshAnnouncement(design) }
                        }
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

                design.profileUpdateAllRequests.onReceive {
                    launch {
                        // Per-profile guard: one subscription failing (network /
                        // bad config / commit) must NOT take down the whole
                        // "Update all" with an uncaught coroutine exception.
                        var failed = 0
                        withProfile {
                            queryAll().forEach { p ->
                                if (p.imported && p.type != Profile.Type.File) {
                                    runCatching { update(p.uuid) }.onFailure { failed++ }
                                }
                            }
                        }
                        if (failed > 0) {
                            design.showToast(
                                getString(R.string.profiles_update_all_failed, failed),
                                ToastDuration.Long,
                            )
                        }
                        design.fetch()
                    }
                }

                design.profileReorderRequests.onReceive { ordered ->
                    launch { withProfile { reorder(ordered.map { it.uuid.toString() }) } }
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
        m.findItem(R.id.profile_menu_subscription_sources).isVisible = profile.imported
        m.findItem(R.id.profile_menu_duplicate).isVisible = profile.imported
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
                R.id.profile_menu_view_config -> {
                    startActivity(ProfileConfigActivity::class.intent.setUUID(profile.uuid))
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
        // Optimistic import: don't block the UI on Profile-Title lookup.
        // Create with a host-based preliminary name (instant), kick off the
        // network guess in the background, and rename after commit() if a
        // better name arrives. Previous flow froze the UI for up to 8s before
        // the progress bar even appeared.
        val pending = com.github.kr328.clash.util.AsyncNameResolver.start(this, this@MainActivity, url)
        val uuid = withProfile {
            create(Profile.Type.Url, pending.preliminaryName, url)
        }
        try {
            commitProfileWithProgress(uuid)
        } catch (e: Exception) {
            pending.betterName.cancel()
            showImportCommitFailureDialog(uuid, e)
            return
        }
        val displayName = upgradeProfileNameIfBetter(uuid, pending)
        val profile = withProfile { queryByUUID(uuid) }
        if (profile?.imported == true) {
            withProfile { setActive(profile) }
            ensureGlobalSelectionSafeWithRetry()
            design.showToast(getString(R.string.import_done_named, displayName), ToastDuration.Long)
        } else {
            launchProperties(uuid)
        }
        design.fetch()
    }

    /**
     * Awaits the background [com.github.kr328.clash.util.AsyncNameResolver]
     * result and renames the imported profile if a better name arrived.
     * Uses [com.github.kr328.clash.service.remote.IProfileManager.renameImported]
     * (direct ImportedDao update) — NOT [patch], which would put the profile
     * back into a pending/draft state and surface as "unsaved" to the user.
     * Returns the effective display name (better one if applied, preliminary
     * otherwise).
     */
    private suspend fun upgradeProfileNameIfBetter(
        uuid: java.util.UUID,
        pending: com.github.kr328.clash.util.AsyncNameResolver.Pending,
    ): String {
        val better = runCatching { pending.betterName.await() }.getOrNull()
            ?: return pending.preliminaryName
        runCatching { withProfile { renameImported(uuid, better) } }
        return better
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
        applyActiveBrand()

        val proxyNames = if (running) {
            runCatching {
                // Visible-only here. ProfileAdapter.effectiveGroupsForProfile
                // applies a 1-hop heuristic on top to surface any hidden auto
                // subgroups (URLTest/Fallback/LoadBalance) that are direct
                // members of a visible root — covers the kaso.fyi pattern
                // (one visible select root, hidden auto children) without
                // flooding the pill bar with deeper internal infra.
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
        val transportInfoByProfile = previewUuids.associateWith { uuid ->
            withProfile { readProxyTransports(uuid) }
        }

        patchProxyGroups(
            proxyNames,
            running,
            state.mode,
            uiStore.proxyLastGroup,
            offlinePreviewByProfile,
            activeUuid,
            offlineSelectionsByProfile,
            transportInfoByProfile,
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
                    val warmed = warmUpAutoProxyGroupsForGlobalOnly()
                    d?.fetch()
                    // Force-push fresh delays into the UI immediately instead
                    // of waiting for the next dashboard ticker (interactive=2s,
                    // idle=30s). Mihomo health-check writes delays into
                    // proxy.LastDelayForTestUrl synchronously when warmup
                    // finishes; without this re-query the carriage stays blank
                    // until the timer rolls over. (FlClash side-steps the
                    // whole class of staleness with a delay event channel —
                    // see docs/path-b-engine-parsing.md follow-ups.)
                    if (warmed.isNotEmpty()) {
                        val patches = refreshRuntimeGroupDetails(warmed)
                        if (patches.isNotEmpty()) {
                            d?.patchProxyDetails(patches)
                        }
                    }
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
                    design?.openProfilesTab()
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
            // Connected actions: cache the release so the Home-header badge lights up
            // immediately and stays even if the user taps "Later", then show the same in-app
            // update dialog the Home icon uses — no system notification on the manual path.
            AppUpdateChecker.cacheRelease(this@MainActivity, latest)
            refreshUpdateBadge(design)
            showUpdateAvailableDialog()
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

    /**
     * Read the cached release snapshot and reflect it in the header badge. Also kicks off
     * a throttled background fetch so a release published since the last AlarmManager tick
     * surfaces while the activity is in the foreground.
     */
    private fun refreshUpdateBadge(design: MainDesign) {
        design.setUpdateBadgeVisible(AppUpdateChecker.isUpdateAvailable(this))
        launch {
            runCatching { AppUpdateChecker.maybeOpportunisticCheck(this@MainActivity) }
            design.setUpdateBadgeVisible(AppUpdateChecker.isUpdateAvailable(this@MainActivity))
        }
    }

    /**
     * In-app counterpart of the system update notification. Reads the cached release from
     * SharedPreferences so it opens instantly; the Download & install button feeds into the
     * same [ACTION_DOWNLOAD_AND_INSTALL_UPDATE] flow the notification action uses.
     */
    private fun showUpdateAvailableDialog() {
        val info = AppUpdateChecker.peekCachedRelease(this) ?: return
        val body = info.body.takeIf { it.isNotBlank() }
            ?: getString(R.string.update_dialog_message_fallback)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.update_dialog_title_fmt, info.tagName))
            .setMessage(body)
            .setNegativeButton(R.string.update_dialog_button_later, null)
        if (!info.apkUrl.isNullOrBlank()) {
            dialog.setPositiveButton(R.string.update_dialog_button_download) { _, _ ->
                AppUpdateChecker.dismissUpdateNotification(this)
                runCatching {
                    val id = GitHubReleaseUpdate.enqueueApkDownload(
                        context = this,
                        tagName = info.tagName.ifBlank { "update" },
                        apkUrl = info.apkUrl,
                        apkName = info.apkName,
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
        } else if (info.htmlUrl.isNotBlank()) {
            // No prebuilt APK on this release — fall back to opening the release page.
            dialog.setPositiveButton(R.string.about_open_release) { _, _ ->
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.htmlUrl)))
                }
            }
        }
        dialog.show()
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
        // Include hidden groups too — manual ping for a subscription whose
        // entire url-test/fallback subtree is hidden (visible root is select)
        // would otherwise stop at the root and miss every auto child.
        val knownGroups = runCatching { withClash { queryAllProxyGroupNamesIncludingHidden().toSet() } }
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

        design.setAnnouncement(
            text = text.takeIf { it.isNotBlank() },
            url = url.takeIf { it.isNotBlank() },
            supportUrl = supportUrl.takeIf { it.isNotBlank() },
            sourceUuid = active?.uuid,
            sourceName = active?.name,
            onOpenUrl = { openExternalUrl(it) },
            onSupport = {
                supportUrl.takeIf { it.isNotBlank() }?.let { openExternalUrl(it) }
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
        val brandManifest = com.github.kr328.clash.common.branding.BrandManifestParser.fetch(
            this,
            url,
            SubscriptionOverrides.getUserAgent(this, active.uuid),
            uiStore.subscriptionMetadataAllowInsecureHttp,
        )
        com.github.kr328.clash.service.branding.BrandRefresh.apply(this, active.uuid, brandManifest)
        meta.shareLinksDisable?.let {
            ServiceStore(this).setSubscriptionShareLinksLockedFor(active.uuid, it)
        }
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

    /**
     * Reads the persisted brand state and pushes a [BrandHolder] snapshot into
     * MainDesign. Picks the theme-appropriate cached logo. Called on every
     * UI refresh tick so the header reflects the latest operator brand without
     * the design layer having to know about persistence.
     */
    private suspend fun applyActiveBrand() {
        val design = design as? com.github.kr328.clash.design.MainDesign ?: return
        // try/finally guarantees the recreate-check runs on EVERY exit path —
        // including the "no active profile" / "no brand for active profile"
        // branches. Otherwise a brand → no-brand switch sheds the UI layer but
        // leaves the theme overlay branded until the app is force-stopped.
        try {
            val activeUuid = runCatching { withProfile { queryActive() } }.getOrNull()?.uuid
            if (activeUuid == null) {
                design.applyBrand(com.github.kr328.clash.design.branding.BrandHolder.EMPTY)
                return
            }
            val json = runCatching { withProfile { readBrandJsonFor(activeUuid) } }.getOrNull()
            if (json.isNullOrBlank()) {
                design.applyBrand(com.github.kr328.clash.design.branding.BrandHolder.EMPTY)
                return
            }
            val manifest = com.github.kr328.clash.common.branding.BrandManifest.fromJson(json)
            val nightMode = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            val logoPath = runCatching {
                withProfile { brandLogoPathFor(activeUuid, nightMode) }
            }.getOrNull()
            com.github.kr328.clash.common.log.Log.d(
                "applyActiveBrand: uuid=$activeUuid, nightMode=$nightMode, " +
                    "manifest.logoUrl=${manifest.logoUrl}, manifest.logoLightUrl=${manifest.logoLightUrl}, " +
                    "resolvedPath=$logoPath",
            )
            design.applyBrand(
                com.github.kr328.clash.design.branding.BrandHolder(
                    manifest = manifest,
                    logoPath = logoPath,
                ),
            )
        } finally {
            // Theme overlay is captured at inflate time. If the active profile's
            // accent diverges from what's actually applied, recreate so the new
            // (or absent) harmonised palette flows into every widget that reads
            // ?attr/colorPrimary etc.
            com.github.kr328.clash.design.branding.BrandThemeApplier.maybeRecreateOnAccentChange(this)
        }
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

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
import com.github.kr328.clash.common.util.StandalonePing
import com.github.kr328.clash.common.util.SubscriptionNameGuesser
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.bridge.*
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
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.util.RussianBypassDefaults
import com.github.kr328.clash.util.HttpTextFetcher
import com.github.kr328.clash.util.showProfileQuickEditSheet
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : BaseActivity<MainDesign>() {
    private data class ReleaseInfo(
        val tagName: String,
        val body: String,
        val htmlUrl: String,
        val apkUrl: String?,
        val apkName: String?,
    )

    private data class HomeRuntimeSnapshot(
        val running: Boolean,
        val activeProfileUuid: UUID?,
        val proxyNames: List<String>,
    )

    private val scanLauncher = registerForActivityResult(ScanQRCode(), ::onScanResult)
    private var lastForwardedTrafficTotal: Long = Long.MIN_VALUE
    private var isCheckingUpdates: Boolean = false
    private var isToggleStatusInFlight: Boolean = false
    private var pendingApkDownloadId: Long = -1L
    private var downloadReceiverRegistered: Boolean = false
    private val updatePrefs by lazy { getSharedPreferences("app_update", MODE_PRIVATE) }
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
            setMode(normalized)

            val running = withContext(Dispatchers.IO) { resolveStatusSnapshot().serviceRunning }
            if (running) {
                val error = runCatching {
                    withClash {
                        val o = queryOverride(Clash.OverrideSlot.Session)
                        o.mode = normalized
                        patchOverride(Clash.OverrideSlot.Session, o)
                    }
                }.exceptionOrNull()

                if (error != null && error !is CancellationException) {
                    showExceptionToast(error as? Exception ?: Exception(error))
                }
            }
        } catch (e: Exception) {
            if (e !is CancellationException) showExceptionToast(e)
        } finally {
            scheduleRefresh()
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

    private fun showHomeImportSheet(design: MainDesign) {
        val dialog = AppBottomSheetDialog(this, fitContentHeight = false)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_home_import, null)
        dialog.setContentView(view)
        view.findViewById<TextView>(R.id.opt_clipboard).setOnClickListener {
            dialog.dismiss()
            launch { importFromClipboard(design) }
        }
        view.findViewById<TextView>(R.id.opt_url).setOnClickListener {
            dialog.dismiss()
            startActivity(NewProfileActivity::class.intent)
        }
        view.findViewById<TextView>(R.id.opt_qr).setOnClickListener {
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
        val tickerIdle = ticker(TimeUnit.SECONDS.toMillis(8))
        val profileTicker = ticker(TimeUnit.MINUTES.toMillis(2))
        val refreshRequests = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
        val proxyDetailRequests =
            kotlinx.coroutines.channels.Channel<Pair<Profile, String>>(kotlinx.coroutines.channels.Channel.CONFLATED)
        var announcementRefreshPending = false
        var proxyDetailJob: Job? = null
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
                                includeAnnouncement = it == Event.ActivityStart,
                                force = serviceStateEvent,
                            )
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
                            startActivity(RoutingHubActivity::class.intent)

                        MainDesign.Request.OpenPerAppRouting ->
                            startActivity(AccessControlActivity::class.intent)

                        MainDesign.Request.OpenProfiles ->
                            startActivity(ProfilesActivity::class.intent)

                        MainDesign.Request.OpenSettings ->
                            startActivity(SettingsActivity::class.intent)

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
                    launch {
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
                        withClash {
                            patchSelector(group, name)
                        }
                        uiStore.proxyLastGroup = group
                        scheduleProxyDetailsRefresh(profile, group)
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
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.profile_menu_set_active -> {
                    launch {
                        withProfile {
                            if (profile.imported) {
                                setActive(profile)
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
        val name = SubscriptionNameGuesser.guessFast(url)
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
        }

        setMode(state.mode)

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

        val detail = withClash { queryProxyGroup(group, uiStore.proxySort) }
        if (withProfile { queryActive()?.uuid } == profile.uuid) {
            patchProxyDetails(mapOf(group to detail))
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
        val latest = fetchLatestReleaseInfo()
        if (latest == null) {
            setStatus(getString(R.string.about_update_check_failed))
            return
        }
        val current = withContext(Dispatchers.IO) {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
        }
        val hasUpdate = compareVersions(latest.tagName, current) > 0
        withContext(Dispatchers.Main) {
            if (!hasUpdate) {
                setStatus(getString(R.string.about_update_latest))
                return@withContext
            }
            setStatus(getString(R.string.about_update_available, latest.tagName))
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(getString(R.string.about_update_available, latest.tagName))
                .setMessage(latest.body.take(1200))
                .setPositiveButton(R.string.about_download_install) { _, _ ->
                    if (!startApkDownload(latest)) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latest.htmlUrl)))
                    }
                }
                .setNeutralButton(R.string.about_open_release) { _, _ ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latest.htmlUrl)))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private suspend fun fetchLatestReleaseInfo(): ReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = "https://api.github.com/repos/Nemu-x/ClashFest/releases/latest"
            val text = HttpTextFetcher.fetchUtf8(
                endpoint,
                connectTimeoutMs = 15_000,
                readTimeoutMs = 15_000,
                headers = mapOf("User-Agent" to "ClashFest/${BuildConfig.VERSION_NAME}"),
            )
            val json = JSONObject(text)
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            var apkName: String? = null

            if (assets != null) {
                // Prefer universal/arm64 alpha assets, then fallback to any APK.
                val candidates = mutableListOf<JSONObject>()
                for (i in 0 until assets.length()) {
                    val item = assets.optJSONObject(i) ?: continue
                    val name = item.optString("name")
                    val url = item.optString("browser_download_url")
                    if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                        candidates += item
                    }
                }
                val picked = candidates.firstOrNull {
                    val n = it.optString("name").lowercase(Locale.ROOT)
                    n.contains("alpha") && n.contains("universal")
                } ?: candidates.firstOrNull {
                    val n = it.optString("name").lowercase(Locale.ROOT)
                    n.contains("alpha") && n.contains("arm64-v8a")
                } ?: candidates.firstOrNull()

                apkUrl = picked?.optString("browser_download_url")
                apkName = picked?.optString("name")
            }

            ReleaseInfo(
                tagName = json.optString("tag_name"),
                body = json.optString("body"),
                htmlUrl = json.optString("html_url"),
                apkUrl = apkUrl,
                apkName = apkName,
            )
        }.getOrNull()
    }

    private fun compareVersions(left: String, right: String): Int {
        fun semverTriplet(v: String): IntArray? {
            val m = Regex("""(\d+)\.(\d+)\.(\d+)""").find(v) ?: return null
            return intArrayOf(
                m.groupValues[1].toIntOrNull() ?: 0,
                m.groupValues[2].toIntOrNull() ?: 0,
                m.groupValues[3].toIntOrNull() ?: 0,
            )
        }

        val a = semverTriplet(left)
        val b = semverTriplet(right)
        if (a != null && b != null) {
            for (i in 0..2) {
                if (a[i] != b[i]) return a[i].compareTo(b[i])
            }
            return 0
        }

        // Safe fallback for unexpected formats.
        return left.compareTo(right)
    }

    private fun startApkDownload(release: ReleaseInfo): Boolean {
        val url = release.apkUrl ?: return false
        val dm = getSystemService<DownloadManager>() ?: return false

        val fileName = (release.apkName ?: "clashfest-${release.tagName}.apk")
            .replace(Regex("""[^A-Za-z0-9._-]"""), "_")

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(getString(R.string.about_update_available, release.tagName))
            .setDescription(getString(R.string.about_download_started))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("application/vnd.android.package-archive")
            .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName)

        pendingApkDownloadId = dm.enqueue(request)
        updatePrefs.edit().putLong("pending_download_id", pendingApkDownloadId).apply()
        Toast.makeText(this, R.string.about_download_started, Toast.LENGTH_SHORT).show()
        return true
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
        groups: Map<String, List<String>>,
    ): List<String> {
        val leaves = linkedSetOf<String>()
        val seenGroups = linkedSetOf<String>()

        fun visitName(name: String) {
            if (name.isBlank()) return
            val nested = groups[name]
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
        val text = uiStore.announcement
        val url = uiStore.announcementUrl
        val supportUrl = uiStore.supportUrl
        val hash = text.hashCode().toString()
        if (uiStore.announcementSeenHash != hash) {
            uiStore.announcementSeenHash = hash
            uiStore.announcementDismissed = false
        }
        val textVisible = text.isNotBlank() && !uiStore.announcementDismissed
        val usage = com.github.kr328.clash.common.util.SubscriptionUsage.parse(
            uiStore.subscriptionUserinfo.takeIf { it.isNotBlank() }
        )

        design.setAnnouncement(
            text = if (textVisible) text else null,
            url = url.takeIf { it.isNotBlank() },
            usage = usage,
            supportUrl = supportUrl.takeIf { it.isNotBlank() },
            onOpenUrl = { openExternalUrl(it) },
            onDismiss = if (text.isNotBlank()) ({ uiStore.announcementDismissed = true }) else null,
            onRefresh = {
                // Force cooldown bypass by resetting timestamp, then re-fetch.
                launch(Dispatchers.IO) {
                    uiStore.subscriptionMetadataLastFetch = 0L
                    runCatching { syncSubscriptionMetadata() }
                    withContext(Dispatchers.Main) { refreshAnnouncement(design) }
                }
            },
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
        if (now - uiStore.subscriptionMetadataLastFetch < cooldown) return

        val meta = com.github.kr328.clash.common.util.SubscriptionMetadataFetcher.fetch(this, url)
        if (meta.isEmpty()) {
            // still mark the attempt to avoid hammering the server every screen-on
            uiStore.subscriptionMetadataLastFetch = now
            return
        }
        uiStore.subscriptionMetadataLastFetch = now

        // Always-mirrored fields (cache only):
        meta.subscriptionUserinfo?.let { uiStore.subscriptionUserinfo = it }
        meta.profileWebPageUrl?.let { uiStore.profileWebPageUrl = it }
        meta.profileUpdateIntervalHours?.let { uiStore.profileUpdateIntervalHours = it }

        // User-overridable fields — skip if user opted out of operator overrides.
        if (!uiStore.subscriptionMetadataLockUser) {
            meta.supportUrl?.let { uiStore.supportUrl = it }
            meta.announcement?.let { uiStore.announcement = it }
            meta.announcementUrl?.let { uiStore.announcementUrl = it }
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
}

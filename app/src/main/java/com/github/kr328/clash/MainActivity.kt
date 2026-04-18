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
import android.provider.Settings
import android.os.PowerManager
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import com.github.kr328.clash.design.dialog.AppBottomSheetDialog
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.util.StandalonePing
import com.github.kr328.clash.common.util.SubscriptionNameGuesser
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.bridge.*
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.MainDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.service.model.Profile
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
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

    private val scanLauncher = registerForActivityResult(ScanQRCode(), ::onScanResult)
    private var lastForwardedTrafficTotal: Long = Long.MIN_VALUE
    private var isCheckingUpdates: Boolean = false
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

    private fun parseTunnelMode(name: String): TunnelState.Mode? =
        when (name) {
            TunnelState.Mode.Rule.name -> TunnelState.Mode.Rule
            TunnelState.Mode.Global.name -> TunnelState.Mode.Global
            TunnelState.Mode.Direct.name -> TunnelState.Mode.Direct
            else -> null
        }

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
        val dialog = AppBottomSheetDialog(this, fitContentHeight = true)
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

        val tickerInteractive = ticker(TimeUnit.SECONDS.toMillis(1))
        val tickerIdle = ticker(TimeUnit.SECONDS.toMillis(3))
        val profileTicker = ticker(TimeUnit.MINUTES.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart,
                        Event.ServiceRecreated,
                        Event.ClashStop,
                        Event.ClashStart,
                        Event.ProfileLoaded,
                        Event.ProfileChanged -> design.fetch()

                        else -> Unit
                    }
                }

                design.requests.onReceive {
                    when (it) {
                        MainDesign.Request.ToggleStatus -> {
                            launch {
                                if (clashRunning) {
                                    stopClashService()
                                } else {
                                    design.setTunnelStarting(true)
                                    try {
                                        design.startClash()
                                    } finally {
                                        design.fetch()
                                    }
                                }
                            }
                        }

                        MainDesign.Request.OpenNewProfile ->
                            showHomeImportSheet(design)

                        MainDesign.Request.OpenProxyGroups ->
                            startActivity(ProxyActivity::class.intent)

                        MainDesign.Request.OpenConnections ->
                            startActivity(ConnectionsActivity::class.intent)

                        MainDesign.Request.OpenRules ->
                            startActivity(RuleSnippetActivity::class.intent)

                        MainDesign.Request.OpenEffectiveRules ->
                            startActivity(EffectiveRulesActivity::class.intent)

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

                        MainDesign.Request.PatchModeDirect -> {
                            uiStore.tunnelModePreference = TunnelState.Mode.Direct.name
                            withClash {
                                val o = queryOverride(Clash.OverrideSlot.Session)
                                o.mode = TunnelState.Mode.Direct
                                patchOverride(Clash.OverrideSlot.Session, o)
                            }
                            design.fetch()
                        }

                        MainDesign.Request.PatchModeGlobal -> {
                            uiStore.tunnelModePreference = TunnelState.Mode.Global.name
                            withClash {
                                val o = queryOverride(Clash.OverrideSlot.Session)
                                o.mode = TunnelState.Mode.Global
                                patchOverride(Clash.OverrideSlot.Session, o)
                            }
                            design.fetch()
                        }

                        MainDesign.Request.PatchModeRule -> {
                            uiStore.tunnelModePreference = TunnelState.Mode.Rule.name
                            withClash {
                                val o = queryOverride(Clash.OverrideSlot.Session)
                                o.mode = TunnelState.Mode.Rule
                                patchOverride(Clash.OverrideSlot.Session, o)
                            }
                            design.fetch()
                        }

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
                        if (!clashRunning) {
                            if (isProxyProviderKeyName(name)) {
                                design.fetch()
                                return@launch
                            }
                            withProfile {
                                rememberProxySelection(profile.uuid, group, name)
                            }
                            uiStore.proxyLastGroup = group
                            design.fetch()
                            return@launch
                        }
                        withClash {
                            patchSelector(group, name)
                        }
                        uiStore.proxyLastGroup = group
                        design.fetch()
                    }
                }

                design.profilePingAllRequests.onReceive { (profile, _, nodeNames) ->
                    launch {
                        try {
                            design.setPingingProfile(profile.uuid)
                            val engineReady = runCatching {
                                clashRunning && withClash { queryProxyGroupNames(false).isNotEmpty() }
                            }.getOrDefault(false)
                            if (engineReady) {
                                val activeUuid = withProfile { queryActive()?.uuid }
                                if (activeUuid != profile.uuid && profile.imported) {
                                    withProfile { setActive(profile) }
                                }
                                waitForProxyEngineReady()
                                design.clearStandalonePingForProfile(profile.uuid)
                                withClash { healthCheckAll() }
                                delay(1200L)
                                design.fetch()
                            } else {
                                val results = LinkedHashMap<String, Int>()
                                for (name in nodeNames) {
                                    if (StandalonePing.isBuiltinProxyName(name)) continue
                                    val yaml = withProfile { readProxyEntryYaml(profile.uuid, name) }
                                        ?: continue
                                    val hp = StandalonePing.parseServerPortFromProxyYaml(yaml) ?: continue
                                    val ms = StandalonePing.tcpConnectMs(hp.first, hp.second)
                                        .getOrNull()?.toInt() ?: continue
                                    results[name] = ms
                                }
                                design.patchStandalonePingResults(profile.uuid, results)
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
                        design.showToast(R.string.profile_update_scheduled, ToastDuration.Long)
                        design.fetch()
                    }
                }

                design.profileProxyYamlRequests.onReceive { (profile, _, proxyName) ->
                    launch {
                        val yaml = withProfile { readProxyEntryYaml(profile.uuid, proxyName) }
                        showProxyYamlDialog(proxyName, yaml ?: getString(R.string.proxy_yaml_missing))
                    }
                }

                design.profileExpandChanged.onReceive {
                    launch {
                        design.fetch()
                    }
                }

                design.profileActivateRequests.onReceive { profile ->
                    withProfile {
                        if (profile.imported) {
                            setActive(profile)
                        } else {
                            design.requestSave(profile)
                        }
                    }
                    design.fetch()
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
                        design.fetchTraffic()
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
        if (!text.startsWith("http://", ignoreCase = true) &&
            !text.startsWith("https://", ignoreCase = true)
        ) {
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
        val name = SubscriptionNameGuesser.guess(this, url)
        val uuid = withProfile {
            create(Profile.Type.Url, name, url)
        }
        try {
            withProfile { commit(uuid) }
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
            AlertDialog.Builder(this@MainActivity)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private suspend fun MainDesign.fetch() {
        setClashRunning(clashRunning)

        var state = withClash {
            queryTunnelState()
        }

        if (clashRunning) {
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
                }
            } else {
                uiStore.tunnelModePreference = state.mode.name
            }
        }

        setMode(state.mode)

        setProfileName(withProfile { queryActive()?.name })
        patchProfiles(withProfile { queryAll() })

        val proxyNames = if (clashRunning) {
            try {
                withClash { queryProxyGroupNames(uiStore.proxyExcludeNotSelectable) }
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        val activeUuid = withProfile { queryActive()?.uuid }
        val expandedSet = getExpandedProfileUuids()
        val offlinePreviewByProfile = expandedSet.associateWith { uuid ->
            withProfile { readProxyGroupsPreview(uuid) }
        }
        val offlineSelectionsByProfile = expandedSet.associateWith { uuid ->
            withProfile { queryProxySelections(uuid) }
        }
        val detailMap = if (
            clashRunning &&
            activeUuid != null &&
            proxyNames.isNotEmpty()
        ) {
            proxyNames.associateWith { name ->
                withClash { queryProxyGroup(name, uiStore.proxySort) }
            }
        } else {
            emptyMap()
        }
        patchProxyGroups(
            proxyNames,
            clashRunning,
            state.mode,
            uiStore.proxyLastGroup,
            offlinePreviewByProfile,
            activeUuid,
            offlineSelectionsByProfile,
        )
        patchProxyDetails(detailMap)
        syncThemeToggleIcon(uiStore.darkMode)
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
        val active = withProfile { queryActive() }

        if (active == null || !active.imported) {
            setTunnelStarting(false)
            showToast(R.string.no_profile_selected, ToastDuration.Long) {
                setAction(R.string.profiles) {
                    startActivity(ProfilesActivity::class.intent)
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

                if (result.resultCode == RESULT_OK) {
                    startClashService()
                } else {
                    setTunnelStarting(false)
                }
            }
        } catch (e: Exception) {
            setTunnelStarting(false)
            design?.showToast(R.string.unable_to_start_vpn, ToastDuration.Long)
        }
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
            val text = URL(endpoint).openStream().bufferedReader().use { it.readText() }
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
}
